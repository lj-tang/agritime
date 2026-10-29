/**
 * 播种确认的跨 frame 桥（T12 日历联动）
 *
 * 农事日历嵌在主页的 iframe 里，CustomEvent 出不了 iframe 边界，
 * 所以日历那边用 postMessage 发出来，这里在同源校验后转成普通的 CustomEvent，
 * 交给 my-crops.js 处理。放在单独文件里，避免主页内联脚本越堆越多。
 */
(function () {
  window.addEventListener('message', function (e) {
    if (e.origin !== location.origin) return;
    var d = e.data;
    if (!d || d.type !== 'agritime:sowing-confirmed') return;
    window.dispatchEvent(new CustomEvent('agritime:sowing-confirmed', { detail: d.detail || {} }));
  });
})();
