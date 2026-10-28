# V4 整合优化 · 编码任务规划（v4_integrated_optimization）

> 本任务规划基于 `v4_integrated_optimization/spec.md`（REQ-VIO-UBI/EVT/STA/OPT/UNW-001 ~ 049，共 49 条 EARS 需求）与 `design.md`（D-VIO-01 ~ 24，共 24 条关键决策）生成。
> 范围红线：**纯前端整合优化**，仅修改 `frontend/` 目录下文件；既有测试全绿（当前 77 个 `test()` 调用）仅允许新增断言或同步更新已移除功能的断言；不引入框架/构建工具/第三方库；不修改后端/nginx/shared/data/deploy/docs。
> 全局约束继承自 `stage2_frontend`/`t00_directory_migration`/`t01_weather_visual`/`v4_t01_ui_revamp`/`v4_t12_my_crops`/`t08_t10_voice_security`：纯原生 JS + 原生 CSS、`crop-map.js` 5 个 cropId 不变、mock null 占位不造假、既有 switchModule 机制仅扩展不破坏、既有日历联动/拍照分析/语音组件/分栏布局/主页表单提交链路不变。

## 任务依赖关系总览

```
组1(天气修复) ─────────────────────────────────────────┐
组2(子菜单重构) → 组3(重命名) → 组4(移除表单) → 组5(悬浮球) ─┤
组6(账号前端) ───────────────────────────────────────────┤
组7(z-index标注, 依赖组5) ────────────────────────────────┤
                                                          ↓
                                              组8(测试断言) → 组9(回归验证)
```

- 组1、组2、组6 相互独立，可并行。
- 组3 依赖组2（同改 sidebar.js MODULE_ENTRIES）。
- 组4 依赖组3（同改 index.html、my-crops.js）。
- 组5 依赖组4（同改 index.html 引入脚本）。
- 组7 依赖组5（悬浮球 z-index 定义后标注完整层叠）。
- 组8 依赖组1~7（所有改动完成后写断言）。
- 组9 依赖组8（测试全绿后回归验证）。

---

## 1. 修复主页天气动画缺失

> **关联需求**：REQ-VIO-UBI-041 ~ 048（天气卡可见/背景动画/时段动画/loading-gate 遮挡/z-index/布局/加载顺序/不改取数）
> **关联决策**：D-VIO-16（根因定位）、D-VIO-17（loading-gate 排查）、D-VIO-19（布局排查）、D-VIO-20（加载顺序排查）、D-VIO-21（最小增量修复）
> **优先级**：最高（语法错误导致 weather-visual.js 完全无法执行，是天气动画缺失的根因）
> **独立性**：仅改 `weather-visual.js` 第 129 行，与其他任务组无文件冲突，可并行启动

### 1.1 修复 weather-visual.js judgeDayNight 语法错误
- [ ] 修复 `frontend/assets/js/weather-visual.js` 第 129 行语法错误：将 `return (cur >= rise &&< set) ? 'day' : 'night';` 改为 `return (cur >= rise && cur < set) ? 'day' : 'night';`
  - **改动描述**：`&&<` 为非法 JS 语法（应为 `&& cur <`），导致整个 IIFE 在解析阶段抛 `SyntaxError: Unexpected token '<'`，weather-visual.js 完全无法执行——init/fallback/renderCard/applyBackground/notifyReady 均不执行，天气卡永不显示、背景动画永不播放、weather-ready 事件永不触发（loading-gate 仅靠 10s 超时隐藏）
  - **验收条件**：修复后 `judgeDayNight()` 正常返回 `'day'` 或 `'night'`；`applyBackground()` 正常设置 `data-daynight` 属性；`init()` 正常执行 → `renderCard` 显示天气卡 → `notifyReady` 触发 `weather-ready` 事件 → loading-gate 及时隐藏
  - **约束**：仅改此 1 行，既有 `WMO_MAP`/`normalizeWeather`/`inferSeason`/`fetchWeather`/`buildWeatherInfo`/`renderCard`/`applyTheme`/`applyBackground`/`fallback`/`init` 逻辑不变（REQ-VIO-UBI-048）；既有取数方式（Open-Meteo URL/参数）、超时（8000ms）、降级机制（fallback）不变

### 1.2 排查并确认 loading-gate 遮挡关系
- [ ] 确认 `frontend/pages/index.html` 第 150-153 行 `weather-ready` 事件监听与第 154-157 行 10s 超时兜底机制在修复后正常工作
  - **改动描述**：修复 weather-visual.js 语法错误后 `weather-ready` 事件正常触发，loading-gate 在天气就绪/降级后及时隐藏（`.hidden` 类 → `opacity:0; pointer-events:none` 不占据层叠）；10s 超时兜底保留作为双重保险
  - **验收条件**：天气数据就绪后 loading-gate 已隐藏（含 `.hidden` 类），不遮挡天气卡与动画；天气数据降级后 loading-gate 同样及时隐藏
  - **约束**：不修改 index.html 第 150-157 行既有逻辑（仅确认）；loading-gate `z-index:9999` 保持（全屏加载遮罩高 z-index 合理，隐藏后不遮挡）

### 1.3 排查并确认 data-layout="app" 布局与脚本加载顺序
- [ ] 确认 `frontend/assets/css/common.css` 第 539-542 行 `body[data-layout="app"]` flex 布局不导致天气内容缺失（weather-card 与 weather-bg-layer 在 `.app-main` 内展示）
  - **验收条件**：布局正常，天气缺失根因已定位为 weather-visual.js 语法错误而非布局
- [ ] 确认 `frontend/pages/index.html` 第 142-148 行脚本加载顺序正确（weather-visual.js 在 time-period.js 之前加载，init 在 DOMContentLoaded 自动执行）
  - **验收条件**：加载顺序正确，根因已定位为语法错误而非加载顺序

---

## 2. 重构导航栏子菜单结构

