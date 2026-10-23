#!/usr/bin/env bash
# ECS 启动脚本 —— 后台常驻运行，日志写到 server.log
# 用法：deploy/ecs-start.sh
# 停止：deploy/ecs-stop.sh
set -e

# 脚本在 deploy/ 下，服务根目录是上一级
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$APP_DIR"

# 找 JDK：优先 JAVA_HOME，其次系统 java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java)"
fi

if [ -z "$JAVA" ]; then
  echo "找不到 JDK。请先安装：yum install -y java-17-openjdk-devel" >&2
  exit 1
fi

if [ ! -d build/classes ]; then
  echo "找不到 build/classes，请确认已解压完整部署包" >&2
  exit 1
fi

# 检查是否已在运行
PID_FILE="$APP_DIR/server.pid"
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "服务已在运行 (PID $OLD_PID)，如需重启请先执行 deploy/ecs-stop.sh"
    exit 1
  fi
  rm -f "$PID_FILE"
fi

# 后台启动
nohup "$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -cp build/classes com.cropcalendar.Server \
  > server.log 2>&1 &

echo $! > "$PID_FILE"
PID=$(cat "$PID_FILE")

echo "服务已启动 (PID $PID)"
echo "日志：tail -f $APP_DIR/server.log"
echo ""

# 等待启动，检查健康
sleep 2
if kill -0 "$PID" 2>/dev/null; then
  PORT="${SERVER_PORT:-8080}"
  if curl -sf "http://localhost:$PORT/health" > /dev/null 2>&1; then
    echo "健康检查通过 -> http://localhost:$PORT/health"
  else
    echo "进程在运行但健康检查未通过，查看日志：tail -20 server.log"
  fi
else
  echo "进程已退出，查看日志：tail -20 server.log"
  exit 1
fi