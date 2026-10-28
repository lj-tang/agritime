# V4 T01 主页 UI 改版与天气视觉动态适配 · 编码任务规划（v4_t01_ui_revamp）

> 本任务规划基于 `v4_t01_ui_revamp/spec.md`（REQ-VUI-UBI/EVT/STA/OPT/UNW-001~039，共 39 条 EARS 需求）与 `design.md`（D-VUI-01~23 共 23 条关键决策、C-01~C-64 共 64 项改动点、2.6 节风险表）生成。
>
> **范围红线**：仅修改/新增 `frontend/` 目录下文件；不触碰后端（Java/8080）、nginx、shared/data/deploy/docs、timeline.html、timeline-logic.js、crop-map.js、voice-input.*、photo-capture.js、history-store.js、chart-client.js、time-period.js、mock-data.js。
>
> **改动文件清单**：
> - 新增：`frontend/assets/weather-bg/sunny-night.svg`、`rain-night.svg`、`cloudy-night.svg`、`snow-night.svg`（C-57~C-60，4 文件）
> - 新增：`frontend/components/sidebar/sidebar.js`、`sidebar.css`（C-15~C-31，2 文件）
> - 修改：`frontend/assets/js/weather-visual.js`（C-32~C-38，增量扩展）
> - 修改：`frontend/assets/css/common.css`（C-39~C-56，末尾追加 + 覆盖）
> - 修改：`frontend/pages/index.html`（C-01~C-14，包裹 DOM + 新增 DOM + 引入脚本/样式）
> - 修改：`frontend/tests/pages.test.js`（C-62~C-64，末尾追加 test 块）
>
> **全局约束继承**：纯原生 JS + 原生 CSS、前端 63/63 既有测试全绿仅允许新增断言、不引入框架/构建工具/第三方库（含 GSAP/Lottie/Vue/React）、mock 保持 null 占位不造假、crop-map.js 5 个 cropId 不变、既有 weather-visual.js 归一化（WMO_MAP/normalizeWeather/inferSeason/fallback）与降级语义不变、既有 time-period.js 6 时段判定输出不变、既有表单字段 id/name 与 validate()/buildTimelineQuery() 逻辑不变、既有分栏布局（body.split-active）兼容。

---

## 任务依赖关系总览

```
组1 夜晚SVG素材 ──┬──────────────────────────┬──→ 组6 测试断言 ──→ 组7 回归验证
                   │                          │
组2 sidebar组件 ──┼──→ 组5 index.html ────────┤
                   │                          │
组3 weather-visual ┘──────────────────────────┘
                   │
组4 common.css ────┘（依赖组1夜晚SVG路径）
```

- **组1、组2、组3 无前置依赖，可并行开发**。
- 组4 依赖组1（夜晚 SVG 文件存在，CSS 选择器引用 `sunny-night.svg` 等路径）。
- 组5 依赖组2（sidebar.js/css 文件存在方可引入）+ 组3（weather-ready 事件机制就绪方可监听）+ 组4（data-layout CSS 就绪方可验证全屏布局）。
- 组6 依赖组1~组5 全部完成（断言需读取所有新增/修改文件）。
- 组7 依赖组6（测试全绿后方可回归验证）。

---

## 1. 新建夜晚 SVG 动画素材

> 对应改动点 C-57~C-61，需求 REQ-VUI-UBI-018/020/021。
> 目标：在 `frontend/assets/weather-bg/` 下新增 4 类夜晚 SVG 矢量背景，与既有 5 类白天素材并列，遵循既有目录规范，单张 ≤300KB。default.svg 白天/夜晚共用（降级态不区分昼夜）。
> 无前置依赖，可与组2/组3 并行开发。

### 1.1 创建夜晚晴天背景 sunny-night.svg
- [ ] 新建 `frontend/assets/weather-bg/sunny-night.svg`，SVG 矢量图形，表现夜晚晴天视觉（深蓝夜空 + 星点/月色），与既有 `sunny.svg`（暖金/绿色调白天）可视觉区分，单张体积 ≤300KB（对应 C-57）。
- 验收条件：文件存在；为合法 SVG（含 `<svg` 根元素且闭合）；体积 ≤300KB；视觉与 sunny.svg 可区分（深色调 vs 暖色调）。

### 1.2 创建夜晚雨天背景 rain-night.svg
- [ ] 新建 `frontend/assets/weather-bg/rain-night.svg`，SVG 矢量图形，表现夜晚雨天视觉（深蓝夜空 + 雨滴），与既有 `rain.svg`（冷蓝色调白天）可视觉区分，单张体积 ≤300KB（对应 C-58）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB；视觉与 rain.svg 可区分。

### 1.3 创建夜晚多云背景 cloudy-night.svg
- [ ] 新建 `frontend/assets/weather-bg/cloudy-night.svg`，SVG 矢量图形，表现夜晚多云视觉（深蓝夜空 + 云层），与既有 `cloudy.svg`（灰蓝色调白天）可视觉区分，单张体积 ≤300KB（对应 C-59）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB；视觉与 cloudy.svg 可区分。

### 1.4 创建夜晚雪天背景 snow-night.svg
- [ ] 新建 `frontend/assets/weather-bg/snow-night.svg`，SVG 矢量图形，表现夜晚雪天视觉（深蓝夜空 + 雪花），与既有 `snow.svg`（冷白蓝色调白天）可视觉区分，单张体积 ≤300KB（对应 C-60）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB；视觉与 snow.svg 可区分。

### 1.5 确认既有 5 类白天 SVG 不变
- [ ] 确认 `frontend/assets/weather-bg/sunny.svg`/`rain.svg`/`cloudy.svg`/`snow.svg`/`default.svg` 既有 5 文件内容逐字节不变（对应 C-61，REQ-VUI-UBI-038）。
- 验收条件：git diff 审查既有 5 类白天 SVG 无修改；既有测试第 435-441 行断言（检查 sunnySvg/rainSvg/cloudySvg/snowSvg/defaultSvg 含 `<svg`）不破坏。

---

## 2. 新建左侧栏导航组件（sidebar.js + sidebar.css）

> 对应改动点 C-15~C-31，需求 REQ-VUI-UBI-001/002/005/006/008/EVT-003/STA-004/OPT-007/009。
> 目标：新建 `frontend/components/sidebar/sidebar.js`（IIFE + window.Sidebar 暴露 API）与 `sidebar.css`，提供 6 板块入口导航、板块切换、占位提示、移动端抽屉。与既有 voice-input.js（IIFE + window 暴露）风格一致。纯原生 JS + 原生 CSS，无 import/require 第三方库。
> 无前置依赖，可与组1/组3 并行开发。

### 2.1 创建 sidebar.js 文件骨架与具名常量
- [ ] 新建 `frontend/components/sidebar/sidebar.js`，采用 IIFE 包裹（`(function () { ... })();`），与既有 `weather-visual.js`/`time-period.js` 风格一致（对应 C-15）。
- [ ] 在模块顶部定义具名常量（对应 C-16，D-VUI-02）：
  - `MODULE_ENTRIES`：数组，6 项，每项含 `{id, name, order, implemented}`，依次为：
    - `{id:'recommend', name:'种植推荐', order:1, implemented:false}`
    - `{id:'plots', name:'我的地块', order:2, implemented:false}`
    - `{id:'calendar', name:'农事日历', order:3, implemented:true}`
    - `{id:'disease', name:'病害检查', order:4, implemented:false}`
    - `{id:'my-crops', name:'我的作物', order:5, implemented:false}`
    - `{id:'service', name:'客服', order:6, implemented:false}`
  - `DEFAULT_MODULE = 'calendar'`（默认选中农事日历，D-VUI-03）
  - `MODULE_PLACEHOLDER_TEXT = '该板块即将上线'`（占位提示文案，D-VUI-13）
- 验收条件：文件存在；含 IIFE 结构；含 `MODULE_ENTRIES`/`DEFAULT_MODULE`/`MODULE_PLACEHOLDER_TEXT` 三个具名常量；MODULE_ENTRIES 恰为 6 项且顺序与名称与需求一致；仅 calendar implemented 为 true。

### 2.2 实现 renderEntries() 渲染 6 入口
- [ ] 实现 `renderEntries()` 函数：根据 `MODULE_ENTRIES` 在 `#app-sidebar` 容器内生成 6 个入口按钮（`<button class="sidebar-entry" data-module="...">`含板块名称），使用 `textContent` 安全渲染（禁止 innerHTML 拼接，REQ-VUI 4.3.2）；节点缺失时静默降级不抛错（对应 C-18，D-VUI-16）。
- 验收条件：函数存在；含 `MODULE_ENTRIES` 遍历；含 `textContent` 赋值板块名称；含 `data-module` 属性设置；含节点缺失判断。

