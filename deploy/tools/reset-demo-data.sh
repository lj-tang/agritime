#!/usr/bin/env bash
# 知农时 · 演示数据重置（回到「只有超管、业务数据为空」的干净状态）
#
#   bash deploy/tools/reset-demo-data.sh --dry-run              # 只预览要删什么
#   bash deploy/tools/reset-demo-data.sh --yes                  # 备份后执行
#   bash deploy/tools/reset-demo-data.sh --yes --keep testuser  # 保留某个演示账号
#
# 清理范围：
#   - backend/var/users.json 里除 super_admin 与 --keep 指定的账号；
#   - 被删账号在 backend/var/{userdata,plot-history,reminders,sensors} 下的文件；
#   - MySQL agritime 库四张业务表中被删账号的行；
#   - 反馈工单 backend/var/support/tickets.json（演示前的旧咨询一起清空）。
# 执行前会把用户数据与数据库完整备份到 /root/backups/demo-reset-<时间戳>.*
set -euo pipefail

VAR_DIR=/agritime/backend/var
USERS_FILE="$VAR_DIR/users.json"
ENV_FILE=/etc/agritime.env
SERVICE=crop-calendar.service
DEST="${DEST:-/root/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_TAR="$DEST/demo-reset-$STAMP.tar.gz"
BACKUP_SQL="$DEST/demo-reset-$STAMP.mysql.gz"

DRY=0
CONFIRM=0
KEEP_USERS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --yes) CONFIRM=1 ;;
    --keep) KEEP_USERS="${2:-}"; shift ;;
    --keep=*) KEEP_USERS="${1#--keep=}" ;;
    -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "[reset] 未知参数：$1" >&2; exit 2 ;;
  esac
  shift
done
if [ "$DRY" = "0" ] && [ "$CONFIRM" = "0" ]; then
  echo "[reset] 先加 --yes 确认（或 --dry-run 预览）" >&2
  exit 2
fi
[ -f "$USERS_FILE" ] || { echo "[reset] 找不到 $USERS_FILE" >&2; exit 1; }

KEEP_IDS="$(python3 - "$USERS_FILE" "$KEEP_USERS" <<'PY'
import json, sys
path, keep_raw = sys.argv[1], sys.argv[2]
doc = json.load(open(path))
users = doc["users"] if isinstance(doc, dict) and "users" in doc else doc
keep_names = {n.strip() for n in keep_raw.split(",") if n.strip()}
ids = [u["id"] for u in users
       if u.get("id") == "u_super_admin" or u.get("role") == "admin"
       or u.get("username") in keep_names]
print(" ".join(ids))
PY
)"
REMOVE_USERS="$(python3 - "$USERS_FILE" "$KEEP_IDS" <<'PY'
import json, sys
path, keep = sys.argv[1], set(sys.argv[2].split())
doc = json.load(open(path))
users = doc["users"] if isinstance(doc, dict) and "users" in doc else doc
for u in users:
    if u.get("id") not in keep:
        print(f'{u["id"]}\t{u.get("username","")}\t{u.get("role","")}')
PY
)"

if [ -z "$REMOVE_USERS" ]; then
  echo "[reset] 没有需要删除的账号，数据已经是干净状态"
  exit 0
fi

echo "[reset] 将删除以下账号及其数据："
printf '%s\n' "$REMOVE_USERS" | awk -F'\t' '{printf "  %s  %s  (%s)\n", $1, $2, $3}'
echo "[reset] 保留：$(printf '%s' "$KEEP_IDS" | tr ' ' '\n' | sed 's/^/  /' | paste -sd, -)"

REMOVE_IDS="$(printf '%s\n' "$REMOVE_USERS" | cut -f1)"

if [ "$DRY" = "1" ]; then
  echo "[reset] (dry-run) 数据库将删除的行："
  for t in userdata reminders plot_history sensors; do
    N="$(mysql --protocol=socket -u root -N -e \
      "SELECT COUNT(*) FROM agritime.$t WHERE k IN ($(printf "'%s'," $REMOVE_IDS | sed 's/,$//'))" 2>/dev/null || echo '?')"
    echo "  $t: $N"
  done
  echo "[reset] (dry-run) 文件将删除："
  for id in $REMOVE_IDS; do
    find "$VAR_DIR/userdata" "$VAR_DIR/plot-history" "$VAR_DIR/reminders" "$VAR_DIR/sensors" \
      -maxdepth 1 -name "$id.json" 2>/dev/null || true
  done
  echo "[reset] (dry-run) 不执行任何修改"
  exit 0
fi

mkdir -p "$DEST"
echo "[reset] 备份到 $BACKUP_TAR / $BACKUP_SQL"
tar -czf "$BACKUP_TAR" -C / agritime/backend/var
DB_USER="$(sed -n 's/^STORE_MYSQL_USER=//p' "$ENV_FILE" | tail -n1 | tr -d '"')"
DB_PASS="$(sed -n 's/^STORE_MYSQL_PASSWORD=//p' "$ENV_FILE" | tail -n1 | tr -d '"')"
if [ -n "$DB_USER" ] && [ -n "$DB_PASS" ]; then
  MYSQL_PWD="$DB_PASS" mysqldump -h127.0.0.1 -u"$DB_USER" \
    --single-transaction agritime 2>/dev/null | gzip > "$BACKUP_SQL"
  gzip -t "$BACKUP_SQL"
fi

echo "[reset] 停止服务"
systemctl stop "$SERVICE"

python3 - "$USERS_FILE" "$KEEP_IDS" <<'PY'
import json, os, sys
path, keep = sys.argv[1], set(sys.argv[2].split())
st = os.stat(path)
doc = json.load(open(path))
if isinstance(doc, dict) and "users" in doc:
    doc["users"] = [u for u in doc["users"] if u.get("id") in keep]
    out = doc
else:
    out = [u for u in doc if u.get("id") in keep]
tmp = path + ".tmp"
with open(tmp, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
# 保持属主/权限不变：服务以 agritime 用户读这个文件，写成 root:600 会导致登录全挂
os.chmod(tmp, st.st_mode & 0o777)
os.chown(tmp, st.st_uid, st.st_gid)
os.replace(tmp, path)
PY
echo "[reset] users.json 已更新（保留 $(wc -c < "$USERS_FILE") 字节）"

for id in $REMOVE_IDS; do
  for dir in userdata plot-history reminders sensors; do
    rm -f "$VAR_DIR/$dir/$id.json"
  done
done
rm -f "$VAR_DIR/support/tickets.json"
echo "[reset] 已清理用户数据文件与工单"

if [ -n "$DB_USER" ] && [ -n "$DB_PASS" ]; then
  IDS_SQL="$(printf "'%s'," $REMOVE_IDS | sed 's/,$//')"
  for t in userdata reminders plot_history sensors; do
    mysql --protocol=socket -u root -e "DELETE FROM agritime.$t WHERE k IN ($IDS_SQL)"
    echo "  $t: 已删除 $REMOVE_IDS 对应的行"
  done
fi

echo "[reset] 启动服务"
systemctl start "$SERVICE"
for _ in $(seq 1 30); do
  if curl -sf -o /dev/null http://127.0.0.1:8080/health; then
    echo "[reset] 服务已恢复（/health 200）"
    break
  fi
  sleep 1
done
curl -sf -o /dev/null http://127.0.0.1:8080/health || { echo "[reset] 服务未恢复，检查 journalctl -u $SERVICE" >&2; exit 1; }

echo "[reset] 完成。备份：$BACKUP_TAR（如需回滚：systemctl stop $SERVICE && tar -xzf $BACKUP_TAR -C / && systemctl start $SERVICE）"
