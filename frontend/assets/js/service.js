/**
 * 客服（V4 T14）—— 用户端
 * 提交工单 → 列表 → 详情对话 → 追问回复。
 * 鉴权复用账号系统 Cookie（未登录也能以访客身份提交）。
 */
(function () {
  var API = '/api/support';
  var STATUS_LABEL = { open: '待回复', replied: '已回复', closed: '已关闭' };

  var pane, listBox, detailBox, form, backBtn, newBtn;

  function init() {
    pane = document.getElementById('service-pane');
    listBox = document.getElementById('service-list');
    detailBox = document.getElementById('service-detail');
    form = document.getElementById('service-form');
    backBtn = document.getElementById('service-back-btn');
    newBtn = document.getElementById('service-new-btn');
    if (!pane || !listBox || !form) return;

    newBtn.addEventListener('click', showForm);
    backBtn.addEventListener('click', showList);
    form.addEventListener('submit', onSubmit);
    loadTickets();
  }

  // ---------- 列表 ----------
  function loadTickets() {
    showList();
    get(API + '/tickets')
      .then(function (data) {
        var list = data.data || [];
        renderList(list);
      })
      .catch(function (e) {
        listBox.innerHTML = '<div class="service-empty">加载失败：' + e.message + '</div>';
      });
  }

  function renderList(list) {
    listBox.innerHTML = '';
    if (!list.length) {
      // 匿名用户：列表接口不给（保护隐私），但本浏览器提交过的可以逐个查出来
      var mine = myIds();
      if (mine.length) {
        Promise.all(mine.map(function (id) {
          return get(API + '/ticket?id=' + encodeURIComponent(id)).then(function (d) {
            return d.data;
          }).catch(function () { return null; });
        })).then(function (rows) {
          var valid = rows.filter(Boolean);
          if (!valid.length) {
            listBox.innerHTML = '<div class="service-empty">还没有咨询记录，点「新建咨询」提第一个问题</div>';
            return;
          }
          valid.forEach(function (t) { listBox.appendChild(renderTicketItem(t)); });
        });
        return;
      }
      listBox.innerHTML = '<div class="service-empty">还没有咨询记录，点「新建咨询」提第一个问题</div>';
      return;
    }
    list.forEach(function (t) { listBox.appendChild(renderTicketItem(t)); });
  }

  function myIds() {
    try { return JSON.parse(localStorage.getItem(MINE_KEY) || '[]'); } catch (e) { return []; }
  }

  function renderTicketItem(t) {
    var item = document.createElement('div');
    item.className = 'service-item';
    item.innerHTML =
      '<div class="service-item-head">' +
      '<span class="service-item-subject"></span>' +
      '<span class="service-item-status service-status--' + (t.status || 'open') + '">' + (STATUS_LABEL[t.status] || t.status) + '</span>' +
      '</div>' +
      '<div class="service-item-meta">' + (t.category || '其他') + ' · ' + (t.createdAt || '') + ' · ' + (t.replyCount || 0) + ' 条回复</div>';
    item.querySelector('.service-item-subject').textContent = t.subject;
    item.addEventListener('click', function () { openDetail(t.id); });
    return item;
  }

  // ---------- 详情 ----------
  function openDetail(id) {
    get(API + '/ticket?id=' + encodeURIComponent(id))
      .then(function (d) { renderDetail(d.data); })
      .catch(function (e) { detailBox.innerHTML = '<div class="service-empty">加载失败：' + e.message + '</div>'; });
  }

  function renderDetail(t) {
    listBox.style.display = 'none';
    form.style.display = 'none';
    backBtn.style.display = 'inline-block';
    detailBox.style.display = 'block';
    detailBox.innerHTML = '';

    var head = document.createElement('div');
    head.className = 'service-detail-head';
    head.innerHTML = '<div class="service-detail-subject"></div>' +
      '<div class="service-detail-meta">' + (t.category || '') + ' · ' + (t.createdAt || '') + ' · ' +
      '<span class="service-item-status service-status--' + (t.status || 'open') + '">' + (STATUS_LABEL[t.status] || t.status) + '</span></div>';
    head.querySelector('.service-detail-subject').textContent = t.subject;
    detailBox.appendChild(head);

    // 原问题
    detailBox.appendChild(bubble(t.userName, t.content, t.createdAt, 'user'));

    // 回复串
    var replies = t.replies || [];
    replies.forEach(function (r) {
      detailBox.appendChild(bubble(r.who, r.content, r.time, r.from === 'admin' ? 'admin' : 'user'));
    });

    // 追问输入
    if (t.status !== 'closed') {
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
        post(API + '/reply', { ticketId: t.id, content: content })
          .then(function (d) { renderDetail(d.data); })
          .catch(function (e) { alert('发送失败：' + e.message); });
      });
      replyBox.appendChild(ta);
      replyBox.appendChild(btn);
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

  // ---------- 表单 ----------
  function showForm() {
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
  }

  function onSubmit(e) {
    e.preventDefault();
    var subject = document.getElementById('service-subject').value.trim();
    var category = document.getElementById('service-category').value;
    var content = document.getElementById('service-content').value.trim();
    var userName = document.getElementById('service-user-name').value.trim();
    if (!subject || !content) { alert('请填写标题和描述'); return; }
    post(API + '/ticket', { subject: subject, category: category, content: content, userName: userName })
      .then(function (d) {
        form.reset();
        rememberMine(d.data.id);
        // 提交成功后，用 create 接口返回的完整数据直接渲染成确认视图，
        // 匿名用户无需再查详情接口（详情接口对匿名有隐私边界）
        renderSubmitted(d.data);
      })
      .catch(function (err) { alert('提交失败：' + err.message); });
  }

  function renderSubmitted(t) {
    listBox.style.display = 'none';
    form.style.display = 'none';
    backBtn.style.display = 'inline-block';
    detailBox.style.display = 'block';
    detailBox.innerHTML = '';
    var ok = document.createElement('div');
    ok.className = 'service-submitted';
    ok.innerHTML = '<div class="service-submitted-title">工单已提交 ✓</div>' +
      '<div class="service-submitted-sub">客服会尽快回复，你可以在「我的咨询」里查看进展（登录后可在任意设备同步）</div>';
    detailBox.appendChild(ok);
    detailBox.appendChild(bubble(t.userName || '访客', t.content, t.createdAt, 'user'));
  }

  // 匿名用户后端无法稳定识别，前端用 localStorage 记下自己提交过的工单，
  // 这样同一浏览器里能追溯到自己的咨询
  var MINE_KEY = 'agritime.my-tickets.v1';
  function rememberMine(id) {
    try {
      var arr = JSON.parse(localStorage.getItem(MINE_KEY) || '[]');
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
