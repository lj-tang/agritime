/**
 * 主页天气视觉动态适配模块（T01）
 * 纯原生 JS，无第三方依赖。
 * 功能：定位 → 请求 Open-Meteo → 归一化 weathercode → 渲染天气卡 + 切换背景/主题色。
 * 降级：定位失败/请求超时/接口异常 → 默认主题，不白屏不报错。
 */
(function () {
  var OPEN_METEO_URL = 'https://api.open-meteo.com/v1/forecast';
  var REQUEST_TIMEOUT_MS = 8000;
  var WEATHER_BG_PATH = '../assets/weather-bg/';
  var DEFAULT_LAT = 39.9042;
  var DEFAULT_LON = 116.4074;
  var DEFAULT_LOC_NAME = '当前位置';
  // 地名要过一次 Photon，最多等这么久；超时就先按原名显示，不拖首屏
  var GEONAME_TIMEOUT_MS = 2500;
  var GEONAME_BUDGET_MS = 3000;

  var WMO_MAP = {
    0: 'sunny', 1: 'sunny', 2: 'cloudy', 3: 'cloudy',
    45: 'cloudy', 48: 'cloudy',
    51: 'rain', 53: 'rain', 55: 'rain',
    56: 'rain', 57: 'rain',
    61: 'rain', 63: 'rain', 65: 'rain',
    66: 'rain', 67: 'rain',
    71: 'snow', 73: 'snow', 75: 'snow',
    77: 'snow',
    80: 'rain', 81: 'rain', 82: 'rain',
    85: 'snow', 86: 'snow',
    95: 'rain', 96: 'rain', 99: 'rain'
  };

  var WEATHER_LABEL = {
    sunny: '晴', rain: '雨', cloudy: '多云', snow: '雪', default: '未知'
  };

  var WEATHER_ICON = {
    sunny: '☀️', rain: '🌧️', cloudy: '☁️', snow: '🌨️', default: '🌡️'
  };

  function normalizeWeather(code) {
    if (typeof code !== 'number' || isNaN(code)) return 'default';
    return WMO_MAP[code] || 'default';
  }

  function inferSeason(month) {
    if (month >= 3 && month <= 5) return 'spring';
    if (month >= 6 && month <= 8) return 'summer';
    if (month >= 9 && month <= 11) return 'autumn';
    return 'winter';
  }

  function getLocationByIP() {
    // 站点是 http://，浏览器没有 geolocation，IP 粗定位是唯一出路；
    // 而浏览器直连 ipinfo.io 在国内时通时断，所以先走后端代查，失败再直连兜底。
    return fetchIpApi('/api/geo/ip', 4000).then(function (r) {
      if (r) return r;
      return fetchIpApi('https://ipinfo.io/json', 5000);
    });
  }

  /** 同时兼容两种返回：/api/geo/ip 的 {lat, lon, city} 与 ipinfo.io 的 {loc, city} */
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
          if (!isFinite(lat) && data && data.loc) {
            var parts = String(data.loc).split(',');
            lat = parseFloat(parts[0]);
            lon = parseFloat(parts[1]);
          }
          if (isFinite(lat) && isFinite(lon)) {
            resolve({ lat: lat, lon: lon, name: data.city || data.region || null });
          } else {
            resolve(null);
          }
        })
        .catch(function () { clearTimeout(timer); resolve(null); });
    });
  }


  function getLocation() {
    return new Promise(function (resolve) {
      // 用户在设置里关了定位权限时，不碰 GPS，直接走 IP 粗定位
      if (window.Prefs && window.Prefs.get('location') === false) { getLocationByIP().then(resolve); return; }
      if (!navigator.geolocation) { getLocationByIP().then(resolve); return; }
      navigator.geolocation.getCurrentPosition(
        function (pos) { resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude }); },
        function () { getLocationByIP().then(resolve); },
        { timeout: 5000, maximumAge: 600000 }
      );
    });
  }

  function isChinese(s) { return /[\u4e00-\u9fa5]/.test(s || ''); }

  /**
   * 英文/拼音城市名 → 中文地名。
   * ipinfo 只给 "Nanjing"，直接贴上卡片就是中英混杂；/api/geo/search 能换成「江苏省南京市」。
   * 取不到返回 null，由调用方退回原名 —— 宁可显示英文，也不能编一个地名。
   */
  function lookupChineseName(raw, lat, lon) {
    if (!raw) return Promise.resolve(null);
    if (isChinese(raw)) return Promise.resolve(raw);
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, GEONAME_TIMEOUT_MS);
    return fetch('/api/geo/search?q=' + encodeURIComponent(raw)
        + '&lat=' + lat + '&lon=' + lon, { signal: controller.signal })
      .then(function (r) { clearTimeout(timer); return r.ok ? r.json() : null; })
      .then(function (body) {
        var items = (body && body.data && body.data.items) || [];
        for (var i = 0; i < items.length; i += 1) {
          var it = items[i] || {};
          if (!isChinese(it.title)) continue;
          var addr = it.address || '';
          // 「南京市」+「江苏省」→「江苏省南京市」；带 · 的地址本身已含完整层级，直接用 title
          if (addr && addr.indexOf(it.title) < 0 && addr.indexOf('·') < 0) return addr + it.title;
          return it.title;
        }
        return null;
      })
      .catch(function () { clearTimeout(timer); return null; });
  }

  /** 只有坐标没有地名时（GPS 授权成功）反查中文地名。 */
  function reverseName(lat, lon) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, GEONAME_TIMEOUT_MS);
    return fetch('/api/geo/reverse?lat=' + lat + '&lon=' + lon, { signal: controller.signal })
      .then(function (r) { clearTimeout(timer); return r.ok ? r.json() : null; })
      .then(function (body) {
        var d = (body && body.data) || {};
        var name = d.city || d.district || d.name || '';
        return isChinese(name) ? name : null;
      })
      .catch(function () { clearTimeout(timer); return null; });
  }

  /**
   * 地名统一出口：有名字就翻译成中文，只有坐标就先反查，
   * 再退一步用出口 IP 的城市名（GPS 与 IP 一般同城）。
   */
  function resolvePlaceName(loc, lat, lon) {
    if (loc && loc.name) return lookupChineseName(loc.name, lat, lon);
    return reverseName(lat, lon).then(function (n) {
      if (n) return n;
      return getLocationByIP().then(function (ip) {
        return ip && ip.name ? lookupChineseName(ip.name, lat, lon) : null;
      });
    });
  }

  /** 地名解析是锦上添花，超时就放弃，别把首屏卡住。 */
  function withTimeout(p, ms) {
    return new Promise(function (resolve) {
      var done = false;
      var timer = setTimeout(function () {
        if (done) return;
        done = true;
        resolve(null);
      }, ms);
      p.then(function (v) {
        if (done) return;
        done = true;
        clearTimeout(timer);
        resolve(v);
      });
    });
  }

  function requestJson(url, timeoutMs) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, timeoutMs);
    return fetch(url, { signal: controller.signal })
      .then(function (r) {
        clearTimeout(timer);
        if (!r.ok) return null;
        return r.json();
      })
      .catch(function () {
        clearTimeout(timer);
        return null;
      });
  }

  /**
   * 天气取数：先走后端代理，失败再直连。
   * 浏览器直连 api.open-meteo.com 在国内时通时断，断了整张卡就只剩"天气数据暂不可用"；
   * 后端在境外、链路稳定（实测 200 / 1.5s），所以代理是主路径、直连是兜底。
   */
  function fetchWeather(lat, lon) {
    var qs = '?latitude=' + lat + '&longitude=' + lon +
      '&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m' +
      '&daily=sunrise,sunset&timezone=auto';
    return requestJson('/api/weather/now' + qs, REQUEST_TIMEOUT_MS).then(function (d) {
      if (d && d.current) { window.__weatherSource = 'proxy'; return d; }
      return requestJson(OPEN_METEO_URL + qs, REQUEST_TIMEOUT_MS).then(function (d2) {
        window.__weatherSource = d2 && d2.current ? 'direct' : 'none';
        return d2;
      });
    });
  }

  function buildWeatherInfo(data) {
    if (!data || !data.current) return null;
    var c = data.current;
    var code = c.weather_code;
    var weather = normalizeWeather(code);
    var month = new Date().getMonth() + 1;
    return {
      temp: c.temperature_2m,
      humidity: c.relative_humidity_2m,
      windSpeed: c.wind_speed_10m,
      weather: weather,
      label: WEATHER_LABEL[weather] || '未知',
      season: inferSeason(month),
      weathercode: code
    };
  }

  /**
   * 天气 → 一句农事建议。
   * 只给一句，但结论必须能被上面的数据支撑：雨天/大风/低温都不该喷药，
   * 高温、高湿、干燥各有各的坑。判断不了就不说话，不硬凑。
   */
  function farmAdvice(info) {
    if (!info) return null;
    var t = info.temp, hum = info.humidity, wind = info.windSpeed, w = info.weather;
    if (w === 'rain') return { level: 'no', text: '今日有雨，不宜喷药施肥，药液会被冲走' };
    if (w === 'snow') return { level: 'no', text: '今日有雪，田间作业暂停，注意棚室保温' };
    if (wind != null && wind >= 20) {
      return { level: 'no', text: '今日风大（' + Math.round(wind) + 'km/h），不宜喷药，药液易飘移' };
    }
    if (t != null && t <= 5) return { level: 'no', text: '今日气温低，药效起得慢，喷药可等回暖' };
    if (t != null && t >= 33) return { level: 'care', text: '今日高温，避开正午喷药，防药害和中暑' };
    if (hum != null && hum >= 85) return { level: 'care', text: '今日湿度偏高，病害易发，注意查田排湿' };
    if (hum != null && hum <= 30) return { level: 'care', text: '今日空气干燥，旱地注意保墒，喷药宜傍晚' };
    return { level: 'ok', text: '今日适宜喷药、施肥和田间作业' };
  }

  function renderCard(info, locationName, advice) {
    var card = document.getElementById('weather-card');
    if (!card) return;
    if (!info) {
      card.hidden = true;
      return;
    }
    card.hidden = false;
    card.setAttribute('data-weather', info.weather);
    card.setAttribute('data-season', info.season);
    var loc = document.getElementById('weather-loc');
    var temp = document.getElementById('weather-temp');
    var desc = document.getElementById('weather-desc');
    var extra = document.getElementById('weather-extra');
    // 这张卡用的是设备/网络定位，跟下面「所在地」不是一回事，必须写清楚，
    // 否则用户填了哈尔滨、卡上却是上海，会以为播期算错了地方。
    if (loc) {
      loc.textContent = locationName ? '当前 ' + locationName : '--';
      loc.title = '按设备或网络定位显示，与下方填写的「所在地」无关';
    }
    if (temp) temp.textContent = (info.temp != null ? Math.round(info.temp) + '°' : '--°');
    if (desc) desc.textContent = (WEATHER_ICON[info.weather] || '') + ' ' + info.label;
    if (extra) extra.textContent = (info.humidity != null ? '湿度' + info.humidity + '%' : '') +
      (info.windSpeed != null ? ' 风速' + Math.round(info.windSpeed) + 'km/h' : '');
    renderFarmAdvice(advice || farmAdvice(info));
  }

  function renderFarmAdvice(advice) {
    var el = document.getElementById('weather-farm');
    if (!el) return;
    if (!advice || !advice.text) { el.hidden = true; el.textContent = ''; return; }
    el.hidden = false;
    el.className = 'weather-farm weather-farm--' + advice.level;
    el.textContent = advice.text;
  }

  function applyTheme(weather, season) {
    var main = document.querySelector('.page--home');
    if (!main) return;
    main.setAttribute('data-weather', weather || 'default');
    main.setAttribute('data-season', season || 'auto');
  }

  function judgeDayNight() {
    var now = new Date();
    var cur = now.getHours() * 60 + now.getMinutes();
    var sd = typeof window !== 'undefined' ? window.__sunData : null;
    var rise = sd && sd.sunrise != null ? sd.sunrise : 6 * 60;
    var set = sd && sd.sunset != null ? sd.sunset : 18 * 60;
    return (cur >= rise && cur < set) ? 'day' : 'night';
  }

  function applyBackground(weather) {
    var bg = document.getElementById('weather-bg-layer');
    if (!bg) return;
    var w = weather || 'default';
    var dn = judgeDayNight();
    var main = document.querySelector('.page--home');
    if (main) main.setAttribute('data-daynight', dn);
    var suffix = (dn === 'night' && w !== 'default') ? '-night' : '';
    bg.style.backgroundImage = "url('" + WEATHER_BG_PATH + w + suffix + ".svg')";
  }

  function notifyReady() {
    document.dispatchEvent(new CustomEvent('weather-ready'));
  }

  function fallback() {
    applyTheme('default', 'auto');
    applyBackground('default');
    var card = document.getElementById('weather-card');
    if (card) {
      card.hidden = false;
      card.setAttribute('data-weather', 'default');
      card.setAttribute('data-season', 'auto');
      var loc = document.getElementById('weather-loc');
      var temp = document.getElementById('weather-temp');
      var desc = document.getElementById('weather-desc');
      var extra = document.getElementById('weather-extra');
      if (loc) loc.textContent = DEFAULT_LOC_NAME;
      if (temp) temp.textContent = '--°';
      if (desc) desc.textContent = '天气数据暂不可用';
      if (extra) extra.textContent = '';
      // 没有天气数据就不给农事建议：宁可空着，也不能凭空说"今日适宜喷药"
      renderFarmAdvice(null);
    }
    notifyReady();
  }

  function notifyLocated(detail) {
    document.dispatchEvent(new CustomEvent('weather-located', { detail: detail }));
  }

  function init() {
    var card = document.getElementById('weather-card');
    if (!card) { notifyReady(); return; }
    var readyCalled = false;
    function ensureReady() { if (!readyCalled) { readyCalled = true; notifyReady(); } }
    setTimeout(ensureReady, 8000);
    getLocation().then(function (loc) {
      var lat = loc ? loc.lat : DEFAULT_LAT;
      var lon = loc ? loc.lon : DEFAULT_LON;
      // 天气与地名并行：地名要过一次 Photon，串起来会让首屏多等一两秒
      Promise.all([
        fetchWeather(lat, lon),
        withTimeout(resolvePlaceName(loc, lat, lon), GEONAME_BUDGET_MS)
      ]).then(function (arr) {
        var data = arr[0];
        var zhName = arr[1];
        var info = buildWeatherInfo(data);
        if (!info) { fallback(); ensureReady(); return; }
        var rawName = (loc && loc.name) || null;
        renderCard(info, zhName || rawName || DEFAULT_LOC_NAME);
        applyTheme(info.weather, info.season);
        applyBackground(info.weather);
        if (data && data.daily && window.TimePeriod) {
          var sr = window.TimePeriod.parseSunTime(data.daily.sunrise && data.daily.sunrise[0]);
          var ss = window.TimePeriod.parseSunTime(data.daily.sunset && data.daily.sunset[0]);
          window.TimePeriod.applySunData(sr, ss);
        }
        // 卡片上显示的是什么地方，就把同一个地名交给首页预填，避免顶部写"南京"、表单写"济宁"
        if (loc) {
          var placeName = zhName || rawName || null;
          window.__lastLocation = { lat: lat, lon: lon, name: placeName };
          if (placeName) notifyLocated({ lat: lat, lon: lon, name: placeName });
        }
        ensureReady();
      });
    });
  }

  if (typeof window !== 'undefined') {
    window.WeatherVisual = {
      init: init,
      normalizeWeather: normalizeWeather,
      inferSeason: inferSeason,
      farmAdvice: farmAdvice
    };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      normalizeWeather: normalizeWeather,
      inferSeason: inferSeason,
      buildWeatherInfo: buildWeatherInfo,
      farmAdvice: farmAdvice,
      lookupChineseName: lookupChineseName
    };
  }
})();