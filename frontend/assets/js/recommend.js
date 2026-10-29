/**
 * 种植推荐（V4 T15）
 * 选月份 + 地点（可自动定位）→ /api/recommend → 展示 Top 推荐与理由。
 * 推荐结果里命中农事日历的作物（cropId 非空）可直接一键进入农事日历。
 */
(function () {
  var API = '/api/recommend';
  var MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

  var form, monthSel, locInput, latInput, lonInput, locBtn, locStatus, resultBox;

  function init() {
    form = document.getElementById('recommend-form');
    monthSel = document.getElementById('recommend-month');
    locInput = document.getElementById('recommend-location');
    latInput = document.getElementById('recommend-lat');
    lonInput = document.getElementById('recommend-lon');
    locBtn = document.getElementById('recommend-loc-btn');
    locStatus = document.getElementById('recommend-loc-status');
    resultBox = document.getElementById('recommend-result');
    if (!form || !monthSel || !resultBox) return;

    fillMonths();
    form.addEventListener('submit', onSubmit);
    locBtn.addEventListener('click', autoLocate);
    showHint();
  }

  function fillMonths() {
    monthSel.innerHTML = '';
    var now = new Date().getMonth() + 1; // 1~12
    MONTHS.forEach(function (label, i) {
      var opt = document.createElement('option');
      opt.value = String(i + 1);
      opt.textContent = label;
      if (i + 1 === now) opt.selected = true;
      monthSel.appendChild(opt);
    });
  }

  function autoLocate() {
    if (!window.LocationPicker || !window.LocationPicker.getCurrentLocation) {
      locStatus.textContent = '定位组件不可用';
      return;
    }
    locStatus.textContent = '定位中…';
    window.LocationPicker.getCurrentLocation(function (loc) {
      if (loc && window.LocationPicker.validateLatLon && window.LocationPicker.validateLatLon(loc.lat, loc.lon)) {
        latInput.value = loc.lat.toFixed(2);
        lonInput.value = loc.lon.toFixed(2);
        locInput.value = loc.name || (loc.lat.toFixed(2) + ', ' + loc.lon.toFixed(2));
        locStatus.textContent = '已定位到当前坐标';
      } else {
        locStatus.textContent = '定位失败，请手动填经纬度';
      }
    });
  }

  function onSubmit(e) {
    e.preventDefault();
    var month = monthSel.value;
    var lat = parseFloat(latInput.value);
    var lon = parseFloat(lonInput.value);
    if (!(lat >= -90 && lat <= 90)) lat = 35.07;
    if (!(lon >= -180 && lon <= 180)) lon = 116.31;

    var url = API + '?month=' + encodeURIComponent(month) +
      '&lat=' + encodeURIComponent(lat) + '&lon=' + encodeURIComponent(lon);

    resultBox.innerHTML = '<div class="recommend-loading">正在综合节气、气候与市场数据…</div>';
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 20000);

    fetch(url, { method: 'GET', signal: controller ? controller.signal : undefined })
      .then(function (res) {
        clearTimeout(timer);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) { render(data); })
      .catch(function (err) {
        clearTimeout(timer);
        var msg = err && err.name === 'AbortError' ? '请求超时，请稍后重试' : '获取推荐失败：' + (err.message || '网络异常');
        showMessage(msg);
      });
  }

  function showHint() {
    showMessage('选择种植月份和地点，点「获取推荐」，这里会给出最适宜种植的作物');
  }

  function showMessage(text) {
    resultBox.innerHTML = '';
    var d = document.createElement('div');
    d.className = 'recommend-empty';
    d.textContent = text;
    resultBox.appendChild(d);
  }

  function render(data) {
    resultBox.innerHTML = '';
    var list = data.recommendations || [];
    if (!list.length) {
      showMessage('没有匹配到推荐作物，试试换个月份或地点');
      return;
    }
    list.forEach(function (r) { resultBox.appendChild(renderCard(r)); });
  }

  function renderCard(r) {
    var card = document.createElement('div');
    card.className = 'recommend-card';

    var head = document.createElement('div');
    head.className = 'recommend-card-head';
    head.innerHTML = '<span class="recommend-rank">' + r.rank + '</span>' +
      '<span class="recommend-name"></span>' +
      '<span class="recommend-score">' + Math.round((r.score || 0)) + '</span>';
    head.querySelector('.recommend-name').textContent = r.name;
    card.appendChild(head);

    // 分数条
    var bar = document.createElement('div');
    bar.className = 'recommend-bar';
    var fill = document.createElement('div');
    fill.className = 'recommend-bar-fill';
    fill.style.width = Math.min(100, Math.round((r.score || 0) / 1.0)) + '%';
    bar.appendChild(fill);
    card.appendChild(bar);

    // 标签：月份/气候/市场
    var tags = document.createElement('div');
    tags.className = 'recommend-tags';
    tags.appendChild(tag(r.monthMatch === 'true' ? '本月适播' : '临近月份', true));
    tags.appendChild(tag(r.climateMatch === 'true' ? '气候匹配' : '气候一般', r.climateMatch === 'true'));
    if (r.market && r.market !== '') tags.appendChild(tag('市场需求 ' + r.market, false));
    card.appendChild(tags);

    // 推荐理由
    if (r.reason) {
      var reason = document.createElement('p');
      reason.className = 'recommend-reason';
      reason.textContent = r.reason;
      card.appendChild(reason);
    }

    // 播种月份提示
    if (r.sowMonths && r.sowMonths.length) {
      var sm = document.createElement('div');
      sm.className = 'recommend-sow-months';
      sm.textContent = '适宜播种：' + r.sowMonths.map(function (m) { return m + '月'; }).join('、');
      card.appendChild(sm);
    }

    // 一键进农事日历（仅命中日历作物的才给入口）
    if (r.cropId && r.cropId !== '') {
      var go = document.createElement('button');
      go.type = 'button';
      go.className = 'btn btn-primary recommend-go';
      go.textContent = '进入农事日历 →';
      go.addEventListener('click', function () { goCalendar(r); });
      card.appendChild(go);
    }

    return card;
  }

  function tag(text, active) {
    var s = document.createElement('span');
    s.className = 'recommend-tag' + (active ? ' recommend-tag--on' : '');
    s.textContent = text;
    return s;
  }

  function goCalendar(r) {
    if (!window.Sidebar || !window.Sidebar.switchModule) return;
    window.Sidebar.switchModule('calendar');
    // 预填作物名，其余交给用户补时间地点
    var cropInput = document.getElementById('crop-input');
    if (cropInput && window.CropMap) {
      cropInput.value = r.name;
      cropInput.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  if (typeof window !== 'undefined') {
    window.Recommend = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
