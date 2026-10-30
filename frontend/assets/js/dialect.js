/**
 * 方言词表纠错（T08）。
 *
 * 能力边界先讲清楚：浏览器 Web Speech API 只有普通话声学模型，
 * 没有方言模型，所以这里做的是**文本纠错**——把识别出来的方言词
 * 映射回标准作物名，不承诺提高方言语音的识别率。
 *
 * 用法：
 *   Dialect.load().then(() => Dialect.correct(text, '山东省济宁市'));
 *   => { text: '番茄', corrections: [{from:'洋柿子', to:'番茄'}], region: '中原官话' }
 */
(function () {
  'use strict';

  var URL_MAP = '/data/voice/dialect-map.json';
  var cache = null;
  var loading = null;

  function fetchMap() {
    if (cache) return Promise.resolve(cache);
    if (loading) return loading;
    loading = fetch(URL_MAP, { cache: 'no-store' })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        cache = data && Array.isArray(data.regions) ? data : { regions: [] };
        return cache;
      })
      .catch(function () {
        cache = { regions: [] };
        return cache;
      });
    return loading;
  }

  function regionOf(map, provinceOrAddress) {
    var addr = provinceOrAddress || '';
    for (var i = 0; i < map.regions.length; i++) {
      var r = map.regions[i];
      var provinces = r.provinces || [];
      for (var j = 0; j < provinces.length; j++) {
        if (addr.indexOf(provinces[j]) !== -1) return r;
      }
    }
    return null;
  }

  // 长词优先，避免"洋柿子"被"柿子"先替换掉
  function sortedWords(region) {
    return (region.words || []).slice().sort(function (a, b) {
      return (b.from || '').length - (a.from || '').length;
    });
  }

  function correct(text, address) {
    var map = cache || { regions: [] };
    var out = { text: text || '', corrections: [], region: null, matched: false };
    if (!out.text) return out;

    var region = regionOf(map, address);
    // 没识别出方言区时，用全部词表兜底（词条本身就是方言写法，误伤风险低）
    var words = region ? sortedWords(region) : (function () {
      var all = [];
      map.regions.forEach(function (r) { all = all.concat(sortedWords(r)); });
      all.sort(function (a, b) { return (b.from || '').length - (a.from || '').length; });
      return all;
    })();

    out.region = region ? region.name : null;

    var working = out.text;
    words.forEach(function (w) {
      if (!w.from || !w.to || w.from === w.to) return;
      if (working.indexOf(w.from) === -1) return;
      // 已经是标准名（如"番茄"）不要再被替换成自己
      working = working.split(w.from).join(w.to);
      out.corrections.push({ from: w.from, to: w.to });
    });

    out.text = working;
    out.matched = out.corrections.length > 0;
    return out;
  }

  window.Dialect = {
    load: fetchMap,
    correct: correct,
    regionOf: function (address) { return regionOf(cache || { regions: [] }, address); }
  };
})();
