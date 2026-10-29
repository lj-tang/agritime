/**
 * 页面地址解析（防止双斜杠拼出协议相对 URL）
 *
 * 背景：站点被用 http://115.175.44.27// 这种地址打开时（很常见，地址栏多打一个斜杠
 * nginx 也照样返回 200），location.pathname 是 "//"。
 * 原来的逻辑 "//".replace(/\/$/, '') + '/timeline.html' 会得到 "//timeline.html"，
 * 浏览器把以 // 开头的地址当协议相对 URL，主机名就成了 timeline.html，
 * 于是报「找不到 timeline.html 的 IP 地址」。
 *
 * 这里统一折叠重复斜杠，并且只返回以 "/" 开头的绝对路径，任何输入都不会产出 "//"。
 */
(function () {
  function normalizePath(pathname) {
    var s = String(pathname == null ? '/' : pathname);
    if (s.charAt(0) !== '/') s = '/' + s;
    // 折叠连续斜杠："//pages///index.html" -> "/pages/index.html"
    return s.replace(/\/{2,}/g, '/');
  }

  /**
   * 由当前路径推出农事时间线页面地址。
   *   /                    -> /timeline.html
   *   //                   -> /timeline.html
   *   /index.html          -> /timeline.html
   *   /pages/              -> /pages/timeline.html
   *   /pages/index.html    -> /pages/timeline.html
   *   /app/pages/index.html-> /app/pages/timeline.html
   */
  function timelineUrl(pathname) {
    var p = normalizePath(pathname)
      .replace(/\/index\.html$/i, '')
      .replace(/\/+$/, '');
    return p + '/timeline.html';
  }

  var api = { normalizePath: normalizePath, timelineUrl: timelineUrl };
  if (typeof window !== 'undefined') window.PageUrl = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();
