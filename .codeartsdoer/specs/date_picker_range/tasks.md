# 前端日期选择器可选范围调整 — 编码任务清单（date_picker_range）

> **本任务规划基于 `date_picker_range/spec.md`（20 条 EARS 需求 REQ-DPR-*-001~020）与 `date_picker_range/design.md`（12 条关键决策 D-DPR-01~12、改动点清单、新增/修改文件清单、风险与边界情况）生成。**
>
> **范围红线**：仅改 `frontend/` 目录下 3 个文件（`index.html` / `common.css` / `pages.test.js`）；不引框架 / 构建工具 / 第三方日期选择库；不改后端（Java/8080、`crop-protocols.json`、`market-festivals.json`、`scoreNode`）、`contract/`、`data/`、`deploy/`、`docs/`；不改 `timeline.html` / `crop-map.js` / `mock-data.js` / `timeline-logic.js` / `history-store.js` / `chart-client.js` / `photo-capture.js` / `timeline.css`。
>
> **测试红线**：`node --test frontend/tests/pages.test.js` 既有 **22 项**断言一字不改、必须全绿，仅允许新增。
>
> **既有契约保留**：`fillYears` / `fillMonths` / `fillDays` / `clampDay` / `validate` 函数名与无入参契约不变；`sowDate` 参数命名不变；`buildTimelineQuery` 提交链路不变；`RANGE_BACK_DAYS` 常量名保留（弃用注释）；`播种日期不能晚于今天` / `播种日期不能早于两年前` 文案字符串保留（既有断言依赖）。
>
> **与 stage2_frontend 关键决策的关系**：本方案不涉及 D-02（POST 隔离，本次无 POST）、不违反 D-06（前端不实现阈值计算，本次为日期边界计算非天气阈值）、不涉及 D-12（mock 不造假，本次不改 mock）。
>
> **执行顺序总览**：组 1（常量与边界函数，基础）→ 组 2（填充函数改造，依赖组 1）→ 组 3（校验与提示，依赖组 1）→ 组 4（CSS 灰度与提示样式，可与组 2/3 并行）→ 组 5（测试断言，依赖组 1~4）→ 组 6（回归验证，依赖全部）。

## 1. 具名常量与日期边界计算函数

> 对应 design D-DPR-01~05、D-DPR-12；关联需求 REQ-DPR-UBI-001~004、UBI-012、UBI-014、OPT-013、OPT-015、UBI-017、UBI-020。
> 本组为后续所有改造的基础，须最先完成。

### 1.1 新增具名常量定义
- [ ] 在 `frontend/pages/index.html` 内联脚本顶部常量区（约第 97-98 行 `RANGE_BACK_DAYS` 声明附近），新增 4 个具名常量：`var MIN_YEAR_OFFSET = 2;`（最小可选日期年份偏移）、`var MAX_FUTURE_DAYS = 10;`（最大可选日期天数偏移，固定不浮动）、`var RANGE_HINT_TEXT = '请选择两年前至今天后 10 天的日期';`（行内提示文案 + option title）、`var RANGE_HINT_TITLE = '可选范围：两年前至今天后 10 天';`（`#date-picker` 容器 title/aria-label）；常量须集中定义于同一区域，不得分散多处
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-012；代码审查常量集中定义、无多处硬编码；`index.html` 含 `MIN_YEAR_OFFSET` / `MAX_FUTURE_DAYS` / `RANGE_HINT_TEXT` / `RANGE_HINT_TITLE` 字符串
- **依赖**：无

### 1.2 保留 RANGE_BACK_DAYS 并标注弃用
- [ ] 保留 `frontend/pages/index.html` 第 97 行 `var RANGE_BACK_DAYS = 730;` 声明，追加弃用注释 `/* @deprecated 改用 MIN_YEAR_OFFSET，保留以兼容既有测试断言 */`；不删除、不改值、不改变其字符串存在性
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-017；既有断言第 8 项 `assert.ok(indexHtml.includes('RANGE_BACK_DAYS'))` 保持通过；design D-DPR-05
- **依赖**：1.1

