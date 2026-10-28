# T01 主页天气与视觉动态适配 · 编码任务规划（t01_weather_visual）

> 本任务规划基于 `t01_weather_visual/spec.md`（42 条 EARS 需求 REQ-TWV-001~042）与 `design.md`（18 条关键决策 D-TWV-01~18、47 项改动点 C-01~C-47、17 条风险 R-01~R-17）生成。
> 全局约束继承自 `stage2_frontend`（60 条）与 `t00_directory_migration`（49 条）：纯原生 JS + 原生 CSS、前端 41/41 测试全绿仅允许新增断言、不引入框架/构建工具/第三方库、不修改后端/nginx/shared/data/deploy/docs、mock 保持 null 占位不造假、crop-map.js 5 个 cropId 不变、时间线页（timeline.html / timeline-logic.js）天气逻辑不变。
> 改动范围：仅 `frontend/` 目录。新增 7 个文件（weather-visual.js + 5 个 weather-bg SVG + 1 个目录），修改 3~4 个文件（index.html + common.css + pages.test.js + 可选 mock-data.js）。

---

## 任务依赖关系总览

```
组1 weather-bg 资源 ──┬──→ 组3 index.html DOM ──┬──→ 组5 测试断言 ──→ 组6 回归验证
                       │                          │
组2 weather-visual.js ─┘──→ 组4 common.css 样式 ──┘
                                                        组7 可选 mock（可与组5并行）
```

- 组1、组2 无前置依赖，可并行开发。
- 组3 依赖组1（背景资源路径）+ 组2（script 引入）。
- 组4 依赖组1（背景资源路径）+ 组3（DOM class/id 选择器）。
- 组5 依赖组1~组4全部完成。
- 组6 依赖组5。
- 组7 可与组5并行，无强制依赖。

---

## 1. 新建天气背景资源

> 对应改动点 C-27~C-32，需求 REQ-TWV-UBI-015~019。
> 目标：在 `frontend/assets/weather-bg/` 下创建 5 类 SVG 矢量背景图，遵循 T00 目录规范，单张 ≤300KB。

### 1.1 创建 weather-bg 目录
- [ ] 新建目录 `frontend/assets/weather-bg/`，用于存放按天气类型分类的背景图片资源（对应 C-27）。
- 验收条件：目录存在，路径符合 `frontend/assets/` 子目录规范（T00 目录规范 REQ-TDM-UBI-008）。

### 1.2 创建晴天背景 sunny.svg
- [ ] 新建 `frontend/assets/weather-bg/sunny.svg`，SVG 矢量图形，表现晴天视觉（暖金/绿色调），单张体积 ≤300KB（对应 C-28）。
- 验收条件：文件存在；为合法 SVG（含 `<svg>` 根元素）；体积 ≤300KB；`background-size: cover` 下三档宽度无变形。

### 1.3 创建雨天背景 rain.svg
- [ ] 新建 `frontend/assets/weather-bg/rain.svg`，SVG 矢量图形，表现雨天视觉（冷蓝色调），单张体积 ≤300KB（对应 C-29）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB。

### 1.4 创建多云背景 cloudy.svg
- [ ] 新建 `frontend/assets/weather-bg/cloudy.svg`，SVG 矢量图形，表现多云视觉（灰蓝色调），单张体积 ≤300KB（对应 C-30）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB。

### 1.5 创建雪天背景 snow.svg
- [ ] 新建 `frontend/assets/weather-bg/snow.svg`，SVG 矢量图形，表现雪天视觉（冷白蓝色调），单张体积 ≤300KB（对应 C-31）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB。

### 1.6 创建默认背景 default.svg
- [ ] 新建 `frontend/assets/weather-bg/default.svg`，SVG 矢量图形，表现中性默认视觉（降级用），单张体积 ≤300KB（对应 C-32）。
- 验收条件：文件存在；为合法 SVG；体积 ≤300KB。

---

## 2. 新建天气视觉逻辑模块

> 对应改动点 C-06~C-16，需求 REQ-TWV-UBI-009~014/027~031、EVT-010、OPT-011/030、UNW-012/027。
> 目标：新建 `frontend/assets/js/weather-visual.js`，以 IIFE + `window.WeatherVisual` 暴露 API，与既有 `timeline-logic.js`/`history-store.js` 模块风格一致。纯原生 JS，无 import/require 第三方库。

