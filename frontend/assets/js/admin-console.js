/**
 * 超管后台（T10）
 *
 * 四个页签：概览 / 账号 / 文件 / 开关。
 * 所有请求都打到 /api/admin/*，权限由服务端判定：
 * 401 提示去登录，403 提示不是管理员 —— 前端不自己判断角色，藏入口只是为了少点误点。
 */
(function () {
  var API = '/api/admin';

  var gateEl, gateTextEl, contentEl, whoEl;
  var statsEl, usersBody, usersCount, usersEmpty;
  var fsBody, fsEmpty, fsRootEl, crumbsEl;
  var editorEl, editorTitle, editorText, editorMsg;
  var swSearch, swLlm, swMsg;

  var currentPath = '/';
  var meUser = null;
  var auditEl, auditEmpty;
  var editingPath = null;
  var busy = false;

  // ---------------- 通用 ----------------

  function get(url) {
    return fetch(url).then(handle);
  }

  function send(url, method, body) {
    var opts = { method: method, headers: {} };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = typeof body === 'string' ? body : JSON.stringify(body);
    }
    return fetch(url, opts).then(handle);
  }

  function handle(res) {
    if (res.status === 401) return { gate: '请用超管账号登录后查看。' };
    if (res.status === 403) return { gate: '当前账号不是管理员，无法进入后台。' };
    if (res.status === 429) {
      return Promise.reject(new Error('请求过于频繁，请等几秒后刷新页面'));
    }
    return res.json().catch(function () { return {}; }).then(function (d) {
      if (!res.ok) throw new Error((d && d.message) || ('HTTP ' + res.status));
      return d;
    });
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  // ---------------- 初始化 ----------------

  function init() {
    gateEl = document.getElementById('admin-gate');
    gateTextEl = document.getElementById('admin-gate-text');
    contentEl = document.getElementById('admin-content');
    whoEl = document.getElementById('admin-who');
    if (!contentEl) return;

    statsEl = document.getElementById('admin-stats');
    usersBody = document.getElementById('admin-users-body');
    usersCount = document.getElementById('admin-count');
    usersEmpty = document.getElementById('admin-empty');
    fsBody = document.getElementById('admin-fs-body');
    fsEmpty = document.getElementById('admin-fs-empty');
    fsRootEl = document.getElementById('admin-fs-root');
    crumbsEl = document.getElementById('admin-crumbs');
    editorEl = document.getElementById('admin-editor');
    editorTitle = document.getElementById('editor-title');
    editorText = document.getElementById('editor-text');
    editorMsg = document.getElementById('editor-msg');
    auditEl = document.getElementById('admin-audit');
    auditEmpty = document.getElementById('admin-audit-empty');
    swSearch = document.getElementById('sw-search');
    swLlm = document.getElementById('sw-llm');
    swMsg = document.getElementById('sw-msg');

    document.querySelectorAll('.admin-tab').forEach(function (tab) {
      tab.addEventListener('click', function () { switchTab(tab.getAttribute('data-tab')); });
    });

    document.getElementById('fs-new-file').addEventListener('click', function () { createEntry(false); });
    document.getElementById('fs-new-dir').addEventListener('click', function () { createEntry(true); });
    document.getElementById('fs-refresh').addEventListener('click', loadFiles);
    document.getElementById('sw-save').addEventListener('click', saveSettings);
    document.getElementById('audit-refresh').addEventListener('click', loadAudit);

    document.getElementById('editor-close').addEventListener('click', closeEditor);
    document.getElementById('editor-cancel').addEventListener('click', closeEditor);
    document.getElementById('editor-save').addEventListener('click', saveFile);

    boot();
  }

  function boot() {
    // 先确认是谁，再拉列表：否则「当前登录」标记和危险按钮的隐藏都无从判断
    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        var u = d && d.data && d.data.user;
        if (u) {
          meUser = u;
          whoEl.textContent = (u.nickname || u.username) + '（管理员）';
        }
      })
      .catch(function () { /* 认人失败不影响后续，服务端仍会校验权限 */ })
      .then(function () { return get(API + '/stats'); })
      .then(function (r) {
        if (r.gate) { showGate(r.gate); return; }
        renderStats(r.data);
        contentEl.hidden = false;
        loadUsers();
        loadFiles();
        loadSettings();
      })
      .catch(function (e) { showGate('后台数据加载失败：' + e.message); });
  }

  function showGate(msg) {
    gateTextEl.textContent = msg;
    gateEl.hidden = false;
    contentEl.hidden = true;
  }

  function switchTab(name) {
    document.querySelectorAll('.admin-tab').forEach(function (t) {
      t.classList.toggle('is-active', t.getAttribute('data-tab') === name);
    });
    document.querySelectorAll('.admin-panel-wrap').forEach(function (p) {
      p.hidden = p.getAttribute('data-panel') !== name;
    });
    if (name === 'audit') loadAudit();
  }

  // ---------------- 概览 ----------------

  function renderStats(s) {
    var cards = [
      { num: s.total, label: '账号总数' },
      { num: s.newToday, label: '今日新增' },
      { num: s.active7d, label: '近 7 天登录' },
      { num: s.adminCount, label: '管理员' },
      { num: s.bannedCount, label: '已封禁' }
    ];
    statsEl.innerHTML = '';
    cards.forEach(function (c) {
      var box = el('div', 'admin-stat');
      box.appendChild(el('div', 'admin-stat-num', c.num == null ? '--' : String(c.num)));
      box.appendChild(el('div', 'admin-stat-label', c.label));
      statsEl.appendChild(box);
    });
  }

  // ---------------- 账号 ----------------

  function loadUsers() {
    get(API + '/users')
      .then(function (d) {
        var items = (d.data && d.data.items) || [];
        usersCount.textContent = '共 ' + items.length + ' 个';
        usersBody.innerHTML = '';
        usersEmpty.hidden = items.length > 0;
        items.forEach(function (u) { usersBody.appendChild(userRow(u)); });
      })
      .catch(function () { usersCount.textContent = '加载失败'; });
  }

  function userRow(u) {
    var tr = el('tr');
    tr.appendChild(el('td', null, u.username));
    tr.appendChild(el('td', null, u.nickname || '—'));

    var roleTd = el('td');
    roleTd.appendChild(el('span', 'admin-role' + (u.role === 'admin' ? ' is-admin' : ''),
      u.role === 'admin' ? '管理员' : '普通用户'));
    tr.appendChild(roleTd);

    var stTd = el('td');
    stTd.appendChild(el('span', 'admin-role' + (u.banned ? ' is-banned' : ''), u.banned ? '已封禁' : '正常'));
    tr.appendChild(stTd);

    tr.appendChild(el('td', null, u.createdAt || '—'));
    tr.appendChild(el('td', null, u.lastLoginAt || '从未登录'));
    tr.appendChild(el('td', null, String(u.loginCount || 0)));

    var isSelf = !!(meUser && meUser.id === u.id);
    var opTd = el('td', 'admin-ops');

    if (u.role !== 'admin') {
      opTd.appendChild(miniBtn(u.banned ? '解封' : '封号', u.banned ? '' : 'is-danger',
        function () { toggleBan(u); }));
    }
    if (!isSelf) {
      opTd.appendChild(miniBtn(u.role === 'admin' ? '取消管理员' : '设为管理员', '',
        function () { toggleRole(u); }));
    }
    opTd.appendChild(miniBtn('重置密码', '', function () { resetPwd(u); }));
    if (u.role !== 'admin' && !isSelf) {
      opTd.appendChild(miniBtn('删除', 'is-danger', function () { removeUser(u); }));
    }
    if (isSelf) opTd.appendChild(el('span', 'admin-muted', '当前登录'));

    tr.appendChild(opTd);
    return tr;
  }

  function toggleBan(u) {
    if (busy) return;
    var next = !u.banned;
    if (next && !confirm('确定封禁「' + u.username + '」？封禁后该账号立即无法登录。')) return;
    busy = true;
    send(API + '/users/ban', 'POST', { id: u.id, banned: next })
      .then(function () { loadUsers(); loadStats(); })
      .catch(function (e) { alert('操作失败：' + e.message); })
      .then(function () { busy = false; });
  }

  function miniBtn(text, extraCls, onClick) {
    var b = el('button', 'admin-mini' + (extraCls ? ' ' + extraCls : ''), text);
    b.addEventListener('click', onClick);
    return b;
  }

  /** 升 / 降管理员 */
  function toggleRole(u) {
    if (busy) return;
    var next = u.role === 'admin' ? 'user' : 'admin';
    var tip = next === 'admin'
      ? '把「' + u.username + '」设为管理员？他将能进入后台并管理所有账号。'
      : '取消「' + u.username + '」的管理员身份？';
    if (!confirm(tip)) return;
    busy = true;
    send(API + '/users/role', 'POST', { id: u.id, role: next })
      .then(function () { loadUsers(); loadStats(); })
      .catch(function (e) { alert('操作失败：' + e.message); })
      .then(function () { busy = false; });
  }

  /** 重置密码：不显示原密码，直接设新的 */
  function resetPwd(u) {
    var pwd = prompt('给「' + u.username + '」设置新密码（至少 6 位）：');
    if (!pwd) return;
    if (pwd.length < 6) { alert('密码至少 6 位'); return; }
    send(API + '/users/password', 'POST', { id: u.id, password: pwd })
      .then(function () { alert('已重置，「' + u.username + '」下次登录请用新密码'); })
      .catch(function (e) { alert('重置失败：' + e.message); });
  }

  /** 删号（连同他的地块/作物数据） */
  function removeUser(u) {
    if (!confirm('删除「' + u.username + '」？账号和他的地块、作物记录都会被清空，不可恢复。')) return;
    send(API + '/users?id=' + encodeURIComponent(u.id), 'DELETE')
      .then(function () { loadUsers(); loadStats(); })
      .catch(function (e) { alert('删除失败：' + e.message); });
  }

  function loadAudit() {
    get(API + '/audit?limit=200')
      .then(function (d) {
        var items = (d.data && d.data.items) || [];
        auditEl.innerHTML = '';
        auditEmpty.hidden = items.length > 0;
        items.forEach(function (line) {
          auditEl.appendChild(el('div', 'admin-audit-row', line));
        });
      })
      .catch(function (e) { auditEl.textContent = '加载失败：' + e.message; });
  }

  function loadStats() {
    get(API + '/stats').then(function (r) {
      if (!r.gate) renderStats(r.data);
    }).catch(function () { /* 忽略 */ });
  }

  // ---------------- 文件 ----------------

  function loadFiles() {
    get(API + '/files?path=' + encodeURIComponent(currentPath))
      .then(function (d) {
        var data = d.data || {};
        currentPath = data.path || '/';
        fsRootEl.textContent = '根目录 ' + (data.root || '');
        renderCrumbs(currentPath);

        var items = data.items || [];
        fsBody.innerHTML = '';
        fsEmpty.hidden = items.length > 0;
        items.forEach(function (it) { fsBody.appendChild(fileRow(it)); });
      })
      .catch(function (e) { alert('读取目录失败：' + e.message); });
  }

  function renderCrumbs(path) {
    crumbsEl.innerHTML = '';
    var parts = path.split('/').filter(Boolean);
    var acc = '';
    var rootBtn = el('button', 'admin-crumb', '根目录');
    rootBtn.addEventListener('click', function () { currentPath = '/'; loadFiles(); });
    crumbsEl.appendChild(rootBtn);

    parts.forEach(function (p) {
      acc += '/' + p;
      var target = acc;
      crumbsEl.appendChild(el('span', 'admin-crumb-sep', '/'));
      var b = el('button', 'admin-crumb', p);
      b.addEventListener('click', function () { currentPath = target; loadFiles(); });
      crumbsEl.appendChild(b);
    });
  }

  function fileRow(it) {
    var tr = el('tr');

    var nameTd = el('td');
    var nameBtn = el('button', 'admin-file-name' + (it.dir ? ' is-dir' : ''), (it.dir ? '📁 ' : '📄 ') + it.name);
    nameBtn.addEventListener('click', function () {
      if (it.dir) { currentPath = it.path; loadFiles(); }
      else openEditor(it.path);
    });
    nameTd.appendChild(nameBtn);
    tr.appendChild(nameTd);

    tr.appendChild(el('td', null, it.dir ? '—' : formatSize(it.size)));
    tr.appendChild(el('td', null, it.mtime || '—'));

    var opTd = el('td');
    var del = el('button', 'admin-mini is-danger', '删除');
    del.addEventListener('click', function () {
      if (!confirm('确定删除「' + it.name + '」？此操作不可撤销。')) return;
      send(API + '/file?path=' + encodeURIComponent(it.path), 'DELETE')
        .then(loadFiles)
        .catch(function (e) { alert('删除失败：' + e.message); });
    });
    opTd.appendChild(del);
    tr.appendChild(opTd);
    return tr;
  }

  function formatSize(n) {
    if (n == null) return '—';
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1024 / 1024).toFixed(1) + ' MB';
  }

  function createEntry(isDir) {
    var base = currentPath === '/' ? '' : currentPath;
    var name = prompt(isDir ? '新建目录名：' : '新建文件名：');
    if (!name) return;
    if (name.indexOf('/') >= 0) { alert('名字里不能带 /'); return; }
    send(API + '/files', 'POST', { path: base + '/' + name, dir: isDir })
      .then(loadFiles)
      .catch(function (e) { alert('创建失败：' + e.message); });
  }

  function openEditor(path) {
    get(API + '/file?path=' + encodeURIComponent(path))
      .then(function (d) {
        editingPath = path;
        editorTitle.textContent = d.data.path || path;
        editorText.value = d.data.content || '';
        editorMsg.textContent = '';
        editorEl.hidden = false;
        editorText.focus();
      })
      .catch(function (e) { alert('打开失败：' + e.message); });
  }

  function closeEditor() {
    editorEl.hidden = true;
    editingPath = null;
    editorText.value = '';
  }

  function saveFile() {
    if (!editingPath) return;
    editorMsg.textContent = '保存中…';
    send(API + '/file?path=' + encodeURIComponent(editingPath), 'PUT', editorText.value)
      .then(function () {
        editorMsg.textContent = '已保存';
        loadFiles();
        setTimeout(closeEditor, 500);
      })
      .catch(function (e) { editorMsg.textContent = '保存失败：' + e.message; });
  }

  // ---------------- 开关 ----------------

  function loadSettings() {
    get(API + '/settings')
      .then(function (d) {
        swSearch.checked = !!(d.data && d.data.searchEnabled);
        swLlm.checked = !!(d.data && d.data.llmEnabled);
      })
      .catch(function () { /* 忽略 */ });
  }

  function saveSettings() {
    swMsg.textContent = '保存中…';
    send(API + '/settings', 'PUT', { searchEnabled: swSearch.checked, llmEnabled: swLlm.checked })
      .then(function (d) {
        swMsg.textContent = '已保存，立即生效';
        setTimeout(function () { swMsg.textContent = ''; }, 2500);
      })
      .catch(function (e) { swMsg.textContent = '保存失败：' + e.message; });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
