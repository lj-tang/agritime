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

> 服务器是纯部署端，`deploy.sh` 在**远端领先时**会 `git reset --hard origin/<分支>`，
> **不要在服务器上直接改代码**，改了下次部署就没了。改代码请在本地开发 → 推送 → 服务器执行 deploy.sh。
>
> V5 起加了三种保护，避免"推送失败把新代码冲掉"这类事故：
> | 本地 HEAD vs origin | 脚本行为 |
> | --- | --- |
> | 一致 | 只重编译 |
> | 落后（远端有新提交） | `reset --hard` 更新到远端 |
> | **领先（有提交没推上去）** | **警告并跳过 reset，按当前工作区部署**（这台机器到 GitHub 经常断，2026-10-30 就出现过服务器比 GitHub 新 6 个提交） |
> | 分叉 | 直接失败退出，不做任何自动处理，避免静默丢一边 |


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

## 九、运维加固记录（2026-11-23）

| 项 | 变更 |
|---|---|
| 备份 | `backup.sh` 增加 `backend/var/`（账号库、用户数据、审计日志）与 MariaDB `agritime` 全量导出（`*.mysql.gz`）；导出失败或为空会报错退出，不会覆盖旧备份。备份仍只存本机 `/root/backups`，后续建议再同步一份到华为云 OBS |
| 超管密码 | 已重置（原密码不明，后台曾 3 周无人登录）；`super_admin` 首次登录会强制改密；旧账号库备份为 `backend/var/users.json.bak-20261123-085148` |
| 敏感配置 | `/etc/agritime.env` 已改为 600 |
| 服务账号 | Java 服务以专用账号 `agritime` 运行（不再 root）；首次搭建见 `backend/deploy/crop-calendar.service` 头部注释 |
| 监听地址 | 后端只绑 `127.0.0.1:8080`（`server.bind.address`），公网流量只走 nginx |
| nginx 反代 | 清理 `/etc/hosts` 里重复的 `127.0.1.1 localhost`（曾让 `localhost` 解析出两个地址，与只绑回环的后端叠加后出现偶发 502）；`proxy_pass` 统一写死 `127.0.0.1:8080`，不再依赖 `localhost` 解析 |
| 反馈工单 500 | `data/support/tickets.json` 原先入库，部署 `git reset --hard` 会删掉的不仅是文件，还有所在目录；而 `data/` 归 root 所有，服务（`agritime`）建不了子目录 → 提交工单 500。已把工单数据迁到 `backend/var/support/tickets.json`（服务账号可写、每日备份覆盖），写入改为「临时文件 + 原子替换」，不再依赖文件属主 |
| 匿名工单 | 匿名访客提交工单后能凭浏览器生成的 `X-Visitor-Id` 查看/回复自己的工单（此前详情接口只认登录用户，匿名用户看不到自己的咨询） |
| 工单类别 | 服务端按白名单（种植技术/病虫害/天气农事/地块管理/其他）落库，前端详情对类别做转义，防止任意 HTML 混入 |
| 探活接口 | `/api/health/detail` 在 nginx 上用精确匹配拦掉（此前注释写了"不暴露"但实际被 `/api/` 前缀代理出去），内部状态只允许本机直连 8080 查看 |
| nginx 日志轮转 | `/etc/logrotate.d/nginx` 原文件是 CRLF 换行 + `endpostrotate` 拼写错误，logrotate 每天静默跳过；已修正并纳入版本库（`deploy/nginx/logrotate.d-nginx`） |
| 遗留清理 | 删除每分钟空跑的 `/etc/cron.d/agritime-frontend`；`certbot.timer` 临时停用、ufw 443 收回（`enable-https.sh` 会自动恢复） |
| 注册防刷 | 同一 IP 每小时最多 30 次注册尝试（识别 nginx 注入的 `X-Real-IP`）；本地直连 8080 不受限 |
| 作物规程 | `data/init.sql`（400 作物 × 8 节点）经 `deploy/tools/gen-protocols-from-initsql.py` 导入 `backend/crop-protocols.json`，规程总数 13 → 400；init.sql 的 `job_type` 与 `job_name` 大面积错位（705 行），`fix-initsql-jobtype.py` 按名称语义修源数据、生成器仍保留推断；`data/crop-base/crop-schedule.json` 的 18 个产区示范规程（31 个产区名映射、党参除外）替换对应作物的 SQL 节点并记录 `regions`，新增 `other` 操作类型（出苗/培土/搭架/清园等）；手工 13 个规程以 `deploy/data/manual-protocols.json` 为稳定输入，重复生成不会被覆盖 |
| 规程复核 | 后台新增「规程」页签（`GET /api/admin/protocols`、`POST /api/admin/protocols/review`）：400 套规程默认「待复核」，可逐条通过 / 驳回 / 撤回并留备注；结论写 `backend/var/protocol-reviews.json`（不动生成的 `crop-protocols.json`，重新生成不会丢），审计记录「规程复核」 |
| nginx 站点配置 | `/etc/nginx/sites-enabled/agritime` 由普通文件改为指向仓库 `deploy/nginx/agritime.conf` 的软链；`deploy.sh` 自动校正软链，`nginx -t` 通过后热加载，校验失败保持旧配置运行 |
| 今日结论 | 时间线只把**当天命中**的节点作为「今日结论」；当天无节点时提示下一个节点倒计时，全部节点过去时提示调整播期 |
| 播期校验 | `sowDate` 仅接受近两年 ~ 未来 30 天，越界返回 400 |
| 播期窗口提示 | 时间线按请求纬度选西南 / 东北 / 西北 / 长江以南 / 华北的常规播期窗口，返回 `sowWindow`（非阻塞）：不在窗口内只提示，仍按用户填写的日期生成 |
| 未知作物候选 | 422 时返回 `suggestions`（相似作物，最多 5 个；检索不到则回退 3 个已有规程的作物），前端渲染「你是不是想找」 |
| 天气磁盘缓存 | 天气窗口成功后落盘 `backend/var/weather-cache.json`（TTL 同 `CACHE_TTL_SECONDS`），重启后仍可命中；内存无缓存时降级先用磁盘缓存，`dataSource=cache` 文案会标明 |
| 限流 | API 120 次/分钟（burst 30），nginx zone 在 `/etc/nginx/nginx.conf`，仓库留档 `deploy/nginx/limit-req-zone.conf` |
| 接口缓存 | `/api/crops` 300 秒、`/api/site-config` 60 秒，其余接口仍 `no-store` |
| 前端请求 | `service.js` 统一 20 秒超时；GET 遇网络层失败自动重试一次（业务错误不重试） |
| 运维自检 | `/api/health/detail` 增加 `protocolCount` / `llmCachedCount`，`cropCount` 只是两者合计，不再单作覆盖指标 |
| CORS | 默认同源，不再返回 `Access-Control-Allow-Origin: *`；`file://` 救生索（Origin: null）与 localhost 开发端口已内置放行，其他来源不返回 CORS 头 |
| 注册收口 | 演示/正式环境把 `/etc/agritime.env` 里的 `AUTH_ALLOW_REGISTER` 设为 `false`（默认 `true` 只方便本地开发）。关闭后注册接口返回 403「当前未开放注册，请联系管理员」 |
| 演示重置 | `deploy/tools/reset-demo-data.sh`：备份后删除除超管外的演示账号、对应的地块/提醒/传感器/用户数据与工单，服务重启后回到干净状态 |