### 1.3 新增 calcMinSelectableDate 函数（含闰年回退）
- [ ] 在 `frontend/pages/index.html` 内联脚本中（`getMinDate` 函数附近）新增 `function calcMinSelectableDate(today)`：`today` 缺省时取 `new Date()`；按日历年减 `MIN_YEAR_OFFSET` 构造候选 `new Date(today.getFullYear() - MIN_YEAR_OFFSET, today.getMonth(), today.getDate())`；检测 `result.getMonth() !== today.getMonth()`（月份溢出，如非闰年 2 月 29 日自动溢出到 3 月 1 日），溢出则回退到该月最后一天 `new Date(today.getFullYear() - MIN_YEAR_OFFSET, today.getMonth() + 1, 0)`；异常回落：`isNaN(today.getFullYear())` 时回落到既有按天减 `RANGE_BACK_DAYS` 逻辑（`var d = new Date(); d.setDate(d.getDate() - RANGE_BACK_DAYS); return d;`），不抛未捕获错误
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-002、OPT-013、UBI-014、OPT-015；design D-DPR-02、D-DPR-03、D-DPR-12；`calcMinSelectableDate(new Date(2024, 1, 29))` → `2022-02-28`（闰年回退）；`calcMinSelectableDate(new Date(2026, 9, 17))` → `2024-10-17`（不回退）；`calcMinSelectableDate(new Date(2026, 2, 1))` → `2024-03-01`（不回退）；异常输入不抛错
- **依赖**：1.1、1.2

### 1.4 新增 getMaxDate 函数
- [ ] 在 `frontend/pages/index.html` 内联脚本中新增 `function getMaxDate()`：`var d = new Date(); d.setDate(d.getDate() + MAX_FUTURE_DAYS); return d;`；使用 `setDate` 自动处理跨月跨年（如今天 2026-12-28 + 10 → 2027-01-07）
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-003、UBI-004；design D-DPR-04；今天 2026-10-17 → `2026-10-27`；今天 2026-12-28 → `2027-01-07`（跨年跨月）；偏移量固定为 10 天不浮动
- **依赖**：1.1

### 1.5 修改 getMinDate 为兼容包装
- [ ] 修改 `frontend/pages/index.html` 第 106 行 `getMinDate()` 函数：内部改为 `return calcMinSelectableDate(new Date());`；保留函数名与无入参契约不变；不再直接引用 `RANGE_BACK_DAYS` 做减法（`RANGE_BACK_DAYS` 仅由 `calcMinSelectableDate` 异常回落分支保留引用）
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-002、UBI-020；design D-DPR-05；`getMinDate()` 调用契约保留；返回值改为按日历年减 2 + 闰年回退的结果
- **依赖**：1.3

## 2. 三联动填充函数改造（maxD 改为 getMaxDate + disabled option title）

> 对应 design D-DPR-07、改动点 2.4.1 第 5-7 行；关联需求 REQ-DPR-UBI-003、UBI-005、STA-006、STA-007、UNW-008、STA-009、UBI-020。
> 本组将三联动渲染的上界从"今天"放宽到"今天 + 10 天"，并为 disabled option 追加提示。

### 2.1 fillYears 改用 getMaxDate
- [ ] 修改 `frontend/pages/index.html` 第 110 行 `fillYears()` 函数：`var minD = getMinDate(), maxD = new Date();` 改为 `var minD = getMinDate(), maxD = getMaxDate();`；其余逻辑不变；确认跨年场景（今天 2026-12-28 → maxD 2027-01-07 → `fillYears` 循环 `y <= maxD.getFullYear()` 自动包含 2027 年）
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-003、UBI-020；design 改动点 2.4.1；跨年时年份下拉包含最大边界所在年
- **依赖**：1.4、1.5

### 2.2 fillMonths 改用 getMaxDate + disabled option title
- [ ] 修改 `frontend/pages/index.html` 第 120 行 `fillMonths()` 函数：`maxD = new Date()` 改为 `maxD = getMaxDate()`；在 disabled option 赋值后追加 `if (o.disabled) o.title = RANGE_HINT_TEXT;`（option hover 提示，浏览器支持不一，为辅助通道）；其余逻辑不变
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-003、STA-009、UBI-020；design 改动点 2.4.1、D-DPR-07；范围外月份置灰且附 title 提示
- **依赖**：1.1、1.4、1.5

### 2.3 fillDays 改用 getMaxDate + disabled option title
- [ ] 修改 `frontend/pages/index.html` 第 133 行 `fillDays()` 函数：`maxD = new Date()` 改为 `maxD = getMaxDate()`；在 disabled option 赋值后追加 `if (o.disabled) o.title = RANGE_HINT_TEXT;`；确认闭区间语义保留（边界当天 `d === maxD.getDate()` 不被 disabled，满足 REQ-DPR-UBI-002/003 闭区间）；确认今天本身可选（今天 `d === today.getDate() < maxD.getDate()`，不被 disabled，满足 REQ-DPR-UBI-005）
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-003、UBI-005、STA-006、STA-007、STA-009、UBI-020；design 改动点 2.4.1、D-DPR-07；范围外日期置灰且附 title 提示；边界当天可选；今天可选
- **依赖**：1.1、1.4、1.5

