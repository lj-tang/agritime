# V4 T06 农事日历地图标点功能 · 编码任务规划（v4_map_marker）

> 本任务规划基于 `v4_map_marker/spec.md`（45 条 EARS 需求 REQ-VMM-001~045）与 `design.md`（19 条关键决策 D-VMM-01~19，35 项改动点 C-01~C-35）生成。
> 范围红线：纯前端地图标点功能，仅修改 `frontend/` 目录下文件；前端既有 77 项测试全绿仅允许新增断言；不引入框架/构建工具（唯一例外：CDN 引入开源免费 Leaflet）；不修改后端/nginx/shared/data/deploy/docs；地图 API 加载失败降级为既有文字输入不白屏不报错。
> 改动文件清单：2 个新增（`location-picker.js`/`location-picker.css`）+ 3 个修改（`index.html`/`timeline.html`/`pages.test.js`）。
> 任务依赖顺序：组1（组件 JS）∥ 组2（组件 CSS）→ 组3（index.html 集成）∥ 组4（timeline.html 集成）→ 组5（测试断言）→ 组6（回归验证与部署）。

---

## 1. 新建 location-picker 组件交互逻辑

> 对应 design.md D-VMM-01~13/17、改动点 C-01~C-13、需求 REQ-VMM-UBI-001/005/007、EVT-003/009/012/013/018/020/024、OPT-016、UNW-036~039。
> 目标：在 `frontend/components/location-picker/location-picker.js` 封装 Leaflet 动态加载、地图初始化、标点放置、搜索定位、反向地理编码、位置回填、降级、只读展示八项能力，以 IIFE + `window.LocationPicker` 暴露 + `module.exports` 供测试 require。

### 1.1 创建组件目录与文件骨架
- [ ] 新建目录 `frontend/components/location-picker/`，创建空文件 `location-picker.js`，遵循 V4 目录规范（与既有 sidebar/voice-input/crop-photo/floating-ball 组件并列）
- [ ] 在 `location-picker.js` 顶部写入 IIFE 骨架 `(function () { ... })();` 与 `window.LocationPicker` 暴露对象 `{ init, open, close, initDisplay, isReady }`，末尾追加 `if (typeof module !== 'undefined' && module.exports) module.exports = window.LocationPicker;` 供测试 require（对应 C-01，验收：文件存在且含 `window.LocationPicker` 与 `module.exports`）

### 1.2 集中定义具名常量
- [ ] 在 IIFE 内顶部集中定义所有可配置项具名常量（对应 C-02，D-VMM-17）：`LEAFLET_CDN_URL`（如 `https://unpkg.com/leaflet@1.9.4/dist/leaflet.js`）、`LEAFLET_CSS_CDN_URL`（如 `https://unpkg.com/leaflet@1.9.4/dist/leaflet.css`）、`OSM_TILE_TEMPLATE`（如 `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`）、`NOMINATIM_ENDPOINT`（`https://nominatim.openstreetmap.org`）、`DEFAULT_CENTER`（`{lat:35.07, lon:116.31}`）、`DEFAULT_ZOOM`（`12`）、`LOAD_TIMEOUT_MS`（`5000`）、`SEARCH_TIMEOUT_MS`（`5000`）、`GEO_TIMEOUT_MS`（`3000`）、`MAX_SEARCH_RESULTS`（`10`）、`DEGRADATION_TEXT`（`'地图功能暂不可用，请文字输入地点'`）、`SEARCH_DEGRADATION_TEXT`（`'搜索服务暂不可用'`）、`TILE_DEGRADATION_TEXT`（`'地图底图加载失败'`）、`NO_RESULT_TEXT`（`'未找到相关地点'`）、`NO_MARKER_TEXT`（`'请在地图上选择一个位置'`）、`INVALID_LATLON_TEXT`（`'位置信息异常，请重新选择'`）、`MAP_PICKER_BTN_ID`（`'map-picker-btn'`）、`LOCATION_INPUT_ID`（`'location'`）
- [ ] 验收：常量均以 `const`/`var` 声明于模块顶部，全文件无其他硬编码 CDN/端点/超时/文案（REQ-VMM-UBI-005）

### 1.3 实现 Leaflet 动态加载与就绪态缓存
- [ ] 实现 `loadLeaflet()` 内部函数（对应 C-08，D-VMM-01）：动态创建 `<script>` 标签引入 `LEAFLET_CDN_URL` + `<link>` 标签引入 `LEAFLET_CSS_CDN_URL`（均 HTTPS），注入 `document.head`；返回 `Promise<boolean>`，resolve on `script.onload` / reject on `script.onerror` 或 `LOAD_TIMEOUT_MS` 超时（用 `setTimeout` + `Promise.race`）；模块级变量 `leafletReady`/`leafletLoading` 缓存就绪态，已就绪直接 resolve(true)，加载中复用同一 Promise 避免重复注入（REQ-VMM 4.1.1 性能，R18）
- [ ] 实现 `isReady()` 公开函数（对应 C-07）：返回 `leafletReady` 布尔值（REQ-VMM-UBI-007）
- [ ] 验收：首次调用注入 script/link，二次调用复用缓存；超时/失败 reject 不抛未捕获错误（REQ-VMM-UNW-036）

