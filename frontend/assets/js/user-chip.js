/**
 * 左下角账号区（T10 升级）：头像 + 昵称 + 箭头整体是一个可点的按钮，
 * 点击弹出分组设置面板（我的账号 / 个人 / 设置）。
 *
 * 默认挂在侧栏左下角（#sidebar-footer）。找不到侧栏时退回页头 .brand-row。
 * 登录态只有服务端知道，先查一次 /api/auth/me 再决定显示什么。
 *
 * 依赖（需先加载）：theme.js（主题三态）、prefs.js（头像/定位偏好）、icons.js（SVG 图标）。
 */
(function () {
  var API = '/api/auth';

  var AVATARS = ['🌾', '🌱', '🌽', '🍅', '🥕', '🐟', '🐔', '🐮', '🌻', '🍎', '🍚', '🌶️'];

  function ic(name, size) {
    return (window.Icons && window.Icons.svg) ? window.Icons.svg(name, size || 16) : '';
  }

  function pickHost() {
    var footer = document.getElementById('sidebar-footer');
    if (footer) return { el: footer, inSidebar: true };
    var row = document.querySelector('.brand-row');
    if (row) return { el: row, inSidebar: false };
    return null;
  }

  function avatarText(name, avatar) {
    if (avatar) return avatar;
    return (name || '·').trim().charAt(0) || '👤';
  }

  function el(tag, cls, html) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  }

  /* ---------------- 登录 / 未登录入口 ---------------- */

  function renderLoggedOut(chip, inSidebar) {
    chip.innerHTML = '';
    if (!inSidebar) {
      chip.innerHTML = '<a href="./auth.html">登录 / 注册</a>';
      return;
    }
    var link = document.createElement('a');
    link.className = 'user-chip-entry';
    link.href = './auth.html';
    link.innerHTML = '<span class="user-chip-icon" aria-hidden="true">' + ic('user') + '</span>'
      + '<span class="user-chip-label">登录 / 注册</span>';
    chip.appendChild(link);
  }

  /* ---------------- 登录态：触发按钮 + 弹出设置面板 ---------------- */

  function renderLoggedIn(chip, user, inSidebar) {
    var name = user.nickname || user.username || '我';
    var avatar = (window.Prefs && window.Prefs.get('avatar')) || '';
    chip.innerHTML = '';

    if (!inSidebar) {
      // 页头简化版：只有名字，不弹面板（独立页面用）
      var safe = el('span', 'user-chip-name');
      safe.textContent = name;
      chip.appendChild(safe);
      return;
    }

    // 触发按钮：头像 + 昵称 + 箭头，整体可点，头像纳入热区
    var trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'user-chip-trigger';
    trigger.setAttribute('aria-haspopup', 'menu');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.innerHTML =
      '<span class="user-chip-avatar" aria-hidden="true">' + avatarText(name, avatar) + '</span>'
      + '<span class="user-chip-name">' + name + '</span>'
      + '<span class="user-chip-caret" aria-hidden="true">' + ic('chevronDown', 14) + '</span>';
    chip.appendChild(trigger);

    var menu = buildMenu(user, name, avatar);
    chip.appendChild(menu);

    var menuOpen = false;
    function setOpen(open) {
      menuOpen = open;
      menu.hidden = !open;
      trigger.setAttribute('aria-expanded', String(open));
      trigger.classList.toggle('is-open', open);
      if (open) syncMenuState(menu, user, name);
    }

    trigger.addEventListener('click', function (e) {
      e.stopPropagation();
      setOpen(!menuOpen);
    });

    menu.addEventListener('click', function (e) { e.stopPropagation(); });

    // 点外部 / 按 Esc 关闭
    document.addEventListener('click', function (e) {
      if (menuOpen && !chip.contains(e.target)) setOpen(false);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && menuOpen) setOpen(false);
    });
  }

  function buildMenu(user, name, avatar) {
    var menu = el('div', 'user-menu');
    menu.hidden = true;

    // 头部
    var head = el('div', 'user-menu-header');
    head.innerHTML =
      '<span class="user-menu-avatar" aria-hidden="true">' + avatarText(name, avatar) + '</span>'
      + '<div class="user-menu-id">'
      + '<div class="user-menu-name">' + name + '</div>'
      + '<div class="user-menu-sub">' + user.username + ' · ' + (user.role === 'admin' ? '管理员' : '普通用户') + '</div>'
      + '</div>';
    menu.appendChild(head);

    var body = el('div', 'user-menu-body');

    // —— 我的账号 ——
    var acct = el('div', 'user-menu-section');
    acct.appendChild(el('div', 'user-menu-section-title', '我的账号'));
    acct.appendChild(menuLink('account.html#pwd-block', 'key', '修改密码'));
    var logout = el('button', 'user-menu-item');
    logout.type = 'button';
    logout.innerHTML = '<span class="user-menu-icon">' + ic('logout') + '</span><span>退出登录</span>';
    logout.addEventListener('click', function () {
      fetch(API + '/logout', { method: 'POST' })
        .catch(function () { /* 忽略 */ })
        .then(function () { location.reload(); });
    });
    acct.appendChild(logout);
    body.appendChild(acct);

    // —— 个人 ——
    var prof = el('div', 'user-menu-section');
    prof.appendChild(el('div', 'user-menu-section-title', '个人'));
    prof.appendChild(menuLink('account.html', 'user', '修改昵称'));

    // 头像：一行可选 emoji + 恢复首字母
    var avatarItem = el('div', 'user-menu-item user-menu-item--static');
    avatarItem.innerHTML = '<span class="user-menu-icon">' + ic('palette') + '</span><span>头像</span>';
    var picker = el('div', 'avatar-picker');
    AVATARS.forEach(function (a) {
      var b = el('button', 'avatar-opt' + (a === avatar ? ' is-active' : ''));
      b.type = 'button';
      b.textContent = a;
      b.setAttribute('aria-label', '选择头像 ' + a);
      b.addEventListener('click', function () {
        if (window.Prefs) window.Prefs.set('avatar', a);
        refreshAvatar(a);
        picker.querySelectorAll('.avatar-opt').forEach(function (x) { x.classList.remove('is-active'); });
        b.classList.add('is-active');
      });
      picker.appendChild(b);
    });
    var reset = el('button', 'avatar-opt avatar-opt--reset');
    reset.type = 'button';
    reset.textContent = '首字母';
    reset.title = '用昵称首字母做头像';
    if (!avatar) reset.classList.add('is-active');
    reset.addEventListener('click', function () {
      if (window.Prefs) window.Prefs.set('avatar', '');
      refreshAvatar('');
      picker.querySelectorAll('.avatar-opt').forEach(function (x) { x.classList.remove('is-active'); });
      reset.classList.add('is-active');
    });
    picker.appendChild(reset);
    avatarItem.appendChild(picker);
    prof.appendChild(avatarItem);
    body.appendChild(prof);

    // —— 设置 ——
    var settings = el('div', 'user-menu-section');
    settings.appendChild(el('div', 'user-menu-section-title', '设置'));

    // 外观主题：三态分段控件
    var themeItem = el('div', 'user-menu-item user-menu-item--block');
    themeItem.innerHTML = '<span class="user-menu-icon">' + ic('sun') + '</span><span class="user-menu-row-label">外观主题</span>';
    var seg = el('div', 'theme-seg');
    [['light', '浅色'], ['dark', '深色'], ['system', '跟随系统']].forEach(function (opt) {
      var b = el('button', 'theme-seg-btn');
      b.type = 'button';
      b.setAttribute('data-theme-opt', opt[0]);
      b.textContent = opt[1];
      b.addEventListener('click', function () {
        if (window.Theme) window.Theme.set(opt[0]);
        seg.querySelectorAll('.theme-seg-btn').forEach(function (x) { x.classList.remove('is-active'); });
        b.classList.add('is-active');
      });
      seg.appendChild(b);
    });
    themeItem.appendChild(seg);
    settings.appendChild(themeItem);

    // 定位权限：开关
    var locItem = el('div', 'user-menu-item user-menu-item--block');
    locItem.innerHTML =
      '<span class="user-menu-icon">' + ic('locate') + '</span>'
      + '<span class="user-menu-row-label">定位权限<span class="user-menu-row-sub">允许自动定位 / 当前位置</span></span>';
    var sw = el('button', 'switch');
    sw.type = 'button';
    sw.setAttribute('role', 'switch');
    sw.innerHTML = '<span class="switch-knob"></span>';
    sw.addEventListener('click', function () {
      var on = sw.getAttribute('aria-checked') !== 'true';
      sw.setAttribute('aria-checked', String(on));
      sw.classList.toggle('is-on', on);
      if (window.Prefs) window.Prefs.set('location', on);
    });
    locItem.appendChild(sw);
    settings.appendChild(locItem);

    body.appendChild(settings);

    // —— 管理后台（仅超管） ——
    if (user.role === 'admin') {
      var adminSec = el('div', 'user-menu-section');
      adminSec.appendChild(menuLink('admin.html', 'gear', '管理后台'));
      body.appendChild(adminSec);
    }

    menu.appendChild(body);
    return menu;
  }

  function menuLink(href, icon, label) {
    var a = el('a', 'user-menu-item');
    a.href = href;
    a.innerHTML = '<span class="user-menu-icon">' + ic(icon) + '</span><span>' + label + '</span>';
    return a;
  }

  /** 每次打开面板时把主题/定位/头像的当前状态同步到控件上 */
  function syncMenuState(menu, user, name) {
    var pref = (window.Theme && window.Theme.get()) || 'system';
    menu.querySelectorAll('.theme-seg-btn').forEach(function (b) {
      b.classList.toggle('is-active', b.getAttribute('data-theme-opt') === pref);
    });
    var sw = menu.querySelector('.switch');
    if (sw) {
      var loc = window.Prefs ? window.Prefs.get('location') : true;
      sw.setAttribute('aria-checked', String(loc !== false));
      sw.classList.toggle('is-on', loc !== false);
    }
  }

  /** 改头像后同步触发按钮与面板头部的头像 */
  function refreshAvatar(avatar) {
    var name = window.__userChipName || '';
    var val = avatarText(name, avatar);
    var chipAvatar = document.querySelector('.user-chip-avatar');
    var menuAvatar = document.querySelector('.user-menu-avatar');
    if (chipAvatar) chipAvatar.textContent = val;
    if (menuAvatar) menuAvatar.textContent = val;
  }

  /* ---------------- 初始化 ---------------- */

  function init() {
    var host = pickHost();
    if (!host || document.getElementById('user-chip')) return;

    var chip = el('div', 'user-chip' + (host.inSidebar ? ' user-chip--sidebar' : ''));
    chip.id = 'user-chip';
    host.el.appendChild(chip);

    fetch(API + '/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        var user = data && data.data && data.data.user;
        if (!user) {
          renderLoggedOut(chip, host.inSidebar);
          return;
        }
        window.__userChipName = user.nickname || user.username || '我';
        renderLoggedIn(chip, user, host.inSidebar);
      })
      .catch(function () {
        renderLoggedOut(chip, host.inSidebar);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
