# T08 语音交互与方言适配 + T10 安全机制与服务器升级 · 编码任务规划（t08_t10_voice_security）

> 本任务规划基于 `t08_t10_voice_security/spec.md`（REQ-TVS-UBI/EVT/STA/OPT/UNW-001~058，共 58 条 EARS 需求）与 `design.md`（22 条关键决策 D-TVS-01~22、30 个改动点 C-01~C-30 + S-01~S-22、19 个风险项、10 个边界情况）生成。
>
> **红线约束**：
> 1. 前端 52/52 既有测试全绿（`node --test frontend/tests/pages.test.js`），仅允许新增断言，禁止删除/注释/弱化既有断言；
> 2. 不引入框架/构建工具/第三方库/第三方语音 SDK，仅使用浏览器原生 Web Speech API；
> 3. T08 仅修改/新增 `frontend/` 目录下文件，不修改后端/nginx/shared/data/deploy/docs；
> 4. T10 仅修改服务器 nginx 配置（`/etc/nginx/`）与防火墙规则，不修改前端代码、不修改后端 Java 代码；
> 5. 不改变 index.html 既有表单字段（crop/sowingDate/location）id/name/类型与提交跳转逻辑（`buildTimelineQuery`/`validate()`）；
> 6. 语音音频流不在前端持久化、不上传第三方、不写 localStorage；识别结果填充用 `value`/`textContent` 安全方式，禁 `innerHTML`。
>
> **任务组依赖顺序**：组1（语音组件核心）→ 组2（index.html 集成）→ 组3（测试断言）；组4（nginx 限流/安全头/日志）→ 组6（防爬策略，依赖组4 同改 nginx）；组5（ufw 端口加固/logrotate，与组4/6 可并行但建议在 nginx 配置生效后执行）；组7（回归验证与部署，依赖全部前序组）。

---

## 1. T08 新建语音组件核心（voice-input.js + voice-input.css）

> 对应 design.md 改动点 C-09~C-28、关键决策 D-TVS-01~D-TVS-09/D-TVS-17~D-TVS-22。
> 目标：在 `frontend/components/voice-input/` 目录下新建语音组件主模块与样式文件，实现能力探测、方言匹配、识别控制、结果填充、状态反馈、降级等全部核心逻辑，不依赖 index.html 改动即可独立存在。

### 1.1 创建语音组件目录与 voice-input.js 模块骨架
- [ ] 新建目录 `frontend/components/voice-input/`，创建 `voice-input.js` 文件
- [ ] 在 `voice-input.js` 中以 IIFE 包裹全部代码，末尾通过 `window.VoiceInput = { init, detectSupport, matchDialect, startRecognition, stopRecognition, fillResult, parseDateText, degrade }` 暴露 API（与既有 `weather-visual.js` 的 `window.WeatherVisual` 风格一致）
- [ ] 验收条件：文件存在；代码以 `(function () { ... })();` 包裹；暴露 `window.VoiceInput` 对象含上述 9 个方法；无 `import`/`require`/CDN 引用（REQ-TVS-UBI-005/058）

### 1.2 定义具名常量（DIALECT_MAP / DIALECT_DEFAULT / RECOGNITION_TIMEOUT_MS / STATUS_TEXT）
- [ ] 在 `voice-input.js` 模块顶部定义 `DIALECT_MAP` 具名常量：地点关键词→方言映射数组，覆盖 ≥5 种方言（普通话 mandarin/zh-CN、西南官话 southwest_mandarin/zh-CN、粤语 cantonese/zh-HK、吴语 wu/zh-CN、闽南语 minnan/zh-TW），每项含 `{ keywords: string[], dialectId: string, langCode: string, label: string }`
- [ ] 定义 `DIALECT_DEFAULT` 常量：`{ dialectId: 'mandarin', langCode: 'zh-CN', label: '普通话' }`，用于未匹配时回退
- [ ] 定义 `RECOGNITION_TIMEOUT_MS = 10000` 常量（录音超时 10 秒）
- [ ] 定义 `STATUS_TEXT` 常量对象：含 `recording: '正在聆听…'`、`success: '识别完成，可编辑确认'`、`error: '识别失败，请重试或手动输入'`、`empty: '未识别到内容，请重试或手动输入'`、`permission: '请允许麦克风权限后使用语音输入'`、`timeout: '录音超时，请重试'`、`dateError: '未能识别日期，请手动选择或重试'`、`unsupported: '语音输入不可用'` 等状态文案
- [ ] 验收条件：4 个具名常量集中定义于模块顶部；`DIALECT_MAP` 覆盖 ≥5 种方言且每项含 keywords/dialectId/langCode/label；方言标识均为预定义枚举值（防注入，REQ-TVS 4.3.3）；常量集中定义满足可维护性（REQ-TVS 4.4.1/4.4.2）

### 1.3 实现 detectSupport() 浏览器能力探测函数
- [ ] 在 `voice-input.js` 中实现 `detectSupport()` 函数：检查 `window.SpeechRecognition` 或 `window.webkitSpeechRecognition` 是否存在，返回 `{ supported: boolean, Ctor: Function|null }`
- [ ] Chrome/Edge 优先取 `webkitSpeechRecognition`，Safari 取 `SpeechRecognition`，Firefox 无此 API 时返回 `{ supported: false, Ctor: null }`
- [ ] 验收条件：函数存在且返回含 `supported` 与 `Ctor` 字段的对象；不抛异常（REQ-TVS-EVT-003/UNW-004）