### 1.4 实现地图选点弹层展开与 DOM 创建
- [ ] 实现 `open(onConfirm)` 公开函数（对应 C-04，D-VMM-04）：动态创建弹层 DOM 注入 `document.body`，结构为 `<div class="location-picker-modal" id="location-picker-modal">` 含遮罩 `.location-picker-overlay` + 弹层主体 `.location-picker-dialog`（搜索框 `.location-picker-search` 含 input + 搜索按钮 + 候选列表 `.location-picker-results` + 地图画布容器 `.location-picker-map` + 位置信息 `.location-picker-info` + 操作按钮 `.location-picker-confirm`「确认位置」/`.location-picker-cancel`「取消」）；弹层 `position:fixed; inset:0; z-index:2000`（D-VMM-14）
- [ ] 在 `open` 内展示「地图加载中…」进度反馈，调用 `loadLeaflet()`；加载成功后初始化 Leaflet 地图（`L.map(.location-picker-map)`）+ 加载 OSM 瓦片层（`L.tileLayer(OSM_TILE_TEMPLATE)`）；加载失败调用 `degrade('leaflet-load-failed')` 并允许关闭弹层（REQ-VMM-UNW-036/037）
- [ ] 实现地图初始视图中心策略（D-VMM-05）：尝试 `navigator.geolocation.getCurrentPosition`（`GEO_TIMEOUT_MS` 超时），成功 `map.setView([lat, lon], DEFAULT_ZOOM)`，失败/拒绝/超时/不支持回退 `map.setView([DEFAULT_CENTER.lat, DEFAULT_CENTER.lon], DEFAULT_ZOOM)`；不反复弹出授权请求（仅 open 时尝试一次，REQ-VMM-UNW-039）
- [ ] 隐藏进度反馈，展示可交互地图；弹层展开后调用 `map.invalidateSize()` 确保画布正确渲染（D-VMM-15）
- [ ] 验收：点击「地图标点」按钮后弹层展开含地图画布/搜索框/候选列表区/位置信息区/确认与取消按钮；地理定位可用时定位当前位置，不可用时定位默认中心不报错（REQ-VMM-EVT-003/009）

### 1.5 实现标点放置与拖拽交互
- [ ] 实现 `placeMarker(lat, lon)` 内部函数（对应 C-09，D-VMM-06）：模块级变量 `currentMarker` 保持单引用，放置新标点前若 `currentMarker` 存在则 `currentMarker.remove()`（单选模式，REQ-VMM-UBI-014）；创建 `L.marker([lat, lon], {draggable:true})` 添加到地图；展示经纬度到 `.location-picker-info`（textContent 安全渲染，REQ-VMM-UBI-015）；触发 `reverseGeocode(lat, lon)` 获取地名
- [ ] 绑定地图 `click` 事件：`map.on('click', function(e){ placeMarker(e.latlng.lat, e.latlng.lng); })`（REQ-VMM-EVT-012）
- [ ] 绑定 marker `drag` 事件实时更新经纬度展示、`dragend` 事件触发反向地理编码获取新位置地名（REQ-VMM-EVT-013）
- [ ] 验收：点击地图某位置出现标点展示经纬度与地名；拖拽标点经纬度实时更新，拖拽结束展示新地名；多次点击仅保留最后一个标点无残留（REQ-VMM-EVT-012/013/UBI-014）

### 1.6 实现反向地理编码
- [ ] 实现 `reverseGeocode(lat, lon)` 内部函数（对应 C-10，D-VMM-07）：`fetch(NOMINATIM_ENDPOINT + '/reverse?format=json&lat=' + lat + '&lon=' + lon + '&zoom=18&accept-language=zh', {signal})` + `AbortController` + `SEARCH_TIMEOUT_MS` 超时；成功解析 `data.display_name` 返回地名字符串；失败/超时/网络异常返回 null（仅展示经纬度不报错不阻塞确认，REQ-VMM-OPT-016）；地名经 textContent 安全渲染（REQ-VMM 4.3.1）
- [ ] 验收：放置标点后反向编码可用时展示地名，失败时仅展示经纬度不报错可确认（REQ-VMM-OPT-016/UBI-015）

