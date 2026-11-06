#!/usr/bin/env bash
# T30 回滚脚本：MySQL → 文件存储。
#
# 当切换 MySQL 后发现问题、想退回文件存储时：
#   1. 用本脚本把 MySQL 里的 userdata 表倒回 backend/var/userdata/*.json；
#   2. 把 store.backend 改回 file（或删掉该配置）；
#   3. 重启服务。
#
# 用法（本机 mysql 客户端；root 走 unix_socket 认证，无需 -p）：
#   bash deploy/rollback-to-file.sh
set -euo pipefail

USER="${MYSQL_USER:-root}"
DB="${MYSQL_DB:-agritime}"

mkdir -p backend/var/userdata

# 一行一条：k \t data。data 里可能有换行，按主键一条条取，避免按行切坏 JSON。
mysql -N -B --raw -u"$USER" "$DB" \
  -e "SELECT k FROM userdata ORDER BY k" | while IFS= read -r k; do
    [ -z "$k" ] && continue
    data=$(mysql -N -B --raw -u"$USER" "$DB" \
      -e "SELECT data FROM userdata WHERE k='$k'")
    printf '%s' "$data" > "backend/var/userdata/$k.json"
    echo "已回滚 $k"
  done

echo "回滚完成。把 store.backend 改回 file 后重启服务即可。"
