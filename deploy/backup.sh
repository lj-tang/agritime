#!/usr/bin/env bash
# 知农时 · 每日备份
#
#   /agritime/deploy/backup.sh            # 手动跑一次
#   KEEP=30 /agritime/deploy/backup.sh    # 保留 30 天（默认 14）
#
# 备份内容：规则数据（data/）、后端配置、nginx 站点配置、敏感环境文件、当前 git 版本号。
# 不备份 frontend/ 与 backend/build/：它们都能从 git 和一次编译完整还原。
#
# 定时任务（root）：
#   17 3 * * * /agritime/deploy/backup.sh >> /var/log/agritime-backup.log 2>&1
set -euo pipefail

DEST="${DEST:-/root/backups}"
KEEP="${KEEP:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARBALL="$DEST/agritime-$STAMP.tar.gz"

mkdir -p "$DEST"

tar -czf "$TARBALL" -C / \
  agritime/data \
  agritime/backend/application.properties \
  agritime/deploy/nginx/agritime.conf \
  etc/agritime.env \
  etc/nginx/sites-enabled/agritime

git -C /agritime rev-parse HEAD > "$DEST/agritime-$STAMP.rev"

# 只清理本脚本产出的归档，别碰目录里其它手工备份
find "$DEST" -maxdepth 1 -name 'agritime-*.tar.gz' -mtime +"$KEEP" -delete
find "$DEST" -maxdepth 1 -name 'agritime-*.rev' -mtime +"$KEEP" -delete

printf '[backup] %s %s (%s)\n' "$(date '+%F %T')" "$TARBALL" "$(du -h "$TARBALL" | cut -f1)"
