# T01 主页动画效果修改优化 · 编码任务规划（t01_enhance_animation）

> 本任务规划基于 `t01_enhance_animation/spec.md`（REQ-TEA-UBI/EVT/STA/OPT/UNW-001~040，共 40 条 EARS 需求）与 `design.md`（D-TEA-01~21 共 21 条关键决策、C-01~C-47 共 47 项改动点、R-01~R-18 共 18 条风险）生成。
>
> **范围红线**：仅修改/新增 `frontend/` 目录下文件；不触碰后端（Java/8080）、nginx、shared/data/deploy/docs、timeline.html、timeline-logic.js、crop-map.js、voice-input.*、mock-data.js。
>
> **改动文件清单**：
> - 新增：`frontend/assets/js/time-period.js`（C-09~C-16）
> - 修改：`frontend/pages/index.html`（C-01~C-06）
> - 修改：`frontend/assets/js/weather-visual.js`（C-17~C-19，增量扩展）
> - 修改：`frontend/assets/css/common.css`（C-22~C-31，末尾追加）
> - 修改：`frontend/tests/pages.test.js`（C-33~C-46，末尾追加）
>
> **任务组依赖顺序**：组1（time-period.js 基础模块）→ 组2（weather-visual.js 扩展，依赖组1接口）→ 组3（common.css 样式，独立可并行）→ 组4（index.html DOM+逻辑，依赖组1脚本引入+组3样式）→ 组5（测试断言，依赖组1~4全部完成）→ 组6（回归验证，依赖组1~5全部完成）。

---

## 1. 新建 time-period.js 时段判定模块

> 对应改动点 C-09~C-16；关联需求 REQ-TEA-UBI-012~022/UNW-016/029/OPT-018/EVT-014。
> 本组为整个功能的基础模块，无前置依赖，须最先完成。

### 1.1 创建 time-period.js 文件骨架
- [ ] 在 `frontend/assets/js/time-period.js` 新建文件，采用 IIFE 包裹（与既有 `weather-visual.js`/`timeline-logic.js` 风格一致），末尾通过 `window.TimePeriod` 暴露 API 对象，并通过 `module.exports` 暴露纯函数供测试 require
- [ ] 验收：文件存在；含 `(function () {` 开头与 `})();` 结尾；含 `window.TimePeriod =` 与 `module.exports`

### 1.2 定义时段枚举与标签常量
- [ ] 在 `time-period.js` 模块顶部定义具名常量 `TIME_PERIODS`（数组，含 7 个枚举值：`morning`/`forenoon`/`noon`/`afternoon`/`evening`/`night`/`default`）
- [ ] 定义 `PERIOD_LABEL`（时段→中文标签映射：早晨/上午/中午/下午/晚上/深夜/默认）
- [ ] 验收：`TIME_PERIODS` 含全部 7 个枚举值；`PERIOD_LABEL` 含 7 个中文标签；满足 REQ-TEA 4.4.2 新增时段仅需增一行

### 1.3 定义默认时段划分常量
- [ ] 定义 `DEFAULT_PERIOD_SCHEME` 具名常量对象，含 6 个时段固定时间点：早晨 06:00~08:00、上午 08:00~11:00、中午 11:00~14:00、下午 14:00~17:00、晚上 17:00~22:00、深夜 22:00~次日06:00；每段含 `startMin`/`endMin`/`isDynamic` 字段（分钟数：360/480/660/840/1020/1320）
- [ ] 验收：6 个时段覆盖全天 00:00~24:00 无遗漏无重叠；`isDynamic` 均为 `false`；满足 REQ-TEA-UBI-016/017

### 1.4 实现 parseSunTime 日出日落解析函数
- [ ] 实现 `parseSunTime(isoStr)` 函数：解析 ISO 8601 时间字符串（如 `2026-10-23T06:12`）提取时分转为分钟数（hour*60+minute）；解析失败/输入为 null/undefined/非法格式时返回 null，不抛异常
- [ ] 验收：`parseSunTime('2026-10-23T06:12')` 返回 372；`parseSunTime(null)` 返回 null；`parseSunTime('invalid')` 返回 null；满足 REQ-TEA-UBI-013

