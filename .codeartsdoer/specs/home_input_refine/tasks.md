# 首页输入表单精细化优化 — 实施任务清单（home_input_refine）

> 对应需求规格：`.codeartsdoer/specs/home_input_refine/spec.md`（24 条需求，已固化）
> 对应技术设计：`.codeartsdoer/specs/home_input_refine/design.md`（912 行）
> 改动文件：`frontend/pages/index.html`、`frontend/assets/css/common.css`、`frontend/tests/pages.test.js`（可选追加）
> 不得改动：`timeline.html`、`timeline.css`、`photo-capture.js`、`mock-data.js`、`frontend/assets/img/*`
> 测试命令：`node --test frontend/tests/pages.test.js`

---

## 1. 改造 HTML 结构与精简文案

**依赖**：无（基础设施任务，最先执行）
**目标**：将 `#field-sowing-date` 内原生 `<input type="date">` 替换为隐藏兼容 input + 三联动 select 容器；删除 tagline 与作物品种 placeholder；调整 label 指向。

### 1.1 删除 tagline 元素
- [ ] 删除 `frontend/pages/index.html` 第 24 行整段 `<p class="tagline">输入三个字段，得到一条会看天、懂节气、知行情的时间线。</p>`，使品牌区下方不再显示该口语化表述。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：index.html 中不再包含 `class="tagline"`；`.tagline` CSS 规则保留不删（无匹配元素，无害）。

### 1.2 删除作物品种 placeholder 属性
- [ ] 移除 `frontend/pages/index.html` 第 31 行 `<input id="crop" ...>` 的 `placeholder="例如：山东金乡大蒜"` 属性，改动后为 `<input id="crop" name="crop" type="text" autocomplete="off" />`。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：index.html 不再包含 `例如：山东金乡大蒜`；所在地 placeholder `例如：山东省济宁市金乡县`（第 44 行）保持不变。

### 1.3 替换播种日期字段为隐藏 input + 三 select 容器
- [ ] 将 `frontend/pages/index.html` 第 38 行 `<input id="sowingDate" name="sowingDate" type="date" />` 替换为：隐藏兼容 input（`id="sowingDate" name="sowingDate" type="hidden"`）+ `<div class="date-picker">` 容器内三个空 `<select>`（id 分别为 `date-year`、`date-month`、`date-day`）。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：`#field-sowing-date` 内含 hidden input（保留 `id="sowingDate"`）、`.date-picker` 容器及三个 select；页面中不再存在 `type="date"`；`#error-sowingDate`（第 39 行）初始文案 `播种日期不能晚于今天` 保留不变（测试红线）。

### 1.4 调整播种日期 label 指向
- [ ] 将 `frontend/pages/index.html` 第 37 行 `<label for="sowingDate">播种日期</label>` 的 for 属性改为 `date-year`，使点击标签聚焦年份下拉（hidden input 不可聚焦）。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：label 的 `for="date-year"`；点击"播种日期"标签时年份 select 获得焦点。

---

## 2. 追加自定义选择器 CSS 样式

**依赖**：T1.3（HTML 中存在 `.date-picker` 容器与三 select）
**目标**：在 common.css 末尾追加式新增 `.date-picker` 系列规则，视觉对齐既有 `.field input` 体系，复用 `:root` 设计变量，不删改任何既有规则（尤其 `overflow-x:hidden`、`max-width:100%`、`.page max-width:460px` 红线）。

### 2.1 追加 date-picker flex 容器与 select 基础样式
- [ ] 在 `frontend/assets/css/common.css` 末尾追加 `.date-picker { display:flex; gap:8px; width:100%; }` 容器规则；追加 `.date-picker select` 基础样式：`flex:1 1 0; min-width:0;` 防溢出，复用 `var(--ink)` 文字色、`var(--line)` 边框、`var(--radius-sm)` 圆角，`appearance:none` + 自定义 SVG 下拉箭头（内联 data URI），`font-size:18px`、`padding:15px 10px` 与 `.field input` 对齐。
- [ ] 追加 `.date-picker select#date-year { flex:1.3 1 0; }` 使年份略宽（4 位数+"年"）。
- **涉及文件**：`frontend/assets/css/common.css`
- **完成标志**：三 select 在 `.date-picker` 内横排等宽（年份略宽），375px 视口下无横向滚动；颜色均引用 CSS 变量，无硬编码颜色（SVG 箭头内联色值除外）。

