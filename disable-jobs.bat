@echo off
echo ========================================
echo 停用定時任務與 Full-Text RSS 服務
echo ========================================
echo.

echo [1/2] 停止 Full-Text RSS 容器...
docker stop fulltextrss
if %ERRORLEVEL% EQU 0 (
    echo ✓ Full-Text RSS 已停止
) else (
    echo ✗ Full-Text RSS 停止失敗（可能已經停止）
)
echo.

echo [2/2] 設定環境變數以停用 JobRunr...
echo.
echo 請在啟動應用程式時使用以下環境變數：
echo.
echo   $env:JOBRUNR_SCHEDULER_ENABLED="false"
echo   $env:JOBRUNR_BACKGROUND_ENABLED="false"
echo   .\gradlew.bat bootRun
echo.
echo 或者建立 .env 檔案包含：
echo   JOBRUNR_SCHEDULER_ENABLED=false
echo   JOBRUNR_BACKGROUND_ENABLED=false
echo.

echo ========================================
echo 完成！定時任務已停用
echo ========================================
pause
