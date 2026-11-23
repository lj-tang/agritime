/* 知农时 Service Worker
 * 目标：弱网/离线下页面壳与静态资源可用，接口数据一律不缓存（避免看到隔夜天气与旧时间线）。
 * 版本号只在需要强制刷新缓存时递增。
 */
var VERSION = 'v1';
var SHELL_CACHE = 'agritime-shell-' + VERSION;
var ASSET_CACHE = 'agritime-asset-' + VERSION;

var SHELL = [
  '/index.html',
  '/timeline.html',
  '/auth.html',
  '/404.html',
  '/manifest.json',
  '/assets/css/common.css',
  '/assets/js/pwa.js',
  '/assets/img/common-logo.svg',
  '/assets/icons/icon-192.png'
];

function isApi(path) {
  return path.indexOf('/api/') === 0 || path === '/health' || path.indexOf('/api?') === 0;
}

function isStatic(path) {
  return (
    path.indexOf('/assets/') === 0 ||
    path.indexOf('/components/') === 0 ||
    path.indexOf('/shared/') === 0 ||
    path.indexOf('/data/voice/') === 0
  );
}

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(SHELL_CACHE).then(function (cache) {
      // 逐个加入：个别文件 404 不应让整个安装失败
      return Promise.all(
        SHELL.map(function (url) {
          return cache.add(new Request(url, { cache: 'reload' })).catch(function () {});
        })
      );
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(
        keys.filter(function (key) {
          return key.indexOf('agritime-') === 0 && key !== SHELL_CACHE && key !== ASSET_CACHE;
        }).map(function (key) { return caches.delete(key); })
      );
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  var req = event.request;
  var url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (isApi(url.pathname)) return;

  // 页面导航：先网络（保证拿到最新 html），失败回退缓存的壳
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req).then(function (res) {
        if (res && res.ok) {
          var copy = res.clone();
          caches.open(SHELL_CACHE).then(function (cache) { cache.put(req, copy); });
        }
        return res;
      }).catch(function () {
        return caches.match(req).then(function (hit) {
          return hit || caches.match('/index.html');
        });
      })
    );
    return;
  }

  if (!isStatic(url.pathname)) return;

  // 静态资源：stale-while-revalidate，命中即返回后台更新
  event.respondWith(
    caches.open(ASSET_CACHE).then(function (cache) {
      return cache.match(req).then(function (hit) {
        var network = fetch(req).then(function (res) {
          if (res && res.ok) cache.put(req, res.clone());
          return res;
        }).catch(function () { return hit; });
        return hit || network;
      });
    })
  );
});