### 2.4 clampDay 保持不变
- [ ] 确认 `frontend/pages/index.html` 第 146-151 行 `clampDay()` 函数不改动；函数名与无入参契约保留；本次变更不涉及当月天数回退逻辑
- **涉及文件**：`frontend/pages/index.html`（保持）
- **验收标准**：REQ-DPR-UBI-020；design 改动点 2.4.1 明确不改动
- **依赖**：无（复核项）

## 3. 校验逻辑与提示文案改造

> 对应 design D-DPR-06、D-DPR-08、D-DPR-09、改动点 2.4.1 第 8-11 行；关联需求 REQ-DPR-UBI-003、UBI-005、UBI-011、STA-009、UBI-010、UBI-017。
> 本组调整 `validate()` 最大边界与文案，并新增行内提示文案 DOM。

### 3.1 validate 最大边界改为 getMaxDate
- [ ] 修改 `frontend/pages/index.html` 第 261 行 `validate()` 函数第 2 分支：`sowingInput.value > todayStr()` 改为 `sowingInput.value > dateToStr(getMaxDate())`；文案 `dateErr.textContent = '播种日期不能晚于今天'` 改为 `dateErr.textContent = '播种日期不能晚于今天后 10 天'`；第 3 分支"播种日期不能早于两年前"保留不变；第 1 分支"请选择播种日期"保留不变
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-UBI-003、UBI-005、UBI-011；design D-DPR-06、改动点 2.4.1；今天之后 10 天内日期校验通过；今天之后 11 天及以上日期校验失败并提示"播种日期不能晚于今天后 10 天"
- **依赖**：1.4

### 3.2 保留 #error-sowingDate 初始文本
- [ ] 确认 `frontend/pages/index.html` 第 43 行 `<div class="error" id="error-sowingDate">播种日期不能晚于今天</div>` 初始文本保留不变（满足既有断言第 2 项 `assert.ok(indexHtml.includes('播种日期不能晚于今天'))`）；`validate()` 校验时动态替换 `dateErr.textContent` 为准确文案（D-DPR-06）
- **涉及文件**：`frontend/pages/index.html`（保持初始文本）
- **验收标准**：REQ-DPR-UBI-017、UBI-011；design D-DPR-06；既有断言第 2 项保持通过；运行时文案与实际范围一致
- **依赖**：3.1

### 3.3 新增 .date-range-hint 行内提示文案元素
- [ ] 在 `frontend/pages/index.html` 第 35-44 行 `#field-sowing-date` 内（`#date-picker` 容器之后、`#error-sowingDate` 之前或之后），新增 `<p class="date-range-hint" id="date-range-hint">请选择两年前至今天后 10 天的日期</p>`；文案为静态具名常量语义（HTML 中可直接写该字符串，与 `RANGE_HINT_TEXT` 一致）；始终可见，作为主通道兼容移动端无 hover
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-STA-009、UBI-010、UBI-011；design D-DPR-08、改动点 2.4.1；`index.html` 含 `.date-range-hint` 与"两年前"与"10 天"文案
- **依赖**：1.1

### 3.4 #date-picker 容器追加 title 与 aria-label
- [ ] 修改 `frontend/pages/index.html` 第 37 行 `<div class="date-picker" id="date-picker">`：追加 `title` 与 `aria-label` 属性，值可为静态字符串"可选范围：两年前至今天后 10 天"（或由 JS 在初始化时动态设置 `document.getElementById('date-picker').title = RANGE_HINT_TITLE`）；hover 时浏览器原生 tooltip 显示范围提示
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR-STA-009；design D-DPR-07、D-DPR-08；`#date-picker` 含 title/aria-label 范围提示
- **依赖**：1.1

### 3.5 提示文案 XSS 防护核对
- [ ] 核对 `RANGE_HINT_TEXT` / `RANGE_HINT_TITLE` 为静态字符串常量，不拼接用户可控输入；`.date-range-hint` 文本通过 HTML 静态写入或 `textContent` 赋值，不使用 `innerHTML` 拼接用户输入
- **涉及文件**：`frontend/pages/index.html`
- **验收标准**：REQ-DPR 4.3.2 安全性；design D-DPR-09；无 XSS 风险
- **依赖**：1.1、3.3

