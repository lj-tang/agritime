# 第二阶段前端增强与数据对齐 — 编码任务清单（stage2_frontend）

> 依据 `.codeartsdoer/specs/stage2_frontend/spec.md`（60 条 EARS 需求）与 `.codeartsdoer/specs/stage2_frontend/design.md`（D-01~D-12 决策、8 项文件变更、测试策略）拆分。
> **范围红线**：仅改 `frontend/` 目录；不引框架/构建工具/第三方库；不改后端（Java/8080、`crop-protocols.json`、`market-festivals.json`、`scoreNode`）、`contract/`、`data/`、`deploy/`、`docs/`；不改 nginx/systemd。
> **测试红线**：`node --test frontend/tests/pages.test.js` 既有 22 项断言一字不改、必须全绿，仅允许新增。
> **数据红线**：前端不硬编码节日日期、不推算农历、不自算天气阈值、不直连 Open-Meteo；mock 保持 null 占位。
> **关键约束（D-02）**：`POST` 字面串必须隔离在新建的 `chart-client.js`，`timeline.html` 禁止出现 `method: 'POST'`（保护既有断言第 14 项）。
>
> **执行顺序总览**：任务组 1（基础设施，可并行）→ 2/3/5 依赖 1.1、1.3 → 4 依赖 1.3、2 → 6/7 依赖 1.1、1.2 → 8 独立 → 9 依赖 2~8 → 10 收尾 → 11/12 分析报告（独立，可全程并行）。

## 1. 基础设施模块（纯函数 / 存储 / 客户端 / 常量）

### 1.1 新建纯函数与映射常量模块 `timeline-logic.js`
- [ ] 新建 `frontend/assets/js/timeline-logic.js`，实现具名常量 `WEATHER_ICON_MAP`（至少覆盖 晴/雨/风/阴/雪/雾/雷/多云 + 未识别通用兜底分支）、`SUITABILITY_MAP`（green/yellow/red → 适宜/谨慎/不宜 + 文案 + class）、`SEVERE_WEATHER_MAP`（暴雨/大风/高温/霜冻 + 兜底），以及纯函数 `calcCountdown(festivalDate, today?)`（返回 `{days, state}`，state∈future/today/past/invalid，仅本地日期差）、`getWeatherIcon(type)`、`getSuitability(value)`、`getSevereAlert(alert)`、`normalizeFactors(input)`；采用 `window.TimelineLogic = ...` + `typeof module !== 'undefined' && module.exports` 双导出，无 DOM 依赖
- **涉及文件**：`frontend/assets/js/timeline-logic.js`（新增）
- **验收标准**：REQ-S2F-UBI-010/011/012、UBI-056、UNW-057、UBI-058、UBI-059、EVT-031~034；`getWeatherIcon(null)` 返回 null；未识别类型返回兜底 + 原文案不抛错
- **依赖**：无，可并行（1.2、1.3、1.4、1.5 同批）

### 1.2 新建历史记录存储模块 `history-store.js`
- [ ] 新建 `frontend/assets/js/history-store.js`，实现 `HistoryStore.list()`（键名 `agritime.history.v1`，解析失败/不可用 → `[]`）、`HistoryStore.add({cropName,sowDate,location})`（按 cropName+sowDate+location 去重 + 头插 + 截断 5 条 + 写回）、`HistoryStore.clear()`；全流程 `try/catch` 包裹，仅存非敏感字段（无 Key/令牌），localStorage 不可用时静默降级不抛出；双导出
- **涉及文件**：`frontend/assets/js/history-store.js`（新增）
- **验收标准**：REQ-S2F-EVT-027/028/029、OPT-030、REQ 6.1 数据约束、REQ 4.3.2 安全性
- **依赖**：无，可并行

### 1.3 新建图表数据客户端 `chart-client.js`（隔离 POST 字面串）
- [ ] 新建 `frontend/assets/js/chart-client.js`，实现 `ChartClient.request(context)` → `Promise<{ok, data?, degradedReason?}>`，内部使用 `method: 'POST'` + `AbortController` 超时请求 `/api/chart`；失败/超时/非法结构一律回落为降级态，**永不 reject**；双导出。确保 `POST` 与 `/api/chart` 字面串**只出现在本文件**
- **涉及文件**：`frontend/assets/js/chart-client.js`（新增）
- **验收标准**：REQ-S2F-UNW-007/008/009、design D-02/D-09；保护既有断言第 14 项
- **依赖**：无，可并行

