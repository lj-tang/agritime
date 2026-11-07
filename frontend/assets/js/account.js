/**
 * 账号设置页：基本信息、改昵称、改密码。
 *
 * 三个约定：
 *   · 未登录直接回登录页，不在这一页显示空白表单
 *   · 改密成功后服务端会重签 Cookie，本页不跳转，原地提示「其他设备需重新登录」
 *   · 如果服务端说还在用初始密码（mustChangePassword），把改密区顶到最前并高亮
 */
(function () {
  var API = '/api/auth';
  var state = { user: null, mustChange: false };

  function $(id) { return document.getElementById(id); }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function showError(id, msg) {
    var box = $(id);
    if (!box) return;
    box.textContent = msg || '';
    box.hidden = !msg;
  }

  function toast(msg, kind) {
    var t = document.querySelector('.account-toast');
    if (!t) {
      t = el('div', 'account-toast');
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.className = 'account-toast is-show' + (kind ? ' is-' + kind : '');
    clearTimeout(t._timer);
    t._timer = setTimeout(function () { t.className = 'account-toast'; }, 2600);
  }

  function fmtTime(s) {
    if (!s) return '—';
    // 服务端给的是 "yyyy-MM-dd HH:mm:ss"，直接展示成日期即可
    return String(s).slice(0, 16).replace('T', ' ');
  }

  function roleLabel(role) {
    return role === 'admin' ? '管理员' : '普通用户';
  }

  function render() {
    var u = state.user;
    if (!u) return;
    var name = u.nickname || u.username;

    var stateEl = $('acc-state');
    if (stateEl) stateEl.hidden = true;

    var avatar = (window.Prefs && window.Prefs.get('avatar')) || '';
    $('acc-avatar').textContent = avatar || (name || '·').trim().charAt(0) || '·';
    $('acc-name').textContent = name;
    $('acc-sub').textContent = '登录名 ' + u.username + ' · ' + roleLabel(u.role);

    var meta = $('acc-meta');
    meta.innerHTML = '';
    [
      ['登录名', u.username],
      ['角色', roleLabel(u.role)],
      ['注册时间', fmtTime(u.createdAt)],
      ['最近登录', fmtTime(u.lastLoginAt)],
      ['登录次数', (u.loginCount || 0) + ' 次']
    ].forEach(function (row) {
      var wrap = el('div', 'account-meta-row');
      wrap.appendChild(el('dt', null, row[0]));
      wrap.appendChild(el('dd', null, row[1]));
      meta.appendChild(wrap);
    });

    $('nick-input').value = u.nickname || '';

    if (state.mustChange) {
      $('acc-force').hidden = false;
      var block = $('pwd-block');
      if (block && block.parentNode.firstElementChild !== block) {
        block.parentNode.insertBefore(block, block.parentNode.querySelector('.account-block'));
      }
      block.classList.add('is-focus');
      var old = $('old-pwd');
      if (old) old.focus();
    }
  }

  function load() {
    showLoading();
    fetch(API + '/me')
      .then(function (r) {
        if (r.status === 401) {
          location.replace('./auth.html?redirect=' + encodeURIComponent('./account.html'));
          return null;
        }
        return r.json()
          .then(function (d) { return { ok: r.ok, d: d }; })
          .catch(function () { return { ok: r.ok, d: null }; });
      })
      .then(function (res) {
        if (res === null) return; // 401，已跳转
        var data = res.d && res.d.data;
        if (!res.ok || !data || !data.user) {
          showLoadError();
          return;
        }
        state.user = data.user;
        state.mustChange = String(data.mustChangePassword) === 'true';
        render();
      })
      .catch(function () {
        showLoadError();
      });
  }

  function showLoading() {
    var stateEl = $('acc-state');
    if (stateEl) stateEl.hidden = true;
    $('acc-name').textContent = '正在读取…';
    $('acc-sub').textContent = '—';
    var meta = $('acc-meta');
    if (meta) meta.innerHTML = '';
  }

  function showLoadError() {
    $('acc-name').textContent = '读取失败';
    $('acc-sub').textContent = '账号信息未能加载';
    var meta = $('acc-meta');
    if (meta) meta.innerHTML = '';
    var stateEl = $('acc-state');
    if (stateEl) stateEl.hidden = false;
    if (window.UiState) {
      window.UiState.error(stateEl || 'acc-meta', {
        text: '网络异常或账号信息读取失败',
        hint: '你的数据都还在，网络恢复后重新加载即可。',
        onRetry: load
      });
    }
  }

  function submitNick(e) {
    e.preventDefault();
    var input = $('nick-input');
    var nick = (input.value || '').trim();
    if (!nick) { showError('nick-error', '昵称不能为空'); return; }
    if (nick.length > 20) { showError('nick-error', '昵称最多 20 个字符'); return; }
    showError('nick-error', '');
    var btn = $('nick-submit');
    btn.disabled = true;
    fetch(API + '/profile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: nick })
    })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, d: d }; }); })
      .then(function (res) {
        btn.disabled = false;
        if (!res.ok) {
          showError('nick-error', (res.d && res.d.message) || '保存失败，请重试');
          return;
        }
        state.user = res.d.data.user;
        render();
        toast('昵称已更新');
      })
      .catch(function (e) {
        btn.disabled = false;
        if (window.console) console.error('[account] 保存昵称失败', e);
        showError('nick-error', '网络异常，请稍后重试');
      });
  }

  /** 与后端 UserStore.passwordProblem 保持一致的即时校验，省一次往返 */
  function pwdProblem(pwd) {
    if (!pwd) return '请输入新密码';
    if (pwd.length < 6) return '新密码至少 6 位';
    if (!/[A-Za-z]/.test(pwd) || !/\d/.test(pwd)) return '新密码需同时包含字母和数字';
    return null;
  }

  function strengthHint() {
    var pwd = $('new-pwd').value || '';
    var pwd2 = $('new-pwd2').value || '';
    var box = $('pwd-strength');
    if (!pwd) { box.textContent = ''; box.className = 'account-strength'; return; }
    var problem = pwdProblem(pwd);
    if (problem) {
      box.textContent = problem;
      box.className = 'account-strength is-bad';
      return;
    }
    if (pwd2 && pwd2 !== pwd) {
      box.textContent = '两次输入不一致';
      box.className = 'account-strength is-bad';
      return;
    }
    var strong = pwd.length >= 10 && /[^A-Za-z0-9]/.test(pwd);
    box.textContent = strong ? '强度：较高' : '强度：可用（再长一些或加符号会更安全）';
    box.className = 'account-strength is-ok';
  }

  function submitPwd(e) {
    e.preventDefault();
    var oldPwd = $('old-pwd').value;
    var pwd = $('new-pwd').value;
    var pwd2 = $('new-pwd2').value;

    if (!oldPwd) { showError('pwd-error', '请输入当前密码'); return; }
    var problem = pwdProblem(pwd);
    if (problem) { showError('pwd-error', problem); return; }
    if (pwd !== pwd2) { showError('pwd-error', '两次输入的新密码不一致'); return; }
    if (pwd === oldPwd) { showError('pwd-error', '新密码不能与当前密码相同'); return; }

    showError('pwd-error', '');
    var btn = $('pwd-submit');
    btn.disabled = true;
    btn.textContent = '正在更新…';
    fetch(API + '/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ oldPassword: oldPwd, newPassword: pwd })
    })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, d: d }; }); })
      .then(function (res) {
        btn.disabled = false;
        btn.textContent = '更新密码';
        if (!res.ok) {
          showError('pwd-error', (res.d && res.d.message) || '修改失败，请重试');
          return;
        }
        $('old-pwd').value = '';
        $('new-pwd').value = '';
        $('new-pwd2').value = '';
        $('pwd-strength').textContent = '';
        $('pwd-strength').className = 'account-strength';
        state.mustChange = false;
        $('acc-force').hidden = true;
        $('pwd-block').classList.remove('is-focus');
        toast('密码已更新，其他设备需重新登录', 'ok');
      })
      .catch(function () {
        btn.disabled = false;
        btn.textContent = '更新密码';
        showError('pwd-error', '网络异常，请稍后重试');
      });
  }

  // ---------------- T26 数据导出与注销 ----------------

  var EXPORT_KEYS = {
    crops: 'agritime.my-crops.v1',
    plots: 'agritime.my-plots.v1',
    sensors: 'agritime.sensors.v1',
    history: 'agritime.history.v1',
    conflicts: 'agritime.sync.conflicts.v1'
  };

  function readJson(key, fallback) {
    try {
      var raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) { return fallback; }
  }

  function collectExport() {
    return {
      exportedAt: new Date().toISOString(),
      user: state.user ? {
        username: state.user.username,
        nickname: state.user.nickname || '',
        role: state.user.role
      } : null,
      crops: readJson(EXPORT_KEYS.crops, []),
      plots: readJson(EXPORT_KEYS.plots, []),
      sensors: readJson(EXPORT_KEYS.sensors, []),
      history: readJson(EXPORT_KEYS.history, []),
      reminders: readJson('agritime.reminders.center.v1', null),
      conflicts: readJson(EXPORT_KEYS.conflicts, [])
    };
  }

  function download(name, text, mime) {
    var blob = new Blob([text], { type: mime || 'text/plain;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  function stamp() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return '' + d.getFullYear() + m + day;
  }

  function exportJson() {
    download('agritime-backup-' + stamp() + '.json',
      JSON.stringify(collectExport(), null, 2), 'application/json;charset=utf-8');
    toast('已导出 JSON 备份', 'ok');
  }

  function csvCell(v) {
    var s = v == null ? '' : String(v);
    return '"' + s.replace(/"/g, '""') + '"';
  }

  function exportCsv() {
    var data = collectExport();
    var rows = [['类型', 'ID/主键', '名称/主题', '创建时间', '更新时间', '详情']];
    function add(type, list, idField, nameField) {
      (list || []).forEach(function (r) {
        rows.push([type, r[idField] || '', r[nameField] || '',
          r.createdAt || '', r.updatedAt || r.sownDate || '', JSON.stringify(r)]);
      });
    }
    add('地块', data.plots, 'plotId', 'name');
    add('作物', data.crops, 'recordId', 'cropName');
    add('设备', data.sensors, 'sensorId', 'name');
    add('搜索历史', data.history, 'key', 'cropName');
    add('提醒', data.reminders && data.reminders.items, 'id', 'title');
    add('同步冲突', data.conflicts, 'id', 'dataType');
    var text = '\ufeff' + rows.map(function (r) {
      return r.map(csvCell).join(',');
    }).join('\r\n');
    download('agritime-backup-' + stamp() + '.csv', text, 'text/csv;charset=utf-8');
    toast('已导出 CSV', 'ok');
  }

  function deleteAccount() {
    var pwd = $('del-pwd').value;
    var confirmText = $('del-confirm').value.trim();
    if (!pwd) { showError('del-error', '请输入当前密码'); return; }
    if (confirmText !== '注销') { showError('del-error', '请输入「注销」两个字确认'); return; }

    var btn = $('del-submit');
    btn.disabled = true;
    btn.textContent = '正在注销…';
    fetch(API + '/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: pwd })
    })
      .then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (d) {
          return { ok: r.ok, d: d };
        });
      })
      .then(function (res) {
        if (!res.ok || res.d.code !== 0) {
          btn.disabled = false;
          btn.textContent = '永久注销账号';
          showError('del-error', res.d.message || '注销失败，请稍后重试');
          return;
        }
        try {
          ['agritime.my-crops.v1', 'agritime.my-plots.v1', 'agritime.sensors.v1',
            'agritime.history.v1', 'agritime.reminders.center.v1',
            'agritime.sync.conflicts.v1', 'agritime.my-tickets.v1'].forEach(function (k) {
            localStorage.removeItem(k);
          });
        } catch (e) { /* 清不掉也不影响账号已删除 */ }
        alert('账号已注销，本机缓存已清理。');
        location.href = './index.html';
      })
      .catch(function () {
        btn.disabled = false;
        btn.textContent = '永久注销账号';
        showError('del-error', '网络异常，请稍后重试');
      });
  }

  function init() {
    $('nick-form').addEventListener('submit', submitNick);
    $('pwd-form').addEventListener('submit', submitPwd);
    $('new-pwd').addEventListener('input', strengthHint);
    $('new-pwd2').addEventListener('input', strengthHint);
    $('export-json').addEventListener('click', exportJson);
    $('export-csv').addEventListener('click', exportCsv);
    $('del-submit').addEventListener('click', deleteAccount);
    load();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
