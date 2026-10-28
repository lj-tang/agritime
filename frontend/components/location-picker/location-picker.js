/**
 * 地图标点组件（V4 T06）
 * Leaflet + OpenStreetMap，动态加载，无 API Key。
 * 功能：地图选点 + 反向地理编码 + 文字搜索 + 降级。
 */
(function () {
  var LEAFLET_CSS = '../assets/leaflet/leaflet.css';
  var LEAFLET_JS = '../assets/leaflet/leaflet.js';
  var OSM_TILE = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}';
  var NOMINATIM_URL = 'https://nominatim.openstreetmap.org';
  var LOAD_TIMEOUT = 5000;
  var DEFAULT_CENTER = [39.9042, 116.4074];
  var DEFAULT_ZOOM = 13;

  var leafletReady = null;
  var mapInstance = null;
  var currentMarker = null;
  var pendingCallback = null;
  var displayMap = null;

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

  function reverseGeocode(lat, lon, cb) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, 4000);
    fetch(NOMINATIM_URL + '/reverse?format=json&lat=' + lat + '&lon=' + lon + '&zoom=18&accept-language=zh', { signal: controller.signal })
      .then(function (r) { clearTimeout(timer); return r.json(); })
      .then(function (data) {
        if (data && data.display_name) cb(data.display_name);
        else cb(null);
      })
      .catch(function () { clearTimeout(timer); cb(null); });
  }

  function searchGeocode(query, cb) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, 4000);
    fetch(NOMINATIM_URL + '/search?format=json&q=' + encodeURIComponent(query) + '&limit=10&accept-language=zh', { signal: controller.signal })
      .then(function (r) { clearTimeout(timer); return r.json(); })
      .then(function (data) {
        if (Array.isArray(data)) cb(data);
        else cb([]);
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
    currentMarker = L.marker([lat, lon], { draggable: true }).addTo(mapInstance);
    currentMarker.on('dragend', function (e) {
      var pos = e.target.getLatLng();
      updateInfo(pos.lat, pos.lng);
    });
    updateInfo(lat, lon);
  }

  function updateInfo(lat, lon) {
    var info = document.getElementById('map-info');
    if (info) info.textContent = '经纬度：' + lat.toFixed(4) + ', ' + lon.toFixed(4) + '（正在获取地名…）';
    reverseGeocode(lat, lon, function (name) {
      if (info) {
        if (name) info.textContent = name + '（' + lat.toFixed(4) + ', ' + lon.toFixed(4) + '）';
        else info.textContent = '经纬度：' + lat.toFixed(4) + ', ' + lon.toFixed(4);
      }
    });
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
        div.textContent = item.display_name || '未知';
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
    if (currentMarker && pendingCallback) {
      var pos = currentMarker.getLatLng();
      var info = document.getElementById('map-info');
      var infoText = info ? info.textContent : '';
      var name = '';
      if (infoText && infoText.indexOf('经纬度：') !== 0) {
        name = infoText.split('（')[0].trim();
      }
      if (!name) name = pos.lat.toFixed(4) + ', ' + pos.lng.toFixed(4);
      window.__markerLatLon = { lat: pos.lat, lon: pos.lng };
      var loc = document.getElementById('location');
      if (loc) loc.value = name;
      pendingCallback({ lat: pos.lat, lon: pos.lng, name: name });
    } else if (currentMarker) {
      var pos2 = currentMarker.getLatLng();
      var info2 = document.getElementById('map-info');
      var infoText2 = info2 ? info2.textContent : '';
      var name2 = '';
      if (infoText2 && infoText2.indexOf('经纬度：') !== 0) {
        name2 = infoText2.split('（')[0].trim();
      }
      if (!name2) name2 = pos2.lat.toFixed(4) + ', ' + pos2.lng.toFixed(4);
      window.__markerLatLon = { lat: pos2.lat, lon: pos2.lng };
      var loc2 = document.getElementById('location');
      if (loc2) loc2.value = name2;
    }
    close();
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
        L.marker([lat, lon]).addTo(displayMap);
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