### 2.1 创建文件骨架与具名常量
- [ ] 新建 `frontend/assets/js/weather-visual.js`，以 IIFE 包裹，末尾 `window.WeatherVisual = { ... }` 暴露 API（对应 C-06）。
- [ ] 在模块顶部定义具名常量（对应 C-07）：
  - `WEATHER_CODE_MAP`：WMO weathercode → 四类映射（0/1→sunny，2/3/45/48→cloudy，51-67/80-82/95-99→rain，71/73/75/77/85-86→snow，未识别→default）；
  - `WEATHER_TYPE_LABEL`：四类→中文标签（sunny→晴、rain→雨、cloudy→多云、snow→雪、default→未知）；
  - `SEASON_MAP`：四季→标签（spring/summer/autumn/winter）；
  - `OPEN_METEO_URL = 'https://api.open-meteo.com/v1/forecast'`；
  - `WEATHER_REQUEST_TIMEOUT_MS = 8000`。
- 验收条件：文件存在；含 IIFE 结构与 `window.WeatherVisual` 暴露；含上述 5 个具名常量；不含 `import`/`require` 第三方库语句。

### 2.2 实现 weathercode 归一化函数
- [ ] 实现 `normalizeWeatherCode(code)`，接收整数 weathercode，依据 `WEATHER_CODE_MAP` 返回 `{ weatherType, weatherLabel }`；未识别编码回退 `{ weatherType: 'default', weatherLabel: '未知' }`，不抛异常（对应 C-11，D-TWV-08）。
- 验收条件：`normalizeWeatherCode(0)` → `{weatherType:'sunny', weatherLabel:'晴'}`；`normalizeWeatherCode(61)` → `{weatherType:'rain', weatherLabel:'雨'}`；`normalizeWeatherCode(999)` → `{weatherType:'default', weatherLabel:'未知'}`。

### 2.3 实现季节推断函数
- [ ] 实现 `inferSeason(date)`，按公历月份划分季节：3-5→spring、6-8→summer、9-11→autumn、12/0/1→winter；缺省取 `new Date()`；不发起网络请求（对应 C-12，D-TWV-09）。
- 验收条件：`inferSeason(new Date(2026,5,1))` → `'summer'`；`inferSeason(new Date(2026,0,1))` → `'winter'`；`inferSeason(new Date(2026,2,1))` → `'spring'`；`inferSeason(new Date(2026,8,1))` → `'autumn'`。

### 2.4 实现地理定位获取函数
- [ ] 实现 `fetchWeatherByGeo()`，优先 `navigator.geolocation.getCurrentPosition`（用户授权后执行）；拒绝/不支持时回退所在地输入推断经纬度（复用 `window.CropMap.CROPS` 中 lat/lng 字段，只读不修改 crop-map.js）；所在地为空时 reject `'location-empty'`（对应 C-09，D-TWV-03）。
- 验收条件：函数存在；含 `navigator.geolocation.getCurrentPosition` 调用；含回退所在地推断分支；不静默调用定位（须在授权回调中执行）。

### 2.5 实现 Open-Meteo 请求函数
- [ ] 实现 `fetchWeatherByOpenMeteo(lat, lon)`，前端直连 `OPEN_METEO_URL`，用 `URLSearchParams` 安全拼装参数（`latitude`/`longitude`/`current_weather=true`/`hourly=relative_humidity_2m,wind_speed_10m`）；用 `AbortController` + `setTimeout(WEATHER_REQUEST_TIMEOUT_MS)` 实现 8s 超时，超时 `abort()` 并 reject `'timeout'`；HTTP 非 2xx reject `'network'`；响应缺 `current_weather.temperature`/`current_weather.weathercode` reject `'invalid-structure'`（对应 C-10，D-TWV-01/13）。
- 验收条件：函数存在；含 `api.open-meteo.com` URL；含 `current_weather` 参数；含 `AbortController` 与 `8000` 超时常量；含结构校验分支；参数用 `URLSearchParams` 拼装（防注入）。

### 2.6 实现天气信息对象组装函数
- [ ] 实现 `buildWeatherInfo(raw, locationText, season)`，组合原始天气数据（temperature/weathercode/humidity/windSpeed）+ 地点文本 + 季节为统一 `WeatherInfo` 对象（含 location/temperature/weatherType/weatherLabel/humidity/windSpeed/season/source 字段）；内部调用 `normalizeWeatherCode`；缺失字段填 null（对应 C-13）。
- 验收条件：函数存在；返回对象含 8 个字段；`weatherType` 来自 `normalizeWeatherCode` 归一化结果。

