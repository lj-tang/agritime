/**
 * 悬浮球与大模型对话组件（V4 整合优化）
 * 右下角悬浮球，点击展开农事智能问答窗口。
 */
(function () {
  var CHAT_API_URL = '/api/chat';
  var MAX_RETRIES = 1;

  function init() {
    var ball = document.createElement('div');
    ball.className = 'floating-ball';
    ball.setAttribute('aria-label', '农事智能问答');
    ball.innerHTML = '🤖';
    ball.addEventListener('click', toggleChat);
    document.body.appendChild(ball);

    var panel = document.createElement('div');
    panel.className = 'chat-panel';
    panel.id = 'chat-panel';
    panel.innerHTML = '<div class="chat-header"><span>农事智能问答</span><button type="button" class="chat-close" id="chat-close">×</button></div><div class="chat-messages" id="chat-messages"></div><div class="chat-input-area"><input type="text" id="chat-input" placeholder="询问农事问题…" /><button type="button" id="chat-send">发送</button></div>';
    document.body.appendChild(panel);

    document.getElementById('chat-close').addEventListener('click', function () { panel.classList.remove('chat-panel--open'); });
    document.getElementById('chat-send').addEventListener('click', sendMessage);
    document.getElementById('chat-input').addEventListener('keydown', function (e) { if (e.key === 'Enter') sendMessage(); });
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
    window.FloatingBall = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();