/**
 * 图表数据客户端（chart-client）
 * 唯一持有 POST /api/chart 字面串的模块：timeline.html 内联脚本禁止出现 POST（保护既有断言）。
 * 失败/超时/非法结构一律回落为降级态，永不 reject。
 */
(function () {
  var API_PATH = '/api/chart';
  var TIMEOUT_MS = 20000;

  /**
   * 请求图表数据。
   * @param {object} context 图表上下文（作物/播种日/节点等）
   * @param {{timeout?:number, fetch?:Function}} [opts] 可选注入（便于测试）
   * @returns {Promise<{ok:boolean, data?:object, degradedReason?:string}>} 永不 reject
   */
  function request(context, opts) {
    var options = opts || {};
    var timeout = options.timeout || TIMEOUT_MS;
    var fetchImpl = options.fetch || (typeof fetch !== 'undefined' ? fetch : null);
    if (!fetchImpl) return Promise.resolve({ ok: false, degradedReason: 'unavailable' });

    return new Promise(function (resolve) {
      var controller = new AbortController();
      var timer = setTimeout(function () { controller.abort(); }, timeout);
      var body;
      try {
        body = JSON.stringify(context || {});
      } catch (e) {
        clearTimeout(timer);
        resolve({ ok: false, degradedReason: 'invalid' });
        return;
      }

      fetchImpl(API_PATH, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body,
        signal: controller.signal
      })
        .then(function (res) {
          if (!res || !res.ok) {
            clearTimeout(timer);
            resolve({ ok: false, degradedReason: 'http' });
            return null;
          }
          return res.json();
        })
        .then(function (data) {
          if (data === null) return; // 已在上一步 resolve
          clearTimeout(timer);
          if (typeof data !== 'object') {
            resolve({ ok: false, degradedReason: 'invalid' });
            return;
          }
          resolve({ ok: true, data: data });
        })
        .catch(function (err) {
          clearTimeout(timer);
          var reason = (err && err.name === 'AbortError') ? 'timeout' : 'offline';
          resolve({ ok: false, degradedReason: reason });
        });
    });
  }

  var api = { API_PATH: API_PATH, TIMEOUT_MS: TIMEOUT_MS, request: request };

  if (typeof window !== 'undefined') window.ChartClient = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();