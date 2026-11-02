/**
 * 账号数据同步（T10 / V5 T26 前端侧）
 *
 * 我的作物 / 我的地块 / 设备 / 搜索历史原本只存在 localStorage，换设备就丢。
 * 这里在登录后做四件事：
 *   1. 拉取服务端数据，与本地按 updatedAt 合并后写回本地；
 *   2. 之后本地任何一次增删改，防抖 900ms 回写服务端；
 *   3. 合并时把被覆盖的版本记进冲突档案（agritime.sync.conflicts.v1），可导出；
 *   4. 同步失败时明确显示「当前为本地数据」，并每 30 秒重试，网络恢复也立即重试。
 *
 * 未登录时整段逻辑不启用，页面行为和以前完全一样。
 */
(function () {
  var API = '/api/me/data';
  var KEYS = {
    crops: 'agritime.my-crops.v1',
    plots: 'agritime.my-plots.v1',
    sensors: 'agritime.sensors.v1',
    history: 'agritime.history.v1'
  };
  var CONFLICT_KEY = 'agritime.sync.conflicts.v1';
  var CONFLICT_MAX = 50;
  var READ_LIMIT = 200;
  var HISTORY_MAX = 20;
  var DEBOUNCE_MS = 900;
  var RETRY_MS = 30000;

  var enabled = false;
  var timer = null;
  var retryTimer = null;
  var pushing = false;
  var again = false;
  var statusEl = null;

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

  // ---------------- 同步状态提示 ----------------

  function ensureStatusBar() {
    if (statusEl || !document.body) return statusEl;
    statusEl = document.createElement('div');
    statusEl.id = 'agri-sync-status';
    statusEl.className = 'agri-sync-status';
    statusEl.setAttribute('role', 'status');
    statusEl.hidden = true;
    document.body.appendChild(statusEl);
    return statusEl;
  }

  /**
   * state: guest（未登录，不提示）/ synced / local（拉取失败）/ error（回写失败）
   */
  function setStatus(state, text) {
    window.dispatchEvent(new CustomEvent('agritime:sync-status', {
      detail: { state: state, at: new Date().toISOString() }
    }));
    var el = ensureStatusBar();
    if (!el) return;
    if (state === 'synced' || state === 'guest') {
      el.hidden = true;
      return;
    }
    el.hidden = false;
    el.textContent = text || (state === 'error'
      ? '同步失败：当前为本地数据，网络恢复后会自动重试'
      : '当前为本地数据，尚未同步到账号');
  }

  // ---------------- 冲突留痕 ----------------

  /** 合并时同一条记录两边都有且版本不同，把被覆盖的那版记下来，供导出核对。 */
  function recordConflict(dataType, id, loser, winner, source) {
    try {
      var arr = [];
      try { arr = JSON.parse(localStorage.getItem(CONFLICT_KEY) || '[]'); } catch (e) { arr = []; }
      arr.unshift({
        dataType: dataType,
        id: id,
        loserUpdatedAt: String(loser.updatedAt || loser.createdAt || ''),
        winnerUpdatedAt: String(winner.updatedAt || winner.createdAt || ''),
        winner: source || 'remote',
        at: new Date().toISOString()
      });
      localStorage.setItem(CONFLICT_KEY, JSON.stringify(arr.slice(0, CONFLICT_MAX)));
    } catch (e) { /* 留痕失败不影响合并 */ }
  }

  // ---------------- 合并策略 ----------------

  /** 按主键合并：同一 id 取 updatedAt 更新的那条，旧版记入冲突档案 */
  function mergeById(local, remote, idField, dataType) {
    var map = {};
    var order = [];

    function put(rec, source) {
      if (!rec || !rec[idField]) return;
      var id = rec[idField];
      var cur = map[id];
      if (!cur) {
        map[id] = rec;
        order.push(id);
        return;
      }
      var recAt = String(rec.updatedAt || rec.createdAt || '');
      var curAt = String(cur.updatedAt || cur.createdAt || '');
      if (recAt > curAt) {
        recordConflict(dataType, id, cur, rec, source);
        map[id] = rec;
      } else if (curAt > recAt) {
        recordConflict(dataType, id, rec, cur, source === 'local' ? 'remote' : 'local');
      }
    }

    local.forEach(function (r) { put(r, 'local'); });
    remote.forEach(function (r) { put(r, 'remote'); });

    return order
      .map(function (id) { return map[id]; })
      .sort(function (a, b) {
        return String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || ''));
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

  /** 传感器以当前设备的配置为准（本地优先），读数按时间合并去重 */
  function mergeSensors(local, remote) {
    var map = {};
    var order = [];

    function put(rec) {
      if (!rec || !rec.sensorId) return;
      var id = rec.sensorId;
      if (!map[id]) {
        map[id] = Object.assign({}, rec, { readings: (rec.readings || []).slice() });
        order.push(id);
        return;
      }
      var base = map[id];
      map[id] = Object.assign({}, rec, base, {
        readings: mergeReadings(base.readings, rec.readings)
      });
    }

    local.forEach(put);
    remote.forEach(put);
    return order
      .map(function (id) { return map[id]; })
      .sort(function (a, b) {
        return String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
      });
  }

  function mergeReadings(a, b) {
    var seen = {};
    var out = [];
    (a || []).concat(b || []).forEach(function (r) {
      if (!r || !r.at) return;
      var k = String(r.at) + '|' + String(r.value);
      if (seen[k]) return;
      seen[k] = 1;
      out.push(r);
    });
    out.sort(function (x, y) { return String(x.at).localeCompare(String(y.at)); });
    return out.slice(-READ_LIMIT);
  }

  // ---------------- 拉取 / 回写 ----------------

  function pull() {
    return fetch(API, { method: 'GET' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        if (!d || !d.data) return;
        var remote = d.data;

        var crops = mergeById(readArr(KEYS.crops), remote.crops || [], 'recordId', 'crops');
        var plots = mergeById(readArr(KEYS.plots), remote.plots || [], 'plotId', 'plots');
        var sensors = mergeSensors(readArr(KEYS.sensors), remote.sensors || []);
        var history = mergeHistory(readArr(KEYS.history), remote.history || []);

        writeArr(KEYS.crops, crops);
        writeArr(KEYS.plots, plots);
        writeArr(KEYS.sensors, sensors);
        writeArr(KEYS.history, history);

        window.dispatchEvent(new CustomEvent('agritime:data-synced'));
        setStatus('synced');
      })
      .catch(function () {
        // 已登录但拉不到：明确告诉用户现在是本地数据，不是「假同步成功」
        setStatus('local');
      });
  }

  function snapshot() {
    return {
      crops: readArr(KEYS.crops),
      plots: readArr(KEYS.plots),
      sensors: readArr(KEYS.sensors),
      history: readArr(KEYS.history),
      updatedAt: new Date().toISOString()
    };
  }

  function push() {
    if (!enabled) return;
    if (pushing) { again = true; return; }
    pushing = true;
    clearTimeout(retryTimer);
    fetch(API, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(snapshot())
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        setStatus('synced');
      })
      .catch(function () {
        setStatus('error');
        retryTimer = setTimeout(push, RETRY_MS);
      })
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

  // ---------------- 挂到 store 上 ----------------

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
    hookStore('SensorsStore', ['add', 'remove', 'addReading']);
    hookStore('HistoryStore', ['add', 'remove', 'clear']);
  }

  function boot() {
    if (typeof fetch !== 'function') return;
    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        if (!d || !d.data || !d.data.user) {
          setStatus('guest');
          return;
        }
        enabled = true;
        hookStores();
        pull();
      })
      .catch(function () { /* 接口不可用就当没登录 */ });
  }

  // 网络恢复立即重试，不用等 30 秒
  window.addEventListener('online', function () {
    if (!enabled) return;
    pull();
    push();
  });

  window.AgriSync = {
    pull: pull,
    push: push,
    conflicts: function () { return readArr(CONFLICT_KEY); },
    isEnabled: function () { return enabled; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
