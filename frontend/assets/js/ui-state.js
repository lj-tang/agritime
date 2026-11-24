/**
 * 四态：加载中 / 空 / 错误 / 部分数据（T20）
 *
 * 以前每个面板各写一套文案，空列表只有一行灰字，接口挂了就白屏 ——
 * 用户既不知道发生了什么，也不知道下一步点哪。这里统一成同一套结构：
 * 图标 + 一句说清原因的话 + 一个能直接点的动作。
 *
 * 用法：
 *   UiState.loading(box, '正在读取地块…');
 *   UiState.empty(box, { text: '还没有地块', actionText: '框一块', onAction: fn });
 *   UiState.error(box, { text: '网络异常', onRetry: fn });
 *   UiState.partial(box, '天气数据暂不可用，以下按规程排期');
 */
(function () {
  function resolve(box) {
    if (typeof box === 'string') return document.getElementById(box);
    return box || null;
  }

  function clear(box) {
    var el = resolve(box);
    if (el) el.innerHTML = '';
    return el;
  }

  function build(kind, opts) {
    var o = opts || {};
    var wrap = document.createElement('div');
    wrap.className = 'ui-state ui-state--' + kind;
    wrap.setAttribute('role', kind === 'error' ? 'alert' : 'status');

    var icon = document.createElement('div');
    icon.className = 'ui-state-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = o.icon || (kind === 'loading' ? '⏳' : kind === 'error' ? '⚠️' : '🌱');
    wrap.appendChild(icon);

    var text = document.createElement('p');
    text.className = 'ui-state-text';
    text.textContent = o.text || '';
    wrap.appendChild(text);

    if (o.hint) {
      var hint = document.createElement('p');
      hint.className = 'ui-state-hint';
      hint.textContent = o.hint;
      wrap.appendChild(hint);
    }

    var action = null;
    if (o.actionText && typeof o.onAction === 'function') {
      action = document.createElement('button');
      action.type = 'button';
      action.className = 'ui-state-action';
      action.textContent = o.actionText;
      action.addEventListener('click', o.onAction);
      wrap.appendChild(action);
    } else if (o.retryText !== false && typeof o.onRetry === 'function') {
      action = document.createElement('button');
      action.type = 'button';
      action.className = 'ui-state-action';
      action.textContent = o.retryText || '重新加载';
      action.addEventListener('click', o.onRetry);
      wrap.appendChild(action);
    }

    return wrap;
  }

  function mount(box, kind, opts) {
    var el = clear(box);
    if (!el) return null;
    var node = build(kind, opts);
    el.appendChild(node);
    return node;
  }

  window.UiState = {
    loading: function (box, text) {
      return mount(box, 'loading', { text: text || '正在加载…', icon: '⏳' });
    },
    empty: function (box, opts) {
      return mount(box, 'empty', opts || {});
    },
    error: function (box, opts) {
      return mount(box, 'error', opts || {});
    },
    /** 部分数据：不替换内容，只在顶部插一条提示条 */
    partial: function (box, text) {
      var el = resolve(box);
      if (!el) return null;
      var old = el.querySelector('.ui-state--partial');
      if (old) old.remove();
      var bar = document.createElement('div');
      bar.className = 'ui-state ui-state--partial';
      bar.setAttribute('role', 'status');
      bar.textContent = text;
      el.insertBefore(bar, el.firstChild);
      return bar;
    },
    clear: clear,

    /**
     * 请求版本号：切换筛选/快速重复查询时，只认最后一次请求的响应，
     * 旧响应回来直接丢弃（pageState + requestVersion 防旧覆盖）。
     */
    guard: function () {
      UiState.__seq = (UiState.__seq || 0) + 1;
      var id = UiState.__seq;
      return { current: function () { return UiState.__seq === id; } };
    },

    /**
     * 一站式四态：加载 → load() → 空 / 渲染 / 错误（自动带重试）。
     * opts: {
     *   loadingText, load,                       // load 返回 Promise<data>
     *   isEmpty(data)->bool, emptyText, emptyActionText, onEmptyAction,
     *   render(data),                            // 有数据时渲染（容器已清空）
     *   errorText, errorHint, retryText
     * }
     * 返回 guard，可中途 invalidate 掉本次请求。
     */
    run: function (box, opts) {
      var o = opts || {};
      var el = resolve(box);
      if (!el || typeof o.load !== 'function') return null;
      var g = UiState.guard();
      function attempt() {
        UiState.loading(el, o.loadingText || '正在加载…');
        Promise.resolve().then(function () { return o.load(); }).then(function (data) {
          if (!g.current()) return; // 旧请求的响应，丢弃
          if (o.isEmpty && o.isEmpty(data)) {
            el.hidden = false;
            UiState.empty(el, {
              icon: o.emptyIcon,
              text: o.emptyText || '暂无数据',
              actionText: o.emptyActionText,
              onAction: o.onEmptyAction
            });
          } else if (typeof o.render === 'function') {
            UiState.clear(el);
            o.render(data);
          }
        }).catch(function () {
          if (!g.current()) return;
          el.hidden = false;
          UiState.error(el, {
            icon: o.errorIcon,
            text: o.errorText || '加载失败',
            hint: o.errorHint || '网络恢复后点下面重新加载。',
            retryText: o.retryText || '重新加载',
            onRetry: attempt
          });
        });
      }
      attempt();
      return g;
    }
  };

  /**
   * 全局轻提示（保存成功 / 已复制 等）。各面板统一用这一个，
   * 不再各自 alert —— alert 打断操作，toast 只在底部闪一下。
   */
  window.showToast = function (msg, type) {
    var t = document.getElementById('global-toast');
    if (!t) {
      t = document.createElement('div');
      t.id = 'global-toast';
      t.className = 'toast';
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.className = 'toast show ' + (type || '');
    if (t.__timer) clearTimeout(t.__timer);
    t.__timer = setTimeout(function () { t.className = 'toast'; }, 2600);
  };
})();
