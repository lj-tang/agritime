#!/usr/bin/env bash
# 数据库初始化脚本：导入 init.sql（含 400 个农作物规程数据）到 MySQL
# 用法: bash sql/init-db.sh
# 连接配置可通过环境变量覆盖，默认与 src/main/resources/application.yml 一致
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-root}"
DB_NAME="${DB_NAME:-zhinongshi}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="$SCRIPT_DIR/init.sql"

if [ ! -f "$SQL_FILE" ]; then
  echo "错误: 找不到 SQL 文件 $SQL_FILE" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "错误: 未找到 mysql 客户端，请先安装（如: apt-get install -y mysql-client）" >&2
  exit 1
fi

EXPECTED_CROPS=$(grep -c 'INSERT INTO crop_procedure' "$SQL_FILE")
echo "==> 即将导入: $SQL_FILE"
echo "==> 预期作物数: $EXPECTED_CROPS"
echo "==> 目标: $DB_HOST:$DB_PORT （init.sql 内含 CREATE DATABASE/USE $DB_NAME）"
echo ""

mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" < "$SQL_FILE"

echo ""
echo "==> 导入完成，开始验证..."
CROP_COUNT=$(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -e "SELECT COUNT(DISTINCT crop_id) FROM $DB_NAME.crop_procedure;")
ID_RANGE=$(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -e "SELECT MIN(crop_id), MAX(crop_id) FROM $DB_NAME.crop_procedure;")
echo "==> crop_procedure 作物数: $CROP_COUNT | crop_id 范围: $ID_RANGE"

if [ "$CROP_COUNT" -lt "$EXPECTED_CROPS" ]; then
  echo "警告: 实际作物数 $CROP_COUNT < 预期 $EXPECTED_CROPS，可能被其他 SQL 覆盖，请排查" >&2
  exit 2
fi

echo "==> 成功: 模糊搜索现可覆盖全部 $CROP_COUNT 个作物"