### 1.7 实现文字搜索定位
- [ ] 实现 `searchGeocode(kw)` 内部函数（对应 C-11，D-VMM-08）：`fetch(NOMINATIM_ENDPOINT + '/search?format=json&q=' + encodeURIComponent(kw) + '&limit=' + MAX_SEARCH_RESULTS + '&accept-language=zh', {signal})` + `AbortController` + `SEARCH_TIMEOUT_MS` 超时；成功解析候选列表（限 Top 10）返回 `Array<{displayName, lat, lon}>`；失败/超时返回 null
- [ ] 绑定搜索框 input + 回车/搜索按钮事件：空关键词不发起请求隐藏候选列表（R9）；非空展示「搜索中…」进度反馈，调用 `searchGeocode(kw)`；成功且非空渲染候选列表（textContent 安全渲染，含地名+经纬度概要，REQ-VMM-EVT-019）；成功但空列表展示 `NO_RESULT_TEXT`（REQ-VMM-UNW-021）；失败/超时展示 `SEARCH_DEGRADATION_TEXT` 用户仍可点击/拖拽标点（REQ-VMM-UNW-022）
- [ ] 绑定候选列表点选事件：点选候选 → `map.setView([candidate.lat, candidate.lon], DEFAULT_ZOOM)` → `placeMarker(candidate.lat, candidate.lon)` → 展示候选地名与经纬度（REQ-VMM-EVT-020）
- [ ] 验收：输入「金乡」并回车展示「搜索中…」后展示候选列表；点选候选地图定位并放置标点；无结果展示「未找到相关地点」；服务不可用展示「搜索服务暂不可用」可点击/拖拽标点（REQ-VMM-EVT-018/019/020/UNW-021/022/UBI-023）

### 1.8 实现经纬度校验与标点位置回填
- [ ] 实现 `validateLatLon(lat, lon)` 内部函数（对应 C-12）：返回 `lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180`（REQ-VMM-UBI-026）
- [ ] 绑定「确认位置」按钮 click 事件（对应 D-VMM-09）：若 `currentMarker` 不存在展示 `NO_MARKER_TEXT` 不回填（REQ-VMM-UBI-028）；若存在获取标点 lat/lon/locationText，调用 `validateLatLon`，校验通过调用 `onConfirm({lat, lon, locationText, source:'marker'|'search'})` 回调并 `close()` 关闭弹层；校验失败展示 `INVALID_LATLON_TEXT` 不回填弹层保持展开（REQ-VMM-UBI-026）
- [ ] 验收：放置标点并点击确认后 onConfirm 被调用弹层关闭；未放置标点点击确认提示不回填；经纬度非法不回填提示异常（REQ-VMM-EVT-024/UBI-026/028）

### 1.9 实现弹层关闭与降级机制
- [ ] 实现 `close()` 公开函数（对应 C-05）：移除弹层 DOM（`document.getElementById('location-picker-modal').remove()`），重置 `currentMarker = null`，不回填任何位置（REQ-VMM-UBI-010/028）
- [ ] 绑定「取消」按钮与遮罩点击事件：调用 `close()`（REQ-VMM-UBI-010）
- [ ] 实现 `degrade(reason)` 内部函数（对应 C-13，D-VMM-12）：按 reason 展示对应降级提示（`'leaflet-load-failed'` → `DEGRADATION_TEXT`；`'tile-load-failed'` → `TILE_DEGRADATION_TEXT`；`'search-unavailable'` → `SEARCH_DEGRADATION_TEXT`；`'geo-unavailable'` → 回退默认中心）；所有降级路径 try-catch 捕获异常不抛未捕获 JS 错误（REQ-VMM 4.2.1）
- [ ] 验收：点击取消或遮罩弹层关闭表单不变未确认标点不回填；Leaflet 加载失败展示降级提示可关闭弹层地点输入框保持可用（REQ-VMM-UBI-010/UNW-036/UBI-040）

### 1.10 实现按钮挂载与农事日历页只读展示
- [ ] 实现 `init()` 公开函数（对应 C-03，D-VMM-13）：`document.getElementById(MAP_PICKER_BTN_ID)` 挂载 click 事件 → 调用 `open(onConfirm)`，onConfirm 回调中 `document.getElementById(LOCATION_INPUT_ID).value = result.locationText`（textContent 安全渲染）并设置模块级回调承接（主页 IIFE 内通过 markerLatLon 承接，REQ-VMM-EVT-024/025）；按钮节点缺失时静默降级不抛错（REQ-VMM 5.1.3）
- [ ] 实现 `initDisplay(container, lat, lon)` 公开函数（对应 C-06，D-VMM-11）：在 container 内初始化只读地图展示组件，调用 `loadLeaflet()`（复用动态 CDN 加载），成功后 `L.map(container)` + `L.tileLayer` + `map.setView([lat, lon], DEFAULT_ZOOM)` + 放置只读 marker（`draggable:false`）；lat/lon 非法（`!validateLatLon`）回退 `DEFAULT_CENTER`（REQ-VMM-UBI-035）；加载失败降级为隐藏组件或展示「地图暂不可用」提示（REQ-VMM-UNW-034）；与既有时间线节点渲染并行不阻塞
- [ ] 验收：init 后按钮点击可触发弹层；initDisplay 在指定容器展示只读地图与标点，加载失败降级隐藏不阻塞节点渲染（REQ-VMM-UBI-002/030/032/033/UNW-034/035）

---

## 2. 新建 location-picker 组件样式