### 1.4 对齐 mock 数据结构（v1.1 新字段 null 占位）
- [ ] 修改 `frontend/assets/mock/mock-data.js`，为节点补 `solarTerm`、`solarTermTip`、`weatherFactors`、`suitability`、`severeWeatherAlert` 字段，为 reminders 补 `dateAnchor`、`applicableCrops`、`salesCopy` 字段，**全部保持 null 占位（或空数组），不得填写任何真实日期/数值/天气结论**
- **涉及文件**：`frontend/assets/mock/mock-data.js`
- **验收标准**：REQ-S2F-UBI-035/036、UBI-054/055/056/059/060、design D-12、REQ 8.2 禁止项
- **依赖**：无，可并行

### 1.5 核对作物映射集合（M3）
- [ ] 审查 `frontend/assets/js/crop-map.js`，确认 cropId 恰为 `dry_garlic / wheat_winter / corn_summer / rice / soybean` 5 个、无臆造值、番茄保持 `cropId: null`；本任务**仅核对，不改变行为**（既有断言 17/18 保护）
- **涉及文件**：`frontend/assets/js/crop-map.js`（保持）
- **验收标准**：REQ-S2F-UBI-037、REQ 4.4.2 参数命名统一
- **依赖**：无，可并行

## 2. 加载体验（M5 进度提示 / M6 骨架屏）

### 2.1 首屏进度提示（≤300ms 可见）
- [ ] 修改 `frontend/pages/timeline.html` 的 `#loading`，新增**不确定态**进度条 + 阶段文案（如"正在读取农时…"），默认可见、不使用百分比（无真实进度事件）；请求进行中持续可见，数据返回/降级时随 `#loading` 隐藏
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-EVT-001/002/003、禁止项（不得空白等待）、REQ 5.1.3 异常场景；design D-01
- **依赖**：无（可并行 2.2）

### 2.2 骨架屏对齐最终布局
- [ ] 调整 `frontend/pages/timeline.html` 的骨架块，使其高度/圆角/间距与 `.tl-card` 节点卡结构一致，增加 `aria-hidden` 与加载语义；结果渲染完成或进入降级时移除骨架占位
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-STA-004/005/UBI-006、禁止项（无布局大幅跳动）
- **依赖**：无（可并行 2.1）

### 2.3 进度条与骨架屏样式（纯 CSS）
- [ ] 在 `frontend/assets/css/timeline.css` 追加不确定态进度条样式与对齐后的 `.skeleton` 样式，保留纯 CSS 动画（不引入第三方加载动画库）
- **涉及文件**：`frontend/assets/css/timeline.css`
- **验收标准**：REQ-S2F-UBI-006、REQ 4.1.1/4.1.5
- **依赖**：2.1、2.2

### 2.4 引入新增脚本
- [ ] 在 `frontend/pages/timeline.html` 以 `<script src>` 引入 `../assets/js/timeline-logic.js` 与 `../assets/js/chart-client.js`（置于内联脚本之前），确认引入后 `timeline.html` 内**不出现** `POST` 字面串
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-006/4.3.4、design D-02、既有断言第 14 项
- **依赖**：1.1、1.3

## 3. 天气可视化（M8 图标 / M9 节气 / v1.1 适宜度·警示·因子·降级）

### 3.1 天气图标行（扩展类型 + 文本/aria）
- [ ] 在 `frontend/pages/timeline.html` 的 `renderNode` 中接入 `TimelineLogic.getWeatherIcon(node.weatherIcon)`，渲染可区分图标（晴/雨/风/阴/雪/雾/雷/多云）并附可读文本与 `aria` 标签；`weatherIcon` 为 null 时不渲染该行且不报错；未识别类型回退通用图标 + 原文案
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-010/011、OPT-012、UBI-058、REQ 5.4 禁止项
- **依赖**：1.1、2.4

### 3.2 节气与天气拆分独立行（去互斥）
- [ ] 修改 `renderNode`，删除 `solarTerm` 与 `weatherIcon` 的 `else if` 互斥逻辑，改为**节气标记行 + 天气行各自独立渲染**，节气标记用独立样式类以区别于普通节点；无节气时不渲染且不报错
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-EVT-013、UBI-014、OPT-015、design D-07
- **依赖**：3.1

