/**
 * 管理后台 - 超管工单管理（T14）
 * 超管登录后可查看全部工单、回复用户、关闭工单。
 * 支持自动刷新、统计概览、时间格式化。
 */
(function () {
  var API = '/api/support';
  var TOKEN_KEY = 'agritime-admin-token';

  var loginEl, panelEl;
  var pollTimer = null;

  function init() {
    loginEl = document.getElementById('admin-login');
    panelEl = document.getElementById('admin-panel');
    if (!loginEl || !panelEl) return;

    var form = document.getElementById('admin-login-form');
    if (form) form.addEventListener('submit', onLogin);

    if (getToken()) { showPanel(); } else { showLogin(); }
  }

  function getToken() { return localStorage.getItem(TOKEN_KEY) || ''; }
  function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
  function clearToken() { localStorage.removeItem(TOKEN_KEY); }

  function showLogin() {
    loginEl.style.display = '';
    panelEl.style.display = 'none';
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  }

  function showPanel() {
    loginEl.style.display = 'none';
    panelEl.style.display = '';
    loadTickets();
    var logoutBtn = document.getElementById('admin-logout');
    if (logoutBtn) logoutBtn.addEventListener('click', function () { clearToken(); showLogin(); });
    var filterSel = document.getElementById('admin-filter');
    if (filterSel) filterSel.addEventListener('change', function () { loadTickets(true); });
    var refreshBtn = document.getElementById('admin-refresh');
    if (refreshBtn) refreshBtn.addEventListener('click', function () { loadTickets(true); });
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(function () { loadTickets(false, true); }, 30000);
  }

  function onLogin(e) {
    e.preventDefault();
    var pwd = (document.getElementById('admin-password').value || '').trim();
    if (!pwd) return;
    var btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = '登录中…';
    fetch(API + '/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: pwd })
    }).then(function (r) { return r.json(); }).then(function (data) {
      btn.disabled = false; btn.textContent = '登录';
      if (data.code && data.code !== '0') { showLoginError(data.message || '密码错误'); return; }
      setToken(data.token);
      showPanel();
    }).catch(function () {
      btn.disabled = false; btn.textContent = '登录';
      showLoginError('无法连接服务，请检查后端是否启动');
    });
  }

  function showLoginError(msg) {
    var box = document.querySelector('.admin-login-box');
    var existing = box.querySelector('.admin-login-error');
    if (existing) existing.remove();
    var div = document.createElement('div');
    div.className = 'admin-login-error';
    div.textContent = '⚠ ' + msg;
    box.appendChild(div);
    setTimeout(function () { div.remove(); }, 4000);
  }

  var currentTicketId = null;

  function loadTickets(showLoading, isPoll) {
    var filterSel = document.getElementById('admin-filter');
    var status = filterSel ? filterSel.value : '';
    var url = API + '/tickets?token=' + encodeURIComponent(getToken());
    if (status) url += '&status=' + encodeURIComponent(status);
    var listEl = document.getElementById('admin-tickets');
    if (showLoading && listEl) listEl.innerHTML = '<div class="service-loading"><span class="service-spinner"></span>加载中…</div>';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      if (data.code && data.code !== '0') { clearToken(); showLogin(); return; }
      renderTickets(data.tickets || []);
    }).catch(function () {
      if (isPoll) return;
      if (listEl) listEl.innerHTML = '<div class="service-error">⚠ 无法连接服务，请检查后端是否启动</div>';
    });
  }

  function renderTickets(tickets) {
    var listEl = document.getElementById('admin-tickets');
    var countEl = document.getElementById('admin-count');
    if (!listEl) return;

    var openCount = 0, repliedCount = 0, closedCount = 0;
    tickets.forEach(function (t) {
      if (t.status === 'open') openCount++;
      else if (t.status === 'replied') repliedCount++;
      else if (t.status === 'closed') closedCount++;
    });

    if (countEl) {
      countEl.innerHTML =
        '<span class="admin-stat">待回复 <strong>' + openCount + '</strong></span>' +
        '<span class="admin-stat">已回复 <strong>' + repliedCount + '</strong></span>' +
        '<span class="admin-stat">已关闭 <strong>' + closedCount + '</strong></span>' +
        '<span class="admin-stat-total">共 ' + tickets.length + ' 个工单</span>';
    }

    if (!tickets.length) {
      listEl.innerHTML = '<div class="admin-empty">暂无工单</div>';
      return;
    }
    var html = '';
    tickets.forEach(function (t) {
      var statusLabel = { open: '待回复', replied: '已回复', closed: '已关闭' }[t.status] || t.status;
      var replyBadge = t.hasAdminReply ? '' : '<span class="admin-badge">新</span>';
      html +=
        '<div class="admin-ticket-card" data-id="' + t.id + '">' +
          '<div class="admin-ticket-top">' +
            '<span class="admin-ticket-id">' + t.id + '</span>' +
            '<span class="admin-ticket-subject"></span>' +
            replyBadge +
            '<span class="service-status service-status--' + t.status + '">' + statusLabel + '</span>' +
          '</div>' +
          '<div class="admin-ticket-meta">' +
            '<span>用户：' + (t.userName || t.userId || '') + '</span>' +
            '<span>' + (t.category || '其他') + '</span>' +
            '<span>' + timeAgo(t.updatedAt || t.createdAt) + '</span>' +
            '<span>' + (t.msgCount || 0) + ' 条消息</span>' +
          '</div>' +
        '</div>';
    });
    listEl.innerHTML = html;
    var cards = listEl.querySelectorAll('.admin-ticket-card');
    tickets.forEach(function (t, i) {
      var card = cards[i];
      card.querySelector('.admin-ticket-subject').textContent = t.subject;
      card.addEventListener('click', function () { openTicket(t.id); });
    });
  }

  function openTicket(id) {
    currentTicketId = id;
    fetch(API + '/ticket?id=' + encodeURIComponent(id))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.code && data.code !== '0') { return; }
        renderDetail(data.ticket);
      })
      .catch(function () {});
  }

  function renderDetail(ticket) {
    var detailEl = document.getElementById('admin-detail');
    if (!detailEl) return;
    var statusLabel = { open: '待回复', replied: '已回复', closed: '已关闭' }[ticket.status] || ticket.status;
    var html =
      '<div class="admin-detail-head">' +
        '<button type="button" class="btn btn-ghost admin-detail-back" id="admin-detail-back">← 返回列表</button>' +
        '<span class="admin-ticket-id">' + ticket.id + '</span>' +
        '<span class="admin-detail-subject"></span>' +
        '<span class="service-status service-status--' + ticket.status + '">' + statusLabel + '</span>' +
      '</div>' +
      '<div class="admin-detail-meta">用户：' + (ticket.userName || '') + '（' + (ticket.userId || '') + '） · ' + (ticket.category || '其他') + ' · ' + (ticket.createdAt || '') + '</div>' +
      '<div class="service-chat" id="admin-chat"></div>';

    if (ticket.status !== 'closed') {
      html +=
        '<div class="service-reply-box">' +
          '<textarea id="admin-reply-text" rows="2" placeholder="回复用户…"></textarea>' +
          '<button type="button" class="btn btn-primary" id="admin-reply-btn">回复</button>' +
          '<button type="button" class="btn btn-ghost" id="admin-close-btn">关闭工单</button>' +
        '</div>';
    } else {
      html += '<div class="admin-closed-tip">此工单已关闭</div>';
    }

    detailEl.innerHTML = html;
    detailEl.querySelector('.admin-detail-subject').textContent = ticket.subject;

    var chat = detailEl.querySelector('#admin-chat');
    (ticket.messages || []).forEach(function (m) {
      var bubble = document.createElement('div');
      bubble.className = 'service-bubble service-bubble--' + (m.from === 'admin' ? 'admin' : 'user');
      bubble.innerHTML =
        '<div class="service-bubble-head">' +
          '<span class="service-bubble-from">' + (m.from === 'admin' ? '客服' : '用户') + '</span>' +
          '<span class="service-bubble-time">' + timeAgo(m.createdAt) + '</span>' +
        '</div>' +
        '<div class="service-bubble-content"></div>';
      bubble.querySelector('.service-bubble-content').textContent = m.content;
      chat.appendChild(bubble);
    });
    chat.scrollTop = chat.scrollHeight;

    var backBtn = detailEl.querySelector('#admin-detail-back');
    if (backBtn) backBtn.addEventListener('click', function () { detailEl.innerHTML = ''; currentTicketId = null; loadTickets(); });

    var replyBtn = detailEl.querySelector('#admin-reply-btn');
    if (replyBtn) {
      replyBtn.addEventListener('click', function () {
        var textarea = detailEl.querySelector('#admin-reply-text');
        var text = (textarea.value || '').trim();
        if (!text) { textarea.focus(); return; }
        replyBtn.disabled = true; replyBtn.textContent = '发送中…';
        postJson(API + '/reply', { ticketId: ticket.id, content: text, from: 'admin', token: getToken() })
          .then(function (data) {
            replyBtn.disabled = false; replyBtn.textContent = '回复';
            if (data.code && data.code !== '0') { return; }
            openTicket(ticket.id);
            loadTickets();
          })
          .catch(function () { replyBtn.disabled = false; replyBtn.textContent = '回复'; });
      });
    }

    var closeBtn = detailEl.querySelector('#admin-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', function () {
        if (!confirm('确认关闭此工单？')) return;
        postJson(API + '/close', { ticketId: ticket.id, token: getToken() })
          .then(function (data) {
            if (data.code && data.code !== '0') { return; }
            openTicket(ticket.id);
            loadTickets();
          })
          .catch(function () {});
      });
    }
  }

  function timeAgo(timeStr) {
    if (!timeStr) return '';
    try {
      var d = new Date(timeStr.replace(' ', 'T'));
      var diff = Date.now() - d.getTime();
      if (diff < 0) return timeStr;
      if (diff < 60000) return '刚刚';
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
      if (diff < 604800000) return Math.floor(diff / 86400000) + '天前';
      return timeStr.substring(5, 16);
    } catch (e) { return timeStr; }
  }

  function postJson(url, body) {
    return fetch(url, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    }).then(function (r) { return r.json(); });
  }

  if (typeof window !== 'undefined') {
    window.Admin = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();