> 对应 design.md D-VMM-14/15、改动点 C-14~C-21、需求 REQ-VMM-UBI-004/011、EVT-003、UBI-006/017/019/030/031。
> 目标：在 `frontend/components/location-picker/location-picker.css` 定义地图标点按钮、地图选点弹层、农事日历页展示组件样式，复用既有 CSS 变量，响应式三档适配。
> 依赖：与组1并行（类名约定由组1 D-VMM-04 确定）。

### 2.1 创建样式文件与按钮样式
- [ ] 新建文件 `frontend/components/location-picker/location-picker.css`（对应 C-14）：定义 `.map-picker-btn` 按钮样式，复用既有 CSS 变量 `--leaf`（绿色系）/`--card`（背景）/`--radius-sm`（圆角）/`--ink`（文字色）；`display:inline-block` 不遮挡 `#location` input（REQ-VMM-UBI-002/004）
- [ ] 验收：按钮样式与项目内部视觉一致不使用外部默认样式，inline-block 不遮挡 input 与其他字段（REQ-VMM-UBI-004）

### 2.2 定义地图选点弹层样式
- [ ] 定义 `.location-picker-modal`/`.location-picker-overlay`/`.location-picker-dialog` 弹层样式（对应 C-15，D-VMM-14）：`.location-picker-modal` `position:fixed; inset:0; z-index:2000`（高于 sidebar 100 与悬浮球 1000，低于 loading-gate 9999）；`.location-picker-overlay` 全屏遮罩半透明背景；`.location-picker-dialog` 主体居中（移动端近全屏，桌面端定宽定高）
- [ ] 定义 `.location-picker-search`/`.location-picker-results` 搜索框与候选列表样式（对应 C-16）：搜索框位于地图画布上方，候选列表位于搜索框下方可纵向滚动，不遮挡地图画布导致无法点击标点（REQ-VMM-UBI-017/019）
- [ ] 定义 `.location-picker-map` 地图画布容器样式（对应 C-17）：尺寸适配弹层尺寸，不遮挡搜索框与确认/取消按钮（REQ-VMM-UBI-006）
- [ ] 定义 `.location-picker-info` 标点位置信息样式（对应 C-18）：展示经纬度与地名，复用 `--muted`/`--ink-soft` 变量（REQ-VMM-UBI-015）
- [ ] 定义 `.location-picker-confirm`/`.location-picker-cancel` 确认/取消按钮样式（对应 C-19）：复用 `--leaf`/`--card`/`--radius-sm` 变量，确认按钮主色 `--leaf`，取消按钮辅色（REQ-VMM-UBI-010/EVT-024）
- [ ] 验收：弹层展开后地图画布/搜索框/候选列表/位置信息/确认取消按钮均可见不互相遮挡；z-index 层叠合理高于 sidebar 低于 loading-gate（REQ-VMM-EVT-003/UBI-006）

### 2.3 定义农事日历页展示组件样式
- [ ] 定义 `.timeline-map` 农事日历页展示组件样式（对应 C-20）：位于时间线页头部结论卡附近，`z-index:auto` 在文档流内不遮挡既有节点列表/模式切换/图表；尺寸适配页面布局不引入横向滚动（REQ-VMM-UBI-030/031）
- [ ] 验收：展示组件位于头部不遮挡核心内容，移动端与桌面端均无横向滚动（REQ-VMM-UBI-031）

### 2.4 定义响应式三档适配
- [ ] 新增 `@media (max-width:767px)` 移动端适配（对应 C-21，D-VMM-15）：弹层近全屏（宽度占满、高度占视口 80% 以上），地图画布尺寸适配弹层（REQ-VMM-UBI-011）
- [ ] 新增 `@media (min-width:768px)` 桌面端适配：弹层定宽定高居中（如宽 600px 高 480px），展示组件适当尺寸（REQ-VMM-UBI-011）
- [ ] 新增 `@media (min-width:1024px)` 大屏适配：弹层适当增大，展示组件适当增大（REQ-VMM-UBI-011）
- [ ] 验收：375px/768px/1024px 三档宽度下弹层与展示组件均正常展示，移动端近全屏，桌面端定宽定高，均无横向滚动（REQ-VMM-UBI-011）

---

## 3. 修改主页 index.html 集成地图标点按钮

> 对应 design.md D-VMM-10/13、改动点 C-22~C-28、需求 REQ-VMM-UBI-002、EVT-003/024/025/029。
> 目标：在 `frontend/pages/index.html` 新增「地图标点」按钮、引入 location-picker.js/css、新增经纬度回填承接变量 `markerLatLon`、调整 submit 经纬度优先级、调用 init。
> 依赖：组1（location-picker.js）+ 组2（location-picker.css）完成。

### 3.1 引入 location-picker 样式与脚本
- [ ] 在 `index.html` `<head>` 内（第 12 行 `floating-ball.css` 之后）新增 `<link rel="stylesheet" href="../components/location-picker/location-picker.css">`（对应 C-22，REQ-VMM-UBI-001/004）
- [ ] 在 `index.html` 第 135 行 `sidebar.js` 之后新增 `<script src="../components/location-picker/location-picker.js"></script>`（对应 C-24，REQ-VMM-UBI-001）
- [ ] 验收：index.html 含 location-picker.css 与 location-picker.js 引入，不破坏既有脚本加载顺序（REQ-VMM-UBI-001）

