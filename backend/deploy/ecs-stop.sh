#!/usr/bin/env bash
# ECS 停止脚本
set -e

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$APP_DIR/server.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "没有找到 PID 文件，服务可能未运行"
  exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "已发送停止信号给 PID $PID"
  # 等待退出
  for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  # 还没退出就强杀
  if kill -0 "$PID" 2>/dev/null; then
    kill -9 "$PID"
    echo "进程未响应，已强制终止"
  fi
  echo "服务已停止"
else
  echo "PID $PID 已不存在"
fi

rm -f "$PID_FILE"