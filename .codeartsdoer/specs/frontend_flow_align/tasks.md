# 前端业务流程对齐后端真实接口 · 编码任务规划（frontend_flow_align）

> 来源：`spec.md`（REQ-FFA，19 条）+ `design.md`（629 行技术设计）
> 范围红线：仅改 `frontend/`；不得改后端、`contract/`、`data/`、`deploy/`、`docs/`、nginx；不引入框架/构建工具/第三方库。
> 强制红线：`frontend/tests/pages.test.js` 既有 13 项断言必须全绿，仅可新增、不可删除或弱化。
> 已确认决策：D-01 有匹配 cropId → 传 cropId，无匹配 → 传中文名（走后端大模型）；D-02 超时上限 20000ms。

---

## 1. 建立作物标识映射共享模块

**涉及文件**：`frontend/assets/js/crop-map.js`（新增，唯一改动文件）

### 1.1 定义合法 cropId 常量与作物数据集

- [ ] 在 `frontend/assets/js/crop-map.js` 定义 `CROP_IDS`（`dry_garlic`、`wheat_winter`、`corn_summer`、`rice`、`soybean` 共 5 个）。
- [ ] 定义 `CROPS`，每条含 `name/cropId/lat/lng/city`：大蒜 `dry_garlic(35.07,116.31)`、小麦 `wheat_winter(35.30,113.93)`、玉米 `corn_summer(46.65,126.99)`、水稻 `rice(30.33,112.24)`、**新增大豆 `soybean(34.75,114.30)`**、番茄 `cropId: null(36.86,118.79)`。
- 验收标准：`CROPS` 覆盖全部 5 个 cropId；番茄 `cropId` 显式为 `null`，无任何后端 5 个之外的臆造 cropId（REQ-FFA-UBI-005/UBI-008）。
- 前置依赖：无。

### 1.2 实现 `getCropByName(name)` 精确查询

- [ ] 实现 `getCropByName(name)`：按 `name` 精确匹配 `CROPS`，未命中返回 `undefined`；无副作用、不抛异常（JSDoc `@returns {CropEntry|undefined}`）。
- 验收标准：`getCropByName('湖北荆州水稻')` 返回含 `cropId:'rice'` 的条目（供回填 `city`/默认坐标）。
- 前置依赖：1.1。

### 1.3 实现 `resolveCropId(name)` 中文名 → cropId 解析

- [ ] 实现 `resolveCropId(name)`，解析顺序：规范化 → 精确匹配 → 包含匹配 → `CROP_KEYWORDS` 关键词命中 → 无匹配返回 `null`。
- [ ] 包含匹配规则：`CROPS[].name` 包含输入或输入包含 `CROPS[].name`，多命中取最长名称；`CROP_KEYWORDS` 覆盖 `大蒜/小麦/冬小麦/玉米/夏玉米/水稻/稻/大豆`。
- [ ] 幂等透传：入参已是合法 cropId（命中 `CROP_IDS`）时直接返回；非字符串入参按空串处理返回 `null`。
- 验收标准：`resolveCropId('大蒜')`→`dry_garlic`、`resolveCropId('山东金乡大蒜')`→`dry_garlic`、`resolveCropId('rice')`→`rice`、`resolveCropId('山东寿光番茄')`→`null`（REQ-FFA-UBI-006、异常场景 5.2.3.1）。
- 前置依赖：1.1。

### 1.4 实现 `buildCropParam(name)` 非空兜底

- [ ] 实现 `buildCropParam(name) = resolveCropId(name) || 规范化中文名`；入参与规范化结果皆空时回落 `CROPS[0].cropId`，保证返回值非空。
- 验收标准：`buildCropParam('河南新乡小麦')`→`wheat_winter`；`buildCropParam('番茄')`→`番茄`（中文名，走 D-01 大模型分支）；返回值恒非空（REQ-FFA-EVT-007）。
- 前置依赖：1.3。

