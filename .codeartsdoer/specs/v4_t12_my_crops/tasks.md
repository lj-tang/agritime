# V4 T12 我的作物 · 编码任务规划（v4_t12_my_crops）

> 本任务规划基于 `v4_t12_my_crops/spec.md`（REQ-VMC-UBI/EVT/STA/OPT/UNW-001 ~ 059，共 59 条 EARS 需求）与 `design.md`（D-VMC-01 ~ 20 共 20 条关键决策、12 条风险、13 项边界情况）生成。
> 全局约束继承自 `stage2_frontend`/`t00_directory_migration`/`t01_weather_visual`/`v4_t01_ui_revamp`：纯原生 JS + 原生 CSS、前端测试断言不可弱化、`crop-map.js` 5 个 cropId 不变、mock 保持 null 占位不造假、左侧栏 6 板块入口已落地。
> **测试基线修正说明**：`design.md` 多处提及"71 项 test() 调用"，经实际核查 `frontend/tests/pages.test.js` 为 **69 项** `test()` 调用（与 `spec.md` REQ-VMC-UBI-056 的"69 项"一致），本任务规划以实际 **69 项** 为基线，既有 69 项断言须全绿、仅允许新增断言。
> 改动范围：仅限 `frontend/` 目录，不触碰后端/nginx/shared/data/deploy/docs；不引入框架/构建工具/第三方库。

---

## 任务依赖关系总览

```
组1（my-crops-store.js 独立存储）  ──┐
                                     ├──> 组3（my-crops 页面三件套）──┐
组2（crop-photo.js 拍照复用层）   ──┘                                ├──> 组5（新增测试断言）──> 组6（回归验证与部署）
                                     └──> 组4（sidebar.js + index.html 集成接入）──┘
```

- **组1、组2 可并行**：均为独立新增模块，无相互依赖（组2 依赖组1 的 `MyCropsStore.update`，建议组1 先行或同步进行）。
- **组3 依赖组1、组2**：my-crops.js 调用 `MyCropsStore` CRUD 与 `CropPhoto.analyze`。
- **组4 依赖组1、组2、组3**：index.html 引入新增脚本/样式，sidebar.js 切换需 content-pane 就绪。
- **组5 依赖组1~组4**：测试断言针对所有新增/修改文件。
- **组6 依赖组1~组5**：回归验证须在全部代码与测试就绪后执行。

---

## 1. 新建独立存储模块 my-crops-store.js

> 对应 D-VMC-01；关联 REQ-VMC-UBI-005/014/049~055、EVT-018/022/026。
> 目标：实现作物记录的 localStorage 读写（独立存储键 `agritime.my-crops.v1`、上限 200）、降级、淘汰、数据校验，参照既有 `history-store.js` 模式但独立实现不修改既有源文件。
> 文件路径：`frontend/assets/js/my-crops-store.js`（与既有 `history-store.js`/`crop-map.js` 同级共享模块目录，便于 Node 测试 require）。

### 1.1 创建模块骨架与具名常量
- [ ] 创建 `frontend/assets/js/my-crops-store.js`，采用 IIFE 包裹，末尾暴露 `window.MyCropsStore`（浏览器）与 `module.exports`（Node 测试 require），与既有 `history-store.js` 第 6/98/99 行同模式
- [ ] 定义具名常量 `KEY = 'agritime.my-crops.v1'`（独立存储键，不与既有 `agritime.history.v1` 冲突）与 `MAX = 200`（记录总数上限），集中定义不散落硬编码
- [ ] 验收：文件存在；含 `KEY`/`MAX`/`agritime.my-crops.v1`/`200` 字符串；含 `window.MyCropsStore` 与 `module.exports` 导出语句

### 1.2 实现 localStorage 可用性检测与数据校验
- [ ] 实现 `getStore()` 函数：try-catch 检测 `typeof localStorage !== 'undefined' && localStorage`，隐私模式/禁用返回 `null`（参照 `history-store.js` 第 10-15 行）
- [ ] 实现 `safeParse(raw)` 函数：JSON.parse + `Array.isArray` 校验 + filter 对象 + 字段类型校验（recordId/cropName/sownDate 为字符串、height 为数值或 null、photoHistory 为数组），损坏数据返回空数组（参照 `history-store.js` 第 17-26 行并增强字段级校验）
- [ ] 验收：`getStore()` 隐私模式返回 null 不抛错；`safeParse()` 对非法 JSON/非数组/字段缺失均返回 `[]`

