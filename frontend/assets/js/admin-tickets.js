/**
 * 超管 · 客服工单独立页（V5 T18）
 *
 * 以前后台只有一句"工单管理在主页的客服面板里"，管理员得回业务页面处理，
 * 和普通用户看到的界面混在一起。这里做成独立页：
 *   · 状态统计 + 页签筛选 + 分页，队列一眼看清；
 *   · 右侧详情抽屉，直接回复、开始处理、标记已解决、重新打开；
 *   · 打开详情即清管理员未读，回复后用户端出现未读与最新状态。
 *
 * 权限由服务端判定，前端只做「非管理员就别让他看见入口」这一层。
 * 初始密码未改的先踢去改密，和 admin.html 的守卫保持一致。
 */
(function () {
  var API = '/api/support';
  var STATUS_LABEL = { open: '待处理', processing: '处理中', resolved: '已解决' };

  var state = {
    status: 'all',
    page: 1,
    pageSize: 12,
    total: 0,
    totalPages: 1,
    unread: 0,
    currentId: null
  };
  var everLoaded = false;

  var gateEl, gateTextEl, contentEl, whoEl;
  var statsEl, tabsEl, unreadEl, bodyEl, emptyEl, tableEl;
  var pagerEl, pageInfoEl, prevBtn, nextBtn;
  var detailEl, maskEl, detailSubject, detailMeta, threadEl, actionsEl, replyInput, replyBtn, closeBtn;

  /** 轻提示，兜底 alert —— ui-state.js 里的全局 showToast 会覆盖它 */
  function toast(msg, kind) {
    if (window.showToast) window.showToast(msg, kind);
    else if (window.alert) window.alert(msg);
  }

  function init() {
    gateEl = document.getElementById('ticket-gate');
    gateTextEl = document.getElementById('ticket-gate-text');
    contentEl = document.getElementById('ticket-content');
    whoEl = document.getElementById('ticket-who');
    statsEl = document.getElementById('ticket-stats');
    tabsEl = document.getElementById('ticket-tabs');
    unreadEl = document.getElementById('ticket-unread');
    bodyEl = document.getElementById('ticket-body');
    emptyEl = document.getElementById('ticket-empty');
    tableEl = document.getElementById('ticket-table');
    pagerEl = document.getElementById('ticket-pager');
    pageInfoEl = document.getElementById('ticket-page-info');
    prevBtn = document.getElementById('ticket-prev');
    nextBtn = document.getElementById('ticket-next');

    detailEl = document.getElementById('ticket-detail');
    maskEl = document.getElementById('ticket-mask');
    detailSubject = document.getElementById('ticket-detail-subject');
    detailMeta = document.getElementById('ticket-detail-meta');
    threadEl = document.getElementById('ticket-thread');
    actionsEl = document.getElementById('ticket-actions');
    replyInput = document.getElementById('ticket-reply');
    replyBtn = document.getElementById('ticket-reply-btn');
    closeBtn = document.getElementById('ticket-detail-close');
    if (!contentEl) return;

    if (tabsEl) {
      tabsEl.addEventListener('click', function (ev) {
        var btn = ev.target.closest ? ev.target.closest('.service-tab') : null;
        if (!btn) return;
        var status = btn.getAttribute('data-status') || 'all';
        if (status === state.status) return;
        load(1, status);
      });
    }
    if (prevBtn) prevBtn.addEventListener('click', function () { if (state.page > 1) load(state.page - 1); });
    if (nextBtn) nextBtn.addEventListener('click', function () { if (state.page < state.totalPages) load(state.page + 1); });
    if (closeBtn) closeBtn.addEventListener('click', closeDetail);
    if (maskEl) maskEl.addEventListener('click', closeDetail);
    if (replyBtn) replyBtn.addEventListener('click', sendReply);
    if (replyInput) {
      replyInput.addEventListener('keydown', function (ev) {
        if ((ev.ctrlKey || ev.metaKey) && ev.key === 'Enter') sendReply();
      });
    }

    boot();
  }

  function boot() {
    fetch('/api/auth/me', { credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        var data = d && d.data;
        var u = data && data.user;
        // 初始密码未改：先去改密，改完再回来
        if (data && String(data.mustChangePassword) === 'true') {
          location.replace('./account.html?force=1');
          return;
        }
        if (!u || u.role !== 'admin') {
          showGate('当前账号不是管理员，无法进入工单后台。');
          return;
        }
        if (whoEl) whoEl.textContent = (u.nickname || u.username) + '（管理员）';
        contentEl.hidden = false;
        load(1, 'all');
      })
      .catch(function () { showGate('登录状态读取失败，请重新登录。'); });
  }

  function showGate(msg) {
    if (gateTextEl) gateTextEl.textContent = msg;
    if (gateEl) gateEl.hidden = false;
    contentEl.hidden = true;
  }

  // ---------------- 列表 ----------------

  function load(page, status) {
    if (typeof page === 'number') state.page = page;
    var fresh = !everLoaded || (!!status && status !== state.status);
    if (status) state.status = status;
    var url = API + '/tickets?status=' + encodeURIComponent(state.status)
      + '&page=' + state.page + '&pageSize=' + state.pageSize;

    // 首次进入或切换状态才显示加载态；翻页/刷新时保留旧行，避免整表闪烁
    if (fresh) showState('loading');

    get(url)
      .then(function (body) {
        everLoaded = true;
        var d = body.data || {};
        state.total = Number(d.total) || 0;
        state.totalPages = Number(d.totalPages) || 1;
        state.page = Number(d.page) || 1;
        state.unread = Number(d.unreadCount) || 0;
        renderStats(d.statusCounts, state.unread);
        updateTabs(d.statusCounts);
        renderRows(Array.isArray(d.items) ? d.items : []);
        renderPager();
      })
      .catch(function (e) {
        everLoaded = true;
        showError(e.message || '加载失败');
      });
  }

  /** 列表区三态：loading / empty / error。table 与状态块互斥显示。 */
  function showState(kind, opts) {
    if (!emptyEl) return;
    if (tableEl) tableEl.hidden = kind !== null;
    emptyEl.hidden = false;
    if (window.UiState) {
      if (kind === 'loading') window.UiState.loading(emptyEl, '正在读取工单…');
      else if (kind === 'empty') window.UiState.empty(emptyEl, opts);
      else if (kind === 'error') window.UiState.error(emptyEl, opts);
    } else {
      emptyEl.textContent = opts && opts.text ? opts.text : '';
    }
  }

  function showTable() {
    if (tableEl) tableEl.hidden = false;
    if (emptyEl) emptyEl.hidden = true;
  }

  function renderStats(counts, unread) {
    if (!statsEl) return;
    var c = counts || {};
    statsEl.innerHTML = '';
    [
      { key: 'open', label: '待处理', cls: 'open' },
      { key: 'processing', label: '处理中', cls: 'processing' },
      { key: 'resolved', label: '已解决', cls: 'resolved' },
      { key: 'unread', label: '未读', cls: 'unread' }
    ].forEach(function (item) {
      var card = document.createElement('div');
      card.className = 'ticket-stat ticket-stat--' + item.cls;
      var num = document.createElement('span');
      num.className = 'ticket-stat-num';
      num.textContent = item.key === 'unread' ? String(unread || 0) : String(c[item.key] || 0);
      var label = document.createElement('span');
      label.className = 'ticket-stat-label';
      label.textContent = item.label;
      card.appendChild(num);
      card.appendChild(label);
      statsEl.appendChild(card);
    });
    if (unreadEl) {
      unreadEl.textContent = unread ? unread + ' 条未读' : '';
      unreadEl.hidden = !unread;
    }
  }

  function updateTabs(counts) {
    if (!tabsEl) return;
    tabsEl.querySelectorAll('.service-tab').forEach(function (tab) {
      var key = tab.getAttribute('data-status');
      tab.classList.toggle('is-active', key === state.status);
      var badge = tab.querySelector('[data-count]');
      if (badge) badge.textContent = counts && counts[key] != null ? counts[key] : 0;
    });
  }

  function renderRows(items) {
    if (!bodyEl) return;
    bodyEl.innerHTML = '';
    if (!items.length) {
      if (state.status === 'all') {
        showState('empty', { icon: '🎫', text: '还没有工单。', hint: '用户提交的咨询会出现在这里。' });
      } else {
        showState('empty', {
          icon: '🎫',
          text: '这个状态下没有工单。',
          actionText: '看全部工单',
          onAction: function () { load(1, 'all'); }
        });
      }
      return;
    }
    showTable();

    items.forEach(function (t) {
      var tr = document.createElement('tr');
      if (t.unread) tr.className = 'ticket-row--unread';

      var tdSubject = document.createElement('td');
      if (t.unread) {
        var dot = document.createElement('span');
        dot.className = 'service-unread-dot';
        tdSubject.appendChild(dot);
      }
      var subject = document.createElement('span');
      subject.className = 'ticket-subject';
      subject.textContent = t.subject || '（无标题）';
      tdSubject.appendChild(subject);
      tr.appendChild(tdSubject);

      tr.appendChild(cell(t.userName || '访客'));
      tr.appendChild(cell(t.category || '其他'));

      var tdStatus = document.createElement('td');
      var badge = document.createElement('span');
      badge.className = 'service-item-status service-status--' + (t.status || 'open');
      badge.textContent = STATUS_LABEL[t.status] || t.status;
      tdStatus.appendChild(badge);
      tr.appendChild(tdStatus);

      tr.appendChild(cell(t.lastReplyAt || '—'));

      var tdOp = document.createElement('td');
      var open = document.createElement('button');
      open.type = 'button';
      open.className = 'admin-mini';
      open.textContent = '查看';
      open.addEventListener('click', function (ev) {
        ev.stopPropagation();
        openDetail(t.id);
      });
      tdOp.appendChild(open);
      tr.appendChild(tdOp);

      tr.addEventListener('click', function () { openDetail(t.id); });
      bodyEl.appendChild(tr);
    });
  }

  function cell(text) {
    var td = document.createElement('td');
    td.textContent = text;
    return td;
  }

  function renderPager() {
    if (!pagerEl) return;
    pagerEl.hidden = state.totalPages <= 1;
    if (pageInfoEl) {
      pageInfoEl.textContent = '第 ' + state.page + ' / ' + state.totalPages + ' 页 · 共 ' + state.total + ' 条';
    }
    if (prevBtn) prevBtn.disabled = state.page <= 1;
    if (nextBtn) nextBtn.disabled = state.page >= state.totalPages;
  }

  function showError(msg) {
    if (bodyEl) bodyEl.innerHTML = '';
    showState('error', {
      text: '工单加载失败：' + msg,
      hint: '已经提交的工单不会丢，网络恢复后重新加载即可。',
      onRetry: function () { showState('loading'); load(state.page); }
    });
  }

  // ---------------- 详情 ----------------

  function openDetail(id) {
    state.currentId = id;
    get(API + '/ticket?id=' + encodeURIComponent(id))
      .then(function (body) {
        renderDetail(body.data);
        showDetail();
        // 详情接口清了这条的管理员未读，刷新列表让角标与未读点同步
        load(state.page, state.status);
      })
      .catch(function (e) { toast('工单加载失败：' + e.message, 'error'); });
  }

  function renderDetail(t) {
    if (!t) return;
    detailSubject.textContent = t.subject || '（无标题）';
    detailMeta.innerHTML = '';
    detailMeta.appendChild(tag(t.category || '其他'));
    detailMeta.appendChild(tag(t.userName || '访客'));
    var st = document.createElement('span');
    st.className = 'service-item-status service-status--' + (t.status || 'open');
    st.textContent = STATUS_LABEL[t.status] || t.status;
    detailMeta.appendChild(st);
    if (t.lastReplyAt) detailMeta.appendChild(tag('最后回复 ' + t.lastReplyAt));

    threadEl.innerHTML = '';
    threadEl.appendChild(bubble(t.userName || '访客', t.content, t.createdAt, 'user'));
    (t.replies || []).forEach(function (r) {
      threadEl.appendChild(bubble(r.who, r.content, r.time, r.from === 'admin' ? 'admin' : 'user'));
    });
    threadEl.scrollTop = threadEl.scrollHeight;

    renderActions(t);
    if (replyInput) {
      replyInput.value = '';
      replyInput.placeholder = t.status === 'resolved'
        ? '回复会重新打开这个工单…' : '回复用户…';
    }
  }

  function renderActions(t) {
    if (!actionsEl) return;
    actionsEl.innerHTML = '';
    if (t.status === 'open') {
      actionsEl.appendChild(actionBtn('开始处理', 'processing', 'admin-btn'));
      actionsEl.appendChild(actionBtn('标记已解决', 'resolved', 'admin-mini'));
    } else if (t.status === 'processing') {
      actionsEl.appendChild(actionBtn('标记已解决', 'resolved', 'admin-btn'));
      actionsEl.appendChild(actionBtn('重新打开', 'open', 'admin-mini'));
    } else {
      actionsEl.appendChild(actionBtn('重新打开', 'open', 'admin-btn'));
    }
  }

  function actionBtn(label, status, cls) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = cls;
    b.textContent = label;
    b.addEventListener('click', function () { setStatus(status); });
    return b;
  }

  function setStatus(status) {
    if (!state.currentId) return;
    post(API + '/status', { ticketId: state.currentId, status: status })
      .then(function (body) {
        renderDetail(body.data);
        load(state.page, state.status);
      })
      .catch(function (e) { toast('状态更新失败：' + e.message, 'error'); });
  }

  function sendReply() {
    var content = replyInput ? replyInput.value.trim() : '';
    if (!content || !state.currentId) return;
    if (replyBtn) replyBtn.disabled = true;
    post(API + '/reply', { ticketId: state.currentId, content: content })
      .then(function (body) {
        renderDetail(body.data);
        load(state.page, state.status);
      })
      .catch(function (e) { toast('回复失败：' + e.message, 'error'); })
      .then(function () { if (replyBtn) replyBtn.disabled = false; });
  }

  function showDetail() {
    detailEl.hidden = false;
    maskEl.hidden = false;
    document.body.classList.add('ticket-detail-open');
  }

  function closeDetail() {
    detailEl.hidden = true;
    maskEl.hidden = true;
    document.body.classList.remove('ticket-detail-open');
    state.currentId = null;
  }

  function bubble(who, content, time, side) {
    var b = document.createElement('div');
    b.className = 'service-bubble service-bubble--' + side;
    var head = document.createElement('div');
    head.className = 'service-bubble-who';
    head.textContent = (who || '访客') + ' · ' + (time || '');
    var text = document.createElement('div');
    text.className = 'service-bubble-text';
    text.textContent = content || '';
    b.appendChild(head);
    b.appendChild(text);
    return b;
  }

  function tag(text) {
    var s = document.createElement('span');
    s.className = 'ticket-tag';
    s.textContent = text;
    return s;
  }

  // ---------------- 工具 ----------------

  function get(url) {
    return fetch(url, { credentials: 'same-origin' }).then(handle);
  }

  function post(url, body) {
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(handle);
  }

  function handle(res) {
    if (res.status === 401) {
      showGate('请用超管账号登录后查看。');
      throw new Error('未登录');
    }
    if (res.status === 403) {
      showGate('当前账号不是管理员，无法进入工单后台。');
      throw new Error('无权限');
    }
    return res.json().catch(function () { return {}; }).then(function (d) {
      if (d.code !== 0) throw new Error(d.message || ('HTTP ' + res.status));
      return d;
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
