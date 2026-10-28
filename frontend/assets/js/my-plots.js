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
  }

  function bindLocationButtons() {
    var autoBtn = document.getElementById('plot-loc-auto');
    var mapBtn = document.getElementById('plot-loc-map');
    if (autoBtn) autoBtn.addEventListener('click', autoLocate);
    if (mapBtn) mapBtn.addEventListener('click', mapPick);
  }

  function autoLocate() {
    if (!window.LocationPicker || !window.LocationPicker.getCurrentLocation) return;
    var btn = document.getElementById('plot-loc-auto');
    if (btn) btn.textContent = '定位中…';
    window.LocationPicker.getCurrentLocation(function (loc) {
      if (btn) btn.textContent = '📍 自动定位';
      if (!loc || !loc.lat) { alert('无法获取定位，请检查权限或使用地图选点'); return; }
      setPlotLocation(loc.lat, loc.lon, loc.name || (loc.lat.toFixed(4) + ', ' + loc.lon.toFixed(4)));
    });
  }

  function mapPick() {
    if (!window.LocationPicker || !window.LocationPicker.open) { alert('地图组件未加载'); return; }
    window.LocationPicker.open(function (res) {
      if (res && res.lat) setPlotLocation(res.lat, res.lon, res.name || (res.lat.toFixed(4) + ', ' + res.lon.toFixed(4)));
    });
  }

  function setPlotLocation(lat, lon, name) {
    var latInput = document.getElementById('plot-lat');
    var lonInput = document.getElementById('plot-lon');
    var locInput = document.getElementById('plot-location');
    if (latInput) latInput.value = lat;
    if (lonInput) lonInput.value = lon;
    if (locInput) locInput.value = name;
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
    locSpan.textContent = '📍 ' + (p.location || (p.lat && p.lon ? p.lat.toFixed(3) + ',' + p.lon.toFixed(3) : '未定位'));
    meta.appendChild(locSpan);
    if (p.area) { var a = document.createElement('span'); a.textContent = '面积：' + p.area + '亩'; meta.appendChild(a); }
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
    var data = {
      name: name,
      type: f['plot-type'].value || 'farmland',
      location: f['plot-location'].value.trim() || null,
      lat: lat, lon: lon,
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
      mapDiv.style.cssText = 'height:180px;border-radius:12px;overflow:hidden;margin-bottom:16px;border:1px solid var(--line);';
      detail.appendChild(mapDiv);
      setTimeout(function () { window.LocationPicker.initDisplay('plot-detail-map', plot.lat, plot.lon); }, 50);
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
    var url = '/api/plots/environment?lat=' + plot.lat + '&lon=' + plot.lon + '&type=' + (plot.type || 'farmland');
    fetch(url, { method: 'GET' }).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    }).then(function (data) {
      loading.remove();
      renderEnvironment(container, plot, data);
    }).catch(function (err) {
      loading.textContent = '环境数据获取失败：' + (err.message || '网络异常') + '，请稍后重试';
    });
  }

  function renderEnvironment(container, plot, data) {
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

    if (plot.type === 'fishpond' && data.waterQuality) {
      var wqTitle = document.createElement('div');
      wqTitle.className = 'my-plots-env-section-title'; wqTitle.textContent = '水质数据';
      container.appendChild(wqTitle);
      var wqGrid = document.createElement('div');
      wqGrid.className = 'my-plots-env-grid';
      wqGrid.appendChild(envItem('水质等级', data.waterQuality.grade, ''));
      wqGrid.appendChild(envItem('pH值', data.waterQuality.ph, ''));
      wqGrid.appendChild(envItem('溶解氧', data.waterQuality.dissolvedOxygen, 'mg/L'));
      wqGrid.appendChild(envItem('浊度', data.waterQuality.turbidity, 'NTU'));
      container.appendChild(wqGrid);
    }

    var soilTitle = document.createElement('div');
    soilTitle.className = 'my-plots-env-section-title'; soilTitle.textContent = '土壤信息';
    container.appendChild(soilTitle);
    var soilMeta = document.createElement('div');
    soilMeta.className = 'my-plots-card-meta';
    soilMeta.textContent = '土壤类型：' + (data.soilType || '未知') + (data.soilDescription ? '（' + data.soilDescription + '）' : '');
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