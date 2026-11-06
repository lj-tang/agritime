#!/usr/bin/env bash
# 知农时 · MySQL 存储接入（一次性执行）
#
# 前提：已装 mariadb-server，已建库建表（见 data/store-mysql.sql），
#       已下载 /agritime/lib/mariadb-java-client-3.3.3.jar，
#       且 systemd ExecStart 的 classpath 已带上该 jar。
#
# 用法（在服务器上，root 执行）：
#   bash /agritime/deploy/setup-mysql.sh <数据库密码>
#
# 做三件事：
#   1. 创建 agritime 数据库用户并授权；
#   2. 把 store.* 配置写进 /etc/agritime.env（幂等，重复执行不产生重复项）；
#   3. 重启 crop-calendar 服务。
#
# 回滚：把 /etc/agritime.env 里的 STORE_BACKEND=mysql 改回 file（或删掉 STORE_*），重启即可。
set -euo pipefail

PW="${1:-}"
if [ -z "$PW" ]; then
  echo "用法：bash deploy/setup-mysql.sh <数据库密码>" >&2
  exit 1
fi

mysql -uroot -e "CREATE USER IF NOT EXISTS 'agritime'@'127.0.0.1' IDENTIFIED BY '$PW'; \
GRANT ALL PRIVILEGES ON agritime.* TO 'agritime'@'127.0.0.1'; FLUSH PRIVILEGES;"

ENV=/etc/agritime.env
TMP="$ENV.tmp"
touch "$ENV"
grep -vE '^STORE_' "$ENV" > "$TMP" || true
{
  echo "STORE_BACKEND=mysql"
  echo "STORE_MYSQL_URL=jdbc:mariadb://127.0.0.1:3306/agritime?useUnicode=true&characterEncoding=utf8mb4"
  echo "STORE_MYSQL_USER=agritime"
  echo "STORE_MYSQL_PASSWORD=$PW"
} >> "$TMP"
mv "$TMP" "$ENV"

systemctl restart crop-calendar
echo "已配置并重启。验证：curl http://127.0.0.1:8080/health"