### 2.2 追加聚焦态、失败态、禁用 option 样式
- [ ] 追加 `.date-picker select:focus` 规则：`outline:none; border-color:var(--leaf-2); box-shadow:0 0 0 4px rgba(63,143,86,0.14);` 与 `.field input:focus` 视觉一致。
- [ ] 追加 `.field.invalid .date-picker select` 规则：`border-color:var(--red); background-color:var(--red-soft);` 与 `.field.invalid input` 一致，使校验失败时三 select 标红。
- [ ] 追加 `.date-picker select option:disabled { color:var(--muted); opacity:0.5; }` 灰显禁用项；追加 `.date-picker select option[value=""] { color:#b3b8a8; }` 占位 option 样式。
- **涉及文件**：`frontend/assets/css/common.css`
- **完成标志**：聚焦任一 select 时边框变 `--leaf-2` 并有绿色外发光；日期校验失败时三 select 边框 `--red`、背景 `--red-soft`；禁用 option 灰显不可选；既有 CSS 规则未被删改。

---

## 3. 新增范围常量与边界计算函数

**依赖**：无（可与 T1 并行）
**目标**：在 index.html 内联脚本区集中定义范围常量与边界计算函数，消除魔法数字。

### 3.1 新增范围常量与 minStr() 下限函数
- [ ] 在 `frontend/pages/index.html` 内联 IIFE 中（`todayStr()` 附近）新增常量 `var RANGE_BACK_DAYS = 730;` 与 `var DEFAULT_BACK_DAYS = 150;`，并新增 `function minStr()`：基于 `new Date()` 减去 `RANGE_BACK_DAYS` 天，返回 `YYYY-MM-DD` 格式字符串（算法同 `todayStr()`）。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：`minStr()` 返回今天-730天的有效日期字符串；`minStr() < todayStr()` 恒成立；常量名 `RANGE_BACK_DAYS`、`DEFAULT_BACK_DAYS` 在脚本中集中定义。

### 3.2 新增 daysInMonth() 闰年判断函数
- [ ] 新增 `function daysInMonth(y, m)`（m 为 1-12）：m=2 时按 `(y%4===0 && y%100!==0) || y%400===0` 返回 29 或 28；m∈{4,6,9,11} 返回 30；其余返回 31。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：`daysInMonth(2024,2)===29`、`daysInMonth(2023,2)===28`、`daysInMonth(2024,1)===31`、`daysInMonth(2024,4)===30`。

---

## 4. 实现联动填充与双向同步函数

**依赖**：T3（minStr/daysInMonth 可用）、T1.3（三 select DOM 存在）
**目标**：实现年份/月份/日选项的联动填充、超范围禁用、值组合与反向同步。

### 4.1 获取三 select DOM 引用并实现 fillYears/fillMonths/fillDays
- [ ] 在 IIFE 中新增 `var selYear = document.getElementById('date-year');`、`selMonth`、`selDay` 引用。
- [ ] 实现 `fillYears()`：清空并填充年份 select，含占位 `<option value="" disabled>年</option>` + 从 `minStr()` 年到 `todayStr()` 年的 option（文本 `Y年`）。
- [ ] 实现 `fillMonths(year)`：首次调用时生成 1-12 option（含占位"月"）；每次按选中年份逐 option 切换 disabled——当年=min.y 时禁用 m<min.m 的月，当年=max.y 时禁用 m>max.m 的月。
- [ ] 实现 `fillDays(year, month)`：重建日 select option（含占位"日" + 1~daysInMonth），逐 option 置 disabled——当年月=min.y-min.m 时禁用 d<min.d 的日，当年月=max.y-max.m 时禁用 d>max.d 的日。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：年份下拉范围恰为 [下限年, 上限年]；选下限年时早于下限月的月份 disabled；选上限年时晚于今天月的月份 disabled；选下限年下限月时早于下限日的日 disabled；选上限年上限月时晚于今天日的日 disabled。