### 2.3 实现 selectModule(id) 板块切换
- [ ] 实现 `selectModule(id)` 函数（对应 C-19，D-VUI-12）：
  - 移除所有 `.sidebar-entry` 的 `active` 类；
  - 为目标入口（`[data-module="id"]`）添加 `active` 类（高亮）；
  - 隐藏当前 content pane，显示目标 content pane（`#pane-${id}`）；
  - 未实现板块（implemented 为 false）展示占位提示（pane 内已含 `MODULE_PLACEHOLDER_TEXT` 文案）；
  - id 非法（不在 MODULE_ENTRIES 中）时静默忽略不切换不报错；
  - 内部 try-catch，异常时保持当前板块不白屏不报错（REQ-VUI-UNW-010）。
- 验收条件：函数存在；含 `active` 类切换逻辑；含 `pane-${id}` 显示/隐藏；含 try-catch；含非法 id 静默忽略分支。

### 2.4 实现 toggleSidebar() 移动端抽屉切换
- [ ] 实现 `toggleSidebar()` 函数：切换 `body` 元素的 `data-sidebar-open` 属性（有则移除、无则设置），CSS transform 据此驱动抽屉滑出/收起（对应 C-20，D-VUI-14）。
- 验收条件：函数存在；含 `data-sidebar-open` 属性切换；含 `toggle` 或 `setAttribute`/`removeAttribute` 逻辑。

### 2.5 实现 getActiveModule() 与 judgeModuleImplemented()
- [ ] 实现 `getActiveModule()` 函数：返回当前选中板块 id（从 `.sidebar-entry.active` 的 `data-module` 属性读取，默认返回 `DEFAULT_MODULE`）（对应 C-21）。
- [ ] 实现 `judgeModuleImplemented(id)` 函数：查 MODULE_ENTRIES 判断板块是否已实现，返回布尔值（对应 C-22）。
- 验收条件：两个函数存在；getActiveModule 含 active 入口读取与默认值回退；judgeModuleImplemented 含 MODULE_ENTRIES 查找。

### 2.6 实现 init() 入口编排
- [ ] 实现 `init()` 函数（对应 C-17，D-VUI-16）：编排 `renderEntries()` → `selectModule(DEFAULT_MODULE)`（默认选中 calendar 高亮 + 显示 pane-calendar）→ 绑定入口点击事件（点击调 selectModule）→ 绑定汉堡按钮点击事件（点击调 toggleSidebar）；内部 catch 所有异常静默降级（左侧栏可能不渲染但主页内容仍可用），不向外抛出。
- 验收条件：函数存在；含 renderEntries 调用；含 selectModule(DEFAULT_MODULE) 调用；含入口点击事件绑定；含汉堡按钮点击事件绑定；含 try-catch 静默降级。

### 2.7 暴露 API 与 module.exports
- [ ] 在 IIFE 末尾通过 `window.Sidebar` 暴露 `{ init, renderEntries, selectModule, toggleSidebar, getActiveModule, judgeModuleImplemented, MODULE_ENTRIES, DEFAULT_MODULE, MODULE_PLACEHOLDER_TEXT }`（对应 C-15）。
- [ ] 通过 `module.exports` 暴露 `{ MODULE_ENTRIES, DEFAULT_MODULE, MODULE_PLACEHOLDER_TEXT, selectModule, getActiveModule, judgeModuleImplemented }` 供测试 require（对应 C-23，REQ-VUI-UBI-039）。
- 验收条件：含 `window.Sidebar` 暴露；含 `module.exports` 暴露；无 `import`/`require` 第三方库语句。

### 2.8 创建 sidebar.css 左侧栏容器与入口样式
- [ ] 新建 `frontend/components/sidebar/sidebar.css`，追加 `.app-sidebar` 容器样式：固定左侧（`position: fixed; top: 0; left: 0; width: 220px; height: 100vh; overflow-y: auto;`），背景 `var(--card)`，右边框 `1px solid var(--line)`，z-index 低于表单/天气卡（对应 C-24，D-VUI-01）。
- [ ] 追加 `.sidebar-entry` 入口按钮样式：纵向排列（`display: block; width: 100%;`），含 padding、文字色 `var(--ink-soft)`、背景透明、边框无、hover 态背景 `var(--leaf-soft)`（对应 C-25）。
- 验收条件：文件存在；含 `.app-sidebar` 选择器与 fixed 定位；含 `.sidebar-entry` 选择器与纵向排列；复用既有 CSS 变量（不引入新色彩体系）。

### 2.9 追加 sidebar.css 高亮与占位提示样式
- [ ] 追加 `.sidebar-entry.active` 高亮样式：背景 `var(--leaf-soft)`、左边框 `3px solid var(--leaf)`、文字色 `var(--leaf)`、font-weight 700（对应 C-26，D-VUI-01）。
- [ ] 追加 `.sidebar-toggle` 汉堡按钮样式：默认隐藏（`display: none;`），<768px 显示（对应 C-27）。
- [ ] 追加 `.module-placeholder` 占位提示样式：居中（`display: flex; align-items: center; justify-content: center; min-height: 60vh;`），文字色 `var(--muted)`，font-size 18px（对应 C-28）。
- 验收条件：含 `.sidebar-entry.active` 高亮样式；含 `.sidebar-toggle` 默认隐藏；含 `.module-placeholder` 居中样式。

### 2.10 追加 sidebar.css 移动端抽屉与响应式
- [ ] 追加 `@media (max-width: 767px)` 移动端抽屉规则（对应 C-29，D-VUI-14）：`.app-sidebar { transform: translateX(-100%); transition: transform 0.3s ease; z-index: 100; }` + `body[data-sidebar-open] .app-sidebar { transform: translateX(0); }` + `.sidebar-toggle { display: block; }`。
- [ ] 追加 `@media (min-width: 768px)` 桌面端规则：`.sidebar-toggle { display: none; }`（对应 C-30）。
- [ ] 追加 `@media (min-width: 1024px)` 宽屏规则：`.app-sidebar { width: 240px; }`（左侧栏加宽）。
- [ ] 追加 `@media (prefers-reduced-motion: reduce)` 抽屉 transition 禁用：`.app-sidebar { transition: none !important; }`（对应 C-31，REQ-VUI-UBI-023）。
- 验收条件：含 `@media (max-width: 767px)` 与 `transform: translateX`；含 `data-sidebar-open` 选择器；含 `@media (min-width: 768px)` 隐藏汉堡按钮；含 `prefers-reduced-motion` 禁用 transition。

---

## 3. 扩展 weather-visual.js（昼夜判定 + 加载门控事件）

> 对应改动点 C-32~C-38，需求 REQ-VUI-UBI-018/019/023/025/037/EVT-028/OPT-030。
> 目标：在 `frontend/assets/js/weather-visual.js` 增量扩展——新增 `judgeDayNight` 昼夜判定函数；`applyBackground` 改为设置 `data-daynight` 属性而非赋值 style；`init` 末尾新增昼夜判定与 `weather-ready` 事件触发；`fallback` 末尾新增 `weather-ready` 事件触发。既有 WMO_MAP/normalizeWeather/inferSeason/fetchWeather/buildWeatherInfo/renderCard/applyTheme 逻辑不变。
> 无前置依赖，可与组1/组2 并行开发。
> 约束：既有归一化/降级逻辑不变（C-38，REQ-VUI-UBI-037）；保留 applyBackground 函数名与调用点（既有测试第 398 行检查 `applyBackground` 字符串）。

### 3.1 新增 judgeDayNight(period) 昼夜判定函数
- [ ] 在 `frontend/assets/js/weather-visual.js` 模块内（建议在 `inferSeason` 函数之后）新增 `judgeDayNight(period)` 函数（对应 C-32，D-VUI-08）：
  - `period` 为 `morning`/`forenoon`/`noon`/`afternoon` → 返回 `'day'`；
  - `period` 为 `evening`/`night` → 返回 `'night'`；
  - 非法输入（null/undefined/未知字符串）→ 返回 `'day'`（默认白天，安全回退）；
  - 不发起网络请求，纯本地映射（REQ-VUI-UBI-025）。
- 验收条件：函数存在；含 morning/forenoon/noon/afternoon → day 映射；含 evening/night → night 映射；含非法输入 → day 回退；无 fetch/XHR 网络请求。

