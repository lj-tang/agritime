#!/usr/bin/env node
/**
 * 本地开发服务器：静态文件 + /api 反向代理。
 *
 * 线上是 nginx 干这两件事（见 deploy/nginx/agritime.conf）：
 *   静态 root = frontend/pages，另有 /assets、/shared、/data/voice 三个 alias；
 *   /api 与 /health 反代到本机 8080 的 Java 服务。
 *
 * 本机没有 nginx，所以用这段 Node 复刻同样的路由规则 —— 只是开发便利，
 * 不参与线上部署，也不改任何业务代码。
 *
 *   node deploy/dev-server.js            # 默认 3000 端口
 *   PORT=4000 node deploy/dev-server.js
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const PAGES = path.join(ROOT, 'frontend', 'pages');
const PORT = Number(process.env.PORT || 3000);
const API_HOST = process.env.API_HOST || '127.0.0.1';
const API_PORT = Number(process.env.API_PORT || 8080);

/** nginx 里的三个 alias，本地照搬 */
const ALIASES = [
  ['/assets/', path.join(ROOT, 'frontend', 'assets')],
  ['/shared/', path.join(ROOT, 'shared')],
  ['/data/voice/', path.join(ROOT, 'data', 'voice')],
  ['/components/', path.join(ROOT, 'frontend', 'components')]
];

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8'
};

function resolveStatic(urlPath) {
  const clean = decodeURIComponent(urlPath.split('?')[0].split('#')[0]);
  for (const [prefix, dir] of ALIASES) {
    if (clean.startsWith(prefix)) {
      const rel = clean.slice(prefix.length);
      const file = path.join(dir, rel);
      return file.startsWith(dir) ? file : null;   // 防目录穿越
    }
  }
  const file = path.join(PAGES, clean);
  return file.startsWith(PAGES) ? file : null;
}

function sendFile(res, file, method) {
  let target = file;
  try {
    if (fs.existsSync(target) && fs.statSync(target).isDirectory()) target = path.join(target, 'index.html');
  } catch (e) { /* 忽略，下面统一 404 */ }

  if (!fs.existsSync(target)) {
    const notFound = path.join(PAGES, '404.html');
    if (fs.existsSync(notFound)) {
      const body = fs.readFileSync(notFound);
      res.writeHead(404, { 'Content-Type': MIME['.html'] });
      res.end(method === 'HEAD' ? undefined : body);
      return;
    }
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('404 Not Found: ' + file);
    return;
  }

  const ext = path.extname(target).toLowerCase();
  const body = fs.readFileSync(target);
  res.writeHead(200, {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Content-Length': body.length,
    // 线上是 no-cache + ETag（改完立刻生效），本地更该这样
    'Cache-Control': 'no-cache'
  });
  res.end(method === 'HEAD' ? undefined : body);
}

function proxy(req, res) {
  const opts = {
    host: API_HOST,
    port: API_PORT,
    method: req.method,
    path: req.url,
    headers: Object.assign({}, req.headers, { host: API_HOST + ':' + API_PORT })
  };
  const upstream = http.request(opts, (r) => {
    res.writeHead(r.statusCode || 502, r.headers);
    r.pipe(res);
  });
  upstream.on('error', (e) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      code: '502',
      message: '后端没起来（' + API_HOST + ':' + API_PORT + '）：' + e.message,
      data: null
    }));
  });
  req.pipe(upstream);
}

const server = http.createServer((req, res) => {
  const url = req.url || '/';
  if (url.startsWith('/api/') || url === '/health' || url.startsWith('/api?')) {
    proxy(req, res);
    return;
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('静态目录只支持 GET/HEAD');
    return;
  }
  const file = resolveStatic(url);
  if (!file) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('403 Forbidden');
    return;
  }
  try {
    sendFile(res, file, req.method);
  } catch (e) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('500 ' + e.message);
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('[dev] 静态目录 ' + PAGES);
  console.log('[dev] /api → http://' + API_HOST + ':' + API_PORT);
  console.log('[dev] 打开 http://127.0.0.1:' + PORT + '/');
});
