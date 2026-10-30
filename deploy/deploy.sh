#!/usr/bin/env bash
# 知农时 · 一键部署
#
#   在服务器上执行：  /agritime/deploy/deploy.sh
#   指定分支：        BRANCH=dev /agritime/deploy/deploy.sh
#   只重编译不拉取：  SKIP_PULL=1 /agritime/deploy/deploy.sh
#
# 做四件事：拉取仓库 → 编译后端 → 重启服务 → 健康检查（失败自动回滚）
# 前端是静态文件，nginx 直接指向 /agritime/frontend/pages，无需构建。
set -euo pipefail

APP_DIR="${APP_DIR:-/agritime}"
BACKEND_DIR="$APP_DIR/backend"
BRANCH="${BRANCH:-main}"
SERVICE="${SERVICE:-crop-calendar.service}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/health}"

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] 失败：%s\n' "$*" >&2; exit 1; }

command -v git   >/dev/null 2>&1 || die "找不到 git"
command -v javac >/dev/null 2>&1 || die "找不到 javac（apt install default-jdk）"

cd "$APP_DIR"
[ -d .git ] || die "$APP_DIR 不是 git 仓库，先执行一次 deploy/init-server.sh"

PREV="$(git rev-parse HEAD)"
log "当前版本 ${PREV:0:7}（$(git log -1 --format=%s "$PREV" | cut -c1-40)）"

# ---------- 1. 拉取 ----------
if [ "${SKIP_PULL:-0}" != "1" ]; then
  # 国内 ECS 到 GitHub 的 TLS 连接经常被掐断（GnuTLS recv error -110），
  # 单次 fetch 失败率不低，所以重试三次；仍失败时若本地已有该分支就带警告继续。
  fetch_ok=0
  for i in 1 2 3; do
    if git fetch --prune origin; then fetch_ok=1; break; fi
    [ "$i" -lt 3 ] && { log "拉取失败（第 $i 次），3 秒后重试"; sleep 3; }
  done
  if [ "$fetch_ok" != "1" ]; then
    if git rev-parse --verify -q "origin/$BRANCH" >/dev/null; then
      log "警告：拉取持续失败，沿用本地已知的 origin/$BRANCH（可能不是最新）"
    else
      die "拉取失败，本地也没有 origin/$BRANCH 可回退"
    fi
  fi

  TARGET="$(git rev-parse "origin/$BRANCH")"
  if [ "$PREV" = "$TARGET" ]; then
    log "远端无新提交，仍继续重编译"
  elif git merge-base --is-ancestor "$TARGET" "$PREV"; then
    # 本地 HEAD 比 origin 更新：说明有提交没推上去（这台机器到 GitHub 经常断）。
    # 此时 reset --hard 会把新代码直接抹掉，改成跳过重置、按当前工作区部署。
    log "警告：本地 ${PREV:0:7} 领先 origin/$BRANCH ${TARGET:0:7}（有提交未推送）"
    log "警告：跳过 reset，按当前工作区部署；请尽快补推，否则下次正常部署会回退"
  elif git merge-base --is-ancestor "$PREV" "$TARGET"; then
    log "更新代码 ${PREV:0:7} -> ${TARGET:0:7}"
    # 服务器是纯部署端，本地改动一律丢弃，以仓库为准
    git reset --hard "origin/$BRANCH"
  else
    # 双方各有对方没有的提交：自动 reset 会静默丢掉一边，必须人工处理
    log "严重：本地与 origin/$BRANCH 已分叉，脚本不做自动处理"
    log "  本地独有：$(git rev-list --count "$TARGET..$PREV") 个提交"
    log "  远端独有：$(git rev-list --count "$PREV..$TARGET") 个提交"
    die "分支分叉，请人工处理后重跑（或 SKIP_PULL=1 只部署当前工作区）"
  fi
fi

# ---------- 2. 编译 ----------
log "编译后端（$(find "$BACKEND_DIR/src" -name '*.java' | wc -l) 个源文件）"
rm -rf "$BACKEND_DIR/build/classes.new"
mkdir -p "$BACKEND_DIR/build/classes.new"
if ! ( cd "$BACKEND_DIR" && javac -encoding UTF-8 -d build/classes.new $(find src -name '*.java') ) 2>"$BACKEND_DIR/build/javac.err"; then
  echo "----- javac 输出 -----" >&2
  head -30 "$BACKEND_DIR/build/javac.err" >&2
  rm -rf "$BACKEND_DIR/build/classes.new"
  die "编译失败，旧版本继续运行，未重启服务"
fi
log "编译通过"

# ---------- 3. 切换字节码并重启 ----------
rm -rf "$BACKEND_DIR/build/classes.prev"
[ -d "$BACKEND_DIR/build/classes" ] && mv "$BACKEND_DIR/build/classes" "$BACKEND_DIR/build/classes.prev"
mv "$BACKEND_DIR/build/classes.new" "$BACKEND_DIR/build/classes"
log "重启 $SERVICE"
systemctl restart "$SERVICE"

# ---------- 4. 健康检查 ----------
ok=0
for _ in $(seq 1 10); do
  sleep 2
  if curl -sf -m 3 "$HEALTH_URL" >/dev/null 2>&1; then ok=1; break; fi
done

if [ "$ok" = "1" ]; then
  rm -rf "$BACKEND_DIR/build/classes.prev" "$BACKEND_DIR/build/javac.err"
  log "部署成功，版本 $(git rev-parse --short HEAD)"
  exit 0
fi

# ---------- 回滚 ----------
log "健康检查未通过，回滚到 ${PREV:0:7}"
git reset --hard "$PREV" >/dev/null 2>&1 || true
rm -rf "$BACKEND_DIR/build/classes"
[ -d "$BACKEND_DIR/build/classes.prev" ] && mv "$BACKEND_DIR/build/classes.prev" "$BACKEND_DIR/build/classes"
systemctl restart "$SERVICE"
die "已回滚到上一个版本，请查看 journalctl -u $SERVICE -n 50"
