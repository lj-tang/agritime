/**
 * 渔业板块（T09）：品类 → 放养日 → 生产日历。
 *
 * 数据全部来自 /api/industry/*；水温适宜度是后端用气温近似的，
 * 页面上必须原样保留那句说明，不能把近似值讲成实测。
 */
(function () {
  'use strict';

  var API = '/api/industry';
  var form = document.getElementById('industry-form');
  if (!form) return;

  var speciesSel = document.getElementById('industry-species');
  var dateInput = document.getElementById('industry-date');
  var locInput = document.getElementById('industry-location');
  var result = document.getElementById('industry-result');

  var CITY_COORDS = {
    '湖北省荆州市': [30.33, 112.24],
    '江苏省苏州市': [31.30, 120.60],
    '广东省广州市': [23.13, 113.26],
    '山东省济宁市': [35.07, 116.31]
  };

  function todayISO() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function coordsOf(location) {
    if (window.__markerLatLon) return [window.__markerLatLon.lat, window.__markerLatLon.lon];
    return CITY_COORDS[location] || [30.33, 112.24];
  }

  function loadSpecies() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', API + '/species', true);
    xhr.onload = function () {
      try {
        var body = JSON.parse(xhr.responseText);
        var list = (body.data && body.data.species) || [];
        speciesSel.innerHTML = '';
        list.forEach(function (s) {
          var opt = document.createElement('option');
          opt.value = s.id;
          opt.textContent = s.name + '（' + s.category + '）';
          speciesSel.appendChild(opt);
        });
        if (list.length) showIntro(list[0]);
      } catch (e) {
        speciesSel.innerHTML = '<option>加载失败</option>';
      }
    };
    xhr.send();
  }

  function showIntro(s) {
    result.innerHTML = '';
    var box = document.createElement('div');
    box.className = 'industry-empty';
    box.textContent = '选好品类和放养日期后，会按阶段生成生产日历。当前品类：'
      + s.name + '，适宜水温 ' + s.waterTempMin + '~' + s.waterTempMax + '℃，'
      + (s.stockSeason || '') + '。';
    result.appendChild(box);
  }

  function statusLabel(status) {
    if (status === 'ok') return '适宜';
    if (status === 'caution') return '需注意';
    return '无数据';
  }

  function render(data) {
    result.innerHTML = '';

    var head = document.createElement('div');
    head.className = 'industry-head';
    var title = document.createElement('div');
    title.className = 'industry-head-title';
    title.textContent = data.species + ' · 生产日历';
    var meta = document.createElement('div');
    meta.className = 'industry-head-meta';
    meta.textContent = '放养 ' + data.stockDate + ' · ' + (data.stockUnit || '')
      + ' · 适宜水温 ' + data.waterTempMin + '~' + data.waterTempMax + '℃';
    head.appendChild(title);
    head.appendChild(meta);
    result.appendChild(head);

    if (data.tips) {
      var tip = document.createElement('p');
      tip.className = 'industry-tip';
      tip.textContent = '养殖要点：' + data.tips;
      result.appendChild(tip);
    }

    var list = document.createElement('div');
    list.className = 'industry-nodes';
    (data.nodes || []).forEach(function (n) {
      var card = document.createElement('div');
      card.className = 'industry-node status-' + (n.status || 'unknown');

      var dot = document.createElement('span');
      dot.className = 'industry-dot';
      card.appendChild(dot);

      var body = document.createElement('div');
      body.className = 'industry-node-body';

      var top = document.createElement('div');
      top.className = 'industry-node-top';
      var name = document.createElement('span');
      name.className = 'name';
      name.textContent = n.title || '';
      var date = document.createElement('span');
      date.className = 'date';
      date.textContent = n.date + '（第 ' + n.dayOffset + ' 天）';
      top.appendChild(name);
      top.appendChild(date);
      body.appendChild(top);

      var detail = document.createElement('p');
      detail.className = 'industry-detail';
      detail.textContent = n.detail || '';
      body.appendChild(detail);

      var badge = document.createElement('span');
      badge.className = 'industry-badge';
      badge.textContent = statusLabel(n.status);
      var advice = document.createElement('p');
      advice.className = 'industry-advice';
      advice.textContent = n.advice || '';
      body.appendChild(badge);
      body.appendChild(advice);

      card.appendChild(body);
      list.appendChild(card);
    });
    result.appendChild(list);

    var note = document.createElement('p');
    note.className = 'industry-note';
    note.textContent = data.tempNote || '';
    result.appendChild(note);
  }

  function load() {
    var id = speciesSel.value;
    if (!id) return;
    var stock = dateInput.value || todayISO();
    var loc = locInput.value.trim();
    var c = coordsOf(loc);
    result.innerHTML = '<div class="industry-empty">正在生成生产日历…</div>';

    var xhr = new XMLHttpRequest();
    xhr.open('GET', API + '/timeline?species=' + encodeURIComponent(id)
      + '&stockDate=' + encodeURIComponent(stock)
      + '&lat=' + c[0] + '&lon=' + c[1], true);
    xhr.timeout = 15000;
    xhr.onload = function () {
      try {
        var body = JSON.parse(xhr.responseText);
        if (body.code === '0' && body.data) { render(body.data); return; }
        result.innerHTML = '<div class="industry-empty">' + (body.message || '生成失败') + '</div>';
      } catch (e) {
        result.innerHTML = '<div class="industry-empty">返回格式异常</div>';
      }
    };
    xhr.onerror = xhr.ontimeout = function () {
      result.innerHTML = '<div class="industry-empty">网络异常，请稍后重试</div>';
    };
    xhr.send();
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    load();
  });

  dateInput.value = todayISO();
  loadSpecies();
})();