## 4. CSS 灰度禁用增强与提示样式

> 对应 design 改动点 2.4.2；关联需求 REQ-DPR-STA-007、UBI-010。
> 本组可与组 2/3 并行，增强既有灰度样式并新增提示文案样式。

### 4.1 增强 .date-picker option:disabled 灰度样式
- [ ] 修改 `frontend/assets/css/common.css` 第 335-338 行 `.date-picker option:disabled` 规则：追加 `cursor: not-allowed;`；保留既有 `color: var(--muted);` 与 `opacity: 0.5;`；不删除既有属性
- **涉及文件**：`frontend/assets/css/common.css`
- **验收标准**：REQ-DPR-STA-007；design 改动点 2.4.2；范围外 option 灰度 + not-allowed 光标
- **依赖**：无（可并行）

### 4.2 新增 .date-range-hint 样式
- [ ] 在 `frontend/assets/css/common.css` 中 `.date-picker` 相关规则之后（约第 343 行），新增 `.date-range-hint { font-size: 13px; color: var(--muted); margin-top: 6px; line-height: 1.4; }`；使用既有 CSS 变量 `--muted` 保持视觉一致；小字号 + muted 色 + 顶部间距，不撑破布局
- **涉及文件**：`frontend/assets/css/common.css`
- **验收标准**：REQ-DPR-UBI-010；design 改动点 2.4.2；`.date-range-hint` 样式存在且视觉协调
- **依赖**：无（可并行）

### 4.3 防溢出约束保持核对
- [ ] 确认 `frontend/assets/css/common.css` 中既有 `overflow-x: hidden` 与 `max-width: 100%` 约束未被本次改动破坏（既有断言第 3 项依赖）；新增 `.date-range-hint` 样式不引入横向滚动
- **涉及文件**：`frontend/assets/css/common.css`（复核）
- **验收标准**：REQ-DPR-UBI-017；既有断言第 3 项保持通过
- **依赖**：4.2

## 5. 测试断言补充（既有 22 项保持全绿）

> 对应 design D-DPR-11、改动点 2.4.3、测试策略 2.7；关联需求 REQ-DPR-UBI-017 及全部新需求。
> 本组仅新增断言，既有 22 项断言一字不改、不注释、不弱化。

### 5.1 既有 22 项断言完整性核对
- [ ] 核对 `frontend/tests/pages.test.js` 既有 22 项断言原文一字未改、未注释、未弱化；特别确认第 2 项（`setDefaultValues` / `播种日期不能晚于今天`）与第 8 项（`date-picker` / `date-year/month/day` / `RANGE_BACK_DAYS` / `播种日期不能早于两年前` / `!type="date"` / `id="sowingDate"`）保持原样
- **涉及文件**：`frontend/tests/pages.test.js`（复核，不改动既有断言）
- **验收标准**：REQ-DPR-UBI-017；design D-DPR-11、测试策略 2.7 回归红线
- **依赖**：无（前置复核）

### 5.2 新增具名常量与最大边界函数断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：具名常量与最大边界函数'`：断言 `indexHtml.includes('MIN_YEAR_OFFSET')` / `indexHtml.includes('MAX_FUTURE_DAYS')` / `indexHtml.includes('RANGE_HINT_TEXT')` / `indexHtml.includes('RANGE_HINT_TITLE')` / `indexHtml.includes('getMaxDate')` / `indexHtml.includes('calcMinSelectableDate')`
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-UBI-012、UBI-003；design 改动点 2.4.3
- **依赖**：1.1、1.3、1.4

### 5.3 新增提示文案与实际范围一致断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：提示文案与实际范围一致'`：断言 `indexHtml.includes('两年前')` / `indexHtml.includes('10 天')` / `indexHtml.includes('date-range-hint')`；断言 `commonCss.includes('.date-range-hint')`
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-STA-009、UBI-010、UBI-011；design 改动点 2.4.3
- **依赖**：3.3、4.2

### 5.4 新增最大边界文案更新断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：最大边界文案更新'`：断言 `indexHtml.includes('播种日期不能晚于今天后 10 天')`（validate 中新文案）；同时确认 `indexHtml.includes('播种日期不能晚于今天')` 仍存在（HTML 初始文本，既有断言第 2 项兼容）
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-UBI-003、UBI-011、UBI-017；design D-DPR-06、改动点 2.4.3
- **依赖**：3.1、3.2