### 3.2 改造 applyBackground 为设置 data-daynight 属性
- [ ] 修改 `frontend/assets/js/weather-visual.js` 第 123-128 行 `applyBackground` 函数实现（对应 C-33，D-VUI-07/D-VUI-21）：
  - 保留函数名 `applyBackground` 与既有调用点（init 第 161 行、fallback 第 132 行）不变；
  - 改为在 `main.page--home` 节点上设置 `data-daynight` 属性：`var main = document.querySelector('.page--home'); if (main) main.setAttribute('data-daynight', daynight || 'day');`；
  - 接受昼夜参数（由 init 传入 judgeDayNight 结果），不再赋值 `bg.style.backgroundImage`；
  - CSS 据此 `[data-daynight][data-weather]` 属性选择器组合切换背景（组4 实现）；
  - 节点缺失时静默降级不抛错。
- 验收条件：函数名仍为 `applyBackground`；含 `setAttribute('data-daynight'`；不含 `style.backgroundImage =` 赋值；含节点缺失判断；既有测试第 398 行断言（检查 `applyBackground` 字符串）不破坏。

### 3.3 init 末尾新增昼夜判定与 weather-ready 事件触发
- [ ] 修改 `frontend/assets/js/weather-visual.js` 第 149-169 行 `init` 函数（对应 C-34，D-VUI-10/D-VUI-22）：
  - 既有 `getLocation` → `fetchWeather` → `buildWeatherInfo` → `renderCard`/`applyTheme` 调用顺序不变；
  - 在 `renderCard`/`applyTheme` 之后、`TimePeriod.applySunData` 调用之后，新增昼夜判定：读取 TimePeriod 当前时段结果（或独立判定），调用 `judgeDayNight(period)` 得到 daynight；
  - 调用 `applyBackground(info.weather, daynight)` 传入昼夜参数（既有 applyBackground 调用点改为传两参数）；
  - 在 init 成功路径末尾新增 `window.dispatchEvent(new Event('weather-ready'))` 触发加载就绪事件；
  - 既有 `fallback()` 降级分支不变（fallback 内部新增事件触发见 3.4）。
- 验收条件：init 含 `judgeDayNight` 调用；含 `applyBackground` 传昼夜参数；含 `window.dispatchEvent(new Event('weather-ready'))`；既有 getLocation/fetchWeather/buildWeatherInfo/renderCard/applyTheme 调用不变。

### 3.4 fallback 末尾新增 weather-ready 事件触发
- [ ] 修改 `frontend/assets/js/weather-visual.js` 第 130-147 行 `fallback` 函数（对应 C-35，D-VUI-10/D-VUI-22）：
  - 既有降级逻辑（`applyTheme('default', 'auto')` + `applyBackground('default')` + weather-card 显示 + 中性提示文案）不变；
  - 在函数末尾新增 `window.dispatchEvent(new Event('weather-ready'))` 触发加载就绪事件（确保超时/失败也隐藏 loading-gate，不永久停留，REQ-VUI-OPT-030）；
  - 新增 try-catch 包裹事件触发（window 或 dispatchEvent 异常时静默不抛错）。
- 验收条件：fallback 末尾含 `window.dispatchEvent(new Event('weather-ready'))`；既有 `data-weather="default"` + 中性提示文案逻辑不变；含 try-catch。

### 3.5 暴露 judgeDayNight 到 window.WeatherVisual 与 module.exports
- [ ] 修改 `frontend/assets/js/weather-visual.js` 第 172 行 `window.WeatherVisual` 暴露，新增 `judgeDayNight`：`window.WeatherVisual = { init: init, normalizeWeather: normalizeWeather, inferSeason: inferSeason, judgeDayNight: judgeDayNight }`（对应 C-36）。
- [ ] 修改第 177 行 `module.exports`，新增导出 `judgeDayNight` 供测试 require：`module.exports = { normalizeWeather: normalizeWeather, inferSeason: inferSeason, buildWeatherInfo: buildWeatherInfo, judgeDayNight: judgeDayNight }`（对应 C-37）。
- 验收条件：`window.WeatherVisual` 含 `judgeDayNight`；`module.exports` 含 `judgeDayNight`；既有 normalizeWeather/inferSeason/buildWeatherInfo 导出不变。

### 3.6 确认既有归一化/降级逻辑不变
- [ ] 确认 `WMO_MAP`（第 15-27 行）/`normalizeWeather`（第 33-36 行）/`inferSeason`（第 38-43 行）/`fetchWeather`（第 56-76 行）/`buildWeatherInfo`（第 78-93 行）/`renderCard`（第 95-114 行）/`applyTheme`（第 116-121 行）既有逻辑逐行不变（对应 C-38，REQ-VUI-UBI-037）。
- 验收条件：git diff 审查 weather-visual.js 既有 WMO_MAP/normalizeWeather/inferSeason/fetchWeather/buildWeatherInfo/renderCard/applyTheme 无修改，仅 applyBackground 改实现 + init/fallback 增量叠加 + 新增 judgeDayNight + 暴露扩展；既有测试第 390-432 行断言全绿。

---

## 4. 追加 common.css 全屏布局与昼夜动画样式

> 对应改动点 C-39~C-56，需求 REQ-VUI-UBI-011/012/013/015/018/020/023/031/EVT-026/OPT-009/STA-016/022。
> 目标：在 `frontend/assets/css/common.css` 末尾追加全屏 flex 布局、移除整页背景、昼夜背景选择器、动画叠加层、加载门控、content pane、响应式左侧栏等样式。既有 `:root` 变量/`prefers-reduced-motion`/weather-*/tea-* 样式不触碰。
> 前置依赖：组1（夜晚 SVG 文件存在，CSS 选择器引用 `sunny-night.svg` 等路径）。
> 约束：既有 `:root` 变量（第 1-21 行）不修改（C-53）；既有 `.page--home` 背景规则（第 293-299 行）保留被覆盖（C-54）；既有 weather-*/tea-* @keyframes 与选择器不触碰（C-55）；既有 `prefers-reduced-motion` 全局规则（第 289-291 行）不触碰（C-56）。

### 4.1 追加全屏 flex 布局选择器
- [ ] 在 `frontend/assets/css/common.css` 文件末尾追加全屏 flex 布局选择器（对应 C-39/C-40/C-41，D-VUI-04）：
  - `body[data-layout="app"] { display: flex; min-height: 100vh; gap: 0; }` 全屏 flex 布局；
  - `body[data-layout="app"] .app-sidebar { flex: 0 0 220px; overflow-y: auto; }` 左侧栏固定宽度；
  - `body[data-layout="app"] .page--home { flex: 1 1 auto; max-width: none; margin: 0; padding: 22px 24px; }` 内容区占满（覆盖既有 `.page` max-width:460px，仅 .page--home 不影响 timeline.html）。
- 验收条件：common.css 含 `body[data-layout="app"]` 与 `display: flex`；含 `.app-sidebar` flex:0 0 220px；含 `.page--home` flex:1 1 auto + max-width:none。

### 4.2 追加移除整页背景图选择器
- [ ] 追加 `body[data-layout="app"] .page--home { background-image: none; }` 移除整页背景图（对应 C-42，D-VUI-05，REQ-VUI-UBI-012）：覆盖既有第 294 行 `background-image: url('../img/index-bg.png')`；保留 `background-color: var(--paper)` 作为底色；既有 weather-bg-layer 继续提供天气背景。
- 验收条件：common.css 含 `background-image: none` 覆盖规则；既有第 293-299 行 `.page--home` 背景规则保留（被覆盖不删除，C-54）；既有测试第 443-450 行断言（检查 .weather-bg-layer/.weather-card 字符串）不破坏。

### 4.3 追加 content pane 板块切换样式
- [ ] 追加 content pane 板块切换样式（对应 C-43，D-VUI-12）：
  - `.content-pane { min-height: 100vh; }` 内容区最小高度铺满；
  - `.content-pane[hidden] { display: none; }` 隐藏的 pane 不占位。
- 验收条件：common.css 含 `.content-pane` 与 `[hidden]` 选择器。

### 4.4 追加 weather-fx-overlay 动画叠加层样式
- [ ] 追加 `.weather-fx-overlay` 动画叠加层样式（对应 C-44，D-VUI-06，REQ-VUI-UBI-013）：`position: absolute; inset: 0; z-index: 1; pointer-events: none;`（绝对定位叠加在内容区图片之上，z-index:1 介于内容图片与表单之间，pointer-events:none 不阻挡交互）。
- [ ] 追加表单/天气卡/历史记录 z-index 高于叠加层：`body[data-layout="app"] .page--home .card, body[data-layout="app"] .page--home .weather-card, body[data-layout="app"] .page--home .history { position: relative; z-index: 2; }` 确保可交互。
- 验收条件：common.css 含 `.weather-fx-overlay` 与 `pointer-events: none`；含表单/天气卡 z-index:2 规则。

