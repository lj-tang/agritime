/**
 * 我的作物存储模块（V4 T12）
 * 独立键 agritime.my-crops.v1，上限200条，localStorage 降级。
 */
(function () {
  var KEY = 'agritime.my-crops.v1';
  var MAX = 200;

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
    return 'mc_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8);
  }

  function add(record, store) {
    var s = store || getStore();
    if (!s) return null;
    try {
      var arr = list(s);
      var now = new Date().toISOString();
      var rec = Object.assign({}, record, {
        recordId: genId(),
        createdAt: now,
        updatedAt: now,
        photoHistory: record.photoHistory || []
      });
      arr.unshift(rec);
      if (arr.length > MAX) arr = arr.slice(0, MAX);
      save(arr, s);
      return rec;
    } catch (e) { return null; }
  }

  function update(recordId, patch, store) {
    var s = store || getStore();
    if (!s) return false;
    try {
      var arr = list(s);
      var found = false;
      arr = arr.map(function (r) {
        if (r.recordId === recordId) {
          found = true;
          return Object.assign({}, r, patch, { updatedAt: new Date().toISOString() });
        }
        return r;
      });
      if (found) save(arr, s);
      return found;
    } catch (e) { return false; }
  }

  function remove(recordId, store) {
    var s = store || getStore();
    if (!s) return false;
    try {
      var arr = list(s);
      var filtered = arr.filter(function (r) { return r.recordId !== recordId; });
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

  function getById(recordId, store) {
    var arr = list(store);
    return arr.filter(function (r) { return r.recordId === recordId; })[0] || null;
  }

  var api = { KEY: KEY, MAX: MAX, list: list, add: add, update: update, remove: remove, clear: clear, getById: getById };

  if (typeof window !== 'undefined') { window.MyCropsStore = api; }
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
})();