### 1.4 实现 matchDialect() 方言匹配函数
- [ ] 在 `voice-input.js` 中实现 `matchDialect(locationText)` 函数：接收地点文本字符串，遍历 `DIALECT_MAP` 用 `locationText.includes(keyword)` 匹配，返回首个匹配项的 `{ dialectId, langCode, label }`
- [ ] 未匹配或输入为空/非字符串时回退返回 `DIALECT_DEFAULT`（普通话/zh-CN）
- [ ] 匹配过程为纯本地计算，不发起任何网络请求
- [ ] 验收条件：`matchDialect("成都")` 返回西南官话；`matchDialect("")` / `matchDialect("未知地点")` 返回普通话默认；无网络请求（REQ-TVS-UBI-014/EVT-015/UBI-016/4.1.3/4.3.3）

### 1.5 实现 parseDateText() 日期文本解析函数
- [ ] 在 `voice-input.js` 中实现 `parseDateText(text)` 函数：用正则匹配中文日期格式（如"2026年3月15日"/"2026-3-15"），解析为 `{ year: number, month: number, day: number }`
- [ ] 仅匹配到月日无年份时（如"3月15日"）用当前年份补全；完全无法匹配返回 `null`
- [ ] 验收条件：`parseDateText("2026年3月15日")` 返回 `{year:2026,month:3,day:15}`；`parseDateText("明天")` 返回 `null`；不抛异常（REQ-TVS-EVT-009/边界 B-TVS-05）

### 1.6 实现 startRecognition() 与 stopRecognition() 识别控制函数
- [ ] 在 `voice-input.js` 中实现 `startRecognition(targetField)` 函数：读取 `#location` 当前值调用 `matchDialect` 匹配方言；用 `detectSupport().Ctor` 创建 SpeechRecognition 实例；设置 `recognition.lang = dialect.langCode`、`recognition.continuous = false`、`recognition.interimResults = false`；绑定 `onresult`/`onerror`/`onend` 事件；调用 `recognition.start()`；≤500ms 内调用 `setButtonState(btn, 'recording', STATUS_TEXT.recording)` 展示录音中反馈；启动 `RECOGNITION_TIMEOUT_MS` 超时计时器
- [ ] `onresult` 回调：取 `event.results[0][0].transcript`，`transcript.trim()` 非空则调用 `fillResult(targetField, transcript)` + `setButtonState(btn, 'success', STATUS_TEXT.success)`，空则 `setButtonState(btn, 'error', STATUS_TEXT.empty)`
- [ ] `onerror` 回调：根据 `error` 类型（`not-allowed`/`service-not-allowed` 为权限拒绝）设置对应提示文案，调用 `setButtonState(btn, 'error', ...)`，输入框内容不变
- [ ] 在 `voice-input.js` 中实现 `stopRecognition()` 函数：调用 `recognition.abort()` 停止录音，清除超时计时器，恢复按钮为 idle 状态
- [ ] 仅允许一个活跃 recognition 实例：启动新录音前先 `stopRecognition()` 停止当前录音（边界 B-TVS-01）
- [ ] 录音中再次点击同一语音按钮时调用 `stopRecognition()` 取消录音，不产生识别结果填充（REQ-TVS-OPT-013）
- [ ] 验收条件：函数存在；`startRecognition` 设置 lang 并调用 start()；`stopRecognition` 调用 abort()；超时 10s 自动停止；权限拒绝展示可读提示（REQ-TVS-EVT-006/008/STA-007/OPT-013/UNW-012/4.2.2/4.2.4）

### 1.7 实现 fillResult() 识别结果填充函数
- [ ] 在 `voice-input.js` 中实现 `fillResult(targetField, transcript)` 函数：
  - `crop` → `document.getElementById('crop').value = transcript`
  - `location` → `document.getElementById('location').value = transcript`
  - `sowingDate` → 调用 `parseDateText(transcript)` 解析；匹配成功则分别设置 `#date-year`/`#date-month`/`#date-day` 的 value 并触发 `change` 事件（使既有 `fillMonths()`/`fillDays()`/`composeDate()` 联动），同步设置 `#sowingDate.value`；匹配失败则展示 `STATUS_TEXT.dateError` 提示，不修改日期选择器
- [ ] 填充后调用 `input.focus()` 聚焦目标输入框，并添加 `.voice-filled` 高亮 class 引导用户确认/编辑
- [ ] 识别结果仅用 `input.value = transcript` 赋值（安全方式），**禁止** `innerHTML` 拼接（防 XSS）
- [ ] 验收条件：crop/location 用 value 赋值；sowingDate 解析年月日分别设置三 select + 同步 `#sowingDate`；填充后聚焦 + 高亮；无 innerHTML（REQ-TVS-EVT-008/009/EVT-020/UBI-019/4.3.2）

### 1.8 实现 setButtonState() 状态反馈函数
- [ ] 在 `voice-input.js` 中实现 `setButtonState(btn, state, message)` 函数：更新语音按钮 className（添加/移除 `recording`/`success`/`error` 类）+ 设置状态文案元素的 `textContent`（安全方式）
- [ ] state 取值：`idle`/`recording`/`success`/`error`；message 为可选状态文案字符串
- [ ] 验收条件：函数存在；更新按钮 className 与状态文案 textContent；不使用 innerHTML（REQ-TVS-EVT-006/UBI-010/UNW-012）

