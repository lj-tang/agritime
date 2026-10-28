#!/usr/bin/env bash
# 知农时 · 服务器首次接管脚本（只跑一次）
#
#   在服务器上执行：  bash /agritime/deploy/init-server.sh
#
# 把已经在跑但"没有版本控制"的 /agritime 接管为 git 工作区。
# 前提：服务器文件内容已与仓库 origin/main 一致（否则本脚本会覆盖，见下方提示）。
set -euo pipefail

APP_DIR="${APP_DIR:-/agritime}"
REPO="${REPO:-https://github.com/lj-tang/agritime.git}"
BRANCH="${BRANCH:-main}"

log() { printf '[init] %s\n' "$*"; }
die() { printf '[init] 失败：%s\n' "$*" >&2; exit 1; }

cd "$APP_DIR" || die "$APP_DIR 不存在"

if [ -d .git ]; then
  log "$APP_DIR 已经是 git 仓库，无需初始化。直接用 deploy/deploy.sh 部署。"
  exit 0
fi

# 备份：万一仓库内容与线上不一致，还能从这里捞回来
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/root/agritime-before-git-$STAMP.tar.gz"
log "备份现有文件到 $BACKUP"
tar -czf "$BACKUP" -C "$(dirname "$APP_DIR")" "$(basename "$APP_DIR")" 2>/dev/null || \
  die "备份失败，中止（先确认磁盘空间）"

git init -q
git remote add origin "$REPO"
git fetch --prune origin || die "拉取失败，检查网络与 /root/.git-credentials"

# 用仓库内容覆盖工作区（与仓库不一致的旧文件会被覆盖，所以上面先打了 tar 备份）
log "以仓库内容覆盖工作区：origin/$BRANCH"
git reset --hard "origin/$BRANCH"
git checkout -q -B "$BRANCH" "origin/$BRANCH"
git branch --set-upstream-to="origin/$BRANCH" "$BRANCH" >/dev/null

# 保留部署端自己的构建产物与密钥文件（仓库已忽略它们）
log "当前版本：$(git log -1 --oneline)"

if [ -n "$(git status --porcelain)" ]; then
  log "警告：工作区与仓库仍有差异（多为服务器上手工改动），差异如下："
  git status --short | head -20
  log "如需以仓库为准，执行：cd $APP_DIR && git reset --hard origin/$BRANCH"
else
  log "工作区干净，与仓库完全一致"
fi

log "初始化完成。备份：$BACKUP"
log "之后部署一律执行：$APP_DIR/deploy/deploy.sh"
