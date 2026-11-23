#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 data/init.sql（「后端乙 计算与规则层」种子数据）转成 backend/crop-protocols.json 同构格式。

背景：data/init.sql 里有 crop_procedure 表与 400 作物 × 8 节点（C001~C400）的种子数据，
但这份 SQL 从未导入数据库，也从未被任何运行时代码读取；线上只有 13 个手工规程。
本脚本把 3200 行转换成 ComputeStub.readProtocols() 能直接读取的格式，
与现有 13 个手工规程合并（手工规程内容保持不变，避免覆盖已复核的内容）。

规则：
  · opType 以 job_name 语义为准（原始数据的 job_type 与 job_name 大面积错位：3200 行里
    2959 行与名称推断不一致，例如「萌芽前施肥」被标成 SPRAY、「花前打药」被标成 FERTILIZE，
    直接采用 job_type 会让「施肥节点」被判成打药，进而让 GuidanceService 给出错误的天气建议）：
      harvest     采收/收获/收割/采摘
      pesticide   打药/防治/病虫害/喷药/用药/防病/杀虫/除草
      irrigation  灌溉/浇水/排水/控水/灌水/滴灌
      fertilize   施肥/追肥/补苗/培肥/促根/促苗
    名称里没有上述关键字的管理类节点（32 行：发菌期控温、苗期间苗、遮阴、打顶、修剪、
    培土、搭架、割胶养护等）保留原 job_type 映射：SPRAY→pesticide、FERTILIZE→fertilize、
    IRRIGATE→irrigation、HARVEST→harvest；
  · 原表没有播种节点（最早是播种后 15~30 天），导入时为每个作物补一个 dayOffset=0 的
    sowing 节点作为规程第 0 天（desc 明确说明这是导入补全的基准节点，不虚构农事细节）；
  · days_after_sowing → dayOffset、job_name → title、description → desc；
  · reviewStatus 一律 pending_review（原始数据未经农技复核，不得当作事实直接展示为定论）；
  · 手工 13 种（大蒜/小麦/玉米/水稻/大豆/番茄/黄瓜/辣椒/茄子/大白菜/马铃薯/萝卜/西瓜）
    以 standardName 判定，保留原手工规程不替换。

用法：
  python deploy/tools/gen-protocols-from-initsql.py            # 生成并覆盖 backend/crop-protocols.json
  python deploy/tools/gen-protocols-from-initsql.py --dry-run  # 只校验与统计，不写文件