### 1.9 实现 degrade() 降级处理函数
- [ ] 在 `voice-input.js` 中实现 `degrade(reason)` 函数：按 reason 处理降级
  - `unsupported` → 三语音按钮设 `hidden` 属性（完全隐藏），不报错
  - `permission-denied` → 展示 `STATUS_TEXT.permission` 提示，按钮恢复 idle
  - `timeout` → 展示 `STATUS_TEXT.timeout` 提示，按钮恢复 idle
  - `error` → 展示 `STATUS_TEXT.error` 提示，按钮恢复 idle
- [ ] 内部 try-catch 包裹，不向外抛出未捕获错误
- [ ] 验收条件：函数存在；unsupported 时三按钮 hidden；其余 reason 展示对应提示且按钮恢复可点击；不抛异常（REQ-TVS-UNW-004/4.2.1~4.2.4）

### 1.10 实现 init() 入口编排函数
- [ ] 在 `voice-input.js` 中实现 `init(options)` 函数：接收 `{ cropInput, sowingInput, locationInput, yearSel, monthSel, daySel, showToast }` 参数
- [ ] 编排流程：调用 `detectSupport()` → 支持则渲染三语音按钮可见（移除 hidden）+ 为每个按钮绑定 click 事件（点击时调用 `startRecognition(targetField)`，录音中再次点击调用 `stopRecognition()`）→ 不支持则调用 `degrade('unsupported')` 隐藏三按钮
- [ ] 内部 try-catch 捕获所有异常，转 `degrade(reason)`，不向外抛出
- [ ] init 异步执行不阻塞表单（不 await 阻塞操作）
- [ ] 验收条件：函数存在；支持时三按钮可见可点击；不支持时三按钮 hidden 不报错；不阻塞表单（REQ-TVS-EVT-003/UBI-005/4.1.6）

### 1.11 创建 voice-input.css 语音按钮样式文件
- [ ] 新建 `frontend/components/voice-input/voice-input.css` 文件
- [ ] 定义 `.voice-btn` 基础样式：绝对定位到 input 右侧内部（约 36×36px 方形按钮），复用 `--leaf`/`--leaf-soft`/`--radius-sm`/`--shadow-soft` CSS 变量，含麦克风 SVG 用 `currentColor`
- [ ] 定义 `.voice-btn.recording` 录音中高亮脉动样式（`--red` 或 `--leaf-2` 边框/背景 + pulse 动画）
- [ ] 定义 `.voice-btn.success` 识别完成短暂高亮样式（`--leaf-2`）
- [ ] 定义 `.voice-btn.error` 错误状态样式（`--red-soft` 背景）
- [ ] 定义 `.voice-btn[hidden]` 降级隐藏样式（`display: none`）
- [ ] 定义 `.voice-status` 状态文案样式（`--muted` 色、小字号、`role="status"` 配合）
- [ ] 定义 `.voice-filled` 识别填充高亮样式（边框闪烁/背景色变化引导确认）
- [ ] 定义 `.field input.has-voice` 样式：调整 `padding-right` 留出语音按钮空间，防止按钮遮挡输入框
- [ ] 定义 `@media (min-width: 768px)` 语音按钮响应式规则（适当间距）；375px 紧凑布局不遮挡 input
- [ ] 验收条件：文件存在；样式复用既有 CSS 变量；按钮约 36×36px 不遮挡 input；含 recording/success/error/hidden/voice-status/voice-filled/has-voice/响应式规则（REQ-TVS-UBI-002/EVT-006/UBI-010/UNW-012/EVT-020/4.5.8/UBI-001 禁止项）

---

## 2. T08 修改 index.html（插入语音按钮 DOM + 引入脚本/样式 + init 调用）

> 对应 design.md 改动点 C-01~C-08。依赖组1 完成。
> 目标：在 `frontend/pages/index.html` 中插入三个语音按钮 DOM、引入 voice-input.css/voice-input.js、在内联 IIFE 末尾追加 init 调用，不改变既有表单字段与提交逻辑。

### 2.1 引入 voice-input.css 样式文件
- [ ] 在 `frontend/pages/index.html` 第 8 行 `<link rel="stylesheet" href="../assets/css/common.css" />` 之后新增 `<link rel="stylesheet" href="../components/voice-input/voice-input.css" />`
- [ ] 验收条件：index.html 含 voice-input.css link 引用，位于 common.css 之后（确保组件样式可覆盖全局，D-TVS-18）

### 2.2 引入 voice-input.js 脚本文件
- [ ] 在 `frontend/pages/index.html` 第 92 行 `<script src="../assets/js/weather-visual.js"></script>` 之后新增 `<script src="../components/voice-input/voice-input.js"></script>`
- [ ] 验收条件：index.html 含 voice-input.js script 引用，位于 weather-visual.js 之后、内联 IIFE 之前（D-TVS-19）

### 2.3 在 #crop 输入框后插入语音按钮 DOM
- [ ] 在 `frontend/pages/index.html` 第 38 行 `<input id="crop" name="crop" type="text" autocomplete="off" />` 之后插入 `<button type="button" class="voice-btn" data-target="crop" aria-label="语音输入作物品种" hidden><svg ...麦克风图标...></svg><span class="voice-status" role="status"></span></button>`
- [ ] 按钮 `type="button"` 防止触发表单提交；初始 `hidden` 属性由 init 根据能力探测结果决定是否移除
- [ ] 验收条件：index.html `#field-crop` 内含 `data-target="crop"` 语音按钮，含 aria-label 与麦克风 SVG + voice-status span（REQ-TVS-UBI-001/002）