### 1.5 实现 judgeTimePeriod 时段判定函数
- [ ] 实现 `judgeTimePeriod(now, sunrise, sunset)` 函数：依据本地时间（Date 实例）与日出日落（ISO 8601 字符串或 null）判定当前时段
- [ ] sunrise/sunset 非 null 时按动态边界判定：早晨(日出~08:00)、上午(08:00~11:00)、中午(11:00~14:00)、下午(14:00~日落前2h)、晚上(日落~22:00)、深夜(22:00~次日日出)
- [ ] sunrise/sunset 为 null 时使用 `DEFAULT_PERIOD_SCHEME` 固定时间点判定
- [ ] 高纬度极昼/极夜处理：日出晚于 08:00 时早晨为空合并至上午（上午起始改为日出）；日落早于 16:00 时下午为空合并至中午（中午结束改为日落前2h）；日出日落缺失用默认划分
- [ ] 验收：`judgeTimePeriod(new Date(2026,9,23,9,30), '2026-10-23T06:00', '2026-10-23T18:00')` 返回 `forenoon`；`judgeTimePeriod(new Date(2026,9,23,19,0), null, '2026-10-23T18:00')` 返回 `evening`；`judgeTimePeriod(new Date(2026,9,23,23,0), null, null)` 返回 `night`；任一时刻唯一归属一个时段；不抛异常；满足 REQ-TEA-UBI-012/015/018

### 1.6 实现 applyTimePeriod 属性设置函数
- [ ] 实现 `applyTimePeriod(period)` 函数：在 `main.page--home` DOM 节点上设置 `data-timeperiod` 属性；period 非枚举值时设为 `default`；节点缺失时静默降级不抛错
- [ ] 验收：调用后 `document.querySelector('.page--home').dataset.timeperiod` 等于传入的合法 period；非法 period 设为 `default`；节点不存在时不报错；满足 REQ-TEA-UBI-022

### 1.7 实现 applySunData 日出日落接入函数
- [ ] 实现 `applySunData(sunrise, sunset)` 函数：接收 weather-visual.js 传入的日出日落时间（ISO 8601 字符串或 null），读取本地时间 `new Date()`，调用 `judgeTimePeriod` 判定时段，调用 `applyTimePeriod` 设置属性；sunrise/sunset 为 null 时使用默认时段划分；内部 try-catch，异常转 `fallbackPeriod()`
- [ ] 验收：传入有效 sunrise/sunset 后 `data-timeperiod` 设置为对应时段；传入 null/null 后按默认划分判定；异常时不抛未捕获错误转降级；满足 REQ-TEA-EVT-014/UBI-015

### 1.8 实现 fallbackPeriod 降级函数
- [ ] 实现 `fallbackPeriod()` 函数：调用 `applyTimePeriod('default')` 设置 `data-timeperiod="default"`；内部 try-catch 不抛异常
- [ ] 验收：调用后 `data-timeperiod="default"`；不抛未捕获错误；满足 REQ-TEA-UNW-016/029

### 1.9 实现 init 入口编排函数
- [ ] 实现 `init()` 函数：页面加载入口，编排时段判定流程；若 weather-visual.js 已先执行并调用 applySunData 则无需重复；若 weather-visual.js 不可用则独立用默认时段判定（读取本地时间 + judgeTimePeriod(now, null, null) + applyTimePeriod）；内部 catch 所有异常转 `fallbackPeriod()`
- [ ] 验收：调用 init 后 `data-timeperiod` 已设置；异常时不抛错转降级；满足 REQ-TEA-UBI-012~031

### 1.10 暴露 API 与模块导出
- [ ] 在 IIFE 末尾通过 `window.TimePeriod` 暴露 `{ init, judgeTimePeriod, parseSunTime, applyTimePeriod, applySunData, fallbackPeriod }`；通过 `module.exports` 暴露纯函数 `{ judgeTimePeriod, parseSunTime, DEFAULT_PERIOD_SCHEME, TIME_PERIODS }` 供测试 require
- [ ] 验收：`window.TimePeriod` 含 6 个函数；`module.exports` 含 4 个成员；无 import/require 第三方库语句；满足 REQ-TEA-UBI-034

---

## 2. 扩展 weather-visual.js 日出日落请求与时段联动

