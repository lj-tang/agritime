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
const timelineLogicJs = read(path.resolve(__dirname, '..', '..', 'shared', 'utils', 'timeline-logic.js'));
const historyStoreJs = read(path.resolve(__dirname, '..', '..', 'shared', 'utils', 'storage.js'));
const chartClientJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'chart-client.js'));
const weatherVisualJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'weather-visual.js'));
const sunnySvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'sunny.svg'));
const rainSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'rain.svg'));
const cloudySvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'cloudy.svg'));
const snowSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'snow.svg'));
const defaultSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'default.svg'));
const voiceInputJs = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.js'));
const voiceInputCss = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.css'));
const timePeriodJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'time-period.js'));
const sidebarJs = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));
const sidebarCss = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.css'));
const sunnyNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'sunny-night.svg'));
const rainNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'rain-night.svg'));
const cloudyNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'cloudy-night.svg'));
const snowNightSvg = read(path.resolve(__dirname, '..', 'assets', 'weather-bg', 'snow-night.svg'));
const myCropsStoreJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-crops-store.js'));
const myCropsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-crops.js'));
const myCropsCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'my-crops.css'));
const cropPhotoJs = read(path.resolve(__dirname, '..', 'components', 'crop-photo', 'crop-photo.js'));
const locationPickerJs = read(path.resolve(__dirname, '..', 'components', 'location-picker', 'location-picker.js'));
const locationPickerCss = read(path.resolve(__dirname, '..', 'components', 'location-picker', 'location-picker.css'));

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
  assert.match(indexHtml, /id="sowingDate"[^>]*type="hidden"/, '主表单播种日期应是隐藏域，由三联动写入');
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
  assert.match(historyStoreJs, /MAX = 20/);
  assert.match(historyStoreJs, /unshift/);
  assert.match(historyStoreJs, /try/);
  assert.match(indexHtml, /storage\.js/);
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
  const TL = require('../../shared/utils/timeline-logic.js');
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

test('历史记录存储：上限 20 / 去重 / 容错', () => {
  const HS = require('../../shared/utils/storage.js');
  const m = {};
  const s = {
    getItem: (k) => (k in m ? m[k] : null),
    setItem: (k, v) => { m[k] = String(v); },
    removeItem: (k) => { delete m[k]; }
  };
  for (let i = 0; i < 22; i += 1) {
    HS.add({ cropName: 'crop' + i, sowDate: '2026-01-0' + ((i % 9) + 1), location: 'loc' + i }, s);
  }
  assert.strictEqual(HS.list(s).length, 20);
  assert.strictEqual(HS.list(s)[0].cropName, 'crop21');
  HS.add({ cropName: 'crop21', sowDate: '2026-01-01', location: 'loc21' }, s);
  assert.strictEqual(HS.list(s).length, 20);
  assert.strictEqual(HS.list(s)[0].cropName, 'crop21');
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
  assert.ok(indexHtml.includes('30 天'), 'V5 起未来范围改为 30 天');
  assert.ok(indexHtml.includes('date-range-hint'));
  assert.ok(commonCss.includes('.date-range-hint'));
});

test('日期范围：最大边界文案更新', () => {
  assert.ok(indexHtml.includes('播种日期不能晚于今天后 30 天'));
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
  assert.match(indexHtml, /id="sowingDate"[^>]*type="hidden"/, '主表单播种日期应是隐藏域，由三联动写入');
});
test('T01 天气卡：DOM 结构与脚本引入', () => {
  assert.ok(indexHtml.includes('id="weather-card"'));
  assert.ok(indexHtml.includes('id="weather-bg-layer"'));
  assert.ok(indexHtml.includes('id="weather-loc"'));
  assert.ok(indexHtml.includes('id="weather-temp"'));
  assert.ok(indexHtml.includes('id="weather-desc"'));
  assert.ok(indexHtml.includes('id="weather-extra"'));
  assert.ok(indexHtml.includes('weather-visual.js'));
  assert.ok(indexHtml.includes('data-weather="default"'));
});

test('T01 weather-visual.js：关键函数与常量', () => {
  assert.ok(weatherVisualJs.includes('normalizeWeather'));
  assert.ok(weatherVisualJs.includes('inferSeason'));
  assert.ok(weatherVisualJs.includes('getLocation'));
  assert.ok(weatherVisualJs.includes('fetchWeather'));
  assert.ok(weatherVisualJs.includes('buildWeatherInfo'));
  assert.ok(weatherVisualJs.includes('renderCard'));
  assert.ok(weatherVisualJs.includes('applyTheme'));
  assert.ok(weatherVisualJs.includes('applyBackground'));
  assert.ok(weatherVisualJs.includes('fallback'));
  assert.ok(weatherVisualJs.includes('OPEN_METEO_URL'));
  assert.ok(weatherVisualJs.includes('REQUEST_TIMEOUT_MS'));
});

test('T01 weathercode 归一化：WMO 映射', () => {
  assert.ok(weatherVisualJs.includes('WMO_MAP'));
  assert.ok(weatherVisualJs.includes('0: \'sunny\''));
  assert.ok(weatherVisualJs.includes('3: \'cloudy\''));
  assert.ok(weatherVisualJs.includes('61: \'rain\''));
  assert.ok(weatherVisualJs.includes('71: \'snow\''));
});

test('T01 季节推断：四季映射', () => {
  assert.ok(weatherVisualJs.includes('spring'));
  assert.ok(weatherVisualJs.includes('summer'));
  assert.ok(weatherVisualJs.includes('autumn'));
  assert.ok(weatherVisualJs.includes('winter'));
});

test('T01 Open-Meteo API：URL 与超时', () => {
  assert.ok(weatherVisualJs.includes('api.open-meteo.com'));
  assert.ok(weatherVisualJs.includes('temperature_2m'));
  assert.ok(weatherVisualJs.includes('relative_humidity_2m'));
  assert.ok(weatherVisualJs.includes('weather_code'));
  assert.ok(weatherVisualJs.includes('wind_speed_10m'));
  assert.ok(weatherVisualJs.includes('AbortController'));
  assert.ok(weatherVisualJs.includes('8000'));
});