### 4.2 实现 composeDate() 与 syncSelectsFromDate() 双向同步
- [ ] 实现 `composeDate()`：从三 select 读取 value，若均有值则拼装 `YYYY-MM-DD`（月日 `padStart(2,'0')`）写入 `sowingInput.value`，否则置空。
- [ ] 实现 `syncSelectsFromDate(str)`：若 str 为空则三 select 回占位；否则解析年月日，依次设 `selYear.value`→`fillMonths(y)`→`selMonth.value`→`fillDays(y,m)`→`selDay.value`。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：三 select 均有值时 `sowingInput.value` 为 `YYYY-MM-DD`（月日两位补零）；给定 `2026-05-15` 调用 `syncSelectsFromDate` 后三 select 分别选中 2026/5/15。

### 4.3 实现 clampDay() 钳制函数
- [ ] 实现 `clampDay(y, m)`：若当前 `selDay.value` > `daysInMonth(y,m)` 则钳制至该月最大日；若钳制后该 option 仍 disabled，则从最大日向下寻找最近非 disabled 日并选中。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：闰年切换场景（2024年2月29日→改选2023年）日自动钳制至 28；钳制后不会停留在 disabled option 上。

### 4.4 绑定 year/month/day change 联动事件
- [ ] 绑定 `selYear` change：取年份→`fillMonths(y)`→若当前月被禁用则清空→若仍有月则 `fillDays`+`clampDay`→`composeDate()`。
- [ ] 绑定 `selMonth` change：取年月→`fillDays(y,m)`→`clampDay(y,m)`→`composeDate()`。
- [ ] 绑定 `selDay` change：`composeDate()`。
- **涉及文件**：`frontend/pages/index.html`
- **完成标志**：改选年份时月份禁用态与日选项刷新；改选月份时日选项按该月天数刷新；改选日时 hidden input 值同步更新。

---

## 5. 整合 setDefaultValues 与 validate

**依赖**：T3、T4（fillYears/syncSelectsFromDate/minStr 可用）
**目标**：改造既有两个函数以适配自定义选择器，保留函数名与测试红线文案。

### 5.1 改造 setDefaultValues()
- [ ] 保留 `locationInput.value = locationInput.value || '山东省济宁市金乡县'` 不变。
- [ ] 在 sowingDate 默认值逻辑前新增 `fillYears()` 初始化年份选项。
- [ ] 保留今天-150天默认值计算逻辑，将硬编码 `150` 替换为 `DEFAULT_BACK_DAYS` 常量。
- [ ] 默认值写入 `sowingInput.value` 后新增 `syncSelectsFromDate(sowingInput.value)` 反向同步三 select。
- [ ] 移除 `sowingInput.max = todayStr()`（hidden input 无 max 语义）。
- **涉及文件**：`frontend/pages/index.html`（第 94-102 行函数体）
- **完成标志**：页面加载后三 select 显示今天-150天的年/月/日；`setDefaultValues` 函数名保留；hidden input 值为今天-150天的 `YYYY-MM-DD`。

### 5.2 改造 validate() 增加下限校验与动态错误文案
- [ ] 保留 crop 与 location 校验逻辑不变。
- [ ] 将日期校验从 `!value || value > todayStr()` 扩展为三分支：空值→invalid，文案 `请选择播种日期`；`value > todayStr()`→invalid，文案 `播种日期不能晚于今天`（保留既有文案，测试红线）；`value < minStr()`→invalid，文案 `播种日期不能早于两年前`（新增下限文案）；其余→valid。
- [ ] 通过 `document.getElementById('error-sowingDate').textContent` 动态切换文案，并调用 `setInvalid('field-sowing-date', invalidDate)`。
- **涉及文件**：`frontend/pages/index.html`（第 137-145 行函数体）
- **完成标志**：晚于今天提交显示"播种日期不能晚于今天"；早于今天-730天提交显示"播种日期不能早于两年前"；空值提交显示"请选择播种日期"；合法日期校验通过；`validate` 函数名保留。

