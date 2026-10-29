/**
 * 客服工单 —— 超管端（V4 T14）
 * 复用 T10 账号系统：进入 admin 板块时用 Cookie 里的登录态判断是不是超管，
 * 是超管才能看全部工单并回复/关闭。不做独立的「密码登录」表单 —— 那会造出第二套超管。
 */
(function () {
  var API = '/api/support';
  var STATUS_LABEL = { open: '待回复', replied: '已回复', closed: '已关闭' };

  var loginBox, panel, listBox, detailBox, filterSel, countEl, refreshBtn;

  function init() {
    loginBox = document.getElementById('admin-login');
    panel = document.getElementById('admin-panel');
    listBox = document.getElementById('admin-tickets');
    detailBox = document.getElementById('admin-detail');
    filterSel = document.getElementById('admin-filter');
    countEl = document.getElementById('admin-count');
    refreshBtn = document.getElementById('admin-refresh');
    if (!panel || !listBox) return;

    refreshBtn.addEventListener('click', load);
    filterSel.addEventListener('change', load);
    // 老的「密码登录」表单改为提示：去账号系统登录超管
    var oldForm = document.getElementById('admin-login-form');
    if (oldForm) {
      oldForm.addEventListener('submit', function (e) { e.preventDefault(); boot(); });
    }
    boot();
  }

  function boot() {
    // 先确认身份（走账号系统 Cookie）
    fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (d) {
        var user = d.data && d.data.user;
        if (user && user.role === 'admin') {
          loginBox.style.display = 'none';
          panel.style.display = 'block';
          load();
        } else {
          loginBox.style.display = 'block';
          panel.style.display = 'none';
          loginBox.querySelector('p').textContent =
            user ? '当前账号不是超管，请用超管账号登录后查看工单'
                 : '请先在登录页用超管账号登录，再回来管理工单';
        }
      })
      .catch(function () {
        loginBox.style.display = 'block';
        panel.style.display = 'none';
      });
  }

  function load() {
    var status = filterSel ? filterSel.value : '';
    var url = API + '/tickets' + (status ? '?status=' + encodeURIComponent(status) : '');
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (d) {
        if (d.code !== 0) throw new Error(d.message || '加载失败');
        var list = d.data || [];
        if (countEl) countEl.textContent = '共 ' + list.length + ' 条';
        renderList(list);
      })
      .catch(function (e) { listBox.innerHTML = '<div class="service-empty">加载失败：' + e.message + '</div>'; });
  }

  function renderList(list) {
    listBox.innerHTML = '';
    detailBox.innerHTML = '';
    if (!list.length) {
      listBox.innerHTML = '<div class="service-empty">没有工单</div>';
      return;
    }
    list.forEach(function (t) { listBox.appendChild(renderItem(t)); });
  }

  function renderItem(t) {
    var item = document.createElement('div');
    item.className = 'service-item';
    item.innerHTML =
      '<div class="service-item-head">' +
      '<span class="service-item-subject"></span>' +
      '<span class="service-item-status service-status--' + (t.status || 'open') + '">' + (STATUS_LABEL[t.status] || t.status) + '</span>' +
      '</div>' +
      '<div class="service-item-meta">' + (t.userName || '访客') + ' · ' + (t.category || '') + ' · ' + (t.createdAt || '') + '</div>';
    item.querySelector('.service-item-subject').textContent = t.subject;
    item.addEventListener('click', function () { openDetail(t.id); });
    return item;
  }

  function openDetail(id) {
    fetch(API + '/ticket?id=' + encodeURIComponent(id), { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (d) {
        if (d.code !== 0) throw new Error(d.message || '加载失败');
        renderDetail(d.data);
      })
      .catch(function (e) { detailBox.innerHTML = '<div class="service-empty">' + e.message + '</div>'; });
  }

  function renderDetail(t) {
    detailBox.innerHTML = '';
    var head = document.createElement('div');
    head.className = 'service-detail-head';
    head.innerHTML = '<div class="service-detail-subject"></div>' +
      '<div class="service-detail-meta">' + (t.userName || '访客') + ' · ' + (t.category || '') + ' · ' + (t.createdAt || '') + '</div>';
    head.querySelector('.service-detail-subject').textContent = t.subject;
    detailBox.appendChild(head);

    detailBox.appendChild(bubble(t.userName, t.content, t.createdAt, 'user'));
    (t.replies || []).forEach(function (r) {
      detailBox.appendChild(bubble(r.who, r.content, r.time, r.from === 'admin' ? 'admin' : 'user'));
    });

    if (t.status !== 'closed') {
      var replyBox = document.createElement('div');
      replyBox.className = 'service-reply-box';
      var ta = document.createElement('textarea');
      ta.placeholder = '回复用户…';
      ta.rows = 2;
      var sendBtn = document.createElement('button');
      sendBtn.type = 'button';
      sendBtn.className = 'btn btn-primary';
      sendBtn.textContent = '回复';
      sendBtn.addEventListener('click', function () {
        var content = ta.value.trim();
        if (!content) return;
        send(API + '/reply', { ticketId: t.id, content: content }, function (d) { renderDetail(d.data); });
      });
      var closeBtn = document.createElement('button');
      closeBtn.type = 'button';
      closeBtn.className = 'btn btn-ghost';
      closeBtn.textContent = '关闭工单';
      closeBtn.addEventListener('click', function () {
        send(API + '/close', { ticketId: t.id }, function (d) { renderDetail(d.data); load(); });
      });
      replyBox.appendChild(ta);
      replyBox.appendChild(sendBtn);
      replyBox.appendChild(closeBtn);
      detailBox.appendChild(replyBox);
    }
  }

  function bubble(who, content, time, side) {
    var b = document.createElement('div');
    b.className = 'service-bubble service-bubble--' + side;
    b.innerHTML = '<div class="service-bubble-who">' + (who || '访客') + ' · ' + (time || '') + '</div>' +
      '<div class="service-bubble-text"></div>';
    b.querySelector('.service-bubble-text').textContent = content;
    return b;
  }

  function send(url, body, done) {
    fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (d) {
        if (d.code !== 0) throw new Error(d.message || '操作失败');
        done(d);
      })
      .catch(function (e) { alert(e.message); });
  }

  if (typeof window !== 'undefined') {
    window.AdminTickets = { init: init, boot: boot };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