test('T01 降级机制：fallback 不白屏', () => {
  assert.ok(weatherVisualJs.includes('function fallback'));
  assert.ok(weatherVisualJs.includes('hidden = true'));
  assert.ok(weatherVisualJs.includes('\'default\''));
});

test('T01 SVG 背景资源：5 类天气', () => {
  assert.ok(sunnySvg.includes('<svg'));
  assert.ok(rainSvg.includes('<svg'));
  assert.ok(cloudySvg.includes('<svg'));
  assert.ok(snowSvg.includes('<svg'));
  assert.ok(defaultSvg.includes('<svg'));
});

test('T01 CSS 天气样式：卡片区与背景层', () => {
  assert.ok(commonCss.includes('.weather-card'));
  assert.ok(commonCss.includes('.weather-bg-layer'));
  assert.ok(commonCss.includes('.weather-loc'));
  assert.ok(commonCss.includes('.weather-temp'));
  assert.ok(commonCss.includes('.weather-desc'));
  assert.ok(commonCss.includes('.weather-extra'));
});

test('T01 CSS 主题色切换：data-weather 属性选择器', () => {
  assert.ok(commonCss.includes('[data-weather="sunny"]'));
  assert.ok(commonCss.includes('[data-weather="rain"]'));
  assert.ok(commonCss.includes('[data-weather="cloudy"]'));
  assert.ok(commonCss.includes('[data-weather="snow"]'));
});

test('T01 CSS 动效：@keyframes 四类天气', () => {
  assert.ok(commonCss.includes('@keyframes weather-sunny'));
  assert.ok(commonCss.includes('@keyframes weather-rain'));
  assert.ok(commonCss.includes('@keyframes weather-cloudy'));
  assert.ok(commonCss.includes('@keyframes weather-snow'));
});

test('T01 CSS 响应式与无障碍：375 断点 + reduced-motion', () => {
  assert.ok(commonCss.includes('max-width: 375px'));
  assert.ok(commonCss.includes('prefers-reduced-motion'));
  assert.ok(commonCss.includes('.weather-extra { display: none; }'));
});
test('T08 语音组件：voice-input.js 关键函数与常量', () => {
  assert.ok(voiceInputJs.includes('SpeechRecognition'));
  assert.ok(voiceInputJs.includes('webkitSpeechRecognition'));
  assert.ok(voiceInputJs.includes('createVoiceButton'));
  assert.ok(voiceInputJs.includes('getDialect'));
  assert.ok(voiceInputJs.includes('DIALECT_MAP'));
  assert.ok(voiceInputJs.includes('zh-CN'));
  assert.ok(voiceInputJs.includes('zh-HK'));
  assert.ok(voiceInputJs.includes('zh-TW'));
});

test('T08 语音组件：降级机制', () => {
  assert.ok(voiceInputJs.includes('style.display'));
  assert.ok(voiceInputJs.includes('none'));
  assert.ok(voiceInputJs.includes('getRecognition'));
});

test('T08 语音组件：识别结果填充与事件触发', () => {
  assert.ok(voiceInputJs.includes('targetInput.value'));
  assert.ok(voiceInputJs.includes('dispatchEvent'));
  assert.ok(voiceInputJs.includes('new Event'));
});

test('T08 语音组件：CSS 样式', () => {
  assert.ok(voiceInputCss.includes('.voice-btn'));
  assert.ok(voiceInputCss.includes('.voice-btn--active'));
  assert.ok(voiceInputCss.includes('@keyframes voice-pulse'));
  assert.ok(voiceInputCss.includes('prefers-reduced-motion'));
});

test('T08 index.html：语音组件引入', () => {
  assert.ok(indexHtml.includes('voice-input.css'));
  assert.ok(indexHtml.includes('voice-input.js'));
});
test('T01增强 time-period.js：时段判定函数', () => {
  assert.ok(timePeriodJs.includes('judgeTimePeriod'));
  assert.ok(timePeriodJs.includes('parseSunTime'));
  assert.ok(timePeriodJs.includes('applyTimePeriod'));
  assert.ok(timePeriodJs.includes('applySunData'));
  assert.ok(timePeriodJs.includes('fallbackPeriod'));
  assert.ok(timePeriodJs.includes('morning'));
  assert.ok(timePeriodJs.includes('forenoon'));
  assert.ok(timePeriodJs.includes('noon'));
  assert.ok(timePeriodJs.includes('afternoon'));
  assert.ok(timePeriodJs.includes('evening'));
  assert.ok(timePeriodJs.includes('night'));
});

test('T01增强 time-period.js：日出日落解析', () => {
  const TP = require('../assets/js/time-period.js');
  assert.strictEqual(TP.parseSunTime('2026-10-23T06:30+08:00'), 6 * 60 + 30);
  assert.strictEqual(TP.parseSunTime('2026-10-23T18:45+08:00'), 18 * 60 + 45);
  assert.strictEqual(TP.parseSunTime(null), null);
  assert.strictEqual(TP.parseSunTime('invalid'), null);
});

test('T01增强 time-period.js：6时段判定', () => {
  const TP = require('../assets/js/time-period.js');
  var morning = new Date(2026, 9, 23, 7, 0);
  var forenoon = new Date(2026, 9, 23, 9, 0);
  var noon = new Date(2026, 9, 23, 12, 0);
  var afternoon = new Date(2026, 9, 23, 15, 0);
  var evening = new Date(2026, 9, 23, 19, 0);
  var night = new Date(2026, 9, 23, 23, 0);
  assert.strictEqual(TP.judgeTimePeriod(morning, 360, 1080), 'morning');
  assert.strictEqual(TP.judgeTimePeriod(forenoon, 360, 1080), 'forenoon');
  assert.strictEqual(TP.judgeTimePeriod(noon, 360, 1080), 'noon');
  assert.strictEqual(TP.judgeTimePeriod(afternoon, 360, 1080), 'afternoon');
  assert.strictEqual(TP.judgeTimePeriod(evening, 360, 1080), 'evening');
  assert.strictEqual(TP.judgeTimePeriod(night, 360, 1080), 'night');
});

