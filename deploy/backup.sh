#!/usr/bin/env bash
# 知农时 · 每日备份
#
#   /agritime/deploy/backup.sh            # 手动跑一次
#   KEEP=30 /agritime/deploy/backup.sh    # 保留 30 天（默认 14）
#
# 备份内容：
#   1) data/ —— 作物库、病害库、指导规则、地域表等规则数据；
#   2) backend/var/ —— users.json（账号库 + 令牌签名密钥）、我的作物/地块/
#      历史、提醒、传感器、站点开关、超管审计日志（2026-11-23 起补入）；
#   3) MariaDB agritime 库全量 mysqldump —— 用户业务数据存 MySQL
#      （userdata / plot_history / reminders / sensors 表），不备份会丢；
#   4) 后端配置、nginx 站点配置、敏感环境文件、当前 git 版本号。
#
# 不备份 frontend/ 与 backend/build/：它们都能从 git 和一次编译完整还原。
#
# 定时任务（root）：
#   17 3 * * * /agritime/deploy/backup.sh >> /var/log/agritime-backup.log 2>&1
set -euo pipefail

DEST="${DEST:-/root/backups}"
KEEP="${KEEP:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARBALL="$DEST/agritime-$STAMP.tar.gz"
SQLGZ="$DEST/agritime-$STAMP.mysql.gz"
ENV_FILE=/etc/agritime.env
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=agritime

mkdir -p "$DEST"

# 1) 文件备份：规则数据 + 账号/用户运行数据 + 配置与敏感环境
tar -czf "$TARBALL" -C / \
  agritime/data \
  agritime/backend/var \
  agritime/backend/application.properties \
  agritime/deploy/nginx/agritime.conf \
  etc/agritime.env \
  etc/nginx/sites-enabled/agritime

# 2) MySQL 全量备份：账号密码从敏感环境文件读取，不回显、
#    不写进命令行（走 MYSQL_PWD，避免 ps 泄露）
if [ -f "$ENV_FILE" ]; then
  DB_USER="$(sed -n 's/^STORE_MYSQL_USER=//p' "$ENV_FILE" | tail -n1)"
  DB_PASS="$(sed -n 's/^STORE_MYSQL_PASSWORD=//p' "$ENV_FILE" | tail -n1)"
  DB_URL="$(sed -n 's/^STORE_MYSQL_URL=//p' "$ENV_FILE" | tail -n1)"
  [ -n "$DB_URL" ] && DB_NAME="$(printf '%s' "$DB_URL" | sed -n 's#.*://[^/]*/##p' | cut -d'?' -f1)"
fi

if [ -n "${DB_USER:-}" ] && [ -n "${DB_PASS:-}" ]; then
  ERR_FILE="$DEST/.agritime-$STAMP.dumperr"
  if MYSQL_PWD="$DB_PASS" mysqldump \
       -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
       --single-transaction --routines --triggers --events \
       "$DB_NAME" 2>"$ERR_FILE" | gzip > "$SQLGZ"; then
    rm -f "$ERR_FILE"
  else
    echo "[backup] MySQL 导出失败：" >&2
    sed 's/^/[backup]   /' "$ERR_FILE" >&2
    rm -f "$SQLGZ" "$ERR_FILE"
    exit 1
  fi
  # 空导出绝不能替换掉昨天的好备份
  if [ ! -s "$SQLGZ" ]; then
    echo "[backup] MySQL 导出为空，放弃本次备份" >&2
    rm -f "$SQLGZ"
    exit 1
  fi
  gzip -t "$SQLGZ"
else
  echo "[backup] 未配置 STORE_MYSQL_USER/PASSWORD，跳过 MySQL 导出（文件备份已生成）"
fi

git -C /agritime rev-parse HEAD > "$DEST/agritime-$STAMP.rev"

# 只清理本脚本产出的归档，别碰目录里其它手工备份
find "$DEST" -maxdepth 1 -name 'agritime-*.tar.gz'  -mtime +"$KEEP" -delete
find "$DEST" -maxdepth 1 -name 'agritime-*.mysql.gz' -mtime +"$KEEP" -delete
find "$DEST" -maxdepth 1 -name 'agritime-*.rev'      -mtime +"$KEEP" -delete

printf '[backup] %s %s (%s) + %s (%s)\n' \
  "$(date '+%F %T')" "$TARBALL" "$(du -h "$TARBALL" | cut -f1)" \
  "$SQLGZ" "$(du -h "$SQLGZ" | cut -f1)"
