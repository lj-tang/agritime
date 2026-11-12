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

  /* ---------------- 主题缩略图 ----------------
     每个主题画一小块「自己的地」：天空用主题底色，地面用主色的淡影，
     作物/建筑/月亮用主色。这样选主题时看到的是一块地，而不是三个色块。
     colors = [sky, ink, ground]，与 theme.js / common.css 的变量同源。 */

  function art(scene, colors) {
    var sky = colors[0];
    var ink = colors[1];
    var crop = colors[3] || colors[1];   // 穗粒/花瓣本色，比主色更亮
    var head = '<svg viewBox="0 0 76 38" preserveAspectRatio="xMidYMid slice" aria-hidden="true">'
      + '<rect width="76" height="38" fill="' + sky + '"/>';
    // 一株穗子：茎用主色，穗粒用作物色，两侧小穗贴茎斜向上，远看才像麦/稻而不像人
    function ear(x, baseY, tipY, grain, w) {
      var out = '<path d="M' + x + ' ' + baseY + ' Q' + (x + 1.5) + ' ' + ((baseY + tipY) / 2) + ' '
        + (x - 1) + ' ' + tipY + '" stroke="' + ink + '" stroke-width="' + w + '" fill="none" stroke-linecap="round"/>';
      var n = 4, step = (baseY - tipY) / (n + 1);
      for (var i = 0; i < n; i += 1) {
        var y = baseY - step * (i + 1);
        out += '<ellipse cx="' + (x - 0.6) + '" cy="' + y + '" rx="1.5" ry="2.6" fill="' + grain + '" transform="rotate(-16 ' + (x - 0.6) + ' ' + y + ')"/>'
          + '<ellipse cx="' + (x + 0.6) + '" cy="' + y + '" rx="1.5" ry="2.6" fill="' + grain + '" transform="rotate(16 ' + (x + 0.6) + ' ' + y + ')"/>';
      }
      return out + '<ellipse cx="' + x + '" cy="' + tipY + '" rx="1.5" ry="2.8" fill="' + grain + '"/>';
    }

    if (scene === 'rice') {
      return head
        + '<path d="M0 26 Q19 22 38 26 T76 25 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.14"/>'
        + '<path d="M0 32 Q19 28 38 32 T76 31 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.09"/>'
        + ear(20, 31, 11, crop, 1.5)
        + ear(29, 32, 16, crop, 1.3)
        + ear(54, 32, 13, crop, 1.4)
        + '</svg>';
    }
    if (scene === 'wheat') {
      return head
        + '<path d="M0 28 Q19 24 38 28 T76 27 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.15"/>'
        + ear(24, 33, 10, crop, 1.5)
        + ear(34, 33, 15, crop, 1.3)
        + ear(48, 33, 12, crop, 1.4)
        + '<path d="M24 10 L22 4 M24 10 L26 3 M48 12 L46 6 M48 12 L50 6" stroke="' + crop
        + '" stroke-width="0.9" opacity="0.55" stroke-linecap="round"/>'
        + '</svg>';
    }
    if (scene === 'bloom') {
      // 油菜花：五瓣小黄花 + 深色花心，三株高矮错开
      function flower(x, y, r) {
        var out = '';
        for (var k = 0; k < 5; k += 1) {
          var a = -Math.PI / 2 + k * Math.PI * 2 / 5;
          out += '<circle cx="' + (x + Math.cos(a) * r).toFixed(2) + '" cy="' + (y + Math.sin(a) * r).toFixed(2) + '" r="' + (r * 0.95).toFixed(2) + '" fill="' + crop + '"/>';
        }
        return out + '<circle cx="' + x + '" cy="' + y + '" r="' + (r * 0.55).toFixed(2) + '" fill="' + ink + '" opacity="0.7"/>';
      }
      return head
        + '<path d="M0 29 Q19 26 38 29 T76 28 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.13"/>'
        + '<path d="M22 34 Q23 28 22 20 M38 35 Q39 29 38 17 M55 34 Q56 28 55 21" stroke="' + ink + '" stroke-width="1.2" fill="none" stroke-linecap="round"/>'
        + flower(22, 17, 3.4) + flower(38, 14, 3.8) + flower(55, 18, 3.2)
        + '</svg>';
    }
    if (scene === 'terrace') {
      // 梯田：一级级抬起来的台地剖面（不是横条纹——台阶要能看出高低）
      return head
        + '<path d="M0 35 H12 V30 H26 V25 H42 V20 H58 V15 H76 V38 H0 Z" fill="' + ink + '" opacity="0.13"/>'
        + '<path d="M0 35 H12 V30 H26 V25 H42 V20 H58 V15 H76" stroke="' + ink
        + '" stroke-width="1.5" fill="none" opacity="0.42" stroke-linejoin="round"/>'
        + '<path d="M0 38 H20 V34 H38 V30 H56 V26 H76" stroke="' + ink
        + '" stroke-width="1.1" fill="none" opacity="0.18"/>'
        // 最上一级再种两行小苗，坡地才有人管的样子
        + '<path d="M14 30 v-3 M26 25 v-3 M42 20 v-3 M58 15 v-3" stroke="' + crop
        + '" stroke-width="1.1" opacity="0.75" stroke-linecap="round"/>'
        + '</svg>';
    }
    if (scene === 'barn') {
      return head
        + '<path d="M0 30 Q19 27 38 30 T76 29 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.15"/>'
        + '<path d="M30 20 L45 9 L60 20 Z" fill="' + ink + '"/>'
        + '<rect x="33" y="20" width="24" height="13" fill="' + ink + '" opacity="0.82"/>'
        + '<rect x="42" y="25" width="6" height="8" fill="' + sky + '" opacity="0.9"/>'
        + '<path d="M45 8 q2 -4 4 -1 q2 -3 3 1" stroke="' + crop + '" stroke-width="1.2" fill="none" opacity="0.55" stroke-linecap="round"/>'
        + '</svg>';
    }
    if (scene === 'night') {
      return head
        + '<path d="M54 7.6 A6.2 6.2 0 1 0 54 20.4 A7.6 7.6 0 1 1 54 7.6 Z" fill="' + crop + '"/>'
        + '<circle cx="14" cy="9" r="0.9" fill="' + crop + '" opacity="0.7"/>'
        + '<circle cx="26" cy="15" r="0.7" fill="' + crop + '" opacity="0.5"/>'
        + '<circle cx="34" cy="7" r="0.8" fill="' + crop + '" opacity="0.6"/>'
        + '<path d="M0 27 Q19 23 38 27 T76 26 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.2"/>'
        + '<path d="M0 32 Q19 29 38 32 T76 31 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.12"/>'
        + '</svg>';
    }
    if (scene === 'soil') {
      return head
        + '<circle cx="14" cy="9" r="0.8" fill="' + crop + '" opacity="0.5"/>'
        + '<circle cx="30" cy="6" r="0.6" fill="' + crop + '" opacity="0.38"/>'
        + '<circle cx="48" cy="10" r="0.7" fill="' + crop + '" opacity="0.42"/>'
        + '<circle cx="64" cy="6" r="0.9" fill="' + crop + '" opacity="0.5"/>'
        + '<path d="M0 28 Q19 25 38 28 T76 27 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.16"/>'
        + '<path d="M0 33 Q19 30 38 33 T76 32 L76 38 L0 38 Z" fill="' + ink + '" opacity="0.1"/>'
        + '</svg>';
    }
    // system：左浅右深，跟系统走
    return '<svg viewBox="0 0 76 38" preserveAspectRatio="xMidYMid slice" aria-hidden="true">'
      + '<rect width="38" height="38" fill="#f4f6f4"/><rect x="38" width="38" height="38" fill="#1b211c"/>'
      + '<circle cx="18" cy="13" r="4.4" fill="#e9b949"/>'
      + '<path d="M0 28 Q19 25 38 28 L38 38 L0 38 Z" fill="#8fae97" opacity="0.55"/>'
      + '<path d="M38 29 Q57 26 76 29 L76 38 L38 38 Z" fill="#4b7a5c" opacity="0.7"/>'
      + '<path d="M52 10.2 A4.4 4.4 0 1 0 52 19 A5.4 5.4 0 1 1 52 10.2 Z" fill="#cfe6d8"/>'
      + '<circle cx="66" cy="9" r="0.9" fill="#cfe6d8"/><circle cx="60" cy="13" r="0.7" fill="#cfe6d8" opacity="0.8"/>'
      + '</svg>';
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

    // 外观主题：一块块「地」的缩略图（配色取自作物，见 common.css 对应主题块）
    var themeItem = el('div', 'user-menu-item user-menu-item--block');
    themeItem.innerHTML = '<span class="user-menu-icon">' + ic('sun') + '</span><span class="user-menu-row-label">外观主题</span>';
    var grid = el('div', 'theme-grid');
    var THEMES = [
      { id: 'light', label: '稻米白', scene: 'rice', note: '素白底配稻绿，日常看最稳', colors: ['#f5f7f5', '#16a34a', '#ffffff', '#d9a21b'] },
      { id: 'paddy', label: '稻田晨雾', scene: 'rice', note: '清晨水田的青绿，雾气压住饱和度', colors: ['#eef5ef', '#2f7d5d', '#ffffff', '#d9a21b'] },
      { id: 'wheat', label: '麦浪金黄', scene: 'wheat', note: '麦熟时节的麦色，暖而不刺眼', colors: ['#faf4e3', '#9a5f0c', '#fffdf6', '#dda72a'] },
      { id: 'rapeseed', label: '油菜花田', scene: 'bloom', note: '三月油菜花的黄绿撞色', colors: ['#f9f8e2', '#5f7f14', '#fffef6', '#e8b81c'] },
      { id: 'terra', label: '红壤梯田', scene: 'terrace', note: '南方红壤的砖红，土气但有劲', colors: ['#f7ece5', '#a24a2f', '#fffaf7', '#d98918'] },
      { id: 'barn', label: '谷仓木纸', scene: 'barn', note: '谷仓木头与牛皮纸，最像纸的底色', colors: ['#f4e8d6', '#8a5a2b', '#fdf7ec', '#c98a15'] },
      { id: 'dark', label: '夜田墨绿', scene: 'night', note: '夜里下地看，不晃眼', colors: ['#0f1310', '#4ade80', '#161a17', '#f5d489'] },
      { id: 'amoled', label: '黑土深夜', scene: 'soil', note: '纯黑省电，OLED 屏最省', colors: ['#000000', '#22c55e', '#0a0a0a', '#f5d489'] },
      { id: 'system', label: '跟随系统', scene: 'system', note: '系统深色就深色，浅色就浅色', colors: ['#e9e9e9', '#888888', '#ffffff', '#e9b949'] }
    ];
    var currentPref = window.Theme ? window.Theme.get() : 'system';
    THEMES.forEach(function (t) {
      var card = el('button', 'theme-card' + (t.id === currentPref ? ' is-active' : ''));
      card.type = 'button';
      card.setAttribute('data-theme-opt', t.id);
      // 名字四个字装不下注解，注解放到悬停与读屏里，不挤版面
      card.title = t.label + ' · ' + t.note;
      card.setAttribute('aria-label', '主题：' + t.label + '，' + t.note);
      card.appendChild(el('span', 'theme-thumb', art(t.scene, t.colors)));
      card.appendChild(el('span', 'theme-card-label', t.label));
      card.addEventListener('click', function () {
        if (window.Theme) window.Theme.set(t.id);
        grid.querySelectorAll('.theme-card').forEach(function (x) { x.classList.remove('is-active'); });
        card.classList.add('is-active');
      });
      grid.appendChild(card);
    });
    themeItem.appendChild(grid);
    settings.appendChild(themeItem);

    // 定位权限：开关
    var locItem = el('div', 'user-menu-item user-menu-item--block');
    locItem.innerHTML =
      '<span class="user-menu-icon">' + ic('locate') + '</span>'
      + '<span class="user-menu-row-label">定位权限<span class="user-menu-row-sub">允许使用定位服务</span></span>';
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
    // 以前这里查的是 .theme-seg-btn，卡片改名为 .theme-card 后一度查不到；
    // 顺手订正，免得「跟随系统」解析出的状态与高亮对不上。
    menu.querySelectorAll('.theme-card').forEach(function (b) {
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