### 3.3 恶劣天气警示（警示优先）
- [ ] 在 `renderNode` 中接入 `TimelineLogic.getSevereAlert(node.severeWeatherAlert)`，非空时渲染警示标识 + 文案；当同时存在 `suitability === green` 时追加"虽有适宜条件但存在极端天气，建议调整"说明；**警示优先于适宜度**展示
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UNW-057、spec 5.13.3 异常场景 4、design D-11
- **依赖**：3.1

### 3.4 天气适宜度三档标识
- [ ] 在 `renderNode` 中接入 `TimelineLogic.getSuitability(node.suitability)`，green/yellow/red → 适宜/谨慎/不宜，渲染徽标 + 可读文案，class 语义与后端枚举一致；`suitability` 为 null 时不渲染、**不臆造等级**；前端**不实现任何阈值计算**
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-056、REQ 6.5 天气适宜度对象、design D-06
- **依赖**：3.1（与 3.3 冲突时按 3.3 优先，由 3.3 控制闸门）

### 3.5 天气因子说明行
- [ ] 在 `renderNode` 中使用 `TimelineLogic.normalizeFactors(node.weatherFactors)` 归一化后渲染因子说明行（支持 string / string[]，如"因未来 3 天有雨，建议推迟打药"）；为空/null 时不渲染因子行
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-059、REQ 6.2.8
- **依赖**：3.1

### 3.6 天气数据中性降级态
- [ ] 在 `renderNode` 中判定"天气数据是否可用"（`weatherIcon` 与 `suitability` 与 `weatherFactors` 均 null → 不可用），不可用时渲染中性提示"天气数据暂不可用"，**不展示**图标/适宜度/警示/因子；依赖 `#timeline.innerHTML=''` 每次重建 DOM 以清除上次天气状态；与既有 20s 超时降级机制衔接，不抛未捕获错误
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-OPT-060、REQ 4.2.6/4.2.7、design D-09、spec 5.13.3 异常场景 1
- **依赖**：3.1、3.3、3.4、3.5

### 3.7 天气相关样式（图标/节气/适宜度/警示/因子）
- [ ] 在 `frontend/assets/css/timeline.css` 新增/调整天气图标、节气标记、适宜度徽标、恶劣天气警示、天气因子行的样式类；各行独立成行、不绝对定位以免遮挡节点核心文案；复用既有 CSS 变量保持视觉一致
- **涉及文件**：`frontend/assets/css/timeline.css`
- **验收标准**：REQ-S2F-UBI-058、UBI-014、REQ 5.4 禁止项、REQ 4.4.1（映射集中定义）
- **依赖**：3.1~3.6

## 4. 图表降级（M7）

### 4.1 接入图表数据客户端与真实/降级闸门
- [ ] 修改 `frontend/pages/timeline.html` 的图表模式逻辑：切至"函数图像"模式时调用 `ChartClient.request(上下文)`，成功且结构合法 → 按真实节点数据绘制曲线；失败/超时/非法结构 → 渲染空坐标轴 + "数据待接入"占位提示（保留既有 `renderChart` 的 `hasRealData`/`hasValidDays` 闸门作为最后防线）；接口返回前展示加载/占位态
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UNW-007/STA-008/EVT-009、禁止项（不硬编码假曲线、不影响节点模式）、REQ 5.3.3 异常场景
- **依赖**：1.3、2.4

### 4.2 确认图表请求串隔离（D-02 复核）
- [ ] 审查确认 `frontend/pages/timeline.html` **不含** `method: 'POST'` 且保留 `method: 'GET'`、`TIMELINE_TIMEOUT_MS`、`AbortController`；`POST /api/chart` 仅存在于 `chart-client.js`
- **涉及文件**：`frontend/pages/timeline.html`、`frontend/assets/js/chart-client.js`
- **验收标准**：design D-02、既有断言第 14/16 项
- **依赖**：4.1

## 5. 响应式适配（M10）

