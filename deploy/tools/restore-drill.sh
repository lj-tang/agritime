#!/usr/bin/env bash
# 知农时 · 备份恢复演练（只读，不碰线上数据）
#
#   bash deploy/tools/restore-drill.sh              # 校验最新一份备份
#   bash deploy/tools/restore-drill.sh 20261123-031701   # 校验指定时间戳
#   bash deploy/tools/restore-drill.sh --keep      # 保留临时目录方便排查
#
# 校验四件事：
#   1) 文件备份和 MySQL 备份的 gzip 完整性；
#   2) 解压后账号库 users.json、站点配置、后端配置、nginx 配置齐全；
#   3) MySQL 备份能导入临时库 agritime_drill_<stamp>，四张业务表行数可读；
#   4) 清理临时库与临时目录（--keep 时保留目录）。
#
# 退出码：0 = PASS；非 0 = 演练失败（缺文件/导入失败/无建库权限）。
set -euo pipefail

DEST="${DEST:-/root/backups}"
KEEP_TMP=0
STAMP_ARG=""
for a in "$@"; do
  case "$a" in
    --keep) KEEP_TMP=1 ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "[drill] 未知参数：$a" >&2; exit 2 ;;
    *) STAMP_ARG="$a" ;;
  esac
done

ENV_FILE=/etc/agritime.env
[ -r "$ENV_FILE" ] || { echo "[drill] 读不到 $ENV_FILE" >&2; exit 1; }
# 只取需要的三个键，不 source 整个环境文件（避免值里的特殊字符被执行）
DB_USER="$(sed -n 's/^STORE_MYSQL_USER=//p' "$ENV_FILE" | tail -n1 | tr -d '"')"
DB_PASS="$(sed -n 's/^STORE_MYSQL_PASSWORD=//p' "$ENV_FILE" | tail -n1 | tr -d '"')"
DB_URL="$(sed -n 's/^STORE_MYSQL_URL=//p' "$ENV_FILE" | tail -n1 | tr -d '"')"
DB_NAME="$(printf '%s' "$DB_URL" | sed -n 's#.*://[^/]*/##p' | cut -d'?' -f1)"
DB_NAME="${DB_NAME:-agritime}"

if [ -n "$STAMP_ARG" ]; then
  TARBALL="$DEST/agritime-$STAMP_ARG.tar.gz"
  SQLGZ="$DEST/agritime-$STAMP_ARG.mysql.gz"
else
  TARBALL="$(ls -1t "$DEST"/agritime-*.tar.gz 2>/dev/null | head -n1 || true)"
  [ -n "$TARBALL" ] || { echo "[drill] $DEST 下没有 agritime-*.tar.gz" >&2; exit 1; }
  SQLGZ="${TARBALL%.tar.gz}.mysql.gz"
fi

for f in "$TARBALL" "$SQLGZ"; do
  [ -f "$f" ] || { echo "[drill] 缺少备份文件：$f" >&2; exit 1; }
done

DRILL_ID="$(date +%Y%m%d%H%M%S)"
TMP="$(mktemp -d "/tmp/agritime-drill-$DRILL_ID.XXXXXX")"
TEMP_DB="agritime_drill_$DRILL_ID"
FAIL=0

cleanup() {
  if [ -n "$DB_USER" ] && [ -n "$DB_PASS" ]; then
    MYSQL_PWD="$DB_PASS" mysql -h127.0.0.1 -u"$DB_USER" \
      -e "DROP DATABASE IF EXISTS \`$TEMP_DB\`" >/dev/null 2>&1 || true
  fi
  if [ "$KEEP_TMP" = "1" ]; then
    echo "[drill] 临时目录保留：$TMP"
  else
    rm -rf "$TMP"
  fi
}
trap cleanup EXIT

echo "[drill] 文件备份：$TARBALL"
echo "[drill] 数据备份：$SQLGZ"
echo "[drill] 1/4 校验压缩包完整性"
gzip -t "$TARBALL"
gzip -t "$SQLGZ"
echo "  OK"

echo "[drill] 2/4 解压并核对关键文件（$TMP）"
tar -xzf "$TARBALL" -C "$TMP"
for p in \
  agritime/backend/var/users.json \
  agritime/backend/var/settings.json \
  agritime/backend/application.properties \
  etc/agritime.env \
  etc/nginx/sites-enabled/agritime; do
  if [ -e "$TMP/$p" ] || [ -L "$TMP/$p" ]; then
    echo "  存在：$p"
  else
    echo "  缺失：$p" >&2
    FAIL=1
  fi
done
if [ -f "$TMP/agritime/backend/var/users.json" ]; then
  USERS="$(grep -o '"username"' "$TMP/agritime/backend/var/users.json" | wc -l)"
  echo "  账号数：$USERS"
fi
DATA_FILES="$(find "$TMP/agritime/data" -type f 2>/dev/null | wc -l)"
echo "  data/ 文件数：$DATA_FILES"
if [ "$DATA_FILES" -lt 20 ]; then
  echo "  data/ 文件数异常（少于 20），备份可能不完整" >&2
  FAIL=1
fi

echo "[drill] 3/4 导入 MySQL 备份到临时库 $TEMP_DB"
if [ -n "$DB_USER" ] && [ -n "$DB_PASS" ]; then
  if MYSQL_PWD="$DB_PASS" mysql -h127.0.0.1 -u"$DB_USER" \
       -e "CREATE DATABASE \`$TEMP_DB\` CHARACTER SET utf8mb4" 2>/dev/null; then
    MYSQL_PWD="$DB_PASS" mysql -h127.0.0.1 -u"$DB_USER" "$TEMP_DB" < <(gzip -dc "$SQLGZ")
    echo "  导入成功"
    for t in userdata plot_history reminders sensors; do
      if MYSQL_PWD="$DB_PASS" mysql -N -h127.0.0.1 -u"$DB_USER" \
           -e "SELECT COUNT(*) FROM \`$TEMP_DB\`.\`$t\`" >/dev/null 2>&1; then
        C="$(MYSQL_PWD="$DB_PASS" mysql -N -h127.0.0.1 -u"$DB_USER" \
             -e "SELECT COUNT(*) FROM \`$TEMP_DB\`.\`$t\`")"
        echo "  表 $t：$C 行"
      else
        echo "  表 $t：不存在" >&2
        FAIL=1
      fi
    done
  else
    echo "  当前数据库账号没有建库权限，SQL 导入演练跳过（文件完整性已校验）" >&2
    FAIL=1
  fi
else
  echo "  /etc/agritime.env 未配置 STORE_MYSQL_USER/PASSWORD，SQL 导入演练跳过" >&2
  FAIL=1
fi

echo "[drill] 4/4 结果"
if [ "$FAIL" = "0" ]; then
  echo "  PASS：备份可解压、可导入，关键文件齐全"
else
  echo "  FAIL：见上方错误项" >&2
fi
exit "$FAIL"
