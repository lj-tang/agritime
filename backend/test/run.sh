#!/usr/bin/env bash
# 逆地理编码解析回归（不需要网络、不需要 key）
#
#   bash backend/test/run.sh
#
# 用固定的高德/腾讯报文验证字段抽取与展示名拼装，
# 覆盖：neighborhood 命中、住宅小区、直辖市 city 为空数组、腾讯数字状态码、上游报错降级。
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$DIR/.." && pwd)"
OUT="$(mktemp -d)"

javac -encoding UTF-8 -cp "$BACKEND/build/classes" -d "$OUT" \
  "$DIR/com/cropcalendar/service/geo/GeoParseTest.java"
java -cp "$BACKEND/build/classes:$OUT" com.cropcalendar.service.geo.GeoParseTest
rm -rf "$OUT"