### 2.4 在 date-picker 后插入 sowingDate 语音按钮 DOM
- [ ] 在 `frontend/pages/index.html` 第 50 行 `</div>`（date-picker 闭合）之后插入 `<button type="button" class="voice-btn" data-target="sowingDate" aria-label="语音输入播种日期" hidden><svg ...></svg><span class="voice-status" role="status"></span></button>`
- [ ] 验收条件：index.html `#field-sowing-date` 内含 `data-target="sowingDate"` 语音按钮（REQ-TVS-UBI-001/002）

### 2.5 在 #location 输入框后插入语音按钮 DOM
- [ ] 在 `frontend/pages/index.html` 第 57 行 `<input id="location" name="location" type="text" autocomplete="off" placeholder="" />` 之后插入 `<button type="button" class="voice-btn" data-target="location" aria-label="语音输入所在地" hidden><svg ...></svg><span class="voice-status" role="status"></span></button>`
- [ ] 验收条件：index.html `#field-location` 内含 `data-target="location"` 语音按钮（REQ-TVS-UBI-001/002）

### 2.6 在内联 IIFE 末尾追加 VoiceInput.init() 调用
- [ ] 在 `frontend/pages/index.html` 第 423 行 `renderHistory();` 之后、第 424 行 `})();` 之前插入 `if (window.VoiceInput) { VoiceInput.init({ cropInput: cropInput, sowingInput: sowingInput, locationInput: locationInput, yearSel: yearSel, monthSel: monthSel, daySel: daySel, showToast: showToast }); }`
- [ ] 用 `if (window.VoiceInput)` 守卫防止脚本加载失败时报错；复用既有 IIFE 内已声明的 `cropInput`/`sowingInput`/`locationInput`/`yearSel`/`monthSel`/`daySel`/`showToast` 变量
- [ ] 验收条件：index.html 内联 IIFE 末尾含 `VoiceInput.init(...)` 调用；传入 7 个参数；不改变既有 `buildTimelineQuery`/`validate()`/submit 跳转逻辑（REQ-TVS-EVT-003/4.1.6/4.5.7）

---

## 3. T08 新增测试断言（pages.test.js）

> 对应 design.md 改动点 C-29~C-30、关键决策 D-TVS-17。依赖组1、组2 完成。
> 目标：在 `frontend/tests/pages.test.js` 末尾追加语音组件相关断言，不破坏既有 52 项断言。

### 3.1 新增 voiceInputJs / voiceInputCss 文件读取变量
- [ ] 在 `frontend/tests/pages.test.js` 第 25 行 `const defaultSvg = read(...)` 之后新增 `const voiceInputJs = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.js'));` 与 `const voiceInputCss = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.css'));`
- [ ] 验收条件：测试文件含 voiceInputJs 与 voiceInputCss 变量定义，路径指向 `frontend/components/voice-input/`（REQ-TVS-UBI-056）

### 3.2 新增语音按钮 DOM 存在性与绑定断言
- [ ] 在 `frontend/tests/pages.test.js` 文件末尾（第 467 行之后）新增 test 块：`test('T08 语音按钮：三输入框旁各存在 data-target 绑定', () => { ... })`
- [ ] 断言 indexHtml 含 3 个 `data-target` 分别为 `crop`/`sowingDate`/`location` 的语音按钮；含 `class="voice-btn"`；含 `aria-label`
- [ ] 断言 indexHtml 含 `voice-input.css` link 与 `voice-input.js` script 引用
- [ ] 断言 indexHtml 含 `VoiceInput.init` 调用
- [ ] 验收条件：新增 test 块通过；断言以字符串包含/正则匹配为主（与既有 52 项风格一致，D-TVS-17）

### 3.3 新增方言映射表与能力探测断言
- [ ] 新增 test 块：`test('T08 方言映射表：具名常量与覆盖范围', () => { ... })`
- [ ] 断言 voiceInputJs 含 `DIALECT_MAP` 具名常量；含 ≥5 种方言标识（mandarin/southwest_mandarin/cantonese/wu/minnan）；含 `DIALECT_DEFAULT`；含 `matchDialect` 函数
- [ ] 新增 test 块：`test('T08 Web Speech API 能力探测与降级', () => { ... })`
- [ ] 断言 voiceInputJs 含 `SpeechRecognition` 与 `webkitSpeechRecognition` 探测；含 `detectSupport` 函数；含 `degrade` 函数；含 `unsupported`/`hidden` 降级分支
- [ ] 验收条件：新增 test 块通过（REQ-TVS-UBI-014/EVT-003/UNW-004）

### 3.4 新增安全填充、日期解析与状态文案断言
- [ ] 新增 test 块：`test('T08 识别结果安全填充与日期解析', () => { ... })`
- [ ] 断言 voiceInputJs 含 `.value =` 赋值（安全填充）；**不含** `innerHTML` 拼接识别结果（防 XSS）；含 `parseDateText` 函数；含日期正则匹配（年/月/日）
- [ ] 新增 test 块：`test('T08 状态反馈文案与超时常量', () => { ... })`
- [ ] 断言 voiceInputJs 含 `STATUS_TEXT` 常量；含"正在聆听"/"识别完成"等状态文案；含 `RECOGNITION_TIMEOUT_MS` 超时常量；含 `10000` 值
- [ ] 验收条件：新增 test 块通过（REQ-TVS-EVT-006/008/009/UBI-010/UNW-012/4.3.2）