### 1.3 实现 list 读取记录集合
- [ ] 实现 `list(store)` 函数：入参 `store` 可注入（便于测试，省略时调用 `getStore()`）；读取 `getItem(KEY)` → `safeParse` → 按 `updatedAt` 倒序排序返回；localStorage 不可用/损坏返回 `[]`，不抛未捕获错误
- [ ] 验收：localStorage 可用时返回记录数组（按 updatedAt 倒序）；不可用时返回 `[]`；异常捕获降级无未捕获错误

### 1.4 实现 add 新增记录与淘汰策略
- [ ] 实现 `genRecordId()` 函数：生成 `'mc-' + Date.now() + '-' + Math.random().toString(36).slice(2,8)`，生成后校验与既有记录不冲突，冲突则重新生成（极小概率，对应 D-VMC-08）
- [ ] 实现 `add(record, store)` 函数：入参 `record` 含 cropName/sownDate 等字段；生成 recordId + createdAt/updatedAt（ISO 8601 时间戳）→ 写入 localStorage → 超 `MAX`(200) 按 `updatedAt` 倒序截断保留最新 200 条（对应 D-VMC-09）→ 返回新集合；localStorage 不可用/异常返回当前 list 不抛错
- [ ] 验收：新增记录含唯一 recordId + createdAt/updatedAt；超 200 条时最旧记录被淘汰总数保持 200；localStorage 不可用时静默失败返回当前 list

### 1.5 实现 update 更新记录
- [ ] 实现 `update(recordId, patch, store)` 函数：按 recordId 定位记录 → 合并 patch（recordId 与 createdAt 不变）→ updatedAt 更新为当前时间戳 → 写入 localStorage → 返回更新后记录；recordId 不存在返回 `null`，localStorage 异常返回 `null`
- [ ] 验收：更新后 updatedAt 刷新、createdAt/recordId 不变；recordId 不存在返回 null；异常返回 null 不抛错

### 1.6 实现 remove 删除记录与 getById 查询
- [ ] 实现 `remove(recordId, store)` 函数：按 recordId 定位并移除 → 写入 localStorage → 返回 `true`；recordId 不存在返回 `false`，localStorage 异常返回 `false`
- [ ] 实现 `getById(recordId, store)` 函数：遍历匹配 recordId，返回记录或 `null`
- [ ] 验收：删除后列表不含该 recordId；不存在返回 false/null；异常不抛错

### 1.7 预留后端 my-crop 接口适配位
- [ ] 在模块内封装 `persistAdapter` 适配位（当前实现为 localStorage 读写降级承接），预留 `persistAdapter.save(record)`/`persistAdapter.load()`/`persistAdapter.remove(recordId)` 接口契约，后端就绪后可切换为 fetch `/api/my-crop/*` 调用，不改动业务逻辑层（对应 D-VMC-12、REQ-VMC-OPT-055）
- [ ] 验收：存在 `persistAdapter` 封装；本任务未实现后端 fetch 调用与账号绑定逻辑

---

## 2. 新建拍照分析复用层 crop-photo.js

> 对应 D-VMC-04/D-VMC-19；关联 REQ-VMC-EVT-036/038、UBI-037/041/042、UNW-039、OPT-040。
> 目标：封装对既有 `photo-capture.js` 的 `PhotoCapture.open(onConfirm, prefer)` 调用，在 onConfirm 回调中承接作物回填与状态补全，不修改既有 `photo-capture.js` 源文件。
> 文件路径：`frontend/components/crop-photo/crop-photo.js`（遵循 V4 目录规范）。
> **依赖**：组1（`MyCropsStore.update`）、既有 `CropMap.resolveCropId`、既有 `PhotoCapture.open`。

### 2.1 创建模块骨架与 analyze 入口
- [ ] 创建 `frontend/components/crop-photo/` 目录与 `crop-photo.js`，采用 IIFE 包裹，暴露 `window.CropPhoto = { analyze: analyze }`
- [ ] 实现 `analyze(recordId, onCropConfirmed)` 函数：内部调用 `window.PhotoCapture.open(onConfirm, prefer)`（既有接口，`photo-capture.js` 第 213-218 行），`prefer` 省略为 undefined 展示双通道按钮
- [ ] 验收：文件存在；含 `PhotoCapture.open` 调用；不修改既有 `photo-capture.js` 源文件

### 2.2 实现 onConfirm 回调承接作物回填
- [ ] 在 `onConfirm(cropName)` 回调中：经 `window.CropMap.resolveCropId(cropName)` 解析 cropId → 回填该记录关联作物（cropName + cropId）
- [ ] 验收：拍照确认候选「水稻」后，该记录 cropName 更新为「水稻」、cropId 为 `rice`；确认「番茄」时 cropId 为 null 但仍更新

