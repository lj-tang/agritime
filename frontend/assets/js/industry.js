/**
 * 渔业产业扩展（T09）
 * 选择渔业品类和开始日期，生成生产日历，叠加天气适宜度与投喂指导。
 * 内置离线降级：后端不可用时前端用 FALLBACK_DATA 自行生成日历。
 */
(function () {
  var API = '/api/industry/aquaculture';
  var formEl, resultEl, categorySel;

  var FALLBACK_CATEGORIES = [
    { category: 'grass_carp', name: '草鱼', alias: '鲩鱼', type: '淡水鱼类', growthCycleDays: 240, optimalTemp: '22~28°C', description: '草鱼是中国四大家鱼之一，生长快、适应性强，以水草和配合饲料为主食' },
    { category: 'common_carp', name: '鲤鱼', alias: '红鲤', type: '淡水鱼类', growthCycleDays: 200, optimalTemp: '20~26°C', description: '鲤鱼适应性强、耐低氧，杂食性，是北方主要养殖品种' },
    { category: 'chinese_mitten_crab', name: '大闸蟹', alias: '中华绒螯蟹', type: '甲壳类', growthCycleDays: 180, optimalTemp: '20~25°C', description: '大闸蟹经济价值高，以长江水系品质最佳，需精细化管理水质与投喂' }
  ];

  var FALLBACK_STAGES = {
    grass_carp: [
      { name: '池塘准备', dayOffset: 0, durationDays: 7, action: '清塘消毒、注水培肥', detail: '生石灰清塘（75kg/亩），注水1.5~2米，施有机肥培肥水质', weatherTip: '晴天操作效果好，雨天不宜清塘', tempMin: 10, tempMax: 35 },
      { name: '放苗', dayOffset: 7, durationDays: 1, action: '投放草鱼鱼苗', detail: '选择体长8~12cm的健康鱼苗，每亩投放800~1200尾，温差不超过2°C', weatherTip: '选择晴天上午放苗，避免阴雨天操作，水温15°C以上为宜', tempMin: 15, tempMax: 30 },
      { name: '驯食投喂期', dayOffset: 8, durationDays: 15, action: '驯化摄食、少量多次投喂', detail: '每日投喂2~3次，日投喂量为鱼体重的2~3%，以配合饲料为主', weatherTip: '水温20°C以上摄食旺盛，低于15°C减少投喂', tempMin: 15, tempMax: 32, feedingGuide: '日投喂量 = 鱼体重 × 2.5%，分3次投喂' },
      { name: '快速生长期', dayOffset: 23, durationDays: 90, action: '加大投喂、定期换水', detail: '每日投喂3~4次，日投喂量3~5%，每10~15天换水1/3，保持溶氧>4mg/L', weatherTip: '高温季节注意增氧防浮头，暴雨前减少投喂', tempMin: 22, tempMax: 32, feedingGuide: '日投喂量 = 鱼体重 × 4%，分4次投喂，上午8点开始' },
      { name: '中期管理', dayOffset: 113, durationDays: 60, action: '水质调控、病害防治', detail: '每15天换水1/4，定期泼洒微生物制剂，注意防治烂鳃病和肠炎', weatherTip: '连续阴雨天注意增氧，高温期注意防泛塘', tempMin: 18, tempMax: 32, feedingGuide: '日投喂量 = 鱼体重 × 3%，根据水温调整' },
      { name: '育肥期', dayOffset: 173, durationDays: 50, action: '加强投喂、蓄肥', detail: '增加饲料蛋白含量，日投喂量3~4%，保持水质清新', weatherTip: '秋季水温下降，白天投喂为主', tempMin: 15, tempMax: 28, feedingGuide: '日投喂量 = 鱼体重 × 3.5%，高蛋白饲料' },
      { name: '停食越冬', dayOffset: 223, durationDays: 10, action: '逐步减少投喂、准备越冬', detail: '水温低于10°C停止投喂，加深水位至2米以上', weatherTip: '寒潮来临前加深水位，防止冻伤', tempMin: 0, tempMax: 15 },
      { name: '捕捞上市', dayOffset: 233, durationDays: 7, action: '排水捕捞、分规格上市', detail: '规格达1.5~2.5kg可上市，拉网捕捞，注意操作轻柔避免受伤', weatherTip: '选择晴天捕捞，避免高温中午操作', tempMin: 5, tempMax: 25 }
    ],
    common_carp: [
      { name: '池塘准备', dayOffset: 0, durationDays: 7, action: '清塘消毒、注水', detail: '干法清塘生石灰100kg/亩，注水1.2~1.8米，施基肥培水', weatherTip: '晴好天气清塘，雨天延长消毒时间', tempMin: 8, tempMax: 30 },
      { name: '放苗', dayOffset: 7, durationDays: 1, action: '投放鲤鱼鱼苗', detail: '选择体长10cm左右健壮鱼苗，每亩投放1000~1500尾，注意温差适应', weatherTip: '水温14°C以上放苗，避开阴雨大风天', tempMin: 14, tempMax: 28 },
      { name: '适应投喂期', dayOffset: 8, durationDays: 12, action: '驯食定点投喂', detail: '每日2次，日投喂量1~2%，以颗粒饲料为主，定点定时', weatherTip: '水温低时午后投喂，水温高时早晚投喂', tempMin: 14, tempMax: 30, feedingGuide: '日投喂量 = 鱼体重 × 1.5%，分2次' },
      { name: '旺盛生长期', dayOffset: 20, durationDays: 100, action: '足量投喂、水质管理', detail: '每日3次，日投喂量3~4%，每10天换水1/3，保持透明度25~30cm', weatherTip: '暴雨前停喂，高温注意增氧，闷热天防浮头', tempMin: 20, tempMax: 30, feedingGuide: '日投喂量 = 鱼体重 × 3.5%，分3次，早8点、午1点、晚5点' },
      { name: '中期调水', dayOffset: 120, durationDays: 50, action: '水质调控、适量投喂', detail: '每15天换水1/4，定期用生石灰调水（10kg/亩），注意防治竖鳞病', weatherTip: '温差大时注意防应激，连阴天减少投喂', tempMin: 16, tempMax: 28, feedingGuide: '日投喂量 = 鱼体重 × 2.5%，根据摄食情况调整' },
      { name: '育肥越冬准备', dayOffset: 170, durationDays: 20, action: '增加营养、加深水位', detail: '提高饲料能量，日投喂量2~3，逐渐加深水位至2米', weatherTip: '寒潮前加深水位，水温低于8°C减少投喂', tempMin: 5, tempMax: 20, feedingGuide: '日投喂量 = 鱼体重 × 2%，高能量饲料' },
      { name: '捕捞上市', dayOffset: 190, durationDays: 10, action: '网捕或干塘捕捞', detail: '规格达1~1.5kg可上市，分批捕捞，大规格先出', weatherTip: '避开冰冻天捕捞，操作防机械损伤', tempMin: 0, tempMax: 20 }
    ],
    chinese_mitten_crab: [
      { name: '蟹塘准备', dayOffset: 0, durationDays: 10, action: '清塘、种草、防逃设施', detail: '生石灰清塘120kg/亩，种植伊乐藻/轮叶黑藻，安装防逃膜，注水0.8~1.2米', weatherTip: '晴天种草成活率高，大风天不宜安装防逃设施', tempMin: 8, tempMax: 30 },
      { name: '放苗（扣蟹）', dayOffset: 10, durationDays: 1, action: '投放扣蟹', detail: '选择规格120~200只/kg的优质扣蟹，每亩投放800~1200只，缓温适应后下塘', weatherTip: '水温12°C以上放苗，避开高温中午，阴天早晨最佳', tempMin: 10, tempMax: 25 },
      { name: '适应恢复期', dayOffset: 11, durationDays: 14, action: '少量投喂、观察摄食', detail: '投喂小杂鱼+配合饲料，日投喂量1~2%，傍晚投喂为主', weatherTip: '水温低时摄食少，天气回暖逐步加量', tempMin: 10, tempMax: 28, feedingGuide: '日投喂量 = 蟹体重 × 1.5%，傍晚5~6点投喂' },
      { name: '蜕壳生长期', dayOffset: 25, durationDays: 75, action: '足量投喂、水质管理、防病害', detail: '日投喂量3~5%，以冰鲜鱼+配合饲料交替，每7~10天换水1/5，保持溶氧>5mg/L', weatherTip: '蜕壳期忌用药，暴雨前增氧，闷热天开增氧机', tempMin: 18, tempMax: 30, feedingGuide: '日投喂量 = 蟹体重 × 4%，傍晚为主，动物性饲料占60%' },
      { name: '育肥增重期', dayOffset: 100, durationDays: 50, action: '高营养投喂、强化育肥', detail: '增加动物性饲料比例（鲜鱼、螺蚌），日投喂量3~4%，注意水草覆盖度保持40~60%', weatherTip: '秋季温差大，注意水质稳定，连阴天减少投喂', tempMin: 15, tempMax: 28, feedingGuide: '日投喂量 = 蟹体重 × 3.5%，动物性饲料占70%' },
      { name: '生殖洄游监控', dayOffset: 150, durationDays: 20, action: '防逃巡查、减少投喂', detail: '气温下降蟹开始爬动，加强防逃设施巡查，日投喂量降至1~2%', weatherTip: '北风降温时蟹活跃，加强夜间巡查防逃', tempMin: 8, tempMax: 20, feedingGuide: '日投喂量 = 蟹体重 × 1.5%' },
      { name: '捕捞上市', dayOffset: 170, durationDays: 10, action: '地笼捕捞、分规格上市', detail: '规格达150~200g（2.5~3两/只）可上市，地笼诱捕，活水暂养提品质', weatherTip: '寒露后品质最佳，避开冻雨天捕捞', tempMin: 5, tempMax: 18 }
    ]
  };

  function init() {
    formEl = document.getElementById('industry-form');
    resultEl = document.getElementById('industry-result');
    categorySel = document.getElementById('industry-category');
    if (!formEl || !resultEl) return;

    loadCategories();
    formEl.addEventListener('submit', onSubmit);

    var today = new Date();
    var dateInput = document.getElementById('industry-start-date');
    if (dateInput && !dateInput.value) {
      dateInput.value = today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-15';
    }

    var locBtn = document.getElementById('industry-loc-btn');
    if (locBtn) locBtn.addEventListener('click', function () {
      if (!navigator.geolocation) return;
      locBtn.textContent = '定位中…';
      navigator.geolocation.getCurrentPosition(function (pos) {
        var latInput = document.getElementById('industry-lat');
        var lonInput = document.getElementById('industry-lon');
        if (latInput) latInput.value = pos.coords.latitude.toFixed(2);
        if (lonInput) lonInput.value = pos.coords.longitude.toFixed(2);
        locBtn.textContent = '📍 自动定位';
      }, function () { locBtn.textContent = '📍 自动定位'; }, { timeout: 8000 });
    });
  }

  function loadCategories() {
    if (!categorySel) return;
    fetch(API + '/list').then(function (r) { return r.json(); }).then(function (data) {
      if (data.code && data.code !== '0') { useFallbackCategories(); return; }
      var cats = data.categories || [];
      if (!cats.length) { useFallbackCategories(); return; }
      renderCategoryOptions(cats);
    }).catch(function () { useFallbackCategories(); });
  }

  function useFallbackCategories() {
    renderCategoryOptions(FALLBACK_CATEGORIES);
  }


  function renderCategoryOptions(cats) {
    categorySel.innerHTML = '';
    cats.forEach(function (c) {
      var opt = document.createElement('option');
      opt.value = c.category;
      opt.textContent = c.name + '（' + c.type + '）· 周期' + c.growthCycleDays + '天';
      categorySel.appendChild(opt);
    });
    if (cats.length) categorySel.selectedIndex = 0;
  }

  function onSubmit(e) {
    e.preventDefault();
    var category = categorySel.value;
    var startDate = document.getElementById('industry-start-date').value;
    var lat = parseFloat((document.getElementById('industry-lat').value || '35.07').trim());
    var lon = parseFloat((document.getElementById('industry-lon').value || '116.31').trim());
    if (!category) return;
    if (!isFinite(lat)) lat = 35.07;
    if (!isFinite(lon)) lon = 116.31;

    var btn = formEl.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = '生成中…';

    var url = API + '/calendar?category=' + encodeURIComponent(category) +
      '&startDate=' + encodeURIComponent(startDate) + '&lat=' + lat + '&lon=' + lon;

    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      btn.disabled = false; btn.textContent = '生成生产日历';
      if (data.code && data.code !== '0') { generateOffline(category, startDate, lat, lon); return; }
      renderCalendar(data);
    }).catch(function () {
      btn.disabled = false; btn.textContent = '生成生产日历';
      generateOffline(category, startDate, lat, lon);
    });
  }

  function generateOffline(category, startDateStr, lat, lon) {
    var cat = FALLBACK_CATEGORIES.filter(function (c) { return c.category === category; })[0];
    if (!cat) { resultEl.innerHTML = '<div class="industry-error">未知品类</div>'; return; }
    var stages = FALLBACK_STAGES[category] || [];
    var startDate;
    try { startDate = new Date(startDateStr); if (isNaN(startDate.getTime())) startDate = new Date(); }
    catch (e) { startDate = new Date(); }

    var nodes = stages.map(function (s) {
      var nodeStart = new Date(startDate.getTime() + s.dayOffset * 86400000);
      var nodeEnd = new Date(nodeStart.getTime() + s.durationDays * 86400000);
      var month = nodeStart.getMonth() + 1;
      var estTemp = estimateTemp(lat, month);
      var suitability = assessSuitability(estTemp, s.tempMin, s.tempMax);
      var weatherAdvice = buildWeatherAdvice(estTemp, s.tempMin, s.tempMax, s.weatherTip || '', month);
      return {
        name: s.name, action: s.action, detail: s.detail,
        startDate: fmtDate(nodeStart), endDate: fmtDate(nodeEnd),
        dayOffset: s.dayOffset, durationDays: s.durationDays,
        suitability: suitability, estTemp: estTemp.toFixed(1),
        tempRange: s.tempMin + '~' + s.tempMax + '°C',
        weatherAdvice: weatherAdvice, weatherTip: s.weatherTip || '',
        feedingGuide: s.feedingGuide || null
      };
    });

    renderCalendar({
      category: cat.category, name: cat.name, alias: cat.alias, type: cat.type,
      growthCycleDays: cat.growthCycleDays, optimalTemp: cat.optimalTemp, description: cat.description,
      startDate: fmtDate(startDate),
      endDate: fmtDate(new Date(startDate.getTime() + cat.growthCycleDays * 86400000)),
      lat: lat, lon: lon, nodes: nodes, offline: true
    });
  }

  function estimateTemp(lat, month) {
    var absLat = Math.abs(lat);
    var base = 28 - absLat * 0.6;
    var seasonal = 12 * Math.cos((month - 7) * Math.PI / 6);
    return base + seasonal;
  }

  function assessSuitability(temp, min, max) {
    if (temp >= min && temp <= max) return '适宜';
    if (temp < min - 5 || temp > max + 5) return '不适宜';
    return '勉强';
  }

  function buildWeatherAdvice(temp, min, max, tip, month) {
    var sb = '';
    if (temp < min) sb = '当前估计水温' + Math.round(temp) + '°C偏低（需≥' + min + '°C）';
    else if (temp > max) sb = '当前估计水温' + Math.round(temp) + '°C偏高（需≤' + max + '°C）';
    else sb = '水温适宜（' + Math.round(temp) + '°C）';
    if (month >= 6 && month <= 8) sb += '；夏季注意增氧防泛塘';
    if (month === 12 || month <= 2) sb += '；冬季注意防冻加深水位';
    if (tip) sb += '；' + tip;
    return sb;
  }

  function fmtDate(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function renderCalendar(data) {
    var nodes = data.nodes || [];
    var offlineBadge = '';
    var html =
      '<div class="industry-cal-header">' +
        '<div class="industry-cal-title">' +
          '<span class="industry-cal-name"></span>' +
          '<span class="industry-cal-type">' + (data.type || '') + '</span>' +
          offlineBadge +
        '</div>' +
        '<div class="industry-cal-meta">' +
          '<span>开始：' + (data.startDate || '') + '</span>' +
          '<span>结束：' + (data.endDate || '') + '</span>' +
          '<span>周期：' + (data.growthCycleDays || '?') + ' 天</span>' +
          '<span>适宜水温：' + (data.optimalTemp || '') + '</span>' +
        '</div>' +
        '<div class="industry-cal-desc"></div>' +
      '</div>';

    html += '<div class="industry-timeline">';
    nodes.forEach(function (n, i) {
      var suitClass = 'industry-suit--' + ({ '适宜': 'good', '勉强': 'ok', '不适宜': 'bad' }[n.suitability] || 'ok');
      html +=
        '<div class="industry-node">' +
          '<div class="industry-node-marker ' + suitClass + '">' + (i + 1) + '</div>' +
          '<div class="industry-node-body">' +
            '<div class="industry-node-head">' +
              '<span class="industry-node-name"></span>' +
              '<span class="industry-node-date">' + (n.startDate || '') + ' ~ ' + (n.endDate || '') + '</span>' +
              '<span class="industry-suit ' + suitClass + '">' + (n.suitability || '') + '</span>' +
            '</div>' +
            '<div class="industry-node-action"></div>' +
            '<div class="industry-node-detail"></div>' +
            '<div class="industry-node-weather">' +
              '<span class="industry-node-temp">估计水温 ' + (n.estTemp || '?') + '°C（需' + (n.tempRange || '') + '）</span>' +
              '<span class="industry-node-advice"></span>' +
            '</div>';
      if (n.feedingGuide) {
        html += '<div class="industry-node-feeding">🍽️ 投喂指导：' + n.feedingGuide + '</div>';
      }
      html += '</div></div>';
    });
    html += '</div>';

    resultEl.innerHTML = html;
    resultEl.querySelector('.industry-cal-name').textContent = data.name + (data.alias ? '（' + data.alias + '）' : '');
    resultEl.querySelector('.industry-cal-desc').textContent = data.description || '';
    nodes.forEach(function (n, i) {
      var nodeEls = resultEl.querySelectorAll('.industry-node');
      var nodeEl = nodeEls[i];
      nodeEl.querySelector('.industry-node-name').textContent = n.name;
      nodeEl.querySelector('.industry-node-action').textContent = '📋 ' + n.action;
      nodeEl.querySelector('.industry-node-detail').textContent = n.detail;
      nodeEl.querySelector('.industry-node-advice').textContent = n.weatherAdvice;
    });
  }

  if (typeof window !== 'undefined') {
    window.Industry = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();