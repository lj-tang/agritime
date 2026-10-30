# 知农时 · 微信小程序端（T11）

与网页端共用同一套 `/api/*`，不另开后端分支；核心日期与时间线逻辑复用 `shared/utils`。

## 运行前必须做的三件事

1. **填 AppID**：`project.config.json` 里 `appid` 目前为空，替换成你自己的小程序 AppID，
   否则微信开发者工具只能以「游客模式」打开，无法真机预览。
2. **配域名**：`app.js` 的 `apiBase` 改成你自己的**已备案 HTTPS 域名**。
   微信小程序后台 → 开发 → 开发设置 → 服务器域名，把这个域名加进 `request 合法域名`。
   未备案域名（包括直接用 IP）无法在小程序里访问——这与网页端的 HTTPS 限制是同一个原因。
3. **同步共享逻辑**：

   ```bash
   node miniprogram/scripts/sync-shared.js
   ```

   微信只允许引用 `miniprogramRoot` 内的文件，不能 `require` 项目根目录的 `shared/`，
   所以先把 `shared/utils/{date-window,timeline-logic}.js` 复制到 `miniprogram/utils/shared/`。
   这两个文件是构建产物，已在 `.gitignore` 里忽略。

## 已实现的页面

| 页面 | 路径 | 功能 |
| --- | --- | --- |
| 查询 | `pages/index` | 作物输入 + 日期选择 + **按节气推荐起点**（调 `/api/sow-window`） |
| 农事日历 | `pages/timeline` | 调 `/api/timeline`，节点按时间线排布，点击展开四类指导；第 5 天起标注「天气为预测数据，仅供参考」 |

## 尚未验证的部分（如实说明）

- 本仓库没有小程序 AppID，也**没有在微信开发者工具或真机上跑过**，
  只做了 JS 语法检查与 JSON 结构校验。
- 首次在开发者工具打开时，若提示找不到 `utils/shared/*.js`，先执行上面的同步命令。
- 登录态与「我的地块 / 我的作物」尚未接入小程序端——那部分依赖账号 Cookie，
  小程序需要换成 token 方案，属于后续工作。