### 1.5 挂载 `window.CropMap` 命名空间

- [ ] 以 IIFE 方式将 `CROP_IDS`、`CROPS`、`CROP_KEYWORDS`、`resolveCropId`、`buildCropParam`、`getCropByName` 挂载到 `window.CropMap`；文件内不含 `Chart.js`/`echarts`/`d3` 等第三方词。
- 验收标准：浏览器加载后 `window.CropMap.resolveCropId` 可调用；文件为纯浏览器脚本（无 `require`/`import`）（REQ-FFA 4.3.2、4.4.1）。
- 前置依赖：1.1~1.4。

---

## 2. 首页表单与提交链路对齐

**涉及文件**：`frontend/pages/index.html`
**前置依赖**：第 1 组完成。

### 2.1 引入映射脚本并引用共享数据集

- [ ] 在 `index.html` 业务脚本之前新增 `<script src="../assets/js/crop-map.js"></script>`。
- [ ] 删除本地 `CROPS` 定义，改为 `var CROPS = window.CropMap.CROPS;`（保持既有 `name/lat/lng/city` 字段名不变）。
- 验收标准：`renderSuggestions`、`findCrop`、识别回填三处仍正常工作；`index.html` 含 `crop-map.js` 引用（REQ-FFA-UBI-005）。
- 前置依赖：2.1 之前的 1.5。

### 2.2 新增 `buildTimelineQuery(opts)` 参数拼装

- [ ] 实现 `buildTimelineQuery({ cropName, sowDate, lat, lon, location })`，用 `URLSearchParams` 编码，`crop` 由 `window.CropMap.buildCropParam(cropName)` 得出，输出 `crop&sowDate&lat&lon&location`（REQ-FFA 4.3.1）。
- 验收标准：输出形如 `crop=dry_garlic&sowDate=2025-03-15&lat=35.07&lon=116.31&location=山东省济宁市金乡县`；不含 `sowingDate`/`latitude`/`longitude`。
- 前置依赖：1.5。

### 2.3 提交跳转参数对齐（sowDate/lat/lon/crop）

- [ ] 改写 `submit` 处理器：`lat/lon` 取自 `findCrop` 命中的 `lat/lng`，未命中回落默认 `35.07/116.31`；跳转改为 `'timeline.html?' + buildTimelineQuery(...)`。
- [ ] 删除旧参数 `sowingDate`、`latitude`、`longitude`、以中文名作 `crop` 的写法。
- 验收标准：提交后 URL 含 `crop&sowDate&lat&lon&location`；`crop` 为 cropId 或中文名（REQ-FFA-UBI-009/UBI-011/UBI-012）。
- 前置依赖：2.2。

### 2.4 识别回填经映射

- [ ] `photo-btn` 的 `onConfirm(cropName)` 回调保持"输入框展示中文名 + 命中则回填 `city`"，提交时统一经 `buildCropParam` 映射为 cropId 传参（不在回填瞬间改展示）。
- 验收标准：识别返回"水稻"确认后，输入框显示中文名、提交得 `crop=rice`（REQ-FFA-EVT-014/UBI-015）。
- 前置依赖：2.2、2.3。

### 2.5 保留既有 DOM 契约（红线）

- [ ] 保留 `id="sowingDate"`/`name="sowingDate"` 隐藏域、`class="date-picker"`、`RANGE_BACK_DAYS`、无 `type="date"`、`id="crop"/"location"/"crop-suggestions"/"photo-btn"`。
- 验收标准：断言 1/2/9 依赖字符串全部保留；不出现 `type="date"`（红线，REQ-FFA-UBI-018）。
- 前置依赖：2.1~2.4。

---

## 3. 时间线页取数与渲染对齐

**涉及文件**：`frontend/pages/timeline.html`
**前置依赖**：第 1 组完成。

### 3.1 引入映射脚本并集中定义 CONFIG 常量

