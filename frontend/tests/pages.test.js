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
const timelineLogicJs = read(path.resolve(__dirname, '..', '..', 'shared', 'utils', 'timeline-logic.js'));
const historyStoreJs = read(path.resolve(__dirname, '..', '..', 'shared', 'utils', 'storage.js'));
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
test('时间线页：函数图像模块已下线', () => {
  assert.ok(!timelineHtml.includes('mode-switch'), '不应再有「时间节点/函数图像」切换条');
  assert.ok(!timelineHtml.includes('data-mode="chart"'), '不应再有函数图像模式');
  assert.ok(!timelineHtml.includes('CHART_CONFIG'), '图表常量应随模块一起删除');
  assert.ok(!timelineHtml.includes('id="chart"'), '不应再有图表容器');
  assert.ok(!timelineHtml.includes('chart-client.js'), '不应再加载图表数据客户端');
  assert.ok(!timelineHtml.includes('createElementNS'), '不再用原生 SVG 画曲线');
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

test('性能：规程生成等待窗口覆盖大模型耗时，不再 15 秒误报超时', () => {
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  const compute = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'ComputeStub.java'));

  // 前端：总等待窗口 ≥ 60s（大模型生成约 30~40s），且有「已等待 N 秒」进度提示
  assert.match(timelineHtml, /MAX_WAIT_MS\s*=\s*(6[0-9]|[7-9][0-9])000/, '总等待窗口应覆盖 30~40s 的生成耗时');
  assert.ok(!/tries\s*<\s*3/.test(timelineHtml), '不应再只重试 3 次就放弃（15s 必超时）');
  assert.ok(timelineHtml.includes('已等待'), '生成期间应有已等待秒数的进度提示');

  // 后端：已有生成任务时立即 202，不再每次轮询都阻塞
  assert.ok(handler.includes('isGenerating(buildCropId)'), '后端应能识别「正在生成」');
  assert.ok(handler.includes('writeGenerating'), '202 响应应抽成统一方法');
  assert.match(compute, /LLM_WAIT_MS\s*=\s*1000/, '单次等待应降到 1s，避免首个 202 白拖 2.5s');
  // 预热的缓存键必须与真实请求一致，且先读磁盘缓存，避免重启把预置作物重新生成一遍
  assert.ok(compute.includes('CropRegistry.timelineCropId'), '预热缓存键应与真实请求归一逻辑一致');
  assert.ok(/ensureLlmCacheLoaded\(\);\s*\n\s*int ok/.test(compute), '预热前应先加载磁盘缓存，避免重启重复生成');

  // 首屏：leaflet 改为按需加载（location-picker 注入），不再首屏加载约 160KB
  assert.ok(!indexHtml.includes('assets/leaflet/leaflet.js'), '首页不应再同步加载 leaflet.js');
  assert.ok(!indexHtml.includes('assets/leaflet/leaflet.css'), '首页不应再同步加载 leaflet.css');
  assert.ok(indexHtml.includes('warmCropProtocol'), '选中作物后应后台预热规程，减少点生成后的干等');
});

test('速度：常见作物进本地规程库（免大模型），且带来源与待复核标记', () => {
  const p = JSON.parse(read(path.resolve(__dirname, '..', '..', 'backend', 'crop-protocols.json')));
  const expect = ['番茄', '黄瓜', '辣椒', '茄子', '大白菜', '马铃薯', '萝卜', '西瓜'];
  const byStd = {};
  p.crops.forEach((c) => { if (c.standardName) byStd[c.standardName] = c; });

  expect.forEach((name) => {
    const c = byStd[name];
    assert.ok(c, '规程库应包含 ' + name);
    const nodes = c.nodes || [];
    assert.ok(nodes.length >= 6 && nodes.length <= 10, name + ' 节点数应为 6~10');
    let last = -1;
    nodes.forEach((n) => {
      assert.ok(['sowing', 'irrigation', 'fertilize', 'pesticide', 'harvest'].includes(n.opType), name + ' opType 非法');
      assert.ok(n.dayOffset > last, name + ' dayOffset 应严格递增');
      last = n.dayOffset;
    });
    assert.ok(c.source && c.source.length > 10, name + ' 应写明数据来源（可溯源）');
    assert.ok(c.reviewStatus === 'pending_review', name + ' 公开资料整理应标 pending_review');
  });

  const llm = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'LlmService.java'));
  assert.ok(!llm.includes('"desc":"描述"'), '提示词不应再要求 desc（解析层不读，纯属白生成）');
  assert.ok(llm.includes('temperature') && llm.includes('0.3'), '温度应降到 0.3，输出更稳定');
});

test('农艺判断：精确播期 + 霜期/越冬拦截，不排出冬天里的夏天作物', () => {
  const props = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'properties.json')));
  const want = {
    '番茄': [1, 2, 3, 7, 8],
    '黄瓜': [3, 4, 5, 6, 7, 8],
    '辣椒': [3, 4, 5],
    '茄子': [3, 4, 5],
    // V5.4 按华北物候收敛：大白菜/秋萝卜都是立秋前后（8 月），
    // 原来的 7–8 / 8–9 是长江流域口径，在华北会排出霜后收获
    '大白菜': [8],
    '马铃薯': [2, 3, 8],
    '萝卜': [8],
    '西瓜': [4, 5]
  };
  const byName = {};
  (props.crops || []).forEach((c) => { byName[c.displayName] = c; });
  Object.keys(want).forEach((name) => {
    const c = byName[name];
    assert.ok(c, '属性库应有 ' + name);
    assert.deepStrictEqual(c.sowMonths, want[name], name + ' 应有精确播期');
    assert.ok(!c.sowMonths.includes(11), name + ' 不应把 11 月列为露地播期');
  });
  assert.ok(byName['黄瓜'].tempRequirement.includes('喜温'), '黄瓜应被识别为喜温作物（原描述只有"喜湿"漏检）');

  const check = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowCheckHandler.java'));
  assert.ok(check.includes('props.hasSowMonths()'), '播季校验应优先用精确播期');
  assert.ok(check.includes('overwinter-risk'), '应有霜期/越冬拦截');
  assert.ok(check.includes('收获期落在常年霜期内'), '应明确提示收获期撞霜');
  assert.ok(!check.includes('sow.getMonth() + " 月"'), '提示里不应输出英文月份名');

  const cp = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropProperties.java'));
  assert.ok(cp.includes('winterRisk'), '第二季应判断是否会跨入霜期');
  assert.ok(cp.includes('String sowDate'), 'expand 应接收播期以判断第二季是否越冬');

  const zone = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'AgroZone.java'));
  assert.ok(zone.includes('夜温低于'), '喜温判定应覆盖"夜温低于"这类描述');
});

test('农艺逻辑：大蒜/小麦补足越冬期，水稻从播种起算', () => {
  const p = JSON.parse(read(path.resolve(__dirname, '..', '..', 'backend', 'crop-protocols.json')));
  const byId = {};
  p.crops.forEach((c) => { byId[c.cropId] = c; });

  const garlic = byId['dry_garlic'];
  assert.ok(garlic.nodes[garlic.nodes.length - 1].dayOffset >= 220, '大蒜应越冬到次年 5~6 月（≥220 天）');
  const garlicGaps = garlic.nodes.slice(1).map((n, i) => n.dayOffset - garlic.nodes[i].dayOffset);
  assert.ok(Math.max.apply(null, garlicGaps) >= 80, '大蒜应有 80 天以上的越冬空档（封冻到返青）');

  const wheat = byId['wheat_winter'];
  assert.ok(wheat.nodes[wheat.nodes.length - 1].dayOffset >= 240, '冬小麦应到次年 6 月收获（≥240 天）');

  const rice = byId['rice'];
  assert.ok(rice.nodes[0].title.indexOf('播种') !== -1, '水稻第 0 天应是播种育秧，而不是插秧');
  assert.ok(rice.nodes.some((n) => n.title.indexOf('插秧') !== -1), '水稻应有插秧节点');

  const props = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'properties.json')));
  const byName = {};
  props.crops.forEach((c) => { byName[c.displayName] = c; });
  ['大蒜', '小麦', '玉米', '水稻', '大豆'].forEach((name) => {
    assert.ok(byName[name] && Array.isArray(byName[name].sowMonths) && byName[name].sowMonths.length,
      name + ' 应有精确播期');
  });
  assert.ok(byName['大蒜'].totalDays >= 220, '大蒜 totalDays 应≥220');
  assert.ok(byName['小麦'].totalDays >= 240, '小麦 totalDays 应≥240');
  assert.ok(byName['水稻'].suitableRegion.indexOf('华北') !== -1, '水稻产区应覆盖华北（小站稻）');
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

test('流程对齐：reminders 缺失容错', () => {
  // 节点区重绘后改为从 cachedData 取 reminders，容错口径不变
  assert.match(timelineHtml, /Array\.isArray\((data|cachedData)\.reminders\)/);
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

test('时间线页：不再请求图表接口', () => {
  assert.ok(!timelineHtml.includes('/api/chart'), '函数图像已下线，页面不应再请求图表接口');
  assert.ok(!timelineHtml.includes("method: 'POST'"), '时间线页只发 GET');
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

// ---------------- T14 反馈 ----------------
const serviceJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
const adminJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin.js'));

test('T14 反馈：index.html 集成（service/admin 脚本与样式）', () => {
  assert.ok(indexHtml.includes('service.js'));
  assert.ok(indexHtml.includes('admin.js'));
  assert.ok(indexHtml.includes('service.css'));
  assert.ok(indexHtml.includes('admin.css'));
  assert.ok(indexHtml.includes('data-module="service"'));
});

test('T14 反馈：用户端走 /api/support 接口', () => {
  assert.ok(serviceJs.includes('/api/support'));
  assert.ok(serviceJs.includes('/ticket'));
  assert.ok(serviceJs.includes('/reply'));
});

test('T14 反馈：超管端复用账号系统而非独立密码登录', () => {
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
  // 倾角超过 90° 只可能是坡向被误存，请求里要归零，不能拿去打分
  assert.ok(/angle=' \+ \(Math\.abs\(plot\.angle/.test(myPlotsJs), '请求应带 angle 并过滤超 90° 的历史值');
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
  // T17 起判定口径改为"未来 3 天 + 前 3 天"，不再是整个窗口平均
  assert.ok(gs.includes('rainNext >= 10'), '未来 3 天有雨应切换到暂停灌溉');
  assert.ok(gs.includes('rainNext < 1 && rainPrev < 2'), '连续无雨应加量补灌');
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

test('T09 渔业：独立板块与地块内嵌并存，品种只认后端', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const industryJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'industry.js'));
  // 独立板块：侧栏入口 + 页内面板 + 资源都要在
  assert.ok(indexHtml.includes('data-module="industry"'), '应有独立的渔业板块面板');
  assert.ok(/id: 'industry'/.test(sidebarJs), '侧栏应有渔业入口');
  assert.ok(indexHtml.includes('assets/js/industry.js'), '首页应引入 industry.js');
  assert.ok(indexHtml.includes('assets/css/industry.css'), '首页应引入 industry.css');
  // 地块内嵌的养殖日历不能因为独立板块而消失，两边共用同一套规程接口
  assert.ok(myPlotsJs.includes('pond-calendar'), '养殖日历应嵌在地块详情里');
  assert.ok(myPlotsJs.includes('/api/industry/timeline'), '养殖日历复用同一套规程接口');
  assert.ok(myPlotsJs.includes('loadSpecies'), '鱼种应读后端品种库，不在前端硬编码');
  // 独立板块同样以接口为准：必须请求 /species，内置表只能当兜底
  assert.ok(industryJs.includes("'/species'"), '板块应请求后端品种库');
  assert.ok(!/var\s+SPECIES_50\s*=[^;]*;\s*renderSpecies\(/.test(industryJs), '不应跳过接口直接用内置表渲染');
});

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

// ---------------- T11 小程序端 ----------------
test('T11 小程序：结构完整且复用同一套接口', () => {
  const root = path.resolve(__dirname, '..', '..', 'miniprogram');
  const appJson = JSON.parse(read(path.join(root, 'app.json')));
  assert.ok(Array.isArray(appJson.pages) && appJson.pages.length >= 2, '至少两页');
  assert.ok(appJson.pages.includes('pages/index/index'));
  assert.ok(appJson.pages.includes('pages/timeline/timeline'));

  const apiJs = read(path.join(root, 'utils', 'api.js'));
  assert.ok(apiJs.includes("'/api/timeline'"), '应复用 /api/timeline');
  assert.ok(apiJs.includes("'/api/sow-window'"), '应复用 /api/sow-window');
  assert.ok(!/https?:\/\/115\.175/.test(apiJs), '不硬编码服务器 IP');

  ['pages/index/index.wxml', 'pages/timeline/timeline.wxml', 'pages/timeline/timeline.wxss'].forEach((f) => {
    assert.ok(read(path.join(root, f)).length > 100, f + ' 内容过少');
  });
  assert.ok(read(path.join(root, 'README.md')).includes('AppID'), 'README 应说明 AppID 前置条件');
});

test('T11 共享逻辑：小程序通过脚本同步 shared/utils，而不是复制一份', () => {
  const sync = read(path.resolve(__dirname, '..', '..', 'miniprogram', 'scripts', 'sync-shared.js'));
  assert.ok(sync.includes('shared'), '同步脚本应读取 shared/utils');
  assert.ok(sync.includes('date-window.js') && sync.includes('timeline-logic.js'));
});

// ---------------- 缺陷修复（V5 前置） ----------------
test('修复：匿名工单身份不可伪造，且同 IP 不串号', () => {
  const h = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SupportHandler.java'));
  assert.ok(h.includes('X-Real-IP'), '应优先用 nginx 写入的 X-Real-IP');
  assert.ok(h.includes('X-Visitor-Id'), '应支持浏览器访客 ID');
  assert.ok(!/fwd\.split\(","\)\[0\]/.test(h), '不能再取 XFF 第一段（客户端可伪造）');
  assert.ok(h.includes('parts[parts.length - 1]'), '应取 XFF 最后一段');
  const svc = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
  assert.ok(svc.includes('agritime.visitorId'), '前端应生成并持久化访客 ID');
  assert.ok(svc.includes("'X-Visitor-Id'"), '请求应带访客 ID');
});

test('修复：一年多熟第二季接在第一季之后，不重叠', () => {
  const cp = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropProperties.java'));
  assert.ok(cp.includes('int shift = span + gap'), '第二季应按整个生育期平移');
  assert.ok(!cp.includes('span / 2'), '不能再用半个生育期');
});

test('修复：未知作物不会长时间占用线程', () => {
  const cs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'ComputeStub.java'));
  assert.ok(cs.includes('LLM_NEGATIVE'), '应缓存生成失败的作物');
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('作物名称过长'), '应拦截超长作物名');
});

test('修复：远期节点只给时段气候概览，不再逐日假精度', () => {
  const orch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'orchestration', 'TimelineOrchestrator.java'));
  // 旧实现拿去年同一天的天气给远期节点打分，产出的"某日有雨"是假精度，已删除
  assert.ok(!orch.includes('minusYears(1)'), '不应再用去年同日逐日补位');
  assert.ok(orch.includes('FORECAST_HORIZON_DAYS'), '应有预报可信期分界');
  assert.ok(orch.includes('"season"'), '远期节点应标 precision=season');
  assert.ok(orch.includes('periodOf'), '远期结论应给到"上/中/下旬"这种粒度');
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('"precision"'), '应下发 precision 字段');
  assert.ok(th.includes('"outlook"'), '应下发常年气候概览');
  assert.ok(timelineHtml.includes('tl-season-chip'), '远期节点应有独立标记');
  assert.ok(timelineHtml.includes('tl-outlook'), '远期节点应展示常年概览');
});

test('修复：提醒键用标准作物名，过期提醒自动清理', () => {
  assert.ok(timelineHtml.includes('__cropStandardName'), '提醒键应使用标准名');
  assert.ok(timelineHtml.includes('pruneReminders'), '应清理过期提醒');
});

test('修复：大模型生成不再阻塞请求，返回 202 让前端重试', () => {
  const cs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'ComputeStub.java'));
  assert.ok(cs.includes('LLM_WAIT_MS'), '应有最大等待时间');
  assert.ok(cs.includes('isGenerating'), '应能区分"生成中"与"不支持"');
  assert.ok(cs.includes('PENDING'), '同一作物并发只触发一次生成');
  assert.ok(cs.includes('persistLlmCache'), '生成结果应落盘');
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('20201'), '生成中应返回 202');
  // 202 分支与「等了多少秒」的提示都在时间线自己的加载态里；
  // 原断言借的是函数图像那句「正在生成图像…」，模块下线后不能再依赖它
  assert.ok(timelineHtml.includes('res.status === 202'), '前端应识别 202 并继续轮询');
  assert.ok(/正在为该作物生成农事规程|正在生成/.test(timelineHtml), '前端应告诉用户规程正在生成');
});

test('落地：我的作物联动去重、拍照留痕、来源可见', () => {
  assert.ok(myCropsJs.includes('var dup'), '重复确认应查重，不能堆多条');
  assert.ok(myCropsJs.includes('photoHistory: history'), '拍照结果应写入 photoHistory');
  assert.ok(myCropsJs.includes("source: 'calendar'"), '来自日历的记录应标来源');
  assert.ok(myCropsJs.includes('my-crops-source'), '卡片应显示来源徽标');
  assert.ok(myCropsJs.includes('MyPlotsStore'), '地块应显示名称而非裸 ID');
  const cp = read(path.resolve(__dirname, '..', 'components', 'crop-photo', 'crop-photo.js'));
  assert.ok(cp.includes('confidence'), '拍照结果应透传置信度');
  assert.ok(photoJs.includes('onConfirm(crop, c.confidence)'), '候选确认应带置信度');
  assert.ok(timelineHtml.includes('detail.plotId = query.plotId'), 'plotId 应传给父页面');
});