### 3.2 新增「地图标点」按钮
- [ ] 在 `index.html` 第 72-76 行 `#field-location` 内 input（第 74 行）之后、error（第 75 行）之前新增 `<button type="button" class="map-picker-btn" id="map-picker-btn">📍 地图标点</button>`（对应 C-23，D-VMM-13）
- [ ] 验收：主页 `#field-location` 内地点输入框旁存在「地图标点」按钮，可点击，不遮挡 input 与作物/日期字段（REQ-VMM-UBI-002/EVT-003）

### 3.3 新增经纬度回填承接变量与优先级
- [ ] 在 `index.html` 内联 IIFE 内（第 148 行 `var CROPS = window.CropMap.CROPS;` 附近）新增模块级变量 `var markerLatLon = null;`（对应 C-25，D-VMM-10）
- [ ] 修改 `index.html` 内联 IIFE 内 submit 处理（第 467-468 行），将 `lat: hit ? hit.lat : 35.07` 改为 `lat: markerLatLon ? markerLatLon.lat : (hit ? hit.lat : 35.07)`，将 `lon: hit ? hit.lng : 116.31` 改为 `lon: markerLatLon ? markerLatLon.lon : (hit ? hit.lng : 116.31)`（对应 C-26，D-VMM-10）
- [ ] 验收：地图标点回填的精确经纬度优先于作物映射默认经纬度参与 buildTimelineQuery；buildTimelineQuery 参数命名 lat/lon/location 不变（REQ-VMM-EVT-025/UBI-029）

### 3.4 调用 LocationPicker.init 并设置 onConfirm 回填
- [ ] 在 `index.html` 内联 IIFE 末尾（第 489 行 `renderHistory();` 之后、第 490 行 `})();` 之前）新增 `if (window.LocationPicker) window.LocationPicker.init();`（对应 C-27，REQ-VMM-UBI-002）
- [ ] 在 init 调用前（或 init 内部 onConfirm 回调中）设置回填逻辑：onConfirm 回调中 `locationInput.value = result.locationText;`（textContent 安全渲染）+ `markerLatLon = { lat: result.lat, lon: result.lon };`（对应 D-VMM-10，REQ-VMM-EVT-024/025）
- [ ] 验收：主页加载后「地图标点」按钮挂载完成可点击；确认标点后地点输入框回填地名、markerLatLon 设置为精确经纬度、弹层关闭（REQ-VMM-UBI-002/EVT-024/025）

### 3.5 确认既有逻辑不触碰
- [ ] 确认 `index.html` 既有表单字段 id/name（crop/sowingDate/location）、`buildTimelineQuery` 参数命名、`validate` 逻辑、既有 77 断言引用的字符串均保留不变（对应 C-28，REQ-VMM-UBI-029/045）
- [ ] 验收：既有表单提交链路、iframe 加载机制、左侧栏 switchModule、拍照/语音/悬浮球/我的产品等已落地功能核心行为不变（REQ-VMM 7.3 不触碰项）

---

## 4. 修改农事日历页 timeline.html 集成地图展示组件

> 对应 design.md D-VMM-11、改动点 C-29~C-33、需求 REQ-VMM-UBI-030/031/032/033/035、UNW-034。
> 目标：在 `frontend/pages/timeline.html` 新增 `#timeline-map` 展示组件容器、引入 location-picker.js/css、调用 initDisplay 展示当前地点只读地图。
> 依赖：组1（location-picker.js）+ 组2（location-picker.css）完成。与组3可并行。

### 4.1 引入 location-picker 样式与脚本
- [ ] 在 `timeline.html` `<head>` 内（第 9 行 `timeline.css` 之后）新增 `<link rel="stylesheet" href="../components/location-picker/location-picker.css">`（对应 C-29，REQ-VMM-UBI-032）
- [ ] 在 `timeline.html` 第 61 行 `chart-client.js` 之后新增 `<script src="../components/location-picker/location-picker.js"></script>`（对应 C-31，REQ-VMM-UBI-032）
- [ ] 验收：timeline.html 含 location-picker.css 与 location-picker.js 引入，不破坏既有脚本加载顺序（REQ-VMM-UBI-032）

### 4.2 新增地图展示组件容器
- [ ] 在 `timeline.html` 第 29 行结论卡 `</section>` 之后、第 31 行 `<div class="loading">` 之前新增 `<section class="timeline-map" id="timeline-map" hidden></section>`（对应 C-30，D-VMM-11）
- [ ] 验收：timeline.html 头部含 `#timeline-map` 展示组件容器，位于结论卡附近不遮挡既有节点列表/模式切换/图表（REQ-VMM-UBI-030/031）

