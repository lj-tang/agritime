# -*- coding: utf-8 -*-
"""
生成 data/geo/places-cn.json（逆地理兜底库，完全离线）

数据来源
  · GeoNames CN.txt（CC BY 4.0）：中文地名 + 坐标，只取行政驻地/有人口记录的居民点
  · china-area-data：省 / 地级市 / 区县三级中文名，用来把 GeoNames 的散点组织成行政层级

输出（字段用短名，控制体积）
  { "v":1,
    "provinces":["湖北省",...],
    "cities":[[市名, 省序号, lat, lon], ...],
    "counties":[[区县名, 所属市序号, lat, lon], ...] }

reverse 的用法：先找最近的区县 → 「省+市+区县」；区县都太远时才退到「省+市 附近」。
"""
import io, json, math, re

CJK_ONLY = re.compile(r'^[\u4e00-\u9fff·]{2,12}$')     # 纯中文，挡掉日文/维吾尔文转写
SUFFIX = ['特别行政区', '自治区', '自治州', '自治县', '自治旗', '地区', '街道',
          '省', '市', '县', '区', '镇', '乡', '盟', '旗']
SKIP = {'市辖区', '县', '省直辖县级行政区划', '自治区直辖县级行政区划', '境外'}
# 开发区/农场一类别名成区县，GeoNames 里坐标飘得厉害，直接排除
SKIP_RE = re.compile(r'(开发区|管理区|高新区|新区|工业园|产业园|示范区|旅游区|保税区|农场|林场|牧场|渔场|监狱|办事处)$')
MUNI = ('110000', '120000', '310000', '500000', '810000', '820000')


def norm(name):
    for s in SUFFIX:
        if name.endswith(s) and len(name) > len(s) + 1:
            return name[: -len(s)]
    return name


def hav(a1, o1, a2, o2):
    r = 6371.0
    p1, p2 = math.radians(a1), math.radians(a2)
    h = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(o2 - o1) / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(h))


# ---------- 1. 行政区划三级清单 ----------
area = json.load(io.open('area.json', encoding='utf-8'))
tree = []   # (省名, [(市名, [(区县名)])])
for pcode, pname in area['86'].items():
    kids = area.get(pcode) or {}
    cities = []
    if pcode in MUNI:
        # 直辖市：省级即城市，下辖的区县直接挂在市下面
        subs = []
        for ccode in kids:
            subs += [v for v in (area.get(ccode) or {}).values() if v not in SKIP]
        cities.append((pname, subs))
    else:
        for ccode, cname in kids.items():
            if cname in SKIP:
                continue
            subs = [v for v in (area.get(ccode) or {}).values() if v not in SKIP]
            cities.append((cname, subs))
    tree.append((pname, cities))

# ---------- 2. GeoNames ----------
cands = {}      # 归一化名 -> [(原名, lat, lon, pop)]
RANK = {'PPLC': 1, 'PPLA': 1, 'PPLA2': 2, 'PPLA3': 3, 'PPLA4': 4, 'PPL': 4}
with io.open('cnx/CN.txt', encoding='utf-8') as f:
    for line in f:
        fs = line.rstrip('\n').split('\t')
        if len(fs) < 15 or fs[6] != 'P':
            continue
        if fs[7] not in RANK:
            continue
        pop = int(fs[14] or 0)
        try:
            lat, lon = float(fs[4]), float(fs[5])
        except ValueError:
            continue
        names = {fs[1]} | set(fs[3].split(','))
        names = [n for n in names if CJK_ONLY.match(n)]
        if not names:
            continue
        for n in names:
            cands.setdefault(norm(n), []).append((n, lat, lon, pop))

# 同一坐标只留一条，避免重复项拖慢查找
for k, lst in cands.items():
    uniq = {}
    for rec in lst:
        key = (round(rec[1], 2), round(rec[2], 2))
        if key not in uniq or rec[3] > uniq[key][3]:
            uniq[key] = rec
    cands[k] = list(uniq.values())


def pick(name, near=None, max_km=250):
    """按名字找坐标；near 给出时优先取附近的那个（同名区县全国有几十个）。"""
    base = norm(name)
    found = cands.get(base)
    if not found:
        for n in range(len(base) - 1, 1, -1):
            found = cands.get(base[:n])
            if found:
                break
    if not found:
        return None
    if near:
        # 给了参照点就必须落在参照点附近：同名村庄全国到处都是，
        # 允许「退而求其次」会把「罗田县」匹配到重庆郊外的罗田村。
        close = [r for r in found if hav(near[0], near[1], r[1], r[2]) <= max_km]
        return max(close, key=lambda r: r[3]) if close else None
    return max(found, key=lambda r: r[3])


# ---------- 3. 逐级落坐标 ----------
provinces, cities_out, counties_out = [], [], []
for pname, cities in tree:
    for cname, subs in cities:
        c = pick(cname)
        if c is None:
            # 自治州在 GeoNames 里常常没有独立条目，用驻地区县代替
            for sub in subs:
                c = pick(sub)
                if c:
                    break
        if c is None:
            continue
        ci = len(cities_out)
        cities_out.append([cname, pname, round(c[1], 4), round(c[2], 4)])
        for sub in subs:
            if SKIP_RE.search(sub):
                continue
            s = pick(sub, near=(c[1], c[2]), max_km=160)
            if s:
                counties_out.append([sub, ci, round(s[1], 4), round(s[2], 4)])

provs = []
for _, pn, _, _ in cities_out:
    if pn not in provs:
        provs.append(pn)
pidx = {p: i for i, p in enumerate(provs)}

out = {
    'v': 1,
    'src': 'GeoNames (CC BY 4.0) + china-area-data',
    'provinces': provs,
    'cities': [[n, pidx[p], la, lo] for n, p, la, lo in cities_out],
    'counties': counties_out,
}
io.open('places-cn.json', 'w', encoding='utf-8', newline='\n').write(
    json.dumps(out, ensure_ascii=False, separators=(',', ':')))
print('市', len(cities_out), '区县', len(counties_out), '省', len(provs))
