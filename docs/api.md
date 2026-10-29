# 接口文档

## 1. GET /api/timeline

获取农事时间线。

### 请求参数

| 参数 | 类型 | 必填 | 取值范围 | 说明 |
|------|------|------|----------|------|
| crop | string | 是 | dry_garlic/wheat_winter/corn_summer/rice/soybean | 作物ID |
| sowDate | string | 是 | YYYY-MM-DD（过去2年~未来30天） | 播种日期 |
| lat | number | 否 | -90~90 | 纬度 |
| lon | number | 否 | -180~180 | 经度 |
| location | string | 否 | - | 地点名称 |

### 响应结构

```json
{
  "apiVersion": "string",
  "dataSource": "string",
  "dataTime": "string",
  "todayConclusion": { "status": "green|yellow|red", "copy": "string" },
  "nodes": [{ "nodeSeq": "number", "daysAfterSowing": "number", "date": "string", "operationType": "string", "name": "string", "description": "string", "status": "string", "copy": "string", "reasons": [], "rescheduleAdvice": "string", "certainty": "string", "weatherIcon": "string" }],
  "reminders": []
}
```

### 错误码

| 错误码 | 名称 | HTTP状态 |
|--------|------|----------|
| 40001 | PARAM_INVALID | 400 |
| 40006 | UNSUPPORTED_CROP | 400 |
| 50001 | INTERNAL_ERROR | 500 |
| 50002 | WEATHER_UNAVAILABLE | 502 |

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

## REST 规范

- 资源命名使用名词复数
- HTTP 方法语义化：GET 查询、POST 创建、PUT 更新、DELETE 删除
- 错误返回统一 JSON 结构：`{ "code": "number", "message": "string", "data": "null" }`
- 成功返回统一 JSON 结构：`{ "code": 0, "message": "SUCCESS", "data": "object|array" }`
