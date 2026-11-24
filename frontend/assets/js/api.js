/**
 * 全站请求层（统一超时 / 网络重试 / 访客身份 / 错误文案）
 *
 * 背景：以前每个模块各写一套 fetch + AbortController + 20s 定时器，
 * 超时文案、重试次数、匿名工单的 X-Visitor-Id 都有出入，改一处漏一片。
 * 这里给所有站内 /api/ 请求一个收口点：
 *   · 默认 20 秒超时；调用方自带 signal 时不重复接管（尊重它自己的超时/取消逻辑）；
 *   · GET 遇网络层失败（DNS / 连接被掐）自动重试一次，HTTP 业务错误不重试；
 *   · 站内请求统一带 X-Visitor-Id，匿名工单不再依赖某一个文件里的实现；
 *   · 超时错误保留 name=AbortError，旧页面的判断逻辑照常生效。
 *
 * 用法：
 *   Api.request(url, opts)  -> Promise<Response>      // 需要自己处理 202/401 等状态码
 *   Api.json(url, opts)     -> Promise<object|null>   // 非 2xx / 解析失败返回 null
 *   Api.get(url, opts)      -> Promise<{code:0,data:...}>  // 自动按 code 判错
 *   Api.post(url, body, opts)
 *
 * 直接写 fetch('/api/...') 的旧代码也会自动走这套逻辑（这里包了一层 window.fetch），
 * 外部接口（天气、地形、瓦片）与带自定义 signal 的请求不受影响。
 */
(function () {
  'use strict';

  var TIMEOUT_MS = 20000;
  var VISITOR_KEY = 'agritime.visitorId';
  var nativeFetch = typeof window.fetch === 'function' ? window.fetch.bind(window) : null;

  function visitorId() {
    try {
      var v = localStorage.getItem(VISITOR_KEY);
      if (v && /^[A-Za-z0-9_-]{8,64}$/.test(v)) return v;
      var buf = new Uint8Array(16);
      (window.crypto || window.msCrypto).getRandomValues(buf);
      v = Array.prototype.map.call(buf, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
      localStorage.setItem(VISITOR_KEY, v);
      return v;
    } catch (e) {
      return '';
    }
  }

  function withVisitor(headers) {
    var h = {};
    if (typeof Headers !== 'undefined' && headers instanceof Headers) {
      headers.forEach(function (v, k) { h[k] = v; });
    } else if (headers && typeof headers.forEach === 'function') {
      headers.forEach(function (v, k) { h[k] = v; });
    } else if (headers) {
      Object.keys(headers).forEach(function (k) { h[k] = headers[k]; });
    }
    var v = visitorId();
    if (v && !h['X-Visitor-Id'] && !h['x-visitor-id']) h['X-Visitor-Id'] = v;
    return h;
  }

  function isInternal(url) {
    return typeof url === 'string' && (url.indexOf('/api/') === 0 || url === '/api');
  }

  /** 真正发请求：每次尝试都新建 controller，避免重试时复用已 abort 的 signal。 */
  function request(url, opts) {
    var o = opts || {};
    // 外部接口、调用方自己管超时的请求（带 signal）、显式跳过，均原样透传。
    if (!nativeFetch || !isInternal(url) || o.signal || o.skipWrapper) {
      return nativeFetch(url, o);
    }

    var method = String(o.method || 'GET').toUpperCase();
    var timeout = o.timeout == null ? TIMEOUT_MS : o.timeout;
    var canRetry = o.retry !== false && method === 'GET';

    function attempt(n) {
      var controller = null;
      var init = {};
      Object.keys(o).forEach(function (k) { init[k] = o[k]; });
      init.headers = withVisitor(o.headers);
      init.credentials = o.credentials || 'same-origin';
      if (typeof AbortController !== 'undefined' && timeout > 0) {
        controller = new AbortController();
        init.signal = controller.signal;
      }
      var timedOut = false;
      var timer = controller && timeout > 0 ? setTimeout(function () {
        timedOut = true;
        controller.abort();
      }, timeout) : null;

      return nativeFetch(url, init).then(function (res) {
        if (timer) clearTimeout(timer);
        return res;
      }, function (err) {
        if (timer) clearTimeout(timer);
        if (timedOut) {
          var e = new Error('请求超时（' + Math.round(timeout / 1000) + ' 秒），请检查网络后重试');
          e.name = 'AbortError';
          throw e;
        }
        // 只对 GET 的网络层失败重试一次；HTTP 4xx/5xx 是业务结果，不重试。
        if (n === 0 && canRetry && err && err.name !== 'AbortError') {
          return new Promise(function (resolve) { setTimeout(resolve, 400); }).then(function () {
            return attempt(1);
          });
        }
        throw err;
      });
    }

    return attempt(0);
  }

  function parse(url, opts) {
    return request(url, opts).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        return { res: res, body: body || {} };
      });
    });
  }

  function json(url, opts) {
    return request(url, opts).then(function (res) {
      if (!res.ok) return null;
      return res.json().catch(function () { return null; });
    });
  }

  function checked(url, opts) {
    return parse(url, opts).then(function (out) {
      var body = out.body;
      if (!out.res.ok) throw new Error(body.message || ('HTTP ' + out.res.status));
      // 老接口有两种返回：{code:0,data:...} 和直接裸数据（推荐/日历/作物）。
      // 只有明确带了非 0 code 才算业务失败，不能因为缺字段把裸数据判成错误。
      if (body.code != null && body.code !== 0) {
        throw new Error(body.message || '请求失败');
      }
      return body;
    });
  }

  function get(url, opts) {
    var o = {};
    Object.keys(opts || {}).forEach(function (k) { o[k] = opts[k]; });
    o.method = 'GET';
    return checked(url, o);
  }

  function post(url, body, opts) {
    var o = {};
    Object.keys(opts || {}).forEach(function (k) { o[k] = opts[k]; });
    o.method = 'POST';
    o.headers = o.headers || {};
    o.headers['Content-Type'] = 'application/json';
    o.body = typeof body === 'string' ? body : JSON.stringify(body == null ? {} : body);
    return checked(url, o);
  }

  window.Api = {
    request: request,
    json: json,
    get: get,
    post: post,
    visitorId: visitorId,
    TIMEOUT_MS: TIMEOUT_MS
  };

  // 老代码里大量 fetch('/api/...') 不改动调用点也能拿到超时与重试。
  // 页面可在加载本文件前设 window.__APIFETCH_SKIP = true 关闭这层包装。
  if (nativeFetch && !window.__APIFETCH_SKIP) {
    window.fetch = function (url, opts) { return request(url, opts); };
  }
})();
