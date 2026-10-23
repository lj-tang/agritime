# 第二阶段 · 服务器运维与安全分析报告（交付物）

> 文档性质：**分析报告 + 建议修改项清单**。本次**不落地任何服务器配置变更**。
> 范围：`/opt/crop-calendar`（后端 8080）、`/agritime/frontend`（前端 80）、nginx、systemd、日志。
> 安全红线：本报告**不回显任何真实密钥/口令/内网凭据**，仅描述风险与整改方向。
> 采集时间：2026-10-17 · 目标主机：115.175.44.27（华为云 FlexusL）

---

## 一、现状快照

| 维度 | 现状 | 采集证据 |
| --- | --- | --- |
| 前端静态服务 | nginx 80，root 指向 `/agritime/frontend/pages` | `curl -I http://localhost/` → 200 |
| 后端服务 | Java 进程监听 `*:8080`（pid 363738） | `ss -tlnp` |
| 后端注册方式 | **无 systemd unit**（未匹配到 crop/zhinong/agri/java 服务） | `systemctl list-units --type=service` 无匹配 |
| 反向代理 | nginx `location /api/` → `http://localhost:8080` | `/etc/nginx/sites-available/agritime` |
| 监听端口 | 80（nginx）、8080（java）、22（sshd） | `ss -tlnp` |
| nginx 版本 | **对外暴露** `Server: nginx/1.24.0 (Ubuntu)` | 响应头 `Server` |
| 后端配置 | `application.properties`，权限 `644`，含明文密钥字段 | `ls -la` / `grep -c` |
| 关键超时 | `upstream.timeout.ms=5000`、`llm.timeout.ms=60000` | 配置项核对 |
| 公网入口 | `http://115.175.44.27/`（仅 HTTP，无 TLS） | 部署说明 |

---

## 二、运维分析（REQ-041 ~ REQ-046）

### REQ-041 后端服务运行形态与 systemd 注册现状
- **现状**：后端以裸 `java` 进程运行（pid 363738），**未注册 systemd**。当前进程存活依赖手动启动，服务器重启后不会自启。
- **风险**：进程崩溃或主机重启后服务不可达，且无统一启停/日志入口。
- **建议**：新增 systemd unit（示例，见 REQ-042），`ExecStart` 指向后端启动脚本，`WorkingDirectory=/opt/crop-calendar`。

### REQ-042 开机自启与崩溃自愈建议
- **建议 unit 关键字段**：
  - `[Unit] After=network.target`
  - `[Service] Restart=on-failure`、`RestartSec=5`、`StartLimitBurst=5`
  - `[Install] WantedBy=multi-user.target`
- **说明**：`Restart=on-failure` 可在进程异常退出后自动拉起，避免"服务悄悄挂掉无人知"。

### REQ-043 日志轮转策略
- **现状**：系统存在 logrotate.d，但**未发现后端专属轮转配置**；若后端日志直写文件，长期运行可能撑爆磁盘。
- **建议**：
  - 若后端写文件日志：新增 `/etc/logrotate.d/crop-calendar`，`size 10M`、`rotate 7`、`compress`、`copytruncate`。
  - 若以 systemd 托管：改用 journald，配置 `SystemMaxUse` 限制总量。
- **验收方向**：单文件 >10MB 自动轮转，保留近期 N 份。

### REQ-044 健康检查对象与告警条件
- **检查对象**：
  1. `GET /health`（后端存活）
  2. TCP `8080`（后端端口可达）
  3. TCP `80` + `GET /`（nginx + 前端）
  4. `GET /api/health/detail`（若 A8 落地：含 `upstreamReachable`）
- **建议告警条件**：连续 3 次探测失败、或 `/health` 5xx、或响应超时 > 5s 触发告警。

### REQ-045 上游依赖失效表现与检测建议
- **上游**：Open-Meteo（天气）、盘古大模型（规程生成）、识别服务。
- **失效表现**：天气源不可达 → 后端降级返回缓存/`unavailable`；大模型超时 → 首次新作物耗时骤增。
- **建议**：健康检查增加上游连通性探测（DNS + TCP + 轻量请求），结果暴露在 `/api/health/detail`；前端 `#data-meta` 已能展示数据来源与降级标识，可复用作为可视信号。

### REQ-046 时间线接口耗时风险（**高危**）
- **现象**：后端 `GET /api/timeline` 首次新作物实测约 61s（`llm.timeout.ms=60000` 触顶），而前端硬超时为 **20s**（`TIMELINE_TIMEOUT_MS`）。
- **后果**：首次请求前端必先超时 → 降级到 mock 数据 → 用户看到"本地兜底数据"而非真实结果。
- **方向性建议**（不属本次前端改动范围）：
  1. 后端规程**预热**（计划书 A2：启动预生成 top10 作物）与 chart LLM 缓存（A5）；
  2. 前端可评估**分层超时**或对首次请求给出"生成中"的更长等待提示（需后端配合返回进度信号）；
  3. 天气请求并行化（A1）降低非 LLM 部分的耗时。