test('落地：作物状态与地块空间分析接真实接口', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  assert.ok(myCropsJs.includes('/api/crop-status'), '作物卡片应拉取真实生长状态');
  assert.ok(myCropsJs.includes('STATUS_CACHE'), '同作物同播期应复用状态，避免重复请求');
  assert.ok(myCropsJs.includes('d.adjustedProgressPct'), '应展示积温校正后的进度而非纯日历进度');
  assert.ok(myCropsCss.includes('my-crops-status-bar'), '状态条样式应存在');
  assert.ok(myPlotsJs.includes('/api/plots/analyze'), '地块应调用空间分析接口');
  assert.ok(myPlotsJs.includes('suitableCrops'), '分析预览应展示后端推荐作物');
  assert.ok(myPlotsJs.includes('reanalyzePlot'), '已保存地块应能重新分析');
  assert.ok(myPlotsJs.includes('plot-analysis-preview'), '框选后应有分析结果预览');
});

test('落地：鱼塘水质与投喂面板接真实计算', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const myPlotsCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'my-plots.css'));
  assert.ok(myPlotsJs.includes('/api/pond-status'), '鱼塘应调用水质状态接口');
  assert.ok(myPlotsJs.includes('renderPondPanel'), '塘类地块应有独立水质面板');
  assert.ok(myPlotsJs.includes('pond-do'), '应允许用户填入实测溶氧覆盖估算');
  assert.ok(myPlotsJs.includes('pond-risk--'), '风险应按等级区分展示');
  assert.ok(myPlotsCss.includes('.pond-metric'), '水质指标样式应存在');
});

test('V5.3 日历：播种校验有阻断能力', () => {
  const sc = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowCheckHandler.java'));
  assert.ok(sc.includes('/api/sow-check') || sc.includes('SowCheckHandler'), '应有播种校验接口');
  assert.ok(sc.includes('"block"'), '应能输出阻断级判定');
  assert.ok(sc.includes('suggestedSowDate'), '阻断时应给替代日期');
  assert.ok(sc.includes('region-mismatch') && sc.includes('season-mismatch'), '区域与播季都要校验');
  assert.ok(sc.includes('sow-in-frost'), '播期落在霜期应阻断');
  assert.ok(indexHtml.includes('/api/sow-check'), '前端生成前应调用校验');
  assert.ok(indexHtml.includes('checkSowThenGo'), '阻断时不应跳到日历');
});

test('V5.3 评分：七因子且权重归一', () => {
  const pa = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotAnalyzeHandler.java'));
  ['terrain', 'soil', 'climate', 'light', 'wind', 'shape', 'drainage'].forEach(k => {
    assert.ok(pa.includes('"' + k + '"') || pa.includes('x.key = "' + k + '"'), '应有因子 ' + k);
  });
  assert.ok(pa.includes('shortwave_radiation_sum'), '光照用真实短波辐射');
  assert.ok(pa.includes('wind_speed_10m_max'), '风害用最大风速');
  assert.ok(pa.includes('moistureSource'), '实测与模型要区分来源');
});

test('V5.3 传感器：实测覆盖估算且进缓存键', () => {
  const store = read(path.resolve(__dirname, '..', 'assets', 'js', 'sensors-store.js'));
  assert.ok(store.includes('measuredParams'), '应能把实测值整理成接口参数');
  assert.ok(store.includes('isStale'), '读数过期要识别');
  const pa = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotAnalyzeHandler.java'));
  assert.ok(pa.includes('fmtKey(measuredMoisture)'), '实测值必须进缓存键');
  assert.ok(indexHtml.includes('sensors-store.js'), '页面应引入设备存储');
});

test('V5.3 去重：共享层已抽取', () => {
  const hj = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'util', 'HttpJson.java'));
  assert.ok(hj.includes('public static Map<?, ?> getMap'), '应有统一的上游抓取');
  const az = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'AgroZone.java'));
  assert.ok(az.includes('regionMatches') && az.includes('sowMonths'), '区划与播季应集中');
  const sc = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'aqua', 'SpeciesCatalog.java'));
  assert.ok(sc.includes('public static Map<String, Object> find'), '鱼种应单一数据源');
  const pond = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PondStatusHandler.java'));
  // 只允许保留单条兜底（查不到品种时用草鱼这档保守值），不允许整张表
  ['南美白对虾', '罗非鱼', '河蟹', '青鱼'].forEach(n => {
    assert.ok(!pond.includes('"' + n + '"'), '不应再硬编码鱼种「' + n + '」');
  });
});

test('V5 任务书：15 项 T16–T30 全量留档并标注状态', () => {
  const tb = read(path.resolve(__dirname, '..', '..', 'docs', 'taskbook-v5.md'));
  assert.ok(tb.includes('**任务编号：T16**'), '首个任务应为 T16，与 V4 的 T00-T15 续接');
  assert.ok(tb.includes('**任务编号：T30**'), '末项应为 T30');
  const ids = tb.match(/\*\*任务编号：T\d+\*\*/g) || [];
  assert.equal(ids.length, 15, '应有 15 个任务（T16-T30）');
  const nums = ids.map(x => parseInt(x.replace(/\D/g, ''), 10));
  nums.forEach((n, i) => assert.equal(n, 16 + i, '任务编号应从 T16 连续递增'));
  ['任务描述', '验收标准', '对应目录'].forEach(k => {
    assert.ok(tb.includes('**' + k + '**') || tb.includes('- **' + k + '**'), '每个任务应含「' + k + '」');
  });
  assert.ok(tb.includes('执行状态总览'), '应有执行状态总览章节');
  assert.ok(tb.includes('15 项全部完成'), '总览应写明 15 项全部完成');
  assert.ok(tb.includes('**状态**'), '每项都应标注状态（已完成/部分完成）');
  assert.ok(tb.includes('## 四、三人分工建议'), '应保留分工章节');
  assert.ok(tb.includes('## 五、开发与认领规则'), '应保留认领规则');
  assert.ok(tb.includes('## 六、最终验收标准'), '应保留最终验收');
  const prio = tb.match(/\*\*优先级\*\*：P\d/g) || [];
  assert.equal(prio.length, 15, '每个任务都应有优先级');
});

test('V5 T16 推荐：地点真参与计算，地块真参与打分', () => {
  const rh = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'RecommendHandler.java'));
  assert.ok(rh.includes('locationSource'), '应接收地点来源，区分解析成功与默认地点');
  assert.ok(rh.includes('"resolved"'), '应回传地点是否解析成功');
  assert.ok(rh.includes('plotCrops') && rh.includes('parsePlotCrops'), '应接收地块适宜作物');
  assert.ok(rh.includes('"plotMatch"'), '应标出是否匹配所选地块');
  assert.ok(rh.includes('"reasons"'), '应给出逐条推荐依据');
  assert.ok(rh.includes('Math.min(100, s)'), '总分应封顶，避免出现 111 分');

  const js = read(path.resolve(__dirname, '..', 'assets', 'js', 'recommend.js'));
  assert.ok(js.includes('/api/geo/search'), '地点文字要经地理编码解析');
  assert.ok(js.includes('stripRegion'), '带省名解析成企业时应去掉省名重试');
  assert.ok(js.includes('locState.source'), '应记录地点来源并传给后端');
  assert.ok(js.includes('recommend-context'), '界面要说清按哪个地点算的');
  assert.ok(js.includes('plotCrops='), '请求应带上地块适宜作物');
  assert.ok(js.includes('__timelinePlotId'), '进日历时应把地块带过去');

  assert.ok(indexHtml.includes('recommend-plot'), '推荐页应有地块下拉');
  assert.ok(indexHtml.includes('recommend-resolve-btn'), '推荐页应有地点解析按钮');
  assert.ok(indexHtml.includes("params.set('plotId'"), '日历跳转应带上 plotId');
});

test('UI 重写：改为现代干净浅色底 + 鲜明叶片绿（主流 SaaS 风格）', () => {
  // 配色升级：干净浅灰绿底 + 纯白卡片 + 鲜明叶片绿
  assert.ok(commonCss.includes('--bg: #f5f7f5'), '底色应为干净浅灰绿 #f5f7f5');
  assert.ok(commonCss.includes('--surface: #ffffff'), '卡片应为纯白 #ffffff');
  assert.ok(commonCss.includes('--ink: #17211a'), '主文字应为近黑 #17211a');
  assert.ok(commonCss.includes('--brand: #16a34a'), '主色应为鲜明叶片绿 #16a34a');
  assert.ok(commonCss.includes('--line: #e3e8e3'), '分隔线应为浅灰 #e3e8e3');
  // 旧变量名必须保留，否则各模块 CSS 会集体失效
  ['--paper:', '--card:', '--leaf:', '--sun:', '--rain:', '--red:'].forEach(v => {
    assert.ok(commonCss.includes(v), '应保留旧变量 ' + v);
  });
  // 主按钮改为纯色（去掉过时大渐变），hover 加深
  assert.ok(!/\n\.btn-primary\s*\{[^}]*linear-gradient/.test(commonCss), '主按钮不应再是渐变');
  assert.ok(commonCss.includes('--brand-hover'), '主按钮应有 hover 加深色');
  // 现代聚焦环 + 定制下拉箭头
  assert.ok(commonCss.includes('--focus-ring'), '应有统一聚焦环变量');
  assert.ok(commonCss.includes('appearance: none'), '原生下拉应定制外观');
  // 布局与尺度保持：侧栏 216px
  assert.match(commonCss, /body\[data-layout="app"\]\s*\{[^}]*padding-left: 216px/);
  assert.ok(commonCss.includes('--radius: 16px'), '卡片圆角升级为 16px');
  assert.match(commonCss, /\.field input\s*\{[^}]*height: 40px/);
  // 侧栏配色也保持：叶绿 hover/选中，图标不灰化
  assert.ok(sidebarCss.includes('var(--leaf-soft'), '侧栏选中态应用叶绿浅底');
  assert.ok(!sidebarCss.includes('grayscale(1)'), '图标不应灰化');
});

test('账号入口固定在侧栏左下角', () => {
  const authCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'auth.css'));
  const userChipJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'user-chip.js'));

  // 结构：账号区在导航之后，靠 flex 贴到侧栏底部
  assert.ok(indexHtml.includes('id="sidebar-footer"'), '侧栏应有底部账号区');
  assert.ok(indexHtml.indexOf('id="sidebar-nav"') < indexHtml.indexOf('id="sidebar-footer"'),
    '账号区应排在导航之后');
  assert.match(sidebarCss, /\.sidebar-footer\s*\{[^}]*flex-shrink: 0/);

  // 逻辑：优先挂侧栏底部，找不到才退回页头
  assert.ok(userChipJs.includes('sidebar-footer'), '账号入口应优先挂到侧栏底部');
  assert.ok(userChipJs.indexOf('sidebar-footer') < userChipJs.indexOf("querySelector('.brand-row')"),
    '应先找侧栏底部，页头只作兜底');
  assert.ok(userChipJs.includes('user-chip--sidebar'), '侧栏变体应有独立类名');

  // 样式：整行入口与导航项同构，登录后是头像+昵称+箭头整体一个可点按钮
  assert.ok(authCss.includes('.user-chip-entry'), '未登录应是整行可点入口');
  assert.ok(authCss.includes('.user-chip-avatar'), '登录后应显示头像');
  assert.ok(authCss.includes('.user-chip-trigger'), '头像+昵称+箭头应整体是一个可点按钮（头像纳入热区）');
  assert.match(authCss, /\.user-chip-name\s*\{[^}]*text-overflow:\s*ellipsis/, '昵称超长应省略不撑破侧栏');
});

test('移动端抽屉：汉堡让位且点外部可关闭', () => {
  assert.ok(sidebarJs.includes("classList.toggle('sidebar-open'"), '抽屉状态要同步到 body');
  assert.ok(sidebarJs.includes('body.classList.remove') || sidebarJs.includes("classList.remove('sidebar-open')"),
    '关闭抽屉要清掉 body 类');
  assert.ok(sidebarCss.includes('body.sidebar-open .sidebar-burger'), '打开时汉堡应隐藏');
});

test('地块：倾角不超 90°、面积保留一位小数', () => {
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const analyze = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotAnalyzeHandler.java'));
  const plotHandler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotHandler.java'));
  const myPlotsCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'my-plots.css'));

  // 倾角：不得再拿坡向（0-360）回填，表单里只留坡度输入 + 坡向只读
  assert.ok(!myPlotsJs.includes("angleInput.value = Math.round(d.terrain.aspectDeg"),
    '不能把坡向方位角当倾角回填');
  assert.ok(!indexHtml.includes('name="plot-angle"'), '表单不应再有倾角输入框');
  assert.ok(indexHtml.includes('id="plot-aspect"'), '坡向应独立只读展示');
  assert.ok(myPlotsCss.includes('.my-plots-readonly'), '只读样式应存在');
  assert.match(myPlotsJs, /Math\.abs\(p\.angle\) <= 90/, '卡片应过滤超 90° 的历史倾角');

  // 后端：读取时归一化，超过 90° 按 0 处理而不是截成 90 判死
  assert.ok(plotHandler.includes('normalizeSlope'), '后端应归一化倾角/坡度');
  assert.match(plotHandler, /a > 90 \? 0 : a/, '超过 90° 应归零而非截断');

  // 面积：亩保留 1 位小数（不足 1 亩保留 2 位）
  assert.match(analyze, /mu >= 1 \? Math\.round\(mu \* 10\)/, '面积应按 1 位小数输出');
  assert.ok(myPlotsJs.includes('function fmtMu'), '前端应有统一的面积格式化');
  assert.match(myPlotsJs, /n >= 1 \? \(Math\.round\(n \* 10\)/, 'fmtMu 应对齐后端口径');
});

test('T20 四态：空态有引导动作、错误态可重试', () => {
  const uiStateJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'ui-state.js'));
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const myCropsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-crops.js'));
  const serviceJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
  const diseaseJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'disease.js'));

  // 统一组件：四种状态各一个入口，空/错误都带可点动作
  ['loading', 'empty', 'error', 'partial'].forEach(k => {
    assert.ok(uiStateJs.includes(k + ':'), '四态组件应提供 ' + k);
  });
  assert.ok(uiStateJs.includes('ui-state-action'), '空态与错误态应带按钮');
  assert.ok(indexHtml.includes('ui-state.js'), '首页应引入四态组件');
  assert.ok(commonCss.includes('.ui-state--error'), '错误态应有独立样式');
  assert.ok(commonCss.includes('.ui-state--partial'), '部分数据应有提示条样式');

  // 空列表不留死文案：都能一键到下一步
  assert.ok(myPlotsJs.includes("actionText: '框一块地'"), '地块空态应能直接开始框地');
  assert.ok(myCropsJs.includes("actionText: '去农事日历'"), '作物空态应能跳到日历');
  assert.ok(serviceJs.includes("actionText: '提第一个问题'"), '工单空态应能新建咨询');

  // 错误态可重试，不用重新填表
  assert.ok(serviceJs.includes('onRetry: loadTickets'), '工单加载失败应可重试');
  assert.ok(diseaseJs.includes('window.UiState.error'), '病害查询失败应走错误态');
  assert.ok(diseaseJs.includes('requestSubmit'), '病害重试应复用手填内容');

  // 移动端触控热区
  assert.match(commonCss, /\.ui-state-action \{ min-height: 44px/, '窄屏按钮应不小于 44px');
});

// ---------------- V5 T18 提醒服务 ----------------
test('T18 提醒中心：三类分级、开关、未读数与降级持久', () => {
  const rcJs = read(path.resolve(__dirname, '..', 'components', 'reminder-center', 'reminder-center.js'));
  const rcCss = read(path.resolve(__dirname, '..', 'components', 'reminder-center', 'reminder-center.css'));
  assert.ok(rcJs.includes('weather') && rcJs.includes('disease') && rcJs.includes('window'), '应覆盖三类提醒');
  assert.ok(rcJs.includes('high') && rcJs.includes('medium') && rcJs.includes('low'), '应有三级风险');
  assert.ok(rcJs.includes('unreadCount'), '应能显示未读数');
  assert.ok(rcJs.includes('setSetting'), '提醒类型开关应可持久设置');
  assert.ok(rcJs.includes('generateFromTimeline'), '应从日历节点生成风险提醒');
  assert.ok(rcJs.includes('LEGACY_KEY'), '旧版单标记提醒应迁移');
  assert.ok(rcJs.includes('当前为本地提醒'), '服务不可用时须明确降级提示');
  assert.ok(rcJs.includes('ahead < 0') && rcJs.includes("n.status === 'done'"), '过期与已完成节点不应产生提醒');
  assert.ok(rcCss.includes('rc-high') && rcCss.includes('rc-badge'), '高风险与未读角标应有样式');
  assert.ok(indexHtml.includes('reminder-center.js') && indexHtml.includes('reminder-center.css'), '首页应引入提醒中心');
  assert.ok(timelineHtml.includes('reminder-center.js'), '时间线页应引入提醒中心');
  assert.ok(timelineHtml.includes('__reminderCenterNoEntry'), '时间线页不应重复挂悬浮铃铛');
  assert.ok(timelineHtml.includes("agritime:reminders-changed"), '提醒变化应刷新内嵌面板');
  assert.ok(timelineHtml.includes('generateFromTimeline'), '时间线页应在日历生成后同步风险提醒');
});

