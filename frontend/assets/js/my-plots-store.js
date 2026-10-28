/**
 * 我的地块存储模块（T06）
 * 独立键 agritime.my-plots.v1，上限100条，localStorage 降级。
 */
(function () {
  var KEY = 'agritime.my-plots.v1';
  var MAX = 100;

  function getStore() {
    try { return typeof localStorage !== 'undefined' ? localStorage : null; }
    catch (e) { return null; }
  }

  function list(store) {
    var s = store || getStore();
    if (!s) return [];
    try {
      var raw = s.getItem(KEY);
      if (!raw) return [];
      var arr = JSON.parse(raw);
      return Array.isArray(arr) ? arr : [];
    } catch (e) { return []; }
  }

  function save(arr, store) {
    var s = store || getStore();
    if (!s) return false;
    try {
      s.setItem(KEY, JSON.stringify(arr));
      return true;
    } catch (e) { return false; }
  }

  function genId() {
    return 'plot_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8);
  }

  function add(record, store) {
    var s = store || getStore();
    if (!s) return null;
    try {
      var arr = list(s);
      var now = new Date().toISOString();
      var rec = Object.assign({}, record, {
        plotId: genId(),
        createdAt: now,
        updatedAt: now
      });
      arr.unshift(rec);
      if (arr.length > MAX) arr = arr.slice(0, MAX);
      save(arr, s);
      return rec;
    } catch (e) { return null; }
  }

  function update(plotId, patch, store) {
    var s = store || getStore();
    if (!s) return false;
    try {
      var arr = list(s);
      var found = false;
      arr = arr.map(function (r) {
        if (r.plotId === plotId) {
          found = true;
          return Object.assign({}, r, patch, { updatedAt: new Date().toISOString() });
        }
        return r;
      });
      if (found) save(arr, s);
      return found;
    } catch (e) { return false; }
  }

  function remove(plotId, store) {
    var s = store || getStore();
    if (!s) return false;
    try {
      var arr = list(s);
      var filtered = arr.filter(function (r) { return r.plotId !== plotId; });
      if (filtered.length === arr.length) return false;
      save(filtered, s);
      return true;
    } catch (e) { return false; }
  }

  function clear(store) {
    var s = store || getStore();
    if (!s) return;
    try { s.removeItem(KEY); } catch (e) {}
  }

  function getById(plotId, store) {
    var arr = list(store);
    return arr.filter(function (r) { return r.plotId === plotId; })[0] || null;
  }

  var api = { KEY: KEY, MAX: MAX, list: list, add: add, update: update, remove: remove, clear: clear, getById: getById };

  if (typeof window !== 'undefined') { window.MyPlotsStore = api; }
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
})();