### 2.7 实现天气卡渲染函数
- [ ] 实现 `renderWeatherCard(info)`，将地点/温度/状况/湿度/风速写入 `#weather-card` 各字段 `textContent`（安全渲染，禁止 innerHTML，防 XSS）；缺失字段展示 `'--'`；节点缺失时静默降级不抛错（对应 C-14，REQ-TWV 4.3.3）。
- 验收条件：函数存在；含 `textContent` 赋值（不含 `innerHTML` 拼接外部数据）；含 `'--'` 占位逻辑；含节点缺失 try-catch。

### 2.8 实现主题切换函数
- [ ] 实现 `applyTheme(weatherType, season)`，在 `#weather-card` 节点上设置 `data-weather` 与 `data-season` 属性（如 `setAttribute('data-weather', weatherType)`），CSS 据此切换背景与主题色；不通过 JS 逐个赋值样式；节点缺失时静默降级（对应 C-15，D-TWV-07）。
- 验收条件：函数存在；含 `setAttribute('data-weather', ...)` 与 `setAttribute('data-season', ...)`；不含逐个 `style.xxx =` 赋值主题色。

### 2.9 实现降级函数
- [ ] 实现 `fallback(reason)`，设置 `data-weather="default"`，按 reason 映射中性提示文案（如 `'timeout'`/`'network'`/`'invalid-structure'` → `'天气数据暂不可用'`；`'geo-denied'` → `'定位未授权'`；`'geo-unsupported'` → `'定位不可用'`；`'location-empty'` → `'请输入所在地'`），展示于 `.weather-fallback` 区域；不抛未捕获错误；内部 try-catch（对应 C-16，D-TWV-12）。
- 验收条件：函数存在；含 `data-weather="default"` 设置；含中性提示文案（"天气数据暂不可用"等）；含 try-catch 不抛错。

### 2.10 实现入口编排函数
- [ ] 实现 `init(options?)`，异步编排：定位→请求→归一化→季节推断→组装→渲染→主题切换；任一环节失败 catch 后调 `fallback(reason)`；不 `await` 阻塞主线程（异步 Promise 链，表单可同时交互）；`options.preferBackend` 为 true 时优先复用后端 `/api/timeline` 响应中天气信息（REQ-TWV-OPT-011）；init 幂等，降级后刷新可重试（对应 C-08，D-TWV-02/18）。
- 验收条件：函数存在；含完整编排链（fetchWeatherByGeo → fetchWeatherByOpenMeteo → normalizeWeatherCode → inferSeason → buildWeatherInfo → renderWeatherCard → applyTheme）；含 catch → fallback 分支；含后端复用可选分支；不阻塞表单（异步执行）。

---

## 3. 修改主页 DOM 集成天气卡

> 对应改动点 C-01~C-05，需求 REQ-TWV-UBI-001~008/036/040。
> 目标：在 `frontend/pages/index.html` 品牌头与表单之间插入天气信息卡 DOM，引入 weather-visual.js 并调用 init。不改变既有表单字段 id/name 与提交跳转逻辑。
> 前置依赖：组1（背景资源路径）、组2（weather-visual.js 脚本）。

### 3.1 插入天气信息卡 DOM 结构
- [ ] 在 `frontend/pages/index.html` 第 22 行 `</header>` 之后、第 25 行 `<div class="term-ruler">` 之前，插入天气信息卡 DOM 结构（对应 C-01，D-TWV-10）：
  ```html
  <section class="weather-card" id="weather-card" data-weather="default" data-season="summer">
    <div class="weather-bg-layer weather-bg-layer-a"></div>
    <div class="weather-bg-layer weather-bg-layer-b"></div>
    <div class="weather-fx" aria-hidden="true"></div>
    <div class="weather-info">
      <span class="weather-loc" id="weather-loc">--</span>
      <span class="weather-temp" id="weather-temp">--</span>
      <span class="weather-desc" id="weather-desc">--</span>
      <span class="weather-humidity" id="weather-humidity">--</span>
      <span class="weather-wind" id="weather-wind">--</span>
    </div>
    <div class="weather-fallback" id="weather-fallback"></div>
  </section>
  ```
  含 5 个字段（地点/温度/状况/湿度/风速）+ 双层背景叠加（`.weather-bg-layer-a/b`）+ 动效图层（`.weather-fx`）+ 降级提示（`.weather-fallback`）。
- 验收条件：index.html 含 `id="weather-card"` 与 `data-weather="default"`；含 5 个字段 id（weather-loc/temp/desc/humidity/wind）；含 `.weather-fx` 与 `.weather-fallback`；天气卡位于 `</header>` 之后、`<div class="term-ruler">` 之前。