### 2.3 实现状态补全确认弹层
- [ ] 拍照确认作物后展示「状态补全确认」弹层（对应 D-VMC-19）：显示识别作物 + 可选发芽状况/长势下拉（预填识别结果或保持当前值）+ 确认/跳过按钮；确认 → 更新关联作物 + 状态字段；跳过 → 仅更新关联作物不补全状态
- [ ] 弹层用 `textContent` 安全渲染，不拼 `innerHTML`；不遮挡左侧栏
- [ ] 验收：拍照确认后展示状态补全弹层；确认补全则更新发芽状况/长势；跳过则仅更新关联作物；弹层不遮挡左侧栏

### 2.4 实现 photoHistory 追加与记录更新
- [ ] 拍照确认后（无论是否补全状态）：将该次识别结果 `{timestamp, recognizedCrop, confidence}` 追加至该记录 `photoHistory` 数组（不存原始全尺寸图片 base64），updatedAt 更新为当前时间戳
- [ ] 调用 `MyCropsStore.update(recordId, patch)` 写入 localStorage，刷新列表展示；若 recordId 已不存在（并发删除），提示「该记录已被删除，无法更新」并关闭弹层
- [ ] 验收：拍照确认后 photoHistory 新增一条识别结果（含时间戳、识别作物、置信度）；updatedAt 更新；列表刷新；localStorage 中无原始全尺寸图片

### 2.5 复用既有失败/超时回落机制
- [ ] 拍照识别失败/超时（既有 800ms 超时）或后端 `/api/recognition` 不可用时，复用既有 `photo-capture.js` 的 `showFallback` 失败回落手动输入机制（第 147-165 行），用户可手动输入作物名确认回填，不卡死、不白屏（对应 REQ-VMC-UNW-039/OPT-040）
- [ ] 验收：识别失败/超时时展示手动输入回落，用户可输入作物名确认回填；流程不中断、不白屏

---

## 3. 新建「我的作物」页面三件套（index.html + my-crops.js + my-crops.css）

> 对应 D-VMC-02/03/07/10/11/14/15/16/17/18；关联 REQ-VMC-UBI-005~035/043~048、EVT-015/018/021/022/025/026/044、UNW-019/023/028/045、OPT-011/012/013/020/035/046。
> 目标：实现「我的作物」业务页面的交互逻辑、样式与独立页面骨架，含记录列表/卡片/表单/分组/空状态/toast/二次确认/日历联动事件订阅。
> 文件路径：`frontend/pages/my-crops/`（`my-crops.js` + `my-crops.css` + `index.html`）。
> **依赖**：组1（`MyCropsStore` CRUD）、组2（`CropPhoto.analyze`）、既有 `CropMap.resolveCropId`/`CropMap.CROPS`。

### 3.1 创建目录与样式文件 my-crops.css
- [ ] 创建 `frontend/pages/my-crops/` 目录与 `my-crops.css`
- [ ] 定义 `#my-crops-pane` 容器布局（占满 `.app-main` 内容区，复用既有 CSS 变量 `--ink`/`--paper`/`--card`/`--leaf`/`--line`/`--radius`/`--shadow` 等，不引入新色彩体系）
- [ ] 定义 `.mc-card`（记录卡片：字段展示 + 编辑/删除/拍照分析操作入口）、`.mc-form`（新增/编辑表单：字段输入控件 + 校验提示 + 保存/取消按钮）、`.mc-group-title`（分组标题）、`.mc-empty`（空状态居中提示 + 添加按钮）、`.mc-toast`（操作反馈提示）、`.mc-confirm`（删除二次确认弹层）、`.mc-error`（字段级校验提示）样式类
- [ ] 定义三档响应式断点：`@media (max-width:767px)`（移动端单栏堆叠）/`@media (min-width:768px)`/`@media (min-width:1024px)`，列表纵向滚动（`.mc-list { overflow-y:auto; }`），左侧栏固定不动，无横向滚动（复用既有 `overflow-x:hidden` 全局规则）
- [ ] 验收：文件存在；含上述样式类；含三档断点；列表纵向滚动；复用既有 CSS 变量

### 3.2 创建交互逻辑骨架与具名常量
- [ ] 创建 `frontend/pages/my-crops/my-crops.js`，采用 IIFE 包裹，暴露 `window.MyCrops = { init, renderList, openForm, validateForm, switchGroup, subscribeSowingConfirmed, GERMINATION_STATUS, GROWTH_VIGOR, HEIGHT_MIN, HEIGHT_MAX, REMARK_MAX, GROUP_BY }`
- [ ] 定义具名常量：`GERMINATION_STATUS = ['未发芽','已发芽','出苗','真叶','成熟']`、`GROWTH_VIGOR = ['旺盛','正常','缓慢','不良']`、`HEIGHT_MIN = 0`、`HEIGHT_MAX = 1000`、`REMARK_MAX = 200`、`GROUP_BY = { CROP:'crop', PLOT:'plot' }`、`SOWING_CONFIRMED_EVENT = 'agritime:sowing-confirmed'`（事件名集中定义，对应 D-VMC-05）
- [ ] 验收：文件存在；含上述具名常量；含 `window.MyCrops` 导出