### 5.1 768px / 1024px 媒体查询
- [ ] 在 `frontend/assets/css/common.css` 追加 `@media (min-width:768px)`（放宽/多列/居中）与 `@media (min-width:1024px)`（居中定宽）规则，纯 CSS 实现，不引栅格框架；**保留** `overflow-x:hidden` 与 `max-width:100%` 约束
- **涉及文件**：`frontend/assets/css/common.css`
- **验收标准**：REQ-S2F-UBI-016/017/018、禁止项（不得以隐藏内容换无滚动）、design D-08
- **依赖**：无（可并行，但需在 3.7/7.x 样式完成后复核）

### 5.2 时间线页宽屏分栏
- [ ] 在 `frontend/assets/css/timeline.css` 追加宽屏分栏/居中定宽规则，使节点卡、时间线、图表在 768/1024px 下布局协调不变形、无大面积留白失衡；375px 档保持现有表现
- **涉及文件**：`frontend/assets/css/timeline.css`
- **验收标准**：REQ-S2F-UBI-017、REQ 4.5.2
- **依赖**：3.7、4.1

## 6. 识别双通道与搜索联想（M11 / M12）

### 6.1 拍照 + 上传双通道入口
- [ ] 修改 `frontend/pages/photo-capture.js`，拆为**两个并列入口**：拍照（`fileInput` 带 `capture="environment"`）与上传（无 `capture`、`accept="image/*"`），复用同一识别流程；**保留**既有断言依赖串 `needConfirm|确认`、`fallback|手动搜索`、`AbortController`、`/api/recognition`
- **涉及文件**：`frontend/pages/photo-capture.js`
- **验收标准**：REQ-S2F-UBI-019/EVT-020、禁止项（不得单通道）、design M11
- **依赖**：无（可并行）

### 6.2 识别失败回落与中文名→cropId 映射
- [ ] 完善 `photo-capture.js`：识别失败/超时/拒绝摄像头权限 → 回落手动搜索不卡死弹层；用户确认候选中文名后经 `CropMap.resolveCropId` 映射再用于后续时间线请求（`crop=rice` 等）
- **涉及文件**：`frontend/pages/photo-capture.js`、`frontend/assets/js/crop-map.js`（仅调用）
- **验收标准**：REQ-S2F-UNW-021、UBI-022、spec 5.7.3 异常场景、禁止项（未确认不得自动进入时间线）
- **依赖**：6.1

### 6.3 首页双通道入口联动
- [ ] 修改 `frontend/pages/index.html`，在识别入口处提供"拍照"与"上传/选图"两个可点击按钮，与 `PhotoCapture.open` 联动，保留既有 `id="photo-btn"` 或新增并列按钮且不破坏既有联想/拍照断言
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-S2F-UBI-019、既有断言第 1 项
- **依赖**：6.1

### 6.4 搜索联想增强（去抖 / 键盘 / 高亮 / 空输入隐藏）
- [ ] 增强 `frontend/pages/index.html` 的 `renderSuggestions`：保留 `CROPS` 本地匹配（**不发网络请求**），增加输入去抖（≤100ms 感知）、键盘上下选择与回车确认、匹配片段高亮；输入为空或无匹配时隐藏下拉；下拉不遮挡输入框
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-S2F-EVT-023/024/STA-025/UBI-026、禁止项、REQ 4.1.3
- **依赖**：无（可并行 6.3）

## 7. 历史记录与节日倒计时（M13 / M14 / v1.1 多节日）

### 7.1 首页历史记录区块 UI
- [ ] 在 `frontend/pages/index.html` 引入 `../assets/js/history-store.js`，新增历史记录区块（如 `#history-list`）渲染 `HistoryStore.list()` 结果（含清空按钮），点击某条可回填作物/日期/所在地；无历史或不可用时区块隐藏/为空且不报错
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-S2F-EVT-029、OPT-030、REQ 6.1 数据约束
- **依赖**：1.2

### 7.2 提交成功写入历史（去重 + 上限 5）
- [ ] 在 `index.html` 表单成功提交（跳转时间线）时调用 `HistoryStore.add({cropName,sowDate,location})`；确保连续 6 次以上查询后历史恒 ≤ 5 条且为最近 5 条；localStorage 抛错时静默降级不阻塞主流程；不写入任何敏感信息
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-S2F-EVT-027/UBI-028/OPT-030、REQ 4.3.2、spec 5.9.3 异常场景（数据损坏容错）
- **依赖**：7.1

