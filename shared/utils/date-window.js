/**
 * 日期窗口（可选范围）—— 多端共享逻辑（T00 / shared/utils）
 *
 * 农事日历的可选日期范围有多处要用：日期轮盘、手动输入校验、年份翻页、
 * 以及后续的提醒倒计时。原先是各页面各写一份，容易漂移，
 * 这里抽成纯函数，前后端约定一致的时间窗口规则。
 *
 * 约定：过去 N 年 ~ 未来 M 天，两端都含当天。
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.DateWindow = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  /** 去掉时分秒，只留日期（避免 23:59 与 00:00 比较出错） */
  function startOfDay(d) {
    var x = new Date(d.getTime());
    x.setHours(0, 0, 0, 0);
    return x;
  }

  function addDays(d, n) {
    var x = new Date(d.getTime());
    x.setDate(x.getDate() + n);
    return x;
  }

  function addYears(d, n) {
    var x = new Date(d.getTime());
    x.setFullYear(x.getFullYear() + n);
    return x;
  }

  /** 本地时区的 yyyy-MM-dd，不能用 toISOString（会按 UTC 偏移串日期） */
  function toISO(d) {
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return d.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day);
  }

  /** 解析 yyyy-MM-dd，非法返回 null */
  function parseISO(s) {
    if (!s || typeof s !== 'string') return null;
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s.trim());
    if (!m) return null;
    var y = +m[1], mo = +m[2], da = +m[3];
    if (mo < 1 || mo > 12 || da < 1 || da > 31) return null;
    var d = new Date(y, mo - 1, da);
    if (d.getFullYear() !== y || d.getMonth() !== mo - 1 || d.getDate() !== da) return null;
    return d;
  }

  /**
   * 计算可选窗口
   * @param {Date} today 基准日（一般传 new Date()）
   * @param {number} pastYears 往前几年
   * @param {number} futureDays 往后几天
   * @returns {{min: Date, max: Date, minISO: string, maxISO: string}}
   */
  function windowFor(today, pastYears, futureDays) {
    var base = startOfDay(today || new Date());
    var min = addYears(base, -(pastYears || 0));
    var max = addDays(base, futureDays || 0);
    return { min: min, max: max, minISO: toISO(min), maxISO: toISO(max) };
  }

  /** 是否在窗口内（含两端）；input 可为 Date 或 yyyy-MM-dd */
  function within(value, win) {
    var d = value instanceof Date ? startOfDay(value) : parseISO(value);
    if (!d || !win) return false;
    return d.getTime() >= startOfDay(win.min).getTime() && d.getTime() <= startOfDay(win.max).getTime();
  }

  /** 两个日期相差的天数（b - a），按自然日算 */
  function daysBetween(a, b) {
    var x = startOfDay(a instanceof Date ? a : parseISO(a));
    var y = startOfDay(b instanceof Date ? b : parseISO(b));
    if (!x || !y) return NaN;
    return Math.round((y.getTime() - x.getTime()) / 86400000);
  }

  /** 是否超出预报可信期：距今天第 N 天起（含第 N 天）标「仅供参考」 */
  function needsForecastNote(date, today, fromDay) {
    var n = daysBetween(today || new Date(), date);
    if (isNaN(n)) return false;
    return n >= (fromDay == null ? 5 : fromDay);
  }

  return {
    startOfDay: startOfDay,
    addDays: addDays,
    addYears: addYears,
    toISO: toISO,
    parseISO: parseISO,
    windowFor: windowFor,
    within: within,
    daysBetween: daysBetween,
    needsForecastNote: needsForecastNote
  };
});