test('T01增强 CSS：分栏布局与时段动画', () => {
  assert.ok(commonCss.includes('split-active'));
  assert.ok(commonCss.includes('.page--home'));
  assert.ok(commonCss.includes('.timeline-pane'));
  assert.ok(commonCss.includes('.timeline-iframe'));
  assert.ok(commonCss.includes('@keyframes tea-morning'));
  assert.ok(commonCss.includes('@keyframes tea-forenoon'));
  assert.ok(commonCss.includes('@keyframes tea-noon'));
  assert.ok(commonCss.includes('@keyframes tea-afternoon'));
  assert.ok(commonCss.includes('@keyframes tea-evening'));
  assert.ok(commonCss.includes('@keyframes tea-night'));
  assert.ok(commonCss.includes('[data-timeperiod="morning"]'));
  assert.ok(commonCss.includes('[data-timeperiod="night"]'));
});

test('T01增强 index.html：分栏DOM与脚本引入', () => {
  assert.ok(indexHtml.includes('id="timeline-pane"'));
  assert.ok(indexHtml.includes('id="timeline-iframe"'));
  assert.ok(indexHtml.includes('id="home-back"'));
  assert.ok(indexHtml.includes('time-period.js'));
  assert.ok(indexHtml.includes('split-active'));
});

test('T01增强 weather-visual.js：日出日落请求', () => {
  assert.ok(weatherVisualJs.includes('daily=sunrise,sunset'));
  assert.ok(weatherVisualJs.includes('TimePeriod'));
  assert.ok(weatherVisualJs.includes('applySunData'));
});
test('V4 T01 左侧栏：sidebar.js 关键函数与常量', () => {
  assert.ok(sidebarJs.includes('MODULE_ENTRIES'));
  assert.ok(sidebarJs.includes('renderSidebar'));
  assert.ok(sidebarJs.includes('switchModule'));
  assert.ok(sidebarJs.includes('toggleDrawer'));
  assert.ok(sidebarJs.includes('recommend'));
  assert.ok(sidebarJs.includes('plots'));
  assert.ok(sidebarJs.includes('calendar'));
  assert.ok(sidebarJs.includes('disease'));
  assert.ok(sidebarJs.includes('my-crops'));
  assert.ok(sidebarJs.includes('service'));
});

test('V4 T01 左侧栏：sidebar.css 样式', () => {
  assert.ok(sidebarCss.includes('.sidebar'));
  assert.ok(sidebarCss.includes('.sidebar-item'));
  assert.ok(sidebarCss.includes('.sidebar-item--active'));
  assert.ok(sidebarCss.includes('.sidebar-burger'));
  assert.ok(sidebarCss.includes('.content-pane'));
});

test('V4 T01 主页全屏化：data-layout + app-main', () => {
  assert.ok(indexHtml.includes('data-layout="app"'));
  assert.ok(indexHtml.includes('class="app-main"'));
  assert.ok(commonCss.includes('body[data-layout="app"]'));
  assert.ok(commonCss.includes('background-image: none'));
});

test('V4 T01 夜间动画：夜晚SVG + data-daynight', () => {
  assert.ok(sunnyNightSvg.includes('<svg'));
  assert.ok(rainNightSvg.includes('<svg'));
  assert.ok(cloudyNightSvg.includes('<svg'));
  assert.ok(snowNightSvg.includes('<svg'));
  assert.ok(weatherVisualJs.includes('judgeDayNight'));
  assert.ok(weatherVisualJs.includes('data-daynight'));
  assert.ok(commonCss.includes('[data-daynight="night"]'));
});

test('V4 T01 加载门控：loading-gate + weather-ready', () => {
  assert.ok(indexHtml.includes('id="loading-gate"'));
  assert.ok(indexHtml.includes('loading-gate-spinner'));
  assert.ok(indexHtml.includes('weather-ready'));
  assert.ok(commonCss.includes('#loading-gate'));
  assert.ok(commonCss.includes('@keyframes spin'));
  assert.ok(weatherVisualJs.includes('notifyReady'));
  assert.ok(weatherVisualJs.includes('CustomEvent'));
});

test('V4 T01 index.html：sidebar + content-pane 引入', () => {
  assert.ok(indexHtml.includes('sidebar.css'));
  assert.ok(indexHtml.includes('sidebar.js'));
  assert.ok(indexHtml.includes('id="sidebar"'));
  assert.ok(indexHtml.includes('id="sidebar-nav"'));
  assert.ok(indexHtml.includes('data-module="calendar"'));
  assert.ok(indexHtml.includes('id="module-placeholder"'));
});
test('V4 T12 my-crops-store.js：存储模块CRUD', () => {
  assert.ok(myCropsStoreJs.includes('agritime.my-crops.v1'));
  assert.ok(myCropsStoreJs.includes('MAX = 200'));
  assert.ok(myCropsStoreJs.includes('function list'));
  assert.ok(myCropsStoreJs.includes('function add'));
  assert.ok(myCropsStoreJs.includes('function update'));
  assert.ok(myCropsStoreJs.includes('function remove'));
  assert.ok(myCropsStoreJs.includes('function clear'));
  assert.ok(myCropsStoreJs.includes('function getById'));
});

test('V4 T12 my-crops.js：交互逻辑', () => {
  assert.ok(myCropsJs.includes('GERMINATION_OPTIONS'));
  assert.ok(myCropsJs.includes('VIGOR_OPTIONS'));
  assert.ok(myCropsJs.includes('renderList'));
  assert.ok(myCropsJs.includes('renderCard'));
  assert.ok(myCropsJs.includes('showForm'));
  assert.ok(myCropsJs.includes('agritime:sowing-confirmed'));
});

