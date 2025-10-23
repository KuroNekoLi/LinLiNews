@echo off
echo ========================================
echo 啟用定時任務與 Full-Text RSS 服務
echo ========================================
echo.

echo [1/2] 啟動 Full-Text RSS 容器...
docker start fulltextrss
if %ERRORLEVEL% EQU 0 (
    echo ✓ Full-Text RSS 已啟動
) else (
    echo ✗ Full-Text RSS 啟動失敗
    echo 可能需要先執行: docker-compose up -d
)
echo.

echo [2/2] 設定環境變數以啟用 JobRunr...
echo.
echo 請在啟動應用程式時使用以下環境變數：
echo.
echo   $env:JOBRUNR_SCHEDULER_ENABLED="true"
echo   $env:JOBRUNR_BACKGROUND_ENABLED="true"
echo   .\gradlew.bat bootRun
echo.
echo 或者建立 .env 檔案包含：
echo   JOBRUNR_SCHEDULER_ENABLED=true
echo   JOBRUNR_BACKGROUND_ENABLED=true
echo.

echo ========================================
echo 完成！定時任務已啟用
echo ========================================
pause
