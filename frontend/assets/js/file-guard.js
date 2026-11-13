/**
 * file:// 直开的「救生索」。
 *
 * 双击 index.html 打开（file:// 协议）时，页面本身一切正常——样式、脚本、地图瓦片
 * 都是相对路径或公网地址。唯独所有 fetch('/api/...') 会被浏览器解析成
 * file:///api/...，直接被拒绝。用户看到的就是「地图能点、没地名、没结果」，
 * 没有任何报错提示，非常误导。
 *
 * 这个脚本在 file:// 下做三件事（http/https 下什么都不做，一行 if 直接退出）：
 *   1. 改写 fetch 与 XMLHttpRequest：以 /api 开头的请求直连本地后端
 *      http://127.0.0.1:8080。后端所有响应都带 Access-Control-Allow-Origin: *，
 *      跨源可读，作物库、播期、天气、地图搜索全部恢复工作。
 *   2. 如果本地整站（127.0.0.1:3000，start-local.bat 起的那套）在跑，直接跳过去：
 *      登录用的是 HttpOnly Cookie，file:// 跨源带不上，只有 3000 上才能登录。
 *   3. 两边都不在：盖一层说明引导用户双击 start-local.bat，服务起来后自动恢复
 *      （期间每 2.5 秒重试一次，不用手动刷新）。
 */
(function () {
  if (location.protocol !== 'file:') return;

  var BACKEND = 'http://127.0.0.1:8080';
  var SITE = 'http://127.0.0.1:3000';

  var rawFetch = window.fetch ? window.fetch.bind(window) : null;

  // 1) fetch 改写：/api 开头 → 本地后端
  if (rawFetch) {
    window.fetch = function (input, init) {
      if (typeof input === 'string' && input.indexOf('/api') === 0) {
        return rawFetch(BACKEND + input, init);
      }
      if (input && typeof input.url === 'string' && input.url.indexOf('/api') === 0) {
        // Request 对象：用同一份方法/头/体重建
        return rawFetch(new Request(BACKEND + input.url, input), init);
      }
      return rawFetch(input, init);
    };
  }

  // XMLHttpRequest 也要改写（industry.js 用 XHR 拉渔类与生产日历）
  var rawOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    var rest = Array.prototype.slice.call(arguments, 2);
    var u = String(url);
    if (u.indexOf('/api') === 0) u = BACKEND + u;
    return rawOpen.apply(this, [method, u].concat(rest));
  };

  // 2) 页名 → 整站对应地址（index.html 是站点根，其余页按文件名）
  function siteTarget() {
    var name = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
    if (!name || name === 'index.html') return SITE + '/';
    return SITE + '/' + name;
  }

  function probe(url) {
    if (!rawFetch) return Promise.resolve(false);
    return rawFetch(url, { method: 'GET' })
      .then(function (r) { return r.ok; })
      .catch(function () { return false; });
  }

  var overlay = null;
  function ensureOverlay() {
    if (overlay) return overlay;
    overlay = document.createElement('div');
    overlay.id = 'file-guard-overlay';
    overlay.setAttribute('style', [
      'position:fixed', 'inset:0', 'z-index:2147483647',
      'display:flex', 'align-items:center', 'justify-content:center',
      'background:rgba(248,250,252,.97)',
      'font:14px/1.7 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif',
      'color:#1f2937'
    ].join(';'));
    overlay.innerHTML = [
      '<div style="max-width:460px;padding:28px 32px;background:#fff;border:1px solid #e5e7eb;'
        + 'border-radius:12px;box-shadow:0 8px 30px rgba(0,0,0,.08)">',
      '  <div style="font-size:17px;font-weight:600;margin-bottom:10px">本地服务还没启动</div>',
      '  <div style="margin-bottom:6px">你是直接双击网页文件打开的，页面能显示，但',
      '  <b>作物库、播期推算、天气、地图搜索</b>都需要本地服务在后台运行。</div>',
      '  <div style="margin:10px 0;padding:10px 14px;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px">',
      '    请双击项目根目录的 <b>start-local.bat</b>（E:\\Projects\\CodeArts\\agritime\\start-local.bat）',
      '  </div>',
      '  <div id="file-guard-status" style="color:#6b7280;font-size:13px">正在等待服务启动…起来后本页会自动恢复</div>',
      '</div>'
    ].join('');
    (document.body || document.documentElement).appendChild(overlay);
    return overlay;
  }

  function hideOverlay() {
    if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
    overlay = null;
  }

  // 只重载一次，避免服务闪断时反复刷新
  var reloaded = false;
  try { reloaded = sessionStorage.getItem('fileguard-reloaded') === '1'; } catch (e) { /* 隐私模式 */ }

  function setStatus(text) {
    var el = overlay && overlay.querySelector('#file-guard-status');
    if (el) el.textContent = text;
  }

  function tick() {
    probe(SITE + '/__ping').then(function (siteUp) {
      if (siteUp) { location.replace(siteTarget()); return; }
      probe(BACKEND + '/health').then(function (up) {
        if (up) {
          if (overlay) {
            // 服务刚起来：页面加载时的一批初始请求已经失败了，重载一次重拿
            if (!reloaded) {
              try { sessionStorage.setItem('fileguard-reloaded', '1'); } catch (e) { /* 同上 */ }
              location.reload();
            } else {
              hideOverlay();
            }
          }
          return;
        }
        ensureOverlay();
        setStatus('正在等待服务启动…起来后本页会自动恢复（已重试 ' + (++retries) + ' 次）');
      });
    });
  }

  var retries = 0;
  tick();
  setInterval(tick, 2500);
})();
