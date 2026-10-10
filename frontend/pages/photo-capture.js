/**
 * 拍照识别与人工确认组件（含确认弹层）
 * 流程：拍照/选图 → 识别请求（≤800ms）→ 3 个候选 + 置信度 → 人工确认 → 回填品种
 * 识别只缩小范围不做决定：未点确认不进入时间线生成；失败/超时回落搜索框，流程不中断。
 */
(function () {
  var FALLBACK_TIMEOUT_MS = 800;


  var state = { onConfirm: null, controller: null };

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function injectStyle() {
    if (document.getElementById('pc-style')) return;
    var style = document.createElement('style');
    style.id = 'pc-style';
    style.textContent = [
      '.pc-mask{position:fixed;inset:0;z-index:1000;display:flex;align-items:flex-end;background:rgba(28,38,26,.42);backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px);animation:pc-fade .2s ease}',
      '.pc-panel{width:100%;max-width:460px;margin:0 auto;padding:12px 18px calc(22px + env(safe-area-inset-bottom));background:var(--card,#fffdf7);border-radius:24px 24px 0 0;box-shadow:0 -14px 40px rgba(28,38,26,.28);animation:pc-rise .28s cubic-bezier(.2,.8,.3,1)}',
      '.pc-grip{width:40px;height:4px;border-radius:999px;background:var(--line,#e4ddcb);margin:0 auto 14px}',
      '.pc-title{font-size:19px;font-weight:800;letter-spacing:1px;color:var(--ink,#23301f);margin-bottom:4px}',
      '.pc-status{font-size:13.5px;color:var(--muted,#8b9382);margin-bottom:14px;line-height:1.5;display:flex;align-items:center;gap:8px}',
      '.pc-candidate{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;padding:14px;margin-bottom:10px;background:#f7f3e8;border:1.5px solid var(--line,#e4ddcb);border-radius:14px;font-family:inherit;text-align:left}',
      '.pc-candidate:active{background:var(--leaf-soft,#e6f0e3);border-color:var(--leaf-2,#3f8f56)}',
      '.pc-cand-main{flex:1;min-width:0}',
      '.pc-cand-name{display:block;font-size:17px;font-weight:700;color:var(--ink,#23301f);margin-bottom:6px}',
      '.pc-bar{height:5px;border-radius:999px;background:rgba(63,143,86,.16);overflow:hidden}',
      '.pc-bar i{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg,var(--leaf-2,#3f8f56),var(--sun,#d99b2b))}',
      '.pc-conf{flex:none;font-size:15px;font-weight:800;color:var(--leaf,#2f6b40);font-variant-numeric:tabular-nums}',
      '.pc-input{width:100%;font-family:inherit;font-size:16px;padding:13px;color:var(--ink,#23301f);background:#fbf8f0;border:1.5px solid var(--line,#e4ddcb);border-radius:12px;margin:8px 0 12px}',
      '.pc-input:focus{outline:none;border-color:var(--leaf-2,#3f8f56);box-shadow:0 0 0 4px rgba(63,143,86,.14)}',
      '.pc-actions{display:flex;gap:10px;margin-top:6px}',
      '.pc-btn{flex:1;display:flex;align-items:center;justify-content:center;gap:7px;font-family:inherit;font-size:16px;font-weight:700;letter-spacing:.5px;padding:14px;border-radius:13px;border:none}',
      '.pc-btn.primary{color:#fff;background:linear-gradient(155deg,var(--leaf-2,#3f8f56),var(--leaf,#2f6b40));box-shadow:0 12px 22px -12px rgba(47,107,64,.8)}',
      '.pc-btn.ghost{color:var(--ink-soft,#55634e);background:transparent;border:1.5px solid var(--line,#e4ddcb)}',
      '.pc-hidden{display:none}',
      '.pc-spinner{width:16px;height:16px;flex:none;border:2.5px solid rgba(63,143,86,.25);border-top-color:var(--leaf-2,#3f8f56);border-radius:50%;animation:pc-spin .7s linear infinite}',
      '@keyframes pc-spin{to{transform:rotate(360deg)}}',
      '@keyframes pc-fade{from{opacity:0}to{opacity:1}}',
      '@keyframes pc-rise{from{transform:translateY(24px)}to{transform:none}}'
    ].join('');
    document.head.appendChild(style);
  }

  function close() {
    if (state.controller) state.controller.abort();
    state.controller = null;
    var mask = document.getElementById('pc-mask');
    if (mask) mask.remove();
  }

  function buildOverlay() {
    injectStyle();
    var mask = el('div', 'pc-mask');
    mask.id = 'pc-mask';
    mask.addEventListener('click', function (e) { if (e.target === mask) close(); });

    var panel = el('div', 'pc-panel');
    panel.appendChild(el('div', 'pc-grip'));
    panel.appendChild(el('div', 'pc-title', '拍照识别作物'));

    var fileInput = el('input');
    fileInput.type = 'file';
    fileInput.accept = 'image/*';
    fileInput.setAttribute('capture', 'environment');
    fileInput.className = 'pc-hidden';
    panel.appendChild(fileInput);

    var status = el('div', 'pc-status', '拍一张作物照片，识别只给候选，需你点一下确认');
    panel.appendChild(status);

    var candidatesBox = el('div');
    panel.appendChild(candidatesBox);

    var fallbackBox = el('div', 'pc-hidden');
    panel.appendChild(fallbackBox);

    var actions = el('div', 'pc-actions');
    var shootBtn = el('button', 'pc-btn primary', '拍照 / 选图');
    var cancelBtn = el('button', 'pc-btn ghost', '取消');
    actions.appendChild(shootBtn);
    actions.appendChild(cancelBtn);
    panel.appendChild(actions);

    mask.appendChild(panel);
    document.body.appendChild(mask);
    cancelBtn.addEventListener('click', close);
    shootBtn.addEventListener('click', function () { fileInput.click(); });
    fileInput.addEventListener('change', function () {
      var file = fileInput.files && fileInput.files[0];
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () { runRecognition(reader.result, status, candidatesBox, fallbackBox, shootBtn); };
      reader.readAsDataURL(file);
    });

    return { status: status, candidatesBox: candidatesBox, fallbackBox: fallbackBox, shootBtn: shootBtn };
  }

  function renderCandidates(list, candidatesBox, onConfirm) {
    candidatesBox.innerHTML = '';
    list.forEach(function (c) {
      var btn = el('button', 'pc-candidate');
      var main = el('div', 'pc-cand-main');
      main.appendChild(el('span', 'pc-cand-name', c.crop));
      var bar = el('div', 'pc-bar');
      var fill = el('i');
      fill.style.width = Math.round(c.confidence * 100) + '%';
      bar.appendChild(fill);
      main.appendChild(bar);
      var conf = el('span', 'pc-conf', Math.round(c.confidence * 100) + '%');
      btn.appendChild(main);
      btn.appendChild(conf);
      btn.addEventListener('click', function () {
        var crop = c.crop;
        close();
        if (onConfirm) onConfirm(crop);
      });
      candidatesBox.appendChild(btn);
    });
  }

  function showFallback(message, fallbackBox, onConfirm) {
    fallbackBox.className = '';
    fallbackBox.innerHTML = '';
    fallbackBox.appendChild(el('div', 'pc-status', message));
    var input = el('input', 'pc-input');
    input.placeholder = '手动输入或搜索作物品种';
    var confirmBtn = el('button', 'pc-btn primary', '确认');
    var row = el('div', 'pc-actions');
    row.appendChild(confirmBtn);
    fallbackBox.appendChild(input);
    fallbackBox.appendChild(row);
    confirmBtn.addEventListener('click', function () {
      var value = input.value.trim();
      if (!value) return;
      close();
      if (onConfirm) onConfirm(value);
    });
    input.focus();
  }

  function setStatusSpinner(status, text) {
    status.innerHTML = '';
    status.appendChild(el('span', 'pc-spinner'));
    status.appendChild(el('span', null, text));
  }

  function runRecognition(image, status, candidatesBox, fallbackBox, shootBtn) {
    candidatesBox.innerHTML = '';
    fallbackBox.className = 'pc-hidden';
    setStatusSpinner(status, '正在识别…');
    shootBtn.disabled = true;

    var controller = new AbortController();
    state.controller = controller;
    var timer = setTimeout(function () { controller.abort(); }, FALLBACK_TIMEOUT_MS);

    fetch('/api/recognition', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: image }),
      signal: controller.signal
    })
      .then(function (res) { if (!res.ok) throw new Error('识别服务返回 ' + res.status); return res.json(); })
      .then(function (data) {
        clearTimeout(timer);
        shootBtn.disabled = false;
        if (!data.candidates || data.candidates.length === 0) {
          status.textContent = data.message || '未识别出品种，请手动搜索';
          showFallback('未识别出品种，请手动搜索', fallbackBox, state.onConfirm);
          return;
        }
        status.textContent = '请点一下确认品种（识别只缩小范围，替你做的决定不做数）';
        renderCandidates(data.candidates, candidatesBox, state.onConfirm);
      })
      .catch(function () {
        clearTimeout(timer);
        shootBtn.disabled = false;
        status.textContent = '该功能需要后端服务支持，暂不可用';
        showFallback('也可手动搜索作物品种', fallbackBox, state.onConfirm);
      });
  }

  function open(onConfirm) {
    state.onConfirm = onConfirm;
    var refs = buildOverlay();
    refs.shootBtn.click();
  }

  window.PhotoCapture = { open: open };
})();