### 4.5 追加白天昼夜背景选择器（4 类白天组合）
- [ ] 追加 4 类白天昼夜背景选择器（对应 C-45，D-VUI-07，REQ-VUI-UBI-018/020/023），在 `main.page--home` 上按 `[data-daynight="day"][data-weather="..."]` 组合驱动 weather-bg-layer 背景图：
  - `[data-daynight="day"][data-weather="sunny"] .weather-bg-layer { background-image: url('../assets/weather-bg/sunny.svg'); }`
  - `[data-daynight="day"][data-weather="rain"] .weather-bg-layer { background-image: url('../assets/weather-bg/rain.svg'); }`
  - `[data-daynight="day"][data-weather="cloudy"] .weather-bg-layer { background-image: url('../assets/weather-bg/cloudy.svg'); }`
  - `[data-daynight="day"][data-weather="snow"] .weather-bg-layer { background-image: url('../assets/weather-bg/snow.svg'); }`
- 验收条件：common.css 含 4 类 `[data-daynight="day"][data-weather="..."]` 选择器；路径指向既有白天 SVG。

### 4.6 追加夜晚昼夜背景选择器（4 类夜晚组合）
- [ ] 追加 4 类夜晚昼夜背景选择器（对应 C-46，D-VUI-07/D-VUI-09，REQ-VUI-UBI-018/020/023），引用组1 新增的夜晚 SVG：
  - `[data-daynight="night"][data-weather="sunny"] .weather-bg-layer { background-image: url('../assets/weather-bg/sunny-night.svg'); }`
  - `[data-daynight="night"][data-weather="rain"] .weather-bg-layer { background-image: url('../assets/weather-bg/rain-night.svg'); }`
  - `[data-daynight="night"][data-weather="cloudy"] .weather-bg-layer { background-image: url('../assets/weather-bg/cloudy-night.svg'); }`
  - `[data-daynight="night"][data-weather="snow"] .weather-bg-layer { background-image: url('../assets/weather-bg/snow-night.svg'); }`
- 验收条件：common.css 含 4 类 `[data-daynight="night"][data-weather="..."]` 选择器；路径指向 `sunny-night.svg`/`rain-night.svg`/`cloudy-night.svg`/`snow-night.svg`。

### 4.7 追加降级背景选择器
- [ ] 追加降级背景选择器（对应 C-47，REQ-VUI-UNW-024）：`[data-weather="default"] .weather-bg-layer { background-image: url('../assets/weather-bg/default.svg'); }` 降级背景（白天/夜晚共用 default.svg）。
- 验收条件：common.css 含 `[data-weather="default"]` 背景选择器；路径指向 `default.svg`。

### 4.8 追加加载门控样式
- [ ] 追加加载门控样式（对应 C-48，D-VUI-10/D-VUI-11，REQ-VUI-EVT-026/UBI-031）：
  - `.loading-gate { position: fixed; inset: 0; z-index: 9999; display: flex; align-items: center; justify-content: center; background: var(--paper); }` 全屏覆盖加载提示；
  - `.loading-gate[hidden] { display: none; }` 隐藏时不占位；
  - `.loading-gate__inner { display: flex; align-items: center; gap: 12px; }` 内层布局；
  - `.loading-gate__spinner { width: 32px; height: 32px; border: 3px solid var(--line); border-top-color: var(--leaf); border-radius: 50%; animation: loading-spin 0.8s linear infinite; }` 旋转图标；
  - `@keyframes loading-spin { to { transform: rotate(360deg); } }` 旋转动画；
  - `.loading-gate__text { color: var(--ink-soft); font-size: 16px; }` 加载文案样式。
- 验收条件：common.css 含 `.loading-gate` 与 `z-index: 9999`；含 `@keyframes loading-spin`；含 `.loading-gate__spinner` 旋转动画；含 `.loading-gate__text` 文案样式；纯 CSS 实现无外部资源。

### 4.9 追加昼夜切换过渡
- [ ] 追加昼夜切换过渡（对应 C-49，D-VUI-23，REQ-VUI-STA-022）：`body[data-layout="app"] .page--home .weather-bg-layer { transition: background-image 0.5s ease, opacity 0.5s ease; }` 昼夜切换平滑过渡 ≤500ms（复用既有 .weather-bg-layer transition 第 525 行）。
- 验收条件：common.css 含 transition 声明且时长 ≤500ms；既有 prefers-reduced-motion 全局规则自动禁用过渡。

### 4.10 追加响应式左侧栏宽度
- [ ] 在既有 `@media (min-width: 768px)`（第 424-427 行）块内追加：`body[data-layout="app"] .app-sidebar { flex: 0 0 220px; }` 左侧栏桌面端宽度（对应 C-50）。
- [ ] 在既有 `@media (min-width: 1024px)`（第 429-431 行）块内追加：`body[data-layout="app"] .app-sidebar { flex: 0 0 240px; }` 左侧栏宽屏加宽（对应 C-50）。
- 验收条件：768px 媒体查询块含 `.app-sidebar` flex:0 0 220px；1024px 媒体查询块含 `.app-sidebar` flex:0 0 240px；既有 768/1024 断点规则保留。

### 4.11 追加移动端抽屉媒体查询
- [ ] 追加移动端抽屉媒体查询（对应 C-51，D-VUI-14，REQ-VUI-OPT-009）：
  ```
  @media (max-width: 767px) {
    body[data-layout="app"] { flex-direction: column; }
    body[data-layout="app"] .app-sidebar { position: fixed; top: 0; left: 0; height: 100vh; z-index: 100; transform: translateX(-100%); transition: transform 0.3s ease; }
    body[data-layout="app"][data-sidebar-open] .app-sidebar { transform: translateX(0); }
  }
  ```
  左侧栏折叠为抽屉，汉堡按钮切换 `data-sidebar-open` 属性驱动抽屉滑出/收起；内容区占满视口不被挤压。
- 验收条件：common.css 含 `@media (max-width: 767px)` 与 `flex-direction: column`；含 `transform: translateX(-100%)` 抽屉折叠；含 `data-sidebar-open` 选择器抽屉滑出。

### 4.12 追加 prefers-reduced-motion 补充规则
- [ ] 追加 `@media (prefers-reduced-motion: reduce)` 补充规则（对应 C-52，REQ-VUI-UBI-023）：`.loading-gate__spinner { animation: none !important; }` + `body[data-layout="app"] .app-sidebar { transition: none !important; }` + `body[data-layout="app"] .page--home .weather-bg-layer { transition: none !important; }` 禁用加载旋转/抽屉过渡/昼夜切换过渡。
- 验收条件：common.css 含 `prefers-reduced-motion` 补充规则；含 `.loading-gate__spinner` animation:none；含 `.app-sidebar` transition:none。

### 4.13 确认既有样式不被修改
- [ ] 确认既有 `:root` 变量（第 1-21 行）不修改（对应 C-53）；既有 `.page--home` 背景规则（第 293-299 行）保留被覆盖不删除（对应 C-54）；既有 weather-* @keyframes 与选择器（第 475-483 行）不触碰（对应 C-55）；既有 tea-* @keyframes 与 data-timeperiod 选择器（第 504-528 行）不触碰（对应 C-55）；既有 `prefers-reduced-motion` 全局规则（第 289-291 行）不触碰（对应 C-56）；既有分栏布局 body.split-active（第 496-502 行）不触碰（D-VUI-15 兼容）。
- 验收条件：git diff 审查 common.css 既有 `:root`/`.page--home`/weather-*/tea-*/prefers-reduced-motion/split-active 无修改，仅末尾追加新规则 + 768/1024 媒体查询块内追加左侧栏规则；既有测试第 443-470/543-564 行断言全绿。

---

## 5. 修改 index.html 集成左侧栏与加载门控

> 对应改动点 C-01~C-14，需求 REQ-VUI-UBI-001/005/008/011/013/014/018/EVT-026/028/STA-016。
> 目标：在 `frontend/pages/index.html` 新增左侧栏 DOM、content pane 包裹、loading-gate、weather-fx-overlay、引入 sidebar.js/css、内联 IIFE 监听 weather-ready 事件。不改变既有表单字段 id/name 与 validate()/buildTimelineQuery() 逻辑。
> 前置依赖：组2（sidebar.js/css 文件存在方可引入）+ 组3（weather-ready 事件机制就绪方可监听）+ 组4（data-layout CSS 就绪方可验证全屏布局）。
> 约束：表单字段 crop/sowingDate/location 的 id/name 不变（C-12）；buildTimelineQuery/submit 跳转逻辑不变（C-13）；既有 weather-card/weather-bg-layer/term-ruler/form/history id 不变（C-14）。

