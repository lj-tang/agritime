# 队友未提交改动归档（2026-11-06）

来源：服务器 `/agritime` 工作区，2026-11-06 部署 `a97b8a5` 前发现并归档。
完整 diff 见 `working-tree.patch`；下面 8 个文件是该时刻队友工作区的完整版本。
服务器上另存一份相同内容的补丁在 `/root/agritime-teammate-archive/20261106/`，
对应 `git stash@{0}`（`pre-deploy 2026-11-06 队友未提交改动`）。

## 这批改动做了什么

两块独立工作，都**未合并**进主线：

### 1. 渔业独立页 + 前端兜底（`industry.js` / `sidebar.js` / `index.html`）
- 前端内置 50 个养殖品种（`SPECIES_50`）+ 每个品种的完整阶段规程（`SS` 表）；
- 后端 `/api/industry/*` 不可用时，前端用 **Open-Meteo 预报 + 归档** API 自己算时间线（`generateGenericTimeline`）；
- 侧栏新增「渔业养殖」入口，`index.html` 新增 `industry-pane`。

**为什么不合并**：
- 与既有测试红线冲突——「品种不得在前端硬编码，应从 `/api/industry/species` 读」
  （`pages.test.js` 明确断言 `!industry.js.includes('硬编码')` 类逻辑）；
- 主线已把养殖日历做成「我的地块 → 鱼塘详情」内嵌（`my-plots.js` 的 `pond-calendar`），
  另起一个独立板块会造成两个渔业入口、两套数据口径。

### 2. 播期日期放宽 + 远期节点常年气候兜底（`SowCheckHandler` / `TimelineOrchestrator` / `TimelineHandler` / `timeline.html` / `index.html` / `common.css`）
- 播种日期可选范围从「今天后 30 天」放宽到「未来一年」，`SowCheckHandler` 的
  `PLANNED_FUTURE_DAYS` 60→180、`HARD_FUTURE_DAYS` 120→400；
- 时间线远期节点在没有实时天气时，改用**常年气候**给出概览（`weatherSummary` 不再为 null，
  显示「按常年气候规划：同期均温 X℃、雨日占比 Y%」），并输出 `weatherIcon` / `suitability` / `weatherFactors`。

**为什么只归档、不直接合并**：
- 日期放宽（30 天→一年）是**产品口径变更**，会联动影响播种校验、茬口校验、提醒的
  「未来 N 天」边界，需要先确认范围再一并改，不宜单点放宽；
- 「常年气候兜底」方向正确、可复用，但实现上把 `TimelineOrchestrator` 的 NodeView 加了三个字段、
  改动 60+ 行，与主线当前的降级口径（`degraded` → 「天气未获取」）是两套并存逻辑，
  需要先统一「远期节点到底展示常年气候还是规程用量」再合。

## 恢复方式

```bash
# 按文件取用（推荐）：直接复制本目录里的完整文件覆盖对应路径
# 或看 diff：
git apply --check attic/2026-11-06-teammate/working-tree.patch   # 先验证
git apply attic/2026-11-06-teammate/working-tree.patch           # 应用（注意与主线已冲突）
```

服务器上的 `git stash@{0}` 是同一份，**不要整包 `git stash pop`**——会与主线已改动的
`TimelineHandler` / `TimelineOrchestrator` / `index.html` 冲突，且渔业独立页与既有测试冲突。
