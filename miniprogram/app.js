// 知农时 · 微信小程序入口（T11）
// 与网页端共用同一套 /api/*，不另开后端分支。
App({
  globalData: {
    // 改成你自己的已备案 HTTPS 域名；开发时可在开发者工具里勾选「不校验合法域名」
    apiBase: 'https://your-domain.example.com',
    lastQuery: null
  }
});
