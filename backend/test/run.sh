#!/usr/bin/env bash
# 后端回归测试：不需要网络、不需要 key
#
#   bash backend/test/run.sh
#
# 遍历 test/com 下所有 *Test.java 的 main：
#   · GeoParseTest / GeoClassifyTest —— 逆地理解析与地块几何
#   · ReminderStoreTest —— 提醒档案持久化、去重、已读、开关、过期清理
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$DIR/.." && pwd)"
# 固定输出目录：javac 每次覆盖，不用 rm -rf（部署机上的删除保护会拦）
OUT="$BACKEND/build/test-classes"
mkdir -p "$OUT"

SOURCES="$(find "$DIR/com" -name '*Test.java' | sort)"
if [ -z "$SOURCES" ]; then
  echo "没有找到测试类" >&2
  exit 1
fi

javac -encoding UTF-8 -cp "$BACKEND/build/classes" -d "$OUT" $SOURCES

for f in $SOURCES; do
  cls="$(printf '%s' "$f" | sed "s|^$DIR/||; s|/|.|g; s|\.java$||")"
  echo "--- $cls ---"
  java -cp "$BACKEND/build/classes:$OUT" "$cls"
done