### 3.2 引入 weather-visual.js 脚本
- [ ] 在 `frontend/pages/index.html` 第 83 行 `<script src="photo-capture.js"></script>` 之后，新增 `<script src="../assets/js/weather-visual.js"></script>`（对应 C-02）。
- 验收条件：index.html 含 `<script src="../assets/js/weather-visual.js"></script>`；位于 photo-capture.js 之后、内联 `<script>` 之前。

### 3.3 内联 IIFE 末尾追加 init 调用
- [ ] 在 `frontend/pages/index.html` 第 392 行 `})();` 之前，追加 `if (window.WeatherVisual) { WeatherVisual.init(); }`（异步调用，不 await 阻塞表单）（对应 C-03，D-TWV-18）。
- 验收条件：index.html 含 `WeatherVisual.init()`；位于内联 IIFE 末尾 `})();` 之前。

### 3.4 确认既有表单与提交逻辑不变
- [ ] 确认 `crop`（id/name）、`sowingDate`（id/name）、`location`（id/name）字段不变；`buildTimelineQuery` 函数与 submit 跳转逻辑不变（对应 C-04/C-05，REQ-TWV-UBI-040）。
- 验收条件：git diff 审查 index.html 既有表单字段 id/name 与 buildTimelineQuery/submit 无修改，仅新增天气卡 DOM + script + init 调用。

---

## 4. 追加天气视觉样式

> 对应改动点 C-17~C-26，需求 REQ-TWV-UBI-015~026/032~035。
> 目标：在 `frontend/assets/css/common.css` 末尾追加天气卡样式、data-weather 背景与主题色选择器、@keyframes 微动效、响应式规则。不修改既有 `:root` 变量与 `prefers-reduced-motion` 规则。
> 前置依赖：组1（背景资源路径 `../weather-bg/*.svg`）、组3（DOM class/id 选择器）。

### 4.1 追加天气卡基础样式
- [ ] 在 `frontend/assets/css/common.css` 末尾追加 `.weather-card` 容器样式（相对定位、圆角、内边距、z-index 分层）+ `.weather-info` 字段布局（flex/inline 自适应）+ 5 个字段样式（`.weather-loc`/`.weather-temp`/`.weather-desc`/`.weather-humidity`/`.weather-wind`）+ `.weather-fallback` 降级提示样式（对应 C-17）。
- 验收条件：common.css 含 `.weather-card` 与 5 个字段 class 样式；天气卡不遮挡或挤压既有 `.card` 表单（z-index 合理）。

### 4.2 追加 data-weather 背景选择器
- [ ] 追加 5 类 `data-weather` 背景选择器，指向 `weather-bg/*.svg`（对应 C-18）：
  - `[data-weather="sunny"] .weather-bg-layer-a { background-image: url('../weather-bg/sunny.svg'); }`
  - `[data-weather="rain"] .weather-bg-layer-a { background-image: url('../weather-bg/rain.svg'); }`
  - `[data-weather="cloudy"] .weather-bg-layer-a { background-image: url('../weather-bg/cloudy.svg'); }`
  - `[data-weather="snow"] .weather-bg-layer-a { background-image: url('../weather-bg/snow.svg'); }`
  - `[data-weather="default"] .weather-bg-layer-a { background-image: url('../weather-bg/default.svg'); }`
  - 各层 `background-size: cover; background-position: center; background-repeat: no-repeat;`
- 验收条件：common.css 含 5 类 `[data-weather="..."]` 背景选择器；路径指向 `../weather-bg/*.svg`；含 `background-size: cover`。

### 4.3 追加双层背景切换 transition
- [ ] 追加双层背景叠加切换机制：`.weather-bg-layer` 绝对定位铺满容器 + `opacity` transition ≤400ms；`.weather-bg-layer-a` 默认 opacity:1，`.weather-bg-layer-b` 默认 opacity:0；切换时交叉淡入淡出（新层 0→1，旧层 1→0），无闪烁/白屏（对应 C-19，D-TWV-04）。
- 验收条件：common.css 含 `.weather-bg-layer` 绝对定位 + `transition` 且时长 ≤400ms；含双层 opacity 交叉机制。