test('V4 T12 crop-photo.js：拍照复用层', () => {
  assert.ok(cropPhotoJs.includes('PhotoCapture'));
  assert.ok(cropPhotoJs.includes('analyze'));
  assert.ok(cropPhotoJs.includes('confirmAndFill'));
});

test('V4 T12 my-crops.css：样式', () => {
  assert.ok(myCropsCss.includes('.my-crops-pane'));
  assert.ok(myCropsCss.includes('.my-crops-card'));
  assert.ok(myCropsCss.includes('.my-crops-form'));
  assert.ok(myCropsCss.includes('.my-crops-empty'));
});

test('V4 T12 index.html：我的作物引入与content-pane', () => {
  assert.ok(indexHtml.includes('my-crops.css'));
  assert.ok(indexHtml.includes('my-crops-store.js'));
  assert.ok(indexHtml.includes('my-crops.js'));
  assert.ok(indexHtml.includes('crop-photo.js'));
  assert.ok(indexHtml.includes('data-module="my-crops"'));
  assert.ok(indexHtml.includes('id="my-crops-pane"'));
  assert.ok(indexHtml.includes('id="my-crops-list"'));
});

test('V4 T12 sidebar.js：我的作物板块已实现', () => {
  assert.ok(sidebarJs.includes("'my-crops'"));
  assert.ok(sidebarJs.includes('implemented: true'));
});
test('V4 地图标点：location-picker.js 关键函数', () => {
  assert.ok(locationPickerJs.includes('loadLeaflet'));
  assert.ok(locationPickerJs.includes('function open'));
  assert.ok(locationPickerJs.includes('function close'));
  assert.ok(locationPickerJs.includes('initDisplay'));
  assert.ok(locationPickerJs.includes('validateLatLon'));
  assert.ok(locationPickerJs.includes('reverseGeocode'));
  assert.ok(locationPickerJs.includes('searchGeocode'));
  assert.ok(locationPickerJs.includes('placeMarker'));
  assert.ok(locationPickerJs.includes('degrade'));
});

test('V4 地图标点：Leaflet CDN + OSM 瓦片', () => {
  assert.ok(locationPickerJs.includes('leaflet'));
  assert.ok(locationPickerJs.includes('openstreetmap'));
  assert.ok(locationPickerJs.includes('nominatim'));
});

test('V4 地图标点：location-picker.css 样式', () => {
  assert.ok(locationPickerCss.includes('.map-overlay'));
  assert.ok(locationPickerCss.includes('.map-panel'));
  assert.ok(locationPickerCss.includes('.map-canvas'));
  assert.ok(locationPickerCss.includes('.map-picker-btn'));
  assert.ok(locationPickerCss.includes('.timeline-map-container'));
});

test('V4 地图标点：index.html 集成', () => {
  assert.ok(indexHtml.includes('location-picker.css'));
  assert.ok(indexHtml.includes('location-picker.js'));
  assert.ok(indexHtml.includes('id="map-picker-btn"'));
  assert.ok(indexHtml.includes('__markerLatLon'));
});

test('V4 地图标点：经纬度校验纯函数', () => {
  const LP = require('../components/location-picker/location-picker.js');
  assert.strictEqual(LP.validateLatLon(35.07, 116.31), true);
  assert.strictEqual(LP.validateLatLon(-90, -180), true);
  assert.strictEqual(LP.validateLatLon(91, 0), false);
  assert.strictEqual(LP.validateLatLon(0, 181), false);
  assert.strictEqual(LP.validateLatLon('abc', 'def'), false);
});
// ---------------- T13 病害检查 ----------------
const diseaseJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'disease.js'));
const diseaseCss = read(path.join(CSS, 'disease.css'));

test('T13 病害检查：index.html 集成', () => {
  assert.ok(indexHtml.includes('data-module="disease"'));
  assert.ok(indexHtml.includes('disease.css'));
  assert.ok(indexHtml.includes('disease.js'));
  assert.ok(indexHtml.includes('id="disease-form"'));
  assert.ok(indexHtml.includes('id="disease-text"'));
  assert.ok(indexHtml.includes('id="disease-photo"'));
});

test('T13 病害检查：脚本走接口且显式处理中文与图片标记', () => {
  assert.ok(diseaseJs.includes('/api/disease/detect'));
  assert.ok(diseaseJs.includes('encodeURIComponent'));
  assert.ok(diseaseJs.includes('hasImage'));
});

test('T13 病害检查：样式与无障碍基础', () => {
  assert.ok(diseaseCss.includes('.disease-card'));
  assert.ok(diseaseCss.includes('.disease-bar-fill'));
  assert.ok(diseaseCss.includes('.disease-empty'));
});

test('T13 病害检查：侧栏入口已置为可用', () => {
  const sidebarJs = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));
  assert.ok(/id: 'disease'[^}]*implemented: true/.test(sidebarJs));
});

test('T13 病害库：结构与字段完整', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'disease', 'library.json')));
  assert.ok(Array.isArray(lib.diseases));
  assert.ok(lib.diseases.length >= 10, '病害条目太少：' + lib.diseases.length);
  lib.diseases.forEach((d) => {
    ['id', 'name', 'crops', 'symptoms', 'treatment', 'medicine', 'prevention'].forEach((k) => {
      assert.ok(d[k], d.id + ' 缺字段 ' + k);
    });
    assert.ok(d.symptoms.length >= 3, d.id + ' 症状词条过少');
  });
});

