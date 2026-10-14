const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const PAGES = path.resolve(__dirname, '..', 'pages');
const CSS = path.resolve(__dirname, '..', 'assets', 'css');
const read = (p) => fs.readFileSync(p, 'utf8');

const indexHtml = read(path.join(PAGES, 'index.html'));
const timelineHtml = read(path.join(PAGES, 'timeline.html'));
const photoJs = read(path.join(PAGES, 'photo-capture.js'));
const commonCss = read(path.join(CSS, 'common.css'));
const timelineCss = read(path.join(CSS, 'timeline.css'));
const cropMapJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'crop-map.js'));
const mockJs = read(path.resolve(__dirname, '..', 'assets', 'mock', 'mock-data.js'));

test('输入表单页：三字段 + 拍照入口 + 模糊联想', () => {
  assert.match(indexHtml, /id="crop"/);
  assert.match(indexHtml, /id="sowingDate"/);
  assert.match(indexHtml, /id="location"/);
  assert.match(indexHtml, /id="crop-suggestions"/);
  assert.match(indexHtml, /id="photo-btn"/);
});

test('输入表单页：默认值优先与日期校验', () => {
  assert.ok(indexHtml.includes('setDefaultValues'));
  assert.ok(indexHtml.includes('播种日期不能晚于今天'));
});

test('移动端适配：viewport 声明与 375px 无横向滚动', () => {
  assert.match(indexHtml, /name="viewport"[^>]*width=device-width/);
  assert.match(timelineHtml, /name="viewport"[^>]*width=device-width/);
  assert.match(commonCss, /overflow-x:\s*hidden/);
  assert.match(commonCss, /max-width:\s*100%/);
});

test('时间线展示页：三层信息与首屏结论', () => {
  assert.ok(timelineHtml.includes('conclusion'));
  assert.match(timelineHtml, /tl-action/);
  assert.match(timelineHtml, /tl-decision/);
  assert.match(timelineHtml, /tl-note/);
});

test('时间线展示页：节点展开与空态', () => {
  assert.match(timelineHtml, /classList\.toggle\('open'\)/);
  assert.ok(timelineHtml.includes('未生成节点，请检查作物或日期'));
});

test('实线/虚线语义：依据 certainty 切换', () => {
  assert.match(timelineHtml, /predicted/);
  assert.match(timelineHtml, /certain/);
  assert.match(timelineCss, /\.tl-node\.predicted[\s\S]*dashed/);
});

test('文案来源受控：页面读取节点 copy 而非硬编码状态文案', () => {
  assert.match(timelineHtml, /node\.copy/);
  assert.ok(!timelineHtml.includes("'今天适宜作业，按计划进行'"));
});

test('拍照识别组件：须人工确认、失败回落搜索、可取消', () => {
  assert.match(photoJs, /needConfirm|确认/);
  assert.match(photoJs, /fallback|手动搜索/);
  assert.match(photoJs, /AbortController/);
  assert.ok(photoJs.includes('/api/recognition'));
});
test('自定义日期选择器：三联动下拉与范围校验', () => {
  assert.match(indexHtml, /class="date-picker"/);
  assert.match(indexHtml, /id="date-year"/);
  assert.match(indexHtml, /id="date-month"/);
  assert.match(indexHtml, /id="date-day"/);
  assert.ok(indexHtml.includes('RANGE_BACK_DAYS'));
  assert.ok(indexHtml.includes('播种日期不能早于两年前'));
  assert.ok(!indexHtml.includes('type="date"'));
  assert.ok(indexHtml.includes('id="sowingDate"'));
});
test('函数图像模式：切换控件与模式选项', () => {
  assert.match(timelineHtml, /mode-switch/);
  assert.match(timelineHtml, /mode-tab/);
  assert.ok(timelineHtml.includes('data-mode="node"'));
  assert.ok(timelineHtml.includes('data-mode="chart"'));
  assert.match(timelineHtml, /class="mode-tab active"/);
});

test('函数图像模式：chart 容器与原生 SVG 构建', () => {
  assert.match(timelineHtml, /id="chart"/);
  assert.match(timelineHtml, /createElementNS/);
});

test('函数图像模式：具名常量与占位提示', () => {
  assert.match(timelineHtml, /CHART_CONFIG/);
  assert.match(timelineHtml, /SIGMA/);
  assert.ok(timelineHtml.includes('数据待接入'));
});

test('函数图像模式：无外部图表库', () => {
  assert.ok(!timelineHtml.includes('Chart.js'));
  assert.ok(!timelineHtml.includes('echarts'));
  assert.ok(!timelineHtml.includes('d3'));
});
test('流程对齐：时间线请求为 GET 且参数名与后端一致', () => {
  assert.match(timelineHtml, /method:\s*'GET'/);
  assert.ok(!timelineHtml.includes("method: 'POST'"));
  assert.ok(!timelineHtml.includes('sowingDate'));
  assert.match(timelineHtml, /sowDate/);
  assert.match(timelineHtml, /URLSearchParams/);
});

test('流程对齐：位置以 lat/lon 标量传参', () => {
  assert.match(timelineHtml, /lat/);
  assert.match(timelineHtml, /lon/);
  assert.ok(!timelineHtml.includes('latitude'));
  assert.ok(!timelineHtml.includes('longitude'));
  assert.ok(!/location\s*:/.test(timelineHtml));
});

test('流程对齐：20s 超时常量与降级标识', () => {
  assert.match(timelineHtml, /TIMELINE_TIMEOUT_MS/);
  assert.ok(timelineHtml.includes('20000'));
  assert.match(timelineHtml, /AbortController/);
  assert.match(timelineHtml, /setTimeout/);
  assert.ok(timelineHtml.includes('本地兜底数据'));
});

test('流程对齐：作物映射共享模块完整且两页面共用', () => {
  ['dry_garlic', 'wheat_winter', 'corn_summer', 'rice', 'soybean'].forEach((id) => {
    assert.ok(cropMapJs.includes(id), 'crop-map.js 缺少 ' + id);
  });
  assert.match(cropMapJs, /resolveCropId/);
  assert.match(cropMapJs, /buildCropParam/);
  assert.match(indexHtml, /crop-map\.js/);
  assert.match(timelineHtml, /crop-map\.js/);
});

test('流程对齐：无臆造 cropId 且番茄显式 null', () => {
  assert.match(cropMapJs, /cropId:\s*null/);
  var ids = cropMapJs.match(/cropId:\s*'[a-z_]+'/g) || [];
  ids.forEach(function (line) {
    var v = line.match(/'([a-z_]+)'/)[1];
    assert.ok(['dry_garlic', 'wheat_winter', 'corn_summer', 'rice', 'soybean'].indexOf(v) !== -1, '臆造 cropId: ' + v);
  });
});

test('流程对齐：首页跳转参数链路自洽', () => {
  assert.match(indexHtml, /buildTimelineQuery/);
  assert.match(indexHtml, /sowDate/);
  assert.match(indexHtml, /lat/);
  assert.match(indexHtml, /lon/);
});

test('流程对齐：函数图像真实/占位双形态闸门', () => {
  assert.match(timelineHtml, /hasRealData/);
  assert.match(timelineHtml, /hasValidDays/);
});

test('流程对齐：reminders 缺失容错', () => {
  assert.match(timelineHtml, /Array\.isArray\(data\.reminders\)/);
});

test('流程对齐：mock 数据 null 占位不造假', () => {
  assert.match(mockJs, /date:\s*null/);
  assert.match(mockJs, /name:\s*null/);
});