> **关联需求**：REQ-VIO-UBI-001 ~ 008（收拢至「我的」子菜单/层级配置/展开收起/子菜单切换/纯原生实现/状态保持/其余板块不变/移动端抽屉）
> **关联决策**：D-VIO-01（children 层级结构）、D-VIO-02（max-height 过渡）、D-VIO-03（复用 switchModule）
> **独立性**：改 `sidebar.js` + `sidebar.css`，与组1无文件冲突，可并行

### 2.1 重构 sidebar.js MODULE_ENTRIES 为含 children 层级结构
- [ ] 在 `frontend/components/sidebar/sidebar.js` 顶部新增具名常量 `var MODULE_LABEL_MY_CROPS = '我的产品';`（集中定义，便于未来扩展）
- [ ] 重构 `frontend/components/sidebar/sidebar.js` 第 7-14 行 `MODULE_ENTRIES`：将扁平数组改为含 `children` 的层级结构
  - **改动描述**：新增 `{id:'my', label:'我的', icon:'🗂️', children:[{id:'plots', label:'我的地块', icon:'📍', implemented:false}, {id:'my-crops', label:MODULE_LABEL_MY_CROPS, icon:'🌾', implemented:true}]}` 一级菜单；plots/my-crops 从顶级入口移除归入 children；其余 4 入口（recommend/calendar/disease/service）保持顶级不变
  - **验收条件**：MODULE_ENTRIES 含 `my` 一级菜单 + `children` 字段；plots/my-crops 在 children 下不再作为顶级入口；其余 4 入口顶级不变；配置以具名常量定义支持扩展
  - **约束**：既有 `switchModule` 核心逻辑不变（REQ-VIO 4.5.6）；保留 `'my-crops'`/`'plots'`/`implemented: true` 等字符串以兼容既有测试断言（pages.test.js:581-592, 680-683）

### 2.2 扩展 renderSidebar 区分一级菜单与子菜单渲染
- [ ] 扩展 `frontend/components/sidebar/sidebar.js` 第 20-34 行 `renderSidebar` 函数：区分一级菜单（含 children）与叶子入口
  - **改动描述**：一级菜单渲染展开/收起按钮（`.sidebar-item--expandable`）+ 子菜单容器（`.sidebar-submenu`）；子菜单项渲染为 `.sidebar-subitem`（设置 `data-module` 属性）；一级菜单点击调用 `toggleSubmenu(id)`，子菜单项点击调用 `switchModule(entry.id)`
  - **验收条件**：一级菜单「我的」可点击展开/收起；子菜单项「我的地块」「我的产品」可点击切换；其余 4 入口保持既有渲染行为
  - **约束**：保留既有入口渲染逻辑（叶子入口仍生成 `<button class="sidebar-item">`）

### 2.3 新增 toggleSubmenu 函数实现子菜单展开/收起
- [ ] 在 `frontend/components/sidebar/sidebar.js` 新增 `toggleSubmenu(id)` 函数：toggle `.sidebar-submenu--open` 类展开/收起子菜单
  - **改动描述**：通过 toggle CSS 类实现展开/收起，过渡由 CSS `max-height` 驱动（≤100ms）；一级菜单展开标识 toggle `.sidebar-item--expanded` 类（箭头旋转）
  - **验收条件**：点击「我的」展开子菜单显示「我的地块」「我的产品」；再次点击收起隐藏；展开/收起有平滑过渡无突变
  - **约束**：纯原生 JS + CSS 实现，不引入第三方菜单库（REQ-VIO-UBI-005）；过渡由 `prefers-reduced-motion` 自动禁用

### 2.4 扩展 switchModule 支持子菜单项高亮与一级菜单保持展开
- [ ] 扩展 `frontend/components/sidebar/sidebar.js` 第 36-62 行 `switchModule` 函数：子菜单项切换时高亮 `.sidebar-subitem--active`；当前选中子菜单项时一级菜单保持展开（REQ-VIO-STA-006）
  - **改动描述**：核心切换逻辑不变（按 `data-module` 切换 `content-pane--active`）；新增子菜单项高亮逻辑；当前选中「我的」下子菜单项时一级菜单保持展开态
  - **验收条件**：展开「我的」后点击「我的产品」→ 右侧内容区切换至「我的产品」页面，该子菜单项高亮，「我的」一级菜单保持展开；切换至非「我的」下板块时「我的」状态不强制收起
  - **约束**：`switchModule` 核心逻辑不变（仅扩展子菜单高亮能力）；未实现板块（plots）仍展示 `#module-placeholder` 占位提示

### 2.5 扩展 window.Sidebar 暴露 toggleSubmenu
- [ ] 在 `frontend/components/sidebar/sidebar.js` 第 77 行 `window.Sidebar` 对象新增 `toggleSubmenu: toggleSubmenu`
  - **验收条件**：`window.Sidebar.toggleSubmenu` 可调用

### 2.6 新增 sidebar.css 子菜单样式
- [ ] 在 `frontend/components/sidebar/sidebar.css` 新增子菜单样式：
  - `.sidebar-submenu { max-height: 0; overflow: hidden; transition: max-height 0.15s ease; padding-left: 12px; }`
  - `.sidebar-submenu--open { max-height: 200px; }`
  - `.sidebar-subitem` 样式（与 `.sidebar-item` 视觉区分，缩进 + 较小字号）
  - `.sidebar-subitem--active` 高亮样式（复用 `--leaf-soft`/`--leaf` 变量）
  - `.sidebar-item--expandable`/`.sidebar-item--expanded` 样式（展开箭头旋转过渡）
  - **验收条件**：子菜单展开/收起有 max-height 过渡（≤100ms）；子菜单项与一级菜单视觉区分；高亮态清晰；移动端抽屉内子菜单正常展示
  - **约束**：复用既有 CSS 变量（`--leaf`/`--leaf-soft`/`--ink-soft`/`--card`/`--line`）；响应式 375px 下子菜单在抽屉内正常工作（REQ-VIO-UBI-008）

### 2.7 确认 sidebar z-index 显式设置
- [ ] 确认 `frontend/components/sidebar/sidebar.css` 第 11 行已有 `z-index: 100`（已存在，无需新增）；可选清理第 55 行拼写错误 `zE-index: 101;`（无效属性，第 56 行有正确 `z-index: 101`）
  - **验收条件**：sidebar z-index:100 确保高于内容层；不影响既有功能

