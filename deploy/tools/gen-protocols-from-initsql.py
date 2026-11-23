#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 data/init.sql（「后端乙 计算与规则层」种子数据）转成 backend/crop-protocols.json 同构格式。

背景：data/init.sql 里有 crop_procedure 表与 400 作物 × 8 节点（C001~C400）的种子数据，
但这份 SQL 从未导入数据库，也从未被任何运行时代码读取；线上只有 13 个手工规程。
本脚本把 3200 行转换成 ComputeStub.readProtocols() 能直接读取的格式，
与现有 13 个手工规程合并（手工规程内容保持不变，避免覆盖已复核的内容）。

规则：
  · opType 以 job_name 语义为准（原始数据的 job_type 与 job_name 大面积错位：3200 行里
    705 行与名称推断不一致，例如「萌芽前施肥」被标成 SPRAY、「花前打药」被标成 FERTILIZE，
    直接采用 job_type 会让「施肥节点」被判成打药，进而让 GuidanceService 给出错误的天气建议；
    data/init.sql 已由 fix-initsql-jobtype.py 修正，本脚本仍保留推断逻辑以防数据回退）：
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
    以 standardName 判定，源文件是 deploy/data/manual-protocols.json（从 backend/crop-protocols.json
    的 13 个手工规程原样抽出，保证重复生成时不被已合并的 400 规程冒充），保留原手工规程不替换；
  · 另一份长期未被代码读取的数据 data/crop-base/crop-schedule.json（31 个「产区×作物」×
    12~13 节点）按产区名映射到 canonical 作物后，用它替换对应作物的 SQL 节点（手工 13 种
    仍不替换）；operationType 归一：sowing→sowing、emergence→other、fertilizer→fertilize、
    irrigation→irrigation、pesticide→pesticide、harvest→harvest、other→other，并把产区名
    记入 regions 字段。党参等 alias.json 里没有 canonical 的作物跳过并打印清单。

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
SCHEDULE_PATH = ROOT / "data" / "crop-base" / "crop-schedule.json"
MANUAL_PATH = ROOT / "deploy" / "data" / "manual-protocols.json"
PROTOCOL_PATH = ROOT / "backend" / "crop-protocols.json"
OUT_PATH = PROTOCOL_PATH

# crop-schedule.json 的 operationType → 产品 opType
SCHEDULE_OP_MAP = {
    "sowing": "sowing",
    "emergence": "other",
    "fertilizer": "fertilize",
    "irrigation": "irrigation",
    "pesticide": "pesticide",
    "harvest": "harvest",
    "other": "other",
}

# 产区示范规程的 crop 名 → alias.json 里的 canonical displayName（须存在，否则跳过并打印）
REGION_DISPLAY = {
    "山东金乡大蒜": "大蒜",
    "河南新乡小麦": "小麦",
    "黑龙江绥化玉米": "玉米",
    "湖北荆州水稻": "水稻",
    "山东寿光番茄": "番茄",
    "黑龙江绥化大豆": "大豆",
    "河北张家口大白菜": "大白菜",
    "山东寿光黄瓜": "黄瓜",
    "广东湛江辣椒": "辣椒",
    "新疆石河子棉花": "棉花",
    "云南曲靖烤烟": "烟草",
    "广西崇左甘蔗": "甘蔗",
    "河南南阳花生": "花生",
    "湖北天门油菜": "油菜",
    "陕西洛川苹果": "苹果",
    "山东烟台大樱桃": "大樱桃",
    "浙江仙居杨梅": "杨梅",
    "福建平和蜜柚": "柚子",
    "内蒙古赤峰马铃薯": "马铃薯",
    "河北石家庄谷子": "谷子",
    "四川成都莴笋": "莴笋",
    "内蒙古呼伦贝尔苜蓿": "苜蓿",
    "甘肃定西党参": "党参",  # alias.json 无党参，运行时会打印为未映射
    "福建安溪铁观音": "茶",
    "云南普洱大叶种茶": "茶",
    "吉林长白山人参": "人参",
    "黑龙江齐齐哈尔燕麦草": "燕麦",
    "河南驻马店芝麻": "芝麻",
    "四川宜宾早春茶": "茶",
    "甘肃岷县当归": "当归",
    "新疆阿勒泰饲用甜菜": "甜菜",
}