test('T18 提醒记录：按账号落盘、去重、已读与过期清理', () => {
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'ReminderHandler.java'));
  const store = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'reminder', 'ReminderStore.java'));
  const fileStore = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'FileBlobStore.java'));
  const server = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  const taskbook = read(path.resolve(__dirname, '..', '..', 'docs', 'taskbook-v5.md'));
  assert.ok(server.includes('/api/reminders'), '服务端应注册提醒接口');
  assert.ok(handler.includes('Auth.current'), '提醒接口应按账号隔离');
  // 原子写下沉到存储层（FileBlobStore），ReminderStore 经 StoreFactory 复用同一实现
  assert.ok(fileStore.includes('ATOMIC_MOVE'), '档案写入应原子替换（存储层）');
  assert.ok(store.includes('StoreFactory.forCollection("reminders")'), '提醒档案应经存储抽象落盘');
  assert.ok(store.includes('PRUNE_AFTER_DAYS'), '过期提醒应清理');
  assert.ok(store.includes('Asia/Shanghai'), '过期判定应显式用北京时间');
  assert.ok(store.includes('old.get("read")'), '重复推送不应把已读顶回未读');
});

// ---------------- T22 日历节点改期 ----------------
test('T22 改期纯函数：顺延 / 拒绝倒序 / 差异与恢复', () => {
  const TL = require('../../shared/utils/timeline-logic.js');
  const nodes = [
    { nodeId: '1', date: '2026-06-01', name: '播种', operationType: 'sowing' },
    { nodeId: '2', date: '2026-06-15', name: '追肥', operationType: 'fertilize' },
    { nodeId: '3', date: '2026-09-20', name: '收获', operationType: 'harvest' },
    { nodeId: '4', date: '2026-10-01', name: '晾晒', operationType: 'postharvest' }
  ];
  const r = TL.reschedule(nodes, 1, '2026-06-20');
  assert.strictEqual(r.ok, true);
  assert.deepStrictEqual(r.nodes.map((n) => n.date),
    ['2026-06-01', '2026-06-20', '2026-09-25', '2026-10-06']);

  const bad = TL.reschedule(nodes, 1, '2026-05-30');
  assert.strictEqual(bad.ok, false);
  assert.match(bad.error, /上一节点/);

  const diff = TL.diffDates(nodes, r.nodes);
  assert.deepStrictEqual(Object.keys(diff).sort(), ['n2', 'n3', 'n4']);

  const applied = TL.applyOverrides(nodes, { dates: diff, manual: { n2: '2026-06-15' } });
  assert.strictEqual(applied[1].date, '2026-06-20');
  assert.strictEqual(applied[1].manualReschedule, true);
  assert.strictEqual(applied[1].originalDate, '2026-06-15');
  assert.strictEqual(applied[2].rescheduled, true);
  assert.strictEqual(applied[2].manualReschedule, false);
  assert.strictEqual(TL.hasOverrides({ dates: {} }), false);
  assert.strictEqual(TL.hasOverrides({ dates: { n2: '2026-06-20' } }), true);
  assert.deepStrictEqual(TL.applyOverrides(nodes, null).map((n) => n.date),
    ['2026-06-01', '2026-06-15', '2026-09-20', '2026-10-01']);
});

test('T22 时间线页接入改期入口与恢复按钮', () => {
  assert.ok(timelineHtml.includes('reschedule-bar') && timelineHtml.includes('恢复按规程排期'));
  assert.ok(timelineHtml.includes('tl-edit-date'), '节点应有改期入口');
  assert.ok(timelineHtml.includes('applyOverrides') && timelineHtml.includes('diffDates'), '应复用纯函数');
  assert.ok(timelineHtml.includes('agritime.timeline.overrides.v1'), '改期档案应持久化');
  assert.ok(timelineHtml.includes('已改期 · 原'), '改期节点应标注原日期');
  assert.ok(timelineCss.includes('.tl-date-editor') && timelineCss.includes('.reschedule-bar'), '应有改期样式');
  assert.ok(/\.reschedule-bar\[hidden\]/.test(timelineCss), '改期条 hidden 时必须真正隐藏（flex 会盖掉 hidden）');
});

// ---------------- V5 T18 工单流转 ----------------
test('T18 工单：分页、状态流转、未读与独立后台页', () => {
  const sh = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SupportHandler.java'));
  const svc = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
  const adminHtml = read(path.join(PAGES, 'admin.html'));
  const adminTicketsHtml = read(path.join(PAGES, 'admin-tickets.html'));
  const adminTicketsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin-tickets.js'));

  assert.ok(sh.includes('DEFAULT_PAGE_SIZE') && sh.includes('totalPages') && sh.includes('statusCounts'), '接口应返回分页与状态计数');
  assert.ok(sh.includes('"processing"') && sh.includes('"resolved"'), '状态应含处理中/已解决');
  assert.ok(sh.includes('unreadForAdmin') && sh.includes('unreadForUser'), '未读应分身份计算');
  assert.ok(sh.includes('path.endsWith("/status")'), '应有状态流转接口');
  assert.ok(sh.includes('Json.stringify(s)'), '落盘应保留完整字段（userId/未读）');
  assert.ok(!sh.includes('serializeTickets'), '不应再用响应结构落盘（会丢 userId）');

  assert.ok(svc.includes('service-tabs') && svc.includes('service-pager'), '用户端应有筛选与分页');
  assert.ok(svc.includes('updateSidebarBadge'), '侧栏应有未读角标');
  assert.ok(svc.includes('resolved'), '用户端应认识已解决状态');

  assert.ok(indexHtml.includes('id="service-tabs"'), '首页应有状态筛选');
  assert.ok(indexHtml.includes('id="service-pager"'), '首页应有分页条');
  assert.ok(adminTicketsHtml.includes('id="ticket-tabs"') && adminTicketsHtml.includes('id="ticket-detail"'), '后台应有独立工单页');
  assert.ok(adminTicketsJs.includes("API + '/status'"), '后台应能流转状态');
  assert.ok(adminTicketsJs.includes('mustChangePassword'), '后台应守卫初始密码');
  assert.ok(adminHtml.includes('admin-tickets.html'), '后台入口应指向独立工单页');
});

// ---------------- V5 T26 持久化前端侧 ----------------
test('T26 持久化：设备同步、冲突留痕、导出与注销', () => {
  const sync = read(path.resolve(__dirname, '..', 'assets', 'js', 'user-sync.js'));
  const accHtml = read(path.join(PAGES, 'account.html'));
  const accJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'account.js'));
  const authH = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'AuthHandler.java'));
  const authCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'auth.css'));

  assert.ok(sync.includes("sensors: 'agritime.sensors.v1'"), '设备应纳入同步');
  assert.ok(sync.includes('CONFLICT_KEY') && sync.includes('recordConflict'), '合并应留冲突记录');
  assert.ok(sync.includes('agri-sync-status') && sync.includes('当前为本地数据'), '降级应明确提示');
  assert.ok(sync.includes("addEventListener('online'"), '网络恢复应立即重试');
  assert.ok(accHtml.includes('id="export-json"') && accHtml.includes('id="export-csv"'), '应能导出 JSON/CSV');
  assert.ok(accHtml.includes('id="del-submit"'), '应有注销入口');
  assert.ok(accJs.includes("'/delete'") && accJs.includes('collectExport'), '前端应调用注销并导出');
  assert.ok(authH.includes('deleteAccount') && authH.includes('ReminderStore.delete'), '注销应删账号与提醒档案');
  assert.ok(authH.includes('管理员账号不能自助注销'), '管理员不应被误删');
  assert.ok(commonCss.includes('.agri-sync-status'), '应有同步状态提示样式');
  assert.ok(authCss.includes('.account-block--danger'), '注销区应有危险样式');
});

// ---------------- V5 T20 播期双依据 ----------------
test('T20 播期：节气 + 积温 + 最晚播期，缺积温如实降级', () => {
  const sw = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowWindowHandler.java'));
  const gdd = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'GddAdvisor.java'));

  assert.ok(sw.includes('GddAdvisor') && sw.includes('"gdd"') && sw.includes('"latest"'), '接口应返回积温与最晚播期');
  assert.ok(sw.includes('gddAvailable'), '应标记积温是否可用');
  assert.ok(gdd.includes('stableAbove') && gdd.includes('stableBelow'), '应有稳定通过/降至的判定');
  assert.ok(gdd.includes('parseTargetGdd') && gdd.includes('parseThreshold'), '应解析品种积温与生长下限温度');
  assert.ok(gdd.includes('isAutumnSown') && gdd.includes('nextWindow'), '应区分春播/秋播窗口');
  assert.ok(gdd.includes('品种未标注所需积温'), '缺积温时应如实说明');

  assert.ok(indexHtml.includes('term-row') && indexHtml.includes('按积温') && indexHtml.includes('最晚播期'), '面板应三行并列');
  assert.ok(indexHtml.includes('info.solar') && indexHtml.includes('info.gdd'), '前端应消费双依据字段');
  assert.ok(commonCss.includes('.term-row') && commonCss.includes('.term-row-btn'), '应有播期行与填入按钮样式');
});

// ---------------- V5 T20 地块历史与地力档案 ----------------
test('T20 地块历史：连作、产量趋势、CSV 与产量补录', () => {
  const ph = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'plot', 'PlotHistory.java'));
  const phh = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotHistoryHandler.java'));
  const myPlotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const myCropsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-crops.js'));

  assert.ok(ph.includes('detectContinuousCropping') && ph.includes('yieldTrend') && ph.includes('toCsv'), '后端应有连作/趋势/CSV');
  assert.ok(ph.includes('analyze'), '应有纯分析入口（不另存一份数据）');
  assert.ok(phh.includes('/history/analyze') && phh.includes('handleAnalyze'), '应有匿名分析接口');
  assert.ok(myPlotsJs.includes('/api/plots/history/analyze'), '地块详情应调用分析接口');
  assert.ok(myPlotsJs.includes('连作提示') && myPlotsJs.includes('产量趋势'), '应展示连作与趋势');
  assert.ok(myPlotsJs.includes('导出 CSV'), '应能导出 CSV');
  assert.ok(indexHtml.includes('name="mc-yield"') && indexHtml.includes('name="mc-harvest-date"'), '作物表单应有产量与收获期');
  assert.ok(myCropsJs.includes('yieldKg') && myCropsJs.includes('产量：'), '作物记录应保存并展示产量');
  assert.ok(myCropsJs.includes('fillPlotOptions'), '作物表单应能关联地块');
});

// ---------------- T17 指导按面积与墒情换算 ----------------

test('T17 指导换算：亩均与总量分开给，未填面积不编总量', () => {
  const gs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'guidance', 'GuidanceService.java'));
  const dose = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'guidance', 'Dose.java'));
  const orch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'orchestration', 'TimelineOrchestrator.java'));
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));

  assert.ok(dose.includes('/亩'), '应只解析带「/亩」的用量');
  assert.ok(dose.includes('灌根1000倍液') || dose.includes('稀释倍数'), '稀释倍数不应按面积换算');
  assert.ok(dose.includes('totalText'), '应能算总面积用量');
  assert.ok(gs.includes('"perMu"') && gs.includes('"total"'), '应同时输出亩均与总量');
  assert.ok(gs.includes('areaMu > 0'), '未填面积时不应给总量');
  assert.ok(gs.includes('填入面积可自动换算总用量'), '未填面积应提示补录');
  assert.ok(gs.includes('"weatherAdvice"'), '应给天气建议');
  assert.ok(gs.includes('before-rain') && gs.includes('drought') && gs.includes('waterlog'), '雨前/补灌/排渍三种口径都应存在');
  assert.ok(orch.includes('GuidanceService.build(n.opType(), slice, cropNameForGuidance'), '近端节点应按天气给指导');
  assert.ok(orch.includes('GuidanceService.build(n.opType(), null, cropNameForGuidance'), '远端节点不应挂天气，只给规程用量');
  assert.ok(th.includes('"areaMu"'), '接口应接受并回传面积');
});

test('T17 指导换算：前端面积条与总量展示', () => {
  const timelineHtml = read(path.resolve(__dirname, '..', 'pages', 'timeline.html'));
  const timelineCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'timeline.css'));
  const commonCss = read(path.resolve(__dirname, '..', 'assets', 'css', 'common.css'));
  const indexHtml = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  assert.ok(timelineHtml.includes('id="area-input"'), '日历页应有面积输入');
  assert.ok(timelineHtml.includes('query.areaMu'), '面积应从 URL 带入');
  assert.ok(timelineHtml.includes('doseText'), '应渲染亩均 + 总量');
  assert.ok(timelineHtml.includes('tl-guide-advice'), '应渲染天气建议');
  assert.ok(timelineCss.includes('.area-bar'), '缺少面积条样式');
  assert.ok(commonCss.includes('.tl-guide-advice--warn'), '缺少天气建议样式');
  assert.ok(indexHtml.includes('areaMu: window.__timelineAreaMu'), '首页应把面积带进日历');
});

// ---------------- T19 茬口冲突校验 ----------------

test('T19 茬口校验：同地块重叠阻断，跨地块只提醒', () => {
  const ph = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'plot', 'PlotHistory.java'));
  const sch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowCheckHandler.java'));
  const indexHtml = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  assert.ok(ph.includes('checkSchedule'), '应有茬口校验纯函数');
  assert.ok(ph.includes('plot-occupied'), '同地块重叠应阻断');
  assert.ok(ph.includes('continuous-cropping'), '连作应提醒');
  assert.ok(ph.includes('cross-plot-busy'), '跨地块农忙应提醒');
  assert.ok(ph.includes('LAND_PREP_DAYS'), '应留整地天数再给最早播期');
  assert.ok(sch.includes('/api/sow-check') && sch.includes('"POST"'), '接口应支持 POST');
  assert.ok(sch.includes('recordsOf'), '应解析前端上传的作物记录');
  assert.ok(sch.includes('laterDate'), '改期建议应取更晚的那个（腾地 vs 适播）');
  assert.ok(indexHtml.includes("fetch('/api/sow-check'"), '前端应 POST 校验');
  assert.ok(indexHtml.includes('window.MyPlotsStore.getById'), '应能取到地块');
  assert.ok(indexHtml.includes('id="plot-select"'), '首页应有地块选择器');
});

// ---------------- T16 病害库扩充（数据部分已落地） ----------------

test('T16 病害库：50 条起，每条带来源、适用范围与安全间隔期', () => {
  const lib = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'disease', 'library.json')));
  assert.ok(lib.diseases.length >= 50, '病害库应扩到 50 条以上，实际 ' + lib.diseases.length);
  const ids = new Set();
  lib.diseases.forEach((d) => {
    assert.ok(!ids.has(d.id), 'id 不应重复：' + d.id);
    ids.add(d.id);
    ['source', 'scope', 'safetyNote'].forEach((k) => {
      assert.ok(d[k], d.id + ' 缺字段 ' + k);
    });
    // 生理性病害（如番茄脐腐病）不用药，间隔期可以是 0，但字段必须在
    assert.ok(typeof d.safetyInterval === 'number', d.id + ' 缺安全间隔期字段');
  });
  const withInterval = lib.diseases.filter((d) => d.safetyInterval > 0).length;
  assert.ok(withInterval >= lib.diseases.length - 3, '需要用药的条目都应给出安全间隔期');
  // 覆盖至少 5 个主栽作物（中英文名都算）
  const crops = new Set();
  lib.diseases.forEach((d) => (d.crops || []).forEach((c) => crops.add(c)));
  ['小麦', '玉米', '水稻', '番茄'].forEach((c) => assert.ok(crops.has(c), '应覆盖 ' + c));
  const rust = lib.diseases.find((d) => d.name === '小麦条锈病');
  assert.ok(rust && rust.symptoms.some((x) => x.includes('条状孢子堆')), '小麦条锈病应有可命中的症状描述');
});

// ---------------- T16 病害问诊深化（匹配度 / 依据 / 转人工） ----------------

test('T16 病害问诊：匹配度与命中依据成对出现，没命中不硬凑', () => {
  const dh = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'DiseaseHandler.java'));
  const js = read(path.resolve(__dirname, '..', 'assets', 'js', 'disease.js'));
  const css = read(path.join(CSS, 'disease.css'));
  const html = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  assert.ok(dh.includes('"matchRate"'), '接口应输出匹配度');
  assert.ok(dh.includes('"matchedSymptoms"') && dh.includes('"evidence"'), '应输出命中的症状词条');
  assert.ok(dh.includes('"needHuman"'), '无匹配应给出转人工标记');
  assert.ok(dh.includes('!s.matchedSymptoms.isEmpty()'), '没有依据的结果不应展示');
  assert.ok(dh.includes('"safetyInterval"') && dh.includes('"safetyNote"'), '应透传安全间隔期与施药安全提示');
  assert.ok(dh.includes('"scope"') && dh.includes('"source"'), '应透传适用范围与来源');

  assert.ok(js.includes('匹配度'), '前端应显示匹配度而不是裸百分比');
  assert.ok(js.includes('disease-evidence-chip'), '前端应列出判断依据');
  assert.ok(js.includes('安全间隔期'), '前端应显示安全间隔期');
  assert.ok(js.includes('renderNeedHuman') && js.includes('goHuman'), '无匹配应给转人工入口');
  assert.ok(js.includes("switchModule('service')"), '转人工应切到反馈板块');
  assert.ok(css.includes('.disease-evidence-chip') && css.includes('.disease-safety'), '缺少依据与用药安全样式');
  assert.ok(html.includes('不承诺识别率'), '图像识别应标注仅辅助、不承诺识别率');
});

