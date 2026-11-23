# 接口文档

## 0. 通用约定

- 成功：HTTP 200，`{"code":0,"message":"ok","data":{...}}`
- 失败：HTTP 4xx/5xx，`{"error":"INVALID_INPUT","message":"具体原因"}`（`error` 取值见各接口）
- 请求体上限 64KB，超过返回 413 `INVALID_INPUT`
- 动态接口默认 `Cache-Control: no-store`；`/api/crops` 为 `public, max-age=300`，`/api/site-config` 为 `public, max-age=60`
- nginx 限流 120 次/分钟（burst 30），超限返回 429 `{"code":429,"message":"请求过于频繁，请稍后再试","data":null}`
- `/health` 是探活接口；`/api/health/detail` 仅供服务器本机自检，公网固定 404

## 1. GET /api/timeline、POST /api/timeline

获取农事时间线。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| crop | string | 是 | 作物名或 ID，支持俗名/拼音/常见错别字，≤20 字 |
| sowDate | string | 否 | yyyy-MM-dd，仅支持近两年内至未来 30 天；缺省时按节气推荐播种日 |
| lat / lon | number | 否 | 纬度/经度；缺省按 35.07,116.31（华北基准）计算规程窗口，前端应尽量传真实坐标 |
| location | string | 否 | 地点名，仅用于展示 |
| areaMu | number | 否 | 地块面积（亩），夹取 0~100000 |

POST 请求体：

```json
{ "crop": "番茄", "sowDate": "2026-12-05", "location": { "latitude": 31.2, "longitude": 121.5 }, "areaMu": 2 }
```

### 响应结构

```json
{
  "code": 0, "message": "ok",
  "data": {
    "apiVersion": "string",
    "dataSource": "realtime|cache|unavailable",
    "dataTime": "string",
    "todayConclusion": { "nodeId": "1", "operationType": "sowing", "status": "green|yellow|red", "copy": "..." },
    "nodes": [{
      "nodeId": "1", "nodeSeq": 1, "daysAfterSowing": 0, "date": "2026-12-05",
      "operationType": "sowing", "name": "播种育苗", "description": "...",
      "status": "green|yellow|red|plan", "copy": "...", "reasons": [],
      "rescheduleAdvice": "...", "certainty": "high|medium|low", "weatherIcon": "...",
      "guidance": "...", "forecastNote": "...", "climateNormal": "...",
      "precision": "day|season", "outlook": "...", "suitability": "...", "weatherFactors": []
    }],
    "reminders": []
  }
}
```

`todayConclusion` 只取**当天命中**的节点；当天没有节点时取下一个未来节点，文案形如
「今天不在农事节点上；下一个节点「播种育苗」还有 12 天（2026-12-05）」；
全部节点都已过去时文案为「本季农事节点已全部过去（最后节点「成熟收割」于 2026-07-28）；
如需新一季，请调整播期后重新生成」。

`operationType` 取值：`sowing`（播种）、`irrigation`（灌溉）、`fertilize`（施肥）、
`pesticide`（打药/防治）、`harvest`（采收）、`other`（产区规程里的出苗、培土、搭架、
清园、修剪等管理类农事；天气建议走通用分支）。

`dataSource` 为 `cache` 时表示天气上游超时、用了缓存；`unavailable` 表示连缓存也没有，
本次不提供天气判断（不会编造数值）。

### 生成中：HTTP 202

作物不在 400 种已接线规程里（例如用户输入了作物库里没有的名字）时，由大模型后台生成（约 4 秒，超时约 30~40 秒），首次请求返回：

```json
{ "code": "20201", "message": "规程生成中，请稍后重试", "retryAfter": 4 }
```

前端建议按 2~4 秒轮询，总等待上限约 60~75 秒。

### 错误

| HTTP | error | 场景 |
|------|-------|------|
| 400 | INVALID_INPUT | 缺 crop、作物名过长、日期格式错、日期超出范围、JSON 非法 |
| 413 | INVALID_INPUT | 请求体过大 |
| 422 | UNSUPPORTED_CROP | 尚无规程且大模型生成失败 |
| 500 | INTERNAL_ERROR | 服务端异常 |

## 2. POST /api/recognition

拍照识别作物。

### 请求体

```json
{ "image": "base64编码的图片数据" }
```

### 响应结构

