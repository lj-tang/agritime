#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""crop_procedure 数据导出/修复共用的规则（生成器与 init.sql 修复脚本共用本模块）。

原始数据 data/init.sql 的 job_type 与 job_name 大面积错位（3200 行里 705 行不一致）：
例如「萌芽前施肥」被标成 SPRAY、「花前打药」被标成 FERTILIZE。产品侧的 opType 会驱动
GuidanceService 的天气建议文案，直接采用 job_type 会让施肥节点被当成打药，所以统一以
job_name 语义为准，只有少量管理类命名（控温/间苗/遮阴/打顶/修剪/培土/搭架/养护/割胶等）
没有操作关键字时才回退到 job_type。

另外 crop-schedule.json 的产区示范规程里 emergence/other（出苗、培土、搭架、清园等管理类
农事）没有更细的产品动作可归类，统一归入 other；GuidanceService 对未知 opType 走 default
分支给通用建议，前端不按 opType 渲染图标，因此 other 是安全的兜底值。
"""

# 原始 job_type ↔ 产品 opType
OP_MAP = {"SPRAY": "pesticide", "FERTILIZE": "fertilize", "IRRIGATE": "irrigation", "HARVEST": "harvest"}
OP_TO_JOB_TYPE = {v: k for k, v in OP_MAP.items()}
ALLOWED_OPS = {"sowing", "irrigation", "fertilize", "pesticide", "harvest", "other"}

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