// ---------------- T17 工程兜底：时区、超时、缓存 ----------------

test('T17 工程兜底：时间走 AppTime，超时收口到 AppConfig，推荐结果有缓存', () => {
  const fs_ = require('fs');
  const timeUtil = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'util', 'AppTime.java'));
  const cfg = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'config', 'AppConfig.java'));
  const server = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  const rec = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'RecommendHandler.java'));
  const timelineHtml = read(path.resolve(__dirname, '..', 'pages', 'timeline.html'));

  assert.ok(timeUtil.includes('Asia/Shanghai'), 'AppTime 应固定北京时间');
  assert.ok(server.includes('TimeZone.setDefault'), '启动时应固定 JVM 默认时区');
  assert.ok(rec.includes('CACHE') && rec.includes('"cached"'), '推荐接口应有结果缓存');
  assert.ok(rec.includes('AppTime.today()'), '缓存 key 应带上日期');
  assert.ok(cfg.includes('httpConnectTimeoutMs') && cfg.includes('httpReadTimeoutMs'), '超时应由 AppConfig 统一定义');
  assert.ok(timelineHtml.includes('天气未获取'), '天气不可用时页头应写「天气未获取」');

  // 业务代码里不应该再有裸的 now()，否则容器跑 UTC 时会偏移 8 小时
  const srcDir = path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar');
  const offenders = [];
  (function walk(dir) {
    fs_.readdirSync(dir, { withFileTypes: true }).forEach((e) => {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) return walk(full);
      if (!e.name.endsWith('.java') || e.name === 'AppTime.java') return;
      const text = fs_.readFileSync(full, 'utf8');
      if (/LocalDate\.now\(\)|LocalDateTime\.now\(\)/.test(text)) offenders.push(e.name);
    });
  })(srcDir);
  assert.deepEqual(offenders, [], '仍有未指定时区的时间调用：' + offenders.join(', '));

  // 出站请求的时长不应再散落在各 handler 里
  const hardcoded = [];
  (function walk2(dir) {
    fs_.readdirSync(dir, { withFileTypes: true }).forEach((e) => {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) return walk2(full);
      if (!e.name.endsWith('.java')) return;
      const text = fs_.readFileSync(full, 'utf8');
      if (text.includes('Duration.ofSeconds') && e.name !== 'Server.java') hardcoded.push(e.name);
    });
  })(srcDir);
  assert.deepEqual(hardcoded, [], '仍有硬编码超时：' + hardcoded.join(', '));
});

// ---------------- T20 养殖成本核算 ----------------

test('T20 养殖核算：成本构成、估算/实际分离、起捕提示', () => {
  const cost = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'aqua', 'AquaCost.java'));
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'IndustryHandler.java'));
  const plotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const plotsCss = read(path.join(CSS, 'my-plots.css'));
  const model = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'industries', 'aquaculture', 'cost-model.json')));

  assert.ok(cost.includes('estimate') && cost.includes('harvestHint'), '应有成本核算与起捕提示');
  assert.ok(cost.includes('estimated'), '估算/实际必须能区分');
  assert.ok(handler.includes('/cost') || handler.includes('"/cost"'), '应挂上成本接口');
  assert.ok(handler.includes('costHint'), '起捕节点应带成本提示');

  assert.ok(model.defaults && model.defaults.fcr > 0, '成本模型应有饲料系数');
  assert.ok(model.defaults.survivalRate > 0, '成本模型应有成活率');
  assert.ok(Object.keys(model.species).length >= 3, '至少覆盖 3 个养殖品类');

  assert.ok(plotsJs.includes('/api/industry/cost'), '塘口详情应调用成本接口');
  assert.ok(plotsJs.includes('估算值') && plotsJs.includes('按实际数据'), '估算与实际应分开标注');
  assert.ok(plotsJs.includes('保本价'), '应给出保本价');
  assert.ok(plotsJs.includes('pond-cost-hint'), '起捕节点应展示成本提示');
  assert.ok(plotsCss.includes('.pond-cost') && plotsCss.includes('.pond-cost-row'), '缺少成本面板样式');
});

// ---------------- T17 传感器拉取与设备告警 ----------------

test('T17 传感器：接口鉴权、SSRF 防护、告警进提醒中心', () => {
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SensorHandler.java'));
  const poller = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorPoller.java'));
  const store = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorStore.java'));
  const fileStoreJs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'FileBlobStore.java'));
  const server = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  const reminderStore = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'reminder', 'ReminderStore.java'));
  const plotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const plotsCss = read(path.join(CSS, 'my-plots.css'));
  const centerJs = read(path.resolve(__dirname, '..', 'components', 'reminder-center', 'reminder-center.js'));

  assert.ok(handler.includes('Auth.current') && handler.includes('401'), '设备接口必须登录后才能用');
  assert.ok(server.includes('"/api/sensor"'), '应注册 /api/sensor 路由');

  assert.ok(poller.includes('assertPublicUrl') && poller.includes('isPrivate'), '拉取前必须做 SSRF 校验');
  assert.ok(poller.includes('isLoopbackAddress') && poller.includes('isSiteLocalAddress'), '应拦回环与内网地址');
  assert.ok(poller.includes('169') && poller.includes('100') , '应拦云元数据与 CGNAT 段');
  assert.ok(poller.includes('ReminderStore.sync'), '告警应写进提醒档案，而不是另存一份');
  assert.ok(poller.includes('sensor|'), '告警 id 应对同指标同日去重');

  assert.ok(store.includes('StoreFactory.forCollection("sensors")'), '读数应按用户隔离走存储抽象');
  assert.ok(store.includes('listKeys') && store.includes('readUser'), '调度器枚举应走 listKeys，档案按用户一份');
  assert.ok(fileStoreJs.includes('ATOMIC_MOVE'), '写入应原子替换（存储层）');
  assert.ok(reminderStore.includes('"sensor"'), '提醒类型应支持 sensor');
  assert.ok(centerJs.includes('sensor:') && centerJs.includes('设备告警'), '提醒中心应识别设备告警');

  assert.ok(plotsJs.includes('/api/sensor/devices') && plotsJs.includes('/api/sensor/poll'), '塘口详情应能接入并拉取设备');
  assert.ok(plotsJs.includes('登录后可接入设备'), '未登录时应给明确提示');
  assert.ok(plotsCss.includes('.pond-devices') && plotsCss.includes('.pond-device-status'), '缺少设备面板样式');
});

// ---------------- T16 评分因子增补 ----------------

test('T16 因子：质地/水源/交通接到真实数据源，取不到就退出', () => {
  const geo = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'geo', 'GeoFactors.java'));
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotAnalyzeHandler.java'));
  const plotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const plotsCss = read(path.join(CSS, 'my-plots.css'));

  assert.ok(geo.includes('rest.isric.org'), '土壤质地应走 SoilGrids');
  assert.ok(geo.includes('overpass-api.de'), '水源/交通应走 Overpass');
  assert.ok(geo.includes('bucket('), '外部查询应按坐标缓存');
  assert.ok(geo.includes('return null'), '取不到应返回 null，而不是默认值');

  assert.ok(handler.includes('"texture"') && handler.includes('"water"') && handler.includes('"road"'), '应输出三个新因子');
  assert.ok(handler.includes('extended'), '深度因子应由开关控制，避免拖慢框地');
  assert.ok(handler.includes('factorNotes'), '缺项原因应回传给前端');
  assert.ok(handler.includes('factorSource') && handler.includes('factorMethod'), '因子应带来源与算法说明');
  assert.ok(handler.includes('wsum'), '权重应按实际参与的因子归一');

  assert.ok(plotsJs.includes('extended: true'), '地块详情应开启深度因子');
  assert.ok(plotsJs.includes('因子说明'), '前端应有因子说明面板');
  assert.ok(plotsJs.includes('plot-factor-missing'), '缺项应提示而不是悄悄消失');
  assert.ok(plotsCss.includes('.plot-factor-detail'), '缺少因子说明样式');
});

// ---------------- T16 推荐气候门控与落选原因（修复） ----------------

test('T16 推荐：气候带不符不再排在气候匹配前面，落选原因单独列出', () => {
  const dh = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'RecommendHandler.java'));
  const js = read(path.resolve(__dirname, '..', 'assets', 'js', 'recommend.js'));
  const css = read(path.join(CSS, 'recommend.css'));

  assert.ok(dh.includes('"rejected"'), '接口应输出落选作物列表');
  assert.ok(dh.includes('climateNote'), '应记录气候带不符的原因');
  assert.ok(dh.includes('if (s.climateMatch) recommended.add(s)'), '气候匹配与不符应分区');
  assert.ok(dh.includes('fellBack'), '无气候匹配作物时应退回全量并打标');
  assert.ok(dh.includes('"why"'), '落选条目应带原因');

  assert.ok(js.includes('renderRejected'), '前端应渲染落选列表');
  assert.ok(js.includes('不适合本地气候的作物'), '落选区块应说明原因');
  assert.ok(js.includes('山东济宁（默认）'), '默认地名应为真实坐标对应的山东济宁，而不是北京');
  assert.ok(css.includes('.recommend-rejected'), '缺少落选样式');
});

// ---------------- T28 传感器定时拉取与离线判定（补齐） ----------------

test('T28 传感器：后台定时拉取 + 间隔配置 + N 周期离线判定', () => {
  const sched = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorScheduler.java'));
  const store = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorStore.java'));
  const server = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  const cfg = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'config', 'AppConfig.java'));
  const plotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));

  assert.ok(sched.includes('scheduleWithFixedDelay'), '应有后台定时调度');
  assert.ok(sched.includes('pollDue') && sched.includes('SensorPoller.pollDevice'), '到期应自动拉取');
  assert.ok(sched.includes('offlineCycles'), '离线判定应按 N 个周期');
  assert.ok(sched.includes('intervalMinOf'), '拉取间隔应从设备配置读取');
  assert.ok(store.includes('listPlots'), '应能枚举全量设备');
  assert.ok(store.includes('setLastPollAt'), '应记录调度时间戳，避免重复拉');
  assert.ok(server.includes('SensorScheduler.start()'), 'Server 启动应拉起调度器');
  assert.ok(cfg.includes('sensorDefaultIntervalMin'), '默认间隔应可配置');
  assert.ok(plotsJs.includes('拉取间隔（分）'), '前端应能让用户设置拉取间隔');
  assert.ok(plotsJs.includes('自动拉取'), '设备列表应说明自动拉取');
});

// ---------------- T21 四态双主题与深色主题（补齐） ----------------

test('T21 深色主题：data-theme 三态切换，四态与输入框可读', () => {
  const common = read(path.join(CSS, 'common.css'));
  const dark = read(path.join(CSS, 'theme-dark.css'));
  const indexHtml = read(path.resolve(__dirname, '..', 'pages', 'index.html'));
  const themeJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'theme.js'));

  assert.ok(common.includes(':root[data-theme="dark"]'), '深色主题应由 data-theme 触发，而非仅跟随系统');
  assert.ok(common.includes('color-scheme: dark'), '应声明 color-scheme 让原生控件跟主题走');
  assert.ok(common.includes('--danger-text') && common.includes('--warn-text'), '四态语义色应变量化');
  assert.ok(dark.includes('input:not([type])'), '深色下应覆盖输入框白底');
  assert.ok(dark.includes('background: var(--surface-2)'), '深色输入框应用深色底');
  assert.ok(indexHtml.includes('theme-dark.css'), '页面应最后加载深色主题兜底');
  // 三态偏好 + 无闪烁 + 跟随系统实时换肤
  assert.ok(themeJs.includes("'system'"), '主题应有 浅色/深色/跟随系统 三态');
  assert.ok(themeJs.includes('matchMedia') && themeJs.includes("addEventListener('change'"), '跟随系统应监听系统变化实时换肤');
  assert.ok(themeJs.includes('localStorage'), '主题偏好应持久化');
  assert.ok(indexHtml.includes("agritime.theme"), '首帧前应有内联脚本写好 data-theme 避免闪烁');
  assert.ok(indexHtml.includes('theme.js'), '页面应加载主题脚本');
});

test('左下角设置：分组弹层 + 主题切换 + 定位开关 + 头像', () => {
  const userChipJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'user-chip.js'));
  const prefsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'prefs.js'));
  const iconsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'icons.js'));
  const sidebarJs = read(path.resolve(__dirname, '..', 'components', 'sidebar', 'sidebar.js'));

  // 分组：我的账号 / 个人 / 设置
  assert.ok(userChipJs.includes('我的账号'), '设置弹层应有「我的账号」分组');
  assert.ok(userChipJs.includes('个人'), '设置弹层应有「个人」分组');
  assert.ok(userChipJs.includes('设置'), '设置弹层应有「设置」分组');
  assert.ok(userChipJs.includes('修改密码') && userChipJs.includes('退出登录'), '我的账号应含改密码与退出登录');
  assert.ok(userChipJs.includes('修改昵称') && userChipJs.includes('头像'), '个人应含改昵称与改头像');
  assert.ok(userChipJs.includes('外观主题') && userChipJs.includes('定位权限'), '设置应含主题与定位权限');

  // 主题三态 + 定位开关 + 头像偏好
  assert.ok(userChipJs.includes("data-theme-opt") && userChipJs.includes('跟随系统'), '主题应是三态分段控件');
  assert.ok(userChipJs.includes("role', 'switch") && userChipJs.includes('aria-checked'), '定位权限应用开关（带 role/aria）');
  assert.ok(prefsJs.includes('avatar') && prefsJs.includes('location'), '头像与定位偏好应本地持久化');
  assert.ok(iconsJs.includes("name") && iconsJs.includes('mic'), '应有统一 SVG 图标库（含话筒）');
  assert.ok(!sidebarJs.includes('📅') && !sidebarJs.includes('⚙️'), '侧栏导航不应再用 emoji 图标');
});

test('图标统一：话筒/定位/铃铛等 emoji 全部换 SVG', () => {
  const voiceJs = read(path.resolve(__dirname, '..', 'components', 'voice-input', 'voice-input.js'));
  const locationJs = read(path.resolve(__dirname, '..', 'components', 'location-picker', 'location-picker.js'));
  const reminderJs = read(path.resolve(__dirname, '..', 'components', 'reminder-center', 'reminder-center.js'));
  const indexHtml = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  assert.ok(!voiceJs.includes('🎤') && !voiceJs.includes('⏹'), '话筒/停止应换成 SVG');
  assert.ok(voiceJs.includes('Icons.svg'), '语音按钮应走图标库');
  assert.ok(!locationJs.includes('📍') && !locationJs.includes('✏️'), '地图标点/框地应换成 SVG');
  assert.ok(!reminderJs.includes('🔔') && !reminderJs.includes('⛈') && !reminderJs.includes('🌡'), '铃铛/天气类型应换成 SVG');
  assert.ok(!indexHtml.includes('✏️ 地图框地') && !indexHtml.includes('🛰') && !indexHtml.includes('📷'), '首页按钮不应再用 emoji');
});

// ---------------- T27 地理抽检回归基线（补齐机制） ----------------

test('T27 抽检基线：20 个地块 + 面积自动回归 + 坡度坡向待人工', () => {
  const base = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'geo-baseline.json')));
  const test = read(path.resolve(__dirname, '..', '..', 'backend', 'test', 'com', 'cropcalendar', 'service', 'geo', 'GeoBaselineTest.java'));
  const terrain = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'geo', 'TerrainService.java'));
  const terrainTest = read(path.resolve(__dirname, '..', '..', 'backend', 'test', 'com', 'cropcalendar', 'service', 'geo', 'TerrainServiceTest.java'));

  assert.ok(base.plots.length >= 20, '基线应覆盖 20 个地块');
  base.plots.forEach((p) => {
    assert.ok(p.expectedAreaMu > 0, p.name + ' 应有期望面积');
    assert.ok(p.ring.length >= 3, p.name + ' 多边形应至少 3 个点');
  });
  // 基线要含坡地样本，坡度/坡向回归才有意义（否则全是平地，坡向空转）
  const slopes = base.plots.filter((p) => p.terrain === 'slope');
  assert.ok(slopes.length >= 10, '基线应含足够坡地样本（≥10）');
  slopes.forEach((p) => {
    assert.ok(p.referenceSlopeDeg >= 1, p.name + ' 坡地应有坡度≥1°');
    assert.ok(p.referenceAspectDeg >= 0 && p.referenceAspectDeg < 360, p.name + ' 坡地应有坡向');
  });
  assert.ok(test.includes('polygonAreaSqm'), '面积回归应走球面面积公式');
  assert.ok(test.includes('0.005'), '面积应有容差，而不是精确相等');
  assert.ok(test.includes('referenceSlopeDeg'), '坡度/坡向应有签字字段');
  assert.ok(test.includes('坡地') && test.includes('slope'), '测试应校验坡地样本坡度/坡向');
  // 坡度/坡向拟合数学要抽出来离线可测，人工回填 reference 值后才有可信对照
  assert.ok(terrain.includes('fitPlane') && terrain.includes('atan2'), '坡度坡向拟合应为纯函数');
  assert.ok(terrainTest.includes('东向坡') && terrainTest.includes('南向坡'), '应有合成数据的坡向方向回归');
  // 签字工作表工具：拉 DEM 算坡度/坡向，产出可人工核对的表格 + computed JSON
  const worksheet = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'tool', 'SignoffWorksheet.java'));
  assert.ok(worksheet.includes('TerrainService.fitPlane'), '签字工作表应复用 TerrainService 拟合');
  assert.ok(worksheet.includes('referenceSlopeDeg') && worksheet.includes('geo-baseline-computed.json'), '工作表应产出可回填的 computed 结果');
});