### 5.1 head 内引入 sidebar.css
- [ ] 在 `frontend/pages/index.html` 第 9 行 `</link>`（voice-input.css 引入）之后，新增 `<link rel="stylesheet" href="../components/sidebar/sidebar.css">`（对应 C-01，REQ-VUI-UBI-008）。
- 验收条件：index.html 含 `<link rel="stylesheet" href="../components/sidebar/sidebar.css">`；位于 voice-input.css 之后。

### 5.2 body 标签新增 data-layout="app"
- [ ] 将 `frontend/pages/index.html` 第 11 行 `<body>` 改为 `<body data-layout="app">` 标识全屏布局（对应 C-02，D-VUI-04，REQ-VUI-UBI-011）。
- 验收条件：index.html 含 `<body data-layout="app">`。

### 5.3 新增 app-sidebar 容器 DOM
- [ ] 在 `frontend/pages/index.html` `<body>` 开标签之后、`<main>` 之前（第 12 行之前），新增 `<aside class="app-sidebar" id="app-sidebar" aria-label="主导航"></aside>`（对应 C-03，REQ-VUI-UBI-001/008）；入口由 sidebar.js renderEntries() 动态渲染。
- 验收条件：index.html 含 `<aside class="app-sidebar" id="app-sidebar"`；位于 `<body>` 之后、`<main>` 之前。

### 5.4 新增 sidebar-toggle 汉堡按钮
- [ ] 在 app-sidebar 之后、`<main>` 之前，新增 `<button class="sidebar-toggle" id="sidebar-toggle" aria-label="切换导航">☰</button>`（对应 C-04，REQ-VUI-OPT-009）；≥768px 由 sidebar.css 隐藏，<768px 显示。
- 验收条件：index.html 含 `<button class="sidebar-toggle" id="sidebar-toggle"`；含 `aria-label`。

### 5.5 main 标签新增 data-daynight="day"
- [ ] 将 `frontend/pages/index.html` 第 12 行 `<main class="page page--home">` 改为 `<main class="page page--home" data-daynight="day">`（对应 C-05，D-VUI-21，REQ-VUI-UBI-018）；默认白天属性，由 weather-visual.js init 运行时更新。
- 验收条件：index.html 含 `<main class="page page--home" data-daynight="day">`。

### 5.6 包裹既有内容进 pane-calendar
- [ ] 在 `frontend/pages/index.html` `<main>` 开标签之后插入 `<div class="content-pane" id="pane-calendar" data-pane="calendar">` 开始标签；在既有 history `</div>`（第 86 行）之后插入 `</div>` 闭合 pane-calendar（对应 C-06，D-VUI-03，REQ-VUI-UBI-005/014）。
- [ ] 包裹既有 header（第 13-23 行）/weather-bg-layer（第 25 行）/weather-card（第 26-31 行）/term-ruler（第 34 行）/form（第 36-78 行）/history（第 80-86 行）进 pane-calendar，**子元素 id/class 不变**（C-14）。
- 验收条件：index.html 含 `<div class="content-pane" id="pane-calendar" data-pane="calendar">`；既有 `id="weather-card"`/`id="weather-bg-layer"`/`id="timeline-form"`/`id="crop"`/`id="sowingDate"`/`id="location"`/`id="history"` 等子元素 id 不变；既有 63 断言不破坏。

### 5.7 新增 5 个占位 content pane
- [ ] 在 pane-calendar 闭合 `</div>` 之后，新增 5 个占位 content pane（对应 C-07，D-VUI-13，REQ-VUI-OPT-007）：
  ```html
  <div class="content-pane module-placeholder" id="pane-recommend" data-pane="recommend" hidden>该板块即将上线</div>
  <div class="content-pane module-placeholder" id="pane-plots" data-pane="plots" hidden>该板块即将上线</div>
  <div class="content-pane module-placeholder" id="pane-disease" data-pane="disease" hidden>该板块即将上线</div>
  <div class="content-pane module-placeholder" id="pane-my-crops" data-pane="my-crops" hidden>该板块即将上线</div>
  <div class="content-pane module-placeholder" id="pane-service" data-pane="service" hidden>该板块即将上线</div>
  ```
  默认 hidden，点击对应入口时由 sidebar.js selectModule 显示。
- 验收条件：index.html 含 5 个 `class="content-pane module-placeholder"` div；id 分别为 pane-recommend/pane-plots/pane-disease/pane-my-crops/pane-service；均含 `hidden` 属性；文案为"该板块即将上线"。

### 5.8 新增 weather-fx-overlay 动画叠加层
- [ ] 在 `frontend/pages/index.html` `<main>` 内（pane-calendar 之后或之前），新增 `<div class="weather-fx-overlay" id="weather-fx-overlay" aria-hidden="true"></div>` 动画叠加层（对应 C-08，D-VUI-06，REQ-VUI-UBI-013）；z-index:1 + pointer-events:none 由 common.css 组4 设置。
- 验收条件：index.html 含 `<div class="weather-fx-overlay" id="weather-fx-overlay"`；含 `aria-hidden="true"`。

### 5.9 新增 loading-gate 加载提示
- [ ] 在 `frontend/pages/index.html` `<body>` 内（`<main>` 之后或之前），新增 loading-gate 全屏加载提示（对应 C-09，D-VUI-10/D-VUI-11，REQ-VUI-EVT-026/UBI-031）：
  ```html
  <div class="loading-gate" id="loading-gate">
    <div class="loading-gate__inner">
      <span class="loading-gate__spinner" aria-hidden="true"></span>
      <span class="loading-gate__text">正在加载天气数据…</span>
    </div>
  </div>
  ```
  默认显示（无 hidden），天气就绪/超时后由内联 IIFE 监听 weather-ready 事件隐藏。
- 验收条件：index.html 含 `id="loading-gate"`；含 `loading-gate__spinner`；含 `正在加载天气数据`；默认无 hidden 属性（加载态可见）。

### 5.10 引入 sidebar.js 脚本
- [ ] 在 `frontend/pages/index.html` 第 100 行 `<script src="../components/voice-input/voice-input.js"></script>` 之后，新增 `<script src="../components/sidebar/sidebar.js"></script>`（对应 C-10，REQ-VUI-UBI-008）；加载顺序在 voice-input.js 之后、内联 `<script>` 之前。
- 验收条件：index.html 含 `<script src="../components/sidebar/sidebar.js"></script>`；位于 voice-input.js 之后、内联 `<script>` 之前。

### 5.11 内联 IIFE 末尾追加 Sidebar.init() 与 weather-ready 监听
- [ ] 在 `frontend/pages/index.html` 第 444 行 `renderHistory();` 之后或内联 IIFE 末尾 `})();` 之前，新增（对应 C-11，D-VUI-10/D-VUI-16，REQ-VUI-EVT-028/UBI-005）：
  ```javascript
  if (window.Sidebar) { window.Sidebar.init(); }
  window.addEventListener('weather-ready', function () {
    var lg = document.getElementById('loading-gate');
    if (lg) lg.hidden = true;
  });
  ```
  Sidebar.init() 编排左侧栏渲染 + 默认选中 calendar；weather-ready 事件监听隐藏 loading-gate（天气就绪/超时均触发）。
- 验收条件：index.html 含 `Sidebar.init()`；含 `window.addEventListener('weather-ready'`；含 `loading-gate` hidden 设置逻辑。

### 5.12 确认既有表单与提交逻辑不变
- [ ] 确认 `frontend/pages/index.html` 既有表单字段 `crop`（id/name）、`sowingDate`（id/name）、`location`（id/name）不变（对应 C-12，REQ-VUI 4.5.8）。
- [ ] 确认 `buildTimelineQuery`（第 399-407 行）与 submit 跳转逻辑（第 409-439 行，含 try-catch 分栏 + 回退整页跳转）不变（对应 C-13，REQ-VUI 4.5.8/STA-016）。
- [ ] 确认既有 weather-card/weather-bg-layer/term-ruler/form/history id 不变（对应 C-14，REQ-VUI-UBI-014/038）。
- 验收条件：git diff 审查 index.html 既有表单字段 id/name 与 buildTimelineQuery/submit 无修改，仅新增 sidebar DOM + content pane 包裹 + loading-gate + weather-fx-overlay + script/link + Sidebar.init/weather-ready 监听。

---

## 6. 新增测试断言

> 对应改动点 C-62~C-64 与 design.md 2.4.8 节断言清单，需求 REQ-VUI-UBI-038/039。
> 目标：在 `frontend/tests/pages.test.js` 末尾追加 test 块，复用既有 `indexHtml`/`commonCss`/`weatherVisualJs` 变量，新增 `sidebarJs`/`sidebarCss`/4 个夜晚 SVG 变量；断言以静态字符串/正则匹配为主（与既有 63 项风格一致），不执行 sidebar.js 运行时逻辑（因含 DOM 依赖），仅 judgeDayNight 纯函数可 require 执行。既有 63 项断言不删除/不注释/不弱化。
> 前置依赖：组1~组5 全部完成（断言需读取所有新增/修改文件）。