### 3.3 实现 init 编排与列表渲染
- [ ] 实现 `init()` 函数：读取 `MyCropsStore.list()` → 调用 `renderList` 渲染列表 → 绑定事件（添加按钮/分组切换）→ 调用 `subscribeSowingConfirmed` 订阅日历联动事件；DOMContentLoaded 时自动调用
- [ ] 实现 `renderList(records, groupBy)` 函数：按 `updatedAt` 倒序排列 → 按分组模式（按作物/按地块）分组 → 渲染记录卡片到 `#mc-list`；所有用户可控文本（cropName/plotName/remark/发芽状况/长势）均用 `textContent` 渲染，禁止拼 `innerHTML`（XSS 防护，对应 D-VMC-15）
- [ ] 按作物分组：以 `cropName` 为分组键，分组标题为作物中文名；按地块分组：以 `plotName` 为分组键，`plotName` 为 null 归入「未指定地块」分组（对应 D-VMC-07）
- [ ] 记录为空时展示空状态提示（如「还没有作物记录，点击添加你的第一株作物」）+ 添加按钮（`#mc-empty`）
- [ ] 验收：进入页面 ≤100ms 呈现列表骨架；记录按 updatedAt 倒序；分组切换正确；空状态展示；用户文本以 textContent 渲染

### 3.4 实现新增/编辑表单与校验
- [ ] 实现 `openForm([recordId])` 函数：recordId 省略为新增（默认值 germinationStatus='未发芽'、growthVigor='正常'），提供为编辑（预填该记录当前字段值）；表单含种下时间（原生 `<input type="date">`，对应 D-VMC-18）、发芽状况（下拉枚举）、高度（数值输入）、长势（下拉枚举）、关联作物（输入框 + 联想下拉）、关联地块（输入框）、备注（textarea）字段
- [ ] 实现关联作物联想：输入框 input 事件 → `CropMap.CROPS.filter(c => c.name.indexOf(kw) !== -1)` 本地匹配候选下拉（与主页搜索联想同模式，对应 D-VMC-17）→ 选择候选回填 cropName，cropId 由 `resolveCropId` 解析
- [ ] 实现 `validateForm(values)` 函数：校验必填 sownDate（YYYY-MM-DD 格式）、高度范围 [0, 1000]、备注长度 ≤200；返回 `{valid, errors}`；校验失败展示字段级 `.mc-error` 提示，表单保持展开（对应 D-VMC-03）
- [ ] 验收：新增表单含全部字段且默认值正确；编辑表单预填当前值；校验失败时对应字段展示提示且表单保持展开、不写入 localStorage

### 3.5 实现保存/删除/分组切换/操作反馈
- [ ] 实现保存记录：校验通过 → 新增时调用 `MyCropsStore.add`（生成 recordId + createdAt/updatedAt）/编辑时调用 `MyCropsStore.update`（updatedAt 刷新，createdAt/recordId 不变）→ 刷新列表 → 关闭表单 → 清空字段 → toast 提示「添加成功」/「编辑成功」；编辑时若 recordId 已不存在提示「该记录已被删除，无法保存」并关闭表单
- [ ] 实现删除二次确认：点击「删除」→ 展示 `.mc-confirm` 弹层（非 `confirm()`，含「确认删除」与「取消」按钮，对应 D-VMC-11）→ 确认后调用 `MyCropsStore.remove` + 刷新列表 + toast「删除成功」→ 取消则关闭弹层不修改；弹层不遮挡左侧栏
- [ ] 实现 `switchGroup(groupBy)` 函数：切换 `#mc-group-toggle` 的 `data-group-by` 属性，重新渲染列表，纯前端本地分组计算 ≤100ms，不发起网络请求
- [ ] 实现 `showMcToast(msg, type)` 函数：独立 toast 实例 `#mc-toast`（非 alert，不依赖主页 `#toast` 避免作用域耦合，对应 D-VMC-10），显示后 ≤3s 自动隐藏
- [ ] 验收：保存成功后列表刷新 + 表单关闭清空 + toast 提示；删除有二次确认；分组切换 ≤100ms 无网络请求；toast 非 alert 且 ≤3s 隐藏