// ---------------- T30 存储层可切换（file / mysql） ----------------

test('T30 存储层：BlobStore 抽象 + file/mysql 双后端 + 迁移回滚', () => {
  const iface = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'BlobStore.java'));
  const file = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'FileBlobStore.java'));
  const mysql = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'MysqlBlobStore.java'));
  const factory = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'StoreFactory.java'));
  const uds = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'user', 'UserDataStore.java'));
  const cfg = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'config', 'AppConfig.java'));
  const sql = read(path.resolve(__dirname, '..', '..', 'data', 'store-mysql.sql'));
  const reminderStore = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'reminder', 'ReminderStore.java'));
  const plotHistory = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'plot', 'PlotHistory.java'));

  assert.ok(iface.includes('interface BlobStore'), '应有存储抽象接口');
  assert.ok(factory.includes('mysqlOrFile') && factory.includes('FileBlobStore'), '工厂应按配置选择后端');
  assert.ok(factory.includes('回落到 file'), 'mysql 不可用应回落 file，数据不丢');
  assert.ok(mysql.includes('java.sql'), 'MySQL 后端应只用 JDK 的 java.sql，编译零依赖');
  assert.ok(mysql.includes('REPLACE INTO'), '迁移与写入应幂等（REPLACE INTO）');
  assert.ok(uds.includes('StoreFactory.userData()'), 'UserDataStore 应走存储抽象');
  assert.ok(cfg.includes('storeBackend'), '应有 store.backend 配置开关');
  assert.ok(sql.includes('CREATE TABLE') && sql.includes('utf8mb4'), '应有 MySQL 建表脚本');
  // 提醒、地块历史、传感器都接入存储抽象，file/mysql 可切换
  assert.ok(sql.includes('CREATE TABLE IF NOT EXISTS reminders'), '应有 reminders 表');
  assert.ok(sql.includes('CREATE TABLE IF NOT EXISTS plot_history'), '应有 plot_history 表');
  assert.ok(sql.includes('CREATE TABLE IF NOT EXISTS sensors'), '应有 sensors 表');
  assert.ok(reminderStore.includes('StoreFactory.forCollection("reminders")'), '提醒档案应走存储抽象');
  assert.ok(plotHistory.includes('StoreFactory.forCollection("plot_history")'), '地块历史应走存储抽象');
  assert.ok(plotHistory.includes('public static void delete'), '地块历史应支持按用户删除（注销清理）');
  // 调度器枚举要能跨后端：BlobStore 提供 listKeys，File/Mysql 各自实现
  assert.ok(iface.includes('listKeys'), '存储抽象应有 listKeys 枚举能力');
  assert.ok(file.includes('listKeys'), '文件后端应实现 listKeys');
  assert.ok(mysql.includes('listKeys') && mysql.includes('SELECT k'), 'MySQL 后端应实现 listKeys');
});

// ---------------- 时间线远期节点常年气候兜底（合并队友改动） ----------------

test('时间线常年气候兜底：远期节点用常年气候概览，且字段与前端对齐', () => {
  const orch = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'orchestration', 'TimelineOrchestrator.java'));
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  const logic = read(path.resolve(__dirname, '..', '..', 'shared', 'utils', 'timeline-logic.js'));

  assert.ok(orch.includes('按常年气候规划'), '无实时天气时应给常年气候概览，而不是空');
  assert.ok(orch.includes('weatherIconOf') && orch.includes('climateIcon'), '短期用预报图标、长期用气候图标');
  assert.ok(orch.includes('suitabilityOf') && orch.includes('weatherFactorsOf'), '应输出适宜度与天气因子');
  assert.ok(orch.includes('climateNormal'), '远期节点应标记 climateNormal');
  assert.ok(handler.includes('n.climateNormal()'), '接口应透传 climateNormal 而不是写死 false');
  assert.ok(handler.includes('n.weatherFactors()'), '接口应透传天气因子');
  assert.ok(logic.includes('getWeatherIcon') && logic.includes('getSuitability') && logic.includes('normalizeFactors'), '前端应已能消费这三个字段');
});

// ---------------- 健壮性加固（本轮） ----------------

test('健壮性：body 限流 + MySQL 降级文件 + 推荐 fellBack 提示', () => {
  const hs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'HttpSupport.java'));
  const mysql = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'store', 'MysqlBlobStore.java'));
  const js = read(path.resolve(__dirname, '..', 'assets', 'js', 'recommend.js'));

  assert.ok(hs.includes('readNBytes(maxBytes + 1)'), '请求体应限量读取，不能 readAllBytes 全量吞下');
  assert.ok(hs.includes('Content-Length'), '应先看 Content-Length 提前拒绝大载荷');

  assert.ok(mysql.includes('fileFallback'), 'MySQL 后端应持有文件后端做降级');
  assert.ok(mysql.includes('已降级到文件存储'), '连接失败应降级到文件而不是 500');
  assert.ok(mysql.includes('fileFallback.write'), '写失败应落到文件');

  assert.ok(js.includes('data.fellBack'), '前端应处理 fellBack（无气候匹配）');
  assert.ok(js.includes('没有匹配当地气候带的作物'), 'fellBack 时应明确提示而不是假装都合适');
});

// ---------------- 健壮性优化（本轮：IO / 坐标 / XSS / 撞库防泄漏） ----------------

test('健壮性：传感器调度批量 IO + 推荐坐标校验 + XSS + 撞库防泄漏', () => {
  const sched = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorScheduler.java'));
  const sstore = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorStore.java'));
  const poller = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'sensor', 'SensorPoller.java'));
  const rec = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'RecommendHandler.java'));
  const auth = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'AuthHandler.java'));
  const adminJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin.js'));
  const serviceJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
  const plotsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));

  // 传感器调度：一次 load、批量处理、一次 save，不再每台设备各 load+save
  assert.ok(sstore.includes('mutatePlot'), '应有批量处理入口 mutatePlot');
  assert.ok(sstore.includes('appendReadingInto'), '应有纯内存追加读数');
  assert.ok(poller.includes('pollDeviceInto'), '应有批量路径 pollDeviceInto');
  assert.ok(sched.includes('mutatePlot') && sched.includes('processPlot'), '调度器应走批量路径');

  // 推荐坐标范围校验
  assert.ok(rec.includes('lat < -90 || lat > 90'), '纬度越界应 400');
  assert.ok(rec.includes('lon < -180 || lon > 180'), '经度越界应 400');

  // 撞库防护：追踪 map 有容量守卫 + 过期清理，不再无限增长
  assert.ok(auth.includes('MAX_TRACK'), '撞库追踪应有容量上限');
  assert.ok(auth.includes('guardCapacity'), '应有容量守卫');
  assert.ok(auth.includes('LOCK_UNTIL.remove(key)'), '锁定到期应惰性清除');

  // XSS：用户昵称与外部地理数据一律走 textContent，不进 innerHTML
  assert.ok(adminJs.includes('whoDiv.textContent'), '反馈气泡昵称应走 textContent');
  assert.ok(serviceJs.includes('whoDiv.textContent'), '反馈气泡昵称应走 textContent');
  assert.ok(adminJs.includes("querySelector('.service-item-meta').textContent"), '工单 meta 昵称应走 textContent');
  assert.ok(plotsJs.includes('createTextNode'), '附近河流名应走 createTextNode');
});

// ---------------- 健壮性：请求体/上游响应统一限流（本轮） ----------------

test('健壮性：所有请求体与上游响应都限量读取，不再 readAllBytes 全量吞', () => {
  const hs = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'HttpSupport.java'));
  const chart = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'ChartHandler.java'));
  const sow = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowCheckHandler.java'));
  const tl = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  const me = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'MeHandler.java'));
  const recog = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'RecognitionHandler.java'));
  const llm = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'LlmService.java'));
  const gi = read(path.resolve(__dirname, '..', '..', '.gitignore'));

  assert.ok(hs.includes('bodyBytes') && hs.includes('readNBytes(maxBytes + 1)'), 'HttpSupport 应提供带上限的限量读取');
  assert.ok(chart.includes('HttpSupport.body(ex)'), 'ChartHandler 应走限量读取');
  assert.ok(sow.includes('HttpSupport.body(ex)') && sow.includes('413'), 'SowCheckHandler 超限应 413');
  assert.ok(tl.includes('HttpSupport.body(ex)') && tl.includes('413'), 'TimelineHandler 超限应 413');
  assert.ok(me.includes('HttpSupport.body(ex, UserDataStore.MAX_BYTES)'), 'MeHandler 应按 512KB 限量');
  assert.ok(recog.includes('HttpSupport.bodyBytes(ex, 512 * 1024)'), 'RecognitionHandler 图片应按 512KB 限量');
  assert.ok(llm.includes('MAX_RESPONSE_BYTES') && llm.includes('readNBytes'), 'LLM 上游响应应限量');
  assert.ok(gi.includes('/lib/'), '.gitignore 应忽略服务器驱动目录');

  // 全仓不应再有 readAllBytes（业务路径都应收口到限量读取）
  const fs_ = require('fs');
  const offenders = [];
  (function walk(dir) {
    fs_.readdirSync(dir, { withFileTypes: true }).forEach((e) => {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) return walk(full);
      if (!e.name.endsWith('.java')) return;
      const text = fs_.readFileSync(full, 'utf8');
      if (text.includes('getRequestBody().readAllBytes()')) offenders.push(e.name);
    });
  })(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api'));
  assert.deepEqual(offenders, [], 'API 层不应再有 readAllBytes：' + offenders.join(', '));
});

// ---------------- 农事日历入口：地点可留空，不再卡死"请先补全" ----------------

test('农事日历入口：选通用作物名后地点不再被清空，地点留空按默认地点继续', () => {
  const html = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  // pickSuggestion 只在作物有产地时填地点，不把已填/已定位地点清成空串
  assert.ok(html.includes('if (c.city) locationInput.value = c.city;'), '选作物建议不应无条件覆盖地点为空串');
  // validate 不再把"地点必填"当作阻断项
  assert.ok(html.includes("setInvalid('field-location', false);"), '地点应可留空');
  assert.ok(!html.includes("if (!locationInput.value.trim()) { setInvalid('field-location', true); ok = false; }"), '地点必填的硬卡逻辑应已移除');
  // 留空时用默认地点并明确提示
  assert.ok(html.includes("location = '山东济宁';"), '地点留空应回落到默认地点');
  assert.ok(html.includes('未填地点，将按默认地点山东济宁计算'), '应提示按默认地点计算');
});

// ---------------- timeline 结果缓存（性能） ----------------

test('timeline 结果缓存：同作物同播期同地点直接命中，key 带日期跨天失效', () => {
  const th = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(th.includes('MAX_CACHE'), '应有缓存容量上限');
  assert.ok(th.includes('removeEldestEntry'), '应按访问序淘汰最久未用');
  assert.ok(th.includes('cached'), '应回传 cached 标记');
  assert.ok(th.includes('AppTime.today()'), '缓存 key 应带日期，跨天自动失效');
  assert.ok(th.includes('Math.round(lat * 100)'), 'key 应含地点，换地点重新计算');
  assert.ok(th.includes('areaMu'), 'key 应含面积，换面积重新计算');
  assert.ok(th.includes('AppConfig.cacheTtlSeconds()'), 'TTL 应由配置控制');
});

// ---------------- CropRegistry 归一（补测试 + 修项目根 cwd 读取路径） ----------------

test('CropRegistry：别名/拼音/规程 id 归一与模糊闸门有单测，且项目根 cwd 能读到规程', () => {
  const reg = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropRegistry.java'));
  const t = read(path.resolve(__dirname, '..', '..', 'backend', 'test', 'com', 'cropcalendar', 'service', 'crop', 'CropRegistryTest.java'));

  assert.ok(reg.includes('backend/crop-protocols.json'), 'PROTOCOL_PATHS 应含项目根 cwd 路径（否则测试/项目根运行读不到规程）');
  assert.ok(reg.includes('allowFuzzy'), '应有模糊匹配闸门');
  assert.ok(t.includes('西红柿'), '应测别名归一');
  assert.ok(t.includes('shuidao'), '应测拼音归一');
  assert.ok(t.includes('rice'), '应测规程 id 反查');
  assert.ok(t.includes('大豆'), '应测首字不同的模糊闸门');
});

// ---------------- 完善性优化五轮（错误重试/防重复/触控/无障碍/反馈） ----------------

test('完善性：错误态带重试、写操作防重复、触控热区、label 关联、保存反馈', () => {
  const uiState = read(path.resolve(__dirname, '..', 'assets', 'js', 'ui-state.js'));
  const rec = read(path.resolve(__dirname, '..', 'assets', 'js', 'recommend.js'));
  const svc = read(path.resolve(__dirname, '..', 'assets', 'js', 'service.js'));
  const adm = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin.js'));
  const plots = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-plots.js'));
  const crops = read(path.resolve(__dirname, '..', 'assets', 'js', 'my-crops.js'));
  const css = read(path.join(CSS, 'common.css'));
  const html = read(path.resolve(__dirname, '..', 'pages', 'index.html'));

  // 1. 错误态带重试
  assert.ok(rec.includes('UiState.error') && rec.includes('onRetry'), '推荐错误态应带重试');
  assert.ok(svc.includes('UiState.error') && svc.includes('onRetry: function () { openDetail(id); }'), '反馈详情错误态应带重试');
  assert.ok(plots.includes('onRetry: function () { analyzePolygon'), '地块分析失败应带重试');
  assert.ok(adm.includes('onRetry: load') && adm.includes('onRetry: function () { openDetail(id); }'), '后台错误态应带重试');

  // 2. 写操作防重复
  assert.ok(svc.includes("submitBtn.disabled = true"), '工单提交应防重复');
  assert.ok(svc.includes("btn.textContent = '发送中…'"), '回复应防重复');
  assert.ok(adm.includes("sendBtn.disabled = true") && adm.includes("closeBtn.disabled = true"), '后台回复/关闭应防重复');

  // 3. 触控热区
  assert.ok(css.includes('map-picker-btn') && css.includes('width: 40px'), '地图标点按钮移动端应放大到 40px');

  // 4. label 关联
  assert.ok(html.includes('label for="mc-crop-name"') && html.includes('id="plot-type"'), '表单 label 应有关联 for/id');

  // 5. 保存反馈
  assert.ok(uiState.includes('window.showToast'), '应有全局 toast');
  assert.ok(plots.includes("showToast('地块已保存'"), '保存地块应有反馈');
  assert.ok(crops.includes("showToast('作物已保存'"), '保存作物应有反馈');
});

// ---------------- 完善性：工单后台页与账号页四态补齐（loading/empty/error+重试） ----------------

test('完善性：工单后台页与账号页补齐四态与重试', () => {
  const ticketsHtml = read(path.resolve(__dirname, '..', 'pages', 'admin-tickets.html'));
  const ticketsJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin-tickets.js'));
  const accountHtml = read(path.resolve(__dirname, '..', 'pages', 'account.html'));
  const accountJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'account.js'));

  // 两个页面都要引入四态组件
  assert.ok(ticketsHtml.includes('ui-state.js'), '工单后台页应引入 ui-state.js');
  assert.ok(accountHtml.includes('ui-state.js'), '账号页应引入 ui-state.js');

  // 工单后台：列表三态（loading/empty/error 均带重试）
  assert.ok(ticketsJs.includes("UiState.loading(emptyEl"), '工单列表应有加载态');
  assert.ok(ticketsJs.includes("UiState.empty(emptyEl"), '工单列表应有空态');
  assert.ok(ticketsJs.includes('UiState.error') && ticketsJs.includes('onRetry'), '工单列表错误态应带重试');

  // 账号页：读取失败不再卡在「正在读取…」，有错误态+重试
  assert.ok(accountJs.includes('showLoadError'), '账号页应有读取失败处理');
  assert.ok(accountJs.includes('UiState.error') && accountJs.includes('onRetry: load'), '账号页错误态应带重试');
  assert.ok(!accountJs.includes('网络异常，账号信息读取失败'), '读取失败不应只弹 toast，要落到页面错误态');
});

// ---------------- 完善性：时间线页去掉本地假数据兜底，改错误态+重试（诚实红线） ----------------

test('完善性：时间线后端不可达走错误态，不拿本地假数据冒充日历', () => {
  const tl = read(path.resolve(__dirname, '..', 'pages', 'timeline.html'));

  // 不再引入 mock 假数据，也不再回退到 MOCK_TIMELINE
  assert.ok(!tl.includes('mock-data.js'), '时间线页不应再引入 mock 假数据');
  assert.ok(!tl.includes('MOCK_TIMELINE'), '时间线页不应再回退到本地假数据');
  assert.ok(tl.includes('ui-state.js'), '时间线页应引入四态组件');
  assert.ok(tl.includes('showTimelineError'), '应有时间线读取失败处理');
  assert.ok(tl.includes('UiState.error') && tl.includes('onRetry'), '时间线错误态应带重试');
  assert.ok(!tl.includes("return window.MOCK_TIMELINE"), '失败时不应返回假数据');
});

// ---------------- 完善性：超管后台错误反馈从 alert 改 toast，不打断操作 ----------------

