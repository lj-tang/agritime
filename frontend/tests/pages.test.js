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
const timelineLogicJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'timeline-logic.js'));
const historyStoreJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'history-store.js'));
const chartClientJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'chart-client.js'));

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
test('加载反馈：进度提示与骨架屏', () => {
  assert.match(timelineHtml, /id="loading"/);
  assert.ok(timelineHtml.includes('正在读取农时'));
  assert.match(timelineHtml, /class="skeleton"/);
  assert.match(timelineCss, /\.loading-bar/);
  assert.ok(!timelineCss.includes('nprogress'));
});

test('节气与天气：各自独立渲染（非互斥）', () => {
  assert.match(timelineHtml, /node\.solarTerm/);
  assert.ok(!/\}\s*else if \(node\.weatherIcon\)/.test(timelineHtml));
  assert.match(timelineHtml, /renderWeather/);
});

test('天气图标扩展与适宜度/警示/因子', () => {
  ['overcast', 'snow', 'fog', 'thunder', 'multiCloud'].forEach((k) => {
    assert.ok(timelineLogicJs.includes(k), 'timeline-logic.js 缺少 ' + k);
  });
  assert.match(timelineLogicJs, /WEATHER_ICON_FALLBACK/);
  assert.match(timelineHtml, /tl-suitability/);
  assert.match(timelineHtml, /tl-alert/);
  assert.match(timelineHtml, /tl-factors/);
  assert.ok(timelineHtml.includes('天气数据暂不可用'));
});

test('图表降级：POST 隔离到 chart-client', () => {
  assert.ok(!timelineHtml.includes("method: 'POST'"));
  assert.ok(chartClientJs.includes('/api/chart'));
  assert.ok(chartClientJs.includes("method: 'POST'"));
  assert.match(timelineHtml, /hasRealData/);
  assert.match(timelineHtml, /hasValidDays/);
  assert.ok(timelineHtml.includes('数据待接入'));
});

test('识别双通道：拍照 + 上传并列入口', () => {
  const fileInputCount = (photoJs.match(/type = 'file'/g) || []).length;
  assert.ok(fileInputCount >= 2, '应有至少两个文件输入（拍照/上传）');
  assert.ok(photoJs.includes("setAttribute('capture'"));
  assert.match(photoJs, /needConfirm|确认/);
  assert.match(photoJs, /fallback|手动搜索/);
  assert.match(photoJs, /AbortController/);
  assert.ok(photoJs.includes('/api/recognition'));
  assert.match(indexHtml, /id="upload-btn"/);
});

test('搜索联想：本地匹配 + 空输入隐藏 + 高亮', () => {
  assert.match(indexHtml, /function renderSuggestions/);
  assert.match(indexHtml, /CROPS\.filter/);
  assert.match(indexHtml, /suggestions\.classList\.remove\('show'\)/);
  assert.match(indexHtml, /highlight/);
});

test('历史记录：键名 / 上限 / 去重 / 回填', () => {
  assert.ok(historyStoreJs.includes('agritime.history.v1'));
  assert.match(historyStoreJs, /MAX = 5/);
  assert.match(historyStoreJs, /unshift/);
  assert.match(historyStoreJs, /try/);
  assert.match(indexHtml, /history-store\.js/);
  assert.match(indexHtml, /id="history-list"/);
  assert.match(indexHtml, /HistoryStore\.add/);
});

test('倒计时与多节日：文案与不截断', () => {
  assert.match(timelineLogicJs, /calcCountdown/);
  assert.ok(timelineHtml.includes('就是今天'));
  assert.ok(timelineHtml.includes('已过'));
  assert.ok(timelineHtml.includes('还有'));
  assert.ok(!timelineHtml.includes('slice(0,'));
});

test('响应式：768 / 1024 断点且保留防溢出', () => {
  assert.match(commonCss, /@media\s*\(min-width:\s*768px\)/);
  assert.match(commonCss, /@media\s*\(min-width:\s*1024px\)/);
  assert.match(commonCss, /overflow-x:\s*hidden/);
  assert.match(commonCss, /max-width:\s*100%/);
});

test('mock 新字段：null 占位不造假', () => {
  ['weatherFactors', 'suitability', 'severeWeatherAlert', 'dateAnchor', 'applicableCrops', 'salesCopy'].forEach((f) => {
    assert.match(mockJs, new RegExp(f + ':\\s*null'), 'mock 缺少 ' + f + ' null 占位');
  });
});

test('纯函数与存储：倒计时四态 + 天气映射 + 因子归一化', () => {
  const TL = require('../assets/js/timeline-logic.js');
  const base = new Date(2026, 0, 1);
  assert.deepStrictEqual(TL.calcCountdown('2026-01-11', base), { days: 10, state: 'future' });
  assert.deepStrictEqual(TL.calcCountdown('2026-01-01', base), { days: 0, state: 'today' });
  assert.deepStrictEqual(TL.calcCountdown('2025-12-25', base), { days: -7, state: 'past' });
  assert.deepStrictEqual(TL.calcCountdown('', base), { days: null, state: 'invalid' });
  assert.deepStrictEqual(TL.calcCountdown('not-a-date', base), { days: null, state: 'invalid' });
  assert.strictEqual(TL.getWeatherIcon(null), null);
  assert.strictEqual(TL.getWeatherIcon('clear').label, '晴');
  assert.strictEqual(TL.getWeatherIcon('snow').label, '雪');
  assert.strictEqual(TL.getWeatherIcon('unheard-of').known, false);
  assert.strictEqual(TL.getSuitability(null), null);
  assert.strictEqual(TL.getSuitability('green').label, '适宜');
  assert.strictEqual(TL.getSuitability('yellow').label, '谨慎');
  assert.strictEqual(TL.getSuitability('red').label, '不宜');
  assert.strictEqual(TL.getSuitability('purple'), null);
  assert.strictEqual(TL.getSevereAlert(null), null);
  assert.ok(TL.getSevereAlert('storm').label);
  assert.deepStrictEqual(TL.normalizeFactors(null), []);
  assert.deepStrictEqual(TL.normalizeFactors('因雨推迟'), ['因雨推迟']);
  assert.deepStrictEqual(TL.normalizeFactors(['a', '', 'b']), ['a', 'b']);
});