### 3.6 实现日历联动事件订阅
- [ ] 实现 `subscribeSowingConfirmed()` 函数：`window.addEventListener(SOWING_CONFIRMED_EVENT, handler)` 订阅「确认播种」事件（对应 D-VMC-05）
- [ ] handler 校验载荷合法性：cropName 非空字符串 + sowDate YYYY-MM-DD 格式；非法载荷跳过不新增记录、不报错、不污染既有记录集合（对应 REQ-VMC-UNW-045）
- [ ] 载荷合法时：经 `CropMap.resolveCropId(cropName)` 解析 cropId → 构造新记录（关联作物/种下时间/关联地块取自事件，默认 germinationStatus='未发芽'/growthVigor='正常'/photoHistory=[]）→ `MyCropsStore.add` 写入 → 刷新列表 → toast「已从农事日历新增作物记录」；≤200ms 完成，不阻塞农事日历板块交互
- [ ] 验收：合法事件触发自动新增记录 + toast 提示；非法事件跳过不报错；自动新增 ≤200ms

### 3.7 创建独立页面骨架 my-crops/index.html
- [ ] 创建 `frontend/pages/my-crops/index.html`：完整 HTML 骨架（`<!DOCTYPE html>` + `<head>` 引入 my-crops.css + sidebar.css + `<body data-layout="app">` 含 sidebar + my-crops content-pane 骨架 DOM + 引入 my-crops-store.js/crop-map.js/photo-capture.js/crop-photo.js/my-crops.js/sidebar.js）
- [ ] 页面骨架 DOM 与组4 中 index.html content-pane 内骨架一致（`#mc-list`/`#mc-add-btn`/`#mc-form-wrap`/`#mc-group-toggle`/`#mc-empty`/`#mc-toast`/`#mc-confirm`）
- [ ] 验收：文件存在；含完整 HTML 骨架与脚本/样式引入；骨架 DOM id 与主页 content-pane 一致；可独立访问或供测试读取

---

## 4. 修改 sidebar.js 与 index.html 完成集成接入

> 对应 D-VMC-06/D-VMC-02；关联 REQ-VMC-UBI-001/002/003/004。
> 目标：将既有左侧栏 `my-crops` 入口的 `implemented` 标记为 true，在 index.html 新增 `data-module="my-crops"` content-pane 并引入新增脚本/样式，复用既有 `switchModule` 机制。
> **依赖**：组1、组2、组3（新增脚本/样式/页面资源就绪）。

### 4.1 修改 sidebar.js 启用 my-crops 入口
- [ ] 修改 `frontend/components/sidebar/sidebar.js` 第 12 行：将 `{ id: 'my-crops', label: '我的作物', icon: '🌾', implemented: false }` 的 `implemented: false` 改为 `implemented: true`（单行常量改动）
- [ ] 其余 5 板块入口（recommend/plots/calendar/disease/service）与顺序不变；`renderSidebar`/`switchModule`/`toggleDrawer`/`init` 函数不变
- [ ] 验收：sidebar.js 第 12 行 `my-crops` 入口 `implemented: true`；既有测试第 577-588 行断言（含 MODULE_ENTRIES/renderSidebar/switchModule/toggleDrawer/6 板块 id 字符串）不破坏

### 4.2 修改 index.html 新增 my-crops content-pane
- [ ] 在 `frontend/pages/index.html` 第 106 行后（calendar content-pane 闭合 `</div>` 后）新增 `<div class="content-pane" data-module="my-crops" id="my-crops-pane">`，内含骨架 DOM：`#mc-list`（列表容器）、`#mc-add-btn`（添加按钮）、`#mc-form-wrap`（表单容器）、`#mc-group-toggle`（分组切换）、`#mc-empty`（空状态）、`#mc-toast`（toast）、`#mc-confirm`（二次确认）
- [ ] 既有 `<main class="page page--home">`/表单/历史/天气卡/timeline-pane/calendar-pane DOM 不变
- [ ] 验收：index.html 含 `data-module="my-crops"` + `id="my-crops-pane"` + 骨架 DOM id；既有测试第 625-632 行断言（含 sidebar.css/sidebar.js/id="sidebar"/id="sidebar-nav"/data-module="calendar"/id="module-placeholder"）不破坏

### 4.3 修改 index.html 引入新增样式与脚本
- [ ] 在 index.html `<head>` 内（sidebar.css 引入后）新增 `<link rel="stylesheet" href="my-crops/my-crops.css">`
- [ ] 在 index.html 脚本加载区（第 113-120 行区间）按依赖顺序插入：`<script src="../assets/js/my-crops-store.js"></script>`（在 crop-map.js/history-store.js 之后）、`<script src="../components/crop-photo/crop-photo.js"></script>`（在 photo-capture.js 之后）、`<script src="my-crops/my-crops.js"></script>`（在 sidebar.js 之后，确保 Sidebar/CropMap/MyCropsStore/CropPhoto 就绪）
- [ ] 既有脚本顺序与既有表单字段/提交链路不变
- [ ] 验收：index.html 含 my-crops.css/my-crops-store.js/crop-photo.js/my-crops.js 引入；引入顺序满足依赖（store → crop-photo → my-crops）；既有脚本顺序不变

