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
  "id": "u_admin", "username": "admin", "nickname": "超级管理员",
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
预置超管 `admin` 只在账号库为空时创建，初始密码取环境变量 `AUTH_ADMIN_PASSWORD`。

## REST 规范

- 资源命名使用名词复数
- HTTP 方法语义化：GET 查询、POST 创建、PUT 更新、DELETE 删除
- 错误返回统一 JSON 结构：`{ "code": "number", "message": "string", "data": "null" }`
- 成功返回统一 JSON 结构：`{ "code": 0, "message": "SUCCESS", "data": "object|array" }`
