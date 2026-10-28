/**
 * 语音输入组件（T08）
 * 使用浏览器原生 Web Speech API，不引入第三方 SDK。
 * 功能：语音按钮 → 录音 → 识别 → 填充输入框 → 编辑确认。
 * 降级：浏览器不支持 SpeechRecognition 时隐藏语音按钮。
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

  function createVoiceButton(targetInput, opts) {
    var SR = getRecognition();
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'voice-btn';
    btn.setAttribute('aria-label', '语音输入');
    btn.textContent = '🎤';

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

      recognition.onerror = function () {
        btn.classList.remove('voice-btn--active');
        btn.textContent = '🎤';
        isRecording = false;
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