---

## 6. 测试回归与新增断言

**依赖**：T1-T5 全部完成
**目标**：确保既有 8 项断言全绿，并可选新增针对自定义选择器的断言。

### 6.1 运行既有测试确认 8 项断言全绿
- [ ] 执行 `node --test frontend/tests/pages.test.js`，确认既有 8 项断言全部通过，重点核对：`id="sowingDate"` 存在、`setDefaultValues` 存在、`播种日期不能晚于今天` 文案存在、`overflow-x:hidden` 与 `max-width:100%` 存在。
- **涉及文件**：`frontend/tests/pages.test.js`（仅运行，不改动）
- **完成标志**：测试输出 8 项全 pass，0 fail。

### 6.2 新增针对自定义选择器的断言（可选）
- [ ] 在 `frontend/tests/pages.test.js` 末尾追加新断言（不改动既有断言）：三 select + date-picker 容器存在（`id="date-year"`/`id="date-month"`/`id="date-day"`/`class="date-picker"`）；hidden input 存在（`id="sowingDate" name="sowingDate" type="hidden"`）；原生 date input 已移除（`doesNotMatch /type="date"/`）；下限文案存在（`播种日期不能早于两年前`）；tagline 已删除（不含 `class="tagline"`）；作物品种 placeholder 已删除且所在地 placeholder 保留；范围常量集中定义（含 `RANGE_BACK_DAYS`、`DEFAULT_BACK_DAYS`）。
- **涉及文件**：`frontend/tests/pages.test.js`
- **完成标志**：新增断言编写完成。

### 6.3 运行完整测试套件确认全绿
- [ ] 再次执行 `node --test frontend/tests/pages.test.js`，确认既有 8 项 + 新增断言全部通过。
- **涉及文件**：`frontend/tests/pages.test.js`
- **完成标志**：全部断言 pass，0 fail。

---

## 7. 交互自测与边界用例验证

**依赖**：T6（测试套件全绿）
**目标**：在浏览器中验证联动逻辑、范围边界、提交拦截与视觉表现。

### 7.1 初始加载与默认值自测
- [ ] 打开首页，确认：无 tagline；作物品种无 placeholder；所在地 placeholder 为"例如：山东省济宁市金乡县"；三 select 显示今天-150天的年/月/日；无 `<input type="date">`。
- [ ] 清空三 select，确认分别显示"年""月""日"中文占位。
- **完成标志**：初始状态与占位符符合预期。

### 7.2 联动与禁用边界自测
- [ ] **下限当月日禁用**：选下限年下限月，确认早于下限日的日选项 disabled 灰显不可选。
- [ ] **上限当月日禁用**：选今天年今天月，确认晚于今天日的日选项 disabled 灰显不可选。
- [ ] **闰年 2/29 切换**：选 2024 年 2 月确认日选项 1-29；选 2024年2月29日后改选 2023 年，确认日刷新为 1-28 且日自动钳制至 28。
- [ ] **联动刷新**：改选年份后月份禁用态刷新、日按新月天数刷新；改选月份后日刷新。
- **完成标志**：四类边界用例均符合预期。

### 7.3 提交校验自测
- [ ] **默认值在范围内**：初始加载后填完作物与所在地直接提交，确认校验通过并跳转 timeline.html，URL 中 `sowingDate=YYYY-MM-DD`（月日两位补零）。
- [ ] **超下限提交拦截**：通过 DevTools 将 hidden input 值改为早于今天-730天的日期并提交，确认字段标红、显示"播种日期不能早于两年前"、不跳转。
- [ ] **超上限提交拦截**：将 hidden input 值改为晚于今天的日期并提交，确认字段标红、显示"播种日期不能晚于今天"、不跳转。
- [ ] **空值提交**：清空日期后提交，确认字段标红、显示"请选择播种日期"。
- **完成标志**：四类提交场景均符合预期，提交格式始终为 `YYYY-MM-DD`。