test('完善性：超管后台错误反馈走 toast，保留确认类交互', () => {
  const adminHtml = read(path.resolve(__dirname, '..', 'pages', 'admin.html'));
  const consoleJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'admin-console.js'));

  assert.ok(adminHtml.includes('ui-state.js'), '超管后台应引入四态组件（含全局 toast）');
  assert.ok(consoleJs.includes('function toast'), '后台应有 toast 兜底');
  // 错误/提示不再用 alert 打断操作；确认类 confirm 仍保留
  assert.ok(consoleJs.includes("toast('操作失败：' + e.message, 'error')"), '封号/改角色失败应走 toast');
  assert.ok(!/\.catch\(function \(e\) \{ alert\(/.test(consoleJs), '错误回调不应再弹 alert');
  assert.ok(consoleJs.includes("confirm('确定封禁"), '封号等破坏性操作仍应二次确认');
  // 账号/文件/日志三个面板也要有四态 + 重试，不留「加载失败」一行灰字
  assert.ok(consoleJs.includes('showError') && consoleJs.includes('onRetry'), '后台面板错误态应带重试');
  assert.ok(consoleJs.includes("showError(usersEmpty"), '账号面板加载失败应走错误态');
  assert.ok(consoleJs.includes("showError(fsEmpty"), '文件面板加载失败应走错误态');
  assert.ok(consoleJs.includes("showError(auditEmpty"), '日志面板加载失败应走错误态');
  assert.ok(consoleJs.includes('showEmpty'), '后台面板空态应走统一组件');
});

test('V5.4/V5.5 播期校核：100 种作物有精确月份，且区分南北窗口', () => {
  const props = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'properties.json')));
  const checked = props.crops.filter((c) => Array.isArray(c.sowMonths) && c.sowMonths.length);
  // V6.0：库里 400 种必须全部有播期窗口，不再有"只能靠季节词猜"的空条目
  assert.strictEqual(checked.length, props.crops.length,
    '每种作物都应有播期窗口，缺 ' + (props.crops.length - checked.length) + ' 种');

  // 南方窗口：至少 90 种有，且月份合法
  const south = checked.filter((c) => Array.isArray(c.sowMonthsSouth) && c.sowMonthsSouth.length);
  assert.ok(south.length >= 100, '应有不少于 100 种带南方窗口，实际 ' + south.length);

  // 精度三层，如实区分：逐条查证 / 继承母本 / 按类别推定
  const derived = checked.filter((c) => c.sowMonthsPrecision === 'derived');
  const inherited = checked.filter((c) => c.sowMonthsPrecision === 'inherited');
  const researched = checked.filter((c) => !c.sowMonthsPrecision || c.sowMonthsPrecision === 'researched');
  assert.ok(researched.length >= 240, '逐条查证应不少于 240 种，实际 ' + researched.length);
  assert.ok(inherited.length >= 110, '继承母本应不少于 110 种，实际 ' + inherited.length);
  assert.ok(derived.length >= 35, '按类别推定应不少于 35 种，实际 ' + derived.length);
  // 野生/共生菌单独一档：不能拿"播期"糊弄
  const wild = checked.filter((c) => c.sowMonthsPrecision === 'wild');
  assert.ok(wild.length >= 2, '松茸/鸡枞这类应有野生标记，实际 ' + wild.length);
  for (const c of wild) {
    assert.ok(c.sowMonthsNote.includes('共生') || c.sowMonthsNote.includes('野生'),
      c.displayName + ' 标为野生却没写原因');
  }
  // 推定不是"没来源"的借口：必须写清依据，且注明未逐品种校核
  for (const c of derived) {
    assert.ok(c.sowMonthsSource && c.sowMonthsSource.trim(),
      c.displayName + ' 判为推定却没写依据');
    assert.ok(c.sowMonthsSource.includes('推定') || c.sowMonthsSource.includes('通用')
      || c.sowMonthsSource.includes('常识') || c.sowMonthsSource.includes('栽培方式'),
      c.displayName + ' 的推定来源应说明依据类型：' + c.sowMonthsSource);
    assert.ok(c.sowMonthsNote && c.sowMonthsNote.trim(),
      c.displayName + ' 推定作物应写地区/茬口说明');
  }

  // 继承必须写明母本，不能只说"按近缘作物推定"
  for (const c of inherited) {
    assert.ok(c.sowMonthsSource && c.sowMonthsSource.includes('母本'),
      c.displayName + ' 继承母本却没写明母本');
    assert.ok(c.sowMonthsSource.includes('继承'), c.displayName + ' 继承来源应说明是继承');
  }
  const fuji = inherited.find((c) => c.displayName === '红富士');
  assert.ok(fuji && fuji.sowMonthsSource.includes('苹果'), '红富士应继承苹果的窗口');
  const kuerle = inherited.find((c) => c.displayName === '库尔勒梨');
  assert.ok(kuerle && kuerle.sowMonthsSource.includes('梨'), '库尔勒梨应继承梨的窗口');

  // 逐条查证的作物必须有具体来源（不能是"资料综合"这种笼统说法之外的空值）
  for (const c of researched) {
    assert.ok(c.sowMonthsSource && c.sowMonthsSource.trim(),
      c.displayName + ' 已校核却没写来源');
  }

  // 本轮查证过的窗口（防回归：别再被"按季节推导"覆盖）
  const expect = {
    '羽衣甘蓝': [2, 3, 6, 7],
    '乌塌菜': [10, 11],
    '红菜苔': [7, 8, 9, 10],
    '芝麻菜': [4, 5, 8, 9],
    '水芹': [8, 9],
    '菱角': [3, 4],
    '香菇': [8, 9, 10],
    '羊肚菌': [10, 11],
    '莜麦': [4, 5],
    '抱子甘蓝': [2, 6, 7],
    '荠菜': [3, 4, 7, 8, 9],
    '蒜苗': [2, 7, 8, 9, 10],
    '板蓝根': [3, 4, 5, 6],
    '穿心莲': [3, 4],
    '树莓': [3, 4, 10, 11],
    '黑莓': [2, 3],
    '蔓越莓': [4],
    '杨梅': [2, 3, 10, 11],
    '三七': [11, 12, 1],
    '薰衣草': [3, 4, 10],
    '黄花菜': [4, 5, 9, 10],
    '马兰头': [3, 4, 8, 9],
    '蕨菜': [3, 4, 9, 10],
    '香葱': [3, 4, 5, 9, 10],
    '分葱': [4, 5, 9, 10],
    '车前草': [3, 4, 10],
    '青稞': [3, 4, 5],
    '黑麦': [9, 10],
    '籽粒苋': [5, 6],
    '亚麻': [4, 5],
    '苎麻': [3, 4, 8],
  };
  const idx = Object.fromEntries(props.crops.map((x) => [x.displayName, x]));
  for (const [name, months] of Object.entries(expect)) {
    const c = idx[name];
    assert.ok(c, '库里应有 ' + name);
    assert.deepStrictEqual(c.sowMonths, months, name + ' 的播期应为本轮查证值');
    assert.notStrictEqual(c.sowMonthsPrecision, 'derived', name + ' 已查证，不应再标推定');
  }

  // 西南窗口：至少 40 种
  const sw = checked.filter((c) => Array.isArray(c.sowMonthsSouthwest) && c.sowMonthsSouthwest.length);
  assert.ok(sw.length >= 40, '应有不少于 40 种带西南窗口，实际 ' + sw.length);
  for (const c of south) {
    assert.ok(c.sowMonthsSouth.every((m) => Number.isInteger(m) && m >= 1 && m <= 12),
      c.displayName + ' 的 sowMonthsSouth 含非法月份');
    assert.ok(new Set(c.sowMonthsSouth).size === c.sowMonthsSouth.length,
      c.displayName + ' 的 sowMonthsSouth 有重复月份');
  }

  const seen = new Set();
  for (const c of checked) {
    assert.ok(c.sowMonths.every((m) => Number.isInteger(m) && m >= 1 && m <= 12),
      c.displayName + ' 的 sowMonths 含非法月份');
    assert.ok(new Set(c.sowMonths).size === c.sowMonths.length, c.displayName + ' 的 sowMonths 有重复月份');
    assert.ok(c.sowMonthsNote && c.sowMonthsNote.trim(), c.displayName + ' 缺播期说明（地区/茬口）');
    assert.ok(c.totalDays > 0, c.displayName + ' 生育期应大于 0');
    assert.ok(!seen.has(c.displayName), '作物重复：' + c.displayName);
    seen.add(c.displayName);
  }

  // 关键作物的人工核对值：防止后续误改回粗粒度季节
  const byName = Object.fromEntries(checked.map((c) => [c.displayName, c]));
  assert.deepStrictEqual(byName['大白菜'].sowMonths, [8], '大白菜应是华北立秋前后（8 月）');
  assert.deepStrictEqual(byName['萝卜'].sowMonths, [8], '秋萝卜应是 8 月');
  assert.deepStrictEqual(byName['洋葱'].sowMonths, [9, 10], '洋葱应秋播 9–10 月');
  assert.ok(byName['生姜'].totalDays >= 150, '生姜生育期不应仍是 90 天');
  assert.ok(byName['芋头'].totalDays >= 180, '芋头生育期不应仍是 90 天');
  assert.strictEqual(byName['西兰花'].lifespan, 'annual', '西兰花是一年生，不是多年生');

  // 南北窗口确实不同：以叶菜和果菜为例
  assert.deepStrictEqual(byName['大白菜'].sowMonthsSouth, [9, 10], '大白菜南方应 9–10 月');
  assert.deepStrictEqual(byName['番茄'].sowMonthsSouth, [8, 9, 10], '番茄南方应以秋茬 8–10 月为主');
  assert.deepStrictEqual(byName['菠菜'].sowMonthsSouth, [10, 11], '菠菜南方应 10–11 月');
  assert.notDeepStrictEqual(byName['马铃薯'].sowMonths, byName['马铃薯'].sowMonthsSouth,
    '马铃薯南方冬种窗口应与北方不同');
});

test('V5.5 南北窗口按纬度切换：26.5°N 以南用南方窗口', () => {
  const win = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'PlantingWindow.java'));
  assert.ok(win.includes('SOUTH_LAT = 26.5'), '应有南方分界纬度');
  assert.ok(win.includes('public static boolean useSouth'), '应有南北窗口选择');
  assert.ok(win.includes('p.sowMonthsSouthwest()'), '西南应使用西南窗口');
  assert.ok(win.includes('p.sowMonthsSouth()'), '华南应使用南方窗口');
  assert.ok(win.includes('Region.SOUTHWEST'), '应有西南窗口');
  assert.ok(win.includes('region.json()'), '窗口应标注地域');
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowWindowHandler.java'));
  assert.ok(handler.includes('"region"'), '接口应返回窗口地域');
});

test('V5.8 本地露地种不熟：不给推荐日，只给结论与参考日', () => {
  const base = path.resolve(__dirname, '..');
  const win = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'PlantingWindow.java'));
  // 给日期就等于承诺：种不熟时必须把推荐日/最晚日/收获日全部置空
  assert.ok(win.includes('boolean suitable'), '窗口应带"能不能种成"的结论位');
  assert.ok(win.includes('String fallbackDate'), '应给参考日，供"看参考日历"使用');
  assert.ok(win.includes('suitable ? chosen.recommended().toString() : null'),
    '不可种时推荐日必须置空');
  assert.ok(win.includes('露地种不熟'), '应明确给出"露地种不熟"的结论');
  assert.ok(win.includes('建议改保护地栽培'), '应给出替代方案而不是一句"不行"');
  // 三条不该误判成"种不熟"的情形（实测踩过：白芍/郁金香/芥菜/广州甜菜被误判）
  assert.ok(win.includes('MATURITY_TOLERANCE_DAYS'), '收获略晚于初霜应给容差，而不是直接判死');
  assert.ok(win.includes('overwinter'), '秋植/越冬作物跨年收获应视为正常农事');
  assert.ok(win.includes('frostFreeDays(lat) >= 350'), '全年无霜区不应受年份边界限制');
  assert.ok(win.includes('喜温') && win.includes('热带'), '喜温/热带作物才不给轻霜余量');

  const handler = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowWindowHandler.java'));
  assert.ok(handler.includes('"suitable"'), '接口应返回 suitable');
  assert.ok(handler.includes('"fallbackDate"'), '接口应返回参考日');

  const timeline = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));
  assert.ok(timeline.includes('w.fallbackDate()'), '无推荐日时时间线应退回参考日');

  const indexHtml = read(path.join(base, 'pages', 'index.html'));
  assert.ok(indexHtml.includes('本地不宜露地种植'), '前端应直说"本地不宜露地种植"');
  assert.ok(indexHtml.includes('sow-auto-force'), '应提供"看参考日历"入口');
  assert.ok(indexHtml.includes('info.suitable === false'), '不可种时不应自动生成日历');
  // 没有推荐日不等于请求失败，前端不能把它当成错误（否则只会弹"播期推算失败"）
  assert.ok(!/body\.data && body\.data\.sowDate/.test(indexHtml),
    '不应把"缺 sowDate"当成请求失败');
  // 地点解析失败会退回默认地点，等于按另一个省算霜期 —— 必须重试并在卡片上明示
  assert.ok(indexHtml.includes('resolveLocationLLOnce'), '地点解析应支持重试');
  assert.ok(indexHtml.includes('__geoUnresolved'), '地点未解析时卡片要写明按默认地点估算');

  const css = read(path.join(base, 'assets', 'css', 'common.css'));
  assert.ok(css.includes('.sow-auto.is-unsuitable'), '不可种应有独立配色，不能复用"已就绪"的绿色');
  assert.ok(css.includes('--warn-soft'), '应有警示底色变量（深浅色都要给值）');
});

test('V6.0 作物库完整：400 种全部可浏览，且带类别/窗口/精度', () => {
  const base = path.resolve(__dirname, '..');
  const handler = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'CropsHandler.java'));
  // 无关键词时不再只返回"有规程的 21 种"
  assert.ok(handler.includes('CropRegistry.all()'), '作物库列表应遍历全部作物');
  assert.ok(handler.includes('"categories"'), '应返回类别汇总，前端才能分组');
  assert.ok(handler.includes('"windowPrecision"'), '每条应带播期精度');
  assert.ok(handler.includes('"propsSource"'), '每条应带属性来源，别把推定说成实测');
  assert.ok(handler.includes('"limit"') || handler.includes('limit'), '应支持分页参数，避免一次吐太多');

  const registry = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropRegistry.java'));
  assert.ok(registry.includes('public static List<Entry> all()'), '注册表应能列出全部作物');

  const indexHtml = read(path.join(base, 'pages', 'index.html'));
  assert.ok(indexHtml.includes('suggest-group'), '浏览全部品种时应按类别分组');
  assert.ok(indexHtml.includes('is-browse'), '浏览模式应有独立样式（更高的下拉）');
  assert.ok(indexHtml.includes('function metaOf'), '每个品种应显示播期与精度');
  assert.ok(indexHtml.includes('共 ' + "' + suggestHits.length + '" + ' 种作物'), '应告诉用户库里到底有多少种');

  const css = read(path.join(base, 'assets', 'css', 'common.css'));
  assert.ok(css.includes('.suggest-group'), '类别分组标题需要样式');
  assert.ok(css.includes('.suggestions.is-browse'), '浏览模式需要更大的下拉高度');
});

test('V6.2 野生/共生菌不提供播种建议', () => {
  const base = path.resolve(__dirname, '..');
  const win = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar',
    'service', 'crop', 'PlantingWindow.java'));
  assert.ok(win.includes('"wild".equals(p.precision())'), '窗口应识别野生菌');
  // 境外不走中国霜期判定，但野生菌任何情况下都必须判为不可播
  assert.ok(win.includes('(overseas || chosen.canMature()) && !wild'), '野生菌必须判为不可播（境外也不例外）');
  assert.ok(win.includes('本条目仅作采收季参考'), '应说明只作采收参考');

  const handler = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'CropsHandler.java'));
  assert.ok(handler.includes('不适用（野生/共生）'), '作物库里不应给它显示播种月份');

  const indexHtml = read(path.join(base, 'pages', 'index.html'));
  assert.ok(indexHtml.includes('不适用播期：野生 / 共生菌'), '前端应直说无法人工栽培');
  assert.ok(indexHtml.includes("p === 'wild'"), '下拉里应标"野生/共生"');
});

