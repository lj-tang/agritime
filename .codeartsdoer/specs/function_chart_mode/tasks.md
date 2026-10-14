# 时间线函数图像模式 — 编码任务清单

> 依据 `spec.md`（19 条 EARS 需求）与 `design.md`（函数划分、代码清理清单、新增 CSS/测试断言方向）拆分。
> 红线：不得破坏既有 9 项测试断言；不得改动 `index.html`、`common.css`、`photo-capture.js`；mock 继续 null 占位。
> 任务顺序：代码清理 → HTML 结构新增 → CSS 样式新增 → JS 渲染逻辑 → render 接入 → 测试断言新增 → 回归验证。

## 1. 代码清理（REQ-FCM-UBI-014 / 015 / 016）

### 1.1 清理 timeline.html 未使用内联 SVG
- [ ] 移除 `frontend/pages/timeline.html` 中 `ICONS.weather` 与 `ICONS.festival` 两个未使用属性（nodeIcon 对 weather/festival 走 `IMG_ICONS` 外部 img，从不调用 svgIcon('weather'/'festival')），保留 `ICONS.term`；清理后运行 `node --test frontend/tests/pages.test.js` 确认既有 9 项断言全绿

### 1.2 清理 timeline.css 未使用 CSS 规则
- [ ] 移除 `frontend/assets/css/timeline.css` 第 26-31 行 `.conclusion-label` 规则块（timeline.html 中无 `class="conclusion-label"` 引用），保留 `.tl-node.predicted::before dashed` 等测试依赖规则；清理后运行测试套件确认全绿

### 1.3 清理 mock-data.js 冗余字段
- [ ] 移除 `frontend/assets/mock/mock-data.js` 中 nodes 末节点的 `festivalIcon: null` 字段（仅出现一次，renderNode/renderReminder 未使用），保持各节点结构一致，其余字段 null 占位策略不变；清理后运行测试套件确认全绿

### 1.4 清理后回归确认
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 9 项断言全部通过；确认 `index.html`、`common.css`、`photo-capture.js` 无任何改动

## 2. HTML 结构新增（切换控件 + chart 容器）

### 2.1 新增模式切换控件 DOM
- [ ] 在 `frontend/pages/timeline.html` 的 `#conclusion-card` 与 `#timeline` 之间插入 `<div class="mode-switch" id="mode-switch"><button type="button" class="mode-tab active" data-mode="node">时间节点</button><button type="button" class="mode-tab" data-mode="chart">函数图像</button></div>`，默认隐藏（hidden 由 JS 在 render 时控制），满足 REQ-FCM-UBI-007/008/009/011

### 2.2 新增函数图像容器 DOM
- [ ] 在 `frontend/pages/timeline.html` 的 `#timeline` 之后新增 `<section class="chart-wrap" id="chart" hidden></section>`，与 `#timeline` 通过 hidden 属性互斥显隐，满足 REQ-FCM-EVT-010

## 3. CSS 样式新增

### 3.1 新增模式切换控件样式
- [ ] 在 `frontend/assets/css/timeline.css` 末尾追加 `.mode-switch`（display:flex; gap:0; margin-bottom:18px; border-radius:var(--radius-sm); overflow:hidden; border:1px solid var(--line)）、`.mode-tab`（flex:1; padding:11px; border:none; background:var(--paper-2); font-size:15px; font-weight:600; cursor:pointer; transition:background 0.18s）、`.mode-tab.active`（background:var(--card); color:var(--leaf); font-weight:700; box-shadow:inset 0 -2px 0 var(--leaf-2)）三条规则，激活态以类名表达不用内联 style（REQ-FCM 可维护性 4.4.1）

### 3.2 新增函数图像容器与 SVG 样式
- [ ] 在 `frontend/assets/css/timeline.css` 追加 `.chart-wrap`（background:var(--card); border:1px solid rgba(228,221,203,0.9); border-radius:var(--radius); box-shadow:var(--shadow); padding:18px; margin-bottom:22px）、`.chart-svg`（width:100%; height:auto; display:block）、`.chart-placeholder`（fill:var(--muted); font-size:13px）三条规则，复用既有 CSS 变量保持视觉一致，375px 视口下 SVG viewBox 自适应缩放（REQ-FCM-UBI-013）