- [ ] 在 `mock-data.js` 之后、业务脚本之前新增 `<script src="../assets/js/crop-map.js"></script>`。
- [ ] 定义 `CONFIG`：`API_PATH='/api/timeline'`、`TIMELINE_TIMEOUT_MS = 20000`、`DEFAULTS`（`cropId:'dry_garlic'`、`sowDate`、`lat:35.07`、`lon:116.31`）；保留既有 `CHART_CONFIG`（含 `SIGMA`）不动。
- 验收标准：含 `TIMELINE_TIMEOUT_MS` 与 `20000` 单点定义；两页面均引用 `crop-map.js`（REQ-FFA 4.1.2、4.4.1）。
- 前置依赖：1.5。

### 3.2 实现 `parseQuery(search)` URL 参数解析对齐

- [ ] 实现 `parseQuery(search)` → `{ crop, sowDate, lat, lon, location }`：`crop` 缺省或非法时经 `CropMap.buildCropParam` 兜底；`sowDate` 缺省回落 `DEFAULTS.sowDate`；`lat/lon` 用 `Number()` 数值化，`NaN`/越界回落默认坐标；可对旧名 `sowingDate/latitude/longitude` 做**只读**兼容并注释标注"仅读不写"。
- 验收标准：无参数直访时返回完整默认值；`crop` 恒非空；`lat/lon` 恒为有限数值（REQ-FFA-UBI-010、异常场景 5.3.3）。
- 前置依赖：3.1。

### 3.3 实现 `buildTimelineUrl(query)` 构造 GET URL

- [ ] 实现 `buildTimelineUrl(query)`：`CONFIG.API_PATH + '?' + URLSearchParams`，**仅含** `crop/sowDate/lat/lon`，不含 `location`。
- 验收标准：输出 `/api/timeline?crop=dry_garlic&sowDate=2025-03-15&lat=35.07&lon=116.31`；不含 `sowingDate/location/latitude/longitude`（REQ-FFA-UBI-002/UBI-003/UBI-012）。
- 前置依赖：3.2。

### 3.4 重写 `getData()`：GET + 20s 超时 + 降级标记

- [ ] `getData()` 改为 `fetch(buildTimelineUrl(query), { method: 'GET', signal: controller.signal })`，删除 JSON body 与 `Content-Type` 提交体。
- [ ] 用 `AbortController` + `setTimeout(CONFIG.TIMELINE_TIMEOUT_MS)` 实现超时中止；`finally`/各分支 `clearTimeout` 成对清理。
- [ ] Promise **永不 reject**：`AbortError`→降级来源 `timeout`、非 2xx→`http`、解析失败/结构非法→`invalid`、网络错误→`offline`，统一回落 `window.MOCK_TIMELINE` 并写入降级来源标记。
- 验收标准：含 `'GET'`、不含 `method: 'POST'`；含 `AbortController`/`setTimeout`/`TIMELINE_TIMEOUT_MS`；降级返回对象 `nodes` 为数组（REQ-FFA-UBI-001、STAT-011、UNW-012）。
- 前置依赖：3.3。

### 3.5 `renderConclusion` 副标题与降级文案

- [ ] brand 副标题由 `payload.crop · payload.sowingDate` 改为 `displayCropName · query.sowDate`（`displayCropName`：命中 CROPS 用 `name`，否则用中文名，禁止展示 cropId）；删除闭包单例 `payload`。
- [ ] "数据来源"文案保留 `realtime/cache/unavailable` 映射；降级时在 `#data-meta` 追加降级标识（如"本地兜底数据"），该文案不得等于既有禁止串 `'今天适宜作业，按计划进行'`。
- 验收标准：副标题显示中文作物名与 `sowDate`；降级时用户可见降级提示，非静默（REQ-FFA-UNW-012、异常场景 5.3）。
- 前置依赖：3.2。

### 3.6 `render()` 契约守卫与全空 reminder 跳过

