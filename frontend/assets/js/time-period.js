/**
 * 时段判定模块（T01 增强动画）
 * 6 时段：早晨/上午/中午/下午/晚上/深夜
 * 日出日落动态计算，降级使用默认时间。
 */
(function () {
  var DEFAULT_SUNRISE = 6 * 60;
  var DEFAULT_SUNSET = 18 * 60;

  function parseSunTime(str) {
    if (!str || typeof str !== 'string') return null;
    var parts = str.split('T');
    if (parts.length < 2) return null;
    var time = parts[1].split('+')[0].split('-')[0];
    var h = parseInt(time.split(':')[0], 10);
    var m = parseInt(time.split(':')[1], 10);
    if (isNaN(h) || isNaN(m)) return null;
    return h * 60 + m;
  }

  function judgeTimePeriod(now, sunrise, sunset) {
    var cur = now.getHours() * 60 + now.getMinutes();
    var rise = (sunrise != null) ? sunrise : DEFAULT_SUNRISE;
    var set = (sunset != null) ? sunset : DEFAULT_SUNSET;
    if (set <= rise) set = rise + 12 * 60;
    var afternoonEnd = set - 120;
    if (afternoonEnd < 14 * 60) afternoonEnd = 14 * 60 + 60;
    if (cur >= rise && cur < 8 * 60) return 'morning';
    if (cur >= 8 * 60 && cur < 11 * 60) return 'forenoon';
    if (cur >= 11 * 60 && cur < 14 * 60) return 'noon';
    if (cur >= 14 * 60 && cur < afternoonEnd) return 'afternoon';
    if (cur >= afternoonEnd && cur < 22 * 60) return 'evening';
    return 'night';
  }

  function applyTimePeriod(period) {
    var main = document.querySelector('.page--home');
    if (main) main.setAttribute('data-timeperiod', period || 'default');
    var tl = document.querySelector('.page--timeline');
    if (tl) tl.setAttribute('data-timeperiod', period || 'default');
  }

  function applySunData(sunrise, sunset) {
    if (typeof window !== 'undefined') {
      window.__sunData = { sunrise: sunrise, sunset: sunset };
    }
    var now = new Date();
    var period = judgeTimePeriod(now, sunrise, sunset);
    applyTimePeriod(period);
    return period;
  }

  function fallbackPeriod() {
    var now = new Date();
    var period = judgeTimePeriod(now, null, null);
    applyTimePeriod(period);
    return period;
  }

  var updateTimer = null;
  function init() {
    fallbackPeriod();
    if (updateTimer) clearInterval(updateTimer);
    updateTimer = setInterval(function () {
      var sd = typeof window !== 'undefined' ? window.__sunData : null;
      var p = sd ? judgeTimePeriod(new Date(), sd.sunrise, sd.sunset) : fallbackPeriod();
      applyTimePeriod(p);
    }, 60000);
  }

  if (typeof window !== 'undefined') {
    window.TimePeriod = { init: init, judgeTimePeriod: judgeTimePeriod, parseSunTime: parseSunTime, applyTimePeriod: applyTimePeriod, applySunData: applySunData, fallbackPeriod: fallbackPeriod };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { judgeTimePeriod: judgeTimePeriod, parseSunTime: parseSunTime };
  }
})();