### 4.3 调用 initDisplay 初始化展示组件
- [ ] 在 `timeline.html` 内联 IIFE 内 `parseQuery` 之后（第 115 行 `var query = parseQuery(window.location.search);` 之后）新增 `if (window.LocationPicker) window.LocationPicker.initDisplay(document.getElementById('timeline-map'), query.lat, query.lon);`（对应 C-32，D-VMM-11）
- [ ] 验收：timeline.html 加载后地图展示组件依据 URL 参数 lat/lon 展示只读标点；URL 参数缺失/非法时回退默认中心或不展示标点不报错（REQ-VMM-UBI-030/035）

### 4.4 确认既有逻辑不触碰
- [ ] 确认 `timeline.html` 既有 `parseQuery`/`getData`/`render`/节点渲染/模式切换/图表/节日倒计时逻辑均保留不变（对应 C-33，REQ-VMM 7.3 不触碰项）
- [ ] 验收：既有时间线页节点渲染、模式切换、图表渲染、节日倒计时核心行为不变；地图组件加载与既有渲染并行不阻塞节点首屏（REQ-VMM-UBI-032/UNW-034）

---

## 5. 新增地图标点测试断言

> 对应 design.md D-VMM-18、改动点 C-34~C-35、需求 REQ-VMM-UBI-041/042。
> 目标：在 `frontend/tests/pages.test.js` 新增 location-picker 读取变量与地图标点相关断言，既有 77 项断言原文保留。
> 依赖：组1~组4 全部完成。

### 5.1 新增文件读取变量
- [ ] 在 `pages.test.js` 第 38 行 `cropPhotoJs` 之后新增 `const locationPickerJs = read(path.resolve(__dirname, '..', 'components', 'location-picker', 'location-picker.js'));` 与 `const locationPickerCss = read(path.resolve(__dirname, '..', 'components', 'location-picker', 'location-picker.css'));`（对应 C-34，REQ-VMM-UBI-042）
- [ ] 验收：pages.test.js 含 locationPickerJs 与 locationPickerCss 变量，文件读取不报错（REQ-VMM-UBI-042）

### 5.2 新增 location-picker 组件结构断言
- [ ] 在 `pages.test.js` 末尾（第 682 行后）追加新 test 块，断言 `locationPickerJs` 含 `window.LocationPicker`/`open`/`close`/`initDisplay`/`isReady`/`init` 接口（REQ-VMM-UBI-001/005）
- [ ] 断言 `locationPickerJs` 含具名常量 `LEAFLET_CDN_URL`/`OSM_TILE_TEMPLATE`/`NOMINATIM_ENDPOINT`/`DEFAULT_CENTER`/`DEFAULT_ZOOM`/`LOAD_TIMEOUT_MS`/`SEARCH_TIMEOUT_MS`/`DEGRADATION_TEXT`（REQ-VMM-UBI-005/017）
- [ ] 断言 `locationPickerJs` 含 `https://` + Leaflet CDN 动态 script 加载逻辑（REQ-VMM-UBI-007）
- [ ] 断言 `locationPickerJs` 含 `nominatim` + `fetch` + `AbortController` + `textContent`（REQ-VMM-UBI-023/EVT-018/019）
- [ ] 断言 `locationPickerJs` 含降级文案「地图功能暂不可用」+「搜索服务暂不可用」+「地图底图加载失败」（REQ-VMM-UNW-036/037/038）
- [ ] 断言 `locationPickerJs` 含经纬度范围校验逻辑（`lat` 与 `-90`/`90` 比较，`lon` 与 `-180`/`180` 比较，REQ-VMM-UBI-026）
- [ ] 验收：新增断言以静态字符串/正则匹配为主（与既有风格一致），不执行 location-picker.js 运行时逻辑（D-VMM-18）

### 5.3 新增 location-picker.css 样式断言
- [ ] 断言 `locationPickerCss` 含 `.map-picker-btn`/`.location-picker-modal`/`.location-picker-map`/`.location-picker-search`/`.location-picker-results`/`.location-picker-info`/`.timeline-map` 类名（REQ-VMM-UBI-004/011）
- [ ] 断言 `locationPickerCss` 含 `@media (max-width:767px)`/`@media (min-width:768px)`/`@media (min-width:1024px)` 三档响应式断点（REQ-VMM-UBI-011）
- [ ] 验收：样式断言覆盖按钮/弹层/搜索/画布/信息/展示组件类名与响应式三档（REQ-VMM-UBI-004/011）

### 5.4 新增 index.html 集成断言
- [ ] 断言 `indexHtml` 含 `id="map-picker-btn"` + `地图标点` + `location-picker.js` + `location-picker.css`（REQ-VMM-UBI-002）
- [ ] 断言 `indexHtml` 含 `markerLatLon` 变量 + submit 中优先取 markerLatLon 逻辑（`markerLatLon ? markerLatLon.lat`）（REQ-VMM-EVT-025）
- [ ] 验收：index.html 集成断言覆盖按钮挂载、引入、经纬度回填承接与优先级（REQ-VMM-UBI-002/EVT-025）

### 5.5 新增 timeline.html 集成断言
- [ ] 断言 `timelineHtml` 含 `id="timeline-map"` + `location-picker.js` + `initDisplay`（REQ-VMM-UBI-030/032）
- [ ] 验收：timeline.html 集成断言覆盖展示组件容器、引入、initDisplay 调用（REQ-VMM-UBI-030/032）

