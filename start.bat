@echo off
rem ============================================================
rem  ABTest 教学演示 · 一键启动（Windows）
rem  依赖：JDK 17+、Maven 3.6+（java/mvn 在 PATH 中）
rem  前端如需重新构建：cd frontend && npm install && npm run build
rem ============================================================
cd /d %~dp0

if not exist frontend\dist\index.html (
  echo [提示] 未找到 frontend\dist，请先执行:
  echo        cd frontend ^&^& npm install ^&^& npm run build
  echo        启动后 API 仍可用，页面会提示先构建前端。
)

if not exist backend\target\abtest-backend-1.0.0.jar (
  echo [1/2] 首次运行，用 Maven 构建后端（需下载依赖，请稍候）...
  pushd backend
  call mvn -B -DskipTests package
  popd
  if errorlevel 1 (
    echo 构建失败，请检查 JDK 17 与 Maven 是否安装
    pause
    exit /b 1
  )
) else (
  echo [1/2] 后端已构建，跳过（如需重建: cd backend ^&^& mvn package）
)

echo [2/2] 启动服务...
cd backend
java -jar target\abtest-backend-1.0.0.jar
pause
