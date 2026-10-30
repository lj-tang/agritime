/**
 * 地图标点 / 框地组件（V4 T06）
 * Leaflet + OpenStreetMap，动态加载，无 API Key。
 * 功能：
 *   · 标点模式：地图选点 + 反向地理编码 + 文字搜索 + 降级
 *   · 画地块模式：在地图上自行框地（多边形），实时面积，确认时做可种植性校验
 *     —— 城市建成区/核心区（商务区、商业区、住宅区等）会被拦截；
 *        城市绿地（公园/花园等）会提示需确认权限；农田/水体直接放行。
 */
(function () {
  var LEAFLET_CSS = '../assets/leaflet/leaflet.css';
  var LEAFLET_JS = '../assets/leaflet/leaflet.js';
  var OSM_TILE = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}';
  var GEO_API = '/api/geo/reverse';
  var GEO_SEARCH = '/api/geo/search';
  var GEO_SUITABILITY = '/api/geo/suitability';
  var LOAD_TIMEOUT = 5000;
  var DEFAULT_CENTER = [39.9042, 116.4074];
  var DEFAULT_ZOOM = 13;
  var M_PER_MU = 666.6667;          // 1 亩 ≈ 666.67 平方米

  var leafletReady = null;
  var mapInstance = null;
  var currentMarker = null;
  var pendingCallback = null;
  var displayMap = null;
  var currentPlace = null;   // 当前标点解析出的中文地名，确认时回填
  var resolving = false;     // 反向地理编码进行中
  var geoToken = 0;          // 丢弃过期的识别结果

  // ---- 画地块状态 ----
  var mode = 'point';        // 'point' | 'draw'
  var drawEnabled = false;   // 本次打开是否允许画地块（我的地块传入 opts.draw=true）
  var vertices = [];         // [{lat,lng}]
  var drawGroup = null;      // 多边形 + 顶点图层
  var drawPolygonLayer = null;
  var drawPolylineLayer = null;
  var drawClosed = false;
  var drawOverride = false;  // 被判为不适合后，用户二次确认「仍要保存」
  var drawSuitability = null;
  var confirmBusy = false;

  /**
   * 标记图标不走 L.Icon.Default：它依赖 leaflet/images/*.png，而仓库只随包了
   * leaflet.css / leaflet.js，那几张图不存在，每个标记都会 404 且地图上没有图标。
   * 这里用 L.divIcon + 内联 SVG —— html 直接渲染，不经过 imagePath 拼接，
   * 因此既不会 404，也不会像 data-URI 方案那样被拼成
   * /assets/leaflet/images/data:image/svg+xml... 的畸形地址。
   */
  var MARKER_SVG = "<svg xmlns='http://www.w3.org/2000/svg' width='28' height='40' viewBox='0 0 28 40'>" +
    "<path d='M14 1C7.4 1 2 6.3 2 12.9 2 21.6 14 39 14 39s12-17.4 12-26.1C26 6.3 20.6 1 14 1z' " +
    "fill='#4a7c59' stroke='#ffffff' stroke-width='2'/><circle cx='14' cy='13' r='4.5' fill='#ffffff'/></svg>";
  var markerIcon = null;

  function getMarkerIcon() {
    if (markerIcon) return markerIcon;
    if (typeof L === 'undefined' || !L.divIcon) return null;
    markerIcon = L.divIcon({
      className: 'agritime-marker',
      html: MARKER_SVG,
      iconSize: [28, 40],
      iconAnchor: [14, 40],
      popupAnchor: [0, -40]
    });
    return markerIcon;
  }

  function getVertexIcon(isFirst) {
    return L.divIcon({
      className: 'agritime-vertex',
      html: '<span class="agritime-vertex-dot' + (isFirst ? ' is-first' : '') + '"></span>',
      iconSize: [14, 14],
      iconAnchor: [7, 7]
    });
  }

  function loadLeaflet() {
    if (leafletReady) return leafletReady;
    leafletReady = new Promise(function (resolve) {
      if (typeof L !== 'undefined') { resolve(true); return; }
      var link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = LEAFLET_CSS;
      document.head.appendChild(link);
      var script = document.createElement('script');
      script.src = LEAFLET_JS;
      script.onload = function () { resolve(true); };
      script.onerror = function () { resolve(false); };
      document.head.appendChild(script);
      setTimeout(function () { resolve(typeof L !== 'undefined'); }, LOAD_TIMEOUT);
    });
    return leafletReady;
  }

  function isReady() { return typeof L !== 'undefined'; }

  function validateLatLon(lat, lon) {
    var la = Number(lat), lo = Number(lon);
    if (isNaN(la) || isNaN(lo)) return false;
    if (la < -90 || la > 90) return false;
    if (lo < -180 || lo > 180) return false;
    return true;
  }

  /**
   * 逆地理编码走后端 /api/geo/reverse，不再直连 Nominatim：
   * 国内访问 nominatim.openstreetmap.org 基本是超时，用户只能看到一串经纬度；
   * 后端接的是国内服务商，能返回到小区/村级的中文地名，key 也不会落到浏览器里。
   */
  function reverseGeocode(lat, lon, cb) {
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 6000);
    var url = GEO_API + '?lat=' + encodeURIComponent(lat) + '&lon=' + encodeURIComponent(lon);
    fetch(url, { signal: controller ? controller.signal : undefined })
      .then(function (r) { clearTimeout(timer); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(function (res) {
        var d = res && res.data;
        cb(d && d.displayName ? d : null);
      })
      .catch(function () { clearTimeout(timer); cb(null); });
  }

  /** 地点搜索同样走后端，Nominatim 在国内不可达 */
  function searchGeocode(query, cb) {
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 8000);
    var center = mapInstance ? mapInstance.getCenter() : null;
    var url = GEO_SEARCH + '?q=' + encodeURIComponent(query)
      + (center ? '&lat=' + center.lat.toFixed(6) + '&lon=' + center.lng.toFixed(6) : '');
    fetch(url, { signal: controller ? controller.signal : undefined })
      .then(function (r) { clearTimeout(timer); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(function (res) {
        var items = res && res.data && res.data.items;
        cb(Array.isArray(items) ? items : []);
      })
      .catch(function () { clearTimeout(timer); cb(null); });
  }

  function getCurrentLocation() {
    return new Promise(function (resolve) {
      if (window.__lastLocation && validateLatLon(window.__lastLocation.lat, window.__lastLocation.lon)) {
        resolve({ lat: window.__lastLocation.lat, lon: window.__lastLocation.lon });
        return;
      }
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          function (pos) { resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude }); },
          function () { getLocationByIP().then(resolve); },
          { timeout: 5000, maximumAge: 600000 }
        );
      } else {
        getLocationByIP().then(resolve);
      }
    });
  }

  function getLocationByIP() {
    return new Promise(function (resolve) {
      var controller = new AbortController();
      var timer = setTimeout(function () { controller.abort(); }, 5000);
      fetch('https://ipinfo.io/json', { signal: controller.signal })
        .then(function (r) { clearTimeout(timer); return r.json(); })
        .then(function (data) {
          if (data && data.loc) {
            var parts = data.loc.split(',');
            resolve({ lat: parseFloat(parts[0]), lon: parseFloat(parts[1]) });
          } else { resolve(null); }
        })
        .catch(function () { clearTimeout(timer); resolve(null); });
    });
  }

  // ---------------- 面板 ----------------

  function buildOverlay() {
    var overlay = document.createElement('div');
    overlay.id = 'map-overlay';
    overlay.className = 'map-overlay';
    overlay.innerHTML =
      '<div class="map-panel">' +
      '<div class="map-header"><span id="map-title">地图标点</span><button type="button" class="map-close" id="map-close">×</button></div>' +
      '<div class="map-mode-switch" id="map-mode-switch" hidden>' +
      '<button type="button" class="map-mode-btn active" id="map-mode-point">📍 标点</button>' +
      '<button type="button" class="map-mode-btn" id="map-mode-draw">✏️ 画地块</button>' +
      '</div>' +
      '<div class="map-search-area"><input type="text" id="map-search-input" placeholder="搜索地点…" /><button type="button" id="map-search-btn">搜索</button><button type="button" id="map-locate-btn" title="定位到当前位置">📍</button></div>' +
      '<div class="map-search-results" id="map-search-results"></div>' +
      '<div class="map-draw-tools" id="map-draw-tools" hidden>' +
      '<div class="map-draw-hint" id="map-draw-hint">点击地图添加顶点，至少 3 个点；双击或点「完成绘制」闭合</div>' +
      '<div class="map-draw-row">' +
      '<span class="map-draw-area" id="map-draw-area">已选 0 个顶点</span>' +
      '<span class="map-draw-btns">' +
      '<button type="button" id="map-draw-undo">撤销点</button>' +
      '<button type="button" id="map-draw-clear">清除</button>' +
      '<button type="button" id="map-draw-finish">完成绘制</button>' +
      '</span></div></div>' +
      '<div class="map-canvas" id="map-canvas"></div>' +
      '<div class="map-info" id="map-info">点击地图选择位置</div>' +
      '<div class="map-verdict" id="map-verdict" hidden></div>' +
      '<div class="map-actions"><button type="button" class="btn btn-primary" id="map-confirm">确认</button><button type="button" class="btn btn-ghost" id="map-cancel">取消</button></div>' +
      '</div>';
    document.body.appendChild(overlay);

    document.getElementById('map-close').addEventListener('click', close);
    document.getElementById('map-cancel').addEventListener('click', close);
    document.getElementById('map-confirm').addEventListener('click', confirmSelect);
    document.getElementById('map-search-btn').addEventListener('click', doSearch);
    document.getElementById('map-locate-btn').addEventListener('click', locateToCurrent);
    document.getElementById('map-search-input').addEventListener('keydown', function (e) { if (e.key === 'Enter') doSearch(); });
    document.getElementById('map-mode-point').addEventListener('click', enterPointMode);
    document.getElementById('map-mode-draw').addEventListener('click', function () { enterDrawMode(); });
    document.getElementById('map-draw-undo').addEventListener('click', undoVertex);
    document.getElementById('map-draw-clear').addEventListener('click', clearDraw);
    document.getElementById('map-draw-finish').addEventListener('click', finishDraw);
    return overlay;
  }

  function open(onConfirm, opts) {
    pendingCallback = onConfirm;
    drawEnabled = !!(opts && opts.draw);
    mode = drawEnabled ? (opts.mode === 'point' ? 'point' : 'draw') : 'point';

    loadLeaflet().then(function (ok) {
      if (!ok) { degrade(); return; }
      var overlay = document.getElementById('map-overlay') || buildOverlay();
      overlay.style.display = 'flex';

      geoToken++;            // 作废上一次打开时还没回来的识别结果
      currentPlace = null;
      resolving = false;
      confirmBusy = false;
      drawSuitability = null;
      setConfirmBusy(false);
      resetDrawState();
      updateModeUI();
      resetVerdict();

      getCurrentLocation().then(function (loc) {
        var center = (loc && validateLatLon(loc.lat, loc.lon)) ? [loc.lat, loc.lon] : DEFAULT_CENTER;
        setTimeout(function () {
          if (!mapInstance) {
            var canvas = document.getElementById('map-canvas');
            if (canvas && canvas.offsetWidth > 0) {
              mapInstance = L.map('map-canvas').setView(center, DEFAULT_ZOOM);
              L.tileLayer(OSM_TILE, { subdomains: ['1','2','3','4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(mapInstance);
              mapInstance.on('click', onMapClick);
              mapInstance.on('dblclick', onMapDblClick);
            }
          } else {
            mapInstance.setView(center, DEFAULT_ZOOM);
            if (currentMarker) { mapInstance.removeLayer(currentMarker); currentMarker = null; }
          }
          if (mapInstance) {
            mapInstance.doubleClickZoom.enable();
            if (mode === 'draw') {
              enterDrawMode();
            } else if (loc && validateLatLon(loc.lat, loc.lon)) {
              placeMarker(loc.lat, loc.lon);
            }
            mapInstance.invalidateSize();
          }
        }, 200);
      });
    });
  }

  function onMapClick(e) {
    if (!mapInstance) return;
    if (mode !== 'draw') { placeMarker(e.latlng.lat, e.latlng.lng); return; }
    if (drawClosed) return;
    // 点回第一个顶点视为闭合
    if (vertices.length >= 3) {
      var first = mapInstance.latLngToContainerPoint([vertices[0].lat, vertices[0].lng]);
      var here = mapInstance.latLngToContainerPoint(e.latlng);
      if (first.distanceTo(here) < 14) { finishDraw(); return; }
    }
    vertices.push({ lat: e.latlng.lat, lng: e.latlng.lng });
    drawOverride = false;
    redrawDraw();
  }

  function onMapDblClick() {
    if (mode !== 'draw') return;
    finishDraw();
  }

  // ---------------- 标点模式 ----------------

  function placeMarker(lat, lon) {
    if (!mapInstance || !validateLatLon(lat, lon)) return;
    if (currentMarker) mapInstance.removeLayer(currentMarker);
    currentMarker = L.marker([lat, lon], { icon: getMarkerIcon(), draggable: true }).addTo(mapInstance);
    currentMarker.on('dragend', function (e) {
      var pos = e.target.getLatLng();
      updateInfo(pos.lat, pos.lng);
    });
    updateInfo(lat, lon);
  }

  function updateInfo(lat, lon) {
    var info = document.getElementById('map-info');
    currentPlace = null;
    resolving = true;
    setConfirmBusy(true);
    if (info) info.textContent = '正在识别位置…';

    var token = ++geoToken;
    reverseGeocode(lat, lon, function (place) {
      if (token !== geoToken) return;   // 拖拽过快时丢弃过期结果
      resolving = false;
      currentPlace = place;
      var el = document.getElementById('map-info');
      if (place) {
        if (el) el.textContent = place.displayName;
      } else if (el) {
        el.textContent = '该位置暂无法识别地名，可直接确认后手动填写地点';
      }
      setConfirmBusy(false);
    });
  }

  /** 识别期间禁用确认，保证回填的一定是地名而不是「地图选点位置」 */
  function setConfirmBusy(busy, label) {
    confirmBusy = !!busy;
    var btn = document.getElementById('map-confirm');
    if (!btn) return;
    btn.disabled = !!busy;
    btn.textContent = busy ? (label || '识别位置…') : '确认';
  }

  // ---------------- 画地块模式 ----------------

  function resetDrawState() {
    vertices = [];
    drawClosed = false;
    drawOverride = false;
    drawPolygonLayer = null;
    drawPolylineLayer = null;
    if (drawGroup) drawGroup.clearLayers();
  }

  function enterDrawMode() {
    if (!drawEnabled) return;
    mode = 'draw';
    if (mapInstance) mapInstance.doubleClickZoom.disable();
    if (currentMarker && mapInstance) { mapInstance.removeLayer(currentMarker); currentMarker = null; }
    if (!drawGroup && mapInstance) drawGroup = L.layerGroup().addTo(mapInstance);
    resetDrawState();
    updateModeUI();
    updateDrawInfo();
    resetVerdict();
    setInfo('点击地图添加顶点，至少 3 个点');
    setConfirmBusy(false);
  }

  function enterPointMode() {
    mode = 'point';
    if (mapInstance) mapInstance.doubleClickZoom.enable();
    resetDrawState();
    updateModeUI();
    resetVerdict();
    setInfo('点击地图选择位置');
    setConfirmBusy(false);
    if (mapInstance) {
      var c = mapInstance.getCenter();
      placeMarker(c.lat, c.lng);
    }
  }

  function updateModeUI() {
    var sw = document.getElementById('map-mode-switch');
    if (sw) sw.hidden = !drawEnabled;
    var tools = document.getElementById('map-draw-tools');
    if (tools) tools.hidden = !(drawEnabled && mode === 'draw');
    var title = document.getElementById('map-title');
    if (title) title.textContent = (mode === 'draw' && drawEnabled) ? '框选地块' : '地图标点';
    var pb = document.getElementById('map-mode-point');
    var db = document.getElementById('map-mode-draw');
    if (pb) pb.classList.toggle('active', mode === 'point');
    if (db) db.classList.toggle('active', mode === 'draw');
  }

  function undoVertex() {
    if (drawClosed) return;
    if (!vertices.length) return;
    vertices.pop();
    drawOverride = false;
    redrawDraw();
  }

  function clearDraw() {
    resetDrawState();
    updateDrawInfo();
    resetVerdict();
    setInfo('已清除，点击地图重新框选');
    setConfirmBusy(false);
  }

  function finishDraw() {
    if (vertices.length < 3) { setInfo('至少需要 3 个顶点才能围成地块'); return; }
    dedupeVertices();
    drawClosed = true;
    redrawDraw();
    var hint = document.getElementById('map-draw-hint');
    if (hint) hint.textContent = '已闭合，可拖拽顶点微调；点「撤销点」或「清除」重新绘制';
    setInfo('地块已闭合，点「确认」保存');
  }

  function dedupeVertices() {
    var out = [];
    for (var i = 0; i < vertices.length; i++) {
      var v = vertices[i];
      var prev = out[out.length - 1];
      if (prev && Math.abs(prev.lat - v.lat) < 1e-7 && Math.abs(prev.lng - v.lng) < 1e-7) continue;
      out.push(v);
    }
    vertices = out;
  }

  function redrawDraw() {
    if (!mapInstance || !drawGroup) return;
    drawGroup.clearLayers();
    drawPolygonLayer = null;
    drawPolylineLayer = null;

    var latlngs = vertices.map(function (v) { return [v.lat, v.lng]; });
    if (latlngs.length >= 3) {
      drawPolygonLayer = L.polygon(latlngs, {
        color: '#3f8f56', weight: 2, fillColor: '#3f8f56', fillOpacity: 0.18
      }).addTo(drawGroup);
    } else if (latlngs.length === 2) {
      drawPolylineLayer = L.polyline(latlngs, {
        color: '#3f8f56', weight: 2, dashArray: '6,6'
      }).addTo(drawGroup);
    }

    vertices.forEach(function (v, i) {
      var m = L.marker([v.lat, v.lng], {
        draggable: true, icon: getVertexIcon(i === 0), zIndexOffset: 1000
      }).addTo(drawGroup);
      m.on('drag', function (e) {
        var p = e.target.getLatLng();
        vertices[i] = { lat: p.lat, lng: p.lng };
        updateDrawGeometry();
        updateDrawInfo();
      });
      m.on('dragend', function (e) {
        var p = e.target.getLatLng();
        vertices[i] = { lat: p.lat, lng: p.lng };
        drawOverride = false;
        redrawDraw();
      });
    });

    updateDrawInfo();
  }

  /** 拖拽顶点时只更新几何，不重建标记（重建会中断拖拽） */
  function updateDrawGeometry() {
    var latlngs = vertices.map(function (v) { return [v.lat, v.lng]; });
    if (drawPolygonLayer && latlngs.length >= 3) drawPolygonLayer.setLatLngs(latlngs);
    if (drawPolylineLayer && latlngs.length === 2) drawPolylineLayer.setLatLngs(latlngs);
  }

  function updateDrawInfo() {
    var areaEl = document.getElementById('map-draw-area');
    var hint = document.getElementById('map-draw-hint');
    if (areaEl) {
      if (vertices.length >= 3) {
        var sqm = polygonAreaSqm(vertices);
        areaEl.textContent = '面积 ' + formatArea(sqm) + '（' + (sqm / M_PER_MU).toFixed(1) + ' 亩）';
      } else {
        areaEl.textContent = '已选 ' + vertices.length + ' 个顶点（至少 3 个）';
      }
    }
    if (hint && !drawClosed) {
      hint.textContent = '点击地图添加顶点，至少 3 个点；双击或点「完成绘制」闭合';
    }
  }

  function formatArea(sqm) {
    if (sqm >= 1000000) return (sqm / 1000000).toFixed(2) + ' km²';
    if (sqm >= 10000) return (sqm / 10000).toFixed(2) + ' 公顷';
    return Math.round(sqm).toLocaleString('zh-CN') + ' ㎡';
  }

  /** 球面多边形面积（平方米），与后端同一公式 */
  function polygonAreaSqm(latlngs) {
    var n = latlngs.length;
    if (n < 3) return 0;
    var R = 6378137;
    var area = 0;
    for (var i = 0; i < n; i++) {
      var p1 = latlngs[i], p2 = latlngs[(i + 1) % n];
      var lat1 = p1.lat * Math.PI / 180, lon1 = p1.lng * Math.PI / 180;
      var lat2 = p2.lat * Math.PI / 180, lon2 = p2.lng * Math.PI / 180;
      area += (lon2 - lon1) * (2 + Math.sin(lat1) + Math.sin(lat2));
    }
    return Math.abs(area * R * R / 2);
  }

  function polygonCentroid(latlngs) {
    var la = 0, lo = 0;
    for (var i = 0; i < latlngs.length; i++) { la += latlngs[i].lat; lo += latlngs[i].lng; }
    return { lat: la / latlngs.length, lon: lo / latlngs.length };
  }

  function setInfo(text) {
    var el = document.getElementById('map-info');
    if (el) el.textContent = text;
  }

  function resetVerdict() {
    var el = document.getElementById('map-verdict');
    if (el) { el.hidden = true; el.textContent = ''; el.className = 'map-verdict'; }
    drawSuitability = null;
    drawOverride = false;
  }

  function renderVerdict(suit) {
    var el = document.getElementById('map-verdict');
    if (!el) return;
    el.hidden = false;
    var st = suit.status || 'unknown';
    el.className = 'map-verdict map-verdict-' + st;
    var icon = st === 'unsuitable' ? '⛔' : st === 'caution' ? '⚠️' : st === 'suitable' ? '✅' : 'ℹ️';
    el.textContent = icon + ' ' + (suit.reason || '');
  }

  /** 把多边形交给后端做可种植性判定；服务不可用时降级为 unknown（放行但提示自行确认） */
  function postSuitability(ring, cb) {
    var sqm = polygonAreaSqm(vertices);
    var fallback = {
      status: 'unknown',
      reason: '未能连接土地校验服务，请自行确认地块性质与用地规定。',
      areaSqm: Math.round(sqm),
      areaMu: Math.round(sqm / M_PER_MU * 10) / 10
    };
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 20000);
    fetch(GEO_SUITABILITY, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ polygon: ring }),
      signal: controller ? controller.signal : undefined
    })
      .then(function (r) { clearTimeout(timer); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(function (res) {
        var d = res && res.data;
        if (!d) { cb(fallback); return; }
        cb({
          status: d.status || 'unknown',
          reason: d.reason || '',
          areaSqm: Number(d.areaSqm) || fallback.areaSqm,
          areaMu: Number(d.areaMu) || fallback.areaMu,
          samples: d.samples || []
        });
      })
      .catch(function () { clearTimeout(timer); cb(fallback); });
  }

  function confirmDraw() {
    if (confirmBusy) return;
    if (vertices.length < 3) { setInfo('请至少绘制 3 个顶点围成地块'); return; }
    dedupeVertices();
    var ring = vertices.map(function (v) { return [round6(v.lat), round6(v.lng)]; });
    var center = polygonCentroid(vertices);

    setConfirmBusy(true, '校验中…');
    setInfo('正在识别地块位置并校验土地性质…');

    var token = ++geoToken;
    reverseGeocode(center.lat, center.lon, function (place) {
      if (token !== geoToken) return;
      postSuitability(ring, function (suit) {
        drawSuitability = suit;
        renderVerdict(suit);

        var areaSqm = suit.areaSqm || polygonAreaSqm(vertices);
        var areaMu = suit.areaMu || (areaSqm / M_PER_MU);
        setInfo('面积 ' + formatArea(areaSqm) + '（' + Number(areaMu).toFixed(1) + ' 亩）');

        if (suit.status === 'unsuitable' && !drawOverride) {
          // 第一次判为不适合：拦截，并给出二次确认入口
          drawOverride = true;
          setConfirmBusy(false);
          var btn = document.getElementById('map-confirm');
          if (btn) btn.textContent = '仍要保存（不推荐）';
          return;
        }

        var result = {
          mode: 'polygon',
          polygon: ring,
          center: { lat: round6(center.lat), lon: round6(center.lon) },
          areaSqm: Math.round(areaSqm),
          areaMu: Math.round(areaMu * 10) / 10,
          name: (place && place.displayName) ? place.displayName : '地图框选地块',
          place: place ? {
            displayName: place.displayName,
            name: place.name,
            district: place.district,
            city: place.city,
            level: place.level
          } : null,
          suitability: suit
        };

        setConfirmBusy(false);
        var cb = pendingCallback;
        pendingCallback = null;
        if (cb) cb(result);
        close();
      });
    });
  }

  function confirmSelect() {
    if (mode === 'draw') { confirmDraw(); return; }
    if (!currentMarker) { close(); return; }
    if (resolving) return;   // 按钮此时是禁用的，这里再兜一层

    var pos = currentMarker.getLatLng();
    var name = selectionName();

    // 经纬度保留 6 位小数（约 0.1 米），只交给后端参与计算，界面不展示
    window.__markerLatLon = { lat: round6(pos.lat), lon: round6(pos.lng) };
    window.__markerPlace = currentPlace ? {
      displayName: currentPlace.displayName,
      name: currentPlace.name,
      district: currentPlace.district,
      city: currentPlace.city,
      level: currentPlace.level
    } : null;

    var loc = document.getElementById('location');
    if (loc) loc.value = name;

    var cb = pendingCallback;
    pendingCallback = null;
    if (cb) {
      cb({
        lat: window.__markerLatLon.lat,
        lon: window.__markerLatLon.lon,
        name: name,
        place: window.__markerPlace
      });
    }
    close();
  }

  /** 回填到地点输入框的文本：优先用小区级地名，识别不出来时才用占位文案 */
  function selectionName() {
    if (currentPlace && currentPlace.displayName) return currentPlace.displayName;
    return '地图选点';
  }

  function round6(v) {
    return Math.round(v * 1e6) / 1e6;
  }

  function locateToCurrent() {
    var info = document.getElementById('map-info');
    if (info) info.textContent = '正在定位…';
    getCurrentLocation().then(function (loc) {
      if (loc && validateLatLon(loc.lat, loc.lon) && mapInstance) {
        mapInstance.setView([loc.lat, loc.lon], DEFAULT_ZOOM);
        if (mode !== 'draw') placeMarker(loc.lat, loc.lon);
      } else if (info) {
        info.textContent = '定位失败，请手动点击地图选择位置';
      }
    });
  }

  function doSearch() {
    var input = document.getElementById('map-search-input');
    if (!input || !input.value.trim()) return;
    var results = document.getElementById('map-search-results');
    if (results) results.innerHTML = '<div class="map-searching">搜索中…</div>';
    searchGeocode(input.value.trim(), function (items) {
      if (!results) return;
      if (items === null) {
        results.innerHTML = '<div class="map-no-result">搜索服务暂不可用，请直接在地图上点击选择位置</div>';
        return;
      }
      results.innerHTML = '';
      if (!items.length) { results.innerHTML = '<div class="map-no-result">无结果</div>'; return; }
      items.forEach(function (item) {
        var div = document.createElement('div');
        div.className = 'map-search-item';
        var title = document.createElement('div');
        title.className = 'map-search-title';
        title.textContent = item.title || '未知';
        div.appendChild(title);
        if (item.address) {
          var addr = document.createElement('div');
          addr.className = 'map-search-addr';
          addr.textContent = item.address;
          div.appendChild(addr);
        }
        div.addEventListener('click', function () {
          if (mapInstance && item.lat && item.lon) {
            mapInstance.setView([Number(item.lat), Number(item.lon)], DEFAULT_ZOOM);
            if (mode !== 'draw') placeMarker(Number(item.lat), Number(item.lon));
          }
          results.innerHTML = '';
        });
        results.appendChild(div);
      });
    });
  }

  function close() {
    var overlay = document.getElementById('map-overlay');
    if (overlay) overlay.style.display = 'none';
    pendingCallback = null;
  }

  function degrade() {
    var loc = document.getElementById('location');
    if (loc) loc.focus();
    if (pendingCallback) { pendingCallback(null); pendingCallback = null; }
  }

  function init() {
    var btn = document.getElementById('map-picker-btn');
    if (btn) btn.addEventListener('click', function () { open(null); });
  }

  function normalizePolygon(polygon) {
    if (!Array.isArray(polygon)) return [];
    var out = [];
    polygon.forEach(function (p) {
      var la, lo;
      if (Array.isArray(p)) { la = Number(p[0]); lo = Number(p[1]); }
      else if (p && typeof p === 'object') { la = Number(p.lat); lo = Number(p.lng != null ? p.lng : p.lon); }
      else return;
      if (validateLatLon(la, lo)) out.push([la, lo]);
    });
    return out;
  }

  function initDisplay(containerId, lat, lon, polygon) {
    loadLeaflet().then(function (ok) {
      if (!ok) return;
      var el = document.getElementById(containerId);
      if (!el) return;
      var pts = normalizePolygon(polygon);
      var center;
      if (pts.length >= 3) {
        var la = 0, lo = 0;
        pts.forEach(function (p) { la += p[0]; lo += p[1]; });
        center = [la / pts.length, lo / pts.length];
      } else {
        center = validateLatLon(lat, lon) ? [lat, lon] : DEFAULT_CENTER;
      }

      if (displayMap) { try { displayMap.remove(); } catch (e) {} displayMap = null; }
      displayMap = L.map(containerId).setView(center, DEFAULT_ZOOM);
      L.tileLayer(OSM_TILE, { subdomains: ['1','2','3','4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(displayMap);

      if (pts.length >= 3) {
        var poly = L.polygon(pts, {
          color: '#3f8f56', weight: 2, fillColor: '#3f8f56', fillOpacity: 0.2
        }).addTo(displayMap);
        try { displayMap.fitBounds(poly.getBounds(), { padding: [18, 18] }); } catch (e) {}
      } else if (validateLatLon(lat, lon)) {
        L.marker([lat, lon], { icon: getMarkerIcon() }).addTo(displayMap);
      }
      setTimeout(function () { displayMap.invalidateSize(); }, 100);
    });
  }

  var api = {
    init: init,
    open: open,
    close: close,
    initDisplay: initDisplay,
    isReady: isReady,
    validateLatLon: validateLatLon,
    getCurrentLocation: getCurrentLocation,
    polygonAreaSqm: polygonAreaSqm
  };

  if (typeof window !== 'undefined') {
    window.LocationPicker = api;
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { validateLatLon: validateLatLon, polygonAreaSqm: polygonAreaSqm };
  }
})();