```json
{
  "needConfirm": "boolean",
  "candidates": [{ "name": "string", "confidence": "number" }],
  "fallback": "string|null"
}
```

### 错误码

| 错误码 | 名称 | HTTP状态 |
|--------|------|----------|
| 40007 | RECOGNITION_EMPTY | 400 |
| 40008 | RECOGNITION_TIMEOUT | 408 |

## 3. POST /api/chart

获取图表数据。

### 请求体

```json
{ "crop": "string", "sowDate": "string", "lat": "number", "lon": "number" }
```

### 响应结构

```json
{
  "chartType": "string",
  "data": [],
  "meta": {}
}
```

## 4. GET /health

健康检查。

### 响应结构

```json
{ "status": "ok" }
```

## 5. GET /api/geo/reverse

地图标点的逆地理编码：经纬度 → 中文地名，目标精度到小区/村。

经纬度只在后端使用与缓存，前端只展示 `displayName`。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lat | number | 是 | 纬度，保留 6 位小数（约 0.1 米） |
| lon | number | 是 | 经度，保留 6 位小数 |

### 响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "displayName": "望京SOHO（望京街道 · 朝阳区 · 北京市）",
    "name": "望京SOHO",
    "street": "望京街道",
    "district": "朝阳区",
    "city": "北京市",
    "province": "北京市",
    "level": "community",
    "source": "amap"
  }
}
```

`level` 取值：`community`（小区/村）> `building` > `poi` > `street` > `district` > `unknown`。
`unknown` 表示没配服务商 key 或上游不可用，HTTP 仍是 200，前端据此提示手动输入。

### 数据来源

服务端按 key 配置选择：腾讯位置服务（`GEO_QQ_KEY`）优先，高德 Web 服务（`GEO_AMAP_KEY`）兜底。
两者都未配置时返回 `level=unknown`。**不要在前端直连 Nominatim**：国内实测 10s 超时。

## 6. GET /api/geo/search

地图弹层的地点搜索联想。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| q | string | 是 | 关键字 |
| lat / lon | number | 否 | 搜索中心点，用于结果排序 |

### 响应结构

```json
{ "code": 0, "message": "ok", "data": { "items": [
  { "title": "望京SOHO", "address": "北京市朝阳区望京街道", "lat": 39.9963, "lon": 116.4809, "source": "amap" }
] } }
```

## 7. /api/auth/*

账号接口。会话是 HttpOnly Cookie（`agritime_token`），前端不接触令牌。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册并自动登录 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 退出（清 Cookie） |
| GET | `/api/auth/me` | 当前登录用户 |

请求体：`{"username":"...","password":"...","nickname":"..."}`（nickname 选填）

成功响应：

```json
{ "code": 0, "message": "ok", "data": { "user": {
  "id": "u_super_admin", "username": "super_admin", "nickname": "超级管理员",
  "role": "admin", "createdAt": "2026-10-29 17:48:29",
  "lastLoginAt": "2026-10-29 17:48:29", "loginCount": 1
} } }
```

错误码：

| code | 场景 |
| --- | --- |
| 400 | 用户名/密码不合规（用户名 3~20 位中英文数字下划线，密码 ≥6 位） |
| 401 | 用户名或密码不正确（不区分「用户不存在」） |
| 403 | 未开放注册（`auth.allow.register=false`） |
| 409 | 用户名已被注册 |
| 429 | 同一用户名连续 5 次密码错误，锁 60 秒 |

## 8. /api/admin/*

超管后台接口，仅 `role=admin` 可访问；未登录 401，非管理员 403。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/users` | 账号列表（注册时间倒序） |
| GET | `/api/admin/stats` | 概况：total / newToday / active7d / adminCount |

```json
{ "code": 0, "message": "ok", "data": {
  "total": 2, "newToday": 2, "active7d": 2, "adminCount": 1
} }
```

### 账号库与超管

账号存 `backend/var/users.json`（已 gitignore），密码用 PBKDF2-SHA256（12 万次迭代）存摘要。
预置超管 `super_admin` 只在账号库为空时创建，初始密码取环境变量 `AUTH_ADMIN_PASSWORD`。

## 9. GET /api/plots/environment

地块实时环境 + 农事适宜度。缓存 5 分钟（key 含坐标、地块类型与地形参数）。