---

## 3. 重命名"我的作物"为"我的产品"

> **关联需求**：REQ-VIO-UBI-009 ~ 012（通用名称/同步更新文案/技术标识不变/泛化扩展）
> **关联决策**：D-VIO-04（重命名为「我的产品」）、D-VIO-05（技术标识不变）
> **依赖**：组2（sidebar.js MODULE_ENTRIES 重构后更新 my-crops label 引用常量）

### 3.1 更新 sidebar.js MODULE_ENTRIES my-crops label 引用具名常量
- [ ] 在 `frontend/components/sidebar/sidebar.js` MODULE_ENTRIES 中 `my-crops` 子菜单项的 `label` 引用组2.1 新增的 `MODULE_LABEL_MY_CROPS` 常量（值 `'我的产品'`）
  - **验收条件**：sidebar.js 中 my-crops 的 label 为 `MODULE_LABEL_MY_CROPS`（非硬编码字符串）；左侧栏显示「我的产品」而非「我的作物」
  - **约束**：技术标识 `data-module="my-crops"` 不变、存储键 `agritime.my-crops.v1` 不变、cropId 映射不变、事件名 `agritime:sowing-confirmed` 不变、文件名 my-crops.js/my-crops.css 不变（REQ-VIO-UBI-011）

### 3.2 更新 index.html 板块标题文案
- [ ] 更新 `frontend/pages/index.html` 第 112 行 `<span class="my-crops-title">我的作物</span>` 为 `我的产品`（或由 my-crops.js 动态设置引用常量）
  - **验收条件**：页面标题展示「我的产品」；无「我的作物」残留
  - **约束**：`.my-crops-title` class 名不变（仅改文案）

### 3.3 更新 my-crops.js 空状态文案泛化
- [ ] 更新 `frontend/assets/js/my-crops.js` 第 30 行空状态文案：将 `还没有作物记录，点击「添加」开始记录` 改为泛化文案（如 `还没有记录，可通过农事日历确认播种自动新增`），移除「作物」种植业专属词与「添加」引导（因表单将移除）
  - **验收条件**：空状态提示不限定种植业语义（不出现「作物」「播种」等专属词在板块标题层）；不引导点击「添加」（表单已移除）
  - **约束**：文案泛化以兼容未来渔业/林业/畜牧业扩展（REQ-VIO-OPT-012）

### 3.4 全量审查面向用户文案无"我的作物"残留混用
- [ ] 全量审查 `sidebar.js`/`index.html`/`my-crops.js` 中所有面向用户的位置，确认无「我的作物」与「我的产品」混用
  - **验收条件**：所有面向用户的位置统一为「我的产品」（或泛化文案），无「我的作物」残留
  - **约束**：技术标识（data-module/存储键/cropId/事件名/文件名/class 名）保持 `my-crops` 不变

---

## 4. 移除"我的产品"页面冗余表单

> **关联需求**：REQ-VIO-UBI-013 ~ 019（移除表单/移除添加按钮/移除地块表单/保留展示/日历联动/拍照分析/删除二次确认）
> **关联决策**：D-VIO-06（移除表单方式）、D-VIO-07（plots 占位提示无表单）、D-VIO-08（自定义确认弹层）、D-VIO-09（移除编辑入口）
> **依赖**：组3（同改 index.html、my-crops.js，空状态文案泛化在组3.3）

### 4.1 移除 index.html my-crops 表单 DOM
- [ ] 删除 `frontend/pages/index.html` 第 113 行 `<button ... id="my-crops-add-btn">+ 添加</button>` 添加按钮
- [ ] 删除 `frontend/pages/index.html` 第 115-128 行 `<form class="my-crops-form" id="my-crops-form">...</form>` 整个表单（含 7 字段输入控件：作物名称/种下时间/发芽状况/高度/长势/地块/备注 + 保存/取消按钮）
  - **验收条件**：进入「我的产品」页面不展示添加/编辑表单（无形输入控件），无「+ 添加」按钮；仅保留 header（标题）+ list（列表容器）
  - **约束**：保留 `data-module="my-crops"`/`id="my-crops-pane"`/`id="my-crops-list"`/`.my-crops-title`/`.my-crops-header` 等技术标识（兼容既有测试断言 pages.test.js:670-678）

### 4.2 移除 my-crops.js 表单逻辑
- [ ] 删除 `frontend/assets/js/my-crops.js` 第 12-13 行 `addBtn.addEventListener('click', ...)` 绑定
- [ ] 删除 `frontend/assets/js/my-crops.js` 第 101-114 行 `showForm` 函数整体
- [ ] 删除 `frontend/assets/js/my-crops.js` 第 116-139 行 `handleSubmit` 函数整体
- [ ] 删除 `frontend/assets/js/my-crops.js` 第 74-79 行 `renderCard` 中 editBtn 创建与绑定（移除编辑入口，仅展示不可编辑）
  - **验收条件**：my-crops.js 不含 `showForm`/`handleSubmit`/`addBtn` 绑定/`editBtn`；保留 `init`/`renderList`/`renderCard`（展示部分）/`handlePhoto` + 日历联动订阅
  - **约束**：保留 `window.MyCrops = { init, renderList, GERMINATION_OPTIONS, VIGOR_OPTIONS }` 暴露（REQ-VIO-UBI-016）；保留第 14-21 行日历联动事件订阅（REQ-VIO-UBI-017）；保留第 91-96 行 photoBtn 拍照分析入口（REQ-VIO-UBI-018）

### 4.3 新增 my-crops.js showConfirmDialog 自定义确认弹层
- [ ] 在 `frontend/assets/js/my-crops.js` 新增 `showConfirmDialog(record)` 函数：创建 DOM 确认弹层（`.mc-confirm`）+ `textContent` 文案 + 确认/取消按钮 + 确认后调用 `MyCropsStore.remove(record.recordId)` + 刷新列表（`renderList`）+ 取消后移除弹层
  - **改动描述**：替代第 84-89 行 `confirm('确认删除这条记录？')` 原生弹窗；delBtn 点击调用 `showConfirmDialog(r)` 而非 `confirm()`
  - **验收条件**：记录卡片上点击「删除」→ 展示自定义确认弹层（非原生 confirm）；确认后记录移除，列表刷新；取消后弹层关闭，记录保留
  - **约束**：弹层文案用 `textContent` 安全渲染（防 XSS，REQ-VIO 4.3.2）；复用既有 CSS 变量风格统一