### 6.1 新增文件读取变量
- [ ] 在 `frontend/tests/pages.test.js` 第 28 行之后（文件读取基座区），新增变量（对应 C-62）：
  - `const sidebarJs = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));`
  - `const sidebarCss = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.css'));`
  - `const sunnyNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'sunny-night.svg'));`
  - `const rainNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'rain-night.svg'));`
  - `const cloudyNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'cloudy-night.svg'));`
  - `const snowNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'snow-night.svg'));`
- 验收条件：pages.test.js 含 `sidebarJs`/`sidebarCss`/`sunnyNightSvg`/`rainNightSvg`/`cloudyNightSvg`/`snowNightSvg` 6 个变量定义；路径指向 `frontend/components/sidebar/` 与 `frontend/assets/weather-bg/`。

### 6.2 新增 V4 左侧栏 DOM 结构与脚本引入断言
- [ ] 在 pages.test.js 第 569 行之后（文件末尾）新增 test 块 `V4 左侧栏：DOM 结构与脚本引入`（对应 design.md 2.4.8 第 1 行）：断言 `indexHtml` 含 `id="app-sidebar"`、`data-layout="app"`、`sidebar.js`、`sidebar.css`。
- 验收条件：新增 test 块通过；断言覆盖左侧栏 DOM 与脚本引入存在性。

### 6.3 新增 V4 sidebar.js 6 板块具名常量断言
- [ ] 新增 test 块 `V4 sidebar.js：6 板块具名常量`（对应 design.md 2.4.8 第 2 行）：断言 `sidebarJs` 含 `MODULE_ENTRIES`、`recommend`、`plots`、`calendar`、`disease`、`my-crops`、`service`、`种植推荐`、`我的地块`、`农事日历`、`病害检查`、`我的作物`、`客服`。
- 验收条件：新增 test 块通过；断言覆盖 6 板块标识与中文名称。

### 6.4 新增 V4 sidebar.js 默认选中与板块切换断言
- [ ] 新增 test 块 `V4 sidebar.js：默认选中与板块切换`（对应 design.md 2.4.8 第 3 行）：断言 `sidebarJs` 含 `DEFAULT_MODULE`、`'calendar'`、`selectModule`、`active`、`MODULE_PLACEHOLDER_TEXT`、`该板块即将上线`。
- 验收条件：新增 test 块通过；断言覆盖默认选中 calendar 与板块切换机制与占位提示文案。

### 6.5 新增 V4 sidebar.js 移动端抽屉断言
- [ ] 新增 test 块 `V4 sidebar.js：移动端抽屉`（对应 design.md 2.4.8 第 4 行）：断言 `sidebarJs` 含 `toggleSidebar`、`data-sidebar-open`。
- 验收条件：新增 test 块通过；断言覆盖移动端抽屉切换机制。

### 6.6 新增 V4 sidebar.css 左侧栏样式断言
- [ ] 新增 test 块 `V4 sidebar.css：左侧栏样式`（对应 design.md 2.4.8 第 5 行）：断言 `sidebarCss` 含 `.app-sidebar`、`.sidebar-entry`、`.sidebar-entry.active`、`.module-placeholder`、`@media (max-width: 767px)`、`transform: translateX`。
- 验收条件：新增 test 块通过；断言覆盖左侧栏容器/入口/高亮/占位/移动端抽屉样式。

### 6.7 新增 V4 全屏布局 data-layout 与移除整页背景断言
- [ ] 新增 test 块 `V4 全屏布局：data-layout 与移除整页背景`（对应 design.md 2.4.8 第 6 行）：断言 `commonCss` 含 `[data-layout="app"]`、`background-image: none` 或 `background-image:none`；断言 `indexHtml` 含 `data-layout="app"`。
- 验收条件：新增 test 块通过；断言覆盖全屏 flex 布局与整页背景移除。

### 6.8 新增 V4 content pane 板块切换容器断言
- [ ] 新增 test 块 `V4 content pane：板块切换容器`（对应 design.md 2.4.8 第 7 行）：断言 `indexHtml` 含 `class="content-pane"`、`id="pane-calendar"`、`data-pane="calendar"`、`pane-recommend`、`pane-plots`、`pane-disease`、`pane-my-crops`、`pane-service`。
- 验收条件：新增 test 块通过；断言覆盖 6 个 content pane 存在性。

### 6.9 新增 V4 加载门控 loading-gate DOM 断言
- [ ] 新增 test 块 `V4 加载门控：loading-gate DOM`（对应 design.md 2.4.8 第 8 行）：断言 `indexHtml` 含 `id="loading-gate"`、`正在加载天气数据`、`weather-ready`。
- 验收条件：新增 test 块通过；断言覆盖加载门控 DOM 与 weather-ready 事件监听。

### 6.10 新增 V4 加载门控 CSS 样式断言
- [ ] 新增 test 块 `V4 加载门控：CSS 样式`（对应 design.md 2.4.8 第 9 行）：断言 `commonCss` 含 `.loading-gate`、`@keyframes loading-spin`、`.loading-gate__spinner`。
- 验收条件：新增 test 块通过；断言覆盖加载门控 CSS 样式与旋转动画。

### 6.11 新增 V4 昼夜动画 4 类夜晚 SVG 素材断言
- [ ] 新增 test 块 `V4 昼夜动画：4 类夜晚 SVG 素材`（对应 design.md 2.4.8 第 10 行）：断言 `sunnyNightSvg`/`rainNightSvg`/`cloudyNightSvg`/`snowNightSvg` 均含 `<svg`。
- 验收条件：新增 test 块通过；断言覆盖 4 类夜晚 SVG 文件存在且为合法 SVG。

### 6.12 新增 V4 昼夜动画 CSS 属性选择器驱动断言
- [ ] 新增 test 块 `V4 昼夜动画：CSS 属性选择器驱动`（对应 design.md 2.4.8 第 11 行）：断言 `commonCss` 含 `[data-daynight="day"`、`[data-daynight="night"`、`sunny-night.svg`、`rain-night.svg`、`cloudy-night.svg`、`snow-night.svg`。
- 验收条件：新增 test 块通过；断言覆盖昼夜属性选择器与夜晚 SVG 路径引用。

### 6.13 新增 V4 weather-visual.js 昼夜判定与加载门控断言
- [ ] 新增 test 块 `V4 weather-visual.js：昼夜判定与加载门控`（对应 design.md 2.4.8 第 12 行）：断言 `weatherVisualJs` 含 `judgeDayNight`、`data-daynight`、`weather-ready`。
- 验收条件：新增 test 块通过；断言覆盖昼夜判定函数与加载门控事件机制。

### 6.14 新增 V4 weather-visual.js judgeDayNight 纯函数断言
- [ ] 新增 test 块 `V4 weather-visual.js：judgeDayNight 纯函数`（对应 design.md 2.4.8 第 13 行）：`const WV = require('../assets/js/weather-visual.js');` 后验证：
  - `assert.strictEqual(WV.judgeDayNight('morning'), 'day');`
  - `assert.strictEqual(WV.judgeDayNight('forenoon'), 'day');`
  - `assert.strictEqual(WV.judgeDayNight('noon'), 'day');`
  - `assert.strictEqual(WV.judgeDayNight('afternoon'), 'day');`
  - `assert.strictEqual(WV.judgeDayNight('evening'), 'night');`
  - `assert.strictEqual(WV.judgeDayNight('night'), 'night');`
  - `assert.strictEqual(WV.judgeDayNight(null), 'day');`（非法输入安全回退）
- 验收条件：新增 test 块通过；纯函数 require 执行无 DOM 依赖报错；6 时段映射 + 非法回退均正确。

### 6.15 新增 V4 动画叠加层 weather-fx-overlay 断言
- [ ] 新增 test 块 `V4 动画叠加层：weather-fx-overlay`（对应 design.md 2.4.8 第 14 行）：断言 `indexHtml` 含 `weather-fx-overlay`；断言 `commonCss` 含 `.weather-fx-overlay`、`pointer-events: none`。
- 验收条件：新增 test 块通过；断言覆盖动画叠加层 DOM 与不阻挡交互样式。

### 6.16 新增 V4 既有 63 断言兼容性复核
- [ ] 新增 test 块 `V4 既有 63 断言兼容性复核`（对应 design.md 2.4.8 第 15 行）：确认 `indexHtml` 含 `id="weather-card"`、`id="weather-bg-layer"`、`id="timeline-form"`、`id="crop"`、`id="sowingDate"`、`id="location"`（包裹 content pane 后仍存在）。
- 验收条件：新增 test 块通过；既有 63 项断言全绿（运行 `node --test frontend/tests/pages.test.js` 无失败）。

