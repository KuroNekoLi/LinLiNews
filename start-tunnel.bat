@echo off
echo 正在啟動 Cloudflared 隧道...
echo.
echo 這將創建一個公開隧道指向 http://localhost:8080
echo 請確保您的 Spring Boot 應用已經在運行
echo.

"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8080

pause