### 4.4 追加主题色变量覆盖选择器
- [ ] 追加四类 × 四季主题色变量覆盖选择器（对应 C-20，D-TWV-07），在 `#weather-card` 上按 `data-weather` + `data-season` 覆盖 `:root` 变量：
  - `[data-weather="sunny"][data-season="summer"] { --leaf: #...; --sun: #...; }`（夏季晴天偏暖金）
  - `[data-weather="sunny"][data-season="winter"] { --leaf: #...; --sun: #...; }`（冬季晴天偏冷蓝）
  - `[data-weather="rain"] { --rain: #...; }`（雨天偏冷蓝）
  - `[data-weather="cloudy"] { ... }`（多云偏灰蓝）
  - `[data-weather="snow"] { ... }`（雪天偏冷白蓝）
  - 共 4 类 × 4 季 = 16 组组合（可按需精简，至少覆盖 4 类主天气 + 季节微调示例）。
- 验收条件：common.css 含 `[data-weather="..."][data-season="..."]` 变量覆盖选择器；覆盖 `--leaf`/`--sun`/`--rain` 等变量；变量覆盖范围限定天气区域，不影响表单/历史记录。

### 4.5 追加主题色 transition
- [ ] 追加主题色过渡：`#weather-card { transition: background-color 300ms, border-color 300ms, color 300ms; }`，时长 ≤300ms，无色彩跳变（对应 C-21）。
- 验收条件：common.css 含 `transition` 且时长 ≤300ms。

### 4.6 追加微动效 @keyframes
- [ ] 追加 4 类微动效 `@keyframes`（对应 C-22，D-TWV-06）：
  - `@keyframes anim-sunny`：日光晕脉动（opacity/scale 循环）；
  - `@keyframes anim-rain`：雨滴下落（translateY 循环）；
  - `@keyframes anim-cloudy`：云层飘移（translateX 循环）；
  - `@keyframes anim-snow`：雪花飘落（translateY + rotate 循环）。
- 验收条件：common.css 含 `@keyframes anim-sunny`/`anim-rain`/`anim-cloudy`/`anim-snow` 4 组关键帧定义。

### 4.7 追加动效应用选择器
- [ ] 追加 4 类动效应用选择器（对应 C-23，D-TWV-06）：
  - `[data-weather="sunny"] .weather-fx { animation: anim-sunny ...; }`
  - `[data-weather="rain"] .weather-fx { animation: anim-rain ...; }`
  - `[data-weather="cloudy"] .weather-fx { animation: anim-cloudy ...; }`
  - `[data-weather="snow"] .weather-fx { animation: anim-snow ...; }`
  - `.weather-fx` 须 `pointer-events: none`（不阻挡表单）+ 绝对定位 + z-index 低于信息文字与表单。
- 验收条件：common.css 含 4 类 `[data-weather="..."] .weather-fx { animation: ...; }`；含 `pointer-events: none`。

### 4.8 追加响应式媒体查询
- [ ] 在既有 `@media (min-width: 768px)`（第 420 行）与 `@media (min-width: 1024px)`（第 425 行）块内追加天气卡响应式布局规则：375px 紧凑（单行或两行排列）、768px+ 宽松布局、1024px 居中定宽（对应 C-24）。
- 验收条件：common.css 768/1024 媒体查询块内含 `.weather-card` 响应式规则；375px 下天气卡紧凑无溢出（依赖既有 `overflow-x: hidden`）。

### 4.9 确认既有样式不被修改
- [ ] 确认既有 `:root` 变量（第 1-21 行）不修改（新增选择器覆盖而非修改 :root）；既有 `prefers-reduced-motion` 规则（第 289-291 行）不修改（全局禁用动画自动覆盖新增动效）；既有 `.page--home` 背景（第 293-299 行）保留（新增 data-weather 选择器叠加）（对应 C-25/C-26）。
- 验收条件：git diff 审查 common.css 既有 `:root`/`prefers-reduced-motion`/`.page--home` 无修改，仅末尾追加新规则。

---

## 5. 新增测试断言

> 对应改动点 C-33~C-45，需求 REQ-TWV-UBI-038~039。
> 目标：在 `frontend/tests/pages.test.js` 末尾追加 test 块，复用既有 `indexHtml`/`commonCss` 变量，新增 `weatherVisualJs`/`weatherBgDir` 变量；断言以字符串包含/正则匹配为主（与既有 41 项风格一致），不执行运行时逻辑。既有 41 项断言不删除/不注释/不弱化。
> 前置依赖：组1~组4全部完成。

### 5.1 新增文件读取变量
- [ ] 在 `frontend/tests/pages.test.js` 第 19 行之后（文件读取基座区），新增变量（对应 C-33）：
  - `const weatherVisualJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'weather-visual.js'));`
  - `const weatherBgDir = fs.readdirSync(path.resolve(__dirname, '..', 'assets', 'weather-bg'));`
