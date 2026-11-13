/**
 * 地图标点 / 框地组件
 * Leaflet + 高德瓦片，动态加载，无 API Key。
 * 功能：
 *   · 标点模式：地图选点 + 逆地理识别到「省市区 + 道路」+ 地点搜索
 *   · 画地块模式：在地图上自行框地（多边形），实时面积，确认时做可种植性校验
 *     —— 城市建成区/核心区（商务区、商业区、住宅区等）会被拦截；
 *        城市绿地（公园/花园等）会提示需确认权限；农田/水体直接放行。
 *
 * 坐标系约定（很容易出错的点）：
 *   · 组件对外一律用 WGS84（浏览器定位、IP 定位、后端接口、存库都是这套）；
 *   · 高德瓦片是 GCJ02，所以「画到地图上」要 +偏，「从地图上取点」要 -偏。
 *   不换算的话，中国境内标点会整体偏 300~600 米，逆地理自然识别到隔壁那条街。
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
  var LOCATE_ZOOM = 15;
  var SEARCH_ZOOM = 16;
  var M_PER_MU = 666.6667;          // 1 亩 ≈ 666.67 平方米

  var leafletReady = null;
  var mapInstance = null;

  /** 图标走 SVG 库（icons.js 先加载），失败时回退到空串 */
  function ic(name) {
    return (window.Icons && window.Icons.svg) ? window.Icons.svg(name) : '';
  }

  var currentMarker = null;
  var currentPoint = null;   // 当前标点（WGS84）
  var pendingCallback = null;
  var displayMap = null;
  var currentPlace = null;   // 当前标点解析出的中文地名，确认时回填
  var resolving = false;     // 反向地理编码进行中
  var geoToken = 0;          // 丢弃过期的识别结果

  // ---- 画地块状态 ----
  var mode = 'point';        // 'point' | 'draw'
  var drawEnabled = false;   // 本次打开是否允许画地块（我的地块传入 opts.draw=true）
  var vertices = [];         // [{lat,lng}] 均为 WGS84
  var drawGroup = null;      // 多边形 + 顶点图层
  var drawPolygonLayer = null;
  var drawPolylineLayer = null;
  var drawClosed = false;
  var drawOverride = false;  // 被判为不适合后，用户二次确认「仍要保存」
  var drawSuitability = null;
  var confirmBusy = false;

  // ---------------- 坐标系换算（WGS84 ⇄ GCJ02） ----------------

  var GCJ_A = 6378245.0;
  var GCJ_EE = 0.00669342162296594323;

  function outOfChina(lat, lon) {
    return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
  }

  function transformLat(x, y) {
    var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
    r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
    r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
    r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
    return r;
  }

  function transformLng(x, y) {
    var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
    r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
    r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
    r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
    return r;
  }

  function wgs84ToGcj02(lat, lon) {
    if (outOfChina(lat, lon)) return { lat: lat, lon: lon };
    var dLat = transformLat(lon - 105.0, lat - 35.0);
    var dLon = transformLng(lon - 105.0, lat - 35.0);
    var radLat = lat / 180.0 * Math.PI;
    var magic = Math.sin(radLat);
    magic = 1 - GCJ_EE * magic * magic;
    var sqrtMagic = Math.sqrt(magic);
    dLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI);
    dLon = (dLon * 180.0) / (GCJ_A / sqrtMagic * Math.cos(radLat) * Math.PI);
    return { lat: lat + dLat, lon: lon + dLon };
  }

  /** 逆变换没有闭式解，迭代两次误差 < 1 米 */
  function gcj02ToWgs84(lat, lon) {
    if (outOfChina(lat, lon)) return { lat: lat, lon: lon };
    var wlat = lat, wlon = lon;
    for (var i = 0; i < 2; i++) {
      var g = wgs84ToGcj02(wlat, wlon);
      wlon += lon - g.lon;
      wlat += lat - g.lat;
    }
    return { lat: wlat, lon: wlon };
  }

  /**
   * 标记图标不走 L.Icon.Default：它依赖 leaflet/images/*.png，而仓库只随包了
   * leaflet.css / leaflet.js，那几张图不存在，每个标记都会 404 且地图上没有图标。
   * 这里用 L.divIcon + 内联 SVG —— html 直接渲染，不经过 imagePath 拼接。
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
   * 后端接的是国内服务商（免 key 的高德网页版 + 离线省市县库），
   * 能返回到「省市区 + 最近道路」的中文地名，key 也不会落到浏览器里。
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
    // 地图中心是 GCJ02，换回 WGS84 再传，让后端按「离我最近」排序
    var w = center ? gcj02ToWgs84(center.lat, center.lng) : null;
    var url = GEO_SEARCH + '?q=' + encodeURIComponent(query)
      + (w ? '&lat=' + w.lat.toFixed(6) + '&lon=' + w.lon.toFixed(6) : '');
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
        resolve({ lat: window.__lastLocation.lat, lon: window.__lastLocation.lon, source: 'cache' });
        return;
      }
      if (navigator.geolocation && !(window.Prefs && window.Prefs.get('location') === false)) {
        navigator.geolocation.getCurrentPosition(
          function (pos) {
            resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude, source: 'gps' });
          },
          function () { getLocationByIP().then(resolve); },
          { enableHighAccuracy: true, timeout: 6000, maximumAge: 300000 }
        );
      } else {
        getLocationByIP().then(resolve);
      }
    });
  }

  function getLocationByIP() {
    // 站点是 http://，浏览器不给 navigator.geolocation（非安全上下文），
    // IP 粗定位是唯一出路；浏览器直连 ipinfo.io 在国内时通时断，先走后端代查。
    return fetchIpApi('/api/geo/ip', 4000).then(function (r) {
      if (r) { r.source = 'ip'; return r; }
      return fetchIpApi('https://ipinfo.io/json', 5000).then(function (x) {
        if (x) x.source = 'ip';
        return x;
      });
    });
  }

  /** 同时兼容 /api/geo/ip 的 {lat, lon} 与 ipinfo.io 的 {loc: "lat,lon"} */
  function fetchIpApi(url, timeoutMs) {
    return new Promise(function (resolve) {
      var controller = new AbortController();
      var timer = setTimeout(function () { controller.abort(); }, timeoutMs);
      fetch(url, { signal: controller.signal })
        .then(function (r) { clearTimeout(timer); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function (body) {
          var data = (body && body.data && typeof body.data === 'object') ? body.data : body;
          var lat = parseFloat(data && data.lat);
          var lon = parseFloat(data && data.lon);
          if (isFinite(lat) && isFinite(lon)) { resolve({ lat: lat, lon: lon }); return; }
          if (data && data.loc) {
            var parts = String(data.loc).split(',');
            resolve({ lat: parseFloat(parts[0]), lon: parseFloat(parts[1]) });
            return;
          }
          resolve(null);
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
      '<button type="button" class="map-mode-btn active" id="map-mode-point">' + ic('pin') + ' 标点</button>' +
      '<button type="button" class="map-mode-btn" id="map-mode-draw">' + ic('pencil') + ' 画地块</button>' +
      '</div>' +
      '<div class="map-search-area">' +
      '<input type="text" id="map-search-input" autocomplete="off" placeholder="搜索地点，如「武汉大学」「金乡县」" />' +
      '<button type="button" id="map-search-btn">搜索</button>' +
      '<button type="button" id="map-locate-btn" title="定位到当前位置">' + ic('locate') + '</button>' +
      '</div>' +
      '<div class="map-search-results" id="map-search-results" hidden></div>' +
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
      '<div class="map-info" id="map-info">' +
      '<div class="map-info-main" id="map-info-main">点击地图选择位置</div>' +
      '<div class="map-info-sub" id="map-info-sub">也可以在上方搜索地名或详细地址</div>' +
      '</div>' +
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

  function initMap(center) {
    var canvas = document.getElementById('map-canvas');
    if (!canvas || canvas.offsetWidth <= 0) return false;
    mapInstance = L.map('map-canvas', { zoomControl: false, attributionControl: true })
      .setView(center, DEFAULT_ZOOM);
    L.tileLayer(OSM_TILE, { subdomains: ['1', '2', '3', '4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(mapInstance);
    // 缩放控件放右下，避免和左上角的搜索结果叠在一起
    L.control.zoom({ position: 'bottomright' }).addTo(mapInstance);
    L.control.scale({ position: 'bottomleft', imperial: false, maxWidth: 110 }).addTo(mapInstance);
    mapInstance.on('click', onMapClick);
    mapInstance.on('dblclick', onMapDblClick);
    return true;
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
      currentPoint = null;
      resolving = false;
      confirmBusy = false;
      drawSuitability = null;
      setConfirmBusy(false);
      resetDrawState();
      updateModeUI();
      resetVerdict();
      hideSearchResults();

      getCurrentLocation().then(function (loc) {
        var hasLoc = loc && validateLatLon(loc.lat, loc.lon);
        var center = hasLoc ? toGcj(loc.lat, loc.lon) : { lat: DEFAULT_CENTER[0], lon: DEFAULT_CENTER[1] };
        setTimeout(function () {
          if (!mapInstance) {
            if (!initMap([center.lat, center.lon])) return;
          } else {
            mapInstance.setView([center.lat, center.lon], DEFAULT_ZOOM);
            if (currentMarker) { mapInstance.removeLayer(currentMarker); currentMarker = null; }
          }
          mapInstance.doubleClickZoom.enable();
          if (mode === 'draw') {
            enterDrawMode();
          } else if (hasLoc) {
            placeMarker(loc.lat, loc.lon);
            if (loc.source === 'ip') {
              setSub('已按网络位置定位到城市，可拖动标记或搜索更精确的地点');
            }
          }
          mapInstance.invalidateSize();
        }, 200);
      });
    });
  }

  function toGcj(lat, lon) {
    var g = wgs84ToGcj02(lat, lon);
    return { lat: g.lat, lon: g.lon };
  }

  function onMapClick(e) {
    if (!mapInstance) return;
    // 地图上的点属于 GCJ02，换回 WGS84 才能和定位/存库对齐
    var w = gcj02ToWgs84(e.latlng.lat, e.latlng.lng);
    if (mode !== 'draw') { placeMarker(w.lat, w.lon); return; }
    if (drawClosed) return;
    // 点回第一个顶点视为闭合
    if (vertices.length >= 3) {
      var first = mapInstance.latLngToContainerPoint(toGcj(vertices[0].lat, vertices[0].lng));
      var here = mapInstance.latLngToContainerPoint(e.latlng);
      if (first.distanceTo(here) < 14) { finishDraw(); return; }
    }
    vertices.push({ lat: w.lat, lng: w.lon });
    drawOverride = false;
    redrawDraw();
  }

  function onMapDblClick() {
    if (mode !== 'draw') return;
    finishDraw();
  }

  // ---------------- 标点模式 ----------------

  /** lat/lon 是 WGS84；只有画到地图上时才换成 GCJ02 */
  function placeMarker(lat, lon) {
    if (!mapInstance || !validateLatLon(lat, lon)) return;
    currentPoint = { lat: Number(lat), lon: Number(lon) };
    var g = toGcj(lat, lon);
    if (currentMarker) mapInstance.removeLayer(currentMarker);
    currentMarker = L.marker([g.lat, g.lon], {
      icon: getMarkerIcon(), draggable: true, zIndexOffset: 900, keyboard: false
    }).addTo(mapInstance);
    currentMarker.on('dragend', function (e) {
      var pos = e.target.getLatLng();
      var w = gcj02ToWgs84(pos.lat, pos.lng);
      updateInfo(w.lat, w.lon);
    });
    updateInfo(lat, lon);
  }

  function coordText(lat, lon) {
    return '经度 ' + Number(lon).toFixed(5) + '，纬度 ' + Number(lat).toFixed(5);
  }

  function updateInfo(lat, lon) {
    currentPoint = { lat: Number(lat), lon: Number(lon) };
    currentPlace = null;
    resolving = true;
    setConfirmBusy(true, '识别位置…');
    setInfo('正在识别位置…', coordText(lat, lon));

    var token = ++geoToken;
    reverseGeocode(lat, lon, function (place) {
      if (token !== geoToken) return;   // 拖拽过快时丢弃过期结果
      resolving = false;
      currentPlace = place;

      if (place) {
        var precise = place.level === 'street' || place.level === 'poi'
          || place.level === 'building' || place.level === 'community';
        setInfo(place.displayName, coordText(lat, lon) + (precise ? ' · 可拖动标记微调' : ' · 可拖动标记或搜索更精确的地点'));
      } else {
        setInfo('未识别到中文地名', coordText(lat, lon) + ' · 可直接确认，或搜索附近地点');
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

  /** 把地图移到某个 WGS84 点；点选搜索结果的「定位到这一点」也走这里 */
  function focusTo(lat, lon, zoom) {
    if (!mapInstance || !validateLatLon(lat, lon)) return;
    var g = toGcj(lat, lon);
    mapInstance.setView([g.lat, g.lon], zoom || SEARCH_ZOOM);
    if (mode !== 'draw') placeMarker(lat, lon);
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
      var w = gcj02ToWgs84(c.lat, c.lng);
      placeMarker(w.lat, w.lon);
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

    var latlngs = vertices.map(function (v) {
      var g = toGcj(v.lat, v.lng);
      return [g.lat, g.lon];
    });
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
      var g = toGcj(v.lat, v.lng);
      var m = L.marker([g.lat, g.lon], {
        draggable: true, icon: getVertexIcon(i === 0), zIndexOffset: 1000
      }).addTo(drawGroup);
      m.on('drag', function (e) {
        var p = e.target.getLatLng();
        var w = gcj02ToWgs84(p.lat, p.lng);
        vertices[i] = { lat: w.lat, lng: w.lon };
        updateDrawGeometry();
        updateDrawInfo();
      });
      m.on('dragend', function (e) {
        var p = e.target.getLatLng();
        var w = gcj02ToWgs84(p.lat, p.lng);
        vertices[i] = { lat: w.lat, lng: w.lon };
        drawOverride = false;
        redrawDraw();
      });
    });

    updateDrawInfo();
  }

  /** 拖拽顶点时只更新几何，不重建标记（重建会中断拖拽） */
  function updateDrawGeometry() {
    var latlngs = vertices.map(function (v) {
      var g = toGcj(v.lat, v.lng);
      return [g.lat, g.lon];
    });
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

  function setInfo(text, sub) {
    var main = document.getElementById('map-info-main');
    if (main) main.textContent = text;
    if (arguments.length > 1) setSub(sub);
  }

  function setSub(text) {
    var sub = document.getElementById('map-info-sub');
    if (sub) sub.textContent = text || '';
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
    if (!currentMarker || !currentPoint) { close(); return; }
    if (resolving) return;   // 按钮此时是禁用的，这里再兜一层

    var name = selectionName();

    // 经纬度保留 6 位小数（约 0.1 米），只交给后端参与计算，界面不展示
    // src=map：这是用户亲手标的点，够精确，可以参与"是不是建成区"的判断
    window.__markerLatLon = { lat: round6(currentPoint.lat), lon: round6(currentPoint.lon), src: 'map' };
    // 同步成"地名 → 坐标"的解析结果：否则提交时会对同一个地名再解析一次，
    // 把用户标的那块地换成市政府坐标（城区判定、霜期全都跟着错）
    window.__resolvedLL = {
      lat: window.__markerLatLon.lat,
      lon: window.__markerLatLon.lon,
      title: name,
      text: name
    };
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

  /** 回填到地点输入框的文本：优先用识别到的地名，识别不出来时才用占位文案 */
  function selectionName() {
    if (currentPlace && currentPlace.displayName) return currentPlace.displayName;
    return '地图选点';
  }

  function round6(v) {
    return Math.round(v * 1e6) / 1e6;
  }

  function locateToCurrent() {
    setInfo('正在定位…', '优先使用设备定位，失败时回退到网络位置');
    getCurrentLocation().then(function (loc) {
      if (loc && validateLatLon(loc.lat, loc.lon) && mapInstance) {
        focusTo(loc.lat, loc.lon, LOCATE_ZOOM);
        if (loc.source === 'ip') {
          setSub('已按网络位置定位到城市，可拖动标记或搜索更精确的地点');
        }
      } else {
        setInfo('定位失败', '请在地图上点击选择，或在上方搜索地点');
      }
    });
  }

  // ---------------- 地点搜索 ----------------

  function hideSearchResults() {
    var box = document.getElementById('map-search-results');
    if (box) { box.hidden = true; box.innerHTML = ''; }
  }

  function showSearchState(html) {
    var box = document.getElementById('map-search-results');
    if (!box) return null;
    box.hidden = false;
    box.innerHTML = html;
    return box;
  }

  function doSearch() {
    var input = document.getElementById('map-search-input');
    if (!input) return;
    var kw = input.value.trim();
    if (!kw) { input.focus(); return; }

    showSearchState('<div class="map-searching">正在搜索「' + escapeHtml(kw) + '」…</div>');
    searchGeocode(kw, function (items) {
      if (items === null) {
        showSearchState('<div class="map-no-result">搜索服务暂不可用，请直接在地图上点击选择位置</div>');
        return;
      }
      if (!items.length) {
        showSearchState('<div class="map-no-result">没有找到「' + escapeHtml(kw) + '」，换个说法试试</div>');
        return;
      }
      renderSearchResults(items);
    });
  }

  function renderSearchResults(items) {
    var box = showSearchState('<div class="map-search-head">找到 ' + items.length + ' 个地点</div>');
    if (!box) return;

    items.forEach(function (item) {
      var row = document.createElement('div');
      row.className = 'map-search-item';

      var title = document.createElement('div');
      title.className = 'map-search-title';
      title.textContent = item.title || '未知地点';
      row.appendChild(title);

      if (item.address) {
        var addr = document.createElement('div');
        addr.className = 'map-search-addr';
        addr.textContent = item.address;
        row.appendChild(addr);
      }

      row.addEventListener('click', function () {
        var lat = Number(item.lat);
        var lon = Number(item.lon);
        if (!validateLatLon(lat, lon)) return;
        hideSearchResults();
        focusTo(lat, lon, SEARCH_ZOOM);
      });
      box.appendChild(row);
    });
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function close() {
    var overlay = document.getElementById('map-overlay');
    if (overlay) overlay.style.display = 'none';
    hideSearchResults();
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
      var pts = normalizePolygon(polygon).map(function (p) {
        var g = toGcj(p[0], p[1]);
        return [g.lat, g.lon];
      });
      var center;
      if (pts.length >= 3) {
        var la = 0, lo = 0;
        pts.forEach(function (p) { la += p[0]; lo += p[1]; });
        center = [la / pts.length, lo / pts.length];
      } else if (validateLatLon(lat, lon)) {
        var g = toGcj(lat, lon);
        center = [g.lat, g.lon];
      } else {
        center = DEFAULT_CENTER;
      }

      if (displayMap) { try { displayMap.remove(); } catch (e) {} displayMap = null; }
      displayMap = L.map(containerId, { zoomControl: true, scrollWheelZoom: false }).setView(center, DEFAULT_ZOOM);
      L.tileLayer(OSM_TILE, { subdomains: ['1', '2', '3', '4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(displayMap);

      if (pts.length >= 3) {
        var poly = L.polygon(pts, {
          color: '#3f8f56', weight: 2, fillColor: '#3f8f56', fillOpacity: 0.2
        }).addTo(displayMap);
        try { displayMap.fitBounds(poly.getBounds(), { padding: [18, 18] }); } catch (e) {}
      } else if (validateLatLon(lat, lon)) {
        L.marker(center, { icon: getMarkerIcon() }).addTo(displayMap);
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
    polygonAreaSqm: polygonAreaSqm,
    wgs84ToGcj02: wgs84ToGcj02,
    gcj02ToWgs84: gcj02ToWgs84
  };

  if (typeof window !== 'undefined') {
    window.LocationPicker = api;
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      validateLatLon: validateLatLon,
      polygonAreaSqm: polygonAreaSqm,
      wgs84ToGcj02: wgs84ToGcj02,
      gcj02ToWgs84: gcj02ToWgs84
    };
  }
})();
