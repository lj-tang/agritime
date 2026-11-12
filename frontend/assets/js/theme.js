/**
 * 主题管理（农业场景主题 + 跟随系统）。
 *
 * 偏好：light | dark | paddy | wheat | rapeseed | terra | barn | amoled | system
 * 除 light/dark/amoled 这三档基础明暗外，其余都是「一块地」：
 *   paddy 稻田晨雾 / wheat 麦浪金黄 / rapeseed 油菜花田 / terra 红壤梯田 / barn 谷仓木纸
 * 配色都从作物上取，见 common.css 里对应的 :root[data-theme="xxx"] 变量块。
 *
 * data-theme 写的是「解析后的实际主题」——system 会解析成 light/dark，
 * 其余原样写入，让 CSS 的 :root[data-theme="xxx"] 直接命中。
 *
 * 无闪烁：各页面 <head> 里有一行内联脚本在首帧前写好 data-theme，
 * 本脚本负责切换、持久化、以及「跟随系统」时监听系统变化实时换肤。
 */
(function () {
  var KEY = 'agritime.theme';

  var VALID = ['light', 'dark', 'paddy', 'wheat', 'rapeseed', 'terra', 'barn', 'amoled', 'system'];
  var DARK = { dark: 1, amoled: 1 };
  var META_COLOR = {
    light: '#f5f7f5',
    dark: '#0f1310',
    paddy: '#eef5ef',
    wheat: '#faf4e3',
    rapeseed: '#f9f8e2',
    terra: '#f7ece5',
    barn: '#f4e8d6',
    amoled: '#000000'
  };

  /* 旧版本的主题 id（海洋/森林/秋收/暖纸），读到时并到现在的同名气质主题上，
     老用户不用重新挑一遍。写回也顺手做掉，下次启动就直接走新值。 */
  var LEGACY = { ocean: 'paddy', forest: 'paddy', sunset: 'wheat', sepia: 'barn' };

  function systemDark() {
    try {
      return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    } catch (e) { return false; }
  }

  /** 偏好 -> 实际主题 */
  function resolve(pref) {
    if (LEGACY[pref]) pref = LEGACY[pref];   // 老 id 也能解析，别落到默认浅色
    if (pref === 'system' || !pref) return systemDark() ? 'dark' : 'light';
    return VALID.indexOf(pref) >= 0 && pref !== 'system' ? pref : 'light';
  }

  function isDark(resolved) {
    return DARK[resolved] === 1;
  }

  function apply(pref) {
    var resolved = resolve(pref);
    var root = document.documentElement;
    root.setAttribute('data-theme', resolved);
    root.style.colorScheme = isDark(resolved) ? 'dark' : 'light';
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', META_COLOR[resolved] || '#f5f7f5');
  }

  function get() {
    try {
      var v = localStorage.getItem(KEY);
      if (LEGACY[v]) {
        // 老 id：并到新的农业主题上，并写回，下次启动就不用再走这一步
        try { localStorage.setItem(KEY, LEGACY[v]); } catch (e2) { /* 忽略 */ }
        return LEGACY[v];
      }
      return VALID.indexOf(v) >= 0 ? v : 'system';
    } catch (e) { return 'system'; }
  }

  function set(pref) {
    if (VALID.indexOf(pref) < 0) pref = 'system';
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

  window.Theme = {
    get: get,
    set: set,
    resolve: resolve,
    apply: apply,
    init: init,
    VALID: VALID,
    META_COLOR: META_COLOR,
    LEGACY: LEGACY
  };
  init();
})();