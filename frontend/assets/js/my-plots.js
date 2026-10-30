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
    if (autoBtn) autoBtn.addEventListener('click', autoLocate);
    if (mapBtn) mapBtn.addEventListener('click', mapPick);
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
    }, { draw: true, mode: 'draw' });
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

    if (res.mode === 'polygon' && Array.isArray(res.polygon) && res.polygon.length >= 3) {
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

    var data = {
      name: name,
      type: f['plot-type'].value || 'farmland',
      location: f['plot-location'].value.trim() || null,
      lat: lat, lon: lon,
      polygon: polygon,
      suitability: suitability,
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
      mapDiv.style.cssText = 'height:' + (hasPolygon ? 240 : 180) + 'px;border-radius:12px;overflow:hidden;margin-bottom:16px;border:1px solid var(--line);';
      detail.appendChild(mapDiv);
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
    shapeTitle.textContent = '地块建模';
    shapeSection.appendChild(shapeTitle);
    var shapeMeta = document.createElement('div');
    shapeMeta.className = 'my-plots-card-meta';
    var shapeParts = [];
    shapeParts.push('形状：' + (SHAPE_TYPES[plot.shape] || plot.shape || '未指定'));
    if (plot.angle) shapeParts.push('倾角：' + plot.angle + '°');
    if (plot.slope) shapeParts.push('坡度：' + plot.slope + '°');
    if (plot.terrace) shapeParts.push('梯田');
    shapeMeta.textContent = shapeParts.join(' · ');
    shapeSection.appendChild(shapeMeta);
    detail.appendChild(shapeSection);

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
    trend.className = 'plot-hyd-trend ' + (h.changePct > 0 ? 'up' : h.changePct < 0 ? 'down' : 'flat');
    trend.textContent = h.trend + ' ' + (h.changePct > 0 ? '+' : '') + h.changePct + '%';
    head.appendChild(val);
    head.appendChild(unit);
    head.appendChild(level);
    head.appendChild(trend);
    box.appendChild(head);

    var rows = h.forecast || [];
    if (rows.length) {
      var max = 1;
      rows.forEach(function (r) { if (r.discharge > max) max = r.discharge; });
      var bars = document.createElement('div');
      bars.className = 'plot-hyd-bars';
      rows.forEach(function (r, i) {
        var col = document.createElement('div');
        col.className = 'plot-hyd-col' + (i === 0 ? ' today' : '');
        var bar = document.createElement('i');
        // 按峰值归一，最矮留 4%，否则小流量日会完全看不见
        bar.style.height = Math.max(4, Math.round(r.discharge / max * 100)) + '%';
        bar.title = r.date + '：' + Math.round(r.discharge) + ' m³/s';
        var lab = document.createElement('span');
        lab.textContent = r.date ? r.date.slice(5) : '';
        col.appendChild(bar);
        col.appendChild(lab);
        bars.appendChild(col);
      });
      box.appendChild(bars);
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