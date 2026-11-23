#!/usr/bin/env python3
"""数据资产接线检查：确保 data/ 下的每个数据文件都有消费方或已在基线里登记。

背景：项目里曾出现 data/init.sql（400 种作物规程）长期没有代码读取、
crop-schedule.json 只被校验脚本引用的情况，数据看起来"都在"，实际没接上。
本脚本把"哪个数据文件被谁消费"变成显式检查，防止新的孤儿数据悄悄堆积。

判定规则：
  1. 在消费方代码（后端/前端/小程序/shared/deploy 脚本与工具）里搜索该文件的
     路径、文件名，命中即视为已接线。
  2. 没有命中但在 deploy/data-assets.json 的 entries 里登记过（status 为
     generator-input / tool-only / ops-reference 等），视为已登记。
  3. 两者都没有 → 孤儿数据，退出码 1。
  4. 基线里登记但磁盘上已不存在的 → 过期条目，退出码 1。

用法：
  python3 deploy/tools/check-data-assets.py            # 检查，失败退出 1
  python3 deploy/tools/check-data-assets.py --list     # 只打印接线情况
  python3 deploy/tools/check-data-assets.py --update-baseline
      # 把当前孤儿写进 deploy/data-assets.json（人工确认后在提交里补 note）
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "data"
BACKEND_DIR = ROOT / "backend"
BASELINE = ROOT / "deploy" / "data-assets.json"

# 后端运行时以 backend/ 为工作目录，Java 里会写 ../data/xxx；前端从站点根写 data/xxx。
CONSUMER_DIRS = [
    "backend/src",
    "backend/test",
    "frontend",
    "miniprogram",
    "shared",
    "deploy",
    "scripts",
]
CONSUMER_FILES = ["deploy.sh"]

# 数据文件后缀；.py 是工具脚本而非数据，跳过
ASSET_EXTS = {".json", ".sql"}

# 这些文件只"声明/盘点"数据，不消费数据：data-contract.json 列了所有数据文件、
# validate-contracts.js 只校验字段合规，若把它们当消费方，会让孤儿数据看起来已接线。
DECLARATION_ONLY = {
    "shared/constants/data-contract.json",
    "shared/constants/validate-contracts.js",
    "deploy/tools/check-data-assets.py",
    "deploy/data-assets.json",
}


def iter_assets() -> list[Path]:
    files: list[Path] = []
    for path in sorted(DATA_DIR.rglob("*")):
        if path.is_file() and path.suffix.lower() in ASSET_EXTS:
            files.append(path)
    for name in ("crop-protocols.json", "market-festivals.json"):
        p = BACKEND_DIR / name
        if p.is_file():
            files.append(p)
    return files


def iter_consumers() -> list[Path]:
    files: list[Path] = []
    for folder in CONSUMER_DIRS:
        base = ROOT / folder
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file():
                continue
            if rel(path) in DECLARATION_ONLY:
                continue
            if path.suffix.lower() in {".java", ".js", ".mjs", ".html", ".css", ".sh", ".py", ".ps1", ".json", ".yml", ".yaml"}:
                files.append(path)
    for folder in CONSUMER_FILES:
        p = ROOT / folder
        if p.is_file():
            files.append(p)
    return files


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def load_consumers() -> dict[Path, str]:
    texts: dict[Path, str] = {}
    for path in iter_consumers():
        try:
            texts[path] = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
    return texts


def find_consumers(asset: Path, consumers: dict[Path, str], name_counts: dict[str, int]) -> list[str]:
    """返回引用了该数据文件的消费方相对路径（最多 8 个）。"""
    asset_rel = rel(asset)
    name = asset.name
    # 站点根路径 data/... 、后端工作目录的 ../data/... 、以及去掉 data/ 前缀的写法
    needles = {
        asset_rel,
        "../" + asset_rel,
        asset_rel[len("data/"):] if asset_rel.startswith("data/") else asset_rel,
    }
    # 文件名只在全局唯一时才算引用，避免 tickets.json 这类同名误判
    if name_counts.get(name, 0) == 1:
        needles.add(name)
    hits: list[str] = []
    for path, text in consumers.items():
        if any(needle in text for needle in needles):
            hits.append(rel(path))
    return hits[:8]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--list", action="store_true", help="只打印接线情况，不判定失败")
    ap.add_argument("--update-baseline", action="store_true", help="把未接线的数据文件写入基线")
    args = ap.parse_args()

    baseline: dict[str, dict] = {}
    if BASELINE.is_file():
        raw = json.loads(BASELINE.read_text(encoding="utf-8"))
        baseline = raw.get("entries", {})

    consumers = load_consumers()
    assets = iter_assets()
    name_counts: dict[str, int] = {}
    for asset in assets:
        name_counts[asset.name] = name_counts.get(asset.name, 0) + 1

    wired: list[tuple[str, list[str]]] = []
    registered: list[tuple[str, str]] = []
    orphans: list[str] = []

    for asset in assets:
        asset_rel = rel(asset)
        hits = find_consumers(asset, consumers, name_counts)
        if hits:
            wired.append((asset_rel, hits))
        elif asset_rel in baseline:
            registered.append((asset_rel, baseline[asset_rel].get("note", "")))
        else:
            orphans.append(asset_rel)

    if args.update_baseline:
        for orphan in orphans:
            baseline[orphan] = {
                "status": "unwired",
                "note": "待确认消费方或迁入 data/attic/（由 check-data-assets.py --update-baseline 生成）",
            }
        BASELINE.parent.mkdir(parents=True, exist_ok=True)
        BASELINE.write_text(
            json.dumps({"version": 1, "entries": dict(sorted(baseline.items()))}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"基线已更新：{BASELINE}（孤儿 {len(orphans)} 个）")

    stale = [key for key in baseline if not (ROOT / key).is_file()]

    if args.list or args.update_baseline:
        for path, hits in wired:
            print(f"[已接线] {path}\n          <- {', '.join(hits)}")
        for path, note in registered:
            print(f"[已登记] {path}  {note}")
        for path in orphans:
            print(f"[孤儿]   {path}")
        for path in stale:
            print(f"[过期]   {path}（基线里有，磁盘上没有）")
        print(f"\n汇总：已接线 {len(wired)}，已登记 {len(registered)}，孤儿 {len(orphans)}，过期 {len(stale)}")
        return 0 if not orphans and not stale else 1 if not args.update_baseline else 0

    for path, hits in wired:
        print(f"[已接线] {path} <- {', '.join(hits)}")
    for path, note in registered:
        print(f"[已登记] {path}  {note}")
    for path in orphans:
        print(f"[孤儿]   {path}：没有代码引用，也没有在 deploy/data-assets.json 登记", file=sys.stderr)
    for path in stale:
        print(f"[过期]   {path}：基线登记了但文件不存在", file=sys.stderr)

    print(f"\n汇总：已接线 {len(wired)}，已登记 {len(registered)}，孤儿 {len(orphans)}，过期 {len(stale)}")
    if orphans or stale:
        print("处理办法：给数据找到消费方；或确认是生成器输入/运维参考后登记到 deploy/data-assets.json。", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