### 4.4 新增 my-crops.js EDITABLE 具名常量配置编辑入口策略
- [ ] 在 `frontend/assets/js/my-crops.js` 新增 `var EDITABLE = false;` 具名常量（编辑入口策略配置，本次为 false 仅展示不可编辑，未来可改为 true 恢复编辑）
  - **验收条件**：编辑入口策略以具名常量配置；`EDITABLE = false` 时不展示编辑按钮
  - **约束**：便于未来 T06/T12 扩展任务恢复编辑能力（REQ-VIO-UBI-019）

### 4.5 确认 plots 地块页面无表单可移除
- [ ] 确认 `frontend/pages/index.html` 无 plots content-pane（plots `implemented:false`，switchModule 展示 `#module-placeholder` 占位提示仅文案无输入控件）；plots 归入「我的」子菜单后保持占位提示
  - **验收条件**：plots 占位提示不含输入控件；归入子菜单后保持既有占位提示行为
  - **约束**：地块完整管理（标注/形状建模/实时监控）由 T06 任务承接（REQ-VIO 1.4 职责边界第 9 条）

### 4.6 移除 my-crops.css 表单样式并新增确认弹层样式
- [ ] 在 `frontend/assets/css/my-crops.css` 删除 `.my-crops-form`/`.my-crops-form-actions`/表单字段相关样式
- [ ] 在 `frontend/assets/css/my-crops.css` 删除 `.my-crops-add` 添加按钮样式
- [ ] 在 `frontend/assets/css/my-crops.css` 新增 `.mc-confirm`/`.mc-confirm__text`/`.mc-confirm__actions`/`.mc-confirm__btn` 确认弹层样式（复用既有 CSS 变量 `--card`/`--leaf`/`--ink`/`--radius`/`--shadow`）
- [ ] 调整 `frontend/assets/css/my-crops.css` 中 `.my-crops-card`/`.my-crops-card-actions` 样式为仅展示+删除+拍照（无编辑按钮）
  - **验收条件**：my-crops.css 不含表单样式；含确认弹层样式；卡片操作区仅含删除+拍照按钮
  - **约束**：保留 `.my-crops-pane`/`.my-crops-card`/`.my-crops-empty`/`.my-crops-group` 等展示态样式（兼容既有测试断言 pages.test.js:663-668，但 `.my-crops-form` 断言须同步更新见组8）

---

## 5. 新增悬浮球与大模型对话

> **关联需求**：REQ-VIO-UBI-030 ~ 040（悬浮球组件/对话窗口/发送问题/回复展示/纯前端实现/降级提示/独立目录/风格统一/关闭恢复/响应式/端点配置）
> **关联决策**：D-VIO-13（悬浮球实现方式）、D-VIO-14（大模型 API 调用与降级）、D-VIO-15（z-index:1000）、D-VIO-22（加载顺序）
> **依赖**：组4（同改 index.html 引入脚本，须在组4移除表单后改动 index.html）

### 5.1 新增 floating-ball 目录与 float-ball.js 悬浮球组件
- [ ] 新增 `frontend/components/floating-ball/float-ball.js`：IIFE + `window.FloatBall = { init, toggle, sendMessage }` + `module.exports`（供测试 require）
  - **核心逻辑**：
    - 具名常量：`LLM_ENDPOINT`（大模型 API 端点，以具名常量定义，默认空字符串表示未配置）、`LLM_TIMEOUT = 15000`、`LLM_FALLBACK_TEXT = '智能问答暂不可用'`、`THINKING_TEXT = '思考中…'`
    - `init()`：挂载悬浮球 DOM（`position:fixed; right:24px; bottom:24px; z-index:1000`）+ 点击事件调用 `toggle()`；DOM 异常时静默降级
    - `toggle()`：toggle 对话窗口显示/隐藏
    - `sendMessage(text)`：展示用户消息（右对齐，`textContent` 安全渲染）→ 展示「思考中…」→ `fetch` 调用大模型 API（`AbortController` + 15s 超时）→ 回复展示为对话气泡（左对齐，`textContent`）→ 自动滚动至最新；API 不可用/超时/错误时展示降级提示，用户消息保留
  - **验收条件**：悬浮球固定右下角可见（z-index:1000）；点击展开对话窗口（含输入框/发送按钮/对话气泡列表）；输入问题点击发送 → 展示用户消息 + 「思考中…」 + 调用 API → 回复展示为对话气泡；API 不可用时展示「智能问答暂不可用」降级提示，用户消息保留，不卡死不白屏
  - **约束**：纯前端原生 `fetch` + 原生 JS/CSS，不引入第三方对话 UI 库/HTTP 库（REQ-VIO-UBI-034）；不在前端硬编码 API Key，鉴权经后端代理（REQ-VIO-OPT-040/4.3.5）；`textContent` 安全渲染防 XSS（REQ-VIO 4.3.2）；端点未配置时不发起请求直接降级提示

### 5.2 新增 float-ball.css 悬浮球与对话窗口样式
- [ ] 新增 `frontend/components/floating-ball/float-ball.css`：悬浮球样式（球形按钮 + 阴影 + hover 过渡）+ 对话窗口样式（输入框/发送按钮/对话气泡列表/用户消息右对齐/模型回复左对齐）+ 响应式三档适配
  - **验收条件**：悬浮球与对话窗口复用既有 CSS 变量（`--leaf`/`--ink`/`--paper`/`--card`/`--radius`/`--shadow`）风格与项目一致；375px/768px/1024px 三档下正常展示；移动端对话窗口近全屏不遮挡底部操作区，桌面端定宽定高
  - **约束**：悬浮球不遮挡左侧栏/表单/列表/天气卡（REQ-VIO-UBI-030）；对话窗口可关闭恢复悬浮球态（REQ-VIO-STA-038）