### 5.6 确认既有 77 断言兼容
- [ ] 确认 `pages.test.js` 既有 77 项 test() 调用原文保留，新增断言仅末尾追加不删除/注释/弱化既有断言（对应 C-35，REQ-VMM-UBI-041）
- [ ] 验收：运行 `node --test frontend/tests/pages.test.js` 全绿，既有 77 项 + 新增断言均通过（REQ-VMM-UBI-041/042）

---

## 6. 回归验证与部署

> 对应 design.md D-VMM-19、需求 REQ-VMM-UBI-041/043/044/045。
> 目标：验证既有功能不破坏、测试全绿、降级路径覆盖、响应式兼容，确认变更范围仅 frontend/ 不触碰后端/nginx。
> 依赖：组1~组5 全部完成。

### 6.1 运行前端测试套件
- [ ] 运行 `node --test frontend/tests/pages.test.js`，确认既有 77 项 + 新增地图标点断言全绿（REQ-VMM-UBI-041/042）
- [ ] 验收：测试输出无失败，总 test 数 ≥ 77 + 新增数（REQ-VMM-UBI-041）

### 6.2 验证既有功能回归
- [ ] 验证主页表单提交链路不变：文字输入地点 + 提交 → timeline.html 经 iframe 加载，URL 参数含 crop/sowDate/lat/lon/location（REQ-VMM-UBI-029）
- [ ] 验证既有作物映射经纬度来源不变：未使用地图标点时 lat/lon 来自 crop-map.js CROPS（REQ-VMM-UBI-045）
- [ ] 验证既有左侧栏 6 板块入口与 switchModule 机制不变（REQ-VMM 7.3）
- [ ] 验证既有时间线页节点渲染、模式切换、图表、节日倒计时不变（REQ-VMM 7.3）
- [ ] 验证既有拍照识别/语音组件/悬浮球对话/我的产品页面/天气视觉/时段动画不变（REQ-VMM 7.3）
- [ ] 验收：所有既有功能核心行为与改动前一致（REQ-VMM 7.3 不触碰项）

### 6.3 验证降级路径
- [ ] 验证 Leaflet CDN 不可达时「地图标点」按钮禁用/隐藏 + 提示「地图功能暂不可用，请文字输入地点」+ 地点输入框可用 + 表单可提交（lat/lon 来自作物映射）（REQ-VMM-UNW-036/UBI-040）
- [ ] 验证 OSM 瓦片加载失败时画布展示「地图底图加载失败」提示，标点交互仍可尝试不白屏（REQ-VMM-UNW-037/4.2.3）
- [ ] 验证 Nominatim 搜索/反向编码不可用时展示「搜索服务暂不可用」/仅展示经纬度，用户仍可点击/拖拽标点（REQ-VMM-UNW-022/OPT-016）
- [ ] 验证浏览器地理定位不可用/拒绝授权时地图回退默认中心 35.07/116.31 不报错不反复请求授权（REQ-VMM-UNW-039）
- [ ] 验收：所有降级路径不白屏、不抛未捕获 JS 错误、不阻塞表单提交与时间线渲染（REQ-VMM 4.2.1/UBI-040）

### 6.4 验证响应式与安全性
- [ ] 验证 375px/768px/1024px 三档宽度下地图弹层与展示组件均正常展示，移动端弹层近全屏，桌面端定宽定高，均无横向滚动（REQ-VMM-UBI-011）
- [ ] 验证所有用户可控文本（搜索候选地名/标点位置地名/回填地点文本）均用 textContent 渲染无 innerHTML 拼接（REQ-VMM 4.3.1/UBI-015/027）
- [ ] 验证 Leaflet CDN/OSM 瓦片/Nominatim 请求均使用 HTTPS 不混合 HTTP/HTTPS（REQ-VMM 4.3.3）
- [ ] 验证前端无硬编码 API Key/令牌/凭据（REQ-VMM 4.3.5）
- [ ] 验收：响应式三档兼容，安全性策略全部满足（REQ-VMM-UBI-011/4.3.1/4.3.3/4.3.5）

### 6.5 确认变更范围与不触碰项
- [ ] 确认变更文件仅 5 个：新增 `frontend/components/location-picker/location-picker.js` + `location-picker.css`，修改 `frontend/pages/index.html` + `frontend/pages/timeline.html` + `frontend/tests/pages.test.js`（D-VMM-19）
- [ ] 确认不触碰：`crop-map.js`/`sidebar.js`/`sidebar.css`/`weather-visual.js`/`time-period.js`/`my-crops.js`/`my-crops.css`/`my-crops-store.js`/`crop-photo.js`/`photo-capture.js`/`history-store.js`/`voice-input.*`/`floating-ball.*`/`timeline-logic.js`/`chart-client.js`/`timeline.css`/`common.css`/`mock-data.js`（REQ-VMM 7.3）
- [ ] 确认不触碰后端 Java/nginx/shared/data/deploy/docs（REQ-VMM-UBI-043）
- [ ] 验收：`git diff --name-only` 输出仅含上述 5 个 frontend/ 下文件（REQ-VMM-UBI-043/044）