### 7.4 窄屏与视觉自测
- [ ] 在 375px 视口下操作三 select，确认横排完整可见、无横向滚动。
- [ ] 聚焦任一 select 确认边框变 `--leaf-2` + 4px 绿色外发光；触发日期校验失败确认三 select 边框 `--red`、背景 `--red-soft`。
- [ ] 禁用浏览器 JS 后打开首页，确认三 select 仍可操作（原生下拉），无联动与自定义校验（渐进增强）。
- **完成标志**：窄屏无溢出；聚焦/失败态视觉与 `.field input` 一致；禁用 JS 下降级可用。

---

## 8. 最终核验与回退确认

**依赖**：T7
**目标**：确认不可改动项未被触碰，改动范围与回退路径清晰。

### 8.1 不可改动项核验
- [ ] 通过 `git diff` 确认 `frontend/pages/timeline.html`、`frontend/pages/photo-capture.js`、`frontend/assets/mock/mock-data.js`、`frontend/assets/img/*` 均无改动。
- [ ] 确认 `common.css` 中 `:root` 变量、`overflow-x:hidden`、`max-width:100%`、`.page max-width:460px`、`.field input` 既有规则、`.tagline` 规则均未被删改。
- [ ] 确认 index.html 中 crop/location/suggestions/photo-btn 元素、图片资产引用、timeline.html 跳转引用均未改动。
- **完成标志**：不可改动项核验通过，改动仅限 index.html、common.css、pages.test.js（可选）。

### 8.2 改动清单与回退路径确认
- [ ] 核对实际改动与 design.md 第 2.9 节文件改动清单一致。
- [ ] 确认回退路径可行：恢复第 24/31/37/38 行原内容、删除追加 CSS 规则、恢复 setDefaultValues/validate 原函数体、删除新增函数与断言即可完整回退。
- **完成标志**：改动清单核对一致；回退路径清晰可独立执行。

---

## 任务依赖关系

```
T1 (HTML/文案) ──┬──> T2 (CSS)
                  └──> T4 (联动函数) ──> T5 (整合) ──┐
T3 (常量/边界) ───┴──> T4                              ├──> T6 (测试) ──> T7 (自测) ──> T8 (核验)
```

- T1、T3 无依赖，可并行。
- T2 依赖 T1.3（HTML 结构存在）。
- T4 依赖 T1.3（DOM）与 T3（边界函数）。
- T5 依赖 T3、T4。
- T6 依赖 T1-T5 全部完成。
- T7 依赖 T6。
- T8 依赖 T7。

## 关键检查点汇总

| 检查点 | 所属任务 | 验证方式 |
|--------|---------|---------|
| 无 `type="date"` 残留 | T1.3 | grep / 浏览器审查 |
| `id="sowingDate"` 保留 | T1.3 | 测试断言 |
| `播种日期不能晚于今天` 文案保留 | T1.3 | 测试断言 |
| 下限当月日禁用 | T4.1 / T7.2 | 浏览器操作 |
| 上限当月日禁用 | T4.1 / T7.2 | 浏览器操作 |
| 闰年 2/29 切换钳制 | T4.3 / T7.2 | 浏览器操作 |
| 默认值=今天-150天且在范围内 | T5.1 / T7.3 | 浏览器操作 |
| 超下限提交拦截 | T5.2 / T7.3 | 浏览器操作 |
| 超上限提交拦截 | T5.2 / T7.3 | 浏览器操作 |
| 提交格式 YYYY-MM-DD | T4.2 / T7.3 | URL 检查 |
| 既有 8 项断言全绿 | T6.1 | `node --test` |
| 375px 无横向滚动 | T2.1 / T7.4 | 浏览器响应式 |
| 不可改动项未触碰 | T8.1 | `git diff` |