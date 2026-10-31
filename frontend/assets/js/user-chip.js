/**
 * 账号入口（T10）
 *
 * 默认挂在侧栏左下角（#sidebar-footer），与主流工具一致：
 * 头像/昵称固定在最下方，不随内容滚动。
 * 找不到侧栏时退回页头 .brand-row，保证独立页面也能用。
 *
 * 登录态只有服务端知道，所以先查一次 /api/auth/me 再决定显示什么。
 */
(function () {
  function pickHost() {
    var footer = document.getElementById('sidebar-footer');
    if (footer) return { el: footer, inSidebar: true };
    var row = document.querySelector('.brand-row');
    if (row) return { el: row, inSidebar: false };
    return null;
  }

  function renderLoggedOut(chip, inSidebar) {
    chip.innerHTML = '';
    if (!inSidebar) {
      chip.innerHTML = '<a href="./auth.html">登录 / 注册</a>';
      return;
    }
    var link = document.createElement('a');
    link.className = 'user-chip-entry';
    link.href = './auth.html';
    link.innerHTML = '<span class="user-chip-icon" aria-hidden="true">👤</span>'
      + '<span class="user-chip-label">登录 / 注册</span>';
    chip.appendChild(link);
  }

  function renderLoggedIn(chip, user, inSidebar) {
    var name = user.nickname || user.username || '我';
    chip.innerHTML = '';

    if (!inSidebar) {
      var safe = document.createElement('span');
      safe.className = 'user-chip-name';
      safe.textContent = name;
      chip.appendChild(safe);
    } else {
      // 左下角：头像 + 昵称（超长省略），右侧挂后台/退出
      var avatar = document.createElement('span');
      avatar.className = 'user-chip-icon user-chip-avatar';
      avatar.setAttribute('aria-hidden', 'true');
      avatar.textContent = name.trim().charAt(0) || '👤';
      chip.appendChild(avatar);

      var label = document.createElement('span');
      label.className = 'user-chip-name';
      label.textContent = name;
      label.title = name;
      chip.appendChild(label);
    }

    if (user.role === 'admin') {
      var admin = document.createElement('a');
      admin.href = './admin.html';
      admin.textContent = '后台';
      chip.appendChild(admin);
    }

    var out = document.createElement('button');
    out.type = 'button';
    out.textContent = '退出';
    out.addEventListener('click', function () {
      fetch('/api/auth/logout', { method: 'POST' })
        .catch(function () { /* 忽略 */ })
        .then(function () { location.reload(); });
    });
    chip.appendChild(out);
  }

  function init() {
    var host = pickHost();
    if (!host || document.getElementById('user-chip')) return;

    var chip = document.createElement('div');
    chip.className = 'user-chip' + (host.inSidebar ? ' user-chip--sidebar' : '');
    chip.id = 'user-chip';
    host.el.appendChild(chip);

    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        var user = data && data.data && data.data.user;
        if (!user) {
          renderLoggedOut(chip, host.inSidebar);
          return;
        }
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