### 请求参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| lat / lon | 是 | 地块坐标 |
| type | 否 | farmland / economic / greenhouse / orchard / paddy / fishpond，默认 farmland |
| angle | 否 | 地块倾角（度） |
| slope | 否 | 地块坡度（度） |
| terrace | 否 | 1 表示已修梯田 |

### 响应结构

```json
{ "code": 0, "message": "ok", "data": {
  "temperature": 21.8, "humidity": 22, "windSpeed": 9.4, "weatherCode": 3,
  "soilType": "潮土", "soilDescription": "土层深厚，肥力中等，宜种小麦玉米大蒜",
  "nearbyRivers": [], "waterQualityGrade": null, "waterQuality": null,
  "factors": ["土壤：潮土", "气温适宜（21.8°C）"],
  "farmingSuitability": {
    "score": 85, "level": "较适宜",
    "summary": "综合土壤、气候与地形，当前评定为较适宜",
    "factors": [
      { "key": "climate", "label": "气候", "score": 75, "weight": 0.38, "note": "气温 21.8℃、湿度 22%" },
      { "key": "soil",    "label": "土壤", "score": 88, "weight": 0.38, "note": "潮土：土层深厚…" },
      { "key": "terrain", "label": "地形", "score": 95, "weight": 0.13, "note": "倾角 0°、坡度 0°" },
      { "key": "type",    "label": "地块类型", "score": 95, "weight": 0.13, "note": "耕田" }
    ]
  },
  "hydrology": {
    "discharge": 12105, "unit": "m³/s", "level": "大流量平稳",
    "trend": "平稳", "changePct": 0,
    "peak": 12473, "peakDate": "2026-11-04",
    "sampleDistanceKm": 6.3,
    "forecast": [
      { "date": "2026-10-30", "discharge": 12105 },
      { "date": "2026-10-31", "discharge": 12133 }
    ],
    "source": "open-meteo.flood"
  },
  "dataSources": {
    "weather": "open-meteo.forecast", "soil": "open-meteo.soil",
    "soilType": "region-rule", "waterQuality": "river-reference",
    "hydrology": "open-meteo.flood(GloFAS)"
  }
} }
```

### 适宜度口径

环境 30% + 土壤 30% + 水质 20% + 地形 10% + 地块类型 10%，缺数据的一项**不参与**、
权重按比例重新归一（附近无监测水体时不会用默认值凑分）：

| 因子 | 分档 |
| --- | --- |
| 气候 | 气温 15~30℃ 记 95；湿度 40~75% 记 95，>85% 记 60 |
| 土壤 | 黑土 95 / 潮土 88 / 紫色土 85 / 黄壤 80 / 褐土 76 / 红壤 64 / 黄绵土 58 |
| 水质 | Ⅰ类 95 / Ⅱ类 88 / Ⅲ类 74 / Ⅳ类 55 / Ⅴ类 35；无数据则不计入 |
| 地形 | 倾角坡度取大者：≤3° 95、≤8° 85、≤15° 72、≤25° 55、>25° 38；修梯田 +8 |
| 地块类型 | 耕田 95 / 经济作物田 90 / 大棚·果园 88；鱼塘·水稻田缺水质数据时降为 55·72 |

等级：≥85 适宜、≥70 较适宜、≥55 一般、其余需注意。


### 河川径流量（hydrology）

数据来自 Open-Meteo Flood API（底层 GloFAS 全球水文模型），逐日河道流量，单位 m³/s。

**采样方式**：GloFAS 是 0.05°（约 5km）网格，对坐标极敏感——同一个荆州城区，
`30.35,112.15` 落在长江干流（12105 m³/s），`30.30,112.20` 只有 0.06 m³/s（一条小支流）。
只查单点会把支流当干流，数字差五个数量级。因此接口一次请求采样 3×3 网格（约 ±5km），
取流量最大者代表「离地块最近的主要河道」，并返回 `sampleDistanceKm` 说明采样点距离。

| 字段 | 说明 |
| --- | --- |
| discharge | 今日径流量（m³/s） |
| peak / peakDate | 未来 7 天峰值及日期 |
| changePct | 未来三天均值相对今日的变化百分比 |
| trend | 涨水 / 回落 / 平稳；流量 < 50 m³/s 时为「流量过小」，此时百分比不具参考意义 |
| level | 水量级 + 涨落：大流量（≥5000）/ 中流量（≥500）/ 小流量（≥50）/ 微流量，后缀 上涨/平稳/回落 |
| sampleDistanceKm | 采样网格点与地块的距离 |
| forecast | 未来 7 天逐日 {date, discharge} |

