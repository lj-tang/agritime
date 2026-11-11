/**
 * 大字模式：给看不清小字的用户一个开关，而不是只把字号整体调大了事。
 *
 * 实现方式是给 <html> 挂 data-font="large"，CSS 里 --fs 从 1 变成 1.16，
 * 各处 font-size 都写成 calc(Npx * var(--fs, 1))，所以放大是全局且成比例的，
 * 不会出现"标题变大、说明还是小字"的错位。
 *
 * 无闪烁：各页面 <head> 里有一行内联脚本在首帧前就按 localStorage 写好属性，
 * 本脚本只负责开关按钮与持久化（与 theme.js 的分工一致）。
 */
(function () {
  var KEY = 'agritime.fontscale';
  var MODES = ['normal', 'large'];

  function get() {
    try {
      var v = localStorage.getItem(KEY);
      return MODES.indexOf(v) >= 0 ? v : 'normal';
    } catch (e) { return 'normal'; }
  }

  function apply(mode) {
    var target = mode === 'large' ? 'large' : 'normal';
    document.documentElement.setAttribute('data-font', target);
    return target;
  }

  function set(mode) {
    if (MODES.indexOf(mode) < 0) mode = 'normal';
    try { localStorage.setItem(KEY, mode); } catch (e) { /* 隐私模式下忽略 */ }
    apply(mode);
    try {
      window.dispatchEvent(new CustomEvent('agritime:font-changed', { detail: { mode: mode } }));
    } catch (e) { /* 老浏览器忽略 */ }
    return mode;
  }

  function toggle() {
    return set(get() === 'large' ? 'normal' : 'large');
  }

  /**
   * 绑定开关按钮。按钮文案说清"现在是什么/点了会怎样"，
   * 而不是只放一个 A+ 图标让用户猜。
   */
  function bind(btn) {
    if (!btn) return;
    function paint() {
      var large = get() === 'large';
      btn.setAttribute('aria-pressed', large ? 'true' : 'false');
      btn.classList.toggle('is-active', large);
      btn.title = large ? '当前为大字模式，点击恢复标准字号' : '字太小？点这里放大正文';
      var label = btn.querySelector('[data-font-label]');
      if (label) label.textContent = large ? '大字已开' : '大字';
    }
    btn.addEventListener('click', function () { toggle(); paint(); });
    paint();
  }

  function init() {
    apply(get());
    bind(document.getElementById('font-toggle'));
  }

  var api = { get: get, set: set, toggle: toggle, apply: apply, bind: bind, init: init };
  if (typeof window !== 'undefined') {
    window.FontScale = api;
    if (document.readyState !== 'loading') init();
    else document.addEventListener('DOMContentLoaded', init);
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();
