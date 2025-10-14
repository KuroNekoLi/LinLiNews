@echo off
echo 啟動 LinLi News...

REM 設定必要的環境變數
set OPENAI_API_KEY=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
set SERVER_URL=https://api.kuronekoli.uk

echo 啟動 Docker...
cd /d C:\Users\WIN10\IdeaProjects\LinLiNews
docker-compose up -d

echo 啟動隧道...
start "Cloudflare-Tunnel" cmd /c "title Cloudflare Tunnel && cloudflared tunnel --config C:\Users\WIN10\.cloudflared\config.yml run myhome"

echo 等待隧道建立...
timeout /t 10 /nobreak > nul

echo 啟動應用...
echo 📍 本機: http://localhost:8080/swagger-ui/index.html
echo 🌐 公開: https://api.kuronekoli.uk/swagger-ui/index.html
echo.

C:\Users\WIN10\IdeaProjects\LinLiNews\gradlew.bat bootRun
