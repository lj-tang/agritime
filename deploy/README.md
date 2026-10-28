# 部署与运维

> 线上地址：<http://115.175.44.27/>（华为云 ECS，Ubuntu 24.04）
> 本文替代旧版说明——旧版写的 Node.js `orchestration/` + FunctionGraph 已不再使用，实际是 **Java + nginx + systemd**。

## 一、线上架构

```
浏览器
  │  :80
  ▼
 nginx  ── /            → /agritime/frontend/pages   （静态页，直接读文件，无构建）
         ── /assets/    → /agritime/frontend/assets
         ── /components/→ /agritime/frontend/components
         ── /api/       → 127.0.0.1:8080           （限流 30 次/分钟，超限 429）
                          │
                          ▼
                    crop-calendar.service（systemd 托管 Java，Restart=always）
                    JDK 自带 HttpServer，零依赖，-cp build/classes
```

| 组件 | 位置 |
|---|---|
| 站点根目录（= git 工作区） | `/agritime` |
| nginx 配置 | `/etc/nginx/sites-enabled/agritime` |
| systemd 单元 | `/etc/systemd/system/crop-calendar.service`（仓库内有同源副本 `backend/deploy/crop-calendar.service`） |
| 敏感配置 | `/etc/agritime.env`（chmod 600，不入库，由 unit 的 `EnvironmentFile` 加载） |
| 应用日志 | `/var/log/crop-calendar/app.log`、`app.err.log`（已配 logrotate） |
| 访问日志 | `/var/log/nginx/access.log` |

## 二、首次接管（只做一次）

服务器上的 `/agritime` 原本没有版本控制，用这个脚本把它变成 git 工作区：

```bash
bash /agritime/deploy/init-server.sh
```

脚本会先把整个目录打包到 `/root/agritime-before-git-<时间戳>.tar.gz`，再 init / 拉取 / 切分支，最后报告工作区与仓库的差异。

## 三、日常部署

```bash
/agritime/deploy/deploy.sh
```

四步：`git fetch` → `javac` 编译 → 切换字节码并重启 → 健康检查。
**编译失败不会重启**（旧版本继续跑）；**健康检查失败自动回滚**到上一个提交。

可选环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `BRANCH` | `main` | 部署分支 |
| `SKIP_PULL` | `0` | 设 1 则只重编译不拉取，用于只调配置 |
| `SERVICE` | `crop-calendar.service` | systemd 单元名 |

> 服务器是纯部署端，`deploy.sh` 会 `git reset --hard origin/<分支>`，**不要在服务器上直接改代码**，改了下次部署就没了。改代码请在本地开发 → 推送 → 服务器执行 deploy.sh。

## 四、敏感配置

`/etc/agritime.env` 内容（不要提交到仓库）：

```bash
LLM_API_URL=https://api.modelarts-maas.com/v2/chat/completions
LLM_API_KEY=<到华为云 MaaS 控制台申请>
LLM_MODEL=openPangu-2.0-flash
```

`AppConfig` 的取值优先级：**环境变量 > application.properties > 内置默认值**，
变量名规则是「配置项转大写、点换下划线」，即 `llm.api.key` → `LLM_API_KEY`。
仓库里的 `backend/application.properties` 该行故意留空。

## 五、HTTPS（当前未启用）

结论先说：**没有备案域名，就上不了可信 HTTPS**。

实测过程：用 `115-175-44-27.nip.io`（免费解析到本机的域名）走 Let's Encrypt 签发，
Let's Encrypt 从境外回访 `http://域名/.well-known/...` 时被重置；
在本机带域名 Host 头访问也返回 **403** —— 这是华为云对未备案域名的拦截。
证书签不下来，且就算签下来，域名访问也过不去。

备案域名就位后，一条命令即可启用（脚本已备好，会自动校验备案/解析再签发）：

```bash
DOMAIN=agritime.example.com bash /agritime/deploy/enable-https.sh
```

脚本会在现有 server 块上直接追加 443 监听（不复制一份配置，避免两处漂移），
自动改 ufw、重载、验证，并依赖 certbot 的 systemd timer 自动续期。
**别忘了同时去华为云控制台安全组放行 443。**

## 六、缓存与压缩

| 资源 | 策略 | 说明 |
|---|---|---|
| `.html` | `Cache-Control: no-cache` | 页面始终回源校验，改完刷新就能看到 |
| `/assets/`、`/components/` | `max-age=300` | 静态资源缓存 5 分钟，部署后最多 5 分钟内生效 |
| gzip | 开启（`gzip_comp_level 5`） | js/css/json/svg 都压，实测 `crop-map.js` 21KB → 6.4KB |

> 改缓存策略时注意：**不要用 `add_header Cache-Control`**，location 里一旦出现
> `add_header`，nginx 就不再继承 server 级的 `add_header`，安全响应头会静默失效。
> 用 `expires` 指令就没这个问题。

## 七、常见排查

```bash
systemctl status crop-calendar.service          # 服务状态
journalctl -u crop-calendar.service -n 50       # 启动/重启日志
tail -f /var/log/crop-calendar/app.log          # 应用输出
tail -f /var/log/nginx/access.log               # 访问日志
curl -s http://127.0.0.1:8080/api/health/detail # 后端自检
nginx -t && systemctl reload nginx              # 改完 nginx 配置
```

### 拉取代码报 GnuTLS / TLS 连接中断

国内 ECS 访问 GitHub 时常见，`deploy.sh` 已内置三次重试。如果仍然频繁失败，
在服务器仓库里放宽低速中断判定（已在本机设置过一次）：

```bash
cd /agritime
git config http.lowSpeedLimit 0
git config http.lowSpeedTime 999
```

### nginx 配置备份不要放在 sites-enabled/

该目录下**所有文件**都会被 nginx 加载，放一个 `agritime.bak` 就等于多了一个
`server_name _` 的 server 块，会报 `conflicting server name "_"`，
而且两个块谁生效取决于加载顺序。备份请放 `/root/backups/`。

## 八、历史遗留（待清理）

- `/root/keep-agritime.sh`：早期没有 systemd 时写的保活脚本，现已失效，且逻辑有误（探 80 端口拉起 python 静态服务会与 nginx 抢端口）。
- `deploy/frontend_server.py`：同一时期的应急静态服务，与 nginx 重复，保留仅作查阅。
