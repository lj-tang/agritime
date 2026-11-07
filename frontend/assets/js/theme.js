/**
 * 主题管理（浅色 / 深色 / 跟随系统）。
 *
 * 存储的是「偏好」三态之一：light | dark | system（默认 system）。
 * data-theme 写的是「解析后的实际值」：light | dark，让 CSS 只需一个
 * :root[data-theme="dark"] 分支，避免把暗色变量写两遍。
 *
 * 无闪烁：各页面 <head> 里有一行内联脚本在首帧前就写好 data-theme，
 * 本脚本只负责切换按钮、持久化、以及「跟随系统」时监听系统变化实时换肤。
 */
(function () {
  var KEY = 'agritime.theme';

  function systemDark() {
    try {
      return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    } catch (e) { return false; }
  }

  /** 偏好 -> 实际主题 */
  function resolve(pref) {
    if (pref === 'light' || pref === 'dark') return pref;
    return systemDark() ? 'dark' : 'light';
  }

  function apply(pref) {
    var resolved = resolve(pref);
    var root = document.documentElement;
    root.setAttribute('data-theme', resolved);
    root.style.colorScheme = resolved;
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', resolved === 'dark' ? '#0f1310' : '#f5f7f5');
  }

  function get() {
    try {
      var v = localStorage.getItem(KEY);
      return (v === 'light' || v === 'dark' || v === 'system') ? v : 'system';
    } catch (e) { return 'system'; }
  }

  function set(pref) {
    if (pref !== 'light' && pref !== 'dark' && pref !== 'system') pref = 'system';
    try { localStorage.setItem(KEY, pref); } catch (e) { /* 隐私模式下忽略 */ }
    apply(pref);
  }

  function init() {
    apply(get());
    if (window.matchMedia) {
      var mql = window.matchMedia('(prefers-color-scheme: dark)');
      var onChange = function () { if (get() === 'system') apply('system'); };
      if (mql.addEventListener) mql.addEventListener('change', onChange);
      else if (mql.addListener) mql.addListener(onChange);
    }
  }

  window.Theme = { get: get, set: set, resolve: resolve, apply: apply, init: init };
  init();
})();
