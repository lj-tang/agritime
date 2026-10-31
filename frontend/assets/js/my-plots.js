/**
 * 我的地块页面交互逻辑（T06）
 * 地块管理 + 环境感知 + 形状建模
 */
(function () {
  var PLOT_TYPES = {
    farmland: '耕田', economic: '经济作物田', greenhouse: '大棚',
    fishpond: '鱼塘', orchard: '果园', paddy: '水稻田'
  };
  var SHAPE_TYPES = { rectangle: '方形', strip: '条状', irregular: '不规则' };

  function init() {
    var pane = document.querySelector('[data-module="plots"]');
    if (!pane) return;
    renderList();
    var addBtn = document.getElementById('my-plots-add-btn');
    if (addBtn) addBtn.addEventListener('click', function () { showForm(null); });
    var form = document.getElementById('my-plots-form');
    if (form) form.addEventListener('submit', handleSubmit);
    var cancelBtn = document.getElementById('my-plots-cancel');
    if (cancelBtn) cancelBtn.addEventListener('click', function () { hideForm(); });
    bindLocationButtons();
    // 账号数据从服务端合并回来后重渲染，避免先显示本地旧数据
    window.addEventListener('agritime:data-synced', renderList);
  }

  function bindLocationButtons() {
    var autoBtn = document.getElementById('plot-loc-auto');
    var mapBtn = document.getElementById('plot-loc-map');
    var btn3d = document.getElementById('plot-loc-3d');
    if (autoBtn) autoBtn.addEventListener('click', autoLocate);
    if (mapBtn) mapBtn.addEventListener('click', mapPick);
    if (btn3d) btn3d.addEventListener('click', mapPick3D);
  }

  function autoLocate() {
    if (!window.LocationPicker || !window.LocationPicker.getCurrentLocation) {
      alert('定位组件未加载，请刷新页面重试');
      return;
    }
    var btn = document.getElementById('plot-loc-auto');
    if (btn) btn.textContent = '定位中…';
    var p = window.LocationPicker.getCurrentLocation();
    if (!p || !p.then) { if (btn) btn.textContent = '📍 自动定位'; return; }
    p.then(function (loc) {
      if (btn) btn.textContent = '📍 自动定位';
      if (!loc || !loc.lat) { alert('无法获取定位，请检查权限或使用地图框地'); return; }
      setPlotLocation({ lat: loc.lat, lon: loc.lon, name: loc.lat.toFixed(4) + ', ' + loc.lon.toFixed(4) });
    }).catch(function () {
      if (btn) btn.textContent = '📍 自动定位';
      alert('定位失败，请使用地图框地');
    });
  }

  function mapPick() {
    if (!window.LocationPicker || !window.LocationPicker.open) { alert('地图组件未加载'); return; }
    // draw=true：允许在地图上自行框地；默认进入画地块模式
    window.LocationPicker.open(function (res) {
      if (!res) return;
      setPlotLocation(res);
      if (res.mode === 'polygon' && res.polygon) analyzePolygon(res.polygon, res);
    }, { draw: true, mode: 'draw' });
  }

  /**
   * 3D 框选：卫星底图 + 地形起伏，按真实地块边界描点。
   * 与 2D 框选共用同一套回填逻辑，区别是能看到坡向与高低差。
   */
  function mapPick3D() {
    if (!window.Plot3D || !window.Plot3D.open) { alert('3D 地图组件未加载，请刷新页面重试'); return; }
    var lat = parseFloat(document.getElementById('plot-lat').value);
    var lon = parseFloat(document.getElementById('plot-lon').value);
    var existing = null;
    try {
      var raw = document.getElementById('plot-polygon').value;
      var parsed = raw ? JSON.parse(raw) : null;
      if (Array.isArray(parsed) && parsed.length >= 3) existing = parsed;
    } catch (e) { existing = null; }

    window.Plot3D.open({
      center: isFinite(lat) && isFinite(lon) ? { lat: lat, lon: lon } : { lat: 35.07, lon: 116.31 },
      polygon: existing,
      onConfirm: function (res) {
        setPlotLocation(res);
        analyzePolygon(res.polygon, res);
      }
    });
  }

  /**
   * 多边形空间分析：面积/坡度/坡向/高程差/评分/适宜作物全部由后端算。
   * 前端只负责展示，避免"前端自己编一个分数"。
   */
  function analyzePolygon(polygon, res) {
    var box = document.getElementById('plot-analysis-preview');
    if (box) {
      box.hidden = false;
      box.innerHTML = '<div class="plot-analysis-loading">正在分析地形与土壤…</div>';
    }
    // 后端直接返回顶层结构（与 /api/plots/environment 一致），不套 data 壳
    var typeEl = document.querySelector('#my-plots-form [name="plot-type"]');
    fetch('/api/plots/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(withMeasured({
        polygon: polygon,
        type: (typeEl && typeEl.value) || 'farmland'
      }))
    }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).then(function (out) {
      var d = out && out.error ? null : out;
      if (!d || d.score == null) throw new Error((out && out.error) || '返回为空');
      var hidden = document.getElementById('plot-analysis');
      if (hidden) hidden.value = JSON.stringify(d);
      if (res) applyAnalysisToForm(d, res);
      renderAnalysisPreview(box, d);
    }).catch(function (err) {
      if (box) box.innerHTML = '<div class="plot-analysis-error">分析失败：' +
        (err.message || '网络异常') + '，面积已保存，评分稍后重试</div>';
    });
  }

  /** 把分析结果回填成表单默认值：形状、坡度、倾角，用户不用自己填 */
  function applyAnalysisToForm(d, res) {
    var shapeSelect = document.querySelector('#my-plots-form [name="plot-shape"]');
    var slopeInput = document.querySelector('#my-plots-form [name="plot-slope"]');
    var angleInput = document.querySelector('#my-plots-form [name="plot-angle"]');
    var areaInput = document.querySelector('#my-plots-form [name="plot-area"]');
    var suitInput = document.getElementById('plot-suitability');

    var g = d.geometry || {};
    if (areaInput) {
      if (g.areaMu != null) areaInput.value = g.areaMu;
      else if (res && res.areaMu) areaInput.value = res.areaMu;
    }
    // 形状不再让用户手填：按紧凑度反推，机器判断比人眼选"方形/条状"更准
    if (shapeSelect && g.compactness != null) {
      shapeSelect.value = g.compactness >= 0.7 ? 'rectangle'
        : g.compactness >= 0.45 ? 'strip' : 'irregular';
    }
    if (slopeInput && d.terrain && d.terrain.slopeDeg != null) slopeInput.value = Math.round(d.terrain.slopeDeg);
    // 倾角存的是坡向方位角，跟坡向标签同源，避免两个字段互相矛盾
    if (angleInput && d.terrain && d.terrain.aspectDeg != null) angleInput.value = Math.round(d.terrain.aspectDeg);
    if (suitInput) {
      suitInput.value = JSON.stringify({
        status: d.level || 'unknown',
        score: d.score,
        areaMu: g.areaMu,
        areaSqm: g.areaM2,
        slopeDeg: d.terrain && d.terrain.slopeDeg,
        limits: (d.limits || []).map(function (l) { return l.label; })
      });
    }
  }

  /**
   * 对已保存地块重新跑一次空间分析并落库。
   * 老地块是手填参数建的、地形和墒情也会随时间变，所以必须能重算。
   */
  function reanalyzePlot(plot, box) {
    if (!plot.polygon || plot.polygon.length < 3) {
      alert('这块地还没有框选范围，请先点击「3D 框选地形」描出边界');
      return;
    }
    var holder = document.createElement('div');
    holder.className = 'plot-analysis-loading';
    holder.textContent = '正在分析地形与土壤…';
    box.appendChild(holder);
    fetch('/api/plots/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(withMeasured({ polygon: plot.polygon, type: plot.type || 'farmland' }, plot.plotId))
    }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).then(function (d) {
      if (!d || d.score == null) throw new Error((d && d.error) || '分析返回为空');
      if (window.MyPlotsStore) {
        window.MyPlotsStore.update(plot.plotId, {
          analysis: d,
          analysisAt: new Date().toISOString(),
          area: (d.geometry && d.geometry.areaMu) || plot.area
        });
        plot.analysis = d;
        plot.area = (d.geometry && d.geometry.areaMu) || plot.area;
      }
      holder.remove();
      var rb = document.createElement('div');
      rb.className = 'my-plots-analysis';
      renderAnalysisPreview(rb, d);
      box.insertBefore(rb, box.querySelector('.my-plots-card-meta') || null);
      renderList();
    }).catch(function (err) {
      holder.className = 'plot-analysis-error';
      holder.textContent = '分析失败：' + (err.message || '网络异常');
    });
  }

  /** 这块地上种了什么、还能种什么 */
  function renderPlotCrops(plot, detail) {
    var all = window.MyCropsStore ? window.MyCropsStore.list() : [];
    var mine = all.filter(function (r) { return r.plotId && r.plotId === plot.plotId; });

    var box = document.createElement('div');
    box.className = 'my-plots-env-section';
    var title = document.createElement('div');
    title.className = 'my-plots-env-section-title';
    title.textContent = '已种作物';
    box.appendChild(title);

    if (!mine.length) {
      var empty = document.createElement('div');
      empty.className = 'my-plots-card-meta';
      empty.textContent = '这块地还没有作物记录。在农事日历点「确认已播种」后会自动关联到这里。';
      box.appendChild(empty);
    } else {
      mine.forEach(function (r) {
        var row = document.createElement('div');
        row.className = 'my-plots-crop-row';
        var name = document.createElement('span');
        name.className = 'my-plots-crop-name';
        name.textContent = r.cropName || '未命名作物';
        var meta = document.createElement('span');
        meta.className = 'my-plots-crop-meta';
        var bits = [];
        if (r.sownDate) bits.push('播期 ' + r.sownDate);
        if (r.sownDate) {
          var days = Math.floor((Date.now() - new Date(r.sownDate + 'T00:00:00').getTime()) / 86400000);
          if (days >= 0) bits.push('已种 ' + days + ' 天');
        }
        if (r.growthVigor) bits.push('长势 ' + r.growthVigor);
        meta.textContent = bits.join(' · ');
        row.appendChild(name);
        row.appendChild(meta);
        box.appendChild(row);
      });
    }

    var rec = (plot.analysis && plot.analysis.crops) || [];
    if (rec.length) {
      var recTitle = document.createElement('div');
      recTitle.className = 'my-plots-env-section-title';
      recTitle.textContent = '这块地适合种';
      box.appendChild(recTitle);
      var tags = document.createElement('div');
      tags.className = 'my-plots-crop-tags';
      rec.slice(0, 5).forEach(function (c) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'my-plots-crop-tag';
        btn.textContent = c.name + (c.fit ? ' · ' + c.fit : '');
        btn.addEventListener('click', function () {
          var input = document.getElementById('crop');
          if (input) {
            input.value = c.name;
            input.dispatchEvent(new Event('input', { bubbles: true }));
          }
          if (window.Sidebar && window.Sidebar.switchModule) window.Sidebar.switchModule('calendar');
        });
        tags.appendChild(btn);
      });
      box.appendChild(tags);
    }
    detail.appendChild(box);
  }

  /**
   * 渲染后端真实分析结果。字段名必须与 PlotAnalyzeHandler 的返回一一对应，
   * 前端不自己算分、不自己补默认值 —— 拿不到就显示"未取到"，不编造。
   */
  function renderAnalysisPreview(box, d) {
    if (!box) return;
    box.innerHTML = '';

    var head = document.createElement('div');
    head.className = 'plot-analysis-head';
    var score = document.createElement('span');
    score.className = 'plot-analysis-score';
    score.textContent = d.score == null ? '—' : d.score;
    var meta = document.createElement('div');
    var level = document.createElement('strong');
    level.textContent = d.level || '待评估';
    var label = document.createElement('span');
    label.className = 'plot-analysis-label';
    label.textContent = (d.type === 'pond' ? '塘址' : '地块') + '综合适宜度';
    meta.appendChild(level);
    meta.appendChild(document.createElement('br'));
    meta.appendChild(label);
    head.appendChild(score);
    head.appendChild(meta);
    box.appendChild(head);

    // 实测几何与地形：这是"框了真有分析"的直观证据
    var facts = [];
    var g = d.geometry || {};
    var t = d.terrain || {};
    if (g.areaMu != null) facts.push('面积 ' + g.areaMu + ' 亩');
    if (g.perimeterM != null) facts.push('周长 ' + g.perimeterM + ' m');
    if (t.available) {
      if (t.slopeDeg != null) facts.push('坡度 ' + t.slopeDeg + '°');
      if (t.relief != null) facts.push('高差 ' + t.relief + ' m');
      if (t.elevMin != null && t.elevMax != null) facts.push('高程 ' + t.elevMin + '~' + t.elevMax + ' m');
      if (t.aspectLabel) facts.push('坡向 ' + t.aspectLabel);
      if (t.sampleCount) facts.push('DEM 采样 ' + t.sampleCount + ' 点');
    } else {
      facts.push('地形数据未取到，地形项不计分');
    }
    if (d.soil && d.soil.soilType) facts.push('土壤 ' + d.soil.soilType);
    if (d.region) facts.push('区划 ' + d.region);
    if (facts.length) {
      var f = document.createElement('div');
      f.className = 'plot-analysis-facts';
      f.textContent = facts.join(' · ');
      box.appendChild(f);
    }

    // 分项评分：让用户看见分数是怎么来的
    if (d.factors && d.factors.length) {
      var wrap = document.createElement('div');
      wrap.className = 'my-plots-analysis-factors';
      d.factors.forEach(function (fa) {
        var row = document.createElement('div');
        row.className = 'my-plots-analysis-factor';
        var k = document.createElement('span');
        k.textContent = fa.label;
        var bar = document.createElement('i');
        var lv = fa.score >= 80 ? '' : fa.score >= 60 ? ' plot-factor-bar--mid' : ' plot-factor-bar--low';
        bar.className = 'plot-factor-bar' + lv;
        bar.style.setProperty('--w', Math.max(0, Math.min(100, fa.score)) + '%');
        var v = document.createElement('b');
        v.textContent = Math.round(fa.score);
        row.appendChild(k);
        row.appendChild(bar);
        row.appendChild(v);
        row.title = fa.note || '';
        wrap.appendChild(row);
      });
      box.appendChild(wrap);
      var note = document.createElement('div');
      note.className = 'plot-analysis-label';
      note.textContent = '权重：' + d.factors.map(function (fa) {
        return fa.label + ' ' + Math.round(fa.weight * 100) + '%';
      }).join(' · ');
      box.appendChild(note);
    }

    // 限制因子
    if (d.limits && d.limits.length) {
      var lim = document.createElement('div');
      lim.className = 'plot-analysis-limit';
      lim.innerHTML = '<strong>限制因子：</strong>';
      d.limits.forEach(function (l, i) {
        var line = document.createElement('div');
        line.textContent = (i + 1) + '. ' + l.label + '（' +
          (l.severity === 'high' ? '高' : l.severity === 'mid' ? '中' : '低') + '）— ' + l.detail;
        lim.appendChild(line);
      });
      box.appendChild(lim);
    }

    // 适宜作物：点一下直接填进日历
    if (d.suitableCrops && d.suitableCrops.length) {
      var crops = document.createElement('div');
      crops.className = 'plot-analysis-crops';
      var ct = document.createElement('span');
      ct.className = 'plot-analysis-crops-title';
      ct.textContent = '推荐种：';
      crops.appendChild(ct);
      d.suitableCrops.slice(0, 5).forEach(function (c) {
        var tag = document.createElement('button');
        tag.type = 'button';
        tag.className = 'plot-analysis-crop';
        tag.textContent = c.name + ' ' + Math.round(c.score) + '分';
        tag.title = c.reason + '（全生育期约 ' + c.days + ' 天）';
        tag.addEventListener('click', function () {
          var input = document.getElementById('crop');
          if (input) {
            input.value = c.name;
            input.dispatchEvent(new Event('input', { bubbles: true }));
          }
          if (window.Sidebar && window.Sidebar.switchModule) window.Sidebar.switchModule('calendar');
        });
        crops.appendChild(tag);
      });
      box.appendChild(crops);
    }

    if (d.summary) {
      var s = document.createElement('div');
      s.className = 'plot-analysis-label';
      s.style.marginTop = '8px';
      s.textContent = d.summary;
      box.appendChild(s);
    }
  }

  /**
   * 回填地块位置。兼容两种结果：
   *   · 标点：{lat, lon, name}
   *   · 框地：{mode:'polygon', polygon, center, areaSqm, areaMu, name, suitability}
   */
  function setPlotLocation(res) {
    if (!res) return;
    var lat = res.lat != null ? res.lat : (res.center && res.center.lat);
    var lon = res.lon != null ? res.lon : (res.center && res.center.lon);
    var latInput = document.getElementById('plot-lat');
    var lonInput = document.getElementById('plot-lon');
    var locInput = document.getElementById('plot-location');
    if (latInput) latInput.value = lat != null ? lat : '';
    if (lonInput) lonInput.value = lon != null ? lon : '';
    if (locInput && res.name) locInput.value = res.name;

    var polyInput = document.getElementById('plot-polygon');
    var suitInput = document.getElementById('plot-suitability');
    var areaInput = document.querySelector('#my-plots-form [name="plot-area"]');
    var shapeSelect = document.querySelector('#my-plots-form [name="plot-shape"]');

    if (res.mode && res.mode.indexOf('polygon') === 0 && Array.isArray(res.polygon) && res.polygon.length >= 3) {
      if (polyInput) polyInput.value = JSON.stringify(res.polygon);
      if (suitInput) {
        suitInput.value = JSON.stringify({
          status: res.suitability ? res.suitability.status : 'unknown',
          reason: res.suitability ? res.suitability.reason : '',
          areaSqm: res.areaSqm,
          areaMu: res.areaMu
        });
      }
      if (areaInput && res.areaMu != null) areaInput.value = res.areaMu;
      if (shapeSelect) shapeSelect.value = 'irregular';
    } else {
      if (polyInput) polyInput.value = '';
      if (suitInput) suitInput.value = '';
    }
  }

  function renderList() {
    var container = document.getElementById('my-plots-list');
    if (!container || !window.MyPlotsStore) return;
    var items = window.MyPlotsStore.list();
    container.innerHTML = '';
    if (!items.length) {
      container.innerHTML = '<div class="my-plots-empty">还没有地块记录，点击「添加地块」开始管理</div>';
      return;
    }
    var cards = document.createElement('div');
    cards.className = 'my-plots-cards';
    items.forEach(function (p) { cards.appendChild(renderCard(p)); });
    container.appendChild(cards);
  }

  function renderCard(p) {
    var card = document.createElement('div');
    card.className = 'my-plots-card';
    var head = document.createElement('div');
    head.className = 'my-plots-card-head';
    var name = document.createElement('div');
    name.className = 'my-plots-card-name';
    name.textContent = p.name || '未命名地块';
    head.appendChild(name);
    var typeBadge = document.createElement('div');
    typeBadge.className = 'my-plots-card-type';
    typeBadge.textContent = PLOT_TYPES[p.type] || p.type || '未分类';
    head.appendChild(typeBadge);
    card.appendChild(head);

    var meta = document.createElement('div');
    meta.className = 'my-plots-card-meta';
    var locSpan = document.createElement('span');
    locSpan.textContent = '📍 ' + (p.location || (p.lat && p.lon ? Number(p.lat).toFixed(3) + ',' + Number(p.lon).toFixed(3) : '未定位'));
    meta.appendChild(locSpan);
    if (p.area) { var a = document.createElement('span'); a.textContent = '面积：' + p.area + '亩'; meta.appendChild(a); }
    if (p.polygon && p.polygon.length >= 3) {
      var g = document.createElement('span'); g.className = 'my-plots-card-range'; g.textContent = '已绘范围'; meta.appendChild(g);
    }
    if (p.suitability && p.suitability.status === 'caution') {
      var w = document.createElement('span'); w.className = 'my-plots-card-warn'; w.textContent = '城市绿地'; meta.appendChild(w);
    }
    // 农事适宜度快照（查看详情时回写），直接显示分值
    if (p.suitability && p.suitability.score != null) {
      var sc = document.createElement('span');
      sc.className = 'my-plots-card-suit';
      sc.textContent = '适宜度 ' + p.suitability.score + '（' + p.suitability.status + '）';
      meta.appendChild(sc);
    }
    if (p.terrace) { var t = document.createElement('span'); t.textContent = '梯田'; meta.appendChild(t); }
    card.appendChild(meta);

    if (p.shape || p.angle || p.slope) {
      var shape = document.createElement('div');
      shape.className = 'my-plots-card-shape';
      var parts = [];
      if (p.shape) parts.push('形状：' + (SHAPE_TYPES[p.shape] || p.shape));
      if (p.angle) parts.push('倾角：' + p.angle + '°');
      if (p.slope) parts.push('坡度：' + p.slope + '°');
      shape.textContent = parts.join(' · ');
      card.appendChild(shape);
    }

    var actions = document.createElement('div');
    actions.className = 'my-plots-card-actions';
    var detailBtn = document.createElement('button');
    detailBtn.type = 'button'; detailBtn.className = 'btn btn-primary'; detailBtn.textContent = '查看详情';
    detailBtn.addEventListener('click', function () { showDetail(p); });
    actions.appendChild(detailBtn);
    var editBtn = document.createElement('button');
    editBtn.type = 'button'; editBtn.className = 'btn btn-ghost'; editBtn.textContent = '编辑';
    editBtn.addEventListener('click', function () { showForm(p); });
    actions.appendChild(editBtn);
    var delBtn = document.createElement('button');
    delBtn.type = 'button'; delBtn.className = 'btn btn-ghost'; delBtn.textContent = '删除';
    delBtn.addEventListener('click', function () {
      if (confirm('确认删除地块「' + (p.name || '未命名') + '」？')) {
        window.MyPlotsStore.remove(p.plotId);
        renderList();
      }
    });
    actions.appendChild(delBtn);
    card.appendChild(actions);
    return card;
  }

  function showForm(record) {
    var form = document.getElementById('my-plots-form');
    if (!form) return;
    form.classList.add('show');
    var f = form.elements;
    f['plot-id'].value = record ? record.plotId : '';
    f['plot-name'].value = record ? (record.name || '') : '';
    f['plot-type'].value = record ? (record.type || 'farmland') : 'farmland';
    f['plot-location'].value = record ? (record.location || '') : '';
    f['plot-lat'].value = record ? (record.lat || '') : '';
    f['plot-lon'].value = record ? (record.lon || '') : '';
    f['plot-shape'].value = record ? (record.shape || 'rectangle') : 'rectangle';
    f['plot-angle'].value = record ? (record.angle || 0) : 0;
    f['plot-slope'].value = record ? (record.slope || 0) : 0;
    f['plot-terrace'].checked = record ? !!record.terrace : false;
    f['plot-area'].value = record ? (record.area || '') : '';
    f['plot-remark'].value = record ? (record.remark || '') : '';
    if (f['plot-polygon']) f['plot-polygon'].value = record && record.polygon ? JSON.stringify(record.polygon) : '';
    if (f['plot-suitability']) f['plot-suitability'].value = record && record.suitability ? JSON.stringify(record.suitability) : '';
    form.scrollIntoView({ behavior: 'smooth' });
  }

  function hideForm() {
    var form = document.getElementById('my-plots-form');
    if (form) form.classList.remove('show');
  }

  function handleSubmit(e) {
    e.preventDefault();
    var f = e.target.elements;
    var name = f['plot-name'].value.trim();
    if (!name) { alert('请输入地块名称'); return; }
    var lat = parseFloat(f['plot-lat'].value);
    var lon = parseFloat(f['plot-lon'].value);
    if (!isFinite(lat) || !isFinite(lon)) { alert('请通过自动定位或地图选点设置地块位置'); return; }
    var polygon = null;
    try {
      var polyRaw = f['plot-polygon'] ? f['plot-polygon'].value : '';
      var polyParsed = polyRaw ? JSON.parse(polyRaw) : null;
      if (Array.isArray(polyParsed) && polyParsed.length >= 3) polygon = polyParsed;
    } catch (e) { polygon = null; }

    var suitability = null;
    try {
      var suitRaw = f['plot-suitability'] ? f['plot-suitability'].value : '';
      suitability = suitRaw ? JSON.parse(suitRaw) : null;
    } catch (e) { suitability = null; }

    // 多边形分析快照：评分、地形、适宜作物。列表与详情直接读这份，不再重算。
    var analysis = null;
    try {
      var anRaw = f['plot-analysis'] ? f['plot-analysis'].value : '';
      analysis = anRaw ? JSON.parse(anRaw) : null;
    } catch (e) { analysis = null; }

    var data = {
      name: name,
      type: f['plot-type'].value || 'farmland',
      location: f['plot-location'].value.trim() || null,
      lat: lat, lon: lon,
      polygon: polygon,
      suitability: suitability,
      analysis: analysis,
      shape: f['plot-shape'].value || 'rectangle',
      angle: parseFloat(f['plot-angle'].value) || 0,
      slope: parseFloat(f['plot-slope'].value) || 0,
      terrace: f['plot-terrace'].checked,
      area: f['plot-area'].value ? parseFloat(f['plot-area'].value) : null,
      remark: f['plot-remark'].value.trim() || null
    };
    var id = f['plot-id'].value;
    if (id) { window.MyPlotsStore.update(id, data); } else { window.MyPlotsStore.add(data); }
    hideForm();
    renderList();
  }

  function showDetail(plot) {
    var detail = document.getElementById('my-plots-detail');
    if (!detail) return;
    document.getElementById('my-plots-list').style.display = 'none';
    document.getElementById('my-plots-form').classList.remove('show');
    var addBtn = document.getElementById('my-plots-add-btn');
    if (addBtn) addBtn.style.display = 'none';
    detail.classList.add('show');

    detail.innerHTML = '';
    var back = document.createElement('button');
    back.type = 'button'; back.className = 'my-plots-back'; back.textContent = '← 返回列表';
    back.addEventListener('click', function () {
      detail.classList.remove('show');
      document.getElementById('my-plots-list').style.display = '';
      if (addBtn) addBtn.style.display = '';
    });
    detail.appendChild(back);

    var h3 = document.createElement('h3');
    h3.textContent = plot.name || '未命名地块';
    detail.appendChild(h3);
    var sub = document.createElement('div');
    sub.className = 'my-plots-detail-sub';
    var subParts = [PLOT_TYPES[plot.type] || plot.type || '未分类'];
    if (plot.location) subParts.push(plot.location);
    if (plot.area) subParts.push(plot.area + '亩');
    if (plot.terrace) subParts.push('梯田');
    sub.textContent = subParts.join(' · ');
    detail.appendChild(sub);

    if (plot.lat && plot.lon && window.LocationPicker && window.LocationPicker.initDisplay) {
      var mapDiv = document.createElement('div');
      mapDiv.id = 'plot-detail-map';
      var hasPolygon = plot.polygon && plot.polygon.length >= 3;
      mapDiv.style.cssText = 'height:' + (hasPolygon ? 240 : 180) + 'px;border-radius:12px;overflow:hidden;margin-bottom:8px;border:1px solid var(--line);';
      detail.appendChild(mapDiv);
      if (hasPolygon && window.Plot3D) {
        var view3d = document.createElement('button');
        view3d.type = 'button';
        view3d.className = 'my-plots-3d-view';
        view3d.textContent = '🛰 3D 查看地形起伏';
        view3d.addEventListener('click', function () {
          window.Plot3D.open({
            center: { lat: plot.lat, lon: plot.lon },
            polygon: plot.polygon,
            onConfirm: function (res) {
              // 查看模式下确认只更新表单，不直接改已保存地块
              var hidden = document.getElementById('plot-polygon');
              if (hidden && res && res.polygon) hidden.value = JSON.stringify(res.polygon);
            }
          });
        });
        var viewWrap = document.createElement('div');
        viewWrap.className = 'my-plots-3d-row';
        viewWrap.appendChild(view3d);
        detail.appendChild(viewWrap);
      }
      setTimeout(function () {
        window.LocationPicker.initDisplay('plot-detail-map', plot.lat, plot.lon, plot.polygon);
      }, 50);
    }

    if (plot.polygon && plot.polygon.length >= 3) {
      var rangeSection = document.createElement('div');
      rangeSection.className = 'my-plots-env-section';
      var rangeTitle = document.createElement('div');
      rangeTitle.className = 'my-plots-env-section-title';
      rangeTitle.textContent = '地块范围';
      rangeSection.appendChild(rangeTitle);
      var rangeMeta = document.createElement('div');
      rangeMeta.className = 'my-plots-card-meta';
      var rangeParts = [];
      rangeParts.push('面积：' + (plot.area ? plot.area + ' 亩' : '未记录'));
      rangeParts.push('边界点：' + plot.polygon.length + ' 个');
      if (plot.suitability && plot.suitability.status) {
        var statusText = {
          suitable: '土地类型适宜种植',
          caution: '城市绿地，需确认使用权限',
          unsuitable: '城市建成区，不建议种植',
          unknown: '土地类型未知'
        };
        if (statusText[plot.suitability.status]) rangeParts.push(statusText[plot.suitability.status]);
      }
      rangeMeta.textContent = rangeParts.join(' · ');
      rangeSection.appendChild(rangeMeta);
      if (plot.suitability && plot.suitability.reason) {
        var reason = document.createElement('div');
        reason.className = 'my-plots-range-reason';
        reason.textContent = plot.suitability.reason;
        rangeSection.appendChild(reason);
      }
      detail.appendChild(rangeSection);
    }

    var shapeSection = document.createElement('div');
    shapeSection.className = 'my-plots-env-section';
    var shapeTitle = document.createElement('div');
    shapeTitle.className = 'my-plots-env-section-title';
    var a = plot.analysis;
    shapeTitle.textContent = a ? '地块空间分析' : '地块建模';
    shapeSection.appendChild(shapeTitle);

    if (a) {
      // 与框选预览共用同一套渲染，避免两处字段名各写一份、改一处漏一处
      var aBox = document.createElement('div');
      aBox.className = 'my-plots-analysis';
      renderAnalysisPreview(aBox, a);
      shapeSection.appendChild(aBox);
      var reBtn = document.createElement('button');
      reBtn.type = 'button';
      reBtn.className = 'my-plots-crop-tag';
      reBtn.style.marginTop = '8px';
      reBtn.textContent = '🔄 重新分析（刷新地形与墒情）';
      reBtn.addEventListener('click', function () { reanalyzePlot(plot, shapeSection); });
      shapeSection.appendChild(reBtn);
    } else {
      var shapeMeta = document.createElement('div');
      shapeMeta.className = 'my-plots-card-meta';
      var shapeParts = [];
      shapeParts.push('形状：' + (SHAPE_TYPES[plot.shape] || plot.shape || '未指定'));
      if (plot.angle) shapeParts.push('倾角：' + plot.angle + '°');
      if (plot.slope) shapeParts.push('坡度：' + plot.slope + '°');
      if (plot.terrace) shapeParts.push('梯田');
      shapeMeta.textContent = shapeParts.join(' · ');
      shapeSection.appendChild(shapeMeta);
      // 老地块是手填参数建的，没有空间分析结果，这里给一个补齐入口
      if (plot.polygon && plot.polygon.length >= 3) {
        var anBtn = document.createElement('button');
        anBtn.type = 'button';
        anBtn.className = 'my-plots-crop-tag';
        anBtn.style.marginTop = '8px';
        anBtn.textContent = '🛰 分析这块地的地形与土壤';
        anBtn.addEventListener('click', function () { reanalyzePlot(plot, shapeSection); });
        shapeSection.appendChild(anBtn);
      }
    }
    detail.appendChild(shapeSection);

    renderPlotCrops(plot, detail);

    var envSection = document.createElement('div');
    envSection.className = 'my-plots-env-section';
    var envTitle = document.createElement('div');
    envTitle.className = 'my-plots-env-section-title';
    envTitle.textContent = '实时环境监控';
    envSection.appendChild(envTitle);
    var loading = document.createElement('div');
    loading.className = 'my-plots-env-loading';
    loading.textContent = '正在获取环境数据…';
    envSection.appendChild(loading);
    detail.appendChild(envSection);

    fetchEnvironment(plot, envSection, loading);

    // 鱼塘单独一块：水温/溶氧/投喂量/泛塘风险，只有塘才有意义
    if (plot.type === 'pond' || plot.type === 'fishpond' || plot.type === 'aquaculture') {
      renderPondPanel(plot, detail);
    }

    // 监测设备：用户自己决定接不接，不接就继续用模型估算
    renderSensors(plot, detail);
  }

  /**
   * 监测设备面板。默认什么都不装 —— 模型能算的先算，
   * 用户愿意插探头就插，插了就以实测为准，界面上标清楚哪个数是测的、哪个是算的。
   */
  function renderSensors(plot, detail) {
    var box = document.createElement('div');
    box.className = 'my-plots-env-section';
    var title = document.createElement('div');
    title.className = 'my-plots-env-section-title';
    title.textContent = '监测设备（可选）';
    box.appendChild(title);

    var hint = document.createElement('div');
    hint.className = 'my-plots-card-meta';
    hint.textContent = W_STORE_READY()
      ? '接入后，实测值会覆盖模型估算，并在结论里标注「实测」。'
      : '当前浏览器不支持本地存储，设备信息无法保存。';
    box.appendChild(hint);

    var listBox = document.createElement('div');
    listBox.className = 'sensor-list';
    box.appendChild(listBox);

    function renderDevices() {
      listBox.innerHTML = '';
      var devices = window.SensorsStore ? window.SensorsStore.list(plot.plotId) : [];
      if (!devices.length) {
        var empty = document.createElement('div');
        empty.className = 'sensor-empty';
        empty.textContent = '还没有接设备。不接也能用，只是数据来自模型推算。';
        listBox.appendChild(empty);
      }
      devices.forEach(function (d) {
        var meta = window.SensorsStore.KINDS[d.kind] || { label: d.kind, unit: '' };
        var row = document.createElement('div');
        row.className = 'sensor-row';

        var head = document.createElement('div');
        head.className = 'sensor-row-head';
        var nm = document.createElement('strong');
        nm.textContent = d.name || meta.label;
        var tag = document.createElement('span');
        tag.className = 'sensor-tag';
        tag.textContent = window.SensorsStore.SOURCES[d.source] || d.source;
        head.appendChild(nm);
        head.appendChild(tag);
        row.appendChild(head);

        var last = d.readings && d.readings.length ? d.readings[d.readings.length - 1] : null;
        var val = document.createElement('div');
        val.className = 'sensor-value';
        if (d.kind === 'camera') {
          val.textContent = d.url ? '监控地址已记录' : '未填监控地址';
        } else if (last) {
          var stale = window.SensorsStore.isStale(last);
          val.textContent = '最新：' + last.value + ' ' + meta.unit + ' · ' + last.at.slice(0, 16).replace('T', ' ')
            + (stale ? '（可能已过期）' : '（实测）');
          val.className = 'sensor-value' + (stale ? ' is-stale' : ' is-fresh');
        } else {
          val.textContent = '还没有读数';
        }
        row.appendChild(val);

        if (d.kind === 'camera' && d.url) {
          var img = document.createElement('img');
          img.className = 'sensor-camera';
          img.alt = '监控画面';
          img.loading = 'lazy';
          img.src = d.url;
          img.onerror = function () {
            img.replaceWith(Object.assign(document.createElement('div'), {
              className: 'sensor-note',
              textContent: '画面加载失败（可能是内网地址或需要鉴权）'
            }));
          };
          row.appendChild(img);
        }

        if (d.kind !== 'camera') {
          var inputRow = document.createElement('div');
          inputRow.className = 'sensor-input-row';
          var inp = document.createElement('input');
          inp.type = 'number';
          inp.step = 'any';
          inp.placeholder = '输入本次读数';
          inp.min = meta.min;
          inp.max = meta.max;
          var ok = document.createElement('button');
          ok.type = 'button';
          ok.className = 'my-plots-crop-tag';
          ok.textContent = '记一笔';
          ok.addEventListener('click', function () {
            if (!inp.value) return;
            window.SensorsStore.addReading(d.sensorId, inp.value);
            renderDevices();
          });
          inputRow.appendChild(inp);
          inputRow.appendChild(ok);
          row.appendChild(inputRow);
        }

        var del = document.createElement('button');
        del.type = 'button';
        del.className = 'sensor-del';
        del.textContent = '移除';
        del.addEventListener('click', function () {
          if (confirm('移除这台设备？已记录的读数也会一并删除。')) {
            window.SensorsStore.remove(d.sensorId);
            renderDevices();
          }
        });
        row.appendChild(del);

        listBox.appendChild(row);
      });
    }

    renderDevices();

    var addRow = document.createElement('div');
    addRow.className = 'sensor-add';
    var kindSel = document.createElement('select');
    Object.keys(window.SensorsStore ? window.SensorsStore.KINDS : {}).forEach(function (k) {
      var o = document.createElement('option');
      o.value = k;
      o.textContent = window.SensorsStore.KINDS[k].label;
      kindSel.appendChild(o);
    });
    var srcSel = document.createElement('select');
    Object.keys(window.SensorsStore ? window.SensorsStore.SOURCES : {}).forEach(function (k) {
      var o = document.createElement('option');
      o.value = k;
      o.textContent = window.SensorsStore.SOURCES[k];
      srcSel.appendChild(o);
    });
    var urlInput = document.createElement('input');
    urlInput.type = 'text';
    urlInput.placeholder = '接口/快照地址（可选）';
    var addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'my-plots-3d-view';
    addBtn.textContent = '添加设备';
    addBtn.addEventListener('click', function () {
      if (!window.SensorsStore) return;
      window.SensorsStore.add(plot.plotId, {
        kind: kindSel.value,
        source: srcSel.value,
        url: urlInput.value.trim()
      });
      urlInput.value = '';
      renderDevices();
      showSensorHint(box);
    });
    addRow.appendChild(kindSel);
    addRow.appendChild(srcSel);
    addRow.appendChild(urlInput);
    addRow.appendChild(addBtn);
    box.appendChild(addRow);

    detail.appendChild(box);
  }

  function showSensorHint(box) {
    var tip = box.querySelector('.sensor-saved');
    if (!tip) {
      tip = document.createElement('div');
      tip.className = 'sensor-saved';
      box.appendChild(tip);
    }
    tip.textContent = '设备已添加。填入一次读数后，分析就会优先使用实测值。';
  }

  function W_STORE_READY() {
    try {
      localStorage.setItem('agritime.__t', '1');
      localStorage.removeItem('agritime.__t');
      return true;
    } catch (e) {
      return false;
    }
  }

  // 兜底清单：正常情况从 /api/industry/species 读，保证与后端品种库同源
  var POND_SPECIES = ['草鱼', '鲢鱼', '鳙鱼', '鲤鱼', '鲫鱼', '青鱼', '罗非鱼', '南美白对虾', '河蟹'];
  var SPECIES_CACHE = null;

  function loadSpecies(cb) {
    if (SPECIES_CACHE) { cb(SPECIES_CACHE); return; }
    fetch('/api/industry/species').then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).then(function (d) {
      var list = (d && d.data && d.data.species) || [];
      var names = list.map(function (x) { return x.name; }).filter(Boolean);
      SPECIES_CACHE = names.length ? names : POND_SPECIES.slice();
      cb(SPECIES_CACHE);
    }).catch(function () {
      SPECIES_CACHE = POND_SPECIES.slice();
      cb(SPECIES_CACHE);
    });
  }

  /** 鱼塘水质与投喂面板：数值全部来自 /api/pond-status，前端不做任何估算。 */
  function renderPondPanel(plot, detail) {
    var box = document.createElement('div');
    box.className = 'my-plots-env-section';
    var title = document.createElement('div');
    title.className = 'my-plots-env-section-title';
    title.textContent = '鱼塘水质与投喂';
    box.appendChild(title);

    var grid = document.createElement('div');
    grid.className = 'pond-form';
    function field(labelText, id, defVal, unit) {
      var wrap = document.createElement('label');
      wrap.className = 'pond-field';
      var t = document.createElement('span');
      t.textContent = labelText + (unit ? '（' + unit + '）' : '');
      var i = document.createElement('input');
      i.id = id;
      i.type = 'number';
      i.step = 'any';
      i.value = defVal == null ? '' : defVal;
      wrap.appendChild(t);
      wrap.appendChild(i);
      grid.appendChild(wrap);
      return i;
    }
    var selWrap = document.createElement('label');
    selWrap.className = 'pond-field';
    var selT = document.createElement('span');
    selT.textContent = '养殖品种';
    var sel = document.createElement('select');
    sel.id = 'pond-species';
    POND_SPECIES.forEach(function (n) {
      var o = document.createElement('option');
      o.value = n;
      o.textContent = n;
      sel.appendChild(o);
    });
    loadSpecies(function (names) {
      var keep = sel.value;
      sel.innerHTML = '';
      names.forEach(function (n) {
        var o = document.createElement('option');
        o.value = n;
        o.textContent = n;
        sel.appendChild(o);
      });
      if (keep) sel.value = keep;
    });
    selWrap.appendChild(selT);
    selWrap.appendChild(sel);
    grid.appendChild(selWrap);

    var areaMu = plot.area || (plot.analysis && plot.analysis.geometry && plot.analysis.geometry.areaMu) || 5;
    field('水面面积', 'pond-area', areaMu, '亩');
    field('放养密度', 'pond-density', 1000, '尾/亩');
    field('当前规格', 'pond-weight', 250, 'g/尾');
    var mp = window.SensorsStore ? window.SensorsStore.measuredParams(plot.plotId) : {};
    var phInput = field('实测 pH', 'pond-ph', mp.ph != null ? mp.ph : '', '选填');
    var nhInput = field('实测氨氮', 'pond-ammonia', mp.ammonia != null ? mp.ammonia : '', 'mg/L，选填');
    var doInput = field('实测溶氧', 'pond-do', mp.dissolvedOxygen != null ? mp.dissolvedOxygen : '', 'mg/L，选填');
    if (mp.ph != null || mp.ammonia != null || mp.dissolvedOxygen != null) {
      var srcNote = document.createElement('div');
      srcNote.className = 'pond-note';
      srcNote.textContent = '已带入传感器最新读数；如需覆盖请直接改上面的数值。';
      box.appendChild(srcNote);
    }
    box.appendChild(grid);

    var btnRow = document.createElement('div');
    btnRow.className = 'pond-actions';
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'my-plots-3d-view';
    btn.textContent = '计算水质与投喂量';
    btnRow.appendChild(btn);
    box.appendChild(btnRow);

    var out = document.createElement('div');
    out.className = 'pond-result';
    out.textContent = '填入存塘情况后计算；实测值优先于模型估算。';
    box.appendChild(out);

    btn.addEventListener('click', function () {
      out.textContent = '正在获取水温与天气…';
      var qs = 'lat=' + encodeURIComponent(plot.lat || 35.07) + '&lon=' + encodeURIComponent(plot.lon || 116.31)
        + '&species=' + encodeURIComponent(sel.value)
        + '&areaMu=' + encodeURIComponent(num('pond-area', 5))
        + '&densityPerMu=' + encodeURIComponent(num('pond-density', 1000))
        + '&avgWeightG=' + encodeURIComponent(num('pond-weight', 250));
      var ph = val('pond-ph'), nh = val('pond-ammonia'), dd = val('pond-do');
      if (ph !== '') qs += '&ph=' + encodeURIComponent(ph);
      if (nh !== '') qs += '&ammonia=' + encodeURIComponent(nh);
      if (dd !== '') qs += '&dissolvedOxygen=' + encodeURIComponent(dd);
      // 水温若接了传感器就直接用实测，比气温推算准
      var wtm = window.SensorsStore ? window.SensorsStore.latest(plot.plotId, 'water_temp') : null;
      if (wtm && !window.SensorsStore.isStale(wtm, 6)) qs += '&waterTemp=' + encodeURIComponent(wtm.value);
      fetch('/api/pond-status?' + qs).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      }).then(function (d) {
        if (!d || d.error) throw new Error((d && d.error) || '返回为空');
        renderPondResult(out, d);
      }).catch(function (e) {
        out.className = 'pond-result pond-result--error';
        out.textContent = '计算失败：' + e.message;
      });
    });

    // 养殖日历：渔业不再是独立板块，而是鱼塘地块自带的一段生产计划
    var calendarBox = document.createElement('div');
    calendarBox.className = 'pond-calendar';
    var cTitle = document.createElement('div');
    cTitle.className = 'pond-calendar-head';
    cTitle.textContent = '养殖日历';
    calendarBox.appendChild(cTitle);

    var cRow = document.createElement('div');
    cRow.className = 'pond-actions';
    var dateInput = document.createElement('input');
    dateInput.type = 'date';
    dateInput.id = 'pond-stock-date';
    dateInput.className = 'pond-date';
    dateInput.value = new Date().toISOString().slice(0, 10);
    var cBtn = document.createElement('button');
    cBtn.type = 'button';
    cBtn.className = 'my-plots-3d-view';
    cBtn.textContent = '按放养日生成';
    cRow.appendChild(dateInput);
    cRow.appendChild(cBtn);
    calendarBox.appendChild(cRow);

    var cOut = document.createElement('div');
    cOut.className = 'pond-calendar-list';
    cOut.textContent = '选放养日期后生成：放苗 / 开食 / 投喂 / 换水 / 起捕。';
    calendarBox.appendChild(cOut);

    cBtn.addEventListener('click', function () {
      cOut.textContent = '正在生成…';
      var sd = dateInput.value;
      if (!sd) { cOut.textContent = '请先选放养日期'; return; }
      var url = '/api/industry/timeline?species=' + encodeURIComponent(sel.value)
        + '&stockDate=' + encodeURIComponent(sd)
        + '&lat=' + encodeURIComponent(plot.lat || 35.07)
        + '&lon=' + encodeURIComponent(plot.lon || 116.31);
      fetch(url).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      }).then(function (d) {
        var stages = (d && d.data && d.data.nodes) || [];
        if (!stages.length) { cOut.textContent = '该品种暂无养殖规程'; return; }
        cOut.innerHTML = '';
        stages.forEach(function (st) {
          var row = document.createElement('div');
          row.className = 'pond-stage';
          var day = document.createElement('span');
          day.className = 'pond-stage-day';
          day.textContent = '第 ' + st.dayOffset + ' 天';
          var name = document.createElement('strong');
          name.textContent = st.title || '';
          var date = document.createElement('span');
          date.className = 'pond-stage-date';
          date.textContent = st.date || '';
          row.appendChild(day);
          row.appendChild(name);
          row.appendChild(date);
          if (st.detail) {
            var p = document.createElement('p');
            p.textContent = st.detail;
            row.appendChild(p);
          }
          cOut.appendChild(row);
        });
      }).catch(function (e) {
        cOut.textContent = '生成失败：' + e.message;
      });
    });

    box.appendChild(calendarBox);
    detail.appendChild(box);
  }

  /** 把该地块已有传感器的实测值塞进请求；没有设备时原样返回，不伪造字段。 */
  function withMeasured(payload, plotId) {
    if (!window.SensorsStore) return payload;
    var pid = plotId || (document.getElementById('plot-id') && document.getElementById('plot-id').value) || null;
    if (!pid) return payload;
    var m = window.SensorsStore.measuredParams(pid);
    Object.keys(m).forEach(function (k) {
      if (m[k] != null) payload[k] = m[k];
    });
    return payload;
  }

  function val(id) {
    var el = document.getElementById(id);
    return el ? String(el.value).trim() : '';
  }

  function num(id, def) {
    var v = parseFloat(val(id));
    return isFinite(v) ? v : def;
  }

  function renderPondResult(out, d) {
    out.className = 'pond-result';
    out.innerHTML = '';
    var w = d.water || {}, f = d.feeding || {};

    var head = document.createElement('div');
    head.className = 'pond-head';
    var feed = document.createElement('strong');
    feed.className = 'pond-feed';
    feed.textContent = (f.kgPerDay != null ? f.kgPerDay : '—') + ' kg';
    var feedLabel = document.createElement('span');
    feedLabel.textContent = '今日投喂（存塘约 ' + (d.biomassKg != null ? d.biomassKg : '—') + ' kg，日投喂率 '
      + (f.ratePercent != null ? f.ratePercent : '—') + '%，分 ' + (f.timesPerDay || 2) + ' 次）';
    head.appendChild(feed);
    head.appendChild(feedLabel);
    out.appendChild(head);

    var grid = document.createElement('div');
    grid.className = 'pond-grid';
    function metric(label, value, unit, src) {
      var c = document.createElement('div');
      c.className = 'pond-metric';
      var l = document.createElement('span');
      l.className = 'pond-metric-label';
      l.textContent = label;
      var v = document.createElement('b');
      v.textContent = value == null ? '—' : value + (unit || '');
      c.appendChild(l);
      c.appendChild(v);
      if (src) {
        var s = document.createElement('i');
        s.textContent = src;
        c.appendChild(s);
      }
      grid.appendChild(c);
    }
    metric('水温', w.tempC, '℃', w.tempSource);
    metric('溶氧日均', w.dissolvedOxygen, ' mg/L', w.dissolvedOxygenSource);
    metric('凌晨最低溶氧', w.dissolvedOxygenDawn, ' mg/L', '泛塘高发时段');
    metric('pH', w.ph, '', w.ph != null ? '实测' : '未填');
    metric('氨氮', w.ammonia, ' mg/L', w.ammonia != null ? '实测' : '未填');
    out.appendChild(grid);

    if (w.saturationReason) {
      var sr = document.createElement('div');
      sr.className = 'pond-note';
      sr.textContent = '溶氧推算依据：' + w.saturationReason;
      out.appendChild(sr);
    }
    if (f.basis) {
      var fb = document.createElement('div');
      fb.className = 'pond-note';
      fb.textContent = '投喂依据：' + f.basis;
      out.appendChild(fb);
    }

    (d.risks || []).forEach(function (r) {
      var row = document.createElement('div');
      row.className = 'pond-risk pond-risk--' + (r.severity || 'low');
      row.textContent = (r.severity === 'high' ? '⚠ ' : '') + r.label + '：' + r.detail;
      out.appendChild(row);
    });
    if (d.actions && d.actions.length) {
      var ul = document.createElement('ul');
      ul.className = 'pond-action-list';
      d.actions.forEach(function (a) {
        var li = document.createElement('li');
        li.textContent = a;
        ul.appendChild(li);
      });
      out.appendChild(ul);
    }
    if (d.dataSources && d.dataSources.notice) {
      var n = document.createElement('div');
      n.className = 'pond-note pond-note--muted';
      n.textContent = d.dataSources.notice;
      out.appendChild(n);
    }
  }

  function fetchEnvironment(plot, container, loading) {
    // 地块建模参数一并带上：倾角/坡度/梯田要参与适宜度评分（T06 验收第 5 条）
    var url = '/api/plots/environment?lat=' + plot.lat + '&lon=' + plot.lon +
      '&type=' + (plot.type || 'farmland') +
      '&angle=' + (plot.angle || 0) +
      '&slope=' + (plot.slope || 0) +
      '&terrace=' + (plot.terrace ? 1 : 0);
    fetch(url, { method: 'GET' }).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    }).then(function (data) {
      loading.remove();
      renderEnvironment(container, plot, data);
      // 把这次评估结果快照回记录，列表里能看到分值
      if (data && data.farmingSuitability && window.MyPlotsStore) {
        window.MyPlotsStore.update(plot.recordId || plot.id, {
          suitability: {
            status: data.farmingSuitability.level,
            score: data.farmingSuitability.score,
            reason: data.farmingSuitability.summary
          }
        });
      }
    }).catch(function (err) {
      loading.textContent = '环境数据获取失败：' + (err.message || '网络异常') + '，请稍后重试';
    });
  }

  /** 农事适宜度卡片：总分 + 等级 + 各因子分值条（土壤/水质/气候/地形/类型） */
  function renderSuitability(s) {
    var box = document.createElement('div');
    box.className = 'plot-suit plot-suit--' + levelClass(s.level);

    var head = document.createElement('div');
    head.className = 'plot-suit-head';
    var score = document.createElement('span');
    score.className = 'plot-suit-score';
    score.textContent = s.score;
    var meta = document.createElement('div');
    meta.className = 'plot-suit-meta';
    var level = document.createElement('span');
    level.className = 'plot-suit-level';
    level.textContent = s.level;
    var label = document.createElement('span');
    label.className = 'plot-suit-label';
    label.textContent = '农事适宜度';
    meta.appendChild(level);
    meta.appendChild(label);
    head.appendChild(score);
    head.appendChild(meta);
    box.appendChild(head);

    if (s.summary) {
      var sum = document.createElement('p');
      sum.className = 'plot-suit-summary';
      sum.textContent = s.summary;
      box.appendChild(sum);
    }

    var list = document.createElement('div');
    list.className = 'plot-suit-factors';
    (s.factors || []).forEach(function (f) {
      var row = document.createElement('div');
      row.className = 'plot-suit-factor';

      var k = document.createElement('span');
      k.className = 'k';
      k.textContent = f.label;
      var v = document.createElement('span');
      v.className = 'v';
      v.textContent = f.score;

      var bar = document.createElement('div');
      bar.className = 'plot-suit-bar';
      var fill = document.createElement('i');
      fill.style.width = Math.max(0, Math.min(100, f.score)) + '%';
      bar.appendChild(fill);

      var note = document.createElement('span');
      note.className = 'note';
      note.textContent = f.note + (f.weight ? '（权重 ' + Math.round(f.weight * 100) + '%）' : '');

      row.appendChild(k);
      row.appendChild(v);
      row.appendChild(bar);
      row.appendChild(note);
      list.appendChild(row);
    });
    box.appendChild(list);
    return box;
  }

  function levelClass(level) {
    if (level === '适宜') return 'good';
    if (level === '较适宜') return 'ok';
    if (level === '一般') return 'warn';
    return 'bad';
  }

  /** 河川径流量（GloFAS 水文模型）：今日流量 + 未来 7 天柱状图 + 峰值 */
  function renderHydrology(h) {
    var box = document.createElement('div');
    box.className = 'plot-hyd';

    var title = document.createElement('div');
    title.className = 'my-plots-env-section-title';
    title.textContent = '河川径流量';
    box.appendChild(title);

    var head = document.createElement('div');
    head.className = 'plot-hyd-head';
    var val = document.createElement('span');
    val.className = 'plot-hyd-value';
    val.textContent = Math.round(h.discharge).toLocaleString();
    var unit = document.createElement('span');
    unit.className = 'plot-hyd-unit';
    unit.textContent = 'm³/s';
    var level = document.createElement('span');
    level.className = 'plot-hyd-level';
    level.textContent = h.level;
    var trend = document.createElement('span');
    // 流量只有几个 m³/s 时，翻一倍也没有农事意义，不显示百分比
    var tiny = h.trend === '流量过小';
    trend.className = 'plot-hyd-trend ' + (tiny ? 'flat' : h.changePct > 0 ? 'up' : h.changePct < 0 ? 'down' : 'flat');
    trend.textContent = tiny
      ? '流量过小，趋势不具参考性'
      : h.trend + ' ' + (h.changePct > 0 ? '+' : '') + h.changePct + '%';
    head.appendChild(val);
    head.appendChild(unit);
    head.appendChild(level);
    head.appendChild(trend);
    box.appendChild(head);

    var rows = h.forecast || [];
    if (rows.length) {
      // 径流量往往在均值附近小幅波动（长江一周内 12049~12473），
      // 若按 0 基线归一，七根柱子会完全一样高、看不出涨落；
      // 所以按「区间」归一，并在下方标出真实上下界，避免视觉夸大。
      var lo = Infinity, hi = -Infinity;
      rows.forEach(function (r) {
        if (r.discharge < lo) lo = r.discharge;
        if (r.discharge > hi) hi = r.discharge;
      });
      var base = Math.max(0, lo * 0.9);
      var span = Math.max(hi - base, 0.1);
      var bars = document.createElement('div');
      bars.className = 'plot-hyd-bars';
      rows.forEach(function (r, i) {
        var col = document.createElement('div');
        col.className = 'plot-hyd-col' + (i === 0 ? ' today' : '');
        var bar = document.createElement('i');
        bar.style.height = Math.round(8 + (r.discharge - base) / span * 88) + '%';
        bar.title = r.date + '：' + Math.round(r.discharge) + ' m³/s';
        var lab = document.createElement('span');
        lab.textContent = r.date ? r.date.slice(5) : '';
        col.appendChild(bar);
        col.appendChild(lab);
        bars.appendChild(col);
      });
      box.appendChild(bars);
      var range = document.createElement('div');
      range.className = 'plot-hyd-range';
      range.textContent = '7 日区间 ' + Math.round(lo).toLocaleString() + ' – ' + Math.round(hi).toLocaleString() + ' m³/s';
      box.appendChild(range);
    }

    var meta = document.createElement('div');
    meta.className = 'plot-hyd-meta';
    meta.textContent = '峰值 ' + Math.round(h.peak).toLocaleString() + ' m³/s'
      + (h.peakDate ? '（' + h.peakDate.slice(5) + '）' : '')
      + '　·　采样点距地块 ' + h.sampleDistanceKm + ' km'
      + '　·　GloFAS 水文模型日值，非水文站断面实测';
    box.appendChild(meta);
    return box;
  }

  function renderEnvironment(container, plot, data) {
    // 农事适宜度放最前面：它是土壤/水质/气候/地形/类型综合出来的结论
    if (data.farmingSuitability) {
      container.appendChild(renderSuitability(data.farmingSuitability));
    }

    var grid = document.createElement('div');
    grid.className = 'my-plots-env-grid';
    function envItem(label, value, unit) {
      var item = document.createElement('div');
      item.className = 'my-plots-env-item';
      var l = document.createElement('div'); l.className = 'my-plots-env-label'; l.textContent = label; item.appendChild(l);
      var v = document.createElement('div'); v.className = 'my-plots-env-value';
      v.textContent = (value != null ? value : '--') + (unit ? '<span class="my-plots-env-unit">' + unit + '</span>' : '');
      v.innerHTML = (value != null ? value : '--') + (unit ? '<span class="my-plots-env-unit">' + unit + '</span>' : '');
      item.appendChild(v);
      return item;
    }
    grid.appendChild(envItem('气温', data.temperature, '°C'));
    grid.appendChild(envItem('湿度', data.humidity, '%'));
    grid.appendChild(envItem('风速', data.windSpeed, 'm/s'));
    container.appendChild(grid);

    // 土壤墒情（真实数据：Open-Meteo 土壤模型，逐小时更新）
    if (data.soil && (data.soil.temperature0cm != null || data.soil.moisturePct != null)) {
      var soilEnvTitle = document.createElement('div');
      soilEnvTitle.className = 'my-plots-env-section-title';
      soilEnvTitle.textContent = '土壤墒情（实测）';
      container.appendChild(soilEnvTitle);

      var soilGrid = document.createElement('div');
      soilGrid.className = 'my-plots-env-grid';
      soilGrid.appendChild(envItem('地表温度', data.soil.temperature0cm, '°C'));
      soilGrid.appendChild(envItem('6cm 地温', data.soil.temperature6cm, '°C'));
      soilGrid.appendChild(envItem('表层含水率', data.soil.moisturePct, '%'));
      soilGrid.appendChild(envItem('墒情', data.soil.status, ''));
      container.appendChild(soilGrid);

      var soilSrc = document.createElement('div');
      soilSrc.className = 'my-plots-card-meta';
      soilSrc.textContent = '数据来源：Open-Meteo 土壤模型，逐小时更新';
      container.appendChild(soilSrc);
    }

    if (plot.type === 'fishpond' && data.waterQuality) {
      var wqTitle = document.createElement('div');
      wqTitle.className = 'my-plots-env-section-title'; wqTitle.textContent = '水质数据';
      container.appendChild(wqTitle);
      var wqGrid = document.createElement('div');
      wqGrid.className = 'my-plots-env-grid';
      wqGrid.appendChild(envItem('水质等级', data.waterQuality.grade || '暂无监测', ''));
      wqGrid.appendChild(envItem('pH值', data.waterQuality.ph, ''));
      wqGrid.appendChild(envItem('溶解氧', data.waterQuality.dissolvedOxygen, 'mg/L'));
      wqGrid.appendChild(envItem('浊度', data.waterQuality.turbidity, 'NTU'));
      container.appendChild(wqGrid);
      if (data.waterQuality.estimated) {
        var wqNote = document.createElement('div');
        wqNote.className = 'my-plots-card-meta';
        wqNote.textContent = '水质等级为邻近水体公开等级参考值（非断面实时监测）；'
          + 'pH／溶解氧／浊度为模型估算值，非实测数据';
        container.appendChild(wqNote);
      }
    }

    var soilTitle = document.createElement('div');
    soilTitle.className = 'my-plots-env-section-title'; soilTitle.textContent = '土壤信息';
    container.appendChild(soilTitle);
    var soilMeta = document.createElement('div');
    soilMeta.className = 'my-plots-card-meta';
    soilMeta.textContent = '土壤类型：' + (data.soilType || '未知') +
      (data.soilDescription ? '（' + data.soilDescription + '）' : '') +
      '　·　按经纬度区域规则推断，非实地采样';
    container.appendChild(soilMeta);

    if (data.nearbyRivers && data.nearbyRivers.length) {
      var riverTitle = document.createElement('div');
      riverTitle.className = 'my-plots-env-section-title'; riverTitle.textContent = '周边河流';
      container.appendChild(riverTitle);
      var riverDiv = document.createElement('div');
      riverDiv.className = 'my-plots-env-river';
      riverDiv.innerHTML = data.nearbyRivers.map(function (r) {
        return r.name + '：约 ' + r.distance + ' km' + (r.waterGrade ? '（水质' + r.waterGrade + '）' : '');
      }).join('<br>');
      container.appendChild(riverDiv);
    }

    // 河川径流量：GloFAS 水文模型的逐日河道流量，与上面"周边河流"的静态距离互补
    if (data.hydrology) {
      container.appendChild(renderHydrology(data.hydrology));
    }

    if (data.factors && data.factors.length) {
      var factorTitle = document.createElement('div');
      factorTitle.className = 'my-plots-env-section-title'; factorTitle.textContent = '农事适宜度因子';
      container.appendChild(factorTitle);
      var factorDiv = document.createElement('div');
      factorDiv.className = 'my-plots-env-factors';
      data.factors.forEach(function (f) {
        var span = document.createElement('span');
        span.className = 'my-plots-env-factor'; span.textContent = f;
        factorDiv.appendChild(span);
      });
      container.appendChild(factorDiv);
    }
  }

  if (typeof window !== 'undefined') {
    window.MyPlots = { init: init, renderList: renderList, PLOT_TYPES: PLOT_TYPES, SHAPE_TYPES: SHAPE_TYPES };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();