> 对应改动点 C-17~C-19；关联需求 REQ-TEA-EVT-014/UBI-013/038。
> 前置依赖：组1 完成（applySunData 接口就绪）。
> 约束：WMO_MAP/normalizeWeather/inferSeason/fallback/getLocation 不触碰（C-20/C-21）。

### 2.1 fetchWeather 追加 daily=sunrise,sunset 参数
- [ ] 在 `frontend/assets/js/weather-visual.js` 第 57-59 行 `fetchWeather` 函数的 URL 拼装中，追加 `&daily=sunrise,sunset` 参数；既有 `current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto` 参数保持不变
- [ ] 验收：fetchWeather URL 含 `daily=sunrise,sunset`；仍含 `temperature_2m`/`relative_humidity_2m`/`weather_code`/`wind_speed_10m`/`timezone=auto`；既有测试第 418-426 行断言不破坏；满足 REQ-TEA-EVT-014/UBI-013

### 2.2 buildWeatherInfo 提取 sunrise/sunset 字段
- [ ] 在 `frontend/assets/js/weather-visual.js` 第 78-93 行 `buildWeatherInfo` 函数中，追加提取 `data.daily.sunrise[0]` 与 `data.daily.sunset[0]` 存入返回对象的 `sunrise`/`sunset` 字段；data.daily 缺失时 sunrise/sunset 设为 null；既有 temp/humidity/windSpeed/weather/label/season/weathercode 字段不变
- [ ] 验收：buildWeatherInfo 返回对象含 `sunrise`/`sunset` 字段；data.daily 缺失时为 null；既有字段不变；满足 REQ-TEA-EVT-014

### 2.3 init 中调用 TimePeriod.applySunData
- [ ] 在 `frontend/assets/js/weather-visual.js` 第 149-164 行 `init` 函数中，`buildWeatherInfo` 成功后追加调用 `if (window.TimePeriod && info) window.TimePeriod.applySunData(info.sunrise, info.sunset);`；`fallback()` 降级分支后追加 `if (window.TimePeriod) window.TimePeriod.applySunData(null, null);`；既有 renderCard/applyTheme/applyBackground/fallback 调用顺序不变
- [ ] 验收：init 成功路径调用 applySunData(sunrise, sunset)；降级路径调用 applySunData(null, null)；既有调用不变；满足 REQ-TEA-UBI-022/023

---

## 3. 追加 common.css 分栏布局与时段动画样式

> 对应改动点 C-22~C-31；关联需求 REQ-TEA-EVT-001/UBI-005~011/019~026/STA-024/027。
> 无前置依赖，可与组1/组2 并行开发。
> 约束：既有 `:root` 变量/`prefers-reduced-motion`/weather-* 样式不触碰（C-32）；复用既有 CSS 变量不引入新色彩体系。

### 3.1 追加分栏布局容器基础样式
- [ ] 在 `frontend/assets/css/common.css` 文件末尾追加 `.home-pane`/`.timeline-pane`/`.timeline-iframe`/`.split-close` 基础样式：home-pane 为分栏左侧容器、timeline-pane 为右侧容器（默认 hidden）、timeline-iframe 设 width:100%/border:none/height:100%、split-close 按钮样式
- [ ] 验收：common.css 含 `.home-pane`/`.timeline-pane`/`.timeline-iframe`/`.split-close` 选择器；iframe 设 `width:100%`/`border:none`；满足 REQ-TEA-UBI-002/005

### 3.2 追加 data-layout 分栏 flex 布局选择器
- [ ] 追加 `[data-layout="split"] { display: flex; gap: 16px; align-items: flex-start; }` 选择器，驱动左右分栏 flex 布局；home-pane/timeline-pane 在 split 态下显示
- [ ] 验收：common.css 含 `[data-layout="split"]` 选择器与 `display: flex`；满足 REQ-TEA-EVT-001/UBI-005

### 3.3 追加分栏切换 transition
- [ ] 追加 home-pane/timeline-pane 的 width/opacity/transform transition，时长 ≤300ms ease，实现分栏切换平滑过渡
- [ ] 验收：common.css 含 transition 声明且时长 ≤300ms；满足 REQ-TEA-UBI-006