sys.path.insert(0, str(Path(__file__).resolve().parent))
from protocol_rules import ALLOWED_OPS, NAME_OP_RULES, OP_MAP, infer_op  # noqa: E402

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

    manual = json.loads(MANUAL_PATH.read_text(encoding="utf-8"))
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

    # ---- 合并 crop-schedule.json 的产区示范规程（非手工作物用产区节点替换 SQL 节点）----
    cid_by_display = {disp: cid for cid, disp in display_by_canonical.items()}
    schedule = json.loads(SCHEDULE_PATH.read_text(encoding="utf-8"))
    region_rows = OrderedDict()
    for r in schedule:
        region_rows.setdefault(r["crop"], []).append(r)

    schedule_targets = OrderedDict()
    unmapped_regions = []
    for region, rows in region_rows.items():
        display = REGION_DISPLAY.get(region)
        cid = cid_by_display.get(display) if display else None
        if cid is None:
            unmapped_regions.append(region)
            continue
        tgt = schedule_targets.get(cid)
        if tgt is None:
            schedule_targets[cid] = {"display": display, "regions": [], "rows": rows}
        schedule_targets[cid]["regions"].append(region)

    by_id = {c["cropId"]: c for c in manual_crops + generated}
    merged_names = []
    schedule_kept_manual = []
    for cid, tgt in schedule_targets.items():
        if tgt["display"] in manual_std:
            schedule_kept_manual.append(tgt["display"])
            continue
        entry = by_id.get(cid)
        if entry is None:
            raise SystemExit(f"crop-schedule 作物 {tgt['display']} 在规程里找不到 {cid}")
        nodes = []
        for r in sorted(tgt["rows"], key=lambda x: int(x["daysAfterSowing"])):
            op = SCHEDULE_OP_MAP.get(r["operationType"])
            if op is None:
                raise SystemExit(f"{tgt['regions'][0]} 出现未知 operationType：{r['operationType']}")
            title = str(r["name"]).strip()
            if not title:
                raise SystemExit(f"{tgt['regions'][0]} 第 {r['nodeSeq']} 个节点缺 name")
            desc = str(r.get("description") or "").strip() or f"{title}（产区示范规程，原数据未提供细节说明）"
            nodes.append({
                "opType": op,
                "dayOffset": int(r["daysAfterSowing"]),
                "title": title,
                "desc": desc,
            })
        last = -1
        for n in nodes:
            if n["dayOffset"] <= last:
                n["dayOffset"] = last + 1
            last = n["dayOffset"]
        entry["nodes"] = nodes
        entry["source"] = (
            "data/crop-base/crop-schedule.json 产区示范规程（" + "、".join(tgt["regions"]) + "）"
        )
        entry["regions"] = tgt["regions"]
        entry["reviewStatus"] = "pending_review"
        merged_names.append(tgt["display"])

    out = {
        "version": "0.5.0",
        "comment": (
            "作物栽培规程表（契约①的数据载体）。opType 只能是 sowing/irrigation/fertilize/pesticide/harvest/other，"
            "dayOffset 相对播种日递增（0 = 播种当天），reviewStatus=pending_review 的内容须经农技复核后才算事实（铁律 8）。"
            " ｜ V0.5.0：data/init.sql 的 crop_procedure 种子数据（400 作物 × 8 节点）全量导入，与原有 13 个人工规程合并；"
            "并用 data/crop-base/crop-schedule.json 的产区示范规程替换非手工作物的节点（产区名记入 regions，emergence/other 归入 other）；"
            "原表无播种节点，导入时统一补 dayOffset=0 的 sowing 节点；原表 job_type 与 job_name 曾大面积错位（705 行），opType 以 job_name 语义为准。"
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
    print(f"crop-schedule 产区规程合并 {len(merged_names)} 个作物：{'、'.join(merged_names)}")
    if schedule_kept_manual:
        print(f"  产区规程与手工规程重复、保留手工：{'、'.join(schedule_kept_manual)}")
    if unmapped_regions:
        print(f"  未映射（alias.json 无对应 canonical，跳过）：{'、'.join(unmapped_regions)}")
    if dry_run:
        print("dry-run：未写文件")
        return
    OUT_PATH.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已写入 {OUT_PATH}")


if __name__ == "__main__":
    main()
