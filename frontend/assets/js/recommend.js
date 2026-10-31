/**
 * 种植推荐（V5 T16）
 *
 * V4 的问题：地点输入框只是摆设 —— 请求里塞的是写死的 35.07/116.31，
 * 输入"山东济宁"和"广州"拿到的推荐一模一样，而界面上看不出这点。
 *
 * 现在的口径：
 *   1. 地点文字必须解析成坐标才参与计算；解析不出来就明说"按默认地点计算"
 *   2. 可关联地块，命中该地块适宜作物 Top5 的会加分，并给出匹配依据
 *   3. 每条推荐可展开看依据（节气/气候/市场/地块各自贡献多少分）
 *   4. 一键进日历时带上地点与地块，不用再填一遍
 */
(function () {
  var API = '/api/recommend';
  var GEO = '/api/geo/search';
  var MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

  var DEFAULT_LAT = 35.07;
  var DEFAULT_LON = 116.31;
  var DEFAULT_NAME = '北京（默认）';

  var form, monthSel, locInput, latInput, lonInput, locBtn, resolveBtn;
  var locStatus, resultBox, plotSel;

  /** 当前地点是否已解析成坐标；source=default 表示会用默认地点。 */
  var locState = { lat: null, lon: null, name: '', source: 'default' };

  function init() {
    form = document.getElementById('recommend-form');
    monthSel = document.getElementById('recommend-month');
    locInput = document.getElementById('recommend-location');
    latInput = document.getElementById('recommend-lat');
    lonInput = document.getElementById('recommend-lon');
    locBtn = document.getElementById('recommend-loc-btn');
    resolveBtn = document.getElementById('recommend-resolve-btn');
    locStatus = document.getElementById('recommend-loc-status');
    resultBox = document.getElementById('recommend-result');
    plotSel = document.getElementById('recommend-plot');
    if (!form || !monthSel || !resultBox) return;

    fillMonths();
    fillPlots();
    form.addEventListener('submit', onSubmit);
    if (locBtn) locBtn.addEventListener('click', autoLocate);
    if (resolveBtn) resolveBtn.addEventListener('click', function () { resolveLocation(true); });
    // 输入框失焦就尝试解析：用户填完地点去看别的字段时，坐标已经准备好了
    if (locInput) {
      locInput.addEventListener('blur', function () { resolveLocation(false); });
      locInput.addEventListener('input', function () {
        // 改了地名，旧坐标立即作废，避免拿上一个地点的坐标算新地点
        if (locState.name && locInput.value.trim() !== locState.name) {
          locState = { lat: null, lon: null, name: '', source: 'default' };
          latInput.value = '';
          lonInput.value = '';
          setStatus('地点已修改，将重新解析', 'warn');
        }
      });
    }
    // 用户可能在别的模块新建了地块再回到这里，切回来时刷新下拉
    window.addEventListener('agritime:module-changed', function (e) {
      var m = e && e.detail && e.detail.module;
      if (m === 'recommend') fillPlots();
    });
    showHint();
  }

  function fillMonths() {
    monthSel.innerHTML = '';
    var now = new Date().getMonth() + 1;
    MONTHS.forEach(function (label, i) {
      var opt = document.createElement('option');
      opt.value = String(i + 1);
      opt.textContent = label;
      if (i + 1 === now) opt.selected = true;
      monthSel.appendChild(opt);
    });
  }

  /** 地块下拉：只提示有分析结果的地块，没有结果的选了也没法参与计算 */
  function fillPlots() {
    if (!plotSel || !window.MyPlotsStore) return;
    var plots = [];
    try { plots = window.MyPlotsStore.list() || []; } catch (e) { plots = []; }
    if (!plots.length) {
      plotSel.disabled = true;
      plotSel.innerHTML = '<option value="">还没有地块（去「我的地块」框一块）</option>';
      return;
    }
    plotSel.disabled = false;
    var keep = plotSel.value;
    plotSel.innerHTML = '<option value="">不关联地块</option>';
    plots.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = p.plotId;
      var hasAnalysis = p.analysis && p.analysis.suitableCrops && p.analysis.suitableCrops.length;
      opt.textContent = (p.name || '未命名地块') + (hasAnalysis ? '' : '（需先做地块分析）');
      plotSel.appendChild(opt);
    });
    if (keep) plotSel.value = keep;
  }

  function setStatus(text, level) {
    if (!locStatus) return;
    locStatus.textContent = text;
    locStatus.className = 'recommend-loc-status' + (level ? ' is-' + level : '');
  }

  function autoLocate() {
    if (!window.LocationPicker || !window.LocationPicker.getCurrentLocation) {
      setStatus('定位组件不可用，请手动输入地点', 'warn');
      return;
    }
    setStatus('定位中…');
    window.LocationPicker.getCurrentLocation(function (loc) {
      if (loc && window.LocationPicker.validateLatLon && window.LocationPicker.validateLatLon(loc.lat, loc.lon)) {
        locState = { lat: loc.lat, lon: loc.lon, name: loc.name || '当前位置', source: 'map' };
        latInput.value = loc.lat.toFixed(4);
        lonInput.value = loc.lon.toFixed(4);
        locInput.value = locState.name;
        setStatus('已使用当前位置：' + locState.name, 'ok');
      } else {
        setStatus('定位失败，请手动填写地点后点「解析地点」', 'error');
      }
    });
  }

  var PROVINCES = ['河北', '山西', '辽宁', '吉林', '黑龙江', '江苏', '浙江', '安徽', '福建', '江西',
    '山东', '河南', '湖北', '湖南', '广东', '广西', '海南', '四川', '贵州', '云南', '西藏',
    '陕西', '甘肃', '青海', '宁夏', '新疆', '内蒙古', '北京', '天津', '上海', '重庆'];

  /** "山东济宁" → "济宁"；"河北省石家庄市" → "石家庄"。用于第一次搜不到行政地名时补搜。 */
  function stripRegion(q) {
    var s = q.trim();
    PROVINCES.forEach(function (p) {
      if (s.indexOf(p) === 0 && s.length > p.length) {
        s = s.slice(p.length).replace(/^(省|市|自治区|壮族自治区|回族自治区|维吾尔自治区)/, '');
      }
    });
    return s.replace(/(市|地区|自治州|盟|区|县)$/, '') || q;
  }

  /**
   * 从候选里挑最像"地名"的一条。
   * Photon 对中文的返回经常先给企业/POI（如"山东济宁运河煤矿责任有限公司"），
   * 直接取第一条会把公司名当地名显示，所以按与输入的接近程度排序。
   */
  function pickBest(items, q) {
    if (!items || !items.length) return null;
    var scored = items.map(function (it) {
      var t = (it.title || '').trim();
      var sc = 0;
      if (t === q) sc = 100;
      else if (t.indexOf(q) === 0) sc = 85;
      else if (q.indexOf(t) === 0) sc = 80;
      else if (t.indexOf(q) >= 0) sc = 70;
      else if (q.indexOf(t.replace(/(市|区|县)$/, '')) >= 0) sc = 65;
      else sc = 40;
      // 名字冗长的多半是企业/POI，行政地名一般短
      var over = Math.max(0, t.length - q.length);
      sc -= Math.min(25, over * 3);
      return { item: it, score: sc };
    });
    scored.sort(function (a, b) { return b.score - a.score; });
    return scored[0].item;
  }

  function searchGeo(q) {
    return fetch(GEO + '?q=' + encodeURIComponent(q)).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).then(function (d) {
      return (d && d.data && d.data.items) || [];
    });
  }

  /**
   * 解析地点：先按原文搜，若命中的名字明显比输入长（多半是 POI），
   * 去掉省名再搜一次，取更像地名的那个。
   */
  function geocode(text) {
    var q = text.trim();
    return searchGeo(q).then(function (items) {
      var best = pickBest(items, q);
      var short = stripRegion(q);
      if (short === q) return { hit: best, name: q };
      // 带省名时几乎总会被解析成企业/园区（"河北省石家庄市"→"河北园-石家庄园"），
      // 所以只要去掉省名能搜到更像地名的结果，就优先用它
      return searchGeo(short).then(function (items2) {
        var best2 = pickBest(items2, short);
        if (!best2) return { hit: best, name: q };
        if (!best) return { hit: best2, name: q };
        var better = best2.title === short || best2.title.length < best.title.length;
        return { hit: better ? best2 : best, name: q };
      }).catch(function () {
        return { hit: best, name: q };
      });
    });
  }

  /**
   * 把地点文字解析成坐标。
   * @param {boolean} loud 用户主动点击时失败要明确告知；失焦触发时只在成功时更新
   */
  function resolveLocation(loud) {
    var text = (locInput.value || '').trim();
    if (!text) {
      clearLoc('未填地点，将按默认地点（北京）计算', 'warn');
      return;
    }
    if (locState.name === text && locState.lat != null) return; // 已经解析过

    setStatus('正在解析「' + text + '」…');
    geocode(text).then(function (res) {
      var hit = res.hit;
      if (!hit) {
        clearLoc('没能解析「' + text + '」，将按默认地点（北京）计算；可换成更具体的市名', 'error');
        return;
      }
      var lat = parseFloat(hit.lat);
      var lon = parseFloat(hit.lon);
      if (!(lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180)) {
        clearLoc('解析结果坐标异常，将按默认地点计算', 'error');
        return;
      }
      // 显示用户输入的地名，而不是 POI 全称，避免出现"XX矿业有限公司"
      locState = { lat: lat, lon: lon, name: res.name, source: 'geocode' };
      latInput.value = lat.toFixed(4);
      lonInput.value = lon.toFixed(4);
      setStatus('已解析「' + res.name + '」→ ' + lat.toFixed(2) + ', ' + lon.toFixed(2)
        + (hit.title && hit.title !== res.name ? '（' + hit.title + '）' : ''), 'ok');
      if (loud) showHint('地点已解析，可以获取推荐了');
    }).catch(function (err) {
      clearLoc('地点解析服务不可用（' + (err.message || '网络异常') + '），将按默认地点计算', 'error');
    });
  }

  function clearLoc(text, level) {
    locState = { lat: null, lon: null, name: '', source: 'default' };
    latInput.value = '';
    lonInput.value = '';
    setStatus(text, level);
  }

  /** 从选中地块里取适宜作物，拼成 "大蒜:86,小麦:74" */
  function plotContext() {
    if (!plotSel || !plotSel.value || !window.MyPlotsStore) {
      return { id: '', name: '', crops: '', top: [] };
    }
    var plot = null;
    try { plot = window.MyPlotsStore.getById(plotSel.value); } catch (e) { plot = null; }
    if (!plot) return { id: '', name: '', crops: '', top: [] };
    var top = (plot.analysis && plot.analysis.suitableCrops) || [];
    var crops = top.map(function (c) { return c.name + ':' + Math.round(c.score); }).join(',');
    return { id: plot.plotId, name: plot.name || '未命名地块', crops: crops, top: top };
  }

  function onSubmit(e) {
    e.preventDefault();
    var month = monthSel.value;

    // 用户填了地点却没解析过，先解析再请求，避免"填了等于没填"
    var text = (locInput.value || '').trim();
    if (text && (locState.lat == null || locState.name !== text)) {
      setStatus('正在解析地点…');
      geocode(text).then(function (res) {
        if (res.hit) {
          var lat = parseFloat(res.hit.lat);
          var lon = parseFloat(res.hit.lon);
          if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
            locState = { lat: lat, lon: lon, name: res.name, source: 'geocode' };
            latInput.value = lat.toFixed(4);
            lonInput.value = lon.toFixed(4);
            setStatus('已解析「' + res.name + '」→ ' + lat.toFixed(2) + ', ' + lon.toFixed(2), 'ok');
          }
        } else {
          clearLoc('没能解析「' + text + '」，本次按默认地点计算', 'error');
        }
        doRequest(month);
      }).catch(function () {
        clearLoc('地点解析服务不可用，本次按默认地点计算', 'error');
        doRequest(month);
      });
      return;
    }
    doRequest(month);
  }

  function doRequest(month) {
    var resolved = locState.lat != null && locState.lon != null;
    var lat = resolved ? locState.lat : DEFAULT_LAT;
    var lon = resolved ? locState.lon : DEFAULT_LON;
    var plot = plotContext();

    var params = [
      'month=' + encodeURIComponent(month),
      'lat=' + encodeURIComponent(lat),
      'lon=' + encodeURIComponent(lon),
      'locationSource=' + encodeURIComponent(resolved ? locState.source : 'default'),
      'location=' + encodeURIComponent(resolved ? locState.name : DEFAULT_NAME)
    ];
    if (plot.crops) {
      params.push('plotCrops=' + encodeURIComponent(plot.crops));
      params.push('plotName=' + encodeURIComponent(plot.name));
    }

    resultBox.innerHTML = '<div class="recommend-loading">正在综合节气、气候与市场数据…</div>';
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 20000);

    fetch(API + '?' + params.join('&'), { method: 'GET', signal: controller ? controller.signal : undefined })
      .then(function (res) {
        clearTimeout(timer);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) { render(data, plot); })
      .catch(function (err) {
        clearTimeout(timer);
        var msg = err && err.name === 'AbortError' ? '请求超时，请稍后重试' : '获取推荐失败：' + (err.message || '网络异常');
        showMessage(msg);
      });
  }

  function showHint(text) {
    showMessage(text || '选择种植月份和地点，点「获取推荐」，这里会给出最适宜种植的作物');
  }

  function showMessage(text) {
    resultBox.innerHTML = '';
    var d = document.createElement('div');
    d.className = 'recommend-empty';
    d.textContent = text;
    resultBox.appendChild(d);
  }

  function render(data, plot) {
    resultBox.innerHTML = '';
    // 先说清楚这次是按哪儿算的 —— V4 最大的坑就是界面不说，用户以为按他填的地方算
    resultBox.appendChild(renderContext(data, plot));

    var list = data.recommendations || [];
    if (!list.length) {
      var empty = document.createElement('div');
      empty.className = 'recommend-empty';
      empty.textContent = '这个月份没有匹配的作物，换个月份或地点再试';
      resultBox.appendChild(empty);
      return;
    }
    list.forEach(function (r) { resultBox.appendChild(renderCard(r)); });
  }

  function renderContext(data, plot) {
    var box = document.createElement('div');
    box.className = 'recommend-context';

    var loc = (data && data.location) || {};
    var locLine = document.createElement('div');
    locLine.className = 'recommend-context-line';
    if (loc.resolved) {
      locLine.textContent = '按地点「' + loc.name + '」（' + Number(loc.lat).toFixed(2) + ', '
        + Number(loc.lon).toFixed(2) + '）计算';
      locLine.classList.add('is-ok');
    } else {
      locLine.textContent = '未解析到地点，本次按默认地点（北京 35.07, 116.31）计算';
      locLine.classList.add('is-warn');
    }
    box.appendChild(locLine);

    if (plot && plot.top && plot.top.length) {
      var plotLine = document.createElement('div');
      plotLine.className = 'recommend-context-line is-ok';
      plotLine.textContent = '已结合地块「' + plot.name + '」的适宜作物 Top' + plot.top.length
        + '（' + plot.top.slice(0, 3).map(function (c) { return c.name; }).join('、') + '…）';
      box.appendChild(plotLine);
    } else if (plot && plot.id) {
      var tip = document.createElement('div');
      tip.className = 'recommend-context-line is-warn';
      tip.textContent = '所选地块还没有空间分析结果，本次未参与计算；可到地块详情点「分析这块地」';
      box.appendChild(tip);
    }

    return box;
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

    var bar = document.createElement('div');
    bar.className = 'recommend-bar';
    var fill = document.createElement('div');
    fill.className = 'recommend-bar-fill';
    fill.style.width = Math.min(100, Math.round((r.score || 0))) + '%';
    bar.appendChild(fill);
    card.appendChild(bar);

    var tags = document.createElement('div');
    tags.className = 'recommend-tags';
    tags.appendChild(tag(r.monthMatch === 'true' ? '本月适播' : '临近月份', r.monthMatch === 'true'));
    tags.appendChild(tag(r.climateMatch === 'true' ? '气候匹配' : '气候一般', r.climateMatch === 'true'));
    if (r.market && r.market !== '') tags.appendChild(tag('市场需求 ' + r.market, false));
    if (r.plotMatch === 'true') tags.appendChild(tag('匹配所选地块', true));
    card.appendChild(tags);

    if (r.reason) {
      var reason = document.createElement('p');
      reason.className = 'recommend-reason';
      reason.textContent = r.reason;
      card.appendChild(reason);
    }

    // 推荐依据：默认收起，点开看每一条为什么加分
    if (r.reasons && r.reasons.length) {
      var toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'recommend-why-toggle';
      toggle.textContent = '为什么推荐它 ▾';
      var panel = document.createElement('div');
      panel.className = 'recommend-why';
      panel.hidden = true;

      r.reasons.forEach(function (rs) {
        var row = document.createElement('div');
        row.className = 'recommend-why-row' + (rs.points > 0 ? ' is-hit' : '');
        var lb = document.createElement('strong');
        lb.textContent = rs.label;
        var dt = document.createElement('span');
        dt.textContent = rs.detail;
        var pt = document.createElement('b');
        pt.textContent = rs.points > 0 ? '+' + rs.points : '0';
        row.appendChild(lb);
        row.appendChild(dt);
        row.appendChild(pt);
        panel.appendChild(row);
      });

      toggle.addEventListener('click', function () {
        panel.hidden = !panel.hidden;
        toggle.textContent = panel.hidden ? '为什么推荐它 ▾' : '收起依据 ▴';
      });
      card.appendChild(toggle);
      card.appendChild(panel);
    }

    if (r.sowMonths && r.sowMonths.length) {
      var sm = document.createElement('div');
      sm.className = 'recommend-sow-months';
      sm.textContent = '适宜播种：' + r.sowMonths.map(function (m) { return m + '月'; }).join('、');
      card.appendChild(sm);
    }

    var foot = document.createElement('div');
    foot.className = 'recommend-foot';
    if (r.cropId && r.cropId !== '') {
      var go = document.createElement('button');
      go.type = 'button';
      go.className = 'btn btn-primary recommend-go';
      go.textContent = '进入农事日历 →';
      go.addEventListener('click', function () { goCalendar(r); });
      foot.appendChild(go);
    } else {
      var note = document.createElement('span');
      note.className = 'recommend-nocal';
      note.textContent = '该作物暂未接入农事日历';
      foot.appendChild(note);
    }
    card.appendChild(foot);

    return card;
  }

  function tag(text, active) {
    var s = document.createElement('span');
    s.className = 'recommend-tag' + (active ? ' recommend-tag--on' : '');
    s.textContent = text;
    return s;
  }

  /** 进日历时把地点和地块都带上，用户不用再填一遍 */
  function goCalendar(r) {
    var resolved = locState.lat != null && locState.lon != null;
    if (resolved) window.__markerLatLon = { lat: locState.lat, lon: locState.lon };
    var plot = plotContext();

    if (window.Sidebar && window.Sidebar.switchModule) window.Sidebar.switchModule('calendar');
    var cropInput = document.getElementById('crop');
    if (cropInput) {
      cropInput.value = r.name;
      cropInput.dispatchEvent(new Event('input', { bubbles: true }));
    }
    var locField = document.getElementById('location');
    if (locField && resolved && locState.name) {
      locField.value = locState.name;
      locField.dispatchEvent(new Event('input', { bubbles: true }));
    }
    if (plot.id) window.__timelinePlotId = plot.id;
  }

  if (typeof window !== 'undefined') {
    window.Recommend = { init: init, refreshPlots: fillPlots };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