### 3.4 追加 data-timeperiod 7 类选择器
- [ ] 追加 `[data-timeperiod="morning"]`/`forenoon`/`noon`/`afternoon`/`evening`/`night`/`default` 共 7 类属性选择器（可先定义占位，后续由动效应用选择器填充）
- [ ] 验收：common.css 含全部 7 个 `[data-timeperiod="..."]` 选择器；满足 REQ-TEA-UBI-022

### 3.5 追加 6 时段 @keyframes 差异化动效
- [ ] 追加 6 组 @keyframes：`tea-morning`（晨光渐入暖色脉动）、`tea-forenoon`（清新明亮亮度脉动）、`tea-noon`（强烈日光金色光晕）、`tea-afternoon`（暖阳斜照琥珀位移）、`tea-evening`（暮色渐浓紫蓝过渡）、`tea-night`（星空静谧深蓝闪烁）；均复用既有 CSS 变量（`--leaf`/`--sun`/`--rain`/`--paper` 等），不引入新色彩体系；6 组动效须可视觉区分
- [ ] 验收：common.css 含 `@keyframes tea-morning`/`tea-forenoon`/`tea-noon`/`tea-afternoon`/`tea-evening`/`tea-night`；6 组动效 keyframe 内容各异；均使用既有 CSS 变量；满足 REQ-TEA-UBI-019/020/021

### 3.6 追加时段动效应用选择器
- [ ] 追加 `[data-timeperiod="morning"] .weather-bg-layer { animation: tea-morning ...; }` 等 6 类动效应用选择器（含 default 类无动画或中性动画）；时段动画作用于 `.weather-bg-layer`（z-index:-1 既有）或新增独立 `.time-period-fx` 图层（pointer-events:none + 低 z-index），与既有 weather-* 动效不冲突
- [ ] 验收：common.css 含 6 类 `[data-timeperiod="..."] .weather-bg-layer { animation: tea-... }` 选择器；动效层 z-index 低于信息文字；满足 REQ-TEA-UBI-022/023

### 3.7 追加时段切换 transition
- [ ] 追加 weather-bg-layer（或 time-period-fx 图层）的 opacity/transform/background-position transition，时长 ≤500ms ease，实现时段切换平滑过渡；背景色 `var(--paper)` 兜底防白屏
- [ ] 验收：common.css 含 transition 声明且时长 ≤500ms；满足 REQ-TEA-EVT-025/UBI-026

### 3.8 在既有 768px 媒体查询块内追加分栏 row 规则
- [ ] 在 `frontend/assets/css/common.css` 第 424-427 行既有 `@media (min-width: 768px)` 块内追加：`[data-layout="split"] { flex-direction: row; }`、`.home-pane { flex: 0 0 40%; }`、`.timeline-pane { flex: 1 1 60%; height: 100vh; }`
- [ ] 验收：768px 媒体查询块含 `flex-direction: row` 与 40%/60% 宽度分配；满足 REQ-TEA-UBI-009/010/011

### 3.9 在既有 1024px 媒体查询块内追加宽度比例
- [ ] 在 `frontend/assets/css/common.css` 第 429-431 行既有 `@media (min-width: 1024px)` 块内追加：`.home-pane { flex: 0 0 35%; }`、`.timeline-pane { flex: 1 1 65%; }`
- [ ] 验收：1024px 媒体查询块含 35%/65% 宽度分配；满足 REQ-TEA-UBI-010

### 3.10 追加 <768px 上下排列媒体查询
- [ ] 在 common.css 追加 `@media (max-width: 767px) { [data-layout="split"] { flex-direction: column; } .timeline-pane { height: auto; } }` 媒体查询，实现移动端上下排列；timeline-pane 可设 max-height 或 overflow-y:auto 防页面过长
- [ ] 验收：common.css 含 `@media (max-width: 767px)` 与 `flex-direction: column`；满足 REQ-TEA-UBI-008

---

## 4. 修改 index.html 分栏 DOM 与 submit 逻辑

> 对应改动点 C-01~C-06；关联需求 REQ-TEA-EVT-001/004/006/UNW-007/STA-003/UBI-002/005/033/036。
> 前置依赖：组1 完成（time-period.js 脚本可引入）；组3 完成（分栏样式就绪可验证过渡）。
> 约束：表单字段 crop/sowingDate/location 的 id/name 不变（C-07）；validate()/buildTimelineQuery() 逻辑不变（C-08）。

