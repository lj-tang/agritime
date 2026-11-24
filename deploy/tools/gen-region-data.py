#!/usr/bin/env python3
"""生成前端地区选择器数据 frontend/assets/js/region-data.json。

来源：data/geo/places-cn.json（GeoNames CC BY 4.0 + china-area-data）。
只保留省份名与城市（省, lat, lon），不含区县，控制体积。
用法：python deploy/tools/gen-region-data.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "data", "geo", "places-cn.json")
DST = os.path.join(ROOT, "frontend", "assets", "js", "region-data.json")


def main():
    with open(SRC, encoding="utf-8") as f:
        d = json.load(f)
    provinces = d.get("provinces", [])
    cities = d.get("cities", [])
    by_prov = {}
    for row in cities:
        if not (isinstance(row, list) and len(row) >= 4):
            continue
        name, idx, lat, lon = row[0], row[1], row[2], row[3]
        try:
            prov = provinces[int(idx)]
            lat_f, lon_f = float(lat), float(lon)
        except (TypeError, ValueError, IndexError):
            continue
        if not (-90 <= lat_f <= 90 and -180 <= lon_f <= 180):
            continue
        by_prov.setdefault(prov, []).append({"n": name, "lat": round(lat_f, 4), "lon": round(lon_f, 4)})
    out = {
        "v": 1,
        "src": "generated from data/geo/places-cn.json by deploy/tools/gen-region-data.py",
        "provinces": [{"n": p, "cities": by_prov.get(p, [])} for p in provinces],
    }
    tmp = DST + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, DST)
    total_cities = sum(len(p["cities"]) for p in out["provinces"])
    print("provinces=%d cities=%d -> %s" % (len(out["provinces"]), total_cities, DST))


if __name__ == "__main__":
    main()