### 3.5 新增纯原生 JS 无第三方依赖断言
- [ ] 新增 test 块：`test('T08 纯原生 JS 无第三方依赖', () => { ... })`
- [ ] 断言 voiceInputJs 不含 `import`/`require` 第三方库语句；不含外部 CDN URL（如 `http`/`https` 引用第三方）；不含百度/讯飞/阿里语音 SDK 关键词
- [ ] 断言 voiceInputCss 不含 `@import` 外部 URL
- [ ] 验收条件：新增 test 块通过（REQ-TVS-UBI-005/058）

### 3.6 新增语音按钮 CSS 样式断言
- [ ] 新增 test 块：`test('T08 语音按钮 CSS 样式与状态', () => { ... })`
- [ ] 断言 voiceInputCss 含 `.voice-btn` 基础样式；含 `.recording`/`.success`/`.error` 状态样式；含 `.voice-status` 文案样式；含 `.voice-filled` 高亮样式；含 `@media` 响应式规则；复用 CSS 变量（`var(--leaf)` 等）
- [ ] 验收条件：新增 test 块通过（REQ-TVS-UBI-002/EVT-006/UBI-010/UNW-012/EVT-020/4.5.8）

### 3.7 运行测试套件验证既有 52 项 + 新增断言全绿
- [ ] 执行 `node --test frontend/tests/pages.test.js`，确认既有 52 项断言全绿且新增断言全绿，无失败
- [ ] 验收条件：测试输出全绿；既有 52 项断言原文保留未被删除/注释/弱化（REQ-TVS-UBI-056/057）

---

## 4. T10 nginx 限流 + 安全头 + 访问日志配置（服务器操作）

> 对应 design.md 改动点 S-01~S-03/S-05~S-11/S-13~S-15、关键决策 D-TVS-10~D-TVS-14。在服务器 `115.175.44.27` 上操作。
> 目标：在 nginx 层配置 IP 限流（30 次/分钟/IP）、429 友好响应、安全头、自定义访问日志格式、server_tokens 隐藏、logrotate 保留 30 天。

### 4.1 修改 nginx.conf 启用 server_tokens off
- [ ] SSH 登录服务器，编辑 `/etc/nginx/nginx.conf`，将 http 块内 `# server_tokens off;` 取消注释为 `server_tokens off;`
- [ ] 验收条件：`nginx.conf` 含 `server_tokens off;`（未注释）；响应头 `Server` 不含版本号（REQ-TVS-UBI-049）

### 4.2 新增 log_format agritime_main 自定义日志格式
- [ ] 在 `/etc/nginx/nginx.conf` http 块 `access_log` 行之前新增 `log_format agritime_main '$remote_addr - [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent"';`（含注释说明各字段含义）
- [ ] 日志格式须含：客户端 IP、时间戳、请求方法+路径+协议、状态码、响应体大小、Referer、User-Agent；**不含** `$request_body`（防敏感信息泄露）
- [ ] 验收条件：`nginx.conf` 含 `log_format agritime_main` 定义；含全部 7 类字段；不含 `$request_body`（REQ-TVS-UBI-038/UNW-042/4.3.7）

### 4.3 新增 limit_req_zone IP 限流定义
- [ ] 在 `/etc/nginx/nginx.conf` http 块 `log_format` 之后新增 `limit_req_zone $binary_remote_addr zone=api_limit:10m rate=30r/m;`（含注释 `# 限流：30次/分钟/IP，共享内存zone 10m`）
- [ ] 使用 `$binary_remote_addr`（二进制 IP，4 字节，比文本 IP 省内存）作为 key；zone 10m 可记录约 16 万 IP
- [ ] 验收条件：`nginx.conf` 含 `limit_req_zone` 定义；key 为 `$binary_remote_addr`；rate 为 `30r/m`；zone 为 `api_limit:10m`（REQ-TVS-UBI-023/027/029）

### 4.4 修改 sites-enabled/agritime 新增 client_max_body_size 与安全头
- [ ] 编辑 `/etc/nginx/sites-enabled/agritime`，在 server 块 `listen 80;` 之后新增 `client_max_body_size 10m;`（含注释 `# 请求体大小限制 10m`）
- [ ] 在 server 块内 `location` 之前新增安全头：`add_header X-Content-Type-Options "nosniff" always;` + `add_header X-Frame-Options "SAMEORIGIN" always;` + `add_header X-XSS-Protection "1; mode=block" always;`
- [ ] 安全头集中在一处定义；`SAMEORIGIN` 而非 `DENY`（避免合法嵌入被拒）；`always` 确保错误响应也附加
- [ ] 验收条件：配置含 `client_max_body_size 10m`；含 3 个安全头 add_header；值正确（nosniff/SAMEORIGIN/1; mode=block）（REQ-TVS-UBI-043/048/050）

### 4.5 新增 access_log 引用自定义格式与 429 响应 location
- [ ] 在 `/etc/nginx/sites-enabled/agritime` server 块内（安全头之后）新增 `access_log /var/log/nginx/agritime.access.log agritime_main;`
- [ ] 在 server 块内新增 `error_page 429 = @rate_limited;`
- [ ] 在 server 块末尾（所有 location 之后）新增 `location @rate_limited { default_type application/json; return 429 '{"code":429,"message":"请求过于频繁，请稍后再试"}'; }`
- [ ] 429 响应体为 JSON 格式 `{code, message}`，message 为中文友好提示
- [ ] 验收条件：配置含 `access_log ... agritime_main`；含 `error_page 429 = @rate_limited`；含 `@rate_limited` location 返回 JSON 429（REQ-TVS-UBI-026/038）

