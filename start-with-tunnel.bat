@echo off
echo 正在啟動 LinLi News 服務...
echo.

echo 步驟 1: 啟動 Docker 服務 (MySQL, Full-Text RSS)
docker-compose up -d

echo.
echo 步驟 2: 等待服務就緒...
timeout /t 10 /nobreak > nul

echo.
echo 步驟 3: 啟動 Spring Boot 應用
echo 注意: 請先在另一個終端中啟動 cloudflared 隧道
echo 命令: "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8080
echo.

REM 如果您已經取得 cloudflared 網址，請取消註釋下面這行並填入您的網址
REM set SERVER_URL=https://your-cloudflare-url.trycloudflare.com

echo 正在啟動應用程式...
.\gradlew.bat bootRun

pause