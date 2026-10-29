/**
 * 地图标点组件（V4 T06）
 * Leaflet + OpenStreetMap，动态加载，无 API Key。
 * 功能：地图选点 + 反向地理编码 + 文字搜索 + 降级。
 */
(function () {
  var LEAFLET_CSS = '../assets/leaflet/leaflet.css';
  var LEAFLET_JS = '../assets/leaflet/leaflet.js';
  var OSM_TILE = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}';
  var GEO_API = '/api/geo/reverse';
  var GEO_SEARCH = '/api/geo/search';
  var LOAD_TIMEOUT = 5000;
  var DEFAULT_CENTER = [39.9042, 116.4074];
  var DEFAULT_ZOOM = 13;

  var leafletReady = null;
  var mapInstance = null;
  var currentMarker = null;
  var pendingCallback = null;
  var displayMap = null;
  var currentPlace = null;   // 当前标点解析出的中文地名，确认时回填
  var resolving = false;     // 反向地理编码进行中
  var geoToken = 0;          // 丢弃过期的识别结果

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

  function open(onConfirm) {
    pendingCallback = onConfirm;
    loadLeaflet().then(function (ok) {
      if (!ok) { degrade(); return; }
      var overlay = document.getElementById('map-overlay');
      if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'map-overlay';
        overlay.className = 'map-overlay';
        overlay.innerHTML =
          '<div class="map-panel">' +
          '<div class="map-header"><span>地图标点</span><button type="button" class="map-close" id="map-close">×</button></div>' +
          '<div class="map-search-area"><input type="text" id="map-search-input" placeholder="搜索地点…" /><button type="button" id="map-search-btn">搜索</button><button type="button" id="map-locate-btn" title="定位到当前位置">📍</button></div>' +
          '<div class="map-search-results" id="map-search-results"></div>' +
          '<div class="map-canvas" id="map-canvas"></div>' +
          '<div class="map-info" id="map-info">点击地图选择位置</div>' +
          '<div class="map-actions"><button type="button" class="btn btn-primary" id="map-confirm">确认</button><button type="button" class="btn btn-ghost" id="map-cancel">取消</button></div>' +
          '</div>';
        document.body.appendChild(overlay);
        document.getElementById('map-close').addEventListener('click', close);
        document.getElementById('map-cancel').addEventListener('click', close);
        document.getElementById('map-confirm').addEventListener('click', confirmSelect);
        document.getElementById('map-search-btn').addEventListener('click', doSearch);
        document.getElementById('map-locate-btn').addEventListener('click', locateToCurrent);
        document.getElementById('map-search-input').addEventListener('keydown', function (e) { if (e.key === 'Enter') doSearch(); });
      }
      overlay.style.display = 'flex';
      currentPlace = null;
      resolving = false;
      setConfirmBusy(false);
      getCurrentLocation().then(function (loc) {
        var center = (loc && validateLatLon(loc.lat, loc.lon)) ? [loc.lat, loc.lon] : DEFAULT_CENTER;
        setTimeout(function () {
          if (!mapInstance) {
            var canvas = document.getElementById('map-canvas');
            if (canvas && canvas.offsetWidth > 0) {
              mapInstance = L.map('map-canvas').setView(center, DEFAULT_ZOOM);
              L.tileLayer(OSM_TILE, { subdomains: ['1','2','3','4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(mapInstance);
              mapInstance.on('click', function (e) { placeMarker(e.latlng.lat, e.latlng.lng); });
              if (loc && validateLatLon(loc.lat, loc.lon)) placeMarker(loc.lat, loc.lon);
            }
          } else {
            mapInstance.setView(center, DEFAULT_ZOOM);
            if (loc && validateLatLon(loc.lat, loc.lon) && !currentMarker) placeMarker(loc.lat, loc.lon);
          }
          if (mapInstance) mapInstance.invalidateSize();
        }, 200);
      });
    });
  }

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
        el.textContent = '该位置暂无法识别地名，可拖动标点重试，或确认后手动补充地点';
      }
      setConfirmBusy(false);
    });
  }

  /** 识别期间禁用确认，保证回填的一定是地名而不是「地图选点位置」 */
  function setConfirmBusy(busy) {
    var btn = document.getElementById('map-confirm');
    if (!btn) return;
    btn.disabled = !!busy;
    btn.textContent = busy ? '识别位置…' : '确认';
  }

  function locateToCurrent() {
    var info = document.getElementById('map-info');
    if (info) info.textContent = '正在定位…';
    getCurrentLocation().then(function (loc) {
      if (loc && validateLatLon(loc.lat, loc.lon) && mapInstance) {
        mapInstance.setView([loc.lat, loc.lon], DEFAULT_ZOOM);
        placeMarker(loc.lat, loc.lon);
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
            placeMarker(Number(item.lat), Number(item.lon));
          }
          results.innerHTML = '';
        });
        results.appendChild(div);
      });
    });
  }

  function confirmSelect() {
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

  /** 回填到地点输入框的文本：优先用小区级地名 */
  function selectionName() {
    if (currentPlace && currentPlace.displayName) return currentPlace.displayName;
    return '地图选点位置';
  }

  function round6(v) {
    return Math.round(v * 1e6) / 1e6;
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

  function initDisplay(containerId, lat, lon) {
    loadLeaflet().then(function (ok) {
      if (!ok) return;
      var el = document.getElementById(containerId);
      if (!el) return;
      var center = validateLatLon(lat, lon) ? [lat, lon] : DEFAULT_CENTER;
      displayMap = L.map(containerId).setView(center, DEFAULT_ZOOM);
      L.tileLayer(OSM_TILE, { subdomains: ['1','2','3','4'], attribution: '© 高德地图', maxZoom: 18 }).addTo(displayMap);
      if (validateLatLon(lat, lon)) {
        L.marker([lat, lon], { icon: getMarkerIcon() }).addTo(displayMap);
      }
      setTimeout(function () { displayMap.invalidateSize(); }, 100);
    });
  }

  var api = { init: init, open: open, close: close, initDisplay: initDisplay, isReady: isReady, validateLatLon: validateLatLon };

  if (typeof window !== 'undefined') {
    window.LocationPicker = api;
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { validateLatLon: validateLatLon };
  }
})();