// ---------------- 子功能板块布局 ----------------
test('布局：子功能板块占满内容区（不再是右侧窄条）', () => {
  const sidebarCss = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.css'));
  const paneRule = sidebarCss.split('.content-pane {')[1].split('}')[0];
  assert.ok(paneRule.includes('flex: 1'), '.content-pane 必须是 flex:1，否则会收缩成内容宽度');
  assert.ok(commonCss.includes('.pane-mode'), '缺少 pane-mode 让日历主体让位的规则');
  assert.ok(commonCss.includes('body[data-layout="app"] .content-pane'), '缺少板块占满内容区的规则');
});

test('布局：切板块时同步切换 pane-mode', () => {
  const sidebarJs = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));
  assert.ok(sidebarJs.includes("classList.toggle('pane-mode'"));
  assert.ok(sidebarJs.includes('openParentOf'), '子板块应自动展开父级菜单');
});

test('布局：病害检查为两列表单+结果', () => {
  assert.ok(indexHtml.includes('class="disease-body"'));
  assert.ok(diseaseCss.includes('.disease-body'));
  assert.ok(/\.disease-body\s*\{[^}]*grid-template-columns/.test(diseaseCss));
});

test('布局：全局 .hidden 工具类存在', () => {
  assert.ok(commonCss.includes('.hidden { display: none !important; }'));
});

test('布局：日历模式下板块不占位（否则与 .app-main 平分宽度，首页被挤窄）', () => {
  assert.ok(
    commonCss.includes('body[data-layout="app"]:not(.pane-mode) .content-pane'),
    '缺少日历模式下隐藏 content-pane 的规则'
  );
});

// ---------------- 地址解析（双斜杠） ----------------
test('地址解析：任何路径都不会拼出 // 开头的协议相对地址', () => {
  const PageUrl = require('../assets/js/page-url.js');
  const cases = [
    ['/', '/timeline.html'],
    ['//', '/timeline.html'],
    ['///', '/timeline.html'],
    ['', '/timeline.html'],
    ['/index.html', '/timeline.html'],
    ['//index.html', '/timeline.html'],
    ['/pages/', '/pages/timeline.html'],
    ['/pages//', '/pages/timeline.html'],
    ['/pages/index.html', '/pages/timeline.html'],
    ['/app/pages/index.html', '/app/pages/timeline.html']
  ];
  cases.forEach(([input, expected]) => {
    assert.strictEqual(PageUrl.timelineUrl(input), expected, '输入 ' + JSON.stringify(input));
  });
  cases.forEach(([input]) => {
    assert.ok(!PageUrl.timelineUrl(input).startsWith('//'), '不允许出现 // 开头：' + input);
  });
});

test('地址解析：index.html 已接入 page-url.js', () => {
  assert.ok(indexHtml.includes('src="../assets/js/page-url.js"'), '必须是真的 script 标签，不是注释里提到');
  assert.ok(indexHtml.includes('window.PageUrl.timelineUrl'));
});

// ---------------- T15 种植推荐 ----------------
const recommendJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'recommend.js'));
const recommendCss = read(path.join(CSS, 'recommend.css'));

test('T15 种植推荐：index.html 集成', () => {
  assert.ok(indexHtml.includes('data-module="recommend"'));
  assert.ok(indexHtml.includes('recommend.css'));
  assert.ok(indexHtml.includes('recommend.js'));
  assert.ok(indexHtml.includes('id="recommend-form"'));
  assert.ok(indexHtml.includes('class="recommend-body"'));
});

test('T15 种植推荐：脚本走接口并带月份/经纬度参数', () => {
  assert.ok(recommendJs.includes('/api/recommend'));
  assert.ok(recommendJs.includes('month'));
  assert.ok(recommendJs.includes('lat'));
  assert.ok(recommendJs.includes('lon'));
});

test('T15 推荐规则库：结构与字段完整', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'market', 'recommend-rules.json')));
  assert.ok(Array.isArray(lib.crops));
  assert.ok(lib.crops.length >= 20, '推荐作物太少：' + lib.crops.length);
  lib.crops.forEach((c) => {
    ['name', 'sowMonths', 'latRange', 'market', 'reason'].forEach((k) => {
      assert.ok(c[k] !== undefined, c.name + ' 缺字段 ' + k);
    });
    assert.ok(c.sowMonths.length >= 1 && c.sowMonths.every((m) => m >= 1 && m <= 12), c.name + ' 月份非法');
  });
});

// ---------------- T14 客服 ----------------
const serviceJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
const adminJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin.js'));

test('T14 客服：index.html 集成（service/admin 脚本与样式）', () => {
  assert.ok(indexHtml.includes('service.js'));
  assert.ok(indexHtml.includes('admin.js'));
  assert.ok(indexHtml.includes('service.css'));
  assert.ok(indexHtml.includes('admin.css'));
  assert.ok(indexHtml.includes('data-module="service"'));
});

test('T14 客服：用户端走 /api/support 接口', () => {
  assert.ok(serviceJs.includes('/api/support'));
  assert.ok(serviceJs.includes('/ticket'));
  assert.ok(serviceJs.includes('/reply'));
});

test('T14 客服：超管端复用账号系统而非独立密码登录', () => {
  assert.ok(adminJs.includes('/api/auth/me'));
  assert.ok(adminJs.includes("user.role === 'admin'"));
  assert.ok(adminJs.includes("'/close'"));
});

// ---------------- T00 共享日期窗口（shared/utils/date-window.js） ----------------
test('T00 共享日期窗口：窗口计算与边界', () => {
  const DW = require('../../shared/utils/date-window.js');
  const today = new Date(2026, 9, 30); // 2026-10-30
  const win = DW.windowFor(today, 2, 10);
  assert.strictEqual(win.minISO, '2024-10-30');
  assert.strictEqual(win.maxISO, '2026-11-09');
  assert.ok(DW.within('2025-06-01', win));
  assert.ok(DW.within('2024-10-30', win), '下界应含当天');
  assert.ok(DW.within('2026-11-09', win), '上界应含当天');
  assert.ok(!DW.within('2024-10-29', win));
  assert.ok(!DW.within('2026-11-10', win));
});