"""
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL_PATH = ROOT / "data" / "init.sql"
ALIAS_PATH = ROOT / "data" / "crop-base" / "alias.json"
PROTOCOL_PATH = ROOT / "backend" / "crop-protocols.json"
OUT_PATH = PROTOCOL_PATH

OP_MAP = {"SPRAY": "pesticide", "FERTILIZE": "fertilize", "IRRIGATE": "irrigation", "HARVEST": "harvest"}
ALLOWED_OPS = {"sowing", "irrigation", "fertilize", "pesticide", "harvest"}

# job_name 关键字 → 产品 opType（顺序有意义：先灌溉/控水，再采收，再打药，最后施肥；
# 「采收前控水」这类名称同时命中灌溉与采收，控水才是真实动作）
NAME_OP_RULES = [
    ("irrigation", ("灌溉", "浇水", "排水", "控水", "灌水", "滴灌")),
    ("harvest", ("采收", "收获", "收割", "采摘")),
    ("pesticide", ("打药", "防治", "病虫害", "喷药", "用药", "防病", "杀虫", "除草")),
    ("fertilize", ("施肥", "追肥", "补苗", "培肥", "促根", "促苗")),
]


def infer_op(job_name: str):
    """按农事名称推断 opType；无关键字时返回 None（调用方回退到 job_type）。"""
    for op, keywords in NAME_OP_RULES:
        if any(k in job_name for k in keywords):
            return op
    return None

# INSERT INTO crop_procedure (... ) VALUES ('C001','水稻','分蘖期病虫害防治','SPRAY',30,'防治稻飞虱'), ...
ROW_RE = re.compile(
    r"\(\s*'?(C\d{3})'?\s*,\s*'([^']*)'\s*,\s*'([^']*)'\s*,\s*'([^']*)'\s*,\s*(\d+)\s*,\s*'([^']*)'"
)


def parse_rows():
    text = SQL_PATH.read_text(encoding="utf-8", errors="replace")
    rows = ROW_RE.findall(text)
    if not rows:
        raise SystemExit("init.sql 里没有解析到任何 crop_procedure 行")
    return rows


def main():
    dry_run = "--dry-run" in sys.argv
    rows = parse_rows()

    alias = json.loads(ALIAS_PATH.read_text(encoding="utf-8"))
    display_by_canonical = {m["canonical"]: m["displayName"] for m in alias["mappings"]}

    manual = json.loads(PROTOCOL_PATH.read_text(encoding="utf-8"))
    manual_crops = manual["crops"]
    manual_std = set()
    manual_ids = set()
    for c in manual_crops:
        if c.get("standardName"):
            manual_std.add(c["standardName"])
        manual_ids.add(c["cropId"])

    # 按作物分组，校验每行
    grouped = OrderedDict()
    op_from_name = 0
    op_from_type = 0
    for cid, crop_name, job_name, job_type, days, desc in rows:
        if job_type not in OP_MAP:
            raise SystemExit(f"{cid} {crop_name} 出现未知 job_type：{job_type}")
        if not job_name.strip() or not desc.strip():
            raise SystemExit(f"{cid} {crop_name} 有空的 job_name/description")
        op = infer_op(job_name)
        if op is None:
            op = OP_MAP[job_type]
            op_from_type += 1
        else:
            op_from_name += 1
        grouped.setdefault(cid, {"name": crop_name, "nodes": []})
        grouped[cid]["nodes"].append(
            {"opType": op, "dayOffset": int(days), "title": job_name.strip(), "desc": desc.strip()}
        )

    # 校验 crop_id 与 alias.json 对齐
    if len(grouped) != 400:
        raise SystemExit(f"期望 400 个作物，实际 {len(grouped)} 个")
    for cid, g in grouped.items():
        if display_by_canonical.get(cid) != g["name"]:
            raise SystemExit(f"作物名不一致：{cid} init.sql={g['name']} alias={display_by_canonical.get(cid)}")

    generated = []
    skipped = []
    for cid, g in grouped.items():
        name = g["name"]
        if name in manual_std:
            skipped.append(name)
            continue
        nodes = [{
            "opType": "sowing",
            "dayOffset": 0,
            "title": "播种",
            "desc": f"当地适宜播期整地播种，作为规程第 0 天（原表从播种后第 {g['nodes'][0]['dayOffset']} 天开始记录）",
        }]
        nodes.extend(sorted(g["nodes"], key=lambda n: n["dayOffset"]))
        # 严格递增（原表无重复天数，这里再守一道）
        last = -1
        for n in nodes:
            if n["dayOffset"] <= last:
                n["dayOffset"] = last + 1
            last = n["dayOffset"]
        generated.append({
            "cropId": cid,
            "name": name,
            "standardName": name,
            "source": (
                "知农时「后端乙 计算与规则层」data/init.sql crop_procedure 种子数据"
                "（400 作物 × 8 节点，导入时补 dayOffset=0 播种节点，未经农技复核）"
            ),
            "reviewStatus": "pending_review",
            "nodes": nodes,
        })

    out = {
        "version": "0.4.0",
        "comment": (
            "作物栽培规程表（契约①的数据载体）。opType 只能是 sowing/irrigation/fertilize/pesticide/harvest，"
            "dayOffset 相对播种日递增（0 = 播种当天），reviewStatus=pending_review 的内容须经农技复核后才算事实（铁律 8）。"
            " ｜ V0.4.0：把「后端乙」data/init.sql 的 crop_procedure 种子数据（400 作物 × 8 节点）全量导入，"
            "与原有 13 个人工规程合并（人工规程原样保留）；原表无播种节点，导入时统一补 dayOffset=0 的 sowing 节点；"
            "原表 job_type 与 job_name 大面积错位，opType 以 job_name 语义为准（无关键字的 32 行回退 job_type）。"
            "生成脚本：deploy/tools/gen-protocols-from-initsql.py。"
        ),
        "crops": manual_crops + generated,
    }

    # 校验
    if len(out["crops"]) != 400:
        raise SystemExit(f"合并后期望 400 个规程，实际 {len(out['crops'])} 个")
    stds = [c.get("standardName") for c in out["crops"]]
    if len(set(stds)) != len(stds):
        raise SystemExit("standardName 有重复")
    for c in out["crops"]:
        if not c.get("cropId") or not c.get("nodes"):
            raise SystemExit(f"规程 {c.get('cropId')} 缺 cropId/nodes")
        last = -1
        for n in c["nodes"]:
            if n["opType"] not in ALLOWED_OPS:
                raise SystemExit(f"{c['cropId']} opType 非法：{n['opType']}")
            if n["dayOffset"] <= last:
                raise SystemExit(f"{c['cropId']} dayOffset 非严格递增")
            last = n["dayOffset"]
            if not n["title"] or not n["desc"]:
                raise SystemExit(f"{c['cropId']} 节点缺 title/desc")

    total_nodes = sum(len(c["nodes"]) for c in out["crops"])
    print(f"手工规程保留 {len(manual_crops)} 个（{', '.join(skipped)}）")
    print(f"从 init.sql 生成 {len(generated)} 个，合计 {len(out['crops'])} 个规程 / {total_nodes} 个节点")
    print(f"opType 来源：按 job_name 语义推断 {op_from_name} 行，无关键字回退 job_type {op_from_type} 行")
    if op_from_type:
        print("  （回退行均为管理类命名：发菌期控温、苗期间苗、遮阴、打顶、修剪、培土、搭架、割胶养护等）")
    if dry_run:
        print("dry-run：未写文件")
        return
    OUT_PATH.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已写入 {OUT_PATH}")


if __name__ == "__main__":
    main()
