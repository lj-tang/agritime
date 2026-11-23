#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补全 data/crop-base/alias.json 的别名与拼音。

背景：CropRegistry 的搜索依赖 alias.json 的 aliases/pinyin 字段。
现状只有 78/400 个作物有拼音，且 31 个「产区×作物」的产区名（如「山东金乡大蒜」）
不在别名表里——用户按产区名搜索时匹配不到对应作物，因为 stripOrigin 只去掉省份前缀，
「金乡大蒜」仍然不是任何作物的别名。

本脚本做两件事：
  1) 把 31 个产区名作为别名加到对应 canonical 作物的 aliases 里（结果幂等，重复运行不会重复追加）；
  2) 如果环境里有 pypinyin，就为没有拼音的作物补齐标准名/别名的拼音（去空格、小写）；
     没有 pypinyin 时跳过拼音补全并打印剩余数量（拼音只影响拼音搜索，不影响中文搜索）。

用法：
  python deploy/tools/enrich-alias.py            # 写回 alias.json
  python deploy/tools/enrich-alias.py --dry-run  # 只统计，不写文件
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ALIAS_PATH = ROOT / "data" / "crop-base" / "alias.json"

# 产区名 → canonical displayName（与 gen-protocols-from-initsql.py 的 REGION_DISPLAY 保持一致；
# 党参在 alias.json 里没有 canonical，跳过）
REGION_ALIAS = {
    "大蒜": ["山东金乡大蒜", "金乡大蒜"],
    "小麦": ["河南新乡小麦", "新乡小麦"],
    "玉米": ["黑龙江绥化玉米", "绥化玉米"],
    "水稻": ["湖北荆州水稻", "荆州水稻"],
    "番茄": ["山东寿光番茄", "寿光番茄"],
    "大豆": ["黑龙江绥化大豆", "绥化大豆"],
    "大白菜": ["河北张家口大白菜", "张家口大白菜"],
    "黄瓜": ["山东寿光黄瓜", "寿光黄瓜"],
    "辣椒": ["广东湛江辣椒", "湛江辣椒"],
    "棉花": ["新疆石河子棉花", "石河子棉花"],
    "烟草": ["云南曲靖烤烟", "曲靖烤烟"],
    "甘蔗": ["广西崇左甘蔗", "崇左甘蔗"],
    "花生": ["河南南阳花生", "南阳花生"],
    "油菜": ["湖北天门油菜", "天门油菜"],
    "苹果": ["陕西洛川苹果", "洛川苹果"],
    "大樱桃": ["山东烟台大樱桃", "烟台大樱桃"],
    "杨梅": ["浙江仙居杨梅", "仙居杨梅"],
    "柚子": ["福建平和蜜柚", "平和蜜柚"],
    "马铃薯": ["内蒙古赤峰马铃薯", "赤峰马铃薯"],
    "谷子": ["河北石家庄谷子", "石家庄谷子"],
    "莴笋": ["四川成都莴笋", "成都莴笋"],
    "苜蓿": ["内蒙古呼伦贝尔苜蓿", "呼伦贝尔苜蓿"],
    "茶": ["福建安溪铁观音", "安溪铁观音", "云南普洱大叶种茶", "普洱大叶种茶", "四川宜宾早春茶", "宜宾早春茶"],
    "人参": ["吉林长白山人参", "长白山人参"],
    "燕麦": ["黑龙江齐齐哈尔燕麦草", "齐齐哈尔燕麦草"],
    "芝麻": ["河南驻马店芝麻", "驻马店芝麻"],
    "当归": ["甘肃岷县当归", "岷县当归"],
    "甜菜": ["新疆阿勒泰饲用甜菜", "阿勒泰饲用甜菜"],
}


def main():
    dry_run = "--dry-run" in sys.argv
    root = json.loads(ALIAS_PATH.read_text(encoding="utf-8"))
    mappings = root["mappings"]
    by_display = {m["displayName"]: m for m in mappings}

    added = 0
    for display, names in REGION_ALIAS.items():
        m = by_display.get(display)
        if m is None:
            raise SystemExit(f"alias.json 里没有作物：{display}")
        aliases = m.setdefault("aliases", [])
        for name in names:
            if name not in aliases:
                aliases.append(name)
                added += 1

    pinyin_added = 0
    pinyin_remaining = 0
    try:
        import pypinyin  # type: ignore
    except ImportError:
        pypinyin = None
        pinyin_remaining = sum(1 for m in mappings if not m.get("pinyin"))
    if pypinyin is not None:
        for m in mappings:
            words = [m["displayName"]] + [a for a in m.get("aliases", []) if len(a) >= 2]
            pinyins = list(m.get("pinyin") or [])
            changed = False
            for w in words:
                py = "".join(pypinyin.lazy_pinyin(w, errors="ignore")).lower()
                if py and py not in pinyins:
                    pinyins.append(py)
                    changed = True
            if changed:
                m["pinyin"] = pinyins
                pinyin_added += 1
        pinyin_remaining = sum(1 for m in mappings if not m.get("pinyin"))

    total_aliases = sum(len(m.get("aliases", [])) for m in mappings)
    print(f"作物 {len(mappings)} 个，别名合计 {total_aliases} 条，本次新增产区别名 {added} 条")
    if pypinyin is None:
        print(f"未安装 pypinyin，拼音补全跳过（仍有 {pinyin_remaining} 个作物无拼音；仅影响拼音搜索）")
        print("  需要时：pip install pypinyin && python deploy/tools/enrich-alias.py")
    else:
        print(f"拼音补全 {pinyin_added} 个，仍有 {pinyin_remaining} 个无拼音")
    if dry_run:
        print("dry-run：未写文件")
        return
    ALIAS_PATH.write_text(json.dumps(root, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已写入 {ALIAS_PATH}")


if __name__ == "__main__":
    main()