### 6.17 确认既有 63 项断言不被弱化
- [ ] 确认 `frontend/tests/pages.test.js` 既有 63 项断言（第 30-569 行）不删除、不注释、不弱化（对应 C-64，REQ-VUI-UBI-038）。
- 验收条件：git diff 审查 pages.test.js 既有 63 项 test 块无修改，仅第 28 行之后新增变量 + 第 569 行之后追加新 test 块。

---

## 7. 回归验证与交付

> 对应需求 REQ-VUI-UBI-038/039、spec 4.5 兼容性、design.md 2.5 兼容性分析。
> 目标：确认前端测试全绿、左侧栏与板块切换正常、全屏布局与响应式无横滚、昼夜动画正确、加载门控无闪现、降级不白屏、不触碰项未被修改、Git 提交。
> 前置依赖：组1~组6 全部完成。

### 7.1 运行前端测试确认全绿
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 63 项断言 + 新增断言（组6 约 16 个 test 块）全部通过，无失败（REQ-VUI-UBI-038/039）。
- 验收条件：测试输出全绿，总数 = 63 + 组6 新增 test 块数；无失败。

### 7.2 浏览器验证左侧栏与板块切换
- [ ] 在浏览器打开 `frontend/pages/index.html`，验证（REQ-VUI-UBI-001/002/005/EVT-003/STA-004/OPT-007）：
  - 页面左侧存在固定导航栏，含 6 个入口（种植推荐/我的地块/农事日历/病害检查/我的作物/客服），顺序正确；
  - 默认选中"农事日历"入口高亮，右侧展示既有主页内容（天气卡 + 表单 + 历史记录）；
  - 点击其他入口（如"种植推荐"）切换为占位提示"该板块即将上线"，该入口高亮，其余非高亮；
  - 恰有 1 个入口高亮；
  - 右侧内容区纵向滚动时左侧栏位置固定不动；
  - 切换 ≤100ms 视觉反馈。
- 验收条件：6 入口存在且顺序正确；默认 calendar 高亮；板块切换正确；恰 1 个高亮；左侧栏固定不滚动；切换 ≤100ms。

### 7.3 浏览器验证全屏布局与响应式
- [ ] 验证全屏布局（REQ-VUI-UBI-011/012/013/014/015/UNW-017）：
  - 内容区占满整个视口（100vw/100vh），无大面积留白；
  - 无整页背景图片作为页面底图（由内容区图片 + 天气动画叠加层构成视觉）；
  - 天气动画叠加层显示在内容图片之上，点击/输入表单字段正常响应（动画层不阻挡交互）；
  - 品牌头、天气卡、表单（作物/播种日期/所在地）、历史记录均完整可见可交互。
- [ ] 验证响应式三档（REQ-VUI-OPT-009/UNW-017）：
  - 375px 宽度：左侧栏折叠为抽屉，汉堡按钮可见，点击抽屉滑出，内容区占满视口不被挤压，无横向滚动；
  - 768px 宽度：左侧栏 220px 固定，内容区占剩余宽度，无横向滚动；
  - 1024px 宽度：左侧栏 240px 固定，内容区占剩余宽度，无横向滚动。
- [ ] 验证分栏态兼容（REQ-VUI-STA-016）：搜索后进入分栏态，左侧栏保持固定，右侧内容区内主页与时间线分栏展示。
- 验收条件：全屏铺满视口；无整页背景；动画叠加层不阻挡交互；核心内容完整可见；三档无横滚；移动端抽屉正常；分栏态左侧栏固定。

### 7.4 浏览器验证昼夜动画
- [ ] 验证昼夜动画匹配（REQ-VUI-UBI-018/019/020/022/023）：
  - 白天时段（修改系统时间至日出~日落之间）打开主页：显示白天对应动画素材（sunny.svg/rain.svg/cloudy.svg/snow.svg），动画正常播放可见；
  - 夜晚时段（修改系统时间至日落~次日日出之间）打开主页：显示夜晚对应动画素材（sunny-night.svg/rain-night.svg/cloudy-night.svg/snow-night.svg），动画正常播放可见，与白天可视觉区分；
  - 昼夜切换瞬间：动画素材平滑过渡，无跳变/闪烁/白屏，过渡 ≤500ms；
  - 4 类天气 × 2 昼夜 = 8 种组合均正确匹配；
  - 动画为纯 CSS @keyframes + 属性选择器驱动，无 JS 动画循环。
- 验收条件：白天/夜晚动画正确匹配；8 组合均正确；切换平滑 ≤500ms；纯 CSS 驱动。

### 7.5 浏览器验证加载门控
- [ ] 验证加载门控消除闪现（REQ-VUI-EVT-026/027/028/029/OPT-030/UBI-031/032）：
  - 打开主页且天气未就绪时：页面展示"正在加载天气数据…"加载提示，不展示降级默认页面；
  - 加载期间不闪现默认页面或空白；
  - 天气数据就绪后：加载提示隐藏，切换为主页内容（真实天气展示与动画）；
  - 表单字段加载完成后可正常填写提交（未阻塞）；
  - 模拟天气请求超时（>8s）或失败：加载提示隐藏，进入降级态展示主页内容（默认主题 + 中性提示），不永久停留加载态。
- 验收条件：加载期间展示加载提示不闪现默认页面；就绪后切换主页内容；超时后进入降级不永久停留；表单可填写提交。

### 7.6 浏览器验证降级场景
- [ ] 模拟天气数据不可用场景（拒绝定位授权/断网/Open-Meteo 超时/响应结构非法/weathercode 未识别），验证（REQ-VUI-UNW-024/035/UBI-036/037）：
  - 降级为默认主题：default.svg 背景 + 默认主题色 + 中性提示文案（"天气数据暂不可用"）；
  - 不白屏、不抛未捕获 JS 错误（控制台无错误）；
  - 表单仍可正常填写提交；
  - 既有 weather-visual.js 的 WMO_MAP/normalizeWeather/inferSeason/fallback 归一化与降级语义不变。
- 验收条件：降级展示默认主题与中性提示；控制台无未捕获错误；表单可提交；既有归一化降级不变。

### 7.7 验证 prefers-reduced-motion
- [ ] 开启浏览器 `prefers-reduced-motion: reduce` 偏好（DevTools Rendering 勾选），验证（REQ-VUI-UBI-023）：
  - 所有昼夜动画禁用（静态背景图）；
  - 加载旋转图标禁用（静态）；
  - 左侧栏抽屉过渡禁用（即时切换）；
  - 昼夜切换过渡禁用（即时切换）。
- 验收条件：减少动效模式下无动画播放；所有 transition/animation 禁用；页面静态展示。

### 7.8 验证不触碰项未被修改
- [ ] 审查 `git diff --name-only`，确认改动文件均在 `frontend/` 下且仅限（REQ-VUI 1.4 职责边界）：
  - `frontend/assets/weather-bg/sunny-night.svg`（新增）
  - `frontend/assets/weather-bg/rain-night.svg`（新增）
  - `frontend/assets/weather-bg/cloudy-night.svg`（新增）
  - `frontend/assets/weather-bg/snow-night.svg`（新增）
  - `frontend/components/sidebar/sidebar.js`（新增）
  - `frontend/components/sidebar/sidebar.css`（新增）
  - `frontend/assets/js/weather-visual.js`（修改）
  - `frontend/assets/css/common.css`（修改）
  - `frontend/pages/index.html`（修改）
  - `frontend/tests/pages.test.js`（修改）
- [ ] 确认不触碰项无修改：`timeline.html`/`timeline-logic.js`/`time-period.js`/`crop-map.js`/`voice-input.js`/`voice-input.css`/`photo-capture.js`/`history-store.js`/`chart-client.js`/`mock-data.js`/后端/nginx/shared/data/deploy/docs/contract。
- 验收条件：git diff --name-only 输出的文件均在上述 10 个允许范围内；不触碰项无修改。

### 7.9 验证无第三方依赖引入
- [ ] 审查 `sidebar.js`/`weather-visual.js`/`index.html`/`common.css`/`sidebar.css`，确认无 `import`/`require` 第三方库语句（不含 GSAP/Lottie/Vue/React/路由库等）；审查项目无新增 package.json 依赖；确认纯原生 JS + 原生 CSS 实现（REQ-VUI 4.5.3）。
- 验收条件：无新增第三方依赖；动画纯 CSS @keyframes；布局纯 CSS flexbox；无构建工具配置。

