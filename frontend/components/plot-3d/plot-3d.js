/**
 * 3D 地块框选（T06 升级）
 *
 * 与 location-picker 的 2D 框选并存：
 *   · 2D（Leaflet）：选点、找地址、快速框选
 *   · 3D（MapLibre）：看地形起伏、按真实地块边界描点、实时算面积
 *
 * 底图用高德卫星瓦片，地形用 AWS Terrarium DEM（免密钥）。
 * 只做「画」和「量」，评分交给后端 /api/plots/analyze，前端不自己编分数。
 */
(function () {
  var LIB = {
    js: '../assets/maplibre/maplibre-gl.js',
    css: '../assets/maplibre/maplibre-gl.css'
  };
  var M_PER_MU = 666.6667;
  var loading = null;
  var state = null;

  function loadLib() {
    if (window.maplibregl) return Promise.resolve(window.maplibregl);
    if (loading) return loading;
    loading = new Promise(function (resolve, reject) {
      var css = document.createElement('link');
      css.rel = 'stylesheet';
      css.href = LIB.css;
      document.head.appendChild(css);
      var s = document.createElement('script');
      s.src = LIB.js;
      s.async = true;
      s.onload = function () { resolve(window.maplibregl); };
      s.onerror = function () { reject(new Error('地图库加载失败')); };
      document.head.appendChild(s);
    });
    return loading;
  }

  function style() {
    var sat = ['01', '02', '03', '04'].map(function (s) {
      return 'https://webst' + s + '.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}';
    });
    var anno = ['01', '02', '03', '04'].map(function (s) {
      return 'https://webst' + s + '.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}';
    });
    return {
      version: 8,
      sources: {
        sat: { type: 'raster', tiles: sat, tileSize: 256, maxzoom: 18, attribution: '影像 © 高德地图' },
        anno: { type: 'raster', tiles: anno, tileSize: 256, maxzoom: 18 },
        dem: {
          type: 'raster-dem',
          tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
          encoding: 'terrarium',
          tileSize: 256,
          maxzoom: 14,
          attribution: '地形 © AWS Terrain Tiles'
        }
      },
      layers: [
        { id: 'sat', type: 'raster', source: 'sat', paint: { 'raster-brightness-max': 0.92 } },
        { id: 'anno', type: 'raster', source: 'anno', paint: { 'raster-opacity': 0.85 } }
      ],
      terrain: { source: 'dem', exaggeration: 1.4 }
    };
  }

  // 球面多边形面积，与后端 GeoResolver.polygonAreaSqm 同一口径
  function ringAreaSqm(ring) {
    if (!ring || ring.length < 3) return 0;
    var R = 6378137, area = 0, n = ring.length;
    for (var i = 0; i < n; i++) {
      var p1 = ring[i], p2 = ring[(i + 1) % n];
      area += (p2[0] - p1[0]) * Math.PI / 180 *
        (2 + Math.sin(p1[1] * Math.PI / 180) + Math.sin(p2[1] * Math.PI / 180));
    }
    return Math.abs(area * R * R / 2);
  }

  function ringPerimeter(ring) {
    if (!ring || ring.length < 2) return 0;
    var R = 6378137, sum = 0;
    for (var i = 0; i < ring.length; i++) {
      var a = ring[i], b = ring[(i + 1) % ring.length];
      var dLat = (b[1] - a[1]) * Math.PI / 180;
      var dLng = (b[0] - a[0]) * Math.PI / 180;
      var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(a[1] * Math.PI / 180) * Math.cos(b[1] * Math.PI / 180) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
      sum += 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
    }
    return sum;
  }

  function centroid(ring) {
    var lat = 0, lon = 0;
    for (var i = 0; i < ring.length; i++) { lon += ring[i][0]; lat += ring[i][1]; }
    return { lat: lat / ring.length, lon: lon / ring.length };
  }

  function fmt(n, d) {
    return Number(n).toFixed(d == null ? 1 : d);
  }

  function buildOverlay() {
    var wrap = document.createElement('div');
    wrap.className = 'p3d-overlay';
    wrap.innerHTML =
      '<div class="p3d-panel">' +
      '  <div class="p3d-head">' +
      '    <div><div class="p3d-title">3D 框选地块</div>' +
      '    <div class="p3d-sub">拖动地图看地形起伏；单击加顶点，双击闭合</div></div>' +
      '    <button type="button" class="p3d-close" aria-label="关闭">×</button>' +
      '  </div>' +
      '  <div class="p3d-map" id="p3d-map"></div>' +
      '  <div class="p3d-hud">' +
      '    <div class="p3d-stat"><span class="k">顶点</span><span class="v" data-k="n">0</span></div>' +
      '    <div class="p3d-stat"><span class="k">面积</span><span class="v" data-k="area">—</span></div>' +
      '    <div class="p3d-stat"><span class="k">周长</span><span class="v" data-k="peri">—</span></div>' +
      '    <div class="p3d-stat"><span class="k">海拔</span><span class="v" data-k="elev">—</span></div>' +
      '  </div>' +
      '  <div class="p3d-tools">' +
      '    <button type="button" class="p3d-btn" data-act="undo">撤销一点</button>' +
      '    <button type="button" class="p3d-btn" data-act="clear">清空重画</button>' +
      '    <button type="button" class="p3d-btn" data-act="reset">复位视角</button>' +
      '    <button type="button" class="p3d-btn" data-act="tilt">切换俯视/倾斜</button>' +
      '  </div>' +
      '  <div class="p3d-foot">' +
      '    <div class="p3d-hint" data-k="hint">至少 3 个顶点才能生成地块</div>' +
      '    <div class="p3d-actions">' +
      '      <button type="button" class="p3d-btn ghost" data-act="cancel">取消</button>' +
      '      <button type="button" class="p3d-btn primary" data-act="done" disabled>用这块地</button>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(wrap);
    return wrap;
  }

  function open(opts) {
    opts = opts || {};
    loadLib().then(function (maplibregl) {
      var wrap = buildOverlay();
      var mapEl = wrap.querySelector('#p3d-map');
      var hint = wrap.querySelector('[data-k="hint"]');
      var doneBtn = wrap.querySelector('[data-act="done"]');
      var center = opts.center || { lat: 35.07, lon: 116.31 };
      var vertices = (opts.polygon || []).map(function (p) {
        return Array.isArray(p) ? [Number(p[1]), Number(p[0])] : [Number(p.lon), Number(p.lat)];
      });
      var closed = vertices.length >= 3;
      var tilted = true;

      var map = new maplibregl.Map({
        container: mapEl,
        style: style(),
        center: [center.lon, center.lat],
        zoom: opts.zoom || 15.2,
        pitch: 55,
        bearing: -18,
        maxPitch: 75,
        attributionControl: { compact: true }
      });
      map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right');
      map.addControl(new maplibregl.ScaleControl({ maxWidth: 90, unit: 'metric' }), 'bottom-left');

      map.on('load', function () {
        map.addSource('draw', {
          type: 'geojson',
          data: emptyFC()
        });
        map.addLayer({
          id: 'draw-fill', type: 'fill', source: 'draw',
          filter: ['==', ['get', 'kind'], 'polygon'],
          paint: { 'fill-color': '#2f7d4f', 'fill-opacity': 0.28 }
        });
        map.addLayer({
          id: 'draw-line', type: 'line', source: 'draw',
          filter: ['in', ['get', 'kind'], ['literal', ['polygon', 'line']]],
          paint: { 'line-color': '#ffd75e', 'line-width': 2.2, 'line-dasharray': [2, 1] }
        });
        map.addLayer({
          id: 'draw-point', type: 'circle', source: 'draw',
          filter: ['==', ['get', 'kind'], 'point'],
          paint: {
            'circle-radius': 5, 'circle-color': '#fff',
            'circle-stroke-color': '#2f7d4f', 'circle-stroke-width': 2
          }
        });
        refresh();
        if (vertices.length >= 3) map.fitBounds(boundsOf(vertices), { padding: 60, pitch: 55, duration: 0 });
      });

      function emptyFC() { return { type: 'FeatureCollection', features: [] }; }

      function boundsOf(v) {
        var b = new maplibregl.LngLatBounds();
        v.forEach(function (p) { b.extend(p); });
        return b;
      }

      function fc() {
        var features = [];
        var ring = vertices.slice();
        if (closed && ring.length >= 3) {
          var poly = ring.concat([ring[0]]);
          features.push({ type: 'Feature', properties: { kind: 'polygon' }, geometry: { type: 'Polygon', coordinates: [poly] } });
        } else if (ring.length >= 2) {
          features.push({ type: 'Feature', properties: { kind: 'line' }, geometry: { type: 'LineString', coordinates: ring } });
        }
        ring.forEach(function (p) {
          features.push({ type: 'Feature', properties: { kind: 'point' }, geometry: { type: 'Point', coordinates: p } });
        });
        return { type: 'FeatureCollection', features: features };
      }

      function refresh() {
        var src = map.getSource('draw');
        if (src) src.setData(fc());
        var sqm = closed ? ringAreaSqm(vertices) : 0;
        wrap.querySelector('[data-k="n"]').textContent = String(vertices.length);
        wrap.querySelector('[data-k="area"]').textContent = sqm > 0
          ? (sqm >= 10000 ? fmt(sqm / 10000, 2) + ' 公顷' : fmt(sqm, 0) + ' m²') + '（' + fmt(sqm / M_PER_MU, 1) + ' 亩）'
          : '—';
        wrap.querySelector('[data-k="peri"]').textContent = vertices.length >= 2 ? fmt(ringPerimeter(vertices), 0) + ' m' : '—';
        doneBtn.disabled = !closed;
        hint.textContent = closed
          ? '面积按球面多边形计算；点「用这块地」后由后端分析地形与土壤'
          : (vertices.length === 0 ? '单击地图开始描点，双击闭合' : '还需 ' + (3 - vertices.length) + ' 个顶点');
      }

      function updateElev() {
        var c = map.getCenter();
        var e = map.queryTerrainElevation ? map.queryTerrainElevation(c) : null;
        wrap.querySelector('[data-k="elev"]').textContent = e == null ? '加载中' : fmt(e, 0) + ' m';
      }

      map.on('mousemove', function (e) {
        if (!map.queryTerrainElevation) return;
        var h = map.queryTerrainElevation(e.lngLat);
        wrap.querySelector('[data-k="elev"]').textContent = h == null ? '—' : fmt(h, 0) + ' m（鼠标处）';
      });
      map.on('moveend', updateElev);

      // 单击加点；双击闭合。MapLibre 默认双击缩放要关掉，否则会和闭合冲突。
      map.doubleClickZoom.disable();
      map.on('click', function (e) {
        if (closed) return;
        vertices.push([e.lngLat.lng, e.lngLat.lat]);
        refresh();
      });
      map.on('dblclick', function (e) {
        e.preventDefault();
        closeRing();
      });

      function closeRing() {
        if (vertices.length < 3) { hint.textContent = '至少 3 个顶点才能闭合'; return; }
        closed = true;
        refresh();
      }

      wrap.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-act]');
        if (!btn) return;
        var act = btn.getAttribute('data-act');
        if (act === 'undo') {
          vertices.pop();
          if (vertices.length < 3) closed = false;
          refresh();
        } else if (act === 'clear') {
          vertices = [];
          closed = false;
          refresh();
        } else if (act === 'reset') {
          map.easeTo({ pitch: tilted ? 55 : 0, bearing: tilted ? -18 : 0, duration: 500 });
        } else if (act === 'tilt') {
          tilted = !tilted;
          map.easeTo({ pitch: tilted ? 55 : 0, bearing: tilted ? -18 : 0, duration: 500 });
        } else if (act === 'cancel') {
          destroy();
        } else if (act === 'done') {
          if (!closed) return;
          var ringLatLon = vertices.map(function (p) {
            return [Number(p[1].toFixed(6)), Number(p[0].toFixed(6))];
          });
          var c = centroid(vertices);
          var sqm = ringAreaSqm(vertices);
          destroy();
          if (opts.onConfirm) {
            opts.onConfirm({
              polygon: ringLatLon,
              center: { lat: Number(c.lat.toFixed(6)), lon: Number(c.lon.toFixed(6)) },
              areaSqm: Math.round(sqm),
              areaMu: Math.round(sqm / M_PER_MU * 10) / 10,
              perimeterM: Math.round(ringPerimeter(vertices)),
              mode: 'polygon-3d'
            });
          }
        }
      });

      wrap.querySelector('.p3d-close').addEventListener('click', destroy);

      function onKey(e) { if (e.key === 'Escape') destroy(); }
      document.addEventListener('keydown', onKey);

      function destroy() {
        document.removeEventListener('keydown', onKey);
        try { map.remove(); } catch (err) {}
        wrap.remove();
        state = null;
      }

      state = { map: map, destroy: destroy };
      // 容器刚插入，尺寸要等一帧才正确
      requestAnimationFrame(function () { map.resize(); });
    }).catch(function (err) {
      alert('3D 地图加载失败：' + (err && err.message ? err.message : '网络异常') + '，可改用普通地图框选');
    });
  }

  function close() { if (state && state.destroy) state.destroy(); }

  if (typeof window !== 'undefined') {
    window.Plot3D = { open: open, close: close, ringAreaSqm: ringAreaSqm };
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { ringAreaSqm: ringAreaSqm, ringPerimeter: ringPerimeter };
  }
})();
