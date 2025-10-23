@echo off
echo ========================================
echo 啟動應用程式（無定時任務模式）
echo ========================================
echo.

echo [1/3] 檢查 Docker 服務...
docker-compose ps
echo.

echo [2/3] 停止 Full-Text RSS（減少資源消耗）...
docker stop fulltextrss 2>nul
echo.

echo [3/3] 啟動 Spring Boot（停用定時任務）...
echo.

set JOBRUNR_SCHEDULER_ENABLED=false
set JOBRUNR_BACKGROUND_ENABLED=false

echo 環境變數已設定：
echo   JOBRUNR_SCHEDULER_ENABLED=false
echo   JOBRUNR_BACKGROUND_ENABLED=false
echo.
echo 正在啟動應用程式...
echo.

.\gradlew.bat bootRun