### 5.3 在 index.html 引入悬浮球脚本与样式
- [ ] 在 `frontend/pages/index.html` `<head>` 内（第 11 行后）新增 `<link rel="stylesheet" href="../components/floating-ball/float-ball.css">`
- [ ] 在 `frontend/pages/index.html` 第 148 行 `sidebar.js` 之后新增 `<script src="../components/floating-ball/float-ball.js"></script>`
  - **验收条件**：主页加载后悬浮球挂载完成（≤500ms）并可见；不破坏既有脚本加载顺序
  - **约束**：float-ball.js 在 sidebar.js 之后加载（D-VIO-22）；不破坏既有 mock-data → crop-map → ... → sidebar → float-ball 顺序

---

## 6. 新增 T10 账号系统前端

> **关联需求**：REQ-VIO-OPT-020 ~ 029（注册/登录/独立目录/模式切换/数据绑定/超管后台/路由守卫/429提示/429节流/降级承接）
> **关联决策**：D-VIO-10（auth 实现方式）、D-VIO-11（登录态持久化）、D-VIO-12（admin 实现方式）
> **独立性**：新增 `frontend/pages/auth/` + `frontend/pages/admin/` 目录，不修改既有文件，可与组5并行

### 6.1 新增 auth 目录与登录注册页 HTML
- [ ] 新增 `frontend/pages/auth/index.html`：独立登录注册页 DOM，含手机号/用户名输入 + 密码输入 + 验证码输入（注册模式）+ 登录/注册按钮 + 模式切换链接（「去注册」/「去登录」）+ 错误提示容器
  - **验收条件**：登录注册页独立存放于 `frontend/pages/auth/`；含登录/注册两种模式切换（同页切换不跳转）；有当前模式可见标识
  - **约束**：复用 `common.css` CSS 变量（`--leaf`/`--ink`/`--paper`/`--card`/`--radius`）风格与项目一致（REQ-VIO-UBI-022）；含左侧栏与整体 UI 风格

### 6.2 新增 auth.js 账号交互逻辑
- [ ] 新增 `frontend/pages/auth/auth.js`：IIFE + `window.Auth = { login, register, logout, getLoginState, isAdmin }` + `module.exports`
  - **核心逻辑**：
    - 具名常量：`AUTH_ENDPOINT`（账号接口端点，以具名常量定义）、`AUTH_TIMEOUT = 10000`、`LOGIN_STATE_KEY = 'agritime.auth.v1'`、`FALLBACK_TEXT = '账号服务暂不可用'`、`RATE_LIMIT_TEXT = '请求过于频繁，请稍后再试'`、`RATE_LIMIT_THROTTLE_MS = 3000`
    - `login(username, password)`：表单校验（用户名非空、密码长度 ≥6）→ `fetch` 调用后端登录接口 → 登录态持久化（localStorage 优先/sessionStorage 降级）→ 返回 `{success, state}`；接口不可用→降级提示；429→节流提示
    - `register(username, password, code)`：表单校验 → `fetch` 调用后端注册接口 → 返回 `{success}`；降级/429 处理同 login
    - `logout()`：清除登录态（localStorage/sessionStorage）
    - `getLoginState()`：返回 `{userId, username, token, role, loginAt}` 或 `null`
    - `isAdmin()`：返回 `role === 'admin'`
    - 429 节流：3 秒内仅展示一次限流提示（复用 toast UI 模式）
  - **验收条件**：登录成功后建立登录态，页面刷新后仍处于登录态；退出后登录态清除；后端不可用时展示「账号服务暂不可用」降级提示不阻塞页面；429 时展示「请求过于频繁，请稍后再试」友好提示，3 秒内多次 429 仅展示一次
  - **约束**：禁止在 localStorage 中明文存储密码（REQ-VIO 4.3.1）；登录态对象 `{userId,username,token,role,loginAt}` 不含密码；接口调用有合理超时（10000ms）不无限等待（REQ-VIO 4.1.7）；429 提示不遮挡整个页面（REQ-VIO 5.4.1 禁止项）

### 6.3 新增 auth.css 登录注册页样式
- [ ] 新增 `frontend/pages/auth/auth.css`：登录/注册表单样式 + 模式切换标识样式，复用既有 CSS 变量
  - **验收条件**：样式与项目内部一致；响应式 375px/768px/1024px 三档正常展示无横向滚动
  - **约束**：不使用外部默认模板样式（REQ-VIO-UBI-022）

### 6.4 新增 admin 目录与超管后台 HTML
- [ ] 新增 `frontend/pages/admin/index.html`：独立超管后台 DOM，含账号列表表格（用户名/手机号、注册时间、活跃情况等列）+ 加载提示 + 降级提示容器
  - **验收条件**：超管后台独立存放于 `frontend/pages/admin/`；含账号列表表格结构
  - **约束**：复用 `common.css` CSS 变量风格与项目一致（REQ-VIO-UBI-026）

### 6.5 新增 admin.js 超管后台逻辑
- [ ] 新增 `frontend/pages/admin/admin.js`：IIFE + `window.Admin = { loadAccounts, guardRoute }` + `module.exports`
  - **核心逻辑**：
    - `guardRoute()`：前端路由守卫——检查 `Auth.getLoginState()` 与 `Auth.isAdmin()`，非登录或非超管用户重定向至登录页（`window.location.href = '../auth/index.html'`）或提示无权限
    - `loadAccounts()`：调用后端超管接口（`fetch` + 超时）→ 渲染账号列表表格（`textContent` 安全渲染）→ 降级提示「账号数据暂不可用」
  - **验收条件**：非超管用户访问超管页→重定向至登录页或提示无权限；超管登录后进入后台→展示账号列表（用户名/手机号、注册时间、活跃情况）；接口不可用时展示可读提示不白屏
  - **约束**：前端路由守卫（REQ-VIO-UBI-026/4.3.4）；后端侧鉴权属协作范围