test('T00 共享日期窗口：本地时区格式化不串日期', () => {
  const DW = require('../../shared/utils/date-window.js');
  assert.strictEqual(DW.toISO(new Date(2026, 0, 1)), '2026-01-01');
  assert.strictEqual(DW.toISO(new Date(2026, 11, 31)), '2026-12-31');
  assert.strictEqual(DW.parseISO('2026-02-29'), null, '2026 不是闰年，应判非法');
  assert.ok(DW.parseISO('2024-02-29'), '2024 是闰年');
});

test('T00 共享日期窗口：第 5 天起需标注仅供参考', () => {
  const DW = require('../../shared/utils/date-window.js');
  const today = new Date(2026, 9, 30);
  assert.strictEqual(DW.needsForecastNote(new Date(2026, 10, 3), today), false, '第 4 天不需要');
  assert.strictEqual(DW.needsForecastNote(new Date(2026, 10, 4), today), true, '第 5 天需要');
  assert.strictEqual(DW.needsForecastNote(new Date(2026, 10, 9), today), true);
});

test('T00 共享逻辑已迁到 shared/utils 并被页面引用', () => {
  assert.ok(indexHtml.includes('../shared/utils/storage.js'), 'index 应引用 shared/utils/storage.js');
  assert.ok(indexHtml.includes('../shared/utils/date-window.js'), 'index 应引用 shared/utils/date-window.js');
  assert.ok(timelineHtml.includes('../shared/utils/timeline-logic.js'), 'timeline 应引用 shared/utils/timeline-logic.js');
  assert.ok(!indexHtml.includes('assets/js/history-store.js'), '旧路径不应残留');
});

// ---------------- T06 农事适宜度（土壤/水质参与评分） ----------------
test('T06 适宜度：地块详情请求带上地形参数', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  assert.ok(myPlotsJs.includes('farmingSuitability'), '详情应消费后端适宜度结果');
  assert.ok(/angle=' \+ \(plot\.angle/.test(myPlotsJs), '请求应带 angle');
  assert.ok(/slope=' \+ \(plot\.slope/.test(myPlotsJs), '请求应带 slope');
  assert.ok(/terrace=' \+ \(plot\.terrace \? 1 : 0\)/.test(myPlotsJs), '请求应带 terrace');
  assert.ok(myPlotsJs.includes('renderSuitability'), '应有适宜度卡片渲染');
});

test('T06 适宜度：后端把土壤/水质/地形纳入评分', () => {
  const plotHandler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotHandler.java'));
  ['farmingSuitability', 'soilScore', 'waterScore', 'terrainScore', 'typeScore', 'climateScore'].forEach((k) => {
    assert.ok(plotHandler.includes(k), 'PlotHandler 缺少 ' + k);
  });
  assert.ok(plotHandler.includes('\\"farmingSuitability\\"'), '响应里应输出 farmingSuitability 字段');
  assert.ok(plotHandler.includes('terrace'), '地形评分应识别梯田');
});

test('T06 实测土壤：消费 Open-Meteo 土壤数据并标注来源', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  assert.ok(myPlotsJs.includes('data.soil'), '应消费后端 soil 字段');
  assert.ok(myPlotsJs.includes('土壤墒情（实测）'), '应有实测墒情分区');
  assert.ok(myPlotsJs.includes('Open-Meteo 土壤模型'), '应标注数据来源');
  assert.ok(myPlotsJs.includes('非实地采样'), '土壤类型应标注为规则推断');
});

// ---------------- T06 河川径流量（GloFAS 水文模型） ----------------
test('T06 径流量：后端按 3×3 网格采样并取最大流量', () => {
  const plotHandler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotHandler.java'));
  assert.ok(plotHandler.includes('fetchHydrology'), '应有 fetchHydrology');
  assert.ok(plotHandler.includes('flood-api.open-meteo.com'), '应调用 Flood API');
  assert.ok(plotHandler.includes('HYDRO_TTL_SECONDS'), '水文数据应单独缓存');
  // GloFAS 0.05° 网格对坐标极敏感，必须多点采样，单点会把支流当干流
  assert.ok(/double\[\] offs = \{-0\.05, 0, 0\.05\}/.test(plotHandler), '应做 3×3 偏移采样');
  assert.ok(plotHandler.includes('sampleDistanceKm'), '应返回采样点距离');
  assert.ok(plotHandler.includes('open-meteo.flood(GloFAS)'), '数据来源标注应含 GloFAS');
});

test('T06 径流量：前端渲染流量与逐日柱状图', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  assert.ok(myPlotsJs.includes('renderHydrology'), '应有 renderHydrology');
  assert.ok(myPlotsJs.includes('data.hydrology'), '应消费 hydrology 字段');
  assert.ok(myPlotsJs.includes('plot-hyd-bars'), '应有逐日柱状图');
  assert.ok(myPlotsJs.includes('非水文站断面实测'), '应标注非断面实测');
  const css = read(path.join(CSS, 'my-plots.css'));
  assert.ok(css.includes('.plot-hyd-bars'), '缺少流量柱状图样式');
  assert.ok(css.includes('.plot-hyd-value'), '缺少流量数值样式');
});

// ---------------- T02 作物名称标准化 ----------------
test('T02 别名库：常见作物带真实俗名与拼音，不是空壳', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'alias.json')));
  const byName = {};
  lib.mappings.forEach((m) => { byName[m.displayName] = m; });
  assert.ok(lib.mappings.length >= 400, '作物库应有 400 条');
  const withAlias = lib.mappings.filter((m) => m.aliases.length > 2);
  assert.ok(withAlias.length >= 60, '带真实别名的作物应 ≥60，实际 ' + withAlias.length);
  assert.ok(byName['番茄'].aliases.includes('西红柿'));
  assert.ok(byName['番茄'].pinyin.includes('tomato'));
  assert.ok(byName['马铃薯'].aliases.includes('土豆'));
  assert.ok(byName['玉米'].aliases.includes('苞谷'));
  // 没填别名的条目必须是「只有 canonical + 标准名」，不能是编的
  lib.mappings.forEach((m) => {
    assert.ok(m.aliases.includes(m.canonical), m.displayName + ' 的 aliases 应含 canonical');
    assert.ok(m.aliases.includes(m.displayName), m.displayName + ' 的 aliases 应含标准名');
  });
});