### 4.1 main 标签追加 data-layout/data-timeperiod 属性
- [ ] 在 `frontend/pages/index.html` 第 12 行 `<main class="page page--home">` 追加 `data-layout="single" data-timeperiod="default"` 属性
- [ ] 验收：index.html 含 `data-layout="single"` 与 `data-timeperiod="default"`；满足 REQ-TEA-EVT-001/UBI-022

### 4.2 包裹既有内容进 home-pane
- [ ] 在 `frontend/pages/index.html` 第 13 行 `<header>` 之前插入 `<div class="home-pane" id="home-pane">` 开始标签；在第 86 行 `</div>`（history 结束）之后插入 `</div>` 闭合 home-pane；包裹 header/weather-bg-layer/weather-card/term-ruler/form/history 既有内容，不改变子元素 id/class
- [ ] 验收：index.html 含 `<div class="home-pane" id="home-pane">`；既有 `id="weather-card"`/`id="timeline-form"`/`id="history"` 等子元素 id 不变；既有 57 断言不破坏；满足 REQ-TEA-UBI-002

### 4.3 新增 timeline-pane + iframe + split-close
- [ ] 在 home-pane 闭合 `</div>` 之后新增 `<div class="timeline-pane" id="timeline-pane" hidden>` 含 `<button class="split-close" id="split-close">收起分栏</button>` + `<iframe class="timeline-iframe" id="timeline-iframe"></iframe>` + `</div>`
- [ ] 验收：index.html 含 `id="timeline-pane"`/`id="timeline-iframe"`/`id="split-close"`；timeline-pane 默认 hidden；满足 REQ-TEA-EVT-001/004/UBI-002/037

### 4.4 引入 time-period.js 脚本
- [ ] 在 `frontend/pages/index.html` 第 93 行 weather-visual.js `<script>` 之后新增 `<script src="../assets/js/time-period.js"></script>`；加载顺序在 weather-visual.js 之后（因 time-period.js 依赖 weather-visual.js 传入 sunrise/sunset）、voice-input.js 之前或之后均可
- [ ] 验收：index.html 含 `time-period.js` 脚本引入；位于 weather-visual.js 之后；满足 REQ-TEA-UBI-034

### 4.5 改造 submit 事件为分栏触发 + try-catch 降级
- [ ] 在 `frontend/pages/index.html` 第 403-420 行 submit 事件处理函数中，`validate()` 通过后：将 `window.location.href = 'timeline.html?' + query` 替换为 try 块内分栏布局触发逻辑：`main.dataset.layout='split'` + `timeline-pane.hidden=false` + `iframe.src='timeline.html?' + query`；catch 分支回退 `window.location.href='timeline.html?' + query`（既有整页跳转作为降级）；保留 `buildTimelineQuery(...)` 调用不变
- [ ] 验收：submit 事件含 try-catch；try 内设置 data-layout="split" + iframe.src；catch 内回退 window.location.href；buildTimelineQuery 函数保留；既有测试第 155-160 行断言不破坏；满足 REQ-TEA-EVT-001/006/UNW-007

### 4.6 新增 split-close 事件与 iframe load 返回按钮覆盖
- [ ] 在 index.html 内联 IIFE 内新增 split-close 按钮 click 事件：`main.dataset.layout='single'` + `timeline-pane.hidden=true` + `iframe.src=''`（清空退出分栏恢复单栏）
- [ ] 新增 iframe load 事件：iframe 加载完成后通过同源 `iframe.contentDocument.querySelector('[onclick*="index.html"]')` 查找 timeline.html 内"返回修改"按钮，移除 onclick 改 addEventListener 通知父页面退出分栏（运行时增强，不修改 timeline.html 源文件）；覆盖失败时静默（用户可点左侧 split-close 退出）
- [ ] 验收：split-close 点击后恢复单栏布局；iframe load 后返回按钮触发父页面退出分栏；timeline.html 源文件不修改；满足 REQ-TEA-EVT-004/STA-003

---

## 5. 新增 pages.test.js 测试断言

