@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  ShrimpFarm release 构建脚本
REM  用法：双击运行，或命令行执行 build-release.cmd
REM  产物：dist\秒算虾料-1.5.8-release.apk（带版本号）
REM  签名：与 mixcalc-lab 同一把 debug.keystore，V1+V2+V3 三签全开
REM ============================================================

cd /d "%~dp0"

REM 指定 JDK 21（compileSdk 36 需要新 JDK；系统 JAVA_HOME 指向 17 会编译失败）
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/4] JDK:
java -version 2>&1

echo [2/4] 清理旧的 release 产物...
if exist "app\build\outputs\apk\release" rd /s /q "app\build\outputs\apk\release"

echo [3/4] 开始构建 release（V1+V2+V3 签名）...
call gradlew.bat assembleRelease --no-daemon
if errorlevel 1 (
    echo [错误] 构建失败，未生成 APK
    exit /b 1
)

REM 读取版本号，供产物命名
set "VER=1.5.8"
for /f "tokens=2 delims= " %%v in ('findstr /C:"versionName" app\build.gradle') do (
    set "VER=%%~v"
)

echo [4/4] 复制产物到 dist...
if not exist dist mkdir dist
set "SRC=app\build\outputs\apk\release\app-release.apk"
set "DST=dist\秒算虾料-%VER%-release.apk"
if not exist "%SRC%" (
    echo [错误] 找不到构建产物 %SRC%
    exit /b 1
)
copy /y "%SRC%" "%DST%" >nul

echo.
echo ========== 构建完成 ==========
echo APK: %CD%\%DST%
echo.

REM 验证三种签名方案是否都生效
set "APKSIGNER=C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat"
if exist "%APKSIGNER%" (
    echo 签名验证：
    call "%APKSIGNER%" verify --verbose --print-certs "%DST%"
)

endlocal