### 7.3 历史记录区块样式
- [ ] 在 `frontend/assets/css/common.css` 追加历史记录区块样式，375/768/1024px 三档下不产生横向滚动、不遮挡表单
- **涉及文件**：`frontend/assets/css/common.css`
- **验收标准**：REQ-S2F-UBI-016、REQ 4.1.4
- **依赖**：7.1、5.1

### 7.4 节日倒计时计算与渲染
- [ ] 扩展 `frontend/pages/timeline.html` 的 `renderReminder`，调用 `TimelineLogic.calcCountdown(r.festivalDate)`：future → "距 XX 还有 N 天"；today → "就是今天"；past → "已过/已结束"（**不显示负数**）；invalid/缺失 → 不展示倒计时且不报错；**保留**既有 `renderReminder` 与 `Array.isArray(data.reminders)` 能力
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-EVT-031/STA-032/OPT-033/UBI-034、REQ 6.3.4、既有断言第 21 项
- **依赖**：1.1、2.4

### 7.5 节日四要素渲染
- [ ] 在 `renderReminder` 中渲染节日四要素：节日名、`dateAnchor`（固定公历/农历浮动）、`applicableCrops`、`salesCopy`（缺失时回退 `reminderCopy`）；节日日期一律取自接口返回值，前端**不硬编码日期、不推算农历**；`festivalName`/文案映射以具名常量定义
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-054、REQ 6.3.5/6.3.7、spec 5.13.3 异常场景 2
- **依赖**：7.4

### 7.6 多节日并列展示（≥6，不截断）
- [ ] 修改 `renderReminder` 遍历逻辑，`reminders` **不设条数上限、不截断**：≥6 条时纵向完整排布不覆盖，1 条正常展示，0 条不渲染区块且不报错；数量与接口返回一致
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-036/UBI-055、REQ 6.3.9、design D-10
- **依赖**：7.4

### 7.7 节日倒计时与提示样式
- [ ] 在 `frontend/assets/css/timeline.css` 追加倒计时元素（如 `.tl-countdown`）与多节日列表样式，宽屏下与节点列表协调、不撑破布局
- **涉及文件**：`frontend/assets/css/timeline.css`
- **验收标准**：REQ-S2F-EVT-031、REQ 4.1.6、design R-10
- **依赖**：7.4、7.6

## 8. 数据对齐前端侧（M1–M4 / v1.1 字段）

### 8.1 节点动态数量渲染（无前端上限）
- [ ] 审查并确认 `renderNode`/`render` 循环对节点数量无上限（可完整渲染 soybean 8 节点），节点字段缺省/为 null 时字段级守卫不抛错
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-035、REQ 4.2.4、spec 5.11.3 异常场景
- **依赖**：3.x 改造完成后复核

### 8.2 状态枚举仅消费不自算
- [ ] 审查 `renderNode` 状态映射，确认 `status`/`suitability` 仅消费后端枚举（green/yellow/red）映射到徽标/样式，**无任何前端阈值计算逻辑**
- **涉及文件**：`frontend/pages/timeline.html`
- **验收标准**：REQ-S2F-UBI-038、REQ 4.4.1、design D-06
- **依赖**：3.4

### 8.3 权威数据不硬编码审查
- [ ] 审查全部 `frontend/` 变更文件，确认无硬编码节点日期、阈值数值、节日日期；mock 保持 null 占位；参数名全链路统一为 `crop/sowDate/lat/lon`
- **涉及文件**：`frontend/`（全量审查）
- **验收标准**：REQ-S2F-UBI-035/036、spec 5.11 禁止项、REQ 4.4.2
- **依赖**：1.4、7.5

## 9. 测试断言补充（既有 22 项保持全绿）

### 9.1 加载反馈与骨架屏断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`timeline.html` 含进度提示元素/文案、`.skeleton` 保留、`timeline.css` 无第三方动画库引用
- **验收标准**：REQ-S2F-EVT-001~005、UBI-006、UBI-040
- **依赖**：2.1、2.2、2.3

### 9.2 节气与天气共存断言
- [ ] 新增 test：校验 `timeline.html` 节气行与天气行非 `else if` 互斥（各自独立渲染）
- **验收标准**：REQ-S2F-EVT-013/UBI-014/OPT-015
- **依赖**：3.2

