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
    clear: clear
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