- 验收条件：pages.test.js 含 `weatherVisualJs` 与 `weatherBgDir` 变量定义；路径指向 `frontend/assets/js/weather-visual.js` 与 `frontend/assets/weather-bg/`。

### 5.2 新增天气信息区域存在断言
- [ ] 在文件末尾（第 369 行之后）新增 test 块，断言 `indexHtml` 含天气卡 DOM 结构：`id="weather-card"`、`data-weather`、5 个字段 id（weather-loc/temp/desc/humidity/wind）、`.weather-fx`、`.weather-fallback`（对应 C-34）。
- 验收条件：新增 test 块通过；断言覆盖天气卡 DOM 结构存在性。

### 5.3 新增 weathercode 归一化映射断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含 `WEATHER_CODE_MAP` 具名常量，且覆盖关键编码：0/1（晴天）、61（雨天）、71（雪天）、2/3（多云）（对应 C-35）。
- 验收条件：新增 test 块通过；断言含 `WEATHER_CODE_MAP` 与编码 0/1/61/71。

### 5.4 新增四类背景资源存在断言
- [ ] 新增 test 块，断言 `weatherBgDir` 含 sunny、rain、cloudy、snow、default 5 个文件（对应 C-36）。
- 验收条件：新增 test 块通过；断言 `weatherBgDir` 数组含 5 类背景文件名。

### 5.5 新增 data-weather 属性切换断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含 `data-weather` 与 `data-season` 属性设置逻辑（`setAttribute`）（对应 C-37）。
- 验收条件：新增 test 块通过；断言含 `setAttribute('data-weather'` 与 `setAttribute('data-season'`。

### 5.6 新增降级默认主题断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含 `fallback` 函数与中性提示文案（"天气数据暂不可用"或"定位未授权"等），且含 `data-weather="default"` 降级设置（对应 C-38）。
- 验收条件：新增 test 块通过；断言含 `fallback` 与中性提示文案与 `default` 降级。

### 5.7 新增 Open-Meteo 请求参数断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含 `api.open-meteo.com` URL 与 `current_weather` 参数（对应 C-39）。
- 验收条件：新增 test 块通过；断言含 `api.open-meteo.com` 与 `current_weather`。

### 5.8 新增超时常量断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含 `8000` 超时常量（`WEATHER_REQUEST_TIMEOUT_MS`）（对应 C-40）。
- 验收条件：新增 test 块通过；断言含 `8000`。

### 5.9 新增季节推断断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 含季节推断逻辑与月份划分（3-5 春、6-8 夏、9-11 秋、12/0/1 冬，可断言含 `getMonth` 与季节标签 spring/summer/autumn/winter）（对应 C-41）。
- 验收条件：新增 test 块通过；断言含 `inferSeason` 或 `getMonth` 与四季标签。

### 5.10 新增纯 CSS 动效断言
- [ ] 新增 test 块，断言 `commonCss` 含 `@keyframes anim-sunny`/`anim-rain`/`anim-cloudy`/`anim-snow` 4 组关键帧（对应 C-42）。
- 验收条件：新增 test 块通过；断言 commonCss 含 4 组 @keyframes。

### 5.11 新增无第三方库断言
- [ ] 新增 test 块，断言 `weatherVisualJs` 不含 `import`/`require` 第三方库语句（可断言不含 `import ` 与 `require('` 非内置模块）（对应 C-43）。
- 验收条件：新增 test 块通过；断言 weatherVisualJs 无第三方库引入。

### 5.12 新增既有断言兼容性复核
- [ ] 新增 test 块，复核既有 41 项断言仍通过：确认 `indexHtml` 仍含 `id="crop"`/`id="sowingDate"`/`id="location"` 等既有字段，`commonCss` 仍含 `:root`/`.page--home`/`prefers-reduced-motion` 等既有规则，`timelineLogicJs` 仍含 `WEATHER_ICON_MAP`/`getWeatherIcon`（对应 C-44）。
- 验收条件：新增 test 块通过；既有 41 项断言全绿（运行 `node --test frontend/tests/pages.test.js` 无失败）。

### 5.13 确认既有 41 项断言不被弱化
- [ ] 确认既有 41 项断言（第 21-369 行）不删除、不注释、不弱化（对应 C-45，REQ-TWV-UBI-038）。
- 验收条件：git diff 审查 pages.test.js 既有 41 项 test 块无修改，仅末尾追加新 test 块与基座区新增变量。

---

## 6. 可选扩充 mock 占位字段

