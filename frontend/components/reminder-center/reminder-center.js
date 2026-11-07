/**
 * 提醒中心（V5 T18）
 *
 * T04 的提醒只是 localStorage 里一个"这个节点我打过钩"的标记：
 * 换设备就没了，没有分类、等级、已读，更没有一条能照着做的建议。
 * 这里把它换成一份可查询的提醒档案：
 *   · 三类：灾害天气 / 病害风险 / 农事适宜窗口，三级风险，高风险置顶；
 *   · 每条附一条可执行建议，过期节点与已完成节点不产生提醒；
 *   · 三类开关、已读、删除都持久化 —— 登录存服务端，未登录降级本地并明说；
 *   · 从农事日历节点自动生成风险提醒，用户也能手动给节点设提醒。
 *
 * 组件自包含：自己挂铃铛入口、自己建面板，任何页面引一行 script 即可。
 */
(function () {
  'use strict';

  var KEY = 'agritime.reminders.center.v1';
  var LEGACY_KEY = 'agritime.reminders.v1';
  var API = '/api/reminders';

  var TYPES = {
    weather: { label: '灾害天气', icon: 'storm', hint: '高温、暴雨、大风等天气预警' },
    disease: { label: '病害风险', icon: 'germ', hint: '高湿或高温时易发的病害' },
    window: { label: '农事适宜窗口', icon: 'partly', hint: '适宜作业的节点与手动设的提醒' },
    sensor: { label: '设备告警', icon: 'thermo', hint: '溶氧、水温、氨氮等传感器超阈值' }
  };
  var LEVELS = {
    high: { label: '高风险', cls: 'rc-high' },
    medium: { label: '中风险', cls: 'rc-medium' },
    low: { label: '低风险', cls: 'rc-low' }
  };

  var state = {
    items: [],
    settings: { weather: true, disease: true, window: true, sensor: true },
    storage: 'local',
    loaded: false,
    entry: null,
    panel: null,
    panelBody: null,
    inlineHost: null
  };

  function todayStr() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function daysFromToday(dateStr) {
    if (!dateStr) return 9999;
    var t = new Date(todayStr() + 'T00:00:00');
    var d = new Date(dateStr + 'T00:00:00');
    if (isNaN(d.getTime())) return 9999;
    return Math.round((d - t) / 86400000);
  }

  function safeParse(raw, fallback) {
    try { var v = JSON.parse(raw); return v == null ? fallback : v; } catch (e) { return fallback; }
  }

  function readLocal() {
    var stored = null;
    try { stored = localStorage.getItem(KEY); } catch (e) { /* 隐私模式 */ }
    var data = stored ? safeParse(stored, null) : null;
    var items = data && Array.isArray(data.items) ? data.items : [];
    var settings = Object.assign({ weather: true, disease: true, window: true, sensor: true },
      data && data.settings ? data.settings : {});

    // 一次性迁移 T04 的单标记：老用户以前打过钩的节点不能丢
    try {
      var legacy = localStorage.getItem(LEGACY_KEY);
      if (legacy) {
        var old = safeParse(legacy, []);
        if (Array.isArray(old)) {
          old.forEach(function (r) {
            if (!r || !r.date) return;
            var id = 'manual|' + (r.crop || '') + '|' + r.date + '|' + (r.name || '');
            if (!items.some(function (x) { return x.id === id; })) {
              items.push({
                id: id, type: 'window', level: 'low',
                title: '节点提醒：' + (r.name || '农事节点'),
                detail: '由旧版「设提醒」迁移而来',
                advice: '按节点规程执行，临近前再刷新一次天气',
                date: r.date, crop: r.crop || '', name: r.name || '',
                nodeKey: r.key || '', source: 'legacy',
                read: false, createdAt: new Date().toISOString()
              });
            }
          });
        }
        localStorage.removeItem(LEGACY_KEY);
      }
    } catch (e) { /* 迁移失败不影响主流程 */ }

    // 本地也做一次过期清理，避免降级时越攒越多
    var today = todayStr();
    var cut = new Date();
    cut.setDate(cut.getDate() - 7);
    var cutStr = cut.getFullYear() + '-' + String(cut.getMonth() + 1).padStart(2, '0') + '-' + String(cut.getDate()).padStart(2, '0');
    items = items.filter(function (r) { return !r.date || r.date >= cutStr; });
    return { items: items, settings: settings, today: today };
  }

  function saveLocal() {
    try {
      localStorage.setItem(KEY, JSON.stringify({ items: state.items, settings: state.settings }));
    } catch (e) { /* 隐私模式忽略 */ }
  }

  function visible() {
    return state.items.filter(function (r) {
      return !r.type || state.settings[r.type] !== false;
    }).sort(function (a, b) {
      var rank = { high: 0, medium: 1, low: 2 };
      var ra = rank[a.level] == null ? 2 : rank[a.level];
      var rb = rank[b.level] == null ? 2 : rank[b.level];
      if (ra !== rb) return ra - rb;
      return String(a.date || '').localeCompare(String(b.date || ''));
    });
  }

  function unreadCount() {
    return visible().filter(function (r) { return !r.read; }).length;
  }

  function emit() {
    if (typeof document === 'undefined') return;
    document.dispatchEvent(new CustomEvent('agritime:reminders-changed', {
      detail: { unread: unreadCount(), storage: state.storage }
    }));
    renderEntry();
    if (state.panelBody && state.panel && !state.panel.hidden) renderPanel(state.panelBody);
    if (state.inlineHost) renderPanel(state.inlineHost);
  }

  function applyServer(data) {
    if (!data) return;
    if (Array.isArray(data.items)) state.items = data.items;
    if (data.settings) {
      state.settings = Object.assign({ weather: true, disease: true, window: true, sensor: true }, data.settings);
    }
    state.storage = 'server';
    saveLocal();
    emit();
  }

  /** 把本地变更推到服务端；服务不可用就保持本地并如实标注。 */
  function push(actions) {
    saveLocal();
    if (state.storage !== 'server') return Promise.resolve();
    var payload = { items: state.items, settings: state.settings };
    if (actions) payload.actions = actions;
    return fetch(API, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(function (r) { return r.ok ? r.json() : null; })
      .then(function (body) {
        if (body && body.code === 0 && body.data) applyServer(body.data);
      })
      .catch(function () {
        state.storage = 'local';
        emit();
      });
  }

  function load() {
    var local = readLocal();
    state.items = local.items;
    state.settings = local.settings;
    state.loaded = true;
    emit();

    return fetch(API, { credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) throw new Error('not-logged-in'); return r.json(); })
      .then(function (body) {
        var data = body && body.data;
        if (!data) throw new Error('bad-response');
        var serverItems = Array.isArray(data.items) ? data.items : [];
        // 首次登录：服务端还没有档案时，把本地已有提醒补上去，别让老数据凭空消失
        var hadLocal = state.items.length > 0;
        applyServer(data);
        if (hadLocal && serverItems.length === 0) return push();
        return state;
      })
      .catch(function () {
        state.storage = 'local';
        emit();
        return state;
      });
  }

  function normalize(item) {
    if (!item || !item.id || !item.title) return null;
    return {
      id: String(item.id).slice(0, 160),
      type: TYPES[item.type] ? item.type : 'window',
      level: LEVELS[item.level] ? item.level : 'low',
      title: String(item.title).slice(0, 200),
      detail: item.detail ? String(item.detail).slice(0, 400) : '',
      advice: item.advice ? String(item.advice).slice(0, 400) : '',
      date: item.date || '',
      crop: item.crop || '',
      plotId: item.plotId || '',
      name: item.name || '',
      nodeKey: item.nodeKey || '',
      source: item.source || 'manual',
      read: !!item.read,
      createdAt: item.createdAt || new Date().toISOString()
    };
  }

  function add(raw, silent) {
    var item = normalize(raw);
    if (!item) return false;
    var idx = -1;
    for (var i = 0; i < state.items.length; i++) {
      if (state.items[i].id === item.id) { idx = i; break; }
    }
    if (idx >= 0) {
      // 自动生成不能把用户已读的提醒又顶回未读
      var keepRead = state.items[idx].read;
      item.read = keepRead;
      item.createdAt = state.items[idx].createdAt || item.createdAt;
      state.items[idx] = item;
      if (!silent) { push(); emit(); }
      return false;
    }
    state.items.push(item);
    if (!silent) {
      push();
      emit(); // 本地降级时 push 不会触发服务端回调，必须自己重渲染
    }
    return true;
  }

  function remove(id) {
    var before = state.items.length;
    state.items = state.items.filter(function (r) { return r.id !== id; });
    if (state.items.length !== before) push({ removeIds: [id] });
    emit();
  }

  function markRead(id) {
    var hit = false;
    state.items.forEach(function (r) { if (r.id === id) { r.read = true; hit = true; } });
    if (hit) push({ readIds: [id] });
    emit();
  }

  function markAllRead() {
    state.items.forEach(function (r) { r.read = true; });
    push({ readAll: true });
    emit();
  }

  function setSetting(type, on) {
    if (!TYPES[type]) return;
    state.settings[type] = !!on;
    push();
    emit();
  }

  function has(id) {
    return state.items.some(function (r) { return r.id === id; });
  }

  function manualId(crop, date, name) {
    return 'manual|' + (crop || '') + '|' + (date || '') + '|' + (name || '');
  }

  function toggleManual(node, crop) {
    if (!node || !node.date) return;
    var id = manualId(crop, node.date, node.name);
    if (has(id)) { remove(id); return false; }
    add({
      id: id, type: 'window', level: 'low',
      title: '节点提醒：' + (node.name || '农事节点'),
      detail: '你在农事日历上给这个节点设了提醒',
      advice: node.copy || node.suggest || '按节点规程执行，临近前再刷新天气判断',
      date: node.date, crop: crop || '', name: node.name || '',
      nodeKey: node.nodeId || '', source: 'manual'
    });
    return true;
  }

  /** 从日历节点生成三类风险提醒。过期 / 已完成 / 只有常年概览的远期节点一律不生成。 */
  function generateFromTimeline(data) {
    if (!data || !Array.isArray(data.nodes)) return 0;
    var crop = data.cropName || data.requestedCrop || '';
    var count = 0;
    data.nodes.forEach(function (n) {
      if (!n || !n.date) return;
      var ahead = daysFromToday(n.date);
      if (ahead < 0) return;                                  // 过期节点
      if (n.status === 'done' || n.completed) return;           // 已完成节点
      if (n.precision === 'season' || n.certainty === 'planned') return; // 远期只有常年概览，不给某天预警
      var key = n.nodeId || n.operationType || '';

      // 1. 灾害天气：节点带天气警示，或状态直接判"不宜"
      var alert = (window.TimelineLogic && window.TimelineLogic.getSevereAlert)
        ? window.TimelineLogic.getSevereAlert(n.severeWeatherAlert) : null;
      if (alert || n.status === 'red') {
        var label = alert ? (alert.label || '极端天气') : '天气不宜作业';
        if (add({
          id: 'weather|' + n.date + '|' + key + '|' + (n.name || ''),
          type: 'weather', level: 'high',
          title: label + ' · ' + (n.name || '农事节点'),
          detail: '农事日历在 ' + n.date + ' 的节点判定为' + (n.status === 'red' ? '不宜作业' : '存在极端天气')
            + (n.copy ? '：' + n.copy : '') + '。',
          advice: alert && alert.tip ? alert.tip : '把这项作业顺延到天气转好后再做，不要抢在坏天气前硬干',
          date: n.date, crop: crop, name: n.name || '', nodeKey: String(key), source: 'calendar'
        }, true)) count++;
      }

      // 2. 病害风险：节点有病虫害条目，且窗口天气依据里出现降雨/高温/高湿
      var g = n.guidance;
      if (g && g.pest && g.pest.length) {
        var basis = (g.watering && g.watering.basis) ? g.watering.basis : '';
        var wet = /降雨|雨|湿度|高温|无雨且最高/.test(basis);
        if (wet) {
          var p = g.pest[0] || {};
          if (add({
            id: 'disease|' + n.date + '|' + key + '|' + (p.name || ''),
            type: 'disease', level: 'medium',
            title: '病害风险：' + (p.name || '常见病虫害'),
            detail: '节点窗口天气依据「' + basis + '」，这种天气容易诱发'
              + (p.name || '病害') + (p.symptom ? '（典型症状：' + p.symptom + '）' : '') + '。',
            advice: p.prevention || '提前巡查田间，发现初期病斑及时处理，避免雨后高温蔓延',
            date: n.date, crop: crop, name: p.name || '', nodeKey: String(key), source: 'calendar'
          }, true)) count++;
        }
      }

      // 3. 农事适宜窗口：7 天内判定适宜的节点
      if (n.status === 'green' && ahead <= 7) {
        if (add({
          id: 'window|' + n.date + '|' + key + '|' + (n.name || ''),
          type: 'window', level: 'low',
          title: '适宜窗口：' + (n.name || '农事节点'),
          detail: '未来 ' + ahead + ' 天内天气条件适宜，可安排「' + (n.name || '该作业') + '」。',
          advice: n.copy || n.suggest || '按节点规程准备农具与用量，作业当天再确认一次天气',
          date: n.date, crop: crop, name: n.name || '', nodeKey: String(key), source: 'calendar'
        }, true)) count++;
      }
    });
    // silent 模式逐条 add 不重渲染，这里统一推一次再刷一次界面
    if (count) push();
    emit();
    return count;
  }

  // ---------------- 渲染 ----------------

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  /** 图标统一走 SVG 库，失败回退空串（由 aria-label 兜底语义） */
  function ic(name) {
    return (window.Icons && window.Icons.svg) ? window.Icons.svg(name) : '';
  }
  function elIcon(cls, name) {
    var s = document.createElement('span');
    if (cls) s.className = cls;
    s.innerHTML = ic(name);
    return s;
  }

  function renderEntry() {
    if (!state.entry) return;
    var n = unreadCount();
    var badge = state.entry.querySelector('.rc-badge');
    if (badge) {
      badge.textContent = n > 99 ? '99+' : String(n);
      badge.hidden = n === 0;
    }
    state.entry.setAttribute('aria-label', n ? '提醒中心，' + n + ' 条未读' : '提醒中心，暂无未读');
    state.entry.title = state.storage === 'server'
      ? '提醒中心（已同步到账号）' : '提醒中心（当前为本地提醒，未登录或未同步）';
  }

  function renderSettings(host) {
    var box = el('div', 'rc-settings');
    var title = el('div', 'rc-settings-title', '提醒类型');
    box.appendChild(title);
    Object.keys(TYPES).forEach(function (type) {
      var label = el('label', 'rc-switch');
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = state.settings[type] !== false;
      cb.addEventListener('change', function () { setSetting(type, cb.checked); });
      label.appendChild(cb);
      label.appendChild(elIcon('rc-switch-icon', TYPES[type].icon));
      label.appendChild(el('span', 'rc-switch-label', TYPES[type].label));
      label.title = TYPES[type].hint;
      box.appendChild(label);
    });
    host.appendChild(box);

    var meta = el('p', 'rc-storage');
    meta.textContent = state.storage === 'server'
      ? '已同步到账号，换设备也能看到'
      : '当前为本地提醒（未登录或同步不可用），仅保存在这台设备';
    if (state.storage !== 'server') meta.classList.add('rc-storage--local');
    host.appendChild(meta);
  }

  function renderList(host) {
    var list = visible();
    if (!list.length) {
      var empty = el('div', 'rc-empty');
      empty.appendChild(elIcon('rc-empty-icon', 'bell'));
      empty.appendChild(el('p', 'rc-empty-title', '暂时没有提醒'));
      empty.appendChild(el('p', 'rc-empty-hint', '从农事日历生成风险提醒，或在节点上点「设提醒」'));
      host.appendChild(empty);
      return;
    }
    list.forEach(function (r) {
      var lv = LEVELS[r.level] || LEVELS.low;
      var tp = TYPES[r.type] || TYPES.window;
      var row = el('div', 'rc-item' + (r.read ? ' rc-item--read' : '')
        + (r.level === 'high' ? ' rc-item--high' : ''));
      row.appendChild(el('span', 'rc-level ' + lv.cls, lv.label));
      var head = el('div', 'rc-item-head');
      var typeSpan = el('span', 'rc-type');
      typeSpan.innerHTML = ic(tp.icon) + ' ' + tp.label;
      head.appendChild(typeSpan);
      head.appendChild(el('span', 'rc-date', r.date || '无日期'));
      // 保留 T04 的倒计时语义：临近的提醒要能一眼看出还有几天
      if (window.TimelineLogic && window.TimelineLogic.calcCountdown) {
        var cd = window.TimelineLogic.calcCountdown(r.date);
        head.appendChild(el('span', 'rc-count',
          cd.state === 'today' ? '今天' : cd.state === 'past' ? '已过' : '还有 ' + cd.days + ' 天'));
      }
      row.appendChild(head);
      row.appendChild(el('div', 'rc-item-title', r.title));
      if (r.detail) row.appendChild(el('p', 'rc-item-detail', r.detail));
      if (r.advice) row.appendChild(el('p', 'rc-item-advice', '建议：' + r.advice));

      var ops = el('div', 'rc-item-ops');
      if (!r.read) {
        var readBtn = el('button', 'rc-op', '标为已读');
        readBtn.type = 'button';
        readBtn.addEventListener('click', function () { markRead(r.id); });
        ops.appendChild(readBtn);
      }
      var delBtn = el('button', 'rc-op rc-op--del', '删除');
      delBtn.type = 'button';
      delBtn.addEventListener('click', function () { remove(r.id); });
      ops.appendChild(delBtn);
      row.appendChild(ops);
      host.appendChild(row);
    });
  }

  function renderPanel(host) {
    if (!host) return;
    host.innerHTML = '';
    var bar = el('div', 'rc-toolbar');
    bar.appendChild(el('span', 'rc-unread', '未读 ' + unreadCount() + ' 条'));
    var all = el('button', 'rc-tool-btn', '全部已读');
    all.type = 'button';
    all.disabled = unreadCount() === 0;
    all.addEventListener('click', function () { markAllRead(); });
    bar.appendChild(all);
    host.appendChild(bar);
    renderSettings(host);
    renderList(host);
  }

  function ensurePanel() {
    if (state.panel) return state.panel;
    var panel = el('div', 'rc-panel');
    panel.id = 'reminder-center-panel';
    panel.hidden = true;
    var head = el('div', 'rc-panel-head');
    head.appendChild(el('strong', '', '提醒中心'));
    var close = el('button', 'rc-close', '×');
    close.type = 'button';
    close.setAttribute('aria-label', '关闭提醒中心');
    close.addEventListener('click', function () { panel.hidden = true; });
    head.appendChild(close);
    panel.appendChild(head);
    var body = el('div', 'rc-body');
    panel.appendChild(body);
    document.body.appendChild(panel);
    state.panel = panel;
    state.panelBody = body;

    document.addEventListener('click', function (ev) {
      if (panel.hidden) return;
      if (panel.contains(ev.target)) return;
      if (state.entry && state.entry.contains(ev.target)) return;
      panel.hidden = true;
    });
    return panel;
  }

  function mountEntry(host) {
    if (state.entry) return state.entry;
    var parent = host || document.getElementById('sidebar-footer') || document.body;
    var btn = el('button', 'rc-entry');
    btn.type = 'button';
    btn.id = 'reminder-center-entry';
    btn.innerHTML = '<span class="rc-bell" aria-hidden="true">' + ic('bell') + '</span>'
      + '<span class="rc-badge" hidden>0</span>'
      + '<span class="rc-entry-label">提醒</span>';
    btn.addEventListener('click', function () {
      var panel = ensurePanel();
      panel.hidden = !panel.hidden;
      if (!panel.hidden) renderPanel(state.panelBody);
    });
    if (parent.firstChild) parent.insertBefore(btn, parent.firstChild);
    else parent.appendChild(btn);
    state.entry = btn;
    renderEntry();
    return btn;
  }

  /** 供 timeline.html 的内嵌「我的农事提醒」面板复用。 */
  function renderInto(host) {
    state.inlineHost = host;
    renderPanel(host);
  }

  /** 页面要等档案加载完再渲染节点，否则「已设提醒」会先按空数据画一遍。 */
  function ready() {
    if (!state.readyPromise) state.readyPromise = load();
    return state.readyPromise;
  }

  function init() {
    // 时间线页有自己的内嵌提醒面板，不再挂一个浮在 iframe 里的铃铛
    if (!window.__reminderCenterNoEntry) mountEntry();
  }

  var api = {
    KEY: KEY, TYPES: TYPES, LEVELS: LEVELS,
    init: init, load: load, ready: ready, mountEntry: mountEntry, renderInto: renderInto,
    list: visible, unreadCount: unreadCount, storage: function () { return state.storage; },
    add: add, remove: remove, markRead: markRead, markAllRead: markAllRead,
    setSetting: setSetting, has: has, manualId: manualId, toggleManual: toggleManual,
    generateFromTimeline: generateFromTimeline
  };

  if (typeof window !== 'undefined') window.ReminderCenter = api;
  if (typeof document !== 'undefined') {
    // 立即开始拉取档案，不等 DOMContentLoaded：时间线页要用 ready() 等它完成，
    // 否则先画一遍空状态、随后服务端数据回来又覆盖，节点设提醒会闪一下。
    state.readyPromise = load();
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', init);
    } else {
      init();
    }
  }
})();
