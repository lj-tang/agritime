/**
 * 反馈（V4 T14 / V5 T18）—— 用户端
 * 提交工单 → 分页列表（状态筛选）→ 详情对话 → 追问回复。
 *
 * T18 之前列表一次返回全部工单，也没有未读：反馈回了话用户不知道。
 * 现在接口带 page/pageSize/total/statusCounts/unreadCount，前端补三样：
 *   · 状态页签（待处理/处理中/已解决）与每类数量；
 *   · 分页条，超过一页才出现；
 *   · 未读圆点 + 侧栏「反馈」角标，进详情即清当前未读。
 *
 * 鉴权复用账号系统 Cookie；未登录仍可按本浏览器记录查看自己提交过的工单。
 */
(function () {
  var API = '/api/support';
  var STATUS_LABEL = {
    open: '待处理',
    processing: '处理中',
    resolved: '已解决',
    replied: '处理中',   // V4 旧值
    closed: '已解决'     // V4 旧值
  };

  var pane, listBox, detailBox, form, backBtn, newBtn;
  var tabsEl, toolbarEl, unreadEl, pagerEl, pageInfoEl, prevBtn, nextBtn;
  var state = {
    status: 'all',
    page: 1,
    pageSize: 8,
    total: 0,
    totalPages: 1,
    unread: 0,
    anonymous: false
  };

  function init() {
    pane = document.getElementById('service-pane');
    listBox = document.getElementById('service-list');
    detailBox = document.getElementById('service-detail');
    form = document.getElementById('service-form');
    backBtn = document.getElementById('service-back-btn');
    newBtn = document.getElementById('service-new-btn');
    tabsEl = document.getElementById('service-tabs');
    toolbarEl = document.getElementById('service-toolbar');
    unreadEl = document.getElementById('service-unread');
    pagerEl = document.getElementById('service-pager');
    pageInfoEl = document.getElementById('service-page-info');
    prevBtn = document.getElementById('service-prev');
    nextBtn = document.getElementById('service-next');
    if (!pane || !listBox || !form) return;

    newBtn.addEventListener('click', showForm);
    backBtn.addEventListener('click', function () { loadTickets(1, state.status); });
    form.addEventListener('submit', onSubmit);

    if (tabsEl) {
      tabsEl.addEventListener('click', function (ev) {
        var btn = ev.target.closest ? ev.target.closest('.service-tab') : null;
        if (!btn) return;
        var status = btn.getAttribute('data-status') || 'all';
        if (status === state.status) return;
        loadTickets(1, status);
      });
    }
    if (prevBtn) prevBtn.addEventListener('click', function () { if (state.page > 1) loadTickets(state.page - 1); });
    if (nextBtn) nextBtn.addEventListener('click', function () { if (state.page < state.totalPages) loadTickets(state.page + 1); });

    // 侧栏会因权限检查重渲染，重建后把未读角标补回去
    var nav = document.getElementById('sidebar-nav');
    if (nav && window.MutationObserver) {
      new MutationObserver(function () { updateSidebarBadge(state.unread); })
        .observe(nav, { childList: true });
    }

    loadTickets(1, 'all');
  }

  // ---------- 列表 ----------
  function loadTickets(page, status) {
    if (typeof page === 'number') state.page = page;
    if (status) state.status = status;
    showListLoading();

    var url = API + '/tickets?status=' + encodeURIComponent(state.status)
      + '&page=' + state.page + '&pageSize=' + state.pageSize;

    get(url)
      .then(function (body) {
        var d = body.data;
        // 兼容旧接口：直接返回数组
        if (Array.isArray(d)) {
          d = {
            items: d, total: d.length, page: 1, pageSize: d.length || state.pageSize,
            totalPages: 1, unreadCount: 0, anonymous: false,
            statusCounts: { all: d.length, open: 0, processing: 0, resolved: 0 }
          };
        }
        d = d || {};
        state.anonymous = !!d.anonymous;
        state.total = Number(d.total) || 0;
        state.totalPages = Number(d.totalPages) || 1;
        state.page = Number(d.page) || 1;
        state.unread = Number(d.unreadCount) || 0;
        updateTabs(d.statusCounts, state.unread);
        updateSidebarBadge(state.unread);

        var items = Array.isArray(d.items) ? d.items : [];
        if (!items.length) {
          if (state.anonymous) { loadAnonymousMine(); return; }
          renderEmpty();
          return;
        }
        renderList(items);
      })
      .catch(function (e) {
        if (window.UiState) {
          window.UiState.error(listBox, {
            text: '工单加载失败：' + (e.message || '网络异常'),
            hint: '已经提交的工单不会丢，网络恢复后点下面重新加载。',
            onRetry: loadTickets
          });
        } else {
          listBox.innerHTML = '<div class="service-empty">加载失败：' + e.message + '</div>';
        }
      });
  }

  function showListLoading() {
    if (toolbarEl) toolbarEl.hidden = false;
    if (pagerEl) pagerEl.hidden = true;
    listBox.style.display = 'block';
  }

  function renderList(items) {
    listBox.innerHTML = '';
    items.forEach(function (t) { listBox.appendChild(renderTicketItem(t)); });
    renderPager();
  }

  function renderEmpty() {
    if (state.status === 'all') {
      if (window.UiState) {
        window.UiState.empty(listBox, {
          icon: '💬',
          text: '还没有咨询记录。',
          actionText: '提第一个问题',
          onAction: function () { if (newBtn) newBtn.click(); }
        });
      } else {
        listBox.innerHTML = '<div class="service-empty">还没有咨询记录，点「新建咨询」提第一个问题</div>';
      }
    } else if (window.UiState) {
      window.UiState.empty(listBox, {
        icon: '💬',
        text: '这个状态下没有工单。',
        actionText: '看全部工单',
        onAction: function () { loadTickets(1, 'all'); }
      });
    } else {
      listBox.innerHTML = '<div class="service-empty">这个状态下没有工单</div>';
    }
    renderPager();
  }

  /** 未登录：列表接口不给数据，用本浏览器记录过的工单 id 逐个查详情 */
  function loadAnonymousMine() {
    var mine = myIds();
    if (!mine.length) { renderEmpty(); return; }
    Promise.all(mine.map(function (id) {
      return get(API + '/ticket?id=' + encodeURIComponent(id)).then(function (d) {
        return d.data;
      }).catch(function () { return null; });
    })).then(function (rows) {
      var valid = rows.filter(Boolean).map(function (t) {
        t.replyCount = (t.replies || []).length;
        return t;
      });
      if (!valid.length) { renderEmpty(); return; }
      if (toolbarEl) toolbarEl.hidden = true;
      if (pagerEl) pagerEl.hidden = true;
      renderList(valid);
    });
  }

  function renderTicketItem(t) {
    var item = document.createElement('div');
    item.className = 'service-item' + (t.unread ? ' service-item--unread' : '');
    item.innerHTML =
      '<div class="service-item-head">' +
      '<span class="service-unread-dot" aria-label="未读"></span>' +
      '<span class="service-item-subject"></span>' +
      '<span class="service-item-status service-status--' + (t.status || 'open') + '">'
      + statusLabel(t.status) + '</span>' +
      '</div>' +
      '<div class="service-item-meta"></div>';
    if (!t.unread) {
      var dot = item.querySelector('.service-unread-dot');
      if (dot) dot.remove();
    }
    item.querySelector('.service-item-subject').textContent = t.subject;
    var meta = (t.category || '其他') + ' · ' + (t.createdAt || '') + ' · ' + (t.replyCount || 0) + ' 条回复';
    if (t.lastReplyAt) meta += ' · 最后回复 ' + t.lastReplyAt;
    item.querySelector('.service-item-meta').textContent = meta;
    item.addEventListener('click', function () { openDetail(t.id); });
    return item;
  }

  function renderPager() {
    if (!pagerEl) return;
    pagerEl.hidden = state.anonymous || state.totalPages <= 1;
    if (pageInfoEl) {
      pageInfoEl.textContent = '第 ' + state.page + ' / ' + state.totalPages + ' 页 · 共 ' + state.total + ' 条';
    }
    if (prevBtn) prevBtn.disabled = state.page <= 1;
    if (nextBtn) nextBtn.disabled = state.page >= state.totalPages;
  }

  function updateTabs(counts, unread) {
    if (tabsEl) {
      tabsEl.querySelectorAll('.service-tab').forEach(function (tab) {
        var key = tab.getAttribute('data-status');
        tab.classList.toggle('is-active', key === state.status);
        var badge = tab.querySelector('[data-count]');
        if (badge) badge.textContent = counts && counts[key] != null ? counts[key] : 0;
      });
    }
    if (unreadEl) {
      unreadEl.textContent = unread ? unread + ' 条未读' : '';
      unreadEl.hidden = !unread;
    }
  }

  function updateSidebarBadge(n) {
    var btn = document.querySelector('.sidebar-item[data-module="service"]');
    if (!btn) return;
    var badge = btn.querySelector('.service-sidebar-badge');
    if (!badge) {
      badge = document.createElement('span');
      badge.className = 'service-sidebar-badge';
      btn.appendChild(badge);
    }
    badge.textContent = n > 99 ? '99+' : String(n);
    badge.hidden = !n;
  }

  function statusLabel(status) {
    return STATUS_LABEL[status] || status || '待处理';
  }

  // ---------- 详情 ----------
  function openDetail(id) {
    get(API + '/ticket?id=' + encodeURIComponent(id))
      .then(function (d) {
        renderDetail(d.data);
        // 详情接口已清掉这条的未读，重新取一次总数刷新角标与页签
        syncUnread();
      })
      .catch(function (e) {
        if (window.UiState) {
          window.UiState.error(detailBox, {
            text: '工单详情加载失败：' + (e.message || '网络异常'),
            onRetry: function () { openDetail(id); }
          });
        } else {
          detailBox.innerHTML = '<div class="service-empty">加载失败：' + e.message + '</div>';
        }
      });
  }

  function renderDetail(t) {
    if (toolbarEl) toolbarEl.hidden = true;
    if (pagerEl) pagerEl.hidden = true;
    listBox.style.display = 'none';
    form.style.display = 'none';
    backBtn.style.display = 'inline-block';
    detailBox.style.display = 'block';
    detailBox.innerHTML = '';

    var head = document.createElement('div');
    head.className = 'service-detail-head';
    head.innerHTML = '<div class="service-detail-subject"></div>' +
      '<div class="service-detail-meta">' + (t.category || '') + ' · ' + (t.createdAt || '') + ' · ' +
      '<span class="service-item-status service-status--' + (t.status || 'open') + '">'
      + statusLabel(t.status) + '</span>' +
      '<span class="service-detail-last">' + (t.lastReplyAt ? '最后回复 ' + t.lastReplyAt : '暂无回复') + '</span>' +
      '</div>';
    head.querySelector('.service-detail-subject').textContent = t.subject;
    detailBox.appendChild(head);

    detailBox.appendChild(bubble(t.userName, t.content, t.createdAt, 'user'));

    var replies = t.replies || [];
    replies.forEach(function (r) {
      detailBox.appendChild(bubble(r.who, r.content, r.time, r.from === 'admin' ? 'admin' : 'user'));
    });

    if (t.status !== 'resolved' && t.status !== 'closed') {
      var replyBox = document.createElement('div');
      replyBox.className = 'service-reply-box';
      var ta = document.createElement('textarea');
      ta.id = 'service-reply-input';
      ta.placeholder = '继续追问…';
      ta.rows = 2;
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-primary';
      btn.textContent = '发送';
      btn.addEventListener('click', function () {
        var content = ta.value.trim();
        if (!content) return;
        btn.disabled = true;
        btn.textContent = '发送中…';
        post(API + '/reply', { ticketId: t.id, content: content })
          .then(function (d) { btn.disabled = false; btn.textContent = '发送'; renderDetail(d.data); syncUnread(); })
          .catch(function (e) { btn.disabled = false; btn.textContent = '发送'; alert('发送失败：' + e.message); });
      });
      replyBox.appendChild(ta);
      replyBox.appendChild(btn);
      detailBox.appendChild(replyBox);
    } else {
      var done = document.createElement('p');
      done.className = 'service-resolved-tip';
      done.textContent = '这个工单已解决；如果还有问题，可以直接新建一条咨询。';
      detailBox.appendChild(done);
    }
  }

  function bubble(who, content, time, side) {
    var b = document.createElement('div');
    b.className = 'service-bubble service-bubble--' + side;
    var whoDiv = document.createElement('div');
    whoDiv.className = 'service-bubble-who';
    whoDiv.textContent = (who || '访客') + ' · ' + (time || '');
    var text = document.createElement('div');
    text.className = 'service-bubble-text';
    text.textContent = content;
    b.appendChild(whoDiv);
    b.appendChild(text);
    return b;
  }

  /** 只刷新未读数与页签计数，不打断当前详情视图 */
  function syncUnread() {
    get(API + '/tickets?status=all&page=1&pageSize=1')
      .then(function (body) {
        var d = body.data || {};
        state.unread = Number(d.unreadCount) || 0;
        updateTabs(d.statusCounts, state.unread);
        updateSidebarBadge(state.unread);
      })
      .catch(function () { /* 角标刷新失败不影响主流程 */ });
  }

  // ---------- 表单 ----------
  function showForm() {
    if (toolbarEl) toolbarEl.hidden = true;
    if (pagerEl) pagerEl.hidden = true;
    listBox.style.display = 'none';
    detailBox.style.display = 'none';
    backBtn.style.display = 'inline-block';
    form.style.display = 'block';
    form.reset();
  }

  function showList() {
    form.style.display = 'none';
    detailBox.style.display = 'none';
    backBtn.style.display = 'none';
    listBox.style.display = 'block';
    if (toolbarEl) toolbarEl.hidden = false;
  }

  function onSubmit(e) {
    e.preventDefault();
    var subject = document.getElementById('service-subject').value.trim();
    var category = document.getElementById('service-category').value;
    var content = document.getElementById('service-content').value.trim();
    var userName = document.getElementById('service-user-name').value.trim();
    if (!subject || !content) { alert('请填写标题和描述'); return; }
    var submitBtn = e.target.querySelector('button[type=submit]');
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = '提交中…'; }
    post(API + '/ticket', { subject: subject, category: category, content: content, userName: userName })
      .then(function (d) {
        form.reset();
        rememberMine(d.data.id);
        renderSubmitted(d.data);
      })
      .catch(function (err) {
        if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = '提交工单'; }
        alert('提交失败：' + err.message);
      });
  }

  function renderSubmitted(t) {
    if (toolbarEl) toolbarEl.hidden = true;
    if (pagerEl) pagerEl.hidden = true;
    listBox.style.display = 'none';
    form.style.display = 'none';
    backBtn.style.display = 'inline-block';
    detailBox.style.display = 'block';
    detailBox.innerHTML = '';
    var ok = document.createElement('div');
    ok.className = 'service-submitted';
    ok.innerHTML = '<div class="service-submitted-title">工单已提交 ✓</div>' +
      '<div class="service-submitted-sub">我们会尽快回复，你可以在「我的咨询」里查看进展（登录后可在任意设备同步）</div>';
    detailBox.appendChild(ok);
    detailBox.appendChild(bubble(t.userName || '访客', t.content, t.createdAt, 'user'));
  }

  // 匿名用户后端无法稳定识别，前端用 localStorage 记下自己提交过的工单，
  // 这样同一浏览器里能追溯到自己的咨询
  var MINE_KEY = 'agritime.my-tickets.v1';
  function myIds() {
    try { return JSON.parse(localStorage.getItem(MINE_KEY) || '[]'); } catch (e) { return []; }
  }
  function rememberMine(id) {
    try {
      var arr = myIds();
      if (arr.indexOf(id) === -1) { arr.push(id); localStorage.setItem(MINE_KEY, JSON.stringify(arr)); }
    } catch (e) { }
  }

  // ---------- 工具 ----------
  /**
   * 匿名访客 ID：同一出口 IP 下（校园网、公司 NAT）大家共用一个公网 IP，
   * 只按 IP 认人会让互不相识的用户看到彼此的工单。这里给每个浏览器一个 ID，
   * 随请求头带上，后端优先用它区分匿名身份。
   */
  var VISITOR_KEY = 'agritime.visitorId';
  function visitorId() {
    try {
      var v = localStorage.getItem(VISITOR_KEY);
      if (v && /^[A-Za-z0-9_-]{8,64}$/.test(v)) return v;
      var buf = new Uint8Array(16);
      (window.crypto || window.msCrypto).getRandomValues(buf);
      v = Array.prototype.map.call(buf, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
      localStorage.setItem(VISITOR_KEY, v);
      return v;
    } catch (e) {
      return '';
    }
  }

  function headers(extra) {
    var h = extra || {};
    var vid = visitorId();
    if (vid) h['X-Visitor-Id'] = vid;
    return h;
  }

  function get(url) {
    return fetch(url, { credentials: 'same-origin', headers: headers() }).then(handle);
  }
  function post(url, body) {
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body)
    }).then(handle);
  }
  function handle(res) {
    return res.json().catch(function () { return {}; }).then(function (d) {
      if (d.code !== 0) throw new Error(d.message || ('HTTP ' + res.status));
      return d;
    });
  }

  if (typeof window !== 'undefined') {
    window.Service = { init: init, loadTickets: loadTickets };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