## 4. JS 渲染逻辑新增

### 4.1 新增 CHART_CONFIG 具名常量
- [ ] 在 `frontend/pages/timeline.html` IIFE 内新增 `CHART_CONFIG` 常量对象，集中定义 VIEWBOX_W:360、VIEWBOX_H:200、PADDING_LEFT:40、PADDING_BOTTOM:30、PADDING_TOP:10、PADDING_RIGHT:10、SIGMA:15、SAMPLE_STEP:2、PEAK_LEVEL:1、AXIS_COLOR:'var(--line)'、CURVE_COLOR:'var(--leaf-2)'、CURVE_FILL:'var(--leaf-soft)'、PEAK_COLOR:'var(--sun)'、PLACEHOLDER_TEXT:'数据待接入'，避免魔法数字（REQ-FCM 可维护性 4.4.2）

### 4.2 新增模式状态闭包变量
- [ ] 在 IIFE 内新增 `var currentMode = 'node'; var chartRendered = false; var cachedData = null;` 三个闭包变量，用于模式切换状态管理与惰性渲染标记（design.md ModeState）

### 4.3 实现 gaussianPath 函数
- [ ] 在 IIFE 内新增 `function gaussianPath(center, sigma, xMin, xMax, w, h)`，按高斯函数 y = exp(-((x-μ)²)/(2σ²)) 以 CHART_CONFIG.SAMPLE_STEP 步长从 xMin 采样到 xMax，每点映射至画布坐标，拼接 `M x0,y0 L x1,y1 ...` 返回 path d 字符串；前置依赖 4.1 CHART_CONFIG

### 4.4 实现 buildEmptyChartSVG 函数
- [ ] 在 IIFE 内新增 `function buildEmptyChartSVG()`，使用 `document.createElementNS('http://www.w3.org/2000/svg', ...)` 安全构建含纵轴 `<line>`、横轴 `<line>`、"高/低"纵轴 `<text>` 标注、"数据待接入"`<text>` 占位提示的 SVG 元素，不绘制任何 `<path>` 曲线（REQ-FCM-STA-005，禁止硬编码假数据）；前置依赖 4.1 CHART_CONFIG

### 4.5 实现 buildChartSVG 函数
- [ ] 在 IIFE 内新增 `function buildChartSVG(nodes)`，用 createElementNS 构建含坐标轴、刻度标注、多峰值钟形曲线的完整 SVG：计算横轴范围（min/max daysAfterSowing）→ 生成坐标轴与刻度 → 遍历 nodes 调用 gaussianPath 生成各峰值曲线 d 字符串并叠加为单一 `<path>`（减少 DOM 节点）→ 各峰值高度相同（PEAK_LEVEL=1 映射至 y=10）；坐标映射 x = 40 + (daysAfterSowing - minDay)/(maxDay - minDay) * 310，y = 170 - recommendLevel * 160；前置依赖 4.1、4.3

### 4.6 实现 renderChart 函数
- [ ] 在 IIFE 内新增 `function renderChart(data)`，判断数据有效性分流：data/nodes 为空或 nodes 所有节点 date 与 name 均为 null → 调用 buildEmptyChartSVG 注入空坐标轴 + 占位提示；否则调用 buildChartSVG 注入完整 SVG；注入前清空 #chart 容器，注入后置 chartRendered = true；不抛 JS 错误（REQ-FCM-STA-005）；前置依赖 4.4、4.5

### 4.7 实现 renderModeSwitch 函数
- [ ] 在 IIFE 内新增 `function renderModeSwitch()`，获取 `#mode-switch` 容器并显示（hidden=false），为两个 `.mode-tab` button 绑定 click 事件调用 `switchMode(this.getAttribute('data-mode'))`，默认竖向按钮带 .active 类（REQ-FCM-UBI-009/011）；前置依赖 4.8 switchMode