---

## 5. 新增测试断言

> 对应 D-VMC-13；关联 REQ-VMC-UBI-056/057。
> 目标：在 `frontend/tests/pages.test.js` 末尾追加 my-crops 相关 test 块，既有 69 项断言原文保留不修改；新增断言为静态字符串/正则匹配 + 纯函数测试（与既有风格一致）。
> 文件路径：`frontend/tests/pages.test.js`。
> **依赖**：组1~组4（所有新增/修改文件就绪）。

### 5.1 新增文件读取变量
- [ ] 在 `pages.test.js` 第 34 行后（文件读取基座区，既有变量之后）新增变量：`myCropsJs`（read `my-crops/my-crops.js`）、`myCropsCss`（read `my-crops/my-crops.css`）、`myCropsHtml`（read `my-crops/index.html`）、`myCropsStoreJs`（read `../assets/js/my-crops-store.js`）、`cropPhotoJs`（read `../components/crop-photo/crop-photo.js`）
- [ ] 既有变量（indexHtml/sidebarJs/commonCss 等）不修改
- [ ] 验收：新增 5 个 read 变量；既有变量不变；文件路径正确

### 5.2 新增 content-pane 与 sidebar 集成断言
- [ ] 新增 test 块断言：`indexHtml` 含 `data-module="my-crops"` + `id="my-crops-pane"` + `#mc-list`/`#mc-add-btn`/`#mc-form-wrap`/`#mc-group-toggle`/`#mc-empty`/`#mc-toast`/`#mc-confirm` 骨架 DOM id
- [ ] 新增 test 块断言：`sidebarJs` 含 `my-crops` 入口 `implemented: true`（正则匹配 `my-crops`.*`implemented:\s*true`）
- [ ] 新增 test 块断言：`indexHtml` 含 my-crops.css/my-crops-store.js/crop-photo.js/my-crops.js 引入
- [ ] 验收：断言通过；不修改既有第 577-588/625-632 行断言

### 5.3 新增 my-crops-store.js 存储模块断言
- [ ] 新增 test 块断言：`myCropsStoreJs` 含 `agritime.my-crops.v1`（独立存储键）+ `MAX = 200`（上限）+ `getStore`/`safeParse`/`list`/`add`/`update`/`remove`/`getById` 函数 + try/catch 降级 + `window.MyCropsStore` + `module.exports`
- [ ] 新增 test 块断言：`myCropsStoreJs` 不含 `agritime.history.v1`（不污染既有存储键）
- [ ] 验收：断言通过；确认独立存储键与既有 history-store.js 不冲突

### 5.4 新增 my-crops-store.js 纯函数测试
- [ ] 新增 test 块：`require('../assets/js/my-crops-store.js')` 注入 mock store（与既有 history-store.js 第 289-309 行测试同模式），测试 CRUD 流程：add 生成唯一 recordId + createdAt/updatedAt → list 按 updatedAt 倒序 → update 更新字段且 createdAt/recordId 不变 → remove 删除 → getById 查询
- [ ] 测试上限淘汰：注入 201 条记录后 add 第 202 条，验证总数保持 200 且最旧记录被淘汰
- [ ] 测试降级：注入 null store，验证 list 返回 `[]`、add/update/remove 静默失败不抛错
- [ ] 测试损坏数据：注入非法 JSON/非数组 store，验证 safeParse 返回 `[]` 不报错
- [ ] 验收：纯函数测试通过；CRUD/上限/降级/损坏数据场景均覆盖

### 5.5 新增 my-crops.js 交互逻辑断言
- [ ] 新增 test 块断言：`myCropsJs` 含具名常量 `GERMINATION_STATUS`/`GROWTH_VIGOR`/`HEIGHT_MIN`/`HEIGHT_MAX`/`REMARK_MAX`/`GROUP_BY`/`SOWING_CONFIRMED_EVENT`
- [ ] 断言：`myCropsJs` 含 `init`/`renderList`/`openForm`/`validateForm`/`switchGroup`/`subscribeSowingConfirmed` 函数
- [ ] 断言：`myCropsJs` 含表单校验逻辑（必填 sownDate、高度 [0,1000] 范围、备注 ≤200 长度）
- [ ] 断言：`myCropsJs` 含按作物/按地块分组逻辑 + 「未指定地块」分组
- [ ] 断言：`myCropsJs` 含删除二次确认（非 `confirm(`）+ toast 操作反馈（非 `alert(`）
- [ ] 断言：`myCropsJs` 含 `window.addEventListener` + `agritime:sowing-confirmed` 事件订阅 + 载荷校验
- [ ] 断言：`myCropsJs` 含 `textContent` 安全渲染（不含 `innerHTML` 拼接用户输入）
- [ ] 验收：断言通过；字段枚举/校验/分组/二次确认/toast/事件订阅/XSS 防护均覆盖