**边界**：全网格流量 < 1 m³/s 时返回 `null`（附近没有模型能解析的河道），不拿 0.06 硬凑一条河。
`level` 只描述流量量级与涨落，不出现「超警戒水位」这类结论——那需要水文站断面实测数据。
水文数据单独缓存 1 小时；与天气请求并发，冷启动耗时取两者较大值。

## 10. GET /api/crops（含搜索）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| q | 否 | 作物关键词。支持标准名、俗名、拼音、错别字；**不传时返回全部作物**（当前 400 种，约 150KB），前端一次拉全、本地筛选 |

不传 `q` 时每个元素含：`cropId / name / canonical / displayName / category / categoryKey / lifespan / totalDays / window / windowAction / windowPrecision / propsSource / hasProtocol / nodeCount / aliases / pinyin`；响应头 `Cache-Control: public, max-age=300`。

带 `q` 时返回 Top8（缓存 60 秒），`matchedBy` 说明命中方式：

```json
{ "code": 0, "message": "ok", "data": { "total": 1, "query": "西红柿", "standardName": "番茄",
  "crops": [{ "cropId": "C031", "name": "番茄", "displayName": "番茄",
              "matchedBy": "alias", "hasProtocol": true, "nodeCount": 9,
              "aliases": ["C031","番茄","西红柿","洋柿子","番茄果"],
              "pinyin": ["fanqie","xihongshi","tomato"] }] } }
```

`matchedBy` 取值：`exact`（标准名）/ `alias`（别名）/ `pinyin`（拼音）/
`contains`（包含）/ `fuzzy`（编辑距离，首字必须相同）。

---

## 11. GET /api/sow-window（节气推荐播种起点）

用户不填播种日期时，按「作物 + 今天」推算最近一个适宜播种的节气。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| crop | 是 | 作物名，支持俗称（内部会归一） |

```json
{ "code": "0", "message": "ok", "data": {
  "crop": "番茄", "standardName": "番茄",
  "sowDate": "2027-02-04", "solarTerm": "立春", "daysFromToday": 97,
  "source": "solar-term-season",
  "basis": "番茄适宜春茬/秋茬，取最近的立春" } }
```

`source` 三级判定，越靠前越贴合作物本身：

| 取值 | 含义 |
| --- | --- |
| `solar-term-crop` | 节气表里点名了该作物 |
| `solar-term-season` | 未点名，但作物适宜季节与节气月份匹配 |
| `nearest-term` | 都没匹配上，取最近节气兜底，提示结合当地经验 |

节气日期由太阳黄经计算，不是查表，逐年逐日都准。

---

## 12. GET /api/industry/species

渔业品类清单（草鱼 / 鲤鱼 / 大闸蟹），含适宜水温区间与放养规格。

## 13. GET /api/industry/timeline

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| species | 是 | 品类 id 或名称，如 `grass_carp` / `草鱼` |
| stockDate | 是 | 放苗日期 yyyy-MM-dd |
| lat / lon | 否 | 放养点位，默认荆州 |

放苗日 + 阶段天数 = 具体日期，叠加该点位天气判断当天适不适合操作。

**温度口径**：Open-Meteo 不提供养殖水体温度，判定用**气温近似**，
响应固定带 `"tempBasis": "air"` 与 `"tempNote"`，不冒充水温实测。

超出预报范围的远期阶段，用**去年同日天气**估算，`advice` 前缀「常年同期参考」并注明非预报。
生产日历缓存 5 分钟（一次请求要打预报 + 历史两个上游）。

---

## 14. GET /api/recommend

种植推荐：月份 + 纬度（气候带）+ 市场需求综合打分，输出 Top8。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| month | 是 | 1~12 |
| lat / lon | 否 | 地点坐标，越界返回 400 |
| location / locationSource | 否 | 地点名与来源（default/resolved） |
| plotName / plotCrops | 否 | 关联地块与已种作物，用于避重 |

响应含 `recommendations[]`（name/score/reasons）、`rejected[]`（气候带不符的作物 + why）、
`fellBack`（当地无气候匹配作物时回退全量并打标）。

