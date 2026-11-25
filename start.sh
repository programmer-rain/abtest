#!/usr/bin/env bash
# ============================================================
#  ABTest 教学演示 · 一键启动（macOS / Linux）
#  依赖：JDK 17+、Maven 3.6+
#  前端如需重新构建：cd frontend && npm install && npm run build
# ============================================================
set -e
cd "$(dirname "$0")"

if [ ! -f frontend/dist/index.html ]; then
  echo "[提示] 未找到 frontend/dist，请先执行:"
  echo "       cd frontend && npm install && npm run build"
fi

if [ ! -f backend/target/abtest-backend-1.0.0.jar ]; then
  echo "[1/2] 首次运行，用 Maven 构建后端（需下载依赖，请稍候）..."
  (cd backend && mvn -B -DskipTests package)
else
  echo "[1/2] 后端已构建，跳过（如需重建: cd backend && mvn package）"
fi

echo "[2/2] 启动服务..."
cd backend
exec java -jar target/abtest-backend-1.0.0.jar