### 6.6 代码审查与设计回顾
- [ ] 审查 location-picker.js 模块化：IIFE + window 暴露 + module.exports，与既有组件风格一致，不与既有页面逻辑耦合（REQ-VMM 4.4.1）
- [ ] 审查具名常量集中定义：所有可配置项集中于模块顶部，无多处硬编码（REQ-VMM-UBI-005/4.4.2）
- [ ] 审查 z-index 层叠：弹层 2000 高于 sidebar 100 与悬浮球 1000，低于 loading-gate 9999；展示组件 z-index:auto（D-VMM-14）
- [ ] 审查设计一致性：实现与 design.md D-VMM-01~19 关键决策一致，45 条 EARS 需求全覆盖（REQ-VMM 2.7 需求覆盖追溯）
- [ ] 验收：代码审查无阻断意见，设计与实现一致性核对通过（REQ-VMM 4.4.1/4.4.2/4.4.5）

---

## 任务依赖关系图

```
组1（location-picker.js）──┐
                          ├──→ 组3（index.html 集成）──┐
组2（location-picker.css）─┘                          │
                          ├──→ 组4（timeline.html 集成）─┤
                          │                              ├──→ 组5（测试断言）──→ 组6（回归验证与部署）
                          └──────────────────────────────┘
```

- **组1 ∥ 组2**：组件 JS 与 CSS 可并行开发（类名约定由组1 D-VMM-04 确定，组2 据此定义样式）
- **组3 ∥ 组4**：index.html 与 timeline.html 集成可并行（均依赖组1+组2 完成）
- **组5**：测试断言依赖组1~组4 全部完成
- **组6**：回归验证依赖组5 完成

## 关键风险点提示

| 风险编号 | 风险点 | 应对策略 | 关联任务 |
|---------|--------|---------|---------|
| R1 | Leaflet CDN 不可达 | 动态加载超时 5000ms 降级，按钮禁用+提示，表单回退作物映射默认经纬度 | 组1.3/组6.3 |
| R2 | OSM 瓦片加载失败 | 画布展示「地图底图加载失败」提示，标点仍可尝试不白屏 | 组1.4/组6.3 |
| R3 | Nominatim 搜索/反向编码不可用 | 超时 5000ms 降级提示，用户仍可点击/拖拽标点 | 组1.6/组1.7/组6.3 |
| R4 | 浏览器地理定位不可用/拒绝 | 超时 3000ms 回退默认中心不报错不反复请求授权 | 组1.4/组6.3 |
| R5 | 回填后用户手动修改地点文本 | 地点文本以用户修改为准，经纬度保持回填值可重新标点覆盖 | 组3.3 |
| R6 | 弹层多次打开/关闭状态不一致 | close() 移除 DOM + 重置 currentMarker，未确认标点不回填 | 组1.9 |
| R7 | 标点经纬度超出合法范围 | validateLatLon 校验，非法不回填提示异常 | 组1.8 |
| R8 | 搜索候选列表过长 | Nominatim limit=10，列表可纵向滚动 | 组1.7 |
| R9 | 搜索关键词为空 | 空关键词不发起请求隐藏候选列表 | 组1.7 |
| R10 | 移动端弹层遮挡底部操作区 | 移动端弹层近全屏高度占视口 80% 以上，invalidateSize 确保正确渲染 | 组2.4 |
| R11 | timeline.html 地图组件阻塞节点首屏 | initDisplay 与 getData().then(render) 并行不阻塞 | 组4.3 |
| R12 | timeline.html URL 参数 lat/lon 缺失/非法 | parseQuery 已有范围校验回退默认，initDisplay 内部再次校验 | 组4.3 |
| R13 | XSS（未转义拼入 innerHTML） | 所有用户可控文本用 textContent 渲染 | 组1.5/组1.7/组3.4/组6.4 |
| R14 | 混合 HTTP/HTTPS 内容 | Leaflet CDN/OSM 瓦片/Nominatim 均使用 HTTPS | 组1.3/组6.4 |
| R15 | 前端硬编码 API Key | Leaflet 与 OSM/Nominatim 均免费无 Key | 组1.2/组6.4 |
| R16 | 既有 77 项测试断言破坏 | 新增按钮保留既有 id，新增引入不删除既有引入，断言仅末尾追加 | 组3.5/组5.6 |
| R17 | buildTimelineQuery 参数命名改变 | 逻辑不变参数名 lat/lon/location 不变，markerLatLon 仅影响值来源 | 组3.3 |
| R18 | Leaflet 重复加载 | loadLeaflet() 缓存就绪态复用不重复注入 | 组1.3 |
| R19 | 弹层 z-index 与既有层叠冲突 | 弹层 2000 高于 sidebar 100/悬浮球 1000 低于 loading-gate 9999 | 组2.2/组6.6 |