test('V6.5 区域窗口扩到五套：东北/西北不再套华北基准', () => {
  const base = path.resolve(__dirname, '..');
  const win = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'PlantingWindow.java'));
  assert.ok(win.includes('NORTHEAST("northeast")'), '应有东北窗口');
  assert.ok(win.includes('NORTHWEST("northwest")'), '应有西北窗口');
  assert.ok(win.includes('"东北".equals(zone)'), '应按 AgroZone 分区分流东北');
  assert.ok(win.includes('p.hasNortheast()'), '东北有专门窗口时应用专门窗口');
  assert.ok(win.includes('东北暂无该作物的专门窗口'), '没有专门窗口时必须说明按华北基准');

  // 数据：东北、西北各有一批真实窗口，且与华北不同
  const props = JSON.parse(read(path.resolve(base, '..', 'data', 'crop-base', 'properties.json')));
  const idx = Object.fromEntries(props.crops.map((c) => [c.displayName, c]));
  const ne = props.crops.filter((c) => Array.isArray(c.sowMonthsNortheast) && c.sowMonthsNortheast.length);
  const nw = props.crops.filter((c) => Array.isArray(c.sowMonthsNorthwest) && c.sowMonthsNorthwest.length);
  assert.ok(ne.length >= 16, '东北窗口应不少于 16 种，实际 ' + ne.length);
  assert.ok(nw.length >= 13, '西北窗口应不少于 13 种，实际 ' + nw.length);

  // 东北必须比华北晚：番茄华北 1–3 月，东北只有 3 月
  assert.deepStrictEqual(idx['番茄'].sowMonthsNortheast, [3], '东北番茄应 3 月育苗');
  assert.deepStrictEqual(idx['玉米'].sowMonthsNortheast, [5], '东北玉米应 5 月播');
  assert.deepStrictEqual(idx['大白菜'].sowMonthsNortheast, [7], '东北大白菜应 7 月中下播');
  assert.deepStrictEqual(idx['棉花'].sowMonthsNorthwest, [4], '西北棉花应 4 月播');
  assert.ok(idx['番茄'].sowMonthsNortheastNote.includes('终霜'), '东北番茄要说明终霜后定植');

  for (const c of ne.concat(nw)) {
    const m = c.sowMonthsNortheast || c.sowMonthsNorthwest;
    assert.ok(m.every((x) => Number.isInteger(x) && x >= 1 && x <= 12), c.displayName + ' 区域窗口月份非法');
  }
  // 高纬一年一熟：不能再排第二季
  const props2 = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropProperties.java'));
  assert.ok(props2.includes('lat >= 42'), '北纬 42° 以北应抑制第二季');

  const indexHtml = read(path.join(base, 'pages', 'index.html'));
  assert.ok(indexHtml.includes("'东北窗口'") && indexHtml.includes("'西北窗口'"), '界面应标出区域窗口');
});

test('V6.6 华东窗口 + 境外按半球气候带换算（不按国家分区）', () => {
  const base = path.resolve(__dirname, '..');
  const win = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'PlantingWindow.java'));
  // 华东
  assert.ok(win.includes('EAST("east")'), '应有华东窗口');
  assert.ok(win.includes('"长江流域".equals(zone)'), '长江中下游应自成一区');
  assert.ok(win.includes('华东（长江中下游）播期'), '依据里应标出华东');
  // 境外：不套中国大区，按半球+气候带
  assert.ok(win.includes('OVERSEAS("overseas")'), '应有境外分支');
  assert.ok(win.includes('public static Set<Integer> overseasMonths'), '境外应按月份换算');
  assert.ok(win.includes('if (lat < 0) shift += 6'), '南半球应整体平移 6 个月');
  const zone = read(path.resolve(base, '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'AgroZone.java'));
  assert.ok(zone.includes('isOverseas'), '应能判定境外坐标');
  assert.ok(zone.includes('lat < 18 || lat > 54 || lon < 73 || lon > 135'), '境外判定用中国大致范围');

  // 数据：华东 19 种，且与华北不同
  const props = JSON.parse(read(path.resolve(base, '..', 'data', 'crop-base', 'properties.json')));
  const idx = Object.fromEntries(props.crops.map((c) => [c.displayName, c]));
  const east = props.crops.filter((c) => Array.isArray(c.sowMonthsEast) && c.sowMonthsEast.length);
  assert.ok(east.length >= 19, '华东窗口应不少于 19 种，实际 ' + east.length);
  assert.deepStrictEqual(idx['小麦'].sowMonthsEast, [10, 11], '华东小麦应 10–11 月（华北是 9–10 月）');
  assert.deepStrictEqual(idx['大白菜'].sowMonthsEast, [8, 9], '华东大白菜应 8–9 月（比华北晚）');
  assert.deepStrictEqual(idx['番茄'].sowMonthsEast, [7, 8], '华东番茄以秋茬 7–8 月为主');
  assert.ok(idx['大白菜'].sowMonthsEastNote.includes('晚'), '要说明比华北晚');

  // 前端标签
  const indexHtml = read(path.join(base, 'pages', 'index.html'));
  assert.ok(indexHtml.includes("east: '华东窗口'"), '界面应标华东窗口');
  assert.ok(indexHtml.includes("overseas: '境外·气候带推算'"), '界面应标境外推算');
  assert.ok(indexHtml.includes('REGION_LABEL[w.region]'), '区域标签应查表而不是嵌套三元');
});

test('V6.7 天气卡要说清定位来源（设备/网络），避免与「所在地」混淆', () => {
  const base = path.resolve(__dirname, '..');
  const wv = read(path.join(base, 'assets', 'js', 'weather-visual.js'));
  assert.ok(wv.includes("'当前 ' + locationName"), '城市名前应加「当前」');
  assert.ok(wv.includes('与下方填写的「所在地」无关'), '应说明与所在地无关');
});

test('V6.8 天气走代理、渔业可定位选地块、城区该拦', () => {
  const base = path.resolve(__dirname, '..');
  const root = path.resolve(base, '..');

  // 1) 天气：后端代理优先，直连兜底
  const wh = read(path.join(root, 'backend', 'src', 'com', 'cropcalendar', 'api', 'WeatherHandler.java'));
  assert.ok(wh.includes('api.open-meteo.com'), '代理应转发到 Open-Meteo');
  assert.ok(wh.includes('CACHE_TTL_MS'), '应有短缓存，别每次都跨境');
  const server = read(path.join(root, 'backend', 'src', 'com', 'cropcalendar', 'Server.java'));
  assert.ok(server.includes('/api/weather'), '应注册天气路由');
  const wv = read(path.join(base, 'assets', 'js', 'weather-visual.js'));
  assert.ok(wv.includes("'/api/weather/now'"), '前端应优先走代理');
  assert.ok(wv.includes('OPEN_METEO_URL + qs'), '代理失败时才直连');

  // 2) 渔业：定位按钮 + 我的地块
  const html = read(path.join(base, 'pages', 'index.html'));
  assert.ok(html.includes('id="industry-locate"'), '渔业应有定位按钮');
  assert.ok(html.includes('id="industry-plot"'), '渔业应有地块选择');
  const ind = read(path.join(base, 'assets', 'js', 'industry.js'));
  assert.ok(ind.includes('MyPlotsStore'), '应从我的地块里取');
  assert.ok(ind.includes('function geocode('), '任意地名应走地理接口，别再靠硬编码城市表');
  assert.ok(!/return CITY_COORDS\[location\] \|\| \[30\.33/.test(ind),
    '不能把没命中的地名一律当荆州');

  // 3) 城区拦截
  const gf = read(path.join(root, 'backend', 'src', 'com', 'cropcalendar', 'service', 'geo', 'GeoFactors.java'));
  // 城区判定走本地区划库（外部 landuse 源在这台机器上全部超时）
  assert.ok(gf.includes('public static Urban urbanCheck'), '应能按区划距离判城区');
  assert.ok(gf.includes('CnGazetteer.nearest'), '城区判定应查本地库，不依赖外部服务');
  assert.ok(gf.includes('cityKm() <= 5'), '市中心 5 公里内应判城区');
  assert.ok(gf.includes('districtKm() <= 3'), '市辖区驻地 3 公里内应判城区');
  const geo = read(path.join(root, 'backend', 'src', 'com', 'cropcalendar', 'api', 'GeoHandler.java'));
  assert.ok(geo.includes('handleLandUse'), '应提供 /api/geo/landuse');
  const pa = read(path.join(root, 'backend', 'src', 'com', 'cropcalendar', 'api', 'PlotAnalyzeHandler.java'));
  assert.ok(pa.includes('urban-land'), '地块分析应给出城区限制');
  assert.ok(/if \(urbanPlot\) \{[\s\S]{0,80}level = "不适宜"/.test(pa), '城区地块不能给"适宜"');
  assert.ok(html.includes('function showUrbanBlock'), '首页应拦截城区坐标');
  assert.ok(html.includes('这里不是农用地'), '拦截文案要直说');
});

test('V6.1 作物分类：芽苗独立成类，且不被"麦/芝麻/红薯"带偏', () => {
  const tpl = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar',
    'service', 'crop', 'CropTemplates.java'));
  assert.ok(tpl.includes('SPROUT("芽苗类"'), '芽苗应独立成类，不能混进禾谷/豆类');
  assert.ok(tpl.includes('"黄豆芽"') && tpl.includes('"荞麦芽"'), '芽苗名表应覆盖常见芽苗');
  // 芽苗必须在禾谷之前判定，否则"荞麦芽"会被当成荞麦排一整季
  const sproutAt = tpl.indexOf('containsAny(name, SPROUT_NAMES)');
  const cerealAt = tpl.indexOf('containsAny(name, CEREAL_NAMES)');
  assert.ok(sproutAt > 0 && cerealAt > 0 && sproutAt < cerealAt, '芽苗判定必须早于禾谷');
  // 几个被关键字带偏的必须钉死
  assert.ok(tpl.includes('name.contains("油麦菜")'), '油麦菜含"麦"但不是禾谷');
  assert.ok(tpl.includes('name.contains("芝麻菜")'), '芝麻菜含"芝麻"但属叶菜');
  assert.ok(tpl.includes('name.contains("红薯叶")'), '红薯叶含"红薯"但属叶菜');
  assert.ok(tpl.includes('name.contains("红花菜豆")'), '红花菜豆属豆类');
  assert.ok(tpl.includes('name.contains("桑葚")'), '桑葚属果树，不是经济作物');
  assert.ok(tpl.includes('case SPROUT ->'), '芽苗需要自己的规程模板（一茬只有 7–15 天）');
});

// ---------------- V5.3：只给作物 + 地点，播期由系统推算 ----------------

test('V5.3 首页：播种日期不再是必填，改为自动推算并展示适宜播期', () => {
  assert.ok(indexHtml.includes('不用填，按作物和地点自动推算'), '播期应明确标为不用填');
  assert.ok(indexHtml.includes('id="sow-auto-main"'), '应有播期结果区');
  assert.ok(indexHtml.includes('id="sow-manual" hidden'), '手动指定应默认收起，不占主流程');
  assert.ok(indexHtml.includes('function applyWindowInfo'), '应把窗口渲染到页面');
  assert.match(indexHtml, /fetchSowWindow\(cropName, function \(err, info\)/, '提交时应先推算窗口');
  // 结果区拆成三个小标签：适宜播期 / 最晚播期 / 预计收获（长句粗体读不动）
  assert.ok(indexHtml.includes('id="sow-window-value"'), '应有适宜播期格');
  assert.ok(indexHtml.includes('id="sow-latest-value"'), '应有最晚播期格');
  assert.ok(indexHtml.includes('id="sow-harvest-value"'), '应有预计收获格');
  assert.ok(indexHtml.includes("setMetric('window', label, fmtRange(w.start, w.end)"), '应展示窗口起止');
  assert.ok(indexHtml.includes('fmtMD(info.latest.date)'), '应展示最晚播期');
  assert.ok(indexHtml.includes('h.date ? fmtMD(h.date)'), '应展示预计收获');
  // 手填日期仍然保留，不剥夺控制权
  assert.ok(indexHtml.includes("sowEditBtn.textContent = open ? '收起手动' : '手动指定'"), '手动指定应可展开/收起');
});

test('V5.3 首页：地点必须先解析成坐标，再算播期', () => {
  assert.ok(indexHtml.includes('function resolveLocationLL'), '应有地点解析');
  assert.ok(indexHtml.includes("'/api/geo/search?q='"), '应调用地理搜索接口');
  assert.ok(indexHtml.includes('function currentLL'), '应有统一的坐标选择');
  assert.ok(indexHtml.includes("window.__resolvedLL.text === location"), '同一地点不应重复解析');
  // 播期推算、播期校验、跳转时间线必须用同一份坐标，否则"北京"会按默认济宁算霜期
  assert.ok(indexHtml.includes('var ll = currentLL(cropName);'), '播期推算应用解析后的坐标');
  assert.ok(indexHtml.includes('var lat = ll.lat;'), '校验应复用同一坐标');
  assert.ok(indexHtml.includes('lat: ll.lat,'), '跳转时间线应复用同一坐标');
});

test('V5.3 后端：窗口与校验共用一套月份规则，且不给过去的日期', () => {
  const win = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'PlantingWindow.java'));
  const check = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowCheckHandler.java'));
  const handler = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'SowWindowHandler.java'));
  const timeline = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'api', 'TimelineHandler.java'));

  assert.ok(win.includes('public static Set<Integer> monthsFor'), '月份规则应集中在一处');
  assert.ok(check.includes('PlantingWindow.monthsFor(props, lat, lon)'), '播期校验必须复用窗口规则（含经纬度）');
  assert.ok(win.includes('public static Set<Integer> monthsFor(CropProperties.Props p, double lat)'), '窗口应做纬度粗调');
  assert.ok(!check.includes('AgroZone.sowMonths(props.suitableSeason())'), '不应保留第二套粗粒度季节规则');
  assert.ok(win.includes('if (e.isBefore(today)) continue'), '已结束的窗口不应再被选中');
  assert.ok(win.includes('canMature'), '应判断能否在霜前成熟');
  assert.ok(win.includes('本地无霜期约'), '霜前熟不了要说清原因');
  assert.ok(handler.includes('PlantingWindow.of(props, lat, lon, today)'), '播期接口应以窗口为主答案');
  assert.ok(handler.includes('"window", windowJson'), '响应应带窗口起止');
  assert.ok(timeline.includes('PlantingWindow.of(wp, lat, lon, AppTime.today())'), '时间线缺播期时应自动补推荐日');
});

test('V5.3 数据：400 种作物都能推导窗口，类别模板覆盖全部作物', () => {
  const props = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'crop-base', 'properties.json')));
  const tpl = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'service', 'crop', 'CropTemplates.java'));
  const stub = read(path.resolve(__dirname, '..', '..', 'backend', 'src', 'com', 'cropcalendar', 'compute', 'ComputeStub.java'));

  assert.ok(Array.isArray(props.crops) && props.crops.length >= 400, '作物库应覆盖 400 种');
  for (const c of props.crops) {
    assert.ok(c.displayName, '每种作物都要有名字');
    assert.ok(c.suitableSeason && c.suitableSeason.trim(), c.displayName + ' 缺适宜季节，窗口无从推导');
    assert.ok(c.totalDays > 0, c.displayName + ' 的 totalDays 应大于 0');
    assert.ok(['annual', 'perennial'].includes(c.lifespan), c.displayName + ' 的 lifespan 取值异常');
  }
  // 多年生不能排"播种"，否则又会出现"苹果 3 月播种 9 月收获"
  assert.ok(tpl.includes('perennial ? "定植" : "播种"'), '多年生应用"定植"而不是"播种"');
  assert.ok(tpl.includes('0.00, 1); add(p, 0.06, 3)'), '多年生果树首节点应是定植，不是修剪');
  assert.ok(tpl.includes('PERENNIAL_FRUIT'), '应有多年生果树模板');
  assert.ok(tpl.includes('MUSHROOM'), '应有食用菌模板（接种/发菌/出菇）');
  // 模板必须排在大模型之前，否则用户仍要等 30 秒
  const tplAt = stub.indexOf('CropTemplates.build');
  const llmAt = stub.indexOf('LLM_NEGATIVE.get(cropId)');
  assert.ok(tplAt > -1 && llmAt > -1 && tplAt < llmAt, '类别模板应在调用大模型之前生效');
});

/* ============================================================
   V5.4 首屏不空着：常用作物快捷选择 + 一键示例
   ============================================================ */

const weatherVisual = require(path.resolve(__dirname, '..', 'assets', 'js', 'weather-visual.js'));

