/**
 * 登录 / 注册页（T10）
 *
 * 会话是 HttpOnly Cookie，前端不接触令牌：
 * 只要 fetch 同源，浏览器会自动带上，所以这里看不到任何 token 处理。
 */
(function () {
  var API = '/api/auth';
  var mode = 'login';
  var redirectTo = './index.html';

  var form, usernameEl, passwordEl, nicknameEl, nicknameField;
  var titleEl, subEl, submitEl, errorEl, switchEl;
  var signedEl, signedNameEl, logoutEl;

  function init() {
    form = document.getElementById('auth-form');
    if (!form) return;

    usernameEl = document.getElementById('username');
    passwordEl = document.getElementById('password');
    nicknameEl = document.getElementById('nickname');
    nicknameField = document.getElementById('nickname-field');
    titleEl = document.getElementById('auth-title');
    subEl = document.getElementById('auth-sub');
    submitEl = document.getElementById('auth-submit');
    errorEl = document.getElementById('auth-error');
    switchEl = document.getElementById('auth-switch');
    signedEl = document.getElementById('auth-signed');
    signedNameEl = document.getElementById('auth-signed-name');
    logoutEl = document.getElementById('auth-logout');

    // 从受保护页面跳过来的，登录后回到原处
    var next = new URLSearchParams(location.search).get('next');
    if (next && /^[\w./-]+$/.test(next) && next.indexOf('//') !== 0) {
      redirectTo = next;
    }

    document.querySelectorAll('.auth-tab').forEach(function (tab) {
      tab.addEventListener('click', function () { setMode(tab.getAttribute('data-tab')); });
    });
    document.querySelectorAll('[data-switch]').forEach(function (a) {
      a.addEventListener('click', function (e) {
        e.preventDefault();
        setMode(a.getAttribute('data-switch'));
      });
    });

    form.addEventListener('submit', onSubmit);
    if (logoutEl) logoutEl.addEventListener('click', onLogout);

    setMode('login');
    checkSignedIn();
  }

  function setMode(next) {
    mode = next === 'register' ? 'register' : 'login';
    var isReg = mode === 'register';

    document.querySelectorAll('.auth-tab').forEach(function (t) {
      t.classList.toggle('is-active', t.getAttribute('data-tab') === mode);
    });

    titleEl.textContent = isReg ? '创建账号' : '欢迎回来';
    subEl.textContent = isReg
      ? '注册后，地块和作物会跟着账号走'
      : '登录后，地块和作物会跟着账号走';
    nicknameField.hidden = !isReg;
    passwordEl.setAttribute('autocomplete', isReg ? 'new-password' : 'current-password');
    submitEl.textContent = isReg ? '注册并登录' : '登录';
    switchEl.innerHTML = isReg
      ? '已经有账号了？<a href="#" data-switch="login">去登录</a>'
      : '还没有账号？<a href="#" data-switch="register">立即注册</a>';
    switchEl.querySelector('[data-switch]').addEventListener('click', function (e) {
      e.preventDefault();
      setMode(isReg ? 'login' : 'register');
    });

    hideError();
  }

  function onSubmit(e) {
    e.preventDefault();
    hideError();

    var username = (usernameEl.value || '').trim();
    var password = passwordEl.value || '';
    var nickname = (nicknameEl.value || '').trim();

    if (!username) { showError('请填写用户名'); usernameEl.focus(); return; }
    if (!password) { showError('请填写密码'); passwordEl.focus(); return; }
    if (password.length < 6) { showError('密码至少 6 位'); passwordEl.focus(); return; }

    var payload = { username: username, password: password };
    if (mode === 'register' && nickname) payload.nickname = nickname;

    submitEl.disabled = true;
    submitEl.textContent = mode === 'register' ? '正在注册…' : '正在登录…';

    fetch(API + (mode === 'register' ? '/register' : '/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (data) {
          return { ok: res.ok, status: res.status, data: data };
        });
      })
      .then(function (r) {
        if (!r.ok) {
          showError(r.data && r.data.message ? r.data.message : '操作失败，请稍后再试');
          resetSubmit();
          return;
        }
        var user = r.data && r.data.data && r.data.data.user;
        if (user && user.role === 'admin') {
          location.href = './admin.html';
          return;
        }
        location.href = redirectTo;
      })
      .catch(function () {
        showError('网络异常，请检查连接后重试');
        resetSubmit();
      });
  }

  function onLogout() {
    fetch(API + '/logout', { method: 'POST' })
      .catch(function () { /* 就算请求失败，本地也按退出处理 */ })
      .then(function () {
        signedEl.hidden = true;
        form.hidden = false;
        switchEl.hidden = false;
      });
  }

  /** 已经登录过就别再让填一遍 */
  function checkSignedIn() {
    fetch(API + '/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        var user = data && data.data && data.data.user;
        if (!user) return;
        signedNameEl.textContent = user.nickname || user.username;
        signedEl.hidden = false;
        form.hidden = true;
        switchEl.hidden = true;
      })
      .catch(function () { /* 未登录或接口不可用，保持表单 */ });
  }

  function resetSubmit() {
    submitEl.disabled = false;
    submitEl.textContent = mode === 'register' ? '注册并登录' : '登录';
  }

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.hidden = false;
  }

  function hideError() {
    errorEl.hidden = true;
    errorEl.textContent = '';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
