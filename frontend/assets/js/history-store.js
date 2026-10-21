/**
 * 历史记录存储模块（history-store）
 * 仅存非敏感字段（作物名/播种日/所在地），localStorage 不可用时静默降级。
 * 支持注入存储对象（便于测试），浏览器 + Node 双导出。
 */
(function () {
  var KEY = 'agritime.history.v1';
  var MAX = 5;

  function getStore() {
    try {
      if (typeof localStorage !== 'undefined' && localStorage) return localStorage;
    } catch (e) { /* 隐私模式等场景访问抛错 */ }
    return null;
  }

  function safeParse(raw) {
    if (!raw) return [];
    try {
      var arr = JSON.parse(raw);
      if (!Array.isArray(arr)) return [];
      return arr.filter(function (r) { return r && typeof r === 'object'; });
    } catch (e) {
      return [];
    }
  }

  function trim(v) {
    return typeof v === 'string' ? v.trim() : '';
  }

  /**
   * 读取历史记录（最近优先），最多返回 MAX 条。
   * @param {Storage} [store] 可注入的存储对象
   */
  function list(store) {
    var s = store || getStore();
    if (!s) return [];
    try {
      return safeParse(s.getItem(KEY)).slice(0, MAX);
    } catch (e) {
      return [];
    }
  }

  /**
   * 写入一条历史：按 cropName+sowDate+location 去重、头插、截断 MAX 条。
   * @param {{cropName?:string, sowDate?:string, location?:string}} record
   * @param {Storage} [store]
   */
  function add(record, store) {
    var s = store || getStore();
    if (!s || !record) return list(s);
    var entry = {
      cropName: trim(record.cropName),
      sowDate: trim(record.sowDate),
      location: trim(record.location)
    };
    if (!entry.cropName && !entry.sowDate && !entry.location) return list(s);
    try {
      var arr = list(s).filter(function (r) {
        return !(r.cropName === entry.cropName && r.sowDate === entry.sowDate && r.location === entry.location);
      });
      arr.unshift(entry);
      arr = arr.slice(0, MAX);
      s.setItem(KEY, JSON.stringify(arr));
      return arr;
    } catch (e) {
      return list(s);
    }
  }

  /**
   * 清空历史记录。
   * @param {Storage} [store]
   */
  function clear(store) {
    var s = store || getStore();
    if (!s) return;
    try { s.removeItem(KEY); } catch (e) { /* 忽略 */ }
  }

  var api = { KEY: KEY, MAX: MAX, list: list, add: add, clear: clear };

  if (typeof window !== 'undefined') window.HistoryStore = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();