## 十、备份恢复演练与失败告警（2026-11-23）

### 每日备份失败时不再静默

`deploy/backup.sh` 现在把任何未处理失败都收进 `alert_fail`：写 stderr 和 journald
（`logger -t agritime-backup`），并在配置了机器人地址时推送一条群消息。
可选配置文件 `/etc/agritime-backup.env`（权限 600）：

```bash
BACKUP_ALERT_WEBHOOK="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxx"
```

没有配置 webhook 时只记录日志，不影响备份退出码：

```bash
tail -f /var/log/agritime-backup.log
journalctl -t agritime-backup -n 20
```

### 每月自动跑一次恢复演练

服务器已安装 `/etc/cron.d/agritime-drill`：每月 1 日 05:00 自动执行一次，结果进
journald（`journalctl -t agritime-drill`）。手动执行：

```bash
bash /agritime/deploy/tools/restore-drill.sh            # 最新一份备份
bash /agritime/deploy/tools/restore-drill.sh 20261123-031701   # 指定时间戳
bash /agritime/deploy/tools/restore-drill.sh --keep     # 保留临时目录排查
```

演练是**只读**的：把文件备份解压到 `/tmp` 临时目录，把 MySQL 备份导入临时库
`agritime_drill_<时间戳>`，核对 `users.json`、`settings.json`、`application.properties`、
`/etc/agritime.env`、nginx 站点配置和四张业务表行数，结束后删除临时库和目录。
输出 `PASS` 才算备份可用；`FAIL` 会说明缺哪个文件、哪张表导入失败或账号没有建库权限。

> 备份目前仍只存本机 `/root/backups`（保留 14 天）。磁盘故障或误删整机时无法恢复，
> 下一步应加一份异地副本（华为云 OBS）并定期演练。

## 十一、演示前收口（2026-11-23）

比赛/演示前按顺序做一遍，回到「只有超管、没有试验账号和陈旧数据」的状态。

### 1. 关闭注册

```bash
grep -q '^AUTH_ALLOW_REGISTER=' /etc/agritime.env \
  && sed -i 's/^AUTH_ALLOW_REGISTER=.*/AUTH_ALLOW_REGISTER=false/' /etc/agritime.env \
  || echo 'AUTH_ALLOW_REGISTER=false' >> /etc/agritime.env
systemctl restart crop-calendar.service
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8080/api/auth/register \
  -H 'Content-Type: application/json' -d '{"username":"demo_probe","password":"DemoProbe123"}'
# 期望 403（当前未开放注册，请联系管理员）
```

需要现场演示注册流程时再把这一行改回 `true` 并重启。

### 2. 清空演示数据

```bash
bash /agritime/deploy/tools/reset-demo-data.sh --dry-run   # 先看会删哪些账号/行/文件
bash /agritime/deploy/tools/reset-demo-data.sh --yes       # 备份后执行
bash /agritime/deploy/tools/reset-demo-data.sh --yes --keep nonghu01   # 保留某个演示号
```

脚本先备份到 `/root/backups/demo-reset-<时间戳>.tar.gz`（含 `backend/var`），再停服、
删账号、清 `userdata/plot-history/reminders/sensors` 文件与 MySQL 对应行、清空工单，
最后重启并等 `/health` 恢复 200。回滚命令脚本结束时会打印。

### 3. 不用再预热 LLM 规程

`backend/crop-protocols.json` 已覆盖 400 种作物（13 份人工 + 387 份导入），常规作物
首次打开时间线直接读本地规程，不再等 LLM 生成。只有库里没有的作物名才会走 LLM
（约 4 秒，失败返回 422「暂不支持该作物品种」）。

### 4. 演示前自检

```bash
curl -s -o /dev/null -w 'home %{http_code}\n' http://115.175.44.27/
curl -s -o /dev/null -w 'health %{http_code}\n' http://115.175.44.27/health
curl -s -o /dev/null -w 'crops %{http_code}\n' http://115.175.44.27/api/crops
# 后台用 super_admin 登录，确认 /api/admin/stats 里 users 只剩超管
```