### 6.6 新增 admin.css 超管后台样式
- [ ] 新增 `frontend/pages/admin/admin.css`：账号列表表格样式 + 加载/降级提示样式，复用既有 CSS 变量
  - **验收条件**：样式与项目内部一致；响应式三档正常展示
  - **约束**：不使用外部默认模板样式（REQ-VIO-UBI-026）

---

## 7. z-index 层叠标注与 common.css 调整

> **关联需求**：REQ-VIO-UBI-044 ~ 045（loading-gate 遮挡排查/z-index 层叠标注）
> **关联决策**：D-VIO-17（loading-gate z-index 保持）、D-VIO-18（z-index 集中注释标注）
> **依赖**：组5（悬浮球 z-index:1000 定义后才能标注完整层叠约定）

### 7.1 在 common.css 新增 z-index 层叠约定集中注释
- [ ] 在 `frontend/assets/css/common.css` 第 432 行附近（weather-bg-layer 定义前）新增集中注释标注 z-index 层叠约定：
  ```
  /* z-index 层叠约定（V4 整合优化）：
     weather-bg-layer(-1) < 内容(0/auto) < weather-fx-overlay(1) < sidebar(100) < 悬浮球(1000) < loading-gate(9999)
     - loading-gate 高 z-index 合理（全屏加载遮罩，隐藏后 opacity:0 + pointer-events:none 不占据层叠）
     - 悬浮球 z-index:1000 确保可见且不遮挡天气卡/表单/列表
     - sidebar z-index:100 确保高于内容层
  */
  ```
  - **验收条件**：common.css 含 z-index 层叠约定集中注释；层叠关系清晰标注
  - **约束**：以集中注释标注避免后续冲突（REQ-VIO 4.4.5）

### 7.2 确认 sidebar z-index 与 loading-gate z-index 保持
- [ ] 确认 `frontend/components/sidebar/sidebar.css` 第 11 行 `.sidebar { z-index: 100; }` 已存在（无需新增）
- [ ] 确认 `frontend/assets/css/common.css` 第 552 行 `#loading-gate { z-index: 9999; }` 保持不变（全屏加载遮罩高 z-index 合理）
  - **验收条件**：sidebar z-index:100、loading-gate z-index:9999、悬浮球 z-index:1000 层叠关系正确
  - **约束**：不改变既有 z-index 值（仅确认 + 注释标注）

---

## 8. 扩充测试断言

> **关联需求**：REQ-VIO-UBI-049（既有断言全绿 + 新增断言）
> **关联决策**：D-VIO-23（静态字符串/正则匹配 + 同步更新表单移除断言）
> **依赖**：组1~7（所有改动完成后才能写断言）

### 8.1 同步更新表单移除相关既有断言
- [ ] 更新 `frontend/tests/pages.test.js` 第 653 行 `assert.ok(myCropsJs.includes('showForm'));` → `assert.ok(!myCropsJs.includes('showForm'));`（或移除此断言并新增 renderList/renderCard/日历联动断言）
  - **改动描述**：移除表单属功能移除，showForm 断言同步调整非弱化（功能已不存在）
  - **验收条件**：此断言调整为断言 my-crops.js 不含 `showForm`（或移除并新增展示态断言）
  - **约束**：属断言同步更新非弱化（REQ-VIO-UBI-049）；既有其余 76 项断言原文保留
- [ ] 更新 `frontend/tests/pages.test.js` 第 666 行 `assert.ok(myCropsCss.includes('.my-crops-form'));` → `assert.ok(!myCropsCss.includes('.my-crops-form'));`（或移除并新增 `.mc-confirm` 断言）
  - **验收条件**：此断言调整为断言 my-crops.css 不含 `.my-crops-form`（或移除并新增确认弹层断言）

### 8.2 新增子菜单结构断言
- [ ] 在 `frontend/tests/pages.test.js` 末尾追加新 test 块：断言 `sidebarJs` 含 `children` 字段 + `my` 一级菜单 + `toggleSubmenu`；断言 `sidebarCss` 含 `.sidebar-submenu`/`.sidebar-submenu--open`/`max-height` 过渡
  - **验收条件**：新断言验证子菜单层级结构、展开/收起样式
  - **关联需求**：REQ-VIO-UBI-001/002/005

### 8.3 新增重命名文案断言
- [ ] 在 `frontend/tests/pages.test.js` 追加断言：`sidebarJs` 含 `MODULE_LABEL_MY_CROPS` 常量 + `我的产品`（通用名称）；`indexHtml` 含 `我的产品`；断言无「我的作物」与「我的产品」混用（技术标识 `my-crops` 除外）
  - **验收条件**：新断言验证重命名文案统一
  - **关联需求**：REQ-VIO-UBI-009/010

### 8.4 新增表单移除断言
- [ ] 在 `frontend/tests/pages.test.js` 追加断言：`indexHtml` 不含 `my-crops-form`/`my-crops-add-btn`；`myCropsJs` 不含 `showForm`/`handleSubmit`
  - **验收条件**：新断言验证表单已移除
  - **关联需求**：REQ-VIO-UBI-013/014

### 8.5 新增删除二次确认弹层断言
- [ ] 在 `frontend/tests/pages.test.js` 追加断言：`myCropsJs` 含 `showConfirmDialog`，不含 `confirm(`（原生 confirm 弹窗）；`myCropsCss` 含 `.mc-confirm`
  - **验收条件**：新断言验证自定义确认弹层替代原生 confirm
  - **关联需求**：REQ-VIO-UBI-019

### 8.6 新增 auth 页面断言
- [ ] 在 `frontend/tests/pages.test.js` 文件读取基座（第 38 行后）新增变量 `authHtml`/`authJs`/`authCss` 读取 `frontend/pages/auth/index.html`/`auth.js`/`auth.css`
- [ ] 追加断言：`authJs` 含 `login`/`register`/`logout`/`getLoginState`/`isAdmin`；`authJs` 含 `LOGIN_STATE_KEY`/`FALLBACK_TEXT`/`RATE_LIMIT_THROTTLE_MS` 具名常量；`authCss` 含登录/注册表单样式
  - **验收条件**：新断言验证 auth 页面存在与关键接口
  - **关联需求**：REQ-VIO-UBI-022