### 5.5 新增灰度禁用样式增强断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：灰度禁用样式增强'`：断言 `commonCss.includes('cursor: not-allowed')`；断言 `.date-picker option:disabled` 规则仍含 `opacity: 0.5`（既有灰度保留）
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-STA-007；design 改动点 2.4.2
- **依赖**：4.1

### 5.6 新增闰年 2 月 29 日回退行为断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：闰年 2 月 29 日回退'`：在测试中独立复现 `calcMinSelectableDate` 算法（按日历年减 2 + 月份溢出检测 + 回退到月末），断言四态正确性：(1) today=`new Date(2024, 1, 29)` → minDate=`2022-02-28`（闰年回退）；(2) today=`new Date(2026, 9, 17)` → minDate=`2024-10-17`（不回退）；(3) today=`new Date(2026, 2, 1)` → minDate=`2024-03-01`（不回退）；(4) today=`new Date(2026, 11, 28)` → minDate=`2024-12-28`（不回退）；同时断言 `index.html` 含关键代码片段 `getFullYear() - MIN_YEAR_OFFSET` 与 `getMonth() !==`（验证实现存在）
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-OPT-013、UBI-014、OPT-015；design D-DPR-03、测试策略 2.7 行为断言层、R-DPR-11
- **依赖**：1.3

### 5.7 新增最大边界跨年行为断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：最大边界跨年跨月'`：在测试中构造 `var d = new Date(2026, 11, 28); d.setDate(d.getDate() + 10);` 断言 `d.getFullYear() === 2027 && d.getMonth() === 0 && d.getDate() === 7`（跨年跨月正确）；同时断言 `index.html` 含 `MAX_FUTURE_DAYS` 关键代码片段
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-UBI-003；design D-DPR-04、R-DPR-04
- **依赖**：1.4

### 5.8 新增既有断言兼容性复核断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test `'日期范围：既有断言兼容性复核'`：复核 `indexHtml.includes('RANGE_BACK_DAYS')` / `indexHtml.includes('播种日期不能晚于今天')` / `indexHtml.includes('播种日期不能早于两年前')` / `indexHtml.includes('class="date-picker"')` / `indexHtml.includes('id="date-year"')` / `indexHtml.includes('id="date-month"')` / `indexHtml.includes('id="date-day"')` / `indexHtml.includes('id="sowingDate"')` / `!indexHtml.includes('type="date"')` 均成立（与既有断言第 2/8 项一致，额外复核）
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-UBI-017；design 改动点 2.4.3
- **依赖**：1.2、3.2

## 6. 回归验证与收尾

> 对应 design 测试策略 2.7 回归红线、风险与边界情况 2.8；关联需求 REQ-DPR-UBI-017、UBI-018、UBI-019、UBI-020。
> 本组为最终验证，确保全部改动正确且不破坏既有功能。

### 6.1 运行完整测试套件
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 22 项断言 + 本次新增断言（5.2~5.8 共 7 项）全部通过、无失败；如有失败，定位失败项并修复至全绿
- **涉及文件**：`frontend/tests/pages.test.js`
- **验收标准**：REQ-DPR-UBI-017；design 测试策略 2.7 回归红线；全部断言通过
- **依赖**：全部代码与测试任务（1~5）

### 6.2 改动范围核对
- [ ] 通过 `git diff --stat` 确认改动仅落在 `frontend/pages/index.html`、`frontend/assets/css/common.css`、`frontend/tests/pages.test.js` 三个文件；无后端 / `contract/` / `data/` / `deploy/` / `docs/` 变更；无 `timeline.html` / `crop-map.js` / `mock-data.js` / `timeline-logic.js` / `history-store.js` / `chart-client.js` / `photo-capture.js` / `timeline.css` 变更；无新增独立模块文件
- **涉及文件**：`frontend/`（全量审查）
- **验收标准**：REQ-DPR-UBI-018、UBI-019；design D-DPR-10、改动点 2.4.4、新增/修改文件清单 2.5
- **依赖**：6.1

### 6.3 既有契约保留核对
- [ ] 核对 `frontend/pages/index.html` 中 `fillYears` / `fillMonths` / `fillDays` / `clampDay` / `validate` 函数名与无入参契约保留；`sowDate` 参数命名不变；`buildTimelineQuery` 提交链路不变；`RANGE_BACK_DAYS` 常量名保留；`播种日期不能晚于今天` / `播种日期不能早于两年前` 文案字符串保留
- **涉及文件**：`frontend/pages/index.html`（复核）
- **验收标准**：REQ-DPR-UBI-020；design 既有契约保留约束
- **依赖**：6.1