### 5.6 新增 crop-photo.js 与样式断言
- [ ] 新增 test 块断言：`cropPhotoJs` 含 `PhotoCapture.open` 调用 + `onConfirm` 回调 + `resolveCropId` 调用 + `MyCropsStore.update` 调用 + `window.CropPhoto`
- [ ] 新增 test 块断言：`myCropsCss` 含 `.mc-card`/`.mc-form`/`.mc-empty`/`.mc-toast`/`.mc-confirm`/`.mc-error`/`.mc-group-title` 样式类 + 三档响应式断点（`max-width:767px`/`min-width:768px`/`min-width:1024px`）+ 列表纵向滚动（`overflow-y:auto`）
- [ ] 新增 test 块断言：`myCropsHtml` 含完整 HTML 骨架 + my-crops.js/css 引入 + 骨架 DOM id
- [ ] 验收：断言通过；拍照复用层/样式/独立页面骨架均覆盖

### 5.7 确认既有 69 项断言兼容
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 69 项断言全绿（新增断言为独立 test 块，不修改既有断言原文）
- [ ] 验收：既有 69 项 pass + 新增断言 pass，无 fail

---

## 6. 回归验证与部署

> 关联 REQ-VMC-UBI-056/057/058/059、7.5 交付任务。
> 目标：确认既有 69 项 + 新增断言全绿、功能验证、响应式验证、XSS 防护验证、改动范围审查、Git 提交推送。
> **依赖**：组1~组5 全部完成。

### 6.1 测试套件全绿验证
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 69 项断言 + 新增断言全部通过，无 fail
- [ ] 验收：测试输出全 pass，无 fail；既有 69 项断言原文保留且通过

### 6.2 浏览器交互功能验证
- [ ] 验证左侧栏点击「我的作物」入口切换至业务页面（展示记录列表 + 添加按钮，不展示占位提示）
- [ ] 验证添加作物记录全流程：点击「添加作物」→ 录入字段 → 保存 → 记录写入 localStorage（键 `agritime.my-crops.v1`）→ 列表刷新出现新记录 → toast「添加成功」
- [ ] 验证编辑作物记录全流程：点击「编辑」→ 修改字段 → 保存 → updatedAt 刷新、createdAt/recordId 不变 → toast「编辑成功」
- [ ] 验证删除作物记录全流程：点击「删除」→ 二次确认 → 确认删除 → 记录移除 → toast「删除成功」
- [ ] 验证拍照分析复用既有弹层：点击「拍照分析」→ 展开既有 photo-capture.js 双通道弹层 → 确认候选 → 作物回填 + photoHistory 追加
- [ ] 验证确认播种事件触发自动新增：控制台执行 `window.dispatchEvent(new CustomEvent('agritime:sowing-confirmed', {detail:{cropName:'水稻', sowDate:'2026-04-15'}}))` → 列表自动新增记录 + toast 提示
- [ ] 验证按作物/地块分组展示切换：点击分组切换 → 列表重新分组 ≤100ms、无网络请求
- [ ] 验收：上述 7 项交互均符合预期

### 6.3 降级与边界验证
- [ ] 验证 localStorage 不可用时降级：隐私模式/禁用 localStorage 后进入「我的作物」→ 展示空列表 + 「本地存储不可用」提示 → 板块切换正常 → 无白屏、无未捕获错误
- [ ] 验证记录上限 200 淘汰：添加第 201 条记录 → 最旧记录被淘汰 → 总数保持 200
- [ ] 验证非法事件载荷跳过：执行 `window.dispatchEvent(new CustomEvent('agritime:sowing-confirmed', {detail:{cropName:'水稻'}}))`（缺 sowDate）→ 不新增记录、不报错
- [ ] 验收：降级/上限/非法载荷场景均优雅处理不白屏不报错

### 6.4 响应式与 XSS 防护验证
- [ ] 验证响应式三档：375px/768px/1024px 宽度下查看「我的作物」页面 → 无横向滚动条 → 列表/表单/卡片正常展示 → 列表纵向滚动 → 左侧栏固定
- [ ] 验证 XSS 防护：录入备注含 `<script>alert(1)</script>` 后查看列表 → 以纯文本展示 → 不执行脚本
- [ ] 验收：三档无横向滚动；XSS 脚本不执行