> 对应改动点 C-46~C-47，需求 REQ-TWV-UBI-042。
> 目标：可选在 `frontend/assets/mock/mock-data.js` 追加主页天气占位字段（值为 null），不造假。可与组5并行。
> 红线：mock 保持 null 占位不造假，不得硬编码虚假天气数值（温度/湿度/风速）。

### 6.1 追加 homeWeather null 占位字段
- [ ] 在 `frontend/assets/mock/mock-data.js` 数据对象中追加 `homeWeather: null` 字段（null 占位，不造假）（对应 C-46）。
- 验收条件：mock-data.js 含 `homeWeather: null`；值为 null，无硬编码天气数值；既有 null 占位字段不变（对应 C-47）。

---

## 7. 回归验证与交付

> 对应需求 REQ-TWV-UBI-032~042、spec 7.5 交付任务。
> 目标：确认前端测试全绿、四类天气视觉正常、降级不白屏、响应式三档无横滚、不触碰项未被修改、Git 提交。
> 前置依赖：组1~组6全部完成。

### 7.1 运行前端测试确认全绿
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 41 项断言 + 新增断言全部通过，无失败。
- 验收条件：测试输出全绿，既有 41 项 + 新增约 12 项断言均通过（REQ-TWV-UBI-038/039）。

### 7.2 浏览器视觉验证四类天气
- [ ] 在浏览器打开 `frontend/pages/index.html`，分别模拟四类天气（晴/雨/多云/雪），验证主页背景图切换、主题色切换、微动效播放均无卡顿、无闪烁、无白屏（REQ-TWV-UBI-015~021）。
- 验收条件：四类天气下背景/主题色/动效正确切换；背景切换 ≤400ms 平滑过渡；主题色切换 ≤300ms 平滑过渡；动效不遮挡信息文字与表单。

### 7.3 验证降级场景
- [ ] 模拟天气数据不可用场景（拒绝定位授权/断网/Open-Meteo 超时），验证主页降级为默认主题：default 背景图 + 默认主题色 + 中性提示文案（"天气数据暂不可用"等），不白屏、不抛未捕获 JS 错误、表单仍可正常填写提交（REQ-TWV-UNW-027/UBI-028/029/031）。
- 验收条件：降级时展示 default 背景与中性提示；控制台无未捕获错误；表单可提交。

### 7.4 验证响应式三档
- [ ] 分别在 375px、768px、1024px 三档视口宽度下打开 index.html，验证无横向滚动条，天气信息卡与动态背景在三档下均正常展示，背景铺满视口无重复/留白/变形（REQ-TWV-UBI-032~035）。
- 验收条件：三档宽度均无横滚；天气卡紧凑/宽松布局正常；背景 `cover` 铺满。

### 7.5 验证无障碍偏好
- [ ] 开启浏览器 `prefers-reduced-motion: reduce` 偏好，验证所有微动效禁用，仅展示静态背景图（REQ-TWV-UBI-021）。
- 验收条件：减少动效模式下无动画播放，背景为静态图片。

### 7.6 验证不触碰项未被修改
- [ ] 审查 `git diff`，确认改动文件均在 `frontend/` 下：`frontend/pages/index.html`、`frontend/assets/css/common.css`、`frontend/assets/js/weather-visual.js`（新增）、`frontend/assets/weather-bg/`（新增目录 + 5 SVG）、`frontend/tests/pages.test.js`、`frontend/assets/mock/mock-data.js`（可选）；确认 `timeline.html`/`timeline-logic.js`/`crop-map.js`/`photo-capture.js`/`history-store.js`/`chart-client.js`/`timeline.css`/后端/nginx/shared/data/deploy/docs 均无修改（REQ-TWV-UBI-036/040/041）。
- 验收条件：git diff --name-only 输出的文件均在上述允许范围内；不触碰项无修改。

### 7.7 验证无第三方依赖引入
- [ ] 审查 `weather-visual.js` 无 `import`/`require` 第三方库语句；审查项目无新增 package.json 依赖；确认纯原生 JS + 原生 CSS 实现（REQ-TWV-UBI-037）。
- 验收条件：无新增第三方依赖；动效纯 CSS @keyframes；天气请求用原生 fetch + AbortController。

### 7.8 Git 提交与推送
- [ ] 提交并推送至 GitHub 仓库 `lj-tang/agritime`，提交信息体现 T01 任务编号（如 `T01: 主页天气与视觉动态适配`）（spec 7.5 交付任务 1）。
- 验收条件：Git 提交成功，提交信息含 T01；推送至远程仓库。

