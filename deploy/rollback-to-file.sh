#!/usr/bin/env bash
# T30 回滚脚本：MySQL → 文件存储。
#
# 当切换 MySQL 后发现问题、想退回文件存储时：
#   1. 用本脚本把 MySQL 里的 userdata 表倒回 var/userdata/*.json；
#   2. 把 store.backend 改回 file（或删掉该配置）；
#   3. 重启服务。
#
# 用法（需要本机 mysql 客户端，-N 去表头、-B 制表符分隔避免空格被切）：
#   bash deploy/rollback-to-file.sh
set -euo pipefail

HOST="${MYSQL_HOST:-127.0.0.1}"
USER="${MYSQL_USER:-root}"
DB="${MYSQL_DB:-agritime}"

mkdir -p var/userdata

# 一行一条：k \t data。data 里可能有换行，用 --raw 且按 k 分组读出会破坏换行，
# 因此这里用逐行 LOOP 的方式，按主键一条条取，最稳妥。
mysql -N -B --raw -h"$HOST" -u"$USER" -p "$DB" \
  -e "SELECT k FROM userdata ORDER BY k" | while IFS= read -r k; do
    [ -z "$k" ] && continue
    data=$(mysql -N -B --raw -h"$HOST" -u"$USER" -p "$DB" \
      -e "SELECT data FROM userdata WHERE k='$k'")
    printf '%s' "$data" > "var/userdata/$k.json"
    echo "已回滚 $k"
  done

echo "回滚完成。把 store.backend 改回 file 后重启服务即可。"
