# 归档：传感器 / 多端同步（2026-11）

这批代码是队友在服务器上写了一半、**未提交、也未跟踪**的实现，原先只存在于
`/tmp/extra-src`（重启即丢）。2026-11-03 清理遗留时抢救到这里，保持原样保存，
**没有接入构建**（`attic/` 不在 `backend/src` 下，`javac` 不会编译它）。

## 里面是什么

| 文件 | 作用 | 对应任务书 |
| --- | --- | --- |
| `SensorStore.java` | 设备与读数持久化，落盘 `var/sensors/<plotId>.json`，读数上限 1000 条 | T19 传感器接口拉取 |
| `SensorPoller.java` | 按设备 URL 拉取读数、阈值判定、生成告警 | T19 |
| `SensorHandler.java` | `/api/sensor/{devices,readings,proxy,poll}` 四组接口 | T19 |
| `SyncStore.java` | 多端同步 `var/sync/<userId>.json`，按 `updatedAt` 合并、留 500 条历史 | T21 存储层 |
| `SyncHandler.java` | `/api/sync/{push,pull,export,account,history,status}` | T21 |

## 为什么没有直接合并

代码本身能编译通过（与当前 `backend/build/classes` 对齐验证过），但有四个问题，
接进去会把已有的坑再挖一遍：

1. **`SensorHandler` 没有鉴权**：`plotId` 由调用方随便传，知道 plotId 就能读写别人的设备与读数。
2. **`/api/sensor/poll` 与 `/api/sensor/proxy` 是 SSRF 入口**：设备 URL 由用户填写，服务端直接去拉，
   没有内网/回环地址过滤，公网部署下可以被用来探测内网。
3. **告警写进了另一套提醒存储**：`SensorPoller.saveAlertReminder` 写的是
   `var/reminders/sensor_alerts.json`，而线上提醒中心读的是 `ReminderStore` 的
   `var/reminders/<userId>.json`。两套存储 → 设备告警在提醒中心里永远看不到。
4. **`SyncStore` 与现有实现重复**：`/api/me/data`（`UserDataStore`）已经在做账号级同步，
   前端 `user-sync.js` 也已经按 `updatedAt` 合并并留痕。再上一套 `/api/sync` 会变成两份数据。
   另外 `SyncHandler.resolveUser` 在未登录时会退到 `?userId=`，等于可以读任意账号的数据。

## 接手 T19 / T21 时怎么做

- **传感器**：保留 `SensorStore` 的落盘结构（`var/sensors/<userId>/<plotId>.json` 建议加一层用户目录），
  在 `handle` 开头用 `Auth.current(ex)` 拿用户，设备与读数都按用户校验归属；
  设备 URL 加白名单校验（只允许 `http/https`，解析后拒绝私有网段与回环）。
  告警改成调 `ReminderStore` 的写入方法，落到该用户自己的提醒档案里，不要另存一份。
- **同步**：不要再加一套。`UserDataStore` + `user-sync.js` 已经在跑，
  缺的是服务端在文件存储之外提供 MySQL 后端（T21 的原意），在 `UserDataStore` 内部做后端切换即可。

## 来源

`/tmp/extra-src/com/cropcalendar/{api,service}`，2026-11-03 由部署机导出。
原始目录已随清理删除；部署机 `/root/agritime-teammate-archive/20261103/` 另存有一份同样的
`extra-src/`，以及当时三个 stash 的补丁与说明。本目录是仓库内的可追溯副本。
