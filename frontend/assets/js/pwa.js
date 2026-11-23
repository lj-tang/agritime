/* 知农时 PWA 支持：Service Worker 注册 + 网络状态提示。
 * 只在 http(s) 下注册（file:// 直接打开时跳过），失败静默，不影响页面功能。
 */
(function () {
  'use strict';

  var BANNER_ID = 'agritime-net-banner';
  var banner = null;
  var hideTimer = null;

  function ensureBanner() {
    if (banner) return banner;
    banner = document.createElement('div');
    banner.id = BANNER_ID;
    banner.className = 'net-banner';
    banner.setAttribute('role', 'status');
    banner.setAttribute('aria-live', 'polite');
    document.body.appendChild(banner);
    return banner;
  }

  function showBanner(text, tone, autoHideMs) {
    var el = ensureBanner();
    el.textContent = text;
    el.className = 'net-banner show' + (tone ? ' ' + tone : '');
    if (hideTimer) { clearTimeout(hideTimer); hideTimer = null; }
    if (autoHideMs) {
      hideTimer = setTimeout(function () { hideBanner(); }, autoHideMs);
    }
  }

  function hideBanner() {
    if (!banner) return;
    banner.className = 'net-banner';
  }

  function syncNetwork() {
    if (navigator.onLine) {
      hideBanner();
    } else {
      showBanner('当前网络不可用，部分数据可能不是最新', 'warn');
    }
  }

  window.addEventListener('online', function () {
    showBanner('网络已恢复', 'ok', 2400);
  });
  window.addEventListener('offline', syncNetwork);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', syncNetwork);
  } else {
    syncNetwork();
  }

  if ('serviceWorker' in navigator) {
    var register = function () {
      navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(function () {});
    };
    if (document.readyState === 'complete') {
      register();
    } else {
      window.addEventListener('load', register);
    }
  }
})();
