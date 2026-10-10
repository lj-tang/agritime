# 知农时 · 部署与公网验证

## 一、本地启动（W1 起即可演示）

```bash
cd orchestration
npm install
npm start
```

- 接口：`POST http://localhost:3000/api/timeline`、`POST http://localhost:3000/api/recognition`
- 页面：用静态服务器托管 `frontend/`，例如 `npx serve frontend`，打开 `pages/index.html`。
- 断网演示：`frontend/assets/mock/mock-data.js` 提供离线时间线，页面请求失败会自动回落。

## 二、环境变量

复制 `deploy/env.example.json` 为实际配置并注入运行环境（模板不含任何真实密钥）：

| 变量 | 说明 | 默认 |
|---|---|---|
| `EXTERNAL_TIMEOUT_MS` | 外部调用超时（毫秒） | 800 |
| `WEATHER_BASE_URL` | 天气服务地址 | Open-Meteo |
| `RECOGNITION_BASE_URL` | 识别服务地址（留空则用本地演示候选） | 空 |
| `CACHE_TTL_SECONDS` | 缓存有效期（秒） | 1800 |
| `API_VERSION` | 接口版本 | v1 |
| `PORT` | 本地监听端口 | 3000 |

## 三、华为云 FunctionGraph 部署

1. 依据 `deploy/functiongraph.yaml` 创建函数，运行时 `Node.js 18`，入口 `orchestration/server.js`。
2. 在函数配置中注入上表环境变量（真实密钥走环境变量，不入库）。
3. 绑定 APIG 触发器，路由 `/api/timeline`、`/api/recognition`（POST）。
4. 静态页面可托管到 OBS/FunctionGraph 静态托管，入口 `frontend/pages/index.html`。
5. 若 FunctionGraph 部署受阻，按 W3 保底：ECS 或本地端口 + 内网穿透，优先保证演示环境可用。

## 四、公网验证方法

```bash
curl -X POST https://<公网地址>/api/timeline \
  -H "Content-Type: application/json" \
  -d '{"crop":"山东金乡大蒜","sowingDate":"2025-03-15","location":{"latitude":35.07,"longitude":116.31,"county":"山东省济宁市金乡县"}}'
```

- 期望：返回 `apiVersion/dataSource/dataTime/todayConclusion/nodes/reminders`。
- 天气不可用时：`dataSource=unavailable`，响应仍返回节点，不报错、不空白。
- 公网打开 `pages/index.html`，三字段 → 生成时间线，375px 视口无横向滚动。

## 五、安全

- 仓库与前端资源中不含任何真实密钥；`.env`、令牌一律通过部署环境注入。