---

## 风险点与注意事项

> 以下为执行中需特别注意的边界情况（源自 design.md R-01~R-17）。

| 风险 | 注意事项 |
|------|---------|
| R-01 Open-Meteo 不可达/超时 | `fetchWeatherByOpenMeteo` 须用 AbortController + 8s 超时，超时后 fallback 降级，不白屏不阻塞表单 |
| R-02 用户拒绝定位授权 | `fetchWeatherByGeo` 须回退所在地输入推断经纬度（复用 CropMap.CROPS 中 lat/lng，只读）；所在地为空再降级 |
| R-03 浏览器不支持 geolocation | 同 R-02 回退所在地；须判断 `navigator.geolocation` 是否存在 |
| R-04 Open-Meteo 响应结构异常 | 须校验 `current_weather.temperature`/`current_weather.weathercode` 存在，缺失 reject `'invalid-structure'` → fallback |
| R-05 weathercode 超出已知范围 | `normalizeWeatherCode` 未识别回退 `default`，不抛错 |
| R-06 背景资源加载失败/404 | CSS `background-color: var(--paper)` 兜底（common.css 既有）；default.svg 为降级背景 |
| R-07 背景切换闪烁/白屏 | 双层 opacity 交叉淡入（新层 0→1 与旧层 1→0 同时进行），transition ≤400ms |
| R-08 动效遮挡信息/表单 | `.weather-fx` 须 `pointer-events: none` + z-index 低于表单；信息文字 z-index 高于动效层 |
| R-09 prefers-reduced-motion 未生效 | 既有全局规则（common.css 第 289-291 行）自动禁用新增动效，不绕过 |
| R-10 主题色破坏既有视觉 | `data-weather`/`data-season` 仅设在 `#weather-card`，变量覆盖范围限定天气区域，不影响表单/历史记录 |
| R-11 天气请求阻塞表单 | `init()` 异步执行不阻塞主线程；表单提交逻辑独立 |
| R-12 既有 41 测试断言被破坏 | 新增内容为追加（末尾/插入点），不改变既有断言匹配的字符串；组5含兼容性复核 |
| R-13 mock 新字段破坏既有断言 | mock 新字段为 null 占位，既有断言匹配特定字段不受影响 |
| R-14 跨域请求 Open-Meteo 被拦截 | Open-Meteo 已支持 CORS；若被拦截 → fallback 降级 |
| R-15 后端复用天气时后端无天气字段 | 复用为可选优先，后端无天气时回退直连 Open-Meteo，不依赖后端必有天气 |
| R-16 季节推断与实际气候不符 | 季节推断为本地系统日期按公历，主题色微调为视觉增强非业务关键 |
| R-17 极窄屏（<375px）溢出 | 既有 `overflow-x: hidden`/`max-width: 100%` 防溢出；天气卡用 flex/inline 自适应 |

---

## 验收标准汇总

| 验收维度 | 验收条件 | 对应需求 |
|---------|---------|---------|
| 天气信息展示 | 主页顶部展示地点+温度+状况+湿度+风速 5 字段，位于品牌头下方表单上方 | REQ-TWV-UBI-001~008 |
| 天气数据获取 | 浏览器定位→Open-Meteo 请求→归一化→渲染；8s 超时降级 | REQ-TWV-UBI-009~014/EVT-010/OPT-030 |
| 四类背景与动效 | 晴/雨/多云/雪四类背景图 + 微动效，CSS transition/animation 实现 | REQ-TWV-UBI-015~021 |
| 主题色切换 | data-weather + data-season 属性驱动 CSS 变量覆盖，≤300ms 过渡 | REQ-TWV-UBI-023~026 |
| 降级机制 | 天气不可用时默认主题 + 中性提示，不白屏不报错 | REQ-TWV-UNW-027/UBI-028/029/031 |
| 响应式适配 | 375/768/1024px 三档无横滚，天气卡与背景正常 | REQ-TWV-UBI-032~035 |
| 范围约束 | 仅改 frontend/，不触碰后端/nginx/shared/data/deploy/docs | REQ-TWV-UBI-036/040/041 |
| 无第三方依赖 | 纯原生 JS + CSS，无框架/构建工具/第三方库 | REQ-TWV-UBI-037 |
| 测试全绿 | 既有 41 项 + 新增断言全绿，不弱化既有断言 | REQ-TWV-UBI-038/039 |
| mock 不造假 | mock 新字段为 null 占位，无硬编码天气数值 | REQ-TWV-UBI-042 |