test('T02 后端：CropsHandler 支持 q、返回标准名与命中方式', () => {
  const src = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'CropsHandler.java'));
  assert.ok(src.includes('CropRegistry.search'), '查询应走 CropRegistry');
  assert.ok(src.includes('matchedBy'), '应返回命中方式');
  assert.ok(src.includes('hasProtocol'), '应标识是否能进时间线');
});

test('T02 后端：时间线把俗称归一到标准名', () => {
  const src = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(src.includes('CropRegistry.standardName'), '时间线应归一作物名');
  assert.ok(src.includes('"cropName"'), '响应应回传标准名');
});

test('T02 前端：联想走后端接口，本地匹配仅作兜底', () => {
  assert.ok(indexHtml.includes('/api/crops?q='), '联想应调用 /api/crops?q=');
  assert.ok(indexHtml.includes('localMatches'), '应保留离线兜底');
  assert.ok(timelineHtml.includes('crop-resolved-hint'), '时间线应提示俗称归一结果');
});

// ---------------- T07 作物属性 ----------------
test('T07 属性库：400 种作物全部带周期/年限/熟制，且标注来源', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'properties.json')));
  assert.ok(Array.isArray(lib.crops));
  assert.ok(lib.crops.length >= 400, '属性应覆盖 400 种作物，实际 ' + lib.crops.length);
  lib.crops.forEach((c) => {
    ['cropId', 'displayName', 'growthCycle', 'lifespan', 'croppingSystem', 'totalDays', 'source'].forEach((k) => {
      assert.ok(c[k] !== undefined, c.displayName + ' 缺字段 ' + k);
    });
    assert.ok(['long', 'short'].includes(c.growthCycle), c.displayName + ' growthCycle 非法');
    assert.ok(['annual', 'perennial'].includes(c.lifespan), c.displayName + ' lifespan 非法');
    assert.ok(['single', 'double', 'multi'].includes(c.croppingSystem), c.displayName + ' croppingSystem 非法');
    assert.ok(['manual', 'category-rule'].includes(c.source), c.displayName + ' source 未标注');
  });
  const byName = {};
  lib.crops.forEach((c) => { byName[c.displayName] = c; });
  assert.strictEqual(byName['苹果'].lifespan, 'perennial');
  assert.strictEqual(byName['番茄'].croppingSystem, 'double');
  assert.strictEqual(byName['水稻'].source, 'manual');
});

test('T07 接入：ComputeStub 按属性扩展节点，编排层传纬度', () => {
  const cs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'ComputeStub.java'));
  const orch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'orchestration', 'TimelineOrchestrator.java'));
  const cp = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropProperties.java'));
  assert.ok(cs.includes('CropProperties.expand'), '节点生成应调用属性扩展');
  assert.ok(cs.includes('buildNodes(String cropId, String sowDate, double lat)'), '应支持带纬度调用');
  assert.ok(orch.includes('buildNodes(cropId, sowDate, lat)'), '编排层应传纬度');
  assert.ok(cp.includes('perennial'), '应处理多年生');
  assert.ok(cp.includes('第二季'), '一年多熟应生成第二季节点');
});

test('T07 前端：时间线展示作物属性', () => {
  assert.ok(timelineHtml.includes('crop-props-hint'));
  assert.ok(timelineHtml.includes('data.cropProperties'));
});

// ---------------- T03 农事指导 ----------------
test('T03 指导：后端四类内容随节点下发，浇水按天气调整', () => {
  const gs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'guidance', 'GuidanceService.java'));
  assert.ok(gs.includes('guidance.json'), '应读取 guidance.json');
  assert.ok(gs.includes('"watering"') && gs.includes('"fertilize"') && gs.includes('"pest"') && gs.includes('"tools"'),
    '四类内容都应输出');
  assert.ok(gs.includes('rain >= 10'), '有雨应切换到暂停灌溉');
  assert.ok(gs.includes('rain < 2 && maxTemp >= 30'), '高温少雨应加量灌溉');
  const orch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'orchestration', 'TimelineOrchestrator.java'));
  assert.ok(orch.includes('GuidanceService.build'), '编排层应为每个节点生成指导');
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('"guidance"'), '响应应包含 guidance');
});

test('T03 指导：前端可展开四类内容', () => {
  assert.ok(timelineHtml.includes('renderGuidance'), '应有 renderGuidance');
  assert.ok(timelineHtml.includes('tl-guide-tools'), '应渲染农具');
  assert.ok(timelineHtml.includes('tl-guide-pest'), '应渲染病虫害');
  assert.ok(commonCss.includes('.tl-guide-title'), '缺少指导样式');
});

// ---------------- T04 时间体系 ----------------
test('T04 日期范围：未来 30 天，且不再强制填日期', () => {
  assert.ok(indexHtml.includes('var MAX_FUTURE_DAYS = 30;'), '未来范围应为 30 天');
  assert.ok(indexHtml.includes('今天后 30 天'), '提示文案应同步');
  assert.ok(!indexHtml.includes("dateErr.textContent = '请选择播种日期'"), '不填日期不应报错');
});

test('T04 双模式播种：不填日期按节气推荐', () => {
  assert.ok(indexHtml.includes('/api/sow-window'), '前端应调用播种窗口接口');
  assert.ok(indexHtml.includes('term-sow-btn'), '应有「按节气推荐」按钮');
  const sow = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'SowAdvisor.java'));
  assert.ok(sow.includes('solar-term-crop'), '应优先按作物匹配节气');
  assert.ok(sow.includes('solar-term-season'), '其次按适宜季节匹配');
  assert.ok(sow.includes('nearest-term'), '都不匹配时要标注为兜底');
  const srv = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  assert.ok(srv.includes('/api/sow-window'), '接口应已注册');
});