- **注意**：本次前端实现遵循既定 20s 超时与降级策略，未改阈值。

---

## 三、安全分析（REQ-047 ~ REQ-053）

### REQ-047 凭据明文泄露风险（**高危**）
- **现状**：`/opt/crop-calendar/application.properties` 中存在明文大模型 API Key 字段，且文件权限为 `644`（同机其他用户可读）。
- **风险**：密钥泄露可导致调用配额盗用、费用损失；配置若随代码/备份外泄风险更高。
- **整改建议**：
  1. 密钥改由**环境变量**注入（配置优先级：环境变量 > 文件），文件中仅留占位；
  2. 收紧权限至 `600`、属主为服务运行用户；
  3. **轮换**已暴露的密钥；
  4. 禁止将含密钥配置提交仓库（加入 `.gitignore` / 改用 `.example` 模板）。
- **红线**：本报告及任何前端产物**不得回显真实密钥**。

### REQ-048 nginx 配置安全项
- **现状**：响应头暴露 `nginx/1.24.0 (Ubuntu)`。
- **整改建议**：
  - `server_tokens off;`（隐藏版本号）；
  - 限制非必要 HTTP 方法（仅 GET/POST）；
  - 限制请求体大小（`client_max_body_size`，结合识别图片上限，如 5m）；
  - 增补安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy`、必要时 `Content-Security-Policy`；
  - 禁止 `/` 目录列表。

### REQ-049 目录权限最小化
- **现状**：`/agritime`、`/opt/crop-calendar` 均 `755 root:root`；前端资源对所有用户可读（静态资源可接受），但后端配置需收紧。
- **整改建议**：后端配置/密钥文件 `600`；服务以**非 root** 专用用户运行；前端目录保持只读权限。

### REQ-050 敏感信息暴露面
- **建议检查**：错误堆栈是否回显给前端（改为统一错误码 + 服务端日志）、错误页是否泄露路径、是否存在可枚举的备份文件（`*.bak`/`*.swp`/`*.git`）、是否开启了目录索引。
- **现状**：nginx 未开启 autoindex（默认关闭），需保持。

### REQ-051 CORS 收紧
- **建议**：后端若设置了 `Access-Control-Allow-Origin: *`，应改为**白名单**（仅本站域名/IP）；同域部署下（nginx 反代 `/api`）CORS 实际非必需，可关闭跨域头，减小攻击面。

### REQ-052 输入校验清单
- **前端（本次已落实）**：
  - `crop` / `sowDate` / `location` 经 `URLSearchParams` 编码后拼入 URL；
  - 日期范围校验（不晚于今天、不早于两年前）；用户可控文本统一以 `textContent` 渲染，避免 XSS；
  - 识别图片类型限制 `accept="image/*"`。
- **后端（建议）**：
  - 服务端二次校验 `crop`（长度/字符）、`sowDate`（格式 + 合理范围）、`lat`/`lon`（数值范围）；
  - 识别接口校验 base64 图片大小/类型，拒绝超大请求；
  - 统一错误响应，不泄露内部细节。

### REQ-053 传输安全（HTTP → HTTPS）
- **现状**：仅 80 端口 HTTP，无 TLS；播种位置等数据明文传输。
- **整改建议**：申请 TLS 证书（如 ACME/Let's Encrypt 或云证书），配置 443 + `301` 从 80 跳转；启用 HTTP/2、HSTS。

---

## 四、建议修改项清单（供后续排期，本次不执行）

| 编号 | 类型 | 建议 | 优先级 | 是否本次落地 |
| --- | --- | --- | --- | --- |
| OPS-1 | 运维 | 注册 systemd + `Restart=on-failure` 自愈 | 高 | 否（仅建议） |
| OPS-2 | 运维 | 日志轮转（logrotate/journald） | 中 | 否 |
| OPS-3 | 运维 | 健康检查 + 上游探测 + 告警 | 中 | 否 |
| OPS-4 | 运维 | 后端规程预热（解 61s 风险，对应计划书 A2） | 高 | 否 |
| SEC-1 | 安全 | 密钥改环境变量注入 + 权限 600 + 轮换 | 高 | 否 |
| SEC-2 | 安全 | nginx `server_tokens off` + 安全响应头 + 限方法/请求体 | 高 | 否 |
| SEC-3 | 安全 | 目录权限最小化 + 非 root 运行 | 中 | 否 |
| SEC-4 | 安全 | CORS 白名单收紧 | 中 | 否 |
| SEC-5 | 安全 | 后端输入二次校验 | 中 | 否 |
| SEC-6 | 安全 | 启用 HTTPS（443 + 跳转 + HSTS） | 高 | 否 |

---

## 五、交付边界声明

1. 本报告为**分析交付物**，不构成对服务器、nginx、systemd、防火墙、证书的任何实际变更。
2. 报告中**不含任何真实密钥、口令或凭据**。
3. 前端侧对应改动已按 `spec.md` 第 7 章约束完成（输入编码、`textContent` 渲染、超时降级、来源标识），未越界修改后端与服务器配置。