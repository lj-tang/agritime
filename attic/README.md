# attic —— 未接线的历史代码

这个目录放**不参与构建**的历史代码：队友未提交的实现、被替代的旧版本、需要改造后才能上线的半成品。
`backend/src` 之外，`javac` 不会编译，测试也不会扫描，纯粹为了不丢东西、可追溯。

规则：

1. 只放**有接手价值**的东西，且必须有 README 说明「是什么、为什么没合并、接手要改什么」。
2. 不放能直接编译进主线的代码 —— 能上线就上线，不要堆在这里。
3. 对应的 stash / 未跟踪文件清理记录在部署机 `/root/agritime-teammate-archive/<日期>/`，
   那里存的是补丁原文（补丁按当时基线生成，只建议按文件取用，不要整包 apply）。

## 现有内容

| 目录 | 内容 | 状态 |
| --- | --- | --- |
| `2026-11-sensor-sync/` | 传感器拉取与多端同步的早期稿 | 传感器部分已于 2026-11-04 按安全口径重做并上线（见 `backend/src/com/cropcalendar/service/sensor/`）；`SyncStore`/`SyncHandler` 仍留档，未采用 |

> 历史：`2026-11-06-teammate/`（队友未提交的渔业独立页 + 播期放宽 + 常年气候兜底）已于 2026-11-14 删除——三块功能主线均已用自己的实现替代（渔业 50 品种走 `/api/industry` 接口、`HARD_FUTURE_DAYS=400`、`TimelineOrchestrator` 的 `climateNormal`/`outlook`）。当时归档的补丁原文仍存于部署机 `/root/agritime-teammate-archive/20261106/`。
