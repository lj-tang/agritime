/**
 * 客服工单系统 - 用户端（T14）
 *9. admin.css (超管管理样式)刷新、未读提醒、时间格式化。
 */
(function () {
  var API = '/api/support';
  var userIdKey = 'agritime-user-id';
  var userNameKey = 'agritime-user-name';
  var readKey = 'agritime-ticket-read';

  var listEl, detailEl, formEl, newBtn, backBtn, refreshBtn, toolbarEl;
  var pollTimer = null;
  var currentTicketId = null;

  function getUserId() {
    var id = localStorage.getItem(userIdKey);
    if (!id) {
      id = 'u_' + Date.now() + '_' + Math.floor(Math.random() * 10000);
      localStorage.setItem(userIdKey, id);
    }
    return id;
  }

  function getUserName() { return localStorage.getItem(userNameKey) || ''; }
  function setUserName(name) { localStorage.setItem(userNameKey, name); }

  function getReadMap() {
    try { return JSON.parse(localStorage.getItem(readKey) || '{}'); } catch (e) { return {}; }
  }
  function markRead(ticketId, msgCount) {
    var m = getReadMap();
    m[ticketId] = msgCount;
    localStorage.setItem(readKey, JSON.stringify(m));
  }
  function isUnread(ticket) {
    var m = getReadMap();
    var lastRead = m[ticket.id] || 0;
    return (ticket.msgCount || 0) > lastRead && ticket.hasAdminReply;
  }

  function init() {
    listEl = document.getElementById('service-list');
    detailEl = document.getElementById('service-detail');
    formEl = document.getElementById('service-form');
    newBtn = document.getElementById('service-new-btn');
    backBtn = document.getElementById('service-back-btn');
    refreshBtn = document.getElementById('service-refresh-btn');
    toolbarEl = document.getElementById('service-toolbar');
    if (!listEl || !formEl) return;

    formEl.addEventListener('submit', onSubmit);
    newBtn.addEventListener('click', showForm);
    backBtn.addEventListener('click', showList);
    if (refreshBtn) refreshBtn.addEventListener('click', function () { refreshList(true); });

    var nameInput = document.getElementById('service-user-name');
    if (nameInput) {
      nameInput.value = getUserName();
      nameInput.addEventListener('change', function () { setUserName(nameInput.value); });
    }

    showList();
    refreshList();
    startPolling();
  }

  function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(function () {
      if (listEl && listEl.style.display !== 'none' && !currentTicketId) {
        refreshList(false, true);
      }
    }, 30000);
  }

  function showList() {
    currentTicketId = null;
    if (listEl) listEl.style.display = '';
    if (detailEl) detailEl.style.display = 'none';
    if (formEl) formEl.style.display = 'none';
    if (newBtn) newBtn.style.display = '';
    if (backBtn) backBtn.style.display = 'none';
    if (refreshBtn) refreshBtn.style.display = '';
  }

  function showForm() {
    if (listEl) listEl.style.display = 'none';
    if (detailEl) detailEl.style.display = 'none';
    if (formEl) formEl.style.display = '';
    if (newBtn) newBtn.style.display = 'none';
    if (backBtn) backBtn.style.display = '';
    if (refreshBtn) refreshBtn.style.display = 'none';
  }

  function showDetail() {
    if (listEl) listEl.style.display = 'none';
    if (detailEl) detailEl.style.display = '';
    if (formEl) formEl.style.display = 'none';
    if (newBtn) newBtn.style.display = 'none';
    if (backBtn) backBtn.style.display = '';
    if (refreshBtn) refreshBtn.style.display = 'none';
  }

  function refreshList(showLoading, isPoll) {
    var url = API + '/tickets?userId=' + encodeURIComponent(getUserId());
    if (showLoading && listEl) listEl.innerHTML = '<div class="service-loading"><span class="service-spinner"></span>加载中…</div>';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      if (data.code && data.code !== '0') {
        if (listEl) listEl.innerHTML = '<div class="service-empty">' + (data.message || '加载失败') + '</div>';
        return;
      }
      renderList(data.tickets || []);
    }).catch(function () {
      if (isPoll) return;
      if (listEl) listEl.innerHTML = '<div class="service-error">⚠ 无法连接客服服务，<a href="javascript:void(0)" onclick="window.Service.refreshList(true)">点击重试</a></div>';
    });
  }

  function renderList(tickets) {
    if (!listEl) return;
    if (!tickets.length) {
      listEl.innerHTML =
        '<div class="service-empty">' +
          '<div class="service-empty-icon">💬</div>' +
          '<div class="service-empty-text">还没有咨询记录</div>' +
          '<button type="button" class="btn btn-primary" onclick="document.getElementById(\'service-new-btn\').click()">+ 发起咨询</button>' +
        '</div>';
      return;
    }
    var html = '';
    tickets.forEach(function (t) {
      var statusClass = 'service-status--' + t.status;
      var statusLabel = { open: '待回复', replied: '已回复', closed: '已关闭' }[t.status] || t.status;
      var unread = isUnread(t);
      var unreadBadge = unread ? '<span class="service-unread-dot"></span>' : '';
      html +=
        '<div class="service-ticket-card' + (unread ? ' service-ticket-card--unread' : '') + '" data-id="' + t.id + '">' +
          unreadBadge +
          '<div class="service-ticket-top">' +
            '<span class="service-ticket-id">' + t.id + '</span>' +
            '<span class="service-ticket-subject"></span>' +
            '<span class="service-status ' + statusClass + '">' + statusLabel + '</span>' +
          '</div>' +
          '<div class="service-ticket-meta">' +
            '<span>' + (t.category || '其他') + '</span>' +
            '<span>' + timeAgo(t.updatedAt || t.createdAt) + '</span>' +
            '<span>' + (t.msgCount || 0) + ' 条消息</span>' +
          '</div>' +
        '</div>';
    });
    listEl.innerHTML = html;
    var cards = listEl.querySelectorAll('.service-ticket-card');
    tickets.forEach(function (t, i) {
      var card = cards[i];
      card.querySelector('.service-ticket-subject').textContent = t.subject;
      card.addEventListener('click', function () { openTicket(t.id); });
    });
  }

  function openTicket(id) {
    currentTicketId = id;
    fetch(API + '/ticket?id=' + encodeURIComponent(id))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.code && data.code !== '0') { showInlineError(detailEl, data.message || '加载失败'); return; }
        renderDetail(data.ticket);
        markRead(id, (data.ticket.messages || []).length);
        showDetail();
      })
      .catch(function () { showInlineError(detailEl, '无法连接客服服务'); });
  }

  function renderDetail(ticket) {
    if (!detailEl) return;
    var statusLabel = { open: '待回复', replied: '已回复', closed: '已关闭' }[ticket.status] || ticket.status;
    var html =
      '<div class="service-detail-head">' +
        '<span class="service-detail-id">' + ticket.id + '</span>' +
        '<span class="service-detail-subject"></span>' +
        '<span class="service-status service-status--' + ticket.status + '">' + statusLabel + '</span>' +
      '</div>' +
      '<div class="service-detail-meta">' + (ticket.category || '其他') + ' · ' + (ticket.createdAt || '') + '</div>' +
      '<div class="service-chat" id="service-chat"></div>';

    if (ticket.status !== 'closed') {
      html +=
        '<div class="service-reply-box">' +
          '<textarea id="service-reply-text" rows="2" placeholder="补充信息或追问…"></textarea>' +
          '<button type="button" class="btn btn-primary" id="service-reply-btn">发送</button>' +
        '</div>';
    } else {
      html += '<div class="service-closed-tip">此工单已关闭，如有需要请发起新咨询</div>';
    }

    detailEl.innerHTML = html;
    detailEl.querySelector('.service-detail-subject').textContent = ticket.subject;

    var chat = detailEl.querySelector('#service-chat');
    (ticket.messages || []).forEach(function (m) {
      var bubble = document.createElement('div');
      bubble.className = 'service-bubble service-bubble--' + (m.from === 'admin' ? 'admin' : 'user');
      bubble.innerHTML =
        '<div class="service-bubble-head">' +
          '<span class="service-bubble-from">' + (m.from === 'admin' ? '客服' : '我') + '</span>' +
          '<span class="service-bubble-time">' + timeAgo(m.createdAt) + '</span>' +
        '</div>' +
        '<div class="service-bubble-content"></div>';
      bubble.querySelector('.service-bubble-content').textContent = m.content;
      chat.appendChild(bubble);
    });
    chat.scrollTop = chat.scrollHeight;

    var replyBtn = detailEl.querySelector('#service-reply-btn');
    if (replyBtn) {
      replyBtn.addEventListener('click', function () {
        var textarea = detailEl.querySelector('#service-reply-text');
        var text = (textarea.value || '').trim();
        if (!text) { textarea.focus(); return; }
        replyBtn.disabled = true;
        replyBtn.textContent = '发送中…';
        postJson(API + '/reply', { ticketId: ticket.id, content: text, from: 'user' })
          .then(function (data) {
            if (data.code && data.code !== '0') { showInlineError(detailEl, data.message || '发送失败'); replyBtn.disabled = false; replyBtn.textContent = '发送'; return; }
            openTicket(ticket.id);
            refreshList();
          })
          .catch(function () { showInlineError(detailEl, '发送失败，请重试'); replyBtn.disabled = false; replyBtn.textContent = '发送'; });
      });
    }
  }

  function onSubmit(e) {
    e.preventDefault();
    var subject = (document.getElementById('service-subject').value || '').trim();
    var category = (document.getElementById('service-category').value || '').trim();
    var content = (document.getElementById('service-content').value || '').trim();
    var userName = (document.getElementById('service-user-name').value || '').trim();
    if (!subject || !content) { showInlineError(formEl, '请填写标题和问题描述'); return; }
    if (userName) setUserName(userName);

    var btn = formEl.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = '提交中…';

    postJson(API + '/ticket', {
      userId: getUserId(), userName: userName, subject: subject, category: category, content: content
    }).then(function (data) {
      btn.disabled = false; btn.textContent = '提交工单';
      if (data.code && data.code !== '0') { showInlineError(formEl, data.message || '提交失败'); return; }
      formEl.reset();
      var nameInput = document.getElementById('service-user-name');
      if (nameInput) nameInput.value = getUserName();
      showList();
      refreshList();
    }).catch(function () {
      btn.disabled = false; btn.textContent = '提交工单';
      showInlineError(formEl, '提交失败，请稍后重试');
    });
  }

  function showInlineError(container, msg) {
    if (!container) { alert(msg); return; }
    var existing = container.querySelector('.service-inline-error');
    if (existing) existing.remove();
    var div = document.createElement('div');
    div.className = 'service-inline-error';
    div.textContent = '⚠ ' + msg;
    container.prepend(div);
    setTimeout(function () { div.remove(); }, 4000);
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
    window.Service = { init: init, refreshList: refreshList };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();