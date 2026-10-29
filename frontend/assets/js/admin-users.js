/**
 * 超管后台 · 账号管理（T10）
 *
 * 权限判断交给后端：/api/admin/* 未登录回 401、非管理员回 403，
 * 前端只负责把这两种情况翻译成「去登录」和「没权限」，不在前端藏数据。
 */
(function () {
  var gateEl, gateTextEl, contentEl, statsEl, countEl, tbodyEl, emptyEl;

  function init() {
    gateEl = document.getElementById('admin-gate');
    gateTextEl = document.getElementById('admin-gate-text');
    contentEl = document.getElementById('admin-content');
    statsEl = document.getElementById('admin-stats');
    countEl = document.getElementById('admin-count');
    tbodyEl = document.getElementById('admin-users-body');
    emptyEl = document.getElementById('admin-empty');
    if (!contentEl) return;

    load();
  }

  function load() {
    fetch('/api/admin/stats')
      .then(function (res) {
        if (res.status === 401) return { gate: '请用超管账号登录后查看。' };
        if (res.status === 403) return { gate: '当前账号不是管理员，无法进入后台。' };
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json().then(function (d) { return { stats: d.data }; });
      })
      .then(function (r) {
        if (r.gate) { showGate(r.gate); return; }
        renderStats(r.stats);
        contentEl.hidden = false;
        loadUsers();
      })
      .catch(function () {
        showGate('后台数据加载失败，请稍后刷新重试。');
      });
  }

  function loadUsers() {
    fetch('/api/admin/users')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (d) {
        var items = (d.data && d.data.items) || [];
        countEl.textContent = '共 ' + items.length + ' 个';
        tbodyEl.innerHTML = '';
        if (!items.length) {
          emptyEl.hidden = false;
          return;
        }
        emptyEl.hidden = true;
        items.forEach(function (u) { tbodyEl.appendChild(row(u)); });
      })
      .catch(function () {
        countEl.textContent = '加载失败';
      });
  }

  function renderStats(s) {
    var cards = [
      { num: s.total, label: '账号总数' },
      { num: s.newToday, label: '今日新增' },
      { num: s.active7d, label: '近 7 天登录' },
      { num: s.adminCount, label: '管理员' }
    ];
    statsEl.innerHTML = '';
    cards.forEach(function (c) {
      var box = document.createElement('div');
      box.className = 'admin-stat';
      var n = document.createElement('div');
      n.className = 'admin-stat-num';
      n.textContent = c.num == null ? '--' : String(c.num);
      var l = document.createElement('div');
      l.className = 'admin-stat-label';
      l.textContent = c.label;
      box.appendChild(n);
      box.appendChild(l);
      statsEl.appendChild(box);
    });
  }

  function row(u) {
    var tr = document.createElement('tr');
    tr.appendChild(cell(u.username));
    tr.appendChild(cell(u.nickname || '—'));

    var roleTd = document.createElement('td');
    var badge = document.createElement('span');
    badge.className = 'admin-role' + (u.role === 'admin' ? ' is-admin' : '');
    badge.textContent = u.role === 'admin' ? '管理员' : '普通用户';
    roleTd.appendChild(badge);
    tr.appendChild(roleTd);

    tr.appendChild(cell(u.createdAt || '—'));
    tr.appendChild(cell(u.lastLoginAt || '从未登录'));
    tr.appendChild(cell(String(u.loginCount || 0)));
    return tr;
  }

  function cell(text) {
    var td = document.createElement('td');
    td.textContent = text;
    return td;
  }

  function showGate(msg) {
    gateTextEl.textContent = msg;
    gateEl.hidden = false;
    contentEl.hidden = true;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
