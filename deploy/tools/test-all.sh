#!/usr/bin/env bash
# 全量测试入口（Linux / macOS / CI 用）。Windows 用 deploy/tools/test-all.ps1。
#
#   bash deploy/tools/test-all.sh
#
# 顺序：后端编译 → 后端回归测试 → 前端页面测试 → 契约校验 → 数据资产接线检查。
# 任一步失败即退出，便于 CI 与本地一键自检。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "== 1/6 后端编译 =="
mkdir -p backend/build/classes
javac -encoding UTF-8 -d backend/build/classes $(find backend/src -name '*.java')

echo "== 2/6 后端回归测试 =="
bash backend/test/run.sh

echo "== 3/6 前端页面测试 =="
node frontend/tests/pages.test.js

echo "== 4/6 契约校验 =="
node shared/constants/validate-contracts.js

echo "== 5/6 数据资产接线检查 =="
python3 deploy/tools/check-data-assets.py

echo "== 6/6 浏览器冒烟测试 =="
if [ "${SKIP_E2E:-0}" = "1" ]; then
  echo "SKIP_E2E=1，跳过浏览器测试"
else
  node frontend/tests/e2e/run.js
fi

echo "全部通过。"
