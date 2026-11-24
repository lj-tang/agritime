/**
 * 浏览器端冒烟测试（不依赖真实后端）
 *
 * 覆盖主链路的三个关键点：
 *   1. 首页能打开、侧栏与查询表单在、无 JS 异常；
 *   2. 时间线页能把 API 数据渲染成节点，并出现改期入口；
 *   3. 接口失败时展示统一的错误态，点重试能恢复到有数据状态。
 *
 * API 全部用 Playwright route 拦截返回固定 fixture，所以跑得快、可复现。
 * 本地：node frontend/tests/e2e/run.js
 * CI  ：npm ci && npx playwright install --with-deps chromium && npm run test:e2e
 */
'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert');

const ROOT = path.resolve(__dirname, '../../..');
const PAGES = path.join(ROOT, 'frontend/pages');
const TIMELINE = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/timeline.json'), 'utf8'));

let chromium;
try {
  ({ chromium } = require('playwright'));
} catch (e) {
  // 开发者本机可复用 .dev 里已有的依赖，不必为跑一次冒烟再装一遍。
  ({ chromium } = require(path.join(ROOT, '.dev/node_modules/playwright')));
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2'
};

function staticPath(pathname) {
  const clean = decodeURIComponent(pathname.split('?')[0]);
  if (clean === '/' || clean === '/index.html') return path.join(PAGES, 'index.html');
  if (/^\/(timeline|auth|admin|404)\.html$/.test(clean)) return path.join(PAGES, path.basename(clean));
  if (clean === '/sw.js' || clean === '/manifest.json') return path.join(PAGES, path.basename(clean));
  if (clean.startsWith('/assets/') || clean.startsWith('/components/')) {
    return path.join(ROOT, 'frontend', clean);
  }
  if (clean.startsWith('/shared/') || clean.startsWith('/data/voice/')) {
    return path.join(ROOT, clean);
  }
  return null;
}

function startServer() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const file = staticPath(url.pathname);
      if (!file || !file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('not found: ' + url.pathname);
        return;
      }
      res.writeHead(200, {
        'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
        'Cache-Control': 'no-store'
      });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function apiBody(pathname, method) {
  if (pathname === '/api/auth/me') {
    return { code: 0, message: 'ok', data: { user: { username: 'nonghu01', nickname: '农户01', role: 'user' } } };
  }
  if (pathname === '/api/site-config') {
    return { code: 0, message: 'ok', data: { searchEnabled: true, llmEnabled: true } };
  }
  if (pathname === '/api/crops') {
    return {
      total: 3, offset: 0, query: '', categories: [],
      crops: [
        { cropId: 'C093', name: '大麦', category: '禾谷类', hasProtocol: true, nodeCount: 9 },
        { cropId: 'C001', name: '大蒜', category: '蔬菜类', hasProtocol: true, nodeCount: 9 },
        { cropId: 'C010', name: '水稻', category: '禾谷类', hasProtocol: true, nodeCount: 10 }
      ]
    };
  }
  if (pathname === '/api/timeline') return TIMELINE;
  if (pathname === '/api/geo/ip') {
    return { code: 0, message: 'ok', data: { lat: 35.07, lon: 116.31, city: '济宁' } };
  }
  if (pathname === '/api/reminders' || pathname.startsWith('/api/reminders/')) {
    return { code: 0, message: 'ok', data: { items: [], unreadCount: 0 } };
  }
  if (pathname === '/api/support/tickets') {
    return {
      code: 0, message: 'ok',
      data: { items: [], total: 0, page: 1, pageSize: 8, totalPages: 1, unreadCount: 0, statusCounts: { all: 0, open: 0, processing: 0, resolved: 0 } }
    };
  }
  if (pathname === '/api/me/data' && method === 'GET') {
    return { code: 0, message: 'ok', data: { crops: [], plots: [], sensors: [], history: [], updatedAt: new Date().toISOString() } };
  }
  return { code: 0, message: 'ok', data: {} };
}

async function installMocks(context, state) {
  await context.route('**/api/**', async (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    if (pathname === '/api/timeline' && state.timelineFails) {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '服务暂时不可用' }) });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiBody(pathname, req.method()))
    });
  });
  // 外部天气/定位绝不打真实网络，避免 CI 因上游抖动误报。
  await context.route('https://**', (route) => route.abort());
  await context.route('http://**', (route) => {
    if (route.request().url().includes('127.0.0.1')) return route.continue();
    return route.abort();
  });
}

async function launch() {
  try {
    return await chromium.launch({ headless: true });
  } catch (e) {
    // 本机没装 Playwright 自带 chromium 时，复用系统 Edge。
    return chromium.launch({ headless: true, channel: 'msedge' });
  }
}

(async () => {
  const server = await startServer();
  const base = 'http://127.0.0.1:' + server.address().port;
  const browser = await launch();
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const state = { timelineFails: false };
  await installMocks(context, state);

  const errors = [];
  const page = await context.newPage();
  page.on('pageerror', (e) => errors.push(e.message));

  try {
    // 1. 首页
    await page.goto(base + '/index.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#sidebar-nav');
    await page.waitForSelector('#timeline-form');
    const sidebarCount = await page.locator('#sidebar-nav .sidebar-item').count();
    assert.ok(sidebarCount >= 5, '侧栏模块应渲染出来，实际 ' + sidebarCount);
    console.log('  ✓ 首页渲染：侧栏 ' + sidebarCount + ' 项');

    // 2. 时间线 + 改期入口
    await page.goto(base + '/timeline.html?crop=dry_garlic&sowDate=2026-06-01&lat=35.07&lon=116.31', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#timeline .tl-node', { timeout: 10000 });
    const nodes = await page.locator('#timeline .tl-node').count();
    const editButtons = await page.locator('#timeline .tl-edit-date').count();
    assert.strictEqual(nodes, 4, '时间线应渲染 4 个节点');
    assert.ok(editButtons >= 3, '具体日期节点应有改期入口');
    console.log('  ✓ 时间线渲染：' + nodes + ' 节点 / ' + editButtons + ' 个改期入口');

    // 3. 错误态 + 重试恢复
    state.timelineFails = true;
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.ui-state--error', { timeout: 10000 });
    assert.ok(await page.locator('.ui-state-action').count() > 0, '错误态应有重试按钮');
    state.timelineFails = false;
    await page.locator('.ui-state-action').first().click();
    await page.waitForSelector('#timeline .tl-node', { timeout: 10000 });
    assert.strictEqual(await page.locator('#timeline .tl-node').count(), 4, '重试后应恢复节点');
    console.log('  ✓ 错误态与重试恢复');

    assert.deepStrictEqual(errors, [], '页面不应有未捕获异常：' + errors.join(' | '));
    console.log('E2E PASS（首页 / 时间线 / 错误重试，无 JS 异常）');
  } finally {
    await browser.close();
    server.close();
  }
})().catch((err) => {
  console.error('E2E FAIL:', err && err.message ? err.message : err);
  process.exitCode = 1;
});