### 9.3 天气图标扩展与适宜度/警示/因子断言
- [ ] 新增 test：`timeline-logic.js` 含 阴/雪/雾/雷/多云 映射与兜底分支；`timeline.html` 含适宜度三档标识、恶劣天气警示、天气因子行类名（如 `.tl-suitability`/`.tl-alert`/`.tl-factors`）
- **验收标准**：REQ-S2F-UBI-058/UBI-056/UNW-057/UBI-059
- **依赖**：3.1、3.3、3.4、3.5

### 9.4 图表降级隔离断言
- [ ] 新增 test：`assert.ok(!timelineHtml.includes("method: 'POST'"))`（不弱化既有断言第 14 项）且 `chart-client.js` 含 `/api/chart` 与 `method: 'POST'`；`timeline.html` 保留 `hasRealData`/`hasValidDays`/`数据待接入`
- **验收标准**：REQ-S2F-UNW-007~009、design D-02
- **依赖**：1.3、4.1

### 9.5 双通道与识别回落断言
- [ ] 新增 test：`photo-capture.js` 存在两个并列入口（一个含 `capture`、一个不含）且保留 `确认`/`fallback`/`AbortController`/`/api/recognition`
- **验收标准**：REQ-S2F-UBI-019/EVT-020/UNW-021/UBI-022
- **依赖**：6.1、6.2

### 9.6 搜索联想断言
- [ ] 新增 test：`index.html` 联想为本地匹配（无 fetch/XHR 于联想路径）、空输入隐藏下拉、候选来源 `CROPS`
- **验收标准**：REQ-S2F-EVT-023~026
- **依赖**：6.4

### 9.7 历史记录断言
- [ ] 新增 test：`history-store.js` 含键名 `agritime.history.v1`、上限 5、去重、`try/catch`；`index.html` 含历史区块与回填逻辑；无敏感字段写入
- **验收标准**：REQ-S2F-EVT-027~030、REQ 4.3.2
- **依赖**：1.2、7.1、7.2

### 9.8 倒计时与多节日断言
- [ ] 新增 test：`timeline-logic.js` 含 `calcCountdown`，`timeline.html` 含 "就是今天"、"已过"、"还有 N 天" 文案；`reminders` 遍历不设上限（无 `slice(0,` 截断）
- **验收标准**：REQ-S2F-EVT-031~034、UBI-054/UBI-055
- **依赖**：7.4、7.5、7.6

### 9.9 响应式断言
- [ ] 新增 test：`common.css`/`timeline.css` 含 `@media` 768 与 1024 断点，且仍保留 `overflow-x:hidden` 与 `max-width:100%`
- **验收标准**：REQ-S2F-UBI-016~018、REQ 4.5.2
- **依赖**：5.1、5.2

### 9.10 mock 与数据不造假断言
- [ ] 新增 test：`mock-data.js` 新增字段（`weatherFactors`/`suitability`/`severeWeatherAlert`/`dateAnchor`/`applicableCrops`/`salesCopy`）均以 `null` 占位
- **验收标准**：REQ-S2F-UBI-035/036、design D-12/R-09
- **依赖**：1.4

### 9.11 纯函数与存储行为断言
- [ ] 新增 test：`require('../assets/js/timeline-logic.js')` 断言 `calcCountdown` 未来/今天/过期/非法四态与天数、`getWeatherIcon`/`getSuitability`/`getSevereAlert` 已知值映射 + 未识别兜底 + null 返回 null、`normalizeFactors` 字符串/数组/null 归一化；`require('../assets/js/history-store.js')` 以内存存储注入断言上限/去重/容错
- **验收标准**：REQ-S2F-UBI-040、design 2.5 行为断言层
- **依赖**：1.1、1.2

### 9.12 既有断言完整性核对
- [ ] 核对 `frontend/tests/pages.test.js` 既有 22 项断言原文一字未改、未注释、未弱化
- **验收标准**：REQ-S2F-UBI-039、spec 5.12 禁止项、design 2.5 回归红线
- **依赖**：9.1~9.11

## 10. 回归验证与收尾