### 4.6 在 /api/ 与 /health 应用 limit_req 限流
- [ ] 在 `/etc/nginx/sites-enabled/agritime` 的 `location /api/` 块内新增 `limit_req zone=api_limit burst=5 nodelay;`（含注释 `# 限流：30次/分钟/IP，突发桶5`）
- [ ] 在 `location /health` 块内新增 `limit_req zone=api_limit burst=5 nodelay;`
- [ ] burst=5 允许短时小幅超限平滑处理（≤10）；nodelay 使 burst 内请求立即放行不排队；静态资源 location 不限流
- [ ] 验收条件：`/api/` 与 `/health` location 含 `limit_req zone=api_limit burst=5 nodelay`；静态资源不限流（REQ-TVS-UBI-024/EVT-025/OPT-028）

### 4.7 nginx 配置语法测试与热加载
- [ ] 执行 `nginx -t` 测试配置语法，确认输出 `syntax is ok` 与 `test is successful`
- [ ] 测试通过后执行 `nginx -s reload` 热加载配置；测试失败则**不 reload**，保持上一可用配置运行，排查语法错误
- [ ] 验收条件：`nginx -t` 通过；reload 后 nginx 正常运行；主页与接口可正常访问（REQ-TVS 4.2.5/5.5.3 异常场景 1，风险 R-TVS-12）

### 4.8 修改 logrotate.d/nginx 保留天数
- [ ] 编辑 `/etc/logrotate.d/nginx`，将 `rotate 14` 改为 `rotate 30`（保留 30 天），其余配置（daily/compress/delaycompress/create 0640 www-data adm）不变
- [ ] 执行 `logrotate -d /etc/logrotate.d/nginx` 调试验证配置语法正确
- [ ] 验收条件：`logrotate.d/nginx` 含 `rotate 30`；其余配置不变；调试模式无错误（REQ-TVS-UBI-039/041，风险 R-TVS-19）

---

## 5. T10 ufw 端口加固（服务器操作）

> 对应 design.md 改动点 S-17~S-22、关键决策 D-TVS-12。在服务器 `115.175.44.27` 上操作。
> 目标：启用 ufw 防火墙，仅开放 80（HTTP）/22（SSH），拒绝 8080 外部直连，保留当前 SSH 会话不断开。
> **⚠️ 高风险操作**：必须严格按顺序执行，先放行 22 再 deny 再 enable，防止 SSH 断开（风险 R-TVS-11）。

### 5.1 先放行 SSH 端口 22（防止 SSH 断开，必须第一步）
- [ ] SSH 登录服务器，执行 `ufw allow 22/tcp`，确认规则已添加
- [ ] 验收条件：`ufw status` 显示 `22/tcp ALLOW`（REQ-TVS-UBI-037，风险 R-TVS-11）

### 5.2 放行 HTTP 端口 80
- [ ] 执行 `ufw allow 80/tcp`，确认规则已添加
- [ ] 验收条件：`ufw status` 显示 `80/tcp ALLOW`（REQ-TVS-UBI-033）

### 5.3 拒绝 8080 端口外部直连
- [ ] 执行 `ufw deny 8080/tcp`，确认规则已添加
- [ ] ufw deny 8080 仅拒绝外部入站连接；nginx 本地 `proxy_pass http://localhost:8080` 通过回环访问不受影响（ufw 默认 allow loopback）
- [ ] 验收条件：`ufw status` 显示 `8080/tcp DENY`；nginx 反代仍可访问后端（REQ-TVS-UBI-034，风险 R-TVS-14）

### 5.4 设置默认策略并启用 ufw
- [ ] 执行 `ufw default deny incoming`（拒绝所有未放行的入站流量）
- [ ] 执行 `ufw default allow outgoing`（允许所有出站流量）
- [ ] 执行 `ufw --force enable`（启用防火墙，--force 跳过交互确认）
- [ ] 验收条件：`ufw status` 显示 `Status: active`；`default deny incoming`；`default allow outgoing`（REQ-TVS-UBI-035）

### 5.5 验证端口加固效果
- [ ] 验证当前 SSH 会话仍保持连接（未断开）
- [ ] 执行 `ufw status verbose` 确认规则：allow 22/tcp、allow 80/tcp、deny 8080/tcp、default deny incoming
- [ ] 从外部尝试 `curl http://115.175.44.27:8080` 确认连接被拒绝/超时
- [ ] 从外部尝试 `curl http://115.175.44.27` 确认 80 端口正常访问
- [ ] 执行 `ss -tlnp` 确认 8080 仍监听但被 ufw 拒绝外部访问（或后端改为监听 127.0.0.1:8080）
- [ ] 验收条件：SSH 不断；80 可访问；8080 外部不可连；ufw active（REQ-TVS-UBI-033/034/035/037）

---

## 6. T10 防爬策略（UA 过滤 + robots.txt）

> 对应 design.md 改动点 S-04/S-12/S-16、关键决策 D-TVS-15。依赖组4 完成（同改 nginx 配置文件，须协调避免冲突）。
> 目标：配置 User-Agent 过滤防爬、新增 robots.txt 声明、client_max_body_size 限制（client_max_body_size 已在组4.4 完成，本组聚焦 UA 过滤与 robots.txt）。

