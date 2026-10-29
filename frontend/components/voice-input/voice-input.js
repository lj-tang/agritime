/**
 * 语音输入组件（T08）
 * 使用浏览器原生 Web Speech API，不引入第三方 SDK。
 * 功能：语音按钮 → 录音 → 识别 → 填充输入框 → 编辑确认。
 * 降级：浏览器不支持 SpeechRecognition 时隐藏语音按钮。
 * 非安全上下文（HTTP）下，点击按钮引导用户切换到 HTTPS。
 */
(function () {
  var DIALECT_MAP = {
    '北京': 'zh-CN', '上海': 'zh-CN', '广州': 'zh-CN', '深圳': 'zh-CN',
    '成都': 'zh-CN', '武汉': 'zh-CN', '西安': 'zh-CN', '哈尔滨': 'zh-CN',
    '昆明': 'zh-CN', '乌鲁木齐': 'zh-CN', '拉萨': 'zh-CN',
    '香港': 'zh-HK', '澳门': 'zh-HK', '台北': 'zh-TW',
    '厦门': 'zh-CN', '泉州': 'zh-CN', '汕头': 'zh-CN',
    '南宁': 'zh-CN', '贵阳': 'zh-CN', '长沙': 'zh-CN'
  };

  function getRecognition() {
    var SR = null;
    if (typeof window !== 'undefined') {
      SR = window.SpeechRecognition || window.webkitSpeechRecognition || null;
    }
    return SR;
  }

  function getDialect(location) {
    if (!location || typeof location !== 'string') return 'zh-CN';
    for (var key in DIALECT_MAP) {
      if (location.indexOf(key) !== -1) return DIALECT_MAP[key];
    }
    return 'zh-CN';
  }

  function showHttpsGuide() {
    var existing = document.getElementById('voice-https-guide');
    if (existing) { existing.remove(); return; }
    var overlay = document.createElement('div');
    overlay.id = 'voice-https-guide';
    overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);z-index:9999;display:flex;align-items:center;justify-content:center;';
    var box = document.createElement('div');
    box.style.cssText = 'background:#fff;border-radius:12px;padding:24px;max-width:360px;margin:16px;box-shadow:0 4px 20px rgba(0,0,0,0.15);font-size:14px;line-height:1.6;color:#333;';
    var title = document.createElement('h3');
    title.textContent = '🎤 需要切换到 HTTPS 才能使用麦克风';
    title.style.cssText = 'margin:0 0 12px;font-size:16px;color:#2f6b40;';
    box.appendChild(title);
    var p1 = document.createElement('p');
    p1.textContent = '浏览器出于安全限制，仅在 HTTPS 网站上允许使用麦克风。';
    p1.style.cssText = 'margin:0 0 12px;';
    box.appendChild(p1);
    var p2 = document.createElement('p');
    p2.textContent = '请点击下方按钮切换到加密访问，首次打开时浏览器会提示证书不安全，点击「高级」→「继续访问」即可。';
    p2.style.cssText = 'margin:0 0 16px;color:#666;font-size:13px;';
    box.appendChild(p2);
    var btnRow = document.createElement('div');
    btnRow.style.cssText = 'display:flex;gap:8px;';
    var btnGo = document.createElement('button');
    btnGo.textContent = '切换到 HTTPS';
    btnGo.style.cssText = 'flex:1;padding:10px;border:none;border-radius:8px;background:#2f6b40;color:#fff;font-size:14px;cursor:pointer;';
    btnGo.addEventListener('click', function () {
      window.location.href = 'https://' + window.location.hostname + window.location.pathname + window.location.search;
    });
    btnRow.appendChild(btnGo);
    var btnClose = document.createElement('button');
    btnClose.textContent = '关闭';
    btnClose.style.cssText = 'flex:1;padding:10px;border:1px solid #ddd;border-radius:8px;background:#fff;color:#666;font-size:14px;cursor:pointer;';
    btnClose.addEventListener('click', function () { overlay.remove(); });
    btnRow.appendChild(btnClose);
    box.appendChild(btnRow);
    overlay.appendChild(box);
    overlay.addEventListener('click', function (e) { if (e.target === overlay) overlay.remove(); });
    document.body.appendChild(overlay);
  }

  function createVoiceButton(targetInput, opts) {
    var SR = getRecognition();
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'voice-btn';
    btn.setAttribute('aria-label', '语音输入');
    btn.textContent = '🎤';

    var isSecure = (typeof window !== 'undefined') && (window.isSecureContext || location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1');

    if (!SR && !isSecure) {
      btn.addEventListener('click', showHttpsGuide);
      return btn;
    }

    if (!SR) {
      btn.style.display = 'none';
      return btn;
    }

    var isRecording = false;
    var recognition = null;

    btn.addEventListener('click', function () {
      if (isRecording) {
        if (recognition) recognition.stop();
        return;
      }
      recognition = new SR();
      var lang = opts && opts.getLocation ? getDialect(opts.getLocation()) : 'zh-CN';
      recognition.lang = lang;
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;

      recognition.onstart = function () {
        isRecording = true;
        btn.classList.add('voice-btn--active');
        btn.textContent = '⏹';
      };

      recognition.onresult = function (event) {
        if (event.results && event.results[0]) {
          var transcript = event.results[0][0].transcript || '';
          transcript = transcript.trim();
          if (transcript && targetInput) {
            targetInput.value = transcript;
            targetInput.focus();
            targetInput.dispatchEvent(new Event('input', { bubbles: true }));
            targetInput.dispatchEvent(new Event('change', { bubbles: true }));
          }
        }
      };

      recognition.onerror = function (event) {
        btn.classList.remove('voice-btn--active');
        btn.textContent = '🎤';
        isRecording = false;
        if (event && event.error === 'not-allowed') {
          showHttpsGuide();
        }
      };

      recognition.onend = function () {
        btn.classList.remove('voice-btn--active');
        btn.textContent = '🎤';
        isRecording = false;
      };

      try {
        recognition.start();
      } catch (e) {
        isRecording = false;
        btn.textContent = '🎤';
      }
    });

    return btn;
  }

  function init() {
    var cropInput = document.getElementById('crop');
    var locationInput = document.getElementById('location');
    var yearSel = document.getElementById('date-year');
    var monthSel = document.getElementById('date-month');
    var daySel = document.getElementById('date-day');

    if (cropInput) {
      var btn1 = createVoiceButton(cropInput, {
        getLocation: function () { return locationInput ? locationInput.value : ''; }
      });
      cropInput.parentNode.appendChild(btn1);
    }
    if (locationInput) {
      var btn2 = createVoiceButton(locationInput);
      locationInput.parentNode.appendChild(btn2);
    }
    if (yearSel) {
      var btn3 = createVoiceButton(yearSel, {
        getLocation: function () { return locationInput ? locationInput.value : ''; }
      });
      yearSel.parentNode.appendChild(btn3);
    }
  }

  if (typeof window !== 'undefined') {
    window.VoiceInput = { init: init, createVoiceButton: createVoiceButton, getDialect: getDialect, getRecognition: getRecognition };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { getDialect: getDialect, DIALECT_MAP: DIALECT_MAP };
  }
})();