### 8.7 新增 admin 页面断言
- [ ] 在 `frontend/tests/pages.test.js` 文件读取基座新增变量 `adminHtml`/`adminJs`/`adminCss` 读取 `frontend/pages/admin/index.html`/`admin.js`/`admin.css`
- [ ] 追加断言：`adminJs` 含 `loadAccounts`/`guardRoute`；`adminJs` 含路由守卫逻辑（`getLoginState`/`isAdmin`/`location.href`）；`adminCss` 含账号列表表格样式
  - **验收条件**：新断言验证 admin 页面存在与路由守卫
  - **关联需求**：REQ-VIO-UBI-026

### 8.8 新增悬浮球组件断言
- [ ] 在 `frontend/tests/pages.test.js` 文件读取基座新增变量 `floatBallJs`/`floatBallCss` 读取 `frontend/components/floating-ball/float-ball.js`/`float-ball.css`
- [ ] 追加断言：`floatBallJs` 含 `init`/`toggle`/`sendMessage`；含 `LLM_ENDPOINT`/`LLM_TIMEOUT`/`LLM_FALLBACK_TEXT` 具名常量；含 `fetch`/`AbortController`/`textContent`；不含 API Key 硬编码；`floatBallCss` 含悬浮球 + 对话窗口样式；`indexHtml` 含 `float-ball.js`/`float-ball.css` 引入
  - **验收条件**：新断言验证悬浮球组件存在、纯前端 fetch、安全渲染、无 API Key 暴露
  - **关联需求**：REQ-VIO-UBI-030~040

### 8.9 新增天气修复断言
- [ ] 在 `frontend/tests/pages.test.js` 追加断言：`weatherVisualJs` 含 `cur < set`（修复语法错误）；不含 `&&<`（非法语法）
  - **验收条件**：新断言验证 judgeDayNight 语法错误已修复
  - **关联需求**：REQ-VIO-UBI-041~043

### 8.10 新增 z-index 层叠断言
- [ ] 在 `frontend/tests/pages.test.js` 追加断言：`commonCss` 含 z-index 层叠约定集中注释（`z-index 层叠约定` 或等价注释）；`sidebarCss` 含 `z-index: 100`；`floatBallCss` 含 `z-index: 1000`（或在 float-ball.js 内联样式中）
  - **验收条件**：新断言验证 z-index 层叠关系标注
  - **关联需求**：REQ-VIO-UBI-045

### 8.11 确认既有 77 项断言兼容
- [ ] 运行 `node --test frontend/tests/pages.test.js` 确认既有 77 项断言（除组8.1 同步调整的 2 项外）原文保留且全绿
  - **验收条件**：既有断言全绿，仅 showForm/.my-crops-form 2 项同步调整，其余 75 项原文保留
  - **约束**：不得删除/注释/弱化既有断言（REQ-VIO-UBI-049）

---

## 9. 回归验证与交付确认

> **依赖**：组8（测试断言全绿后回归验证）

### 9.1 运行测试套件确认全绿
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认所有测试（既有 77 项 + 新增断言）全绿
  - **验收条件**：测试套件全绿，无失败项
  - **约束**：既有 77 项中仅 showForm/.my-crops-form 2 项同步调整，其余原文保留

### 9.2 响应式三档验证
- [ ] 在 375px、768px、1024px 三档宽度下验证：「我的」子菜单展开/收起正常；登录注册页/超管后台正常展示；悬浮球与对话窗口正常展示不遮挡核心内容；无横向滚动
  - **验收条件**：三档下所有新增/修改功能正常展示，无横向滚动，不遮挡核心内容
  - **关联需求**：REQ-VIO-UBI-008/039

### 9.3 降级场景验证
- [ ] 验证后端账号接口不可用时：登录注册页展示「账号服务暂不可用」降级提示，不白屏不报错，页面其他功能正常
- [ ] 验证大模型 API 不可用/超时时：对话窗口展示「智能问答暂不可用」降级提示，用户消息保留，不卡死不白屏
- [ ] 验证天气数据不可用时：降级为默认主题（fallback），不白屏不报错；loading-gate 在 weather-ready 后隐藏
- [ ] 验证 localStorage 不可用（隐私模式）时：登录态降级 sessionStorage，my-crops-store 已有降级，不白屏不报错
- [ ] 验证 429 限流时：展示友好提示，3 秒内多次 429 仅展示一次不堆叠
  - **验收条件**：所有降级场景优雅降级，不白屏不抛未捕获 JS 错误
  - **关联需求**：REQ-VIO 4.2 可靠性

### 9.4 既有功能回归验证
- [ ] 验证既有左侧栏 switchModule 机制：其余 4 入口（种植推荐/农事日历/病害检查/客服）点击切换行为不变
- [ ] 验证既有作物映射：`crop-map.js` 5 个 cropId（dry_garlic/wheat_winter/corn_summer/rice/soybean）不变
- [ ] 验证既有日历联动：农事日历确认播种后「我的产品」列表自动新增记录
- [ ] 验证既有拍照分析：记录卡片上「拍照分析」入口可用（复用 crop-photo.js）
- [ ] 验证既有主页表单提交链路：生成时间线功能不变
- [ ] 验证既有时间线页渲染、语音组件、分栏布局、查询历史不变
  - **验收条件**：既有已落地功能核心行为不变
  - **关联需求**：REQ-VIO 4.5.6 既有功能兼容

### 9.5 安全性验证
- [ ] 审查 localStorage 中无明文密码存储
- [ ] 审查对话窗口/账号列表用 `textContent` 安全渲染（防 XSS）
- [ ] 审查 float-ball.js/admin.js/auth.js 不在前端硬编码 API Key
- [ ] 审查超管后台前端路由守卫：非超管用户不得访问超管页面
  - **验收条件**：安全性要求满足
  - **关联需求**：REQ-VIO 4.3 安全性

