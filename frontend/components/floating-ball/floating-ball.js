/**
 * 悬浮球与大模型对话组件（V4 整合优化）
 *
 * 右下角悬浮球，点击展开农事智能问答窗口。
 *
 * V5.5：悬浮球会挡住页面底部的「上传 / 选图」按钮，所以
 *   1) 位置上移，让开主操作区；
 *   2) 加「收起」按钮，收进右边缘变成一条小耳朵，随时能叫回来；
 *   3) 收起状态记在 localStorage，下次进来还是收起的。
 */
(function () {
  var CHAT_API_URL = '/api/chat';
  var COLLAPSE_KEY = 'agritime.chatcollapsed';

  function chatIcon() {
    return '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>';
  }

  function readCollapsed() {
    try { return localStorage.getItem(COLLAPSE_KEY) === '1'; } catch (e) { return false; }
  }

  function saveCollapsed(v) {
    try { localStorage.setItem(COLLAPSE_KEY, v ? '1' : '0'); } catch (e) { /* 隐私模式下忽略 */ }
  }

  function init() {
    // 球与收起按钮是兄弟节点，不是父子：role=button 里再套一个 button，
    // 读屏软件会遇到"按钮里还有一个按钮"，里层那个基本点不到。
    var dock = document.createElement('div');
    dock.className = 'floating-ball-dock';

    var ball = document.createElement('div');
    ball.className = 'floating-ball';
    ball.setAttribute('role', 'button');
    ball.setAttribute('tabindex', '0');
    ball.setAttribute('aria-label', '农事智能问答');
    // 用简洁的聊天气泡图标替代机器人 emoji，视觉更统一
    ball.innerHTML = chatIcon();
    ball.addEventListener('click', toggleChat);
    ball.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleChat(); }
    });
    dock.appendChild(ball);

    var hideBtn = document.createElement('button');
    hideBtn.type = 'button';
    hideBtn.className = 'floating-ball-hide';
    hideBtn.setAttribute('aria-label', '收起客服按钮');
    hideBtn.title = '收起（不挡住页面按钮）';
    hideBtn.textContent = '×';
    hideBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      setCollapsed(true);
    });
    dock.appendChild(hideBtn);

    document.body.appendChild(dock);

    // 收起后的小耳朵：贴在右边缘，提示"还能叫回来"
    var tab = document.createElement('button');
    tab.type = 'button';
    tab.className = 'chat-tab';
    tab.setAttribute('aria-label', '展开农事问答');
    tab.title = '农事问答';
    tab.innerHTML = chatIcon();
    tab.addEventListener('click', function () { setCollapsed(false); });
    document.body.appendChild(tab);

    var panel = document.createElement('div');
    panel.className = 'chat-panel';
    panel.id = 'chat-panel';
    panel.innerHTML = '<div class="chat-header"><span>农事智能问答</span><button type="button" class="chat-close" id="chat-close">×</button></div><div class="chat-messages" id="chat-messages"></div><div class="chat-input-area"><input type="text" id="chat-input" placeholder="询问农事问题…" /><button type="button" id="chat-send">发送</button></div>';
    document.body.appendChild(panel);

    document.getElementById('chat-close').addEventListener('click', function () { panel.classList.remove('chat-panel--open'); });
    document.getElementById('chat-send').addEventListener('click', sendMessage);
    document.getElementById('chat-input').addEventListener('keydown', function (e) { if (e.key === 'Enter') sendMessage(); });

    setCollapsed(readCollapsed());
  }

  function setCollapsed(collapsed) {
    var dock = document.querySelector('.floating-ball-dock');
    var tab = document.querySelector('.chat-tab');
    if (dock) dock.classList.toggle('is-hidden', collapsed);
    if (tab) tab.classList.toggle('is-shown', collapsed);
    if (collapsed) {
      var panel = document.getElementById('chat-panel');
      if (panel) panel.classList.remove('chat-panel--open');
    }
    saveCollapsed(collapsed);
  }

  function toggleChat() {
    var panel = document.getElementById('chat-panel');
    if (panel) panel.classList.toggle('chat-panel--open');
  }

  function addMessage(role, text) {
    var msgs = document.getElementById('chat-messages');
    if (!msgs) return;
    var div = document.createElement('div');
    div.className = 'chat-msg chat-msg--' + role;
    div.textContent = text;
    msgs.appendChild(div);
    msgs.scrollTop = msgs.scrollHeight;
  }

  function sendMessage() {
    var input = document.getElementById('chat-input');
    if (!input) return;
    var text = input.value.trim();
    if (!text) return;
    addMessage('user', text);
    input.value = '';
    addMessage('bot', '思考中…');
    fetch(CHAT_API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: text })
    }).then(function (r) {
      if (!r.ok) throw new Error('API error');
      return r.json();
    }).then(function (data) {
      var msgs = document.getElementById('chat-messages');
      if (msgs && msgs.lastChild) msgs.removeChild(msgs.lastChild);
      addMessage('bot', data.answer || data.reply || data.message || '收到');
    }).catch(function () {
      var msgs = document.getElementById('chat-messages');
      if (msgs && msgs.lastChild) msgs.removeChild(msgs.lastChild);
      addMessage('bot', '智能问答暂不可用，请稍后再试');
    });
  }

  if (typeof window !== 'undefined') {
    window.FloatingBall = { init: init, setCollapsed: setCollapsed, isCollapsed: readCollapsed };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
