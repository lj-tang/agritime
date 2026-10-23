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

## REST 规范

- 资源命名使用名词复数
- HTTP 方法语义化：GET 查询、POST 创建、PUT 更新、DELETE 删除
- 错误返回统一 JSON 结构：`{ "code": "number", "message": "string", "data": "null" }`
- 成功返回统一 JSON 结构：`{ "code": 0, "message": "SUCCESS", "data": "object|array" }`