### 7.10 验证既有逻辑不变
- [ ] 审查确认（REQ-VUI-UBI-037/025/4.5.8）：
  - 表单字段 crop/sowingDate/location 的 id/name 不变；
  - validate()/buildTimelineQuery() 逻辑不变；
  - weather-visual.js 的 WMO_MAP/normalizeWeather/inferSeason/fetchWeather/buildWeatherInfo/renderCard/applyTheme 既有逻辑不变；
  - time-period.js 的 judgeTimePeriod/parseSunTime/applyTimePeriod/applySunData 6 时段判定输出不变；
  - common.css 的 :root 变量/既有 weather-*/tea-* 样式/既有分栏 split-active 不变；
  - pages.test.js 既有 63 项断言未删除/弱化。
- 验收条件：既有核心逻辑与断言全部保留不变；既有 63 项断言全绿。

### 7.11 Git 提交与推送
- [ ] 提交并推送至 GitHub 仓库，提交信息体现 V4 T01 任务编号（如 `V4 T01: 主页 UI 改版与天气视觉动态适配`）。
- 验收条件：Git 提交成功，提交信息含 V4 T01；推送至远程仓库。

---

## 风险点与边界情况

> 以下为执行中需特别注意的边界情况（源自 design.md 2.6 节风险表）。

| 风险 | 触发条件 | 影响评估 | 缓解措施 | 关联任务 |
|------|---------|---------|---------|---------|
| 包裹 content pane 破坏既有 DOM 查询 | 既有 JS 用 `querySelector('.page--home > .weather-card')` 直接子选择器 | 中：直接子选择器失效 | 审查既有代码：weather-visual.js 用 `getElementById`（id 查询不受包裹影响）；index.html 内联 IIFE 用 `getElementById` 查询表单字段（不受影响）；无直接子选择器 | 5.6/7.10 |
| applyBackground 改实现破坏既有背景显示 | applyBackground 从赋值 style 改为设属性，CSS 选择器未正确配置 | 中：背景停留 default | CSS C-45/C-46/C-47 选择器覆盖 8 组合 + default；既有 weather-bg-layer 默认 background-image:default.svg（第 441 行）兜底；测试断言验证选择器存在 | 3.2/4.5/4.6/4.7 |
| 加载门控永久停留 | weather-ready 事件未触发（init 异常未 catch） | 高：用户永久看到加载提示 | weather-visual.js init 内部 try-catch 确保任何路径都触发 weather-ready；fallback 末尾也触发；既有 8s 超时保证最终触发 | 3.3/3.4/7.5 |
| 左侧栏遮挡表单/天气卡 | 左侧栏 z-index 过高或内容区未留出宽度 | 中：核心内容被遮挡 | sidebar z-index 低于表单/天气卡；内容区 flex:1 自动占剩余宽度；移动端抽屉 transform 不挤压内容；表单/天气卡 z-index:2 | 2.8/4.4/7.3 |
| 夜晚 SVG 素材缺失或体积超限 | 夜晚 SVG 文件未创建或 >300KB | 中：夜晚无动画或加载慢 | 测试断言验证 4 类夜晚 SVG 存在；素材创建时控制 ≤300KB；缺失时 CSS 选择器无匹配，weather-bg-layer 保持 default.svg 兜底 | 1.1~1.4/6.11 |
| 昼夜判定错误（高纬度极昼/极夜） | 日出日落异常或缺失 | 低：动画按默认边界切换 | 复用既有 time-period.js DEFAULT_SUNRISE/DEFAULT_SUNSET（06:00~18:00）兜底；judgeDayNight 非法输入返回 day（安全回退） | 3.1/7.4 |
| 板块切换白屏 | content pane DOM 异常或 selectModule 抛错 | 中：右侧内容区空白 | sidebar.js selectModule 内部 try-catch，异常时保持当前板块或展示占位提示；未实现板块展示占位提示不白屏 | 2.3/7.2 |
| 全屏化导致横向滚动 | 内容区宽度超出视口 | 中：出现横向滚动条 | 既有 `html,body { overflow-x: hidden; max-width: 100%; }`（第 25-30 行）防溢出；flex 布局自动适应 | 4.1/7.3 |
| 移动端抽屉与既有分栏冲突 | <768px 同时触发抽屉与分栏 | 低：布局层次混乱 | 移动端分栏为上下排列（既有第 531-536 行），抽屉为 overlay 不挤压内容；两者独立 | 4.11/7.3 |
| weather-ready 事件重复触发 | init 多次调用或 fallback 后再次 init | 低：loading-gate 多次隐藏（幂等） | loading-gate.hidden = true 幂等；weather-visual.js init 幂等（既有设计） | 3.3/3.4/7.5 |
| 既有 weather-card hidden 与加载门控时序 | weather-card 初始 hidden，加载门控期间是否显示 | 低：加载期间 weather-card 保持 hidden 由 loading-gate 覆盖 | D-VUI-11 加载门控不干预 weather-card hidden 逻辑；天气就绪后 renderCard 显示 + loading-gate 隐藏 | 5.9/7.5 |
| sidebar.js 在 index.html 之外页面误加载 | sidebar.js 仅 index.html 引入，其他页面不引入 | 无影响 | sidebar.js 仅在 index.html `<script>` 引入（C-10）；timeline.html 不引入 | 5.10 |
| CSS 选择器优先级冲突 | 新增 `[data-layout="app"] .page--home` 与既有 `.page--home` 优先级 | 低：可能背景未覆盖 | 新选择器含 body 属性选择器 + .page--home，特异性高于既有 `.page--home`，覆盖生效 | 4.1/4.2 |
| 既有 63 测试断言被破坏 | 新增内容改变既有断言匹配的字符串/结构 | 高：测试失败 | 包裹 content pane 保留既有 id/class；weather-visual.js 保留既有函数名；common.css 保留既有字符串；新增断言为追加不修改既有 | 5.6/3.2/4.13/6.17 |

---

## 验收标准汇总

| 验收维度 | 验收条件 | 对应需求 |
|---------|---------|---------|
| 左侧栏导航 | 主页左侧固定导航栏含 6 入口（种植推荐/我的地块/农事日历/病害检查/我的作物/客服），顺序正确，默认选中农事日历高亮，切换 ≤100ms，恰 1 个高亮 | REQ-VUI-UBI-001/002/005/006/EVT-003/STA-004 |
| 板块占位提示 | 未实现板块展示"该板块即将上线"占位提示，不白屏不报错不跳转 404 | REQ-VUI-OPT-007/UNW-010 |
| 主页全屏化 | 内容区占满视口（100vw/100vh），无整页背景图片，动画叠加层在内容图片之上不阻挡交互，核心内容完整可见 | REQ-VUI-UBI-011/012/013/014/015 |
| 夜间动画修复 | 白天/夜晚分别匹配动画素材，24 小时均有正确视觉，8 组合（4 天气 × 2 昼夜）+ 默认降级，切换 ≤500ms 平滑过渡 | REQ-VUI-UBI-018/019/020/022/023/025 |
| 加载门控 | 天气未就绪时展示"正在加载天气数据…"，不闪现默认页面，就绪/超时后切换为主页内容，不永久停留 | REQ-VUI-EVT-026/027/028/029/OPT-030/UBI-031/032 |
| 天气信息保留 | 天气就绪后展示地点/温度/状况/湿度/风速 5 项信息，4 类天气动画可区分，降级为默认主题不白屏 | REQ-VUI-UBI-033/034/UNW-035/UBI-036/037 |
| 响应式适配 | 375/768/1024px 三档无横向滚动，移动端左侧栏折叠为抽屉不挤压内容区 | REQ-VUI-OPT-009/UNW-017 |
| 分栏兼容 | 分栏态左侧栏保持固定，右侧内容区内主页与时间线分栏展示 | REQ-VUI-STA-016 |
| 范围约束 | 仅改 frontend/，不触碰后端/nginx/shared/data/deploy/docs/timeline.html/time-period.js 等 | REQ-VUI 1.4/4.5.4 |
| 无第三方依赖 | 纯原生 JS + CSS，无框架/构建工具/第三方库（含 GSAP/Lottie/Vue/React） | REQ-VUI 4.5.3 |
| 既有逻辑不变 | 表单字段 id/name 不变，validate/buildTimelineQuery 不变，weather-visual.js 归一化/降级不变，time-period.js 6 时段判定不变 | REQ-VUI-UBI-037/025/4.5.8 |
| 测试全绿 | 既有 63 项 + 新增断言全绿，不弱化既有断言 | REQ-VUI-UBI-038/039 |
| 目录规范 | sidebar.js/css 放 `frontend/components/sidebar/`，夜晚 SVG 放 `frontend/assets/weather-bg/` | REQ-VUI-UBI-008/021 |