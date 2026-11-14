@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ============================================================
rem  知农时 · 本地一键启动
rem    双击运行：编译后端（仅首次）→ 起后端 8080 → 起前端 3000 → 开浏览器
rem    想强制重新编译：start-local.bat rebuild
rem ============================================================

set "JAVA=E:\DevTools\Intellij_IDEA2026\jbr\bin\java.exe"
set "JAVAC=E:\DevTools\Intellij_IDEA2026\jbr\bin\javac.exe"
set "NODE=E:\DevTools\nodejs\node.exe"

where java >nul 2>&1 && set "JAVA=java"
where javac >nul 2>&1 && set "JAVAC=javac"
if not exist "%NODE%" set "NODE=node"

if not exist "%JAVA%" (
  echo [错误] 找不到 Java。装一个 JDK 21+，或改本文件顶部的 JAVA 路径。
  pause
  exit /b 1
)
if not exist "%NODE%" (
  where node >nul 2>&1 || (
    echo [错误] 找不到 Node.js。装一个 Node 18+，或改本文件顶部的 NODE 路径。
    pause
    exit /b 1
  )
)

cd backend
set "NEED_BUILD=0"
if /i "%~1"=="rebuild" set "NEED_BUILD=1"
if not exist "build\classes-local\com\cropcalendar\Server.class" set "NEED_BUILD=1"

if "!NEED_BUILD!"=="1" (
  echo [1/3] 编译后端（首次约 10-20 秒）...
  if not exist "build\classes-local" mkdir "build\classes-local"
  dir /s /b src\*.java > "build\sources.txt"
  "%JAVAC%" -encoding UTF-8 -nowarn -d "build\classes-local" "@build\sources.txt"
  if errorlevel 1 (
    echo.
    echo [错误] 编译失败，上面是 javac 的输出。
    pause
    exit /b 1
  )
  echo       编译通过
) else (
  echo [1/3] 已有编译产物，跳过编译（要重编请运行 start-local.bat rebuild）
)

echo [2/3] 启动后端 http://127.0.0.1:8080 ...
start "知农时-后端" cmd /k ""%JAVA%" -Dfile.encoding=UTF-8 -cp build\classes-local com.cropcalendar.Server"
cd ..
ping -n 4 127.0.0.1 >nul

echo [3/3] 启动前端 http://127.0.0.1:3000 ...
start "知农时-前端" cmd /k ""%NODE%" deploy\dev-server.js"
ping -n 3 127.0.0.1 >nul

start "" http://127.0.0.1:3000/

echo.
echo ============================================================
echo  已启动
echo    网页   http://127.0.0.1:3000/
echo    接口   http://127.0.0.1:8080/health
echo.
echo  关闭服务：直接关掉那两个命令行窗口
echo  改了 Java 代码：start-local.bat rebuild
echo  改了前端文件：刷新浏览器即可（本地不缓存）
echo ============================================================
echo.
pause