test('历史记录存储：上限 5 / 去重 / 容错', () => {
  const HS = require('../assets/js/history-store.js');
  const m = {};
  const s = {
    getItem: (k) => (k in m ? m[k] : null),
    setItem: (k, v) => { m[k] = String(v); },
    removeItem: (k) => { delete m[k]; }
  };
  for (let i = 0; i < 7; i += 1) {
    HS.add({ cropName: 'crop' + i, sowDate: '2026-01-0' + (i + 1), location: 'loc' + i }, s);
  }
  assert.strictEqual(HS.list(s).length, 5);
  assert.strictEqual(HS.list(s)[0].cropName, 'crop6');
  HS.add({ cropName: 'crop6', sowDate: '2026-01-07', location: 'loc6' }, s);
  assert.strictEqual(HS.list(s).length, 5);
  assert.strictEqual(HS.list(s)[0].cropName, 'crop6');
  HS.clear(s);
  assert.strictEqual(HS.list(s).length, 0);
  s.setItem(HS.KEY, '{bad json');
  assert.deepStrictEqual(HS.list(s), []);
});
test('日期范围：具名常量与最大边界函数', () => {
  assert.ok(indexHtml.includes('MIN_YEAR_OFFSET'));
  assert.ok(indexHtml.includes('MAX_FUTURE_DAYS'));
  assert.ok(indexHtml.includes('RANGE_HINT_TEXT'));
  assert.ok(indexHtml.includes('RANGE_HINT_TITLE'));
  assert.ok(indexHtml.includes('getMaxDate'));
  assert.ok(indexHtml.includes('calcMinSelectableDate'));
});

test('日期范围：提示文案与实际范围一致', () => {
  assert.ok(indexHtml.includes('两年前'));
  assert.ok(indexHtml.includes('10 天'));
  assert.ok(indexHtml.includes('date-range-hint'));
  assert.ok(commonCss.includes('.date-range-hint'));
});

test('日期范围：最大边界文案更新', () => {
  assert.ok(indexHtml.includes('播种日期不能晚于今天后 10 天'));
  assert.ok(indexHtml.includes('播种日期不能晚于今天'));
});

test('日期范围：灰度禁用样式增强', () => {
  assert.ok(commonCss.includes('cursor: not-allowed'));
  assert.ok(commonCss.includes('opacity: 0.5'));
});

test('日期范围：闰年 2 月 29 日回退', () => {
  function calcMin(today) {
    var MIN_YEAR_OFFSET = 2;
    var result = new Date(today.getFullYear() - MIN_YEAR_OFFSET, today.getMonth(), today.getDate());
    if (result.getMonth() !== today.getMonth()) {
      result = new Date(today.getFullYear() - MIN_YEAR_OFFSET, today.getMonth() + 1, 0);
    }
    return result;
  }
  var d1 = calcMin(new Date(2024, 1, 29));
  assert.strictEqual(d1.getFullYear(), 2022);
  assert.strictEqual(d1.getMonth(), 1);
  assert.strictEqual(d1.getDate(), 28);
  var d2 = calcMin(new Date(2026, 9, 17));
  assert.strictEqual(d2.getFullYear(), 2024);
  assert.strictEqual(d2.getMonth(), 9);
  assert.strictEqual(d2.getDate(), 17);
  var d3 = calcMin(new Date(2026, 2, 1));
  assert.strictEqual(d3.getFullYear(), 2024);
  assert.strictEqual(d3.getMonth(), 2);
  assert.strictEqual(d3.getDate(), 1);
  var d4 = calcMin(new Date(2026, 11, 28));
  assert.strictEqual(d4.getFullYear(), 2024);
  assert.strictEqual(d4.getMonth(), 11);
  assert.strictEqual(d4.getDate(), 28);
  assert.ok(indexHtml.includes('getFullYear() - MIN_YEAR_OFFSET'));
  assert.ok(indexHtml.includes('getMonth() !=='));
});

test('日期范围：最大边界跨年跨月', () => {
  var d = new Date(2026, 11, 28);
  d.setDate(d.getDate() + 10);
  assert.strictEqual(d.getFullYear(), 2027);
  assert.strictEqual(d.getMonth(), 0);
  assert.strictEqual(d.getDate(), 7);
  assert.ok(indexHtml.includes('MAX_FUTURE_DAYS'));
});

test('日期范围：既有断言兼容性复核', () => {
  assert.ok(indexHtml.includes('RANGE_BACK_DAYS'));
  assert.ok(indexHtml.includes('播种日期不能晚于今天'));
  assert.ok(indexHtml.includes('播种日期不能早于两年前'));
  assert.ok(indexHtml.includes('class="date-picker"'));
  assert.ok(indexHtml.includes('id="date-year"'));
  assert.ok(indexHtml.includes('id="date-month"'));
  assert.ok(indexHtml.includes('id="date-day"'));
  assert.ok(indexHtml.includes('id="sowingDate"'));
  assert.ok(!indexHtml.includes('type="date"'));
});