## 15. GET /api/disease

病害问诊：文字描述 → 命中的病害 + 判断依据 + 用药安全。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| crop | 否 | 限定作物，如「小麦」 |
| q | 是 | 症状描述文字 |

响应含 `matches[]`（name / matchRate 匹配度 / evidence 命中词条 / safetyInterval 安全间隔期 /
safetyNote / scope / source）、`needHuman`（无匹配时转人工）。

## 16. POST /api/sow-check

茬口冲突校验：常识判定 + 同地块茬口 / 连作 / 跨地块农忙。

```json
{ "crop": "小麦", "sowDate": "2026-10-10", "lat": 34.8, "lon": 114.3,
  "plotId": "p1", "records": [ { "recordId": "r1", "plotId": "p1", "cropName": "玉米",
  "sowDate": "2026-06-01", "harvestDate": "2026-10-01" } ] }
```

响应 `verdict`（ok/warn/block）+ `items[]` + `suggestedSowDate`（最早可排期）。

## 17. GET /api/reminders

提醒中心（需登录）：天气/病害/适宜窗口/设备告警四类，分级、开关、未读。

## 18. POST /api/plots/analyze

地块评分：polygon 多边形 + 坡度/坡向/面积/墒情等因子，综合打分。

| 参数 | 说明 |
| --- | --- |
| polygon | 多边形坐标数组 |
| type | farmland / 等 |
| extended | true 时追加土壤质地/水源/交通三因子（打外部慢接口） |

响应 `score` / `level` / `factors[]`（每个带 source/method/note）/ `factorNotes`（取不到的因子说明）。

## 19. GET /api/plots/history

地块历史：某地块历年种植记录（按年倒序）+ 连作提示 + 产量趋势，支持 CSV 导出。

## 20. GET|POST /api/sensor/*

传感器设备与读数（需登录）：`devices` / `readings` / `poll`。
设备地址用户填，服务端拉取前做 SSRF 校验；阈值告警写进提醒中心（type=sensor）。
设备可配 `intervalMin` 拉取间隔，后台调度器到期自动拉，超 N 周期无数据标离线。

## 21. GET|POST /api/support/*

客服工单：`POST /ticket`（提交）/ `GET /tickets`（分页+状态筛选）/ `GET /ticket?id=`（详情）/
`POST /reply`（回复）/ `POST /status`、`POST /close`（超管）。

## 22. GET|PUT /api/me/data

多端同步（需登录）：整份业务数据读写，`updatedAt` 合并 + 冲突留痕。

## 23. GET /api/crop-status

我的作物真实状态：`crop` + `sowDate` + `lat/lon` → 当前生育阶段与建议。

## 24. GET|POST /api/pond-status

鱼塘水质状态与投喂建议：`lat/lon` + `species` + `areaMu` + 密度/均重 + 实测 pH/氨氮/溶氧/水温。

## 25. GET /api/site-config

公开站点开关（注册开关、功能开关等）。

## 26. GET /api/health/detail（仅服务器本机）

运维详情：缓存状态、规程数量、LLM 配置等。**公网不暴露**（nginx 精确匹配返回 404），
只能在服务器上 `curl http://127.0.0.1:8080/api/health/detail` 查看。

关键字段：`protocolCount`（已接线的规程数）、`llmCachedCount`（大模型规程缓存条数）、
`cropCount`（两者合计，会随运行状态变化，不要当覆盖指标）、`cacheTtlSeconds`、`upstreamTimeoutMs`、
`llmTimeoutMs`、`llmModel`、`startedAt`。

## 27. GET /api/industry/cost

养殖成本与盈亏：`species` + `areaMu` + 可选实际投喂/出塘/售价。
估算模式（默认参数）与实际模式（填实际数据）分离，输出成本六项、保本价、盈亏。

---

## REST 规范

- 资源命名使用名词复数
- HTTP 方法语义化：GET 查询、POST 创建、PUT 更新、DELETE 删除
- 成功返回统一 JSON 结构：`{ "code": 0, "message": "ok", "data": "object|array" }`
- 失败返回统一 JSON 结构：`{ "error": "INVALID_INPUT", "message": "string" }`（HTTP 4xx/5xx）
- 异步生成中返回 202：`{ "code": "20201", "message": "...", "retryAfter": 4 }`