> 对应改动点 C-33~C-46；关联需求 REQ-TEA-UBI-035。
> 前置依赖：组1~组4 全部完成（time-period.js/weather-visual.js/common.css/index.html 改动就绪）。
> 约束：既有 57 项断言不删除/不注释/不弱化（C-47）；新增断言为静态字符串/正则匹配（与既有风格一致），不执行 time-period.js 运行时逻辑（因含 DOM 依赖），仅 judgeTimePeriod 纯函数可 require 执行。

### 5.1 新增 timePeriodJs 文件读取变量
- [ ] 在 `frontend/tests/pages.test.js` 第 27 行之后（文件读取基座区）新增 `const timePeriodJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'time-period.js'));`
- [ ] 验收：pages.test.js 含 timePeriodJs 变量定义；路径指向 `frontend/assets/js/time-period.js`；满足 C-33

### 5.2 新增分栏布局 DOM 断言
- [ ] 在 pages.test.js 第 503 行之后（文件末尾）新增 test 块，断言 indexHtml 含 `home-pane`/`timeline-pane`/`timeline-iframe`/`split-close`/`data-layout`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-EVT-001/UBI-002

### 5.3 新增分栏布局 CSS 断言
- [ ] 新增 test 块，断言 commonCss 含 `[data-layout="split"]`/`.home-pane`/`.timeline-pane`/`.timeline-iframe`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-005/006

### 5.4 新增 data-timeperiod 选择器断言
- [ ] 新增 test 块，断言 commonCss 含 `[data-timeperiod="morning"]`/`forenoon`/`noon`/`afternoon`/`evening`/`night`/`default` 共 7 个选择器
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-022

### 5.5 新增 6 时段 @keyframes 断言
- [ ] 新增 test 块，断言 commonCss 含 `@keyframes tea-morning`/`tea-forenoon`/`tea-noon`/`tea-afternoon`/`tea-evening`/`tea-night`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-019/021

### 5.6 新增 time-period.js 关键函数断言
- [ ] 新增 test 块，断言 timePeriodJs 含 `judgeTimePeriod`/`applyTimePeriod`/`applySunData`/`parseSunTime`/`fallbackPeriod`/`init`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-012/015/022

### 5.7 新增默认时段划分常量断言
- [ ] 新增 test 块，断言 timePeriodJs 含 `DEFAULT_PERIOD_SCHEME` + 时间点常量（`06:00` 或 `360`/`08:00` 或 `480`/`11:00` 或 `660`/`14:00` 或 `840`/`17:00` 或 `1020`/`22:00` 或 `1320`）
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-016/017

### 5.8 新增时段枚举断言
- [ ] 新增 test 块，断言 timePeriodJs 含 `TIME_PERIODS` + `morning`/`forenoon`/`noon`/`afternoon`/`evening`/`night`/`default`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-022

### 5.9 新增日出日落请求参数断言
- [ ] 新增 test 块，断言 weatherVisualJs 含 `daily=sunrise,sunset` 或同时含 `sunrise` 与 `sunset`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-EVT-014/UBI-013

### 5.10 新增降级机制断言
- [ ] 新增 test 块，断言 timePeriodJs 含 `fallbackPeriod` + `default`
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UNW-016/029

### 5.11 新增无第三方库断言
- [ ] 新增 test 块，断言 timePeriodJs 不含 `import`/`require` 第三方库语句（不含 GSAP/Lottie/Anime.js 等）
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-034

### 5.12 新增响应式分栏断言
- [ ] 新增 test 块，断言 commonCss 含 `flex-direction: row` 或等价左右分栏规则，且含 `flex-direction: column` 或等价上下排列规则
- [ ] 验收：新增 test 块通过；满足 REQ-TEA-UBI-008/009

### 5.13 新增 judgeTimePeriod 纯函数断言
- [ ] 新增 test 块，`require('../assets/js/time-period.js')` 后验证 `judgeTimePeriod(new Date(2026,9,23,9,30), '2026-10-23T06:00', '2026-10-23T18:00')` 返回 `forenoon`；`judgeTimePeriod(new Date(2026,9,23,12,0), '2026-10-23T06:00', '2026-10-23T18:00')` 返回 `noon`；`judgeTimePeriod(new Date(2026,9,23,23,0), null, null)` 返回 `night`；`parseSunTime('2026-10-23T06:12')` 返回 372
- [ ] 验收：新增 test 块通过；纯函数 require 执行无 DOM 依赖报错；满足 REQ-TEA-UBI-015