### 6.1 在 nginx.conf 新增 map UA 过滤规则
- [ ] 编辑 `/etc/nginx/nginx.conf` http 块，在 `limit_req_zone` 之后新增 `map $http_user_agent $bad_agent { default 0; "" 1; "~*Scrapy" 1; "~*bot" 1; "~*crawler" 1; "~*spider" 1; }`（含注释说明防爬 UA 过滤）
- [ ] map 仅匹配空 UA 和已知爬虫 UA（Scrapy/bot/crawler/spider）；主流浏览器 UA（Chrome/Edge/Safari/Firefox）不含这些关键词，不会被误伤
- [ ] 验收条件：`nginx.conf` 含 `map $http_user_agent $bad_agent` 定义；含空 UA 与爬虫 UA 匹配规则；default 为 0（REQ-TVS-UBI-043/EVT-045/UNW-047，风险 R-TVS-15）

### 6.2 在 sites-enabled/agritime 新增 UA 过滤 if 规则
- [ ] 编辑 `/etc/nginx/sites-enabled/agritime`，在 `location /` 块内 `try_files` 之前新增 `if ($bad_agent) { return 403; }`
- [ ] 被拦截请求返回 403（可读提示，非静默丢弃）
- [ ] 验收条件：配置含 `if ($bad_agent) { return 403; }`；空 UA/爬虫 UA 返回 403（REQ-TVS-EVT-045）

### 6.3 新增 robots.txt 文件
- [ ] 在服务器 `/agritime/frontend/pages/` 目录下新建 `robots.txt` 文件，内容为 `User-agent: *\nDisallow: /api/\nAllow: /`
- [ ] 声明不允许爬虫抓取 `/api/` 路径，允许抓取静态页面内容
- [ ] 验收条件：访问 `http://115.175.44.27/robots.txt` 返回 200 且内容含 `Disallow: /api/`（REQ-TVS-UBI-044）

### 6.4 nginx 配置语法测试与热加载
- [ ] 执行 `nginx -t` 测试配置语法，确认通过后执行 `nginx -s reload`
- [ ] 验收条件：`nginx -t` 通过；reload 后服务正常（风险 R-TVS-12）

### 6.5 验证防爬策略效果
- [ ] 发送空 User-Agent 请求 `curl -H "User-Agent: " http://115.175.44.27/` 确认返回 403
- [ ] 发送爬虫 UA 请求 `curl -H "User-Agent: Scrapy/2.0" http://115.175.44.27/` 确认返回 403
- [ ] 发送正常浏览器 UA 请求 `curl -H "User-Agent: Mozilla/5.0 (Chrome)" http://115.175.44.27/` 确认返回 200
- [ ] 验收条件：空 UA/爬虫 UA 返回 403；主流浏览器 UA 返回 200；正常用户不被误伤（REQ-TVS-EVT-045/UNW-047）

---

## 7. 回归验证与部署

> 依赖全部前序组完成。目标：全面验证 T08 前端语音组件与 T10 服务器安全加固的功能正确性，确认无回归，提交 Git 变更。

### 7.1 前端测试套件全绿验证
- [ ] 在本地执行 `node --test frontend/tests/pages.test.js`，确认既有 52 项断言 + 新增语音组件断言全部通过，无失败
- [ ] 确认既有 52 项断言原文未被删除/注释/弱化
- [ ] 验收条件：测试输出全绿；断言总数 > 52（REQ-TVS-UBI-056/057）

### 7.2 浏览器手动验证语音组件（支持与降级场景）
- [ ] 在支持 Web Speech API 的浏览器（Chrome/Edge）中打开 index.html，确认三个语音按钮可见可点击
- [ ] 点击作物品种语音按钮，口述"水稻"，确认 `#crop` 填充为"水稻"且输入框聚焦高亮
- [ ] 点击播种日期语音按钮，口述"2026年3月15日"，确认年/月/日三 select 分别设为 2026/3/15 且 `#sowingDate` 值为"2026-03-15"
- [ ] 点击所在地语音按钮，口述"济南"，确认 `#location` 填充为"济南"
- [ ] 在不支持 Web Speech API 的浏览器（Firefox）中打开 index.html，确认三个语音按钮隐藏，表单正常填写提交，控制台无未捕获错误
- [ ] 验证所在地为"成都"时点击语音按钮，方言匹配为西南官话（recognition.lang 设为对应语言代码）
- [ ] 验证录音中再次点击语音按钮可取消录音，输入框不变
- [ ] 验证识别结果填充后输入框可编辑，手动修改后提交按修改值校验
- [ ] 验证 375px/768px/1024px 三档宽度下语音按钮布局正常不遮挡输入框
- [ ] 验收条件：支持浏览器语音录入正常；不支持浏览器降级隐藏不报错；方言匹配正确；编辑确认正常；响应式正常（REQ-TVS-EVT-003~020/UBI-001~002/UNW-004/4.5.8）

### 7.3 nginx 限流压测验证
- [ ] 在服务器上用 `ab` 或 `for` 循环对 `/api/timeline` 发起 35 次连续请求，确认第 31 次起返回 429
- [ ] 检查 429 响应体为 JSON `{"code":429,"message":"请求过于频繁，请稍后再试"}`
- [ ] 等待 1 分钟后重新请求，确认限流计数重置，正常返回 200
- [ ] 确认正常浏览主页+查询（约 5-10 次请求）不触发限流
- [ ] 验收条件：31 次请求起返回 429；429 响应体为 JSON 友好提示；1 分钟后恢复；正常浏览不限流（REQ-TVS-UBI-023/024/026/EVT-025）

