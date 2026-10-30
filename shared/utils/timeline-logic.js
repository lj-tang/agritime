/**
 * 时间线纯函数与映射常量模块（timeline-logic）
 * 供 timeline.html 与测试共享；无 DOM 依赖，浏览器 + Node 双导出。
 * 数据红线：前端不硬编码节日日期、不推算农历、不自算天气阈值。
 */
(function () {
  var WEATHER_ICON_MAP = {
    clear: { label: '晴', icon: '☀️' },
    sunny: { label: '晴', icon: '☀️' },
    multiCloud: { label: '多云', icon: '⛅' },
    cloudy: { label: '多云', icon: '⛅' },
    overcast: { label: '阴', icon: '☁️' },
    lightRain: { label: '小雨', icon: '🌦️' },
    rain: { label: '雨', icon: '🌧️' },
    heavyRain: { label: '大雨', icon: '🌧️' },
    storm: { label: '暴雨', icon: '⛈️' },
    thunder: { label: '雷', icon: '⛈️' },
    snow: { label: '雪', icon: '❄️' },
    fog: { label: '雾', icon: '🌫️' },
    windy: { label: '风', icon: '🌬️' },
    gale: { label: '大风', icon: '🌬️' },
    hot: { label: '高温', icon: '🔥' },
    cold: { label: '低温', icon: '🧊' },
    haze: { label: '霾', icon: '😷' }
  };
  var WEATHER_ICON_FALLBACK = { label: '天气', icon: '🌡️' };

  var SUITABILITY_MAP = {
    green: { label: '适宜', class: 'tl-suit-green', tone: 'good' },
    yellow: { label: '谨慎', class: 'tl-suit-yellow', tone: 'warn' },
    red: { label: '不宜', class: 'tl-suit-red', tone: 'bad' }
  };

  var SEVERE_WEATHER_MAP = {
    storm: { label: '暴雨', tip: '强降水易致涝渍与冲刷' },
    heavyRain: { label: '暴雨', tip: '强降水易致涝渍与冲刷' },
    gale: { label: '大风', tip: '大风易致倒伏与飘移' },
    windy: { label: '大风', tip: '大风易致倒伏与飘移' },
    highTemp: { label: '高温', tip: '高温易致灼伤与失水' },
    hot: { label: '高温', tip: '高温易致灼伤与失水' },
    frost: { label: '霜冻', tip: '霜冻易致冻害' },
    cold: { label: '霜冻', tip: '霜冻易致冻害' }
  };

  function normStr(v) {
    return typeof v === 'string' ? v.trim() : '';
  }

  function parseDate(s) {
    var str = normStr(s);
    if (!str) return null;
    var m = str.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
    if (!m) return null;
    var y = Number(m[1]), mo = Number(m[2]), d = Number(m[3]);
    if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
    var dt = new Date(y, mo - 1, d);
    if (dt.getFullYear() !== y || dt.getMonth() !== mo - 1 || dt.getDate() !== d) return null;
    return dt;
  }

  function todayOnly() {
    var now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  /**
   * 节日倒计时（仅本地日期差，不推算农历）。
   * @param {string} festivalDate 形如 YYYY-MM-DD
   * @param {Date|string} [today] 参照日，缺省取今天
   * @returns {{days: number|null, state: 'future'|'today'|'past'|'invalid'}}
   */
  function calcCountdown(festivalDate, today) {
    var target = parseDate(festivalDate);
    if (!target) return { days: null, state: 'invalid' };
    var base;
    if (today instanceof Date) base = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    else if (typeof today === 'string') { var t = parseDate(today); base = t || todayOnly(); }
    else base = todayOnly();
    var days = Math.round((target.getTime() - base.getTime()) / 86400000);
    var state = days > 0 ? 'future' : (days === 0 ? 'today' : 'past');
    return { days: days, state: state };
  }

  /**
   * 天气类型 → 图标 + 文案。null 返回 null；未识别返回兜底且保留原文案。
   */
  function getWeatherIcon(type) {
    if (type === null || type === undefined || type === '') return null;
    var key = String(type);
    var hit = WEATHER_ICON_MAP[key];
    if (hit) return { label: hit.label, icon: hit.icon, known: true };
    return { label: key, icon: WEATHER_ICON_FALLBACK.icon, known: false };
  }

  /**
   * 适宜度枚举 → 文案/样式。仅消费后端枚举，null 或未知返回 null（不臆造等级）。
   */
  function getSuitability(value) {
    if (value === null || value === undefined || value === '') return null;
    var hit = SUITABILITY_MAP[String(value)];
    if (!hit) return null;
    return { label: hit.label, class: hit.class, tone: hit.tone };
  }

  /**
   * 恶劣天气标识 → {label, tip}。空返回 null；未识别保留原文。
   */
  function getSevereAlert(alert) {
    if (alert === null || alert === undefined || alert === '') return null;
    if (typeof alert === 'object') {
      var t = alert.type || alert.code || alert.label;
      if (!t) return null;
      var hit = SEVERE_WEATHER_MAP[String(t)];
      return { label: hit ? hit.label : String(t), tip: alert.tip || (hit ? hit.tip : '') };
    }
    var key = String(alert);
    var known = SEVERE_WEATHER_MAP[key];
    if (known) return { label: known.label, tip: known.tip };
    return { label: key, tip: '' };
  }

  /**
   * 天气因子归一化：string / string[] / null → 干净的字符串数组。
   */
  function normalizeFactors(input) {
    if (input === null || input === undefined) return [];
    if (typeof input === 'string') {
      var s = input.trim();
      return s ? [s] : [];
    }
    if (Array.isArray(input)) {
      return input
        .map(function (v) { return normStr(v); })
        .filter(function (v) { return v.length > 0; });
    }
    return [];
  }

  var api = {
    WEATHER_ICON_MAP: WEATHER_ICON_MAP,
    WEATHER_ICON_FALLBACK: WEATHER_ICON_FALLBACK,
    SUITABILITY_MAP: SUITABILITY_MAP,
    SEVERE_WEATHER_MAP: SEVERE_WEATHER_MAP,
    calcCountdown: calcCountdown,
    getWeatherIcon: getWeatherIcon,
    getSuitability: getSuitability,
    getSevereAlert: getSevereAlert,
    normalizeFactors: normalizeFactors
  };

  if (typeof window !== 'undefined') window.TimelineLogic = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();