### 5.14 新增既有 57 断言兼容性复核
- [ ] 新增 test 块，复核 indexHtml 仍含 `buildTimelineQuery`/`sowDate`/`lat`/`lon`/`id="weather-card"`/`id="timeline-form"` 等既有断言关键字符串，确认包裹 DOM 与分栏改造未破坏既有断言
- [ ] 验收：新增 test 块通过；既有 57 项断言全绿；满足 REQ-TEA-UBI-035

---

## 6. 回归验证与部署确认

> 前置依赖：组1~组5 全部完成。
> 本组为验收阶段，不产生新代码，仅运行测试与浏览器手动验证。

### 6.1 运行前端测试套件确认全绿
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 57 项断言全绿 + 新增断言（组5）全绿，无失败
- [ ] 验收：测试输出全绿，总数 = 57 + 组5新增 test 块数；满足 REQ-TEA-UBI-035

### 6.2 浏览器手动验证分栏布局切换
- [ ] 在浏览器打开 `frontend/pages/index.html`，填写有效条件并点击"生成时间线"，验证：页面不发生整页跳转、主页内容收缩至屏幕左侧、时间线内容在屏幕右侧展开、形成左右分栏布局、切换过渡 ≤300ms 平滑无跳变/闪烁/白屏
- [ ] 验收：分栏布局正确切换；过渡平滑 ≤300ms；满足 REQ-TEA-EVT-001/UBI-006

### 6.3 浏览器手动验证响应式分栏适配
- [ ] 在 375px 宽度下提交搜索，验证主页在上、时间线在下上下排列，无横向滚动条；在 768px 与 1024px 宽度下提交搜索，验证主页在左、时间线在右左右分栏，两侧内容完整可见，宽度比例协调
- [ ] 验收：375px 上下排列无横向滚动；768px/1024px 左右分栏比例协调；满足 REQ-TEA-UBI-008/009/010

### 6.4 浏览器手动验证 6 时段差异化动画
- [ ] 通过修改系统时间或 mock sunrise/sunset，分别在各时段（早晨/上午/中午/下午/晚上/深夜）查看主页动画，验证 6 个时段动画风格各异可视觉区分、时段切换平滑过渡 ≤500ms 无突兀跳变/闪烁/白屏
- [ ] 验收：6 时段动画各异可视觉区分；切换过渡 ≤500ms；核心信息文字可读不被遮挡；满足 REQ-TEA-UBI-019/EVT-025/UBI-026

### 6.5 浏览器手动验证日出日落动态计算
- [ ] 模拟不同地点（如北京 vs 哈尔滨）提交搜索，验证时段边界依据各自日出日落动态不同（可通过 DevTools Network 查看 Open-Meteo 请求含 `daily=sunrise,sunset` 参数，响应含 sunrise/sunset 字段）
- [ ] 验收：Open-Meteo 请求含 daily=sunrise,sunset；不同地点时段边界不同；无硬编码固定日出日落；满足 REQ-TEA-UBI-013/EVT-014

### 6.6 浏览器手动验证降级机制
- [ ] 模拟日出日落请求失败（断网/超时/响应异常），验证使用默认时段划分（固定时间点）正常播放动画，不白屏、不报错、不影响既有天气视觉与表单交互；模拟分栏切换异常，验证回退整页跳转；模拟时段判定全失败，验证 data-timeperiod="default" 默认动画
- [ ] 验收：日出日落失败默认时段降级不白屏不报错；分栏失败回退整页跳转；时段判定失败 default 动画；控制台无未捕获错误；满足 REQ-TEA-UNW-016/029/030/032

### 6.7 浏览器手动验证 prefers-reduced-motion
- [ ] 在系统设置开启"减少动画"（或 DevTools Rendering 勾选 prefers-reduced-motion），验证所有时段动画与分栏切换过渡被禁用（即时切换），不播放任何动画
- [ ] 验收：reduced-motion 开启后无动画播放；页面静态展示；满足 REQ-TEA-STA-024/027

