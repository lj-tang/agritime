/**
 * 账号数据同步（T10）
 *
 * 我的作物 / 我的地块 / 搜索历史原本只存在 localStorage，换设备就丢。
 * 这里在登录后做三件事：
 *   1. 拉取服务端数据，与本地按 updatedAt 合并后写回本地；
 *   2. 之后本地任何一次增删改，防抖 900ms 回写服务端；
 *   3. 合并完广播 agritime:data-synced，让列表重渲染。
 *
 * 未登录时整段逻辑不启用，页面行为和以前完全一样。
 */
(function () {
  var API = '/api/me/data';
  var KEYS = {
    crops: 'agritime.my-crops.v1',
    plots: 'agritime.my-plots.v1',
    history: 'agritime.history.v1'
  };
  var HISTORY_MAX = 20;
  var DEBOUNCE_MS = 900;

  var enabled = false;
  var timer = null;
  var pushing = false;
  var again = false;

  function readArr(key) {
    try {
      var raw = localStorage.getItem(key);
      var v = raw ? JSON.parse(raw) : [];
      return Array.isArray(v) ? v : [];
    } catch (e) {
      return [];
    }
  }

  function writeArr(key, arr) {
    try {
      localStorage.setItem(key, JSON.stringify(arr));
    } catch (e) {
      /* 配额满了就放弃本地落盘，服务端仍有一份 */
    }
  }

  // ---------------- 合并策略 ----------------

  /** 按主键合并：同一 id 取 updatedAt 更新的那条 */
  function mergeById(local, remote, idField) {
    var map = {};
    var order = [];

    function put(rec) {
      if (!rec || !rec[idField]) return;
      var id = rec[idField];
      var cur = map[id];
      if (!cur) {
        map[id] = rec;
        order.push(id);
        return;
      }
      if (String(rec.updatedAt || '') > String(cur.updatedAt || '')) map[id] = rec;
    }

    local.forEach(put);
    remote.forEach(put);

    return order
      .map(function (id) { return map[id]; })
      .sort(function (a, b) {
        return String(b.updatedAt || '').localeCompare(String(a.updatedAt || ''));
      });
  }

  /** 历史没有时间戳，按「作物+日期+地点」去重，本地在前，截断到 20 条 */
  function mergeHistory(local, remote) {
    var seen = {};
    var out = [];
    local.concat(remote).forEach(function (r) {
      if (!r) return;
      var k = (r.cropName || '') + '|' + (r.sowDate || '') + '|' + (r.location || '');
      if (seen[k]) return;
      seen[k] = 1;
      out.push(r);
    });
    return out.slice(0, HISTORY_MAX);
  }

  // ---------------- 拉取 / 回写 ----------------

  function pull() {
    return fetch(API, { method: 'GET' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        if (!d || !d.data) return;
        var remote = d.data;

        var crops = mergeById(readArr(KEYS.crops), remote.crops || [], 'recordId');
        var plots = mergeById(readArr(KEYS.plots), remote.plots || [], 'plotId');
        var history = mergeHistory(readArr(KEYS.history), remote.history || []);

        writeArr(KEYS.crops, crops);
        writeArr(KEYS.plots, plots);
        writeArr(KEYS.history, history);

        window.dispatchEvent(new CustomEvent('agritime:data-synced'));
      })
      .catch(function () { /* 离线时按纯本地用 */ });
  }

  function snapshot() {
    return {
      crops: readArr(KEYS.crops),
      plots: readArr(KEYS.plots),
      history: readArr(KEYS.history),
      updatedAt: new Date().toISOString()
    };
  }

  function push() {
    if (!enabled) return;
    if (pushing) { again = true; return; }
    pushing = true;
    fetch(API, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(snapshot())
    })
      .catch(function () { /* 下次变更会再试 */ })
      .then(function () {
        pushing = false;
        if (again) { again = false; schedule(); }
      });
  }

  function schedule() {
    if (!enabled) return;
    clearTimeout(timer);
    timer = setTimeout(push, DEBOUNCE_MS);
  }

  // ---------------- 挂到三个 store 上 ----------------

  /** 包一层写操作：调用完就排队回写，不改 store 自身实现 */
  function hookStore(name, methods) {
    var store = window[name];
    if (!store) return;
    methods.forEach(function (m) {
      var orig = store[m];
      if (typeof orig !== 'function' || orig.__synced) return;
      var wrapped = function () {
        var r = orig.apply(this, arguments);
        schedule();
        return r;
      };
      wrapped.__synced = true;
      store[m] = wrapped;
    });
  }

  function hookStores() {
    hookStore('MyCropsStore', ['add', 'update', 'remove', 'clear']);
    hookStore('MyPlotsStore', ['add', 'update', 'remove', 'clear']);
    hookStore('HistoryStore', ['add', 'remove', 'clear']);
  }

  function boot() {
    if (typeof fetch !== 'function') return;
    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        if (!d || !d.data || !d.data.user) return;   // 未登录：纯本地模式
        enabled = true;
        hookStores();
        pull();
      })
      .catch(function () { /* 接口不可用就当没登录 */ });
  }

  window.AgriSync = {
    pull: pull,
    push: push,
    isEnabled: function () { return enabled; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
