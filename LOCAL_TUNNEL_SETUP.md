# LinLi News — 本地部署 + Cloudflare 隧道

這份文件適用於固定域名部署（如：kuronekoli.uk），僅需一個步驟即可自動發布公開 API 網址。

## 一鍵啟動（唯一推薦，最簡流程）

> 本專案只需要執行 `start-simple.bat` 就能：
> - 啟動 Docker (MySQL + Full-Text RSS)
> - 啟動 Cloudflare 隧道（固定公開網址 https://app.kuronekoli.uk）
> - 啟動 Spring Boot 應用
    > 其他批次檔案都不需要。

### 步驟

1. 確認 `.cloudflared` 目錄 **（通常在 `C:\Users\WIN10\.cloudflared`）** 包含下列檔案：
   - `cert.pem`
   - `config.yml`（含正確 ingress 規則，指向 http://localhost:8080）
   - `{tunnel-id}.json`（Cloudflare 隧道憑證）
2. 本機的 Cloudflared 隧道 tunnel 名稱、DNS 及 Cloudflare 網域都已配置好。
3. 雙擊專案根目錄的 `start-simple.bat`。

所有啟動、憑證、映射、Cloudflare 隧道都會自動完成。

---

### `start-simple.bat` 範例內容

```bat
@echo off
echo 啟動 LinLi News...
REM 設定必要的環境變數
set OPENAI_API_KEY=sk-xxxxxx
set SERVER_URL=https://app.kuronekoli.uk
echo 啟動 Docker...
docker-compose up -d
echo 啟動隧道...
start "Cloudflare-Tunnel" cmd /c "title Cloudflare Tunnel && cloudflared tunnel --config C:\Users\WIN10\.cloudflared\config.yml run myhome"
echo 等待隧道建立...
timeout /t 10 /nobreak > nul
echo 啟動應用...
echo 📍 本機: http://localhost:8080/swagger-ui/index.html
echo 🌐 公開: https://app.kuronekoli.uk/swagger-ui/index.html
echo.
.\gradlew.bat bootRun
```

### 公開網址 & 測試 API

- 公開網址：https://app.kuronekoli.uk/swagger-ui/index.html
- API 範例：
   - https://app.kuronekoli.uk/api/articles?page=0&size=5
   - https://app.kuronekoli.uk/api/ai-articles/random-with-processing?count=3

---

## 常見故障處理

- **本地可以，公開 503/1033**：通常是 cloudflared 沒有連正確憑證或 config，重新執行 tunnel login 並確保憑證在主用戶資料夾。
- **本地 8080 占用**：確保無其他程式佔用。
- **config.yml 無 ingress**：務必包含 hostname 指向 http://localhost:8080。
- **API Key 無效**：在批次檔或 `OPENAI_API_KEY` 環境變數指定。
- **Swagger 無法打開**：先檢查本機 http://localhost:8080 運行正常。
- 隧道正在運行：使用 `cloudflared tunnel list`、`cloudflared tunnel info myhome` 檢查，或 Cloudflare 網站 Dashboard。

---

## 停止與清理

### 停止所有服務

- 在運行的終端視窗按 `Ctrl+C`
- 或關閉 `Cloudflare-Tunnel` 視窗
- Docker 下：
```powershell
docker-compose down
```

### 完全清理
```powershell
docker-compose down -v
del tunnel_url.txt
del %TEMP%\tunnel*.txt
del %TEMP%\tunnel*.log
```

---

## 安全提醒

- 公開 API 為開發/測試用，勿直接放生產
- OpenAI API Key 請勿提交至 git 倉庫
- Cloudflare 隧道失效時需重新登入與憑證校驗
- 所有多餘啟動批次檔案都可直接刪除，不再維護

---

## 舊啟動方式已移除

- `start-all.bat`、`start-with-notification.bat`、`start-complete.bat`、`start-with-ai.bat`、`start-with-tunnel.bat`、
  `start-tunnel.bat` 等都可刪除。
- 本文件只保留一鍵起啟、唯一的官方方案。