/**
 * 首页右上角的账号入口（T10）
 *
 * 不做成静态标签，是因为登录态只有服务端知道；
 * 这里查一次 /api/auth/me，再决定显示「登录/注册」还是「昵称 + 退出」。
 */
(function () {
  function init() {
    var row = document.querySelector('.brand-row');
    if (!row || document.getElementById('user-chip')) return;

    var chip = document.createElement('div');
    chip.className = 'user-chip';
    chip.id = 'user-chip';
    row.appendChild(chip);

    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        var user = data && data.data && data.data.user;
        if (!user) {
          chip.innerHTML = '<a href="./auth.html">登录 / 注册</a>';
          return;
        }
        var name = user.nickname || user.username;
        var safe = document.createElement('span');
        safe.className = 'user-chip-name';
        safe.textContent = name;
        chip.appendChild(safe);

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
      })
      .catch(function () {
        chip.innerHTML = '<a href="./auth.html">登录 / 注册</a>';
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