### 7.4 服务器端口安全验证
- [ ] 执行 `ufw status verbose` 确认 active + allow 22/80 + deny 8080 + default deny incoming
- [ ] 执行 `ss -tlnp` 确认对外暴露端口仅 80/22，8080 被防火墙拒绝外部访问
- [ ] 从外部尝试直连 `115.175.44.27:8080` 确认连接被拒绝/超时
- [ ] 确认 SSH 仍可正常连接
- [ ] 验收条件：ufw active；仅 80/22 对外；8080 外部不可连；SSH 正常（REQ-TVS-UBI-033/034/035/037）

### 7.5 访问日志与安全头验证
- [ ] 执行 `curl -I http://115.175.44.27/` 检查响应头含 `X-Content-Type-Options: nosniff`、`X-Frame-Options: SAMEORIGIN`、`X-XSS-Protection: 1; mode=block`；`Server` 头不含版本号
- [ ] 检查 `/var/log/nginx/agritime.access.log` 日志文件存在且有写入内容，每条日志含 IP/时间/方法/路径/状态码/响应大小/Referer/UA 字段
- [ ] 确认日志格式不含 `$request_body`（不记录请求体敏感信息）
- [ ] 确认日志文件权限为 640（`ls -l /var/log/nginx/agritime.access.log`）
- [ ] 验收条件：3 个安全头存在且值正确；Server 无版本号；日志含全部字段无 request_body；权限 640（REQ-TVS-UBI-038/041/048/049/UNW-042）

### 7.6 防爬策略验证
- [ ] 访问 `http://115.175.44.27/robots.txt` 确认返回 200 且含 `Disallow: /api/`
- [ ] 发送空 UA 请求确认返回 403；发送爬虫 UA 请求确认返回 403；发送正常浏览器 UA 确认返回 200
- [ ] 验收条件：robots.txt 可访问；空/爬虫 UA 返回 403；正常 UA 返回 200（REQ-TVS-UBI-044/EVT-045/UNW-047）

### 7.7 Git 提交变更
- [ ] 在本地 `frontend/` 目录执行 `git add` 新增 `frontend/components/voice-input/voice-input.js`、`frontend/components/voice-input/voice-input.css` 与修改的 `frontend/pages/index.html`、`frontend/tests/pages.test.js`
- [ ] 提交 commit，message 注明 T08 语音组件与 T10 安全加固变更范围
- [ ] 服务器端 nginx 配置与 ufw 规则变更须以可追溯方式记录（如运维操作日志或配置备份），便于复盘与回滚
- [ ] 验收条件：前端变更已 Git 提交；服务器变更已记录（REQ-TVS 4.4.6）

---

## 风险点与注意事项

> 执行中须特别注意的边界情况与高风险操作。

### T08 前端语音组件风险
- **R-TVS-08 XSS 风险（高）**：识别结果仅用 `input.value = transcript` 赋值，状态文案用 `textContent`，**严禁** `innerHTML` 拼接识别文本
- **R-TVS-09 方言标识注入（高）**：方言标识须来自 `DIALECT_MAP` 预定义枚举值，不得将用户输入的地点文本直接作为方言标识传递
- **R-TVS-01 浏览器兼容性（中）**：`detectSupport()` 须同时探测 `SpeechRecognition` 与 `webkitSpeechRecognition`，不支持时 `hidden` 降级不报错
- **R-TVS-06 按钮遮挡输入框（低）**：input 须增加 `padding-right`（`.has-voice` 类）留出按钮空间，375px 下验证不遮挡
- **R-TVS-10 组件阻塞表单（中）**：`VoiceInput.init()` 须在 IIFE 末尾调用（DOM 已就绪），不 await 阻塞，表单在 init 之前即可填写提交

### T10 服务器安全加固风险
- **R-TVS-11 ufw 启用后 SSH 断开（高）**：**必须严格按顺序**：先 `ufw allow 22/tcp` → 再 `ufw allow 80/tcp` → 再 `ufw deny 8080/tcp` → 再 `ufw default deny incoming` → 最后 `ufw --force enable`；启用前验证 22 规则已生效
- **R-TVS-12 nginx 配置语法错误（高）**：修改后先 `nginx -t` 测试，通过再 `nginx -s reload`，失败不 reload 保持上一配置运行
- **R-TVS-14 8080 ufw deny 后反代失败（中）**：ufw deny 8080 仅拒绝外部入站，nginx 本地 `proxy_pass http://localhost:8080` 走回环不受影响（ufw 默认 allow loopback）
- **R-TVS-15 防爬 UA 误伤正常用户（中）**：map 仅匹配空 UA 和已知爬虫 UA（Scrapy/bot/crawler/spider），主流浏览器 UA 不含这些关键词

### 边界情况
- **B-TVS-01 快速连续点击多个语音按钮**：仅允许一个活跃 recognition 实例，点击新按钮时先 `stopRecognition()` 停止当前录音
- **B-TVS-03 所在地为空时点击语音按钮**：`matchDialect("")` 回退普通话默认方言，识别正常进行
- **B-TVS-04 识别结果为纯空格**：`transcript.trim()` 后为空则不填充，展示"未识别到内容"提示
- **B-TVS-05 sowingDate 识别到部分日期**：仅匹配到月日时用当前年份补全；完全无法匹配返回 null + 错误提示
- **B-TVS-09 轻量账号系统后端不可用**：本次默认场景，前端不展示登录入口，查询记录使用既有 localStorage 本地历史