#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修正 data/init.sql 里 crop_procedure 的 job_type 错位。

背景：原始种子数据 3200 行里有 705 行的 job_type 与 job_name 语义不符（「萌芽前施肥」标
SPRAY、「花前打药」标 FERTILIZE 等），产品侧 opType 直接采信 job_type 会给出错误的农事/
天气建议。本脚本用与 gen-protocols-from-initsql.py 相同的 protocol_rules.infer_op()
逐行推断，把 job_type 改成对应的 SPRAY/FERTILIZE/IRRIGATE/HARVEST；名称里没有操作关键
字的管理类节点（控温/间苗/遮阴/打顶/修剪/培土/搭架/养护/割胶等 32 行）保持原值。

用法：
  python deploy/tools/fix-initsql-jobtype.py --dry-run   # 只统计差异
  python deploy/tools/fix-initsql-jobtype.py             # 原地改写 data/init.sql
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL_PATH = ROOT / "data" / "init.sql"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from protocol_rules import OP_TO_JOB_TYPE, infer_op  # noqa: E402

ROW_RE = re.compile(
    r"\(\s*'?(C\d{3})'?\s*,\s*'([^']*)'\s*,\s*'([^']*)'\s*,\s*'([^']*)'\s*,\s*(\d+)\s*,\s*'([^']*)'"
)
MARKER = "-- [job_type-fix 2026-11-23]"


def main():
    dry_run = "--dry-run" in sys.argv
    text = SQL_PATH.read_text(encoding="utf-8")
    changes = Counter()
    kept = 0
    rows = 0

    def replace(m):
        nonlocal kept, rows
        rows += 1
        cid, crop, job, job_type, days, desc = m.groups()
        op = infer_op(job)
        if op is None:
            kept += 1
            return m.group(0)
        new_type = OP_TO_JOB_TYPE[op]
        if new_type == job_type:
            return m.group(0)
        changes[f"{job_type}->{new_type}"] += 1
        return f"('{cid}','{crop}','{job}','{new_type}',{days},'{desc}')"

    new_text = ROW_RE.sub(replace, text)
    changed = sum(changes.values())
    print(f"扫描 {rows} 行：job_type 修正 {changed} 行，无关键字回退保留 {kept} 行")
    for k, v in changes.most_common():
        print(f"  {k}: {v}")
    if changed == 0:
        print("没有需要修正的行")
        return
    if MARKER not in new_text:
        new_text = MARKER + f" 按 job_name 语义批量修正 {changed} 行（原数据 job_type 与名称错位）。\n" + new_text
    if dry_run:
        print("dry-run：未写文件")
        return
    SQL_PATH.write_text(new_text, encoding="utf-8", newline="\n")
    print(f"已写入 {SQL_PATH}")


if __name__ == "__main__":
    main()