### 6.4 闰年回退与跨年边界人工核验
- [ ] 人工核验：构造今天为 2024-02-29（闰年）场景，确认最小可选日期为 2022-02-28（回退正确）；构造今天为 2026-12-28 场景，确认最大可选日期为 2027-01-07（跨年跨月正确）；构造今天为 2026-10-17 场景，确认可选范围为 2024-10-17 ~ 2026-10-27（闭区间两端可选）；构造浏览器本地日期异常场景，确认回落到既有行为不抛错不白屏
- **涉及文件**：`frontend/pages/index.html`（人工核验）
- **验收标准**：REQ-DPR-UBI-002、UBI-003、UBI-005、OPT-013、UBI-014；design D-DPR-03、D-DPR-04、D-DPR-12、R-DPR-03、R-DPR-04、R-DPR-07
- **依赖**：6.1

### 6.5 置灰禁用与提示文案交互核验
- [ ] 人工核验：打开 `frontend/pages/index.html` 日期选择器，确认范围外日期置灰禁用、不可被鼠标/键盘选中；悬停/聚焦 `#date-picker` 容器时显示 tooltip 范围提示；`.date-range-hint` 行内文案始终可见（兼容移动端无 hover）；`validate()` 提交今天之后 11 天日期时提示"播种日期不能晚于今天后 10 天"；提交两年前前一天日期时提示"播种日期不能早于两年前"
- **涉及文件**：`frontend/pages/index.html`、`frontend/assets/css/common.css`（人工核验）
- **验收标准**：REQ-DPR-STA-006、STA-007、UNW-008、STA-009、UBI-010、UBI-011；design D-DPR-07、D-DPR-08、R-DPR-08、R-DPR-09
- **依赖**：6.1

### 6.6 降级与安全行为核验
- [ ] 核验：浏览器本地日期异常时 `calcMinSelectableDate` 回落到既有 `getMinDate()` 按天减 730 行为，不抛未捕获错误、不白屏；提示文案为静态具名常量、不拼接用户可控输入（防 XSS）；`sowDate` 参数经既有表单提交链路以 URL 查询参数传递至 timeline.html，提交方式与参数命名不变
- **涉及文件**：`frontend/pages/index.html`（核验）
- **验收标准**：REQ-DPR 4.2.2 可靠性、4.3.2 安全性、UBI-020；design D-DPR-09、D-DPR-12、R-DPR-07
- **依赖**：6.1

## 7. 明确排除项（不纳入本次任务）

- [ ] 后端 Java 服务（`/opt/crop-calendar`、8080）、`crop-protocols.json`、`market-festivals.json`、阈值表、`scoreNode` 算法——**不修改**（REQ-DPR-UBI-016）
- [ ] 后端 `/api/timeline` 的 `sowDate` 校验逻辑同步——**不纳入本次范围**，属后续后端任务（REQ-DPR-UBI-016、spec 5.5.3 异常场景 1）
- [ ] `frontend/pages/timeline.html`——**不修改**（仅消费 `sowDate` 参数，不涉及日期选择器逻辑）
- [ ] `frontend/assets/js/crop-map.js` / `timeline-logic.js` / `history-store.js` / `chart-client.js`——**不修改**（与日期选择器无关）
- [ ] `frontend/assets/mock/mock-data.js`——**不修改**（与日期选择器无关；D-12 mock 不造假）
- [ ] `frontend/assets/css/timeline.css`——**不修改**（日期选择器样式在 common.css）
- [ ] `frontend/pages/photo-capture.js`——**不修改**（与日期选择器无关）
- [ ] `contract/` / `data/` / `deploy/` / `docs/` 等非前端模块——**不修改**（REQ-DPR-UBI-019）
- [ ] 框架 / 构建工具 / 第三方库（含任何日期选择库如 Pikaday、flatpickr）——**禁止引入**（REQ-DPR-UBI-018）
- [ ] 既有 22 项测试断言的删除 / 注释 / 弱化——**禁止**，仅允许新增（REQ-DPR-UBI-017）
- [ ] 新增独立模块文件（如 `date-range.js`）——**不新增**，日期边界计算函数内嵌于 index.html 内联脚本，符合纯原生 JS 无框架无构建工具约束（design 2.5 新增/修改文件清单）