- [ ] 渲染前守卫：`data` 非对象 / `nodes` 非数组 → 归一化为 `{ todayConclusion: {}, nodes: [] }`；缺 `todayConclusion` 用 `{}`（`status` 默认 `green`、`copy` 默认空串）。
- [ ] `reminders` 渲染前过滤"全空 reminder"（`festivalName/festivalDate/reminderCopy` 均空则跳过），保留 `renderReminder` 函数与 `data.reminders &&` 守卫；空态提前 `return` 文案不变。
- 验收标准：后端不返回 `reminders` 时不报错、不渲染空节点；缺 `todayConclusion` 不抛 `TypeError`（REQ-FFA 4.2.1/4.2.2）。
- 前置依赖：3.4。

### 3.7 `renderChart` 闸门强化（真实/占位对齐）

- [ ] `renderChart(data)` 闸门改为 `hasRealData && hasValidDays`（`hasRealData = nodes.some(n => n.date != null || n.name != null)`，`hasValidDays = nodes.some(n => n.daysAfterSowing != null)`），仅双真时调用 `buildChartSVG`，否则 `buildEmptyChartSVG`。
- [ ] 不新增任何直接调用 `buildChartSVG` 的旁路；`buildChartSVG` 内部 `days.length===0 → buildEmptyChartSVG` 二次兜底保留。
- 验收标准：真实 nodes 绘多峰曲线、峰位对应 `daysAfterSowing`；mock null 占位显示空坐标轴 + "数据待接入"、不绘假曲线（REQ-FFA-UBI-016、STA-017）。
- 前置依赖：3.4。

### 3.8 清除残留并保持既有断言依赖字符串

- [ ] 全面清除 `timeline.html` 内 `sowingDate`、`location:` 对象字面量、`latitude`/`longitude`，`location` 仅作展示变量。
- [ ] 保留 `conclusion`/`tl-action`/`tl-decision`/`tl-note`/`node.copy`/`predicted`/`certain`/`classList.toggle('open')`/`未生成节点，请检查作物或日期`/`mode-switch`/`mode-tab`/`data-mode`/`id="chart"`/`createElementNS`/`CHART_CONFIG`/`SIGMA`/`数据待接入`，且不引入 `Chart.js`/`echarts`/`d3`。
- 验收标准：断言 3~7、10~13 依赖字符串全部保留；无 POST/旧参数残留（红线，REQ-FFA-UBI-018）。
- 前置依赖：3.4~3.7。

---

## 4. 测试断言补充与回归

**涉及文件**：`frontend/tests/pages.test.js`
**前置依赖**：第 1~3 组完成。

### 4.1 新增 12 条对齐方向断言

- [ ] 新增断言并逐条覆盖：GET 方法对齐、参数名对齐（含 `sowDate/lat/lon/URLSearchParams`、`timeline.html` 无 `sowingDate`）、位置标量传参（无 `location:` 对象/`latitude`/`longitude`）、超时常量（`TIMELINE_TIMEOUT_MS`+`20000`+`AbortController`+`setTimeout`）、映射表完整性（`crop-map.js` 含 5 个 cropId + `resolveCropId`+`buildCropParam`）、页面共用映射（两页面均含 `crop-map.js` 引用）、无臆造 cropId（番茄 `null`）、URL 参数链路自洽（`index.html` 含 `sowDate`/`lat`/`lon`）、reminders 容错、函数图像双形态、降级不静默、mock 不造假（`date: null`/`name: null`）。
- 验收标准：12 条新增断言读取正确文件（`assets/js/crop-map.js`、两页面、`mock-data.js`）且全部通过（REQ-FFA-UBI-019）。
- 前置依赖：1.5、2.5、3.8。

### 4.2 既有 13 项断言红线核查

- [ ] 逐条核对既有 13 项断言依赖字符串未被改动（对照 design 2.4.1 表格）；不删除、不弱化、不改写既有断言原文。
- 验收标准：既有 13 项断言原文与 `main` 一致；无断言被跳过/注释（REQ-FFA-UBI-018）。
- 前置依赖：4.1。