### 6.8 浏览器手动验证分栏态重新提交与退出
- [ ] 在分栏布局中修改左侧表单条件并重新提交，验证右侧时间线依据新条件刷新（不整页跳转）；点击"收起分栏"按钮，验证主页恢复全屏单栏布局；点击 iframe 内"返回修改"按钮，验证退出分栏聚焦左侧表单
- [ ] 验收：重新提交右侧刷新不跳转；split-close 恢复单栏；iframe 内返回按钮退出分栏；满足 REQ-TEA-STA-003/EVT-004

### 6.9 代码审查确认不触碰文件清单
- [ ] 运行 `git diff --name-only` 确认改动文件仅在 `frontend/` 目录内，且仅限：`frontend/pages/index.html`、`frontend/assets/js/time-period.js`（新增）、`frontend/assets/js/weather-visual.js`、`frontend/assets/css/common.css`、`frontend/tests/pages.test.js`；确认 timeline.html/timeline-logic.js/crop-map.js/voice-input.*/mock-data.js/后端/nginx/shared/data/deploy/docs 无改动
- [ ] 验收：git diff 文件清单仅含 5 个目标文件；不触碰文件清单无改动；满足 REQ-TEA-UBI-033/037/039/040

### 6.10 代码审查确认无第三方库引入
- [ ] 审查 time-period.js/weather-visual.js/index.html/common.css，确认无 import/require/CDN 引入第三方库（GSAP/Lottie/Anime.js 等），无 package.json 新增依赖，无构建工具配置；确认时段动画纯 CSS @keyframes、分栏布局纯 CSS flexbox
- [ ] 验收：无第三方库引用；纯原生 JS + 原生 CSS；满足 REQ-TEA-UBI-034

### 6.11 代码审查确认既有逻辑不变
- [ ] 审查确认：表单字段 crop/sowingDate/location 的 id/name 不变；validate()/buildTimelineQuery() 逻辑不变；weather-visual.js 的 WMO_MAP/normalizeWeather/inferSeason/fallback 不变；common.css 的 :root 变量/既有 weather-* 样式不变；pages.test.js 既有 57 项断言未删除/弱化
- [ ] 验收：既有核心逻辑与断言全部保留不变；满足 REQ-TEA-UBI-035/036/037/038

---

## 风险点与边界情况提示

> 执行中需特别注意的边界情况，对应 design.md R-01~R-18。

| 风险 | 注意事项 | 关联任务 |
|------|---------|---------|
| R-01 Open-Meteo 不返回 daily.sunrise/sunset | judgeTimePeriod 须处理 null 走默认划分，不白屏不报错 | 1.5/1.7/6.6 |
| R-03 分栏切换 DOM 异常 | submit 事件须 try-catch 回退整页跳转 | 4.5/6.6 |
| R-04 iframe 内返回按钮导致 iframe 跳转 index.html | iframe load 后立即覆盖 onclick；覆盖失败时 split-close 仍可用 | 4.6/6.8 |
| R-05 高纬度极昼/极夜 | judgeTimePeriod 空时段合并相邻，不报错 | 1.5/6.4 |
| R-07 时段动画遮挡信息文字 | 动效层 z-index 低于信息文字，pointer-events:none | 3.6/6.4 |
| R-09 分栏布局横向滚动 | flex-basis 合计 100% + gap 用 padding 内扣；iframe width:100% + border:none | 3.1/3.8/6.3 |
| R-10 既有 57 测试断言被破坏 | 包裹 home-pane 不改变子元素 id/class；新增 CSS/JS 末尾追加不删除既有字符串 | 4.2/5.14/6.1 |
| R-11 追加 daily 参数破坏既有 API 断言 | 追加不删除既有 temperature_2m 等字符串 | 2.1/6.1 |
| R-13 分栏态重新提交 iframe 不刷新 | 重新提交时 iframe.src 设为新 query；相同条件可先 src='' 再设新值强制刷新 | 4.5/6.8 |
| R-14 时段动画与既有天气动效冲突 | 时段动画用独立图层或同图层不同 animation-name 不覆盖 | 3.6/6.4 |
| R-17 页面加载先默认再切换目标时段动画 | index.html 初始 data-timeperiod="default"；applySunData 调用时立即设置目标时段 transition 平滑过渡 | 4.1/6.4 |