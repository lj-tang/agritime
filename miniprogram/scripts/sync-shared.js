#!/usr/bin/env node
/**
 * 把 shared/utils 下的多端共享逻辑同步到小程序目录。
 *
 * 为什么需要这一步：微信小程序只允许引用 miniprogramRoot 之内的文件，
 * 不能 require 项目根目录的 shared/。所以打开开发者工具前先跑一次：
 *
 *   node miniprogram/scripts/sync-shared.js
 *
 * 同步过来的文件在 miniprogram/utils/shared/，已被 .gitignore 忽略（构建产物）。
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const srcDir = path.join(root, 'shared', 'utils');
const outDir = path.resolve(__dirname, '..', 'utils', 'shared');

const files = ['date-window.js', 'timeline-logic.js'];

fs.mkdirSync(outDir, { recursive: true });
let n = 0;
files.forEach(function (f) {
  const from = path.join(srcDir, f);
  if (!fs.existsSync(from)) {
    console.warn('[sync-shared] 跳过（不存在）:', f);
    return;
  }
  fs.copyFileSync(from, path.join(outDir, f));
  n++;
});
console.log('[sync-shared] 已同步 ' + n + ' 个文件到 ' + path.relative(root, outDir));