### 9.6 文件范围与不触碰项确认
- [ ] 确认仅修改 `frontend/` 下文件，未修改后端/nginx/shared/data/deploy/docs
- [ ] 确认未引入框架/构建工具/第三方库
- [ ] 确认不触碰文件保持不变：`my-crops-store.js`/`crop-map.js`/`time-period.js`/`mock-data.js`/`photo-capture.js`/`crop-photo.js`/`timeline.html`/`timeline-logic.js`/`timeline.css`/`voice-input.js`/`voice-input.css`/`history-store.js`
  - **验收条件**：文件范围符合红线，不触碰项确认无变更
  - **关联需求**：REQ-VIO 1.4 职责边界

### 9.7 交付确认
- [ ] 确认所有 49 条 EARS 需求（REQ-VIO-001~049）已映射到任务并实现
- [ ] 确认所有 24 条关键决策（D-VIO-01~24）已落实
- [ ] 确认 tasks.md 所有任务项已完成
  - **验收条件**：需求全覆盖、决策全落实、任务全完成

---

## 风险点与边界情况

> 执行中需特别注意的边界情况（基于 design.md 2.6 风险与边界情况）

| 风险点 | 影响任务组 | 应对策略 |
|--------|----------|---------|
| **R1: MODULE_ENTRIES 重构破坏既有 switchModule** | 组2 | switchModule 核心逻辑不变，子菜单项仍按 data-module 切换 content-pane；renderSidebar 扩展保留既有入口渲染；组8 新增断言验证 |
| **R2: 重命名导致既有测试断言失败** | 组3/组8 | 重命名仅改 label 文案，技术标识 data-module="my-crops" 不变；组8 同步更新引用旧文案的断言（仅文案断言，不弱化功能断言） |
| **R3: 移除表单导致 showForm/.my-crops-form 断言失败** | 组4/组8 | 组8.1 同步调整 pages.test.js 第 653/666 行断言为 `!includes`（功能已移除，断言同步更新非弱化） |
| **R4: weather-visual.js 修复后暴露既有隐藏问题** | 组1 | 修复仅改第 129 行语法，既有 fallback 降级机制不变；init 正常执行后 fallback 仍能降级承接 |
| **R5: 后端账号接口未就绪** | 组6 | 前端降级承接：展示「账号服务暂不可用」提示，登录态临时存 sessionStorage，不阻塞页面其他功能 |
| **R6: 大模型 API 不可用/超时** | 组5 | 降级提示「智能问答暂不可用」，用户消息保留，不卡死不白屏；15s 超时兜底 |
| **R7: 429 限流提示堆叠** | 组6 | 3 秒内仅展示一次限流提示（节流），复用 toast UI 模式 |
| **R8: 悬浮球遮挡核心内容** | 组5/组7 | z-index:1000 低于 loading-gate(9999)，高于 sidebar(100)；position:fixed 右下角不遮挡左侧栏；响应式三档适配 |
| **R9: 子菜单展开导致左侧栏滚动异常** | 组2 | 左侧栏 nav 区域 `overflow-y: auto` 纵向滚动（sidebar.css:25 已有），不遮挡其他入口 |
| **R10: localStorage 不可用（隐私模式）** | 组6 | 登录态降级 sessionStorage（会话内）；my-crops-store 已有降级；不白屏不报错 |
| **边界: 子菜单项 implemented 为 false（plots）** | 组2 | 复用既有占位提示机制，点击后展示「该板块正在开发中，敬请期待」 |
| **边界: 移除表单后用户期望手动新增记录** | 组4 | 页面仅展示信息，可通过农事日历确认播种自动新增或后续 T06 接入其他入口 |
| **边界: 移除表单后 localStorage 数据损坏** | 组4 | 忽略损坏数据，重置为空集合，展示空状态提示 |
| **边界: 超管账户未预设** | 组6 | 超管登录失败，提示「超管账户未配置」或通用错误 |
| **边界: 登录态过期/令牌失效** | 组6 | 接口返回 401 时清除登录态，提示重新登录 |
| **边界: 大模型 API 端点未配置** | 组5 | 对话窗口展示「智能问答暂不可用」提示，不发起请求 |
| **边界: 对话窗口移动端遮挡底部操作区** | 组5 | 移动端对话窗口近全屏或适配尺寸，不遮挡底部操作区 |

---

## 需求覆盖追溯

> 确认所有 49 条 EARS 需求已映射到任务组

| 需求编号范围 | 任务组 | 覆盖状态 |
|-------------|--------|---------|
| REQ-VIO-UBI-001 ~ 008（导航栏结构调整） | 组2 | ✅ 全覆盖 |
| REQ-VIO-UBI-009 ~ 012（重命名与泛化） | 组3 | ✅ 全覆盖 |
| REQ-VIO-UBI-013 ~ 019（移除冗余表单） | 组4 | ✅ 全覆盖 |
| REQ-VIO-OPT-020 ~ 029（T10 账号前端） | 组6 | ✅ 全覆盖 |
| REQ-VIO-UBI-030 ~ 040（悬浮球与大模型对话） | 组5 | ✅ 全覆盖 |
| REQ-VIO-UBI-041 ~ 048（天气与动画修复） | 组1 + 组7 | ✅ 全覆盖 |
| REQ-VIO-UBI-049（测试断言） | 组8 | ✅ 全覆盖 |

---

> 本任务规划共 9 个任务组、49 个子任务（### 级别），覆盖 49 条 EARS 需求与 24 条关键决策。
> 关键依赖顺序：组1(天气修复) ∥ 组2(子菜单) → 组3(重命名) → 组4(移除表单) → 组5(悬浮球) ∥ 组6(账号前端) ∥ 组7(z-index标注) → 组8(测试断言) → 组9(回归验证)。
> 改动文件：8 个修改（sidebar.js/sidebar.css/index.html/weather-visual.js/my-crops.js/my-crops.css/common.css/pages.test.js）+ 8 个新增（auth/index.html+auth.js+auth.css + admin/index.html+admin.js+admin.css + float-ball.js+float-ball.css）。
> 不触碰：后端/nginx/shared/data/deploy/docs + my-crops-store.js/crop-map.js/time-period.js/mock-data.js/photo-capture.js/crop-photo.js/timeline.html/timeline-logic.js/timeline.css/voice-input.js/voice-input.css/history-store.js。