### 6.5 改动范围审查与 Git 提交
- [ ] 运行 `git diff --stat` 审查改动文件均在 `frontend/` 下，无后端/data/shared/deploy/docs 改动；确认不触碰既有 `photo-capture.js`/`crop-map.js`/`history-store.js`/`sidebar.css`/`weather-visual.js`/`time-period.js`/`voice-input.js`/`timeline.html` 源文件
- [ ] 确认未引入任何框架/构建工具/第三方库（无新增 package.json 依赖、无 import/require 第三方库语句）
- [ ] Git 提交并推送至 GitHub 仓库 `lj-tang/agritime`，提交信息须体现 T12 任务编号（如 `feat(T12): 实现我的作物功能模块`）
- [ ] 验收：git diff 改动范围限 frontend/；无新依赖；提交推送成功

---

## 风险点与边界情况备忘

> 执行中需特别注意的边界情况（对应 design.md 2.7 风险与边界）。

| 风险/边界 | 注意事项 | 关联组 |
|---------|---------|-------|
| **测试基线 69 项（非 71 项）** | design.md 多处误写"71 项"，实际为 69 项 `test()` 调用，以实际代码为准；既有 69 项断言原文保留不修改 | 组5/组6 |
| **既有断言不破坏** | 第 577-588 行（sidebar.js 含 MODULE_ENTRIES/renderSidebar/switchModule/toggleDrawer/6 板块 id）与第 625-632 行（index.html 含 sidebar.css/sidebar.js/id="sidebar"/id="sidebar-nav"/data-module="calendar"/id="module-placeholder"）断言须保留通过 | 组4/组5 |
| **独立存储键不污染既有** | `my-crops-store.js` 必须用 `agritime.my-crops.v1`，不得与既有 `agritime.history.v1` 冲突；不修改既有 `history-store.js` 源文件 | 组1 |
| **不修改既有 photo-capture.js** | `crop-photo.js` 仅调用 `PhotoCapture.open`，不修改既有源文件的双通道/人工确认/800ms 超时/失败回落机制 | 组2 |
| **不修改既有 crop-map.js** | 仅消费 `resolveCropId`/`CROPS`，5 个 cropId 与 6 作物条目不变 | 组2/组3 |
| **sidebar.js 仅改 1 行** | 只改第 12 行 `implemented: false` → `true`，`renderSidebar`/`switchModule`/`toggleDrawer`/`init` 函数不变，6 板块顺序与标识不变 | 组4 |
| **XSS 安全渲染** | 所有用户可控文本（cropName/plotName/remark/发芽状况/长势）均用 `textContent` 渲染，禁止拼 `innerHTML` | 组3 |
| **localStorage 降级不白屏** | 隐私模式/禁用/配额满时静默降级为只读空列表 + 中性提示，不抛未捕获错误、不白屏 | 组1/组3 |
| **损坏数据重置空集合** | `safeParse` 校验 JSON 解析 + Array.isArray + 字段类型，损坏数据返回 `[]` 不报错 | 组1 |
| **recordId 冲突重生成** | 生成后校验唯一性，冲突则重新生成（极小概率） | 组1 |
| **删除二次确认非 confirm()** | 用自定义 `.mc-confirm` 弹层，非原生 `confirm()`，不遮挡左侧栏 | 组3 |
| **toast 非 alert** | 用独立 `#mc-toast` 实例，非原生 `alert()`，不依赖主页 `#toast` 避免作用域耦合 | 组3 |
| **拍照不存原始全尺寸图片** | photoHistory 仅存识别结果文本（timestamp/recognizedCrop/confidence），不存 base64 图片 | 组2 |
| **日历联动非法载荷跳过** | 事件处理器校验 cropName 非空字符串 + sowDate YYYY-MM-DD 格式，非法跳过不新增、不报错 | 组3 |
| **多标签页并发编辑/删除** | 保存时检测 recordId 是否存在，不存在则提示「该记录已被删除」并关闭表单 | 组3 |
| **状态补全经用户确认** | 拍照确认作物后展示状态补全确认弹层，不自动保存状态补全（给用户确认机会） | 组2 |
| **脚本加载顺序** | index.html 引入顺序须满足依赖：my-crops-store.js（在 crop-map/history-store 后）→ crop-photo.js（在 photo-capture 后）→ my-crops.js（在 sidebar 后） | 组4 |
| **content-pane 切换不冲突** | 复用既有 switchModule 的 `.content-pane--active` toggle 机制，同一时刻仅一个 content-pane display:block | 组4 |