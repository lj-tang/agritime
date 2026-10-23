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

  function getLocation() {
    return new Promise(function (resolve) {
      if (!navigator.geolocation) { resolve(null); return; }
      navigator.geolocation.getCurrentPosition(
        function (pos) { resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude }); },
        function () { resolve(null); },
        { timeout: 5000, maximumAge: 600000 }
      );
    });
  }

  function fetchWeather(lat, lon) {
    var url = OPEN_METEO_URL + '?latitude=' + lat + '&longitude=' + lon +
      '&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m' +
      '&timezone=auto';
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MS);
    return fetch(url, { signal: controller.signal })
      .then(function (r) {
        clearTimeout(timer);
        if (!r.ok) return null;
        return r.json();
      })
      .then(function (data) {
        clearTimeout(timer);
        return data;
      })
      .catch(function () {
        clearTimeout(timer);
        return null;
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

  function renderCard(info, locationName) {
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
    if (loc) loc.textContent = locationName || '--';
    if (temp) temp.textContent = (info.temp != null ? Math.round(info.temp) + '°' : '--°');
    if (desc) desc.textContent = info.label;
    if (extra) extra.textContent = (info.humidity != null ? '湿度' + info.humidity + '%' : '') +
      (info.windSpeed != null ? ' 风速' + Math.round(info.windSpeed) + 'km/h' : '');
  }

  function applyTheme(weather, season) {
    var main = document.querySelector('.page--home');
    if (!main) return;
    main.setAttribute('data-weather', weather || 'default');
    main.setAttribute('data-season', season || 'auto');
  }

  function applyBackground(weather) {
    var bg = document.getElementById('weather-bg-layer');
    if (!bg) return;
    var w = weather || 'default';
    bg.style.backgroundImage = "url('" + WEATHER_BG_PATH + w + ".svg')";
  }

  function fallback() {
    renderCard(null);
    applyTheme('default', 'auto');
    applyBackground('default');
  }

  function init() {
    var card = document.getElementById('weather-card');
    if (!card) return;
    getLocation().then(function (loc) {
      if (!loc) { fallback(); return; }
      fetchWeather(loc.lat, loc.lon).then(function (data) {
        var info = buildWeatherInfo(data);
        if (!info) { fallback(); return; }
        var locName = loc.lat.toFixed(1) + ',' + loc.lon.toFixed(1);
        renderCard(info, locName);
        applyTheme(info.weather, info.season);
        applyBackground(info.weather);
      });
    });
  }

  if (typeof window !== 'undefined') {
    window.WeatherVisual = { init: init, normalizeWeather: normalizeWeather, inferSeason: inferSeason };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { normalizeWeather: normalizeWeather, inferSeason: inferSeason, buildWeatherInfo: buildWeatherInfo };
  }
})();