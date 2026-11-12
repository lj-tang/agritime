/**
 * 二十四节气：日期推算 + 当前节气 + 农事提示。
 *
 * 为什么放前端算：首页顶部那条节气尺要在首屏就画出来，等接口会有空白期。
 *
 * 数据与 data/guidance/solar-term-farming.json 同源（名称 / 月份 / 世纪常数 / 农事提示）。
 * 两份数据不能各写各的：tests/pages.test.js 会逐条比对，只改一边会直接报红。
 *
 * 日期用通用公式 day = floor(Y*0.2422 + C) - floor(Y/4)，Y 取年份后两位、C 为 21 世纪常数。
 * 个别年份会有 ±1 天偏差，所以这里只用于「现在处在哪个节气」的展示，
 * 播期、霜期这些真正影响农事的计算仍然走服务端，不用这套近似值。
 */
(function () {
  'use strict';

  var TERMS = [
    { name: '小寒', month: 1, c: 5.4055, tip: '防寒防冻，检查大棚保温' },
    { name: '大寒', month: 1, c: 20.12, tip: '越冬作物防冻害，积肥备耕' },
    { name: '立春', month: 2, c: 3.87, tip: '春耕备耕，检修农机与种子' },
    { name: '雨水', month: 2, c: 18.73, tip: '返青水与追肥关键期，关注降雨' },
    { name: '惊蛰', month: 3, c: 5.63, tip: '病虫害始发，准备第一次防治' },
    { name: '春分', month: 3, c: 20.646, tip: '春播适宜期，注意倒春寒' },
    { name: '清明', month: 4, c: 4.81, tip: '播种移栽，清沟排水防涝' },
    { name: '谷雨', month: 4, c: 20.1, tip: '雨生百谷，肥水管理并防病害' },
    { name: '立夏', month: 5, c: 5.52, tip: '夏管开始，中耕除草' },
    { name: '小满', month: 5, c: 21.04, tip: '麦类是灌浆关键期，防干热风' },
    { name: '芒种', month: 6, c: 5.678, tip: '抢收抢种，农事最忙' },
    { name: '夏至', month: 6, c: 21.37, tip: '高温高湿，防治病虫害' },
    { name: '小暑', month: 7, c: 7.108, tip: '防涝抗旱并重，适时灌溉' },
    { name: '大暑', month: 7, c: 22.83, tip: '高温期避午间作业，防药害' },
    { name: '立秋', month: 8, c: 7.5, tip: '秋季田管，追肥促壮' },
    { name: '处暑', month: 8, c: 23.13, tip: '防早霜，抓好秋收准备' },
    { name: '白露', month: 9, c: 7.646, tip: '昼夜温差大，注意作物管护' },
    { name: '秋分', month: 9, c: 23.042, tip: '秋收秋种，抢时播种' },
    { name: '寒露', month: 10, c: 8.318, tip: '秋播作物保墒，防寒露风' },
    { name: '霜降', month: 10, c: 23.438, tip: '防初霜冻，及时采收' },
    { name: '立冬', month: 11, c: 7.438, tip: '冬前管理，浇灌越冬水' },
    { name: '小雪', month: 11, c: 22.36, tip: '大棚保温，畜禽防寒' },
    { name: '大雪', month: 12, c: 7.18, tip: '积雪保温，清雪护棚' },
    { name: '冬至', month: 12, c: 21.94, tip: '数九寒天，越冬作物防冻' }
  ];

  var DAY_MS = 86400000;

  function pad2(n) { return String(n).padStart(2, '0'); }

  function startOfDay(d) {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  function fmtMD(d) {
    return (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }

  /** 第 i 个节气在某一年的日期 */
  function termDate(year, i) {
    var t = TERMS[i];
    var y = year % 100;
    var day = Math.floor(y * 0.2422 + t.c) - Math.floor(y / 4);
    return new Date(year, t.month - 1, day);
  }

  /** 一年 24 个节气（按时间正序） */
  function yearTerms(year) {
    var out = [];
    for (var i = 0; i < TERMS.length; i += 1) {
      var d = termDate(year, i);
      var t = TERMS[i];
      out.push({
        index: i,
        name: t.name,
        tip: t.tip,
        year: d.getFullYear(),
        date: d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()),
        md: fmtMD(d),
        time: d.getTime()
      });
    }
    out.sort(function (a, b) { return a.time - b.time; });
    return out;
  }

  /**
   * 当前节气。跨年也算对：1 月 1~4 日的当前节气是上一年的冬至。
   * 返回的 all 是「今年」的 24 个刻度（首页那条尺子用它画）；
   * index 是当前节气在 all 里的下标，不在今年（年初那几天）时为 -1，尺子上就不点亮。
   *
   * since 是「已经进入当前节气第几天」：立冬 11月7日、今天 11月12日 → since = 5。
   * 首页顶部用 since 和 next.days 两头夹住今天，不再直接甩一个节气日期给用户去心算。
   */
  function current(now) {
    var today = startOfDay(now || new Date());
    var year = today.getFullYear();
    var span = yearTerms(year - 1).concat(yearTerms(year), yearTerms(year + 1));

    var at = 0;
    for (var i = 0; i < span.length; i += 1) {
      if (span[i].time <= today.getTime()) at = i;
    }
    var cur = span[at];
    var next = span[at + 1] || cur;

    var all = yearTerms(year);
    var index = -1;
    for (var j = 0; j < all.length; j += 1) {
      if (all[j].name === cur.name && all[j].year === cur.year) { index = j; break; }
    }

    return {
      index: index,
      name: cur.name,
      date: cur.date,
      md: cur.md,
      tip: cur.tip,
      since: Math.round((today.getTime() - cur.time) / DAY_MS),
      next: {
        name: next.name,
        date: next.date,
        md: next.md,
        days: Math.round((next.time - today.getTime()) / DAY_MS)
      },
      all: all
    };
  }

  var api = { TERMS: TERMS, termDate: termDate, yearTerms: yearTerms, current: current };

  if (typeof window !== 'undefined') window.SolarTerms = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();