### 4.8 实现 switchMode 函数
- [ ] 在 IIFE 内新增 `function switchMode(mode)`，mode 非 'node'/'chart' 时忽略；更新两按钮 .active 类名 → 互斥设置 `#timeline.hidden` 与 `#chart.hidden` → 首次切至 chart 且 chartRendered 为 false 时调用 renderChart(cachedData)；仅 DOM hidden 与类名切换无全页刷新（REQ-FCM-EVT-010），渲染 <300ms；前置依赖 4.6 renderChart

## 5. render 接入

### 5.1 render 内插入模式切换控件渲染
- [ ] 修改 `frontend/pages/timeline.html` 的 `render(data)` 函数：在 `renderConclusion(data)` 调用之后、`timeline.appendChild(renderNode(n))` 循环之前，插入 `cachedData = data; renderModeSwitch();` 两行，签名不变，既有空数据走 empty-state 分支逻辑不变（design.md render 改造要点）；前置依赖 4.2、4.7

### 5.2 render 默认模式初始化
- [ ] 在 render 函数末尾（timeline.hidden = false 之后）确保 `currentMode = 'node'` 默认竖向模式，`#chart` 保持 hidden，`#mode-switch` 显示；既有 footer 显隐逻辑不变

## 6. 测试断言新增（不破坏既有 9 项）

### 6.1 新增模式切换控件存在断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.match(timelineHtml, /mode-switch/)` 与 `assert.match(timelineHtml, /mode-tab/)`，验证切换控件 DOM 存在

### 6.2 新增两模式选项 data-mode 断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.ok(timelineHtml.includes('data-mode="node"'))` 与 `assert.ok(timelineHtml.includes('data-mode="chart"'))`，验证两模式选项存在

### 6.3 新增 chart 容器存在断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.match(timelineHtml, /id="chart"/)`，验证函数图像容器 DOM 存在

### 6.4 新增原生 SVG 构建方式断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.match(timelineHtml, /createElementNS/)`，确认使用原生 SVG API 构建而非 innerHTML 注入（REQ-FCM-UBI-002 / 安全性 4.3.2）

### 6.5 新增高斯函数具名常量断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.match(timelineHtml, /CHART_CONFIG/)` 与 `assert.match(timelineHtml, /SIGMA/)`，验证图表参数集中定义（REQ-FCM 可维护性 4.4.2）

### 6.6 新增占位提示文案断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.ok(timelineHtml.includes('数据待接入'))`，验证 null 占位时显示占位提示（REQ-FCM-STA-005）

### 6.7 新增激活态类名断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.match(timelineHtml, /class="mode-tab active"/)`，验证激活态以 CSS 类名表达而非内联 style（REQ-FCM 可维护性 4.4.1）

### 6.8 新增无外部图表库断言
- [ ] 在 `frontend/tests/pages.test.js` 新增 test：`assert.ok(!timelineHtml.includes('Chart.js'))`、`assert.ok(!timelineHtml.includes('echarts'))`、`assert.ok(!timelineHtml.includes('d3'))`，确认无外部图表库引用（REQ-FCM-UBI-002 / 安全性 4.3.1）

## 7. 回归验证

### 7.1 运行完整测试套件
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 9 项断言 + 新增 8 项断言全部通过（共 17 项），无失败

### 7.2 验证不可改动文件无变更
- [ ] 通过 git diff 确认 `frontend/pages/index.html`、`frontend/assets/css/common.css`、`frontend/pages/photo-capture.js` 三个文件无任何改动（REQ-FCM 兼容性 4.5.2）

### 7.3 验证 mock null 占位策略不变
- [ ] 审查 `frontend/assets/mock/mock-data.js`，确认 nodes 各节点 date/name/status/copy/weatherIcon 等字段保持 null 占位，未硬编码假数据（REQ-FCM-STA-005 / 兼容性 4.5.4）

### 7.4 代码审查确认清理与新增完整
- [ ] 审查 `timeline.html` 无未使用变量/重复逻辑/废弃注释（REQ-FCM-UBI-014）；审查 `timeline.css` 无未使用规则/重复声明（REQ-FCM-UBI-015）；确认 ICONS.weather/festival 已移除、.conclusion-label 已移除、festivalIcon 已移除；确认新增 renderModeSwitch/switchMode/renderChart/buildChartSVG/gaussianPath/buildEmptyChartSVG 六函数均已实现