### 4.3 运行回归测试

- [ ] 执行 `node --test frontend/tests/pages.test.js`，确认既有 13 项 + 新增断言全绿。
- 验收标准：命令输出 0 失败；无未捕获异常（REQ-FFA-UBI-018/UBI-019）。
- 前置依赖：4.1、4.2。

---

## 5. 端到端验收验证

**前置依赖**：4.3。

### 5.1 三条链路人工验证

- [ ] 本地静态服务下验证：①命中 cropId（大蒜 → `crop=dry_garlic`）；②无匹配（番茄 → `crop=番茄` 中文名）；③无参数直访（默认值兜底）。三条链路页面均可渲染、无控制台未捕获错误。
- 验收标准：①请求 URL `crop=dry_garlic`；②`crop=` 为中文名且非空；③页面正常展示（design 2.4.3.2）。
- 前置依赖：4.3。

### 5.2 降级与函数图像模式验证

- [ ] 断开 `/api` 或强制 20s 超时：验证 loading 在 20s 内结束、渲染 mock 占位视图、`#data-meta` 显示降级标识；切到函数图像模式显示空坐标轴 + "数据待接入"、无假曲线；控制台无未捕获错误。
- 验收标准：20s 内结束 loading；降级视图可用；函数图像模式占位正确（REQ-FFA-STA-011、UNW-012、STA-017、UBI-013）。
- 前置依赖：5.1。

---

## 6. 交付：上传服务器与 Git 提交

**前置依赖**：第 5 组完成。
**红线**：不改动后端、`contract/`、`data/`、`deploy/`、`docs/`。

### 6.1 上传至目标服务器

- [ ] 将修改后的 `frontend/` 代码上传至 115.175.44.27 的 `/agritime`（依赖 7.2 nginx 前提已由部署方确认）。
- 验收标准：服务器上 `/agritime` 下 `crop-map.js` 与两页面为最新版本；通过同源 `/api` 可访问（交付任务，spec 7.3.1）。
- 前置依赖：5.2。

### 6.2 Git 提交并推送

- [ ] 将本次前端改动提交并推送至 GitHub 仓库 `lj-tang/agritime`；提交信息说明前端流程对齐内容。
- 验收标准：远端仓库含本次改动；提交范围仅限 `frontend/` 与 `.codeartsdoer/specs/frontend_flow_align/`（交付任务，spec 7.3.2）。
- 前置依赖：6.1。

---

## 需求覆盖对照

| 需求 | 覆盖任务 |
|------|---------|
| REQ-FFA-UBI-001/002/003/004（GET + 参数名 + cropId + 响应解析） | 3.3、3.4、3.6 |
| REQ-FFA-UBI-005/006（CROPS 补 cropId + 映射能力） | 1.1、1.3、2.1 |
| REQ-FFA-EVT-007（有匹配传 cropId / 无匹配传中文名） | 1.4、2.2 |
| REQ-FFA-UBI-008（无臆造 cropId） | 1.1、4.1 |
| REQ-FFA-UBI-009/010/011/012（参数链路与标量位置传参） | 2.2、2.3、3.2、3.3 |
| REQ-FFA-UBI-013（降级下渲染可用） | 3.7、5.2 |
| REQ-FFA-STA-011（20s 超时中止） | 3.1、3.4 |
| REQ-FFA-UNW-012（降级不静默） | 3.4、3.5 |
| REQ-FFA-EVT-014/UBI-015（识别中文名映射） | 2.4 |
| REQ-FFA-UBI-016/STA-017（函数图像真实/占位对齐） | 3.7 |
| REQ-FFA-UBI-018/019（既有 13 项全绿 + 新增断言） | 4.1、4.2、4.3 |
| 交付（上传服务器 + Git） | 6.1、6.2 |