### 10.1 运行完整测试套件
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 22 项断言 + 新增断言全部通过、无失败
- **验收标准**：REQ-S2F-UBI-039/UBI-040、REQ 4.5.1
- **依赖**：全部代码与测试任务（1~9）

### 10.2 改动范围核对
- [ ] 通过 `git diff --stat` 确认改动仅落在 `frontend/`（含新增 `assets/js/timeline-logic.js`、`history-store.js`、`chart-client.js`），无后端 / `contract/` / `data/` / `deploy/` / `docs/` 变更
- **验收标准**：spec 8.1/8.4、REQ 4.5.4
- **依赖**：10.1

### 10.3 三档宽度人工核对
- [ ] 在 375px / 768px / 1024px 三档宽度打开 `index.html` 与 `timeline.html`，确认无横向滚动、核心内容完整可见（未以隐藏内容换无滚动）、无元素变形
- **验收标准**：REQ-S2F-UBI-016/017、禁止项
- **依赖**：5.1、5.2

### 10.4 降级与安全行为核验
- [ ] 核验：接口失败/超时（>20s）无白屏无未捕获错误；天气字段全 null 显示中性提示不臆造；localStorage 禁用主流程正常；用户可控文本使用 `textContent`/安全方式渲染、请求参数经 `URLSearchParams` 编码
- **验收标准**：REQ-S2F-OPT-060、REQ 4.2.1/4.2.6、REQ 4.3.1/4.3.3
- **依赖**：3.6、7.2、10.1

## 11. 交付物：服务器运维分析报告（文档，不改服务器）

### 11.1 运维分析报告成文
- [ ] 生成运维分析报告文档 `.codeartsdoer/specs/stage2_frontend/ops-security-report.md`（第 7.1 节内容），以"现状分析 + 建议修改项清单"形式交付，**不落地任何服务器配置变更**；覆盖：后端服务运行形态与 systemd 注册现状（REQ-041）、开机自启与崩溃自愈建议（`Restart=on-failure`，REQ-042）、日志轮转策略（logrotate/journald，REQ-043）、健康检查对象与告警条件（`/health`、8080、80 端口，REQ-044）、上游依赖（天气源/大模型/识别服务）失效表现与检测建议（REQ-045）、时间线接口耗时约 61s 超前端 20s 阈值的风险与方向性建议（REQ-046）
- **涉及文件**：`.codeartsdoer/specs/stage2_frontend/ops-security-report.md`（新增文档）
- **验收标准**：REQ-S2F-UBI-041/042/043/044/045/046、spec 8.3
- **依赖**：无，可与编码任务并行

## 12. 交付物：安全分析报告（文档，仅建议）

### 12.1 安全分析报告成文
- [ ] 在 `ops-security-report.md` 第 7.2 节补齐安全分析：API Key/凭据明文泄露风险与整改建议（REQ-047）、nginx 配置安全项（路径收敛/隐藏版本/限制方法与请求体/安全响应头，REQ-048）、目录权限最小化（REQ-049）、敏感信息暴露面（错误堆栈/目录列表/备份文件，REQ-050）、CORS 收紧建议（REQ-051）、前端输入校验清单与后端校验建议（crop/sowDate/lat/lon 与图片类型大小，REQ-052）、传输安全 HTTP→HTTPS 建议（REQ-053）；**报告中不得回显任何真实密钥/口令**，不得将结论转为实际服务器配置变更
- **涉及文件**：`.codeartsdoer/specs/stage2_frontend/ops-security-report.md`
- **验收标准**：REQ-S2F-UBI-047~053、spec 7.2 禁止项、spec 8.3
- **依赖**：11.1

## 13. 明确排除项（不纳入本次任务）

- [ ] 后端 Java 服务（`/opt/crop-calendar`、8080）、`crop-protocols.json`、`market-festivals.json`、阈值表、`scoreNode` 算法——**不修改**
- [ ] nginx / systemd / logrotate / 防火墙 / HTTPS 证书等服务器配置——**仅分析建议，不落地**
- [ ] M16（PPT 更新含实际截图）——**排除**，属文档交付任务
- [ ] 框架 / 构建工具 / 第三方库（含 Chart.js、ECharts、D3）——**禁止引入**
- [ ] `contract/`、`data/`、`deploy/`、`docs/` 等非前端模块——**不修改**