test('V5.4 首屏空状态：常用作物 chip 与一键示例', () => {
  for (const name of ['水稻', '小麦', '玉米', '大豆', '油菜']) {
    assert.ok(indexHtml.includes('data-crop="' + name + '"'), '缺少常用作物 chip：' + name);
  }
  assert.ok(indexHtml.includes('id="crop-quick"'), '快捷作物应是一组可点的 chip');
  assert.ok(indexHtml.includes('id="demo-btn"'), '应有「试一试示例」按钮');
  assert.ok(indexHtml.includes('试一试示例'), '按钮文案要让用户知道这是示例');
  assert.ok(indexHtml.includes("DEMO_CROP = '水稻'"), '示例作物应是水稻');
  assert.ok(indexHtml.includes("DEMO_LOCATION = '江苏省南京市'"), '示例地点应是江苏南京');
  // 点一次就要看到结果：填完表单还得继续走生成流程
  assert.match(indexHtml, /demo\.addEventListener\('click'[\s\S]{0,1400}?handleSubmit\(\)/,
    '示例应直接触发生成，不能只把字填进输入框');
  assert.ok(indexHtml.includes('crop-chip'), '缺少 chip 样式挂载点');
  assert.ok(commonCss.includes('.crop-chip'), '缺少 chip 样式');
  assert.ok(commonCss.includes('.crop-quick'), 'chips 应放在 .field 外，避免挡住联想下拉');
});

test('V5.4 示例不被"远期计划"提示卡住', () => {
  assert.ok(indexHtml.includes('window.__demoMode'), '示例需要单独的放行标记');
  assert.match(indexHtml, /if \(isDemo\) \{[\s\S]{0,300}?goTimeline/,
    'warn（如「属于远期计划」）时示例应继续生成');
  // block 必须照旧拦住：排一份不成立的计划比不排更糟
  assert.ok(indexHtml.includes("showToast('这个播期安排不合理，已阻止生成'"), 'block 仍应阻止生成');
});

test('V5.4 地点与定位一致：中文地名 + 自动预填', () => {
  assert.ok(weatherVisualJs.includes('/api/geo/search'), '英文城市名应换成中文地名');
  assert.ok(weatherVisualJs.includes('weather-located'), '定位结果应通知首页');
  assert.ok(indexHtml.includes("addEventListener('weather-located'"), '首页应接收定位地名');
  assert.ok(indexHtml.includes('__resolvedLL = { lat: d.lat, lon: d.lon'), '预填地名时要一起给出坐标，省掉一次地名解析');
  assert.ok(!indexHtml.includes('placeholder="如：山东济宁（留空按默认地点）"'),
    '占位不该再写死一个与定位无关的城市');
  // 用户自己写过的地点不能被定位覆盖
  assert.ok(/autofill !== '1'\) return/.test(indexHtml), '用户已填地点时不应被定位覆盖');
});

test('V5.4 天气服务农事：给一句能执行的结论', () => {
  assert.ok(indexHtml.includes('id="weather-farm"'), '天气卡应有农事建议位');
  assert.ok(commonCss.includes('.weather-farm'), '缺少农事建议样式');
  assert.equal(weatherVisual.farmAdvice({ weather: 'rain', temp: 22, humidity: 80, windSpeed: 5 }).level, 'no',
    '有雨不宜喷药');
  assert.equal(weatherVisual.farmAdvice({ weather: 'snow', temp: -2, humidity: 80, windSpeed: 5 }).level, 'no',
    '下雪不宜田间作业');
  assert.equal(weatherVisual.farmAdvice({ weather: 'sunny', temp: 22, humidity: 60, windSpeed: 26 }).level, 'no',
    '大风不宜喷药');
  assert.equal(weatherVisual.farmAdvice({ weather: 'cloudy', temp: 2, humidity: 60, windSpeed: 5 }).level, 'no',
    '低温不宜喷药');
  const good = weatherVisual.farmAdvice({ weather: 'sunny', temp: 22, humidity: 60, windSpeed: 8 });
  assert.equal(good.level, 'ok', '正常天气应判为适宜');
  assert.match(good.text, /喷药/, '结论要落到能不能喷药这种具体动作上');
  assert.equal(weatherVisual.farmAdvice(null), null, '没有天气数据就不该硬给建议');
});

/* ============================================================
   V5.5 可读性与交互：节气尺 / 播期三格 / 大字模式 / 下拉搜索 /
   日期选择器 / 新建地块 / 录音动画 / 加载状态 / 悬浮球
   ============================================================ */

const solarTerms = require(path.resolve(__dirname, '..', 'assets', 'js', 'solar-terms.js'));
const termFarming = JSON.parse(read(path.resolve(__dirname, '..', '..', 'data', 'guidance', 'solar-term-farming.json')));
const floatingBallJs = read(path.resolve(__dirname, '..', 'components', 'floating-ball', 'floating-ball.js'));
const floatingBallCss = read(path.resolve(__dirname, '..', 'components', 'floating-ball', 'floating-ball.css'));

test('V5.5 节气尺：写今天几号，左右夹住前后两个节气的天数', () => {
  assert.ok(indexHtml.includes('id="term-strip"'), '节气尺应有独立容器');
  assert.ok(indexHtml.includes('id="term-now"'), '应写出今天的日期');
  assert.ok(indexHtml.includes('id="term-next"'), '应写出距前后节气几天');
  assert.ok(indexHtml.includes('id="term-tip"'), '应带一句农事提示');
  assert.ok(indexHtml.includes('../assets/js/solar-terms.js'), '应引入节气模块');
  assert.ok(commonCss.includes('.term-ruler i.is-now'), '当前节气那一格要单独点亮');
  assert.ok(indexHtml.includes('tick.title = t.name'), '每格 hover 要能看出是什么节气');

  // 左边是「今天」，不是上一个节气的日期
  assert.ok(indexHtml.includes('class="term-today-tag"') && indexHtml.includes('class="term-today-date"'),
    '左侧应是今天日期，而不是节气日期');
  assert.ok(indexHtml.includes("WEEK[today.getDay()]"), '日期旁要带星期');
  assert.ok(commonCss.includes('.term-today-tag') && commonCss.includes('.term-today-date'), '缺少今日日期样式');

  // 右边两头夹住今天：立冬已过 N 天 · 距小雪 M 天
  assert.ok(indexHtml.includes('term-gap is-prev') && indexHtml.includes('term-gap is-next'),
    '右侧要同时给上一个和下一个节气的天数');
  assert.ok(indexHtml.includes('info.since'), '上一个节气要用「已过几天」表达');
  assert.ok(commonCss.includes('.term-gap em'), '天数要单独加粗');

  const info = solarTerms.current(new Date(2026, 10, 11));
  assert.equal(info.name, '立冬');
  assert.equal(info.md, '11月7日');
  assert.equal(info.since, 4, '11月11日已进入立冬 4 天');
  assert.equal(info.next.name, '小雪');
  assert.equal(info.next.days, 11);
  assert.equal(info.index, 20, '立冬在 24 个节气里是第 21 个');
  assert.equal(info.tip, '冬前管理，浇灌越冬水');

  // 正好是节气当天：since 归零，文案说「今天立冬」
  const onDay = solarTerms.current(new Date(2026, 10, 7));
  assert.equal(onDay.name, '立冬');
  assert.equal(onDay.since, 0);
  assert.equal(onDay.next.days, 15, '立冬到小雪 15 天');
});

test('V5.5 节气：年初几天不能点错格', () => {
  // 1 月 2 日的当前节气还是上一年的冬至，尺子画的是今年的 24 格，所以不点亮
  const jan = solarTerms.current(new Date(2027, 0, 2));
  assert.equal(jan.name, '冬至');
  assert.equal(jan.index, -1);
  assert.equal(jan.next.name, '小寒');
});

test('V5.5 节气数据与后端农事表同源', () => {
  assert.equal(solarTerms.TERMS.length, 24);
  assert.equal(termFarming.length, 24);
  solarTerms.TERMS.forEach((t, i) => {
    assert.equal(t.name, termFarming[i].termName, '第 ' + (i + 1) + ' 个节气名不一致');
    assert.equal(t.month, termFarming[i].dateRule.month, t.name + ' 月份不一致');
    assert.equal(t.c, termFarming[i].dateRule.cConstant, t.name + ' 世纪常数不一致');
    assert.equal(t.tip, termFarming[i].farmingTip, t.name + ' 农事提示不一致');
  });
});

test('V5.5 播期卡片：三个小标签先告诉用户会得到什么', () => {
  ['适宜播期', '最晚播期', '预计收获'].forEach((label) => {
    assert.ok(indexHtml.includes(label), '缺少标签：' + label);
  });
  assert.ok(indexHtml.includes('class="sow-metric is-empty"'), '未推算时应是占位态');
  assert.ok((indexHtml.match(/待推算/g) || []).length >= 3, '三格都要有占位值');
  assert.ok(commonCss.includes('.sow-metrics'), '缺少三格布局');
  assert.ok(commonCss.includes('.sow-metric.is-empty'), '占位态要有独立样式');
  // 说明文字对比度：不再用最浅的 --muted
  assert.ok(commonCss.includes('--muted-strong'), '应有一档更深的说明文字颜色');
  assert.ok(commonCss.includes('.sow-auto-sub { font-size: calc(12.5px * var(--fs, 1)); color: var(--muted-strong)'), '副说明要用更深的灰');
});

test('V5.5 大字模式：一个开关放大全局正文', () => {
  assert.ok(indexHtml.includes('id="font-toggle"'), '页头应有大字开关');
  assert.ok(indexHtml.includes("localStorage.getItem('agritime.fontscale')"), '首帧前应用，避免先小后大');
  assert.match(timelineHtml, /agritime\.fontscale/, '时间线页也要跟着放大');
  assert.ok(commonCss.includes('html[data-font="large"] { --fs: 1.16; }'), '倍率要落在 CSS 变量上');
  assert.ok(commonCss.includes('.font-toggle'), '缺少开关样式');
  assert.ok(commonCss.includes('--fs: 1;'), '默认倍率为 1');

  // 所有字号都必须走倍率，否则会出现"标题变大、说明还是小字"
  const bareSizes = commonCss.match(/font-size:\s*\d+(?:\.\d+)?px/g) || [];
  assert.deepEqual(bareSizes, [], '还有硬编码字号：' + bareSizes.join(', '));
});

test('V5.5 作物品种：下拉 + 搜索，不是纯文本框', () => {
  assert.ok(indexHtml.includes('id="crop-toggle"'), '应有展开全部品种的按钮');
  assert.ok(indexHtml.includes('aria-expanded="false"'), '展开状态要能读出来');
  assert.ok(indexHtml.includes("xhr.open('GET', '/api/crops', true)"), '展开时读后端全部品种');
  assert.ok(indexHtml.includes('function openCropList'));
  assert.ok(indexHtml.includes('localAllCrops'), '接口不可用时要能退回本地品种表');
  assert.ok(indexHtml.includes('稻子'), '要说明别名也能找到标准名');
  assert.ok(commonCss.includes('.crop-toggle'), '缺少下拉箭头样式');
});

test('V5.5 手动指定播期：日期选择器 + 偏离风险', () => {
  assert.match(indexHtml, /<input type="date" id="sow-date-native"/, '应给原生日期控件');
  assert.ok(indexHtml.includes('id="sow-risk"'), '应有风险提示位');
  assert.ok(indexHtml.includes('比适宜播期早 '), '早于窗口要说早多少天');
  assert.ok(indexHtml.includes('比适宜播期晚 '), '晚于窗口要说晚多少天');
  assert.ok(indexHtml.includes("cls: 'is-early'"));
  assert.ok(indexHtml.includes("cls: 'is-late'"));
  assert.ok(indexHtml.includes("cls: 'is-ok'"), '窗口内也要给结论');
  assert.ok(indexHtml.includes('dateNative.min = dateToStr(getMinDate())'), '范围与三联动一致');
  assert.ok(indexHtml.includes('dateNative.addEventListener'), '两个控件要互相同步');
  assert.ok(commonCss.includes('.sow-risk.is-early'));
});

test('V5.5 关联地块：没有地块时不是死胡同', () => {
  assert.ok(indexHtml.includes("add.value = '__new__'"), '下拉里要有新建入口');
  assert.ok(indexHtml.includes('＋ 新建地块'), '文案要说清是新建地块');
  assert.ok(indexHtml.includes('function openNewPlotForm'));
  assert.ok(indexHtml.includes("switchModule('plots')"), '应跳到地块面板');
  assert.ok(indexHtml.includes("getElementById('my-plots-add-btn')"), '直接打开新建表单');
  // "新建地块"不能被当成一个已选中的地块带进生成参数
  assert.ok(indexHtml.includes("sel.value = window.__timelinePlotId || ''"));
});

test('V5.5 语音输入：录音中有动画反馈', () => {
  assert.ok(voiceInputJs.includes('voice-status'), '录音中要有状态提示');
  assert.ok(voiceInputJs.includes('正在聆听'), '要有明确文案');
  assert.ok(voiceInputJs.includes('hideStatus'), '结束录音要收起提示');
  assert.ok(voiceInputCss.includes('.voice-bars'), '缺少音量条');
  assert.ok(voiceInputCss.includes('@keyframes voice-bar'), '缺少跳动动画');
  assert.ok(voiceInputCss.includes('prefers-reduced-motion'), '减少动效时也要能看');
});

test('V5.5 生成时间线：等待时一直有话可说', () => {
  assert.ok(indexHtml.includes('id="gen-loading"'), '表单里应有加载条');
  assert.ok(indexHtml.includes('id="pane-loading"'), '时间线面板应有加载态');
  assert.ok(indexHtml.includes('function showGenLoading'));
  assert.ok(indexHtml.includes("'正在结合' + shortPlace(location) + '的气候推算适宜播期…'"), '推算播期要说清用哪儿的气候');
  assert.ok(indexHtml.includes("'正在核对「' + cropName"), '校验阶段也要有文案');
  assert.ok(indexHtml.includes("iframe.addEventListener('load'"), '算完要收起等待态');
  assert.ok(commonCss.includes('.gen-loading-spinner'));
  assert.ok(indexHtml.includes('function shortPlace'), '地点要写短，别把「江苏省南京市」整串塞进去');
});

test('V5.5 悬浮客服：让开主按钮，且能收起', () => {
  assert.match(floatingBallCss, /\.floating-ball-dock \{[^}]*bottom: 88px/, '悬浮球要抬高，别压住上传按钮');
  assert.ok(floatingBallCss.includes('.floating-ball-dock.is-hidden'), '收起后要能隐藏');
  assert.ok(floatingBallCss.includes('.chat-tab'), '收起后要留一个能叫回来的入口');
  assert.ok(floatingBallJs.includes('agritime.chatcollapsed'), '收起状态要记住');
  assert.ok(floatingBallJs.includes('e.stopPropagation()'), '收起按钮不能冒泡成打开对话');
  assert.ok(floatingBallJs.includes('setCollapsed(readCollapsed())'));
});

test('V5.5 病害提示：加图标，别让它灰到看不见', () => {
  assert.match(indexHtml, /class="photo-entry">\s*<svg/, '提示前应有图标');
  assert.ok(indexHtml.includes('识别只给候选，需你点一下确认'), '这句是产品诚实的证据，必须保留');
  assert.ok(commonCss.includes('.photo-entry svg'));
});

test('V5.6 表单控件：字不能被框裁成半截', () => {
  // 教训：.field input/select 写死 height: 40px，模块再把纵向内边距补成 11~12px，
  // 内容区就被压到 16px 左右，15px 的中文上下各裁掉一截，界面上看着只露一半字。
  // 现在统一用 min-height：内边距变大时框能跟着长高，大字模式放大字号也不会裁字。
  assert.match(commonCss, /\.field input \{[^}]*min-height: 40px/, '输入框要用 min-height 撑高');
  assert.match(commonCss, /\.field select \{[^}]*min-height: 40px/, '下拉框要用 min-height 撑高');
  assert.doesNotMatch(commonCss, /\.field (input|select) \{[^}]*\n\s*height: 40px/, '不能写死 height');
  assert.match(recommendCss, /\.recommend-form select, \.recommend-form input \{[^}]*padding: 0 12px/,
    '种植推荐控件不能靠纵向内边距撑高');
  assert.match(recommendCss, /min-height: calc\(44px \* var\(--fs, 1\)\)/, '框高要随字号一起变');
  assert.match(diseaseCss, /\.disease-form \.field select \{[^}]*padding: 0 36px 0 12px/,
    '病害下拉框不能靠纵向内边距撑高，右侧要留出箭头位置');
  assert.match(diseaseCss, /\.disease-form \.field textarea \{[^}]*padding: 11px 12px/,
    '文本域是多行控件，内边距照旧');
});

test('V5.7 外观主题：九块地，各长各的庄稼', () => {
  const themeJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'theme.js'));
  const userChipJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'user-chip.js'));
  const decorJs = read(path.resolve(__dirname, '..', 'assets', 'js', 'theme-decor.js'));
  const common = read(path.join(CSS, 'common.css'));

  // 五个农业主题：theme.js 认得，common.css 有对应配色
  ['paddy', 'wheat', 'rapeseed', 'terra', 'barn'].forEach((id) => {
    assert.ok(themeJs.includes("'" + id + "'"), '主题清单应含 ' + id);
    assert.ok(common.includes(':root[data-theme="' + id + '"]'), '缺少 ' + id + ' 的配色变量块');
  });
  // 老主题的偏好不能丢：ocean/forest→paddy、sunset→wheat、sepia→barn
  assert.match(themeJs, /ocean: 'paddy'/, '海洋主题应并入稻田晨雾');
  assert.match(themeJs, /forest: 'paddy'/, '森林主题应并入稻田晨雾');
  assert.match(themeJs, /sunset: 'wheat'/, '秋收主题应并入麦浪金黄');
  assert.match(themeJs, /sepia: 'barn'/, '暖纸主题应并入谷仓木纸');
  assert.ok(themeJs.includes('LEGACY[pref]') && themeJs.includes('LEGACY[v]'),
    '读取与解析都要走迁移表，否则老用户会被打回浅色');

  // 面板上九个名字都是农事意象，不再是 浅色/深色/海洋 这种通用色名
  ['稻米白', '稻田晨雾', '麦浪金黄', '油菜花田', '红壤梯田', '谷仓木纸', '夜田墨绿', '黑土深夜', '跟随系统']
    .forEach((label) => assert.ok(userChipJs.includes(label), '缺少主题「' + label + '」'));
  assert.ok(!userChipJs.includes("label: '海洋'"), '不该再出现「海洋」这种通用色名');
  // 色卡换成场景缩略图，配色取自该主题自己的变量
  assert.ok(userChipJs.includes("'theme-thumb'"), '色卡应该是场景缩略图');
  assert.ok(userChipJs.includes('function art('), '缩略图按场景生成');
  assert.ok(userChipJs.includes("colors[3]"), '穗粒/花瓣要用比主色更亮的作物色');
  assert.ok(common.includes('.theme-thumb svg'), '缩略图要撑满卡片');
  assert.ok(common.includes('.theme-card.is-active::after'), '选中要有标记');

  // 装饰层：每个主题都映射到场景，不能全落到稻花上
  ['light', 'paddy', 'wheat', 'rapeseed', 'terra', 'barn', 'dark', 'amoled'].forEach((id) => {
    assert.match(decorJs, new RegExp(id + ": '[a-z]+'"), '装饰层缺少 ' + id + ' 的场景映射');
  });
  assert.ok(decorJs.includes("bloom: BLOOM") && decorJs.includes("terrace: TERRACE"),
    '油菜花与梯田要有各自的场景');
});