test('T04 第 5 天起标注仅供参考', () => {
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('forecastNote'), '后端应标注 forecastNote');
  assert.ok(th.includes('plusDays(4)'), '第 5 天起（今天+4 之后的日期）才标注');
  assert.ok(timelineHtml.includes('天气为预测数据，仅供参考'), '前端应显示该标注');
});

test('T04 节点提醒：可设置、可删除、有倒计时', () => {
  assert.ok(timelineHtml.includes('REMIND_KEY'), '提醒应持久化');
  assert.ok(timelineHtml.includes('toggleReminder'), '应可开关提醒');
  assert.ok(timelineHtml.includes('calcCountdown'), '应显示倒计时');
  assert.ok(timelineHtml.includes('reminder-panel'), '应有提醒面板');
});

// ---------------- T09 渔业试点 ----------------
test('T09 渔业：3 个品类且阶段覆盖放苗/投喂/换水/捕捞', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'industries', 'aquaculture', 'species.json')));
  assert.ok(Array.isArray(lib.species));
  assert.ok(lib.species.length >= 3, '至少 3 个品类，实际 ' + lib.species.length);
  const ops = new Set();
  lib.species.forEach((s) => {
    assert.ok(s.name && s.stages.length >= 4, s.name + ' 阶段过少');
    assert.ok(typeof s.waterTempMin === 'number' && typeof s.waterTempMax === 'number', s.name + ' 缺水温区间');
    s.stages.forEach((st) => {
      ops.add(st.opType);
      ['dayOffset', 'opType', 'title', 'detail'].forEach((k) => assert.ok(st[k] !== undefined, s.name + ' 阶段缺 ' + k));
    });
  });
  ['stock', 'feed', 'water', 'harvest'].forEach((k) => assert.ok(ops.has(k), '缺少 ' + k + ' 阶段'));
});

test('T09 渔业：接口注册与水质口径声明', () => {
  const srv = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  assert.ok(srv.includes('/api/industry'), '接口应注册');
  const h = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'IndustryHandler.java'));
  assert.ok(h.includes('tempBasis'), '应声明温度口径');
  assert.ok(h.includes('气温近似'), '应说明是气温近似而非水温实测');
  assert.ok(h.includes('fetchWindowWeather'), '应叠加天气适宜度');
});

test('T09 渔业：前端板块与侧栏入口', () => {
  assert.ok(indexHtml.includes('data-module="industry"'), '应有渔业板块');
  assert.ok(indexHtml.includes('industry.js') && indexHtml.includes('industry.css'));
  const sidebar = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));
  assert.ok(/id: 'industry'[^}]*implemented: true/.test(sidebar), '侧栏入口应可用');
});

// ---------------- T08 方言词表 ----------------
test('T08 方言：三方言区词表，且都映射到标准作物名', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'voice', 'dialect-map.json')));
  assert.ok(Array.isArray(lib.regions));
  assert.ok(lib.regions.length >= 3, '至少 3 个方言区');
  let total = 0;
  lib.regions.forEach((r) => {
    assert.ok(r.id && r.name && Array.isArray(r.provinces) && r.provinces.length, r.id + ' 结构不完整');
    assert.ok(r.words.length >= 8, r.name + ' 词条过少');
    r.words.forEach((w) => {
      assert.ok(w.from && w.to, r.name + ' 词条缺 from/to');
      total++;
    });
  });
  assert.ok(total >= 30, '总词条应 ≥30，实际 ' + total);
});

test('T08 方言：语音结果先纠错再填入，并标注能力边界', () => {
  const voice = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.js'));
  assert.ok(voice.includes('Dialect.correct'), '识别结果应走方言纠错');
  assert.ok(voice.includes('voice-corrected'), '应派发纠错事件');
  const dialect = read(path.resolve(__dirname, '..', 'assets', 'js', 'dialect.js'));
  assert.ok(dialect.includes('方言语音'), '应说明不是方言语音识别');
  assert.ok(indexHtml.includes('dialect.js'));
  assert.ok(indexHtml.includes('voice-hint'));
  assert.ok(indexHtml.includes('文本纠错，非方言语音识别'), '界面应写明能力边界');
});

test('T08 方言：nginx 放行 /data/voice/', () => {
  const conf = read(path.resolve(__dirname, '..', '..', 'deploy', 'nginx', 'agritime.conf'));
  assert.ok(conf.includes('/data/voice/'), '浏览器要读词表，nginx 需放行该目录');
});

// ---------------- T13 病害图片输入 ----------------
test('T13 图片：前端提取颜色特征并随请求发送', () => {
  const d = read(path.resolve(__dirname, '..', 'assets', 'js', 'disease.js'));
  assert.ok(d.includes('extractColorFeatures'), '应有颜色特征提取');
  assert.ok(d.includes('getImageData'), '应读取像素');
  assert.ok(d.includes("'&brown='"), '应把褐斑占比传给后端');
  assert.ok(d.includes("'&yellow='"), '应把黄化占比传给后端');
  assert.ok(d.includes("'&white='"), '应把白粉占比传给后端');
});

test('T13 图片：后端按颜色特征排序并如实标注方法', () => {
  const h = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'DiseaseHandler.java'));
  assert.ok(h.includes('dominantFeature'), '应判定主导颜色特征');
  assert.ok(h.includes('hintBonus'), '应按症状词加分');
  assert.ok(h.includes('color-feature'), '应返回识别方法');
  assert.ok(h.includes('不是训练模型识别'), '应说明不是模型识别');
  assert.ok(!h.includes('"imageUsed", "false"'), 'imageUsed 不应写死 false');
});
