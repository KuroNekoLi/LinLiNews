# 本地 Docker + Cloudflared 隧道設置指南

這個指南將幫助您在本地運行 LinLi News 專案，並使用 Cloudflared 隧道讓其他人可以訪問。

## 前置需求

✅ Docker Desktop 已安裝並運行  
✅ JDK 21 已安裝  
✅ Cloudflared 已安裝在 `C:\Program Files (x86)\cloudflared\cloudflared.exe`

## 方法 1：使用批次檔案（推薦）

### 步驟 1：啟動應用服務

雙擊 `start-with-tunnel.bat` 或在 PowerShell 中執行：

```powershell
.\start-with-tunnel.bat
```

### 步驟 2：啟動隧道服務

**另開一個 PowerShell 視窗**，雙擊 `start-tunnel.bat` 或執行：

```powershell
.\start-tunnel.bat
```

## 方法 2：手動執行

### 步驟 1：啟動基礎服務

```powershell
# 啟動 MySQL 和 Full-Text RSS
docker-compose up -d

# 檢查狀態
docker-compose ps
```

### 步驟 2：啟動 Spring Boot

```powershell
.\gradlew.bat bootRun
```

### 步驟 3：開啟隧道

**新開 PowerShell 視窗**：

```powershell
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8080
```

## 取得公開網址

當 cloudflared 啟動後，您會看到類似這樣的輸出：

```
Your quick Tunnel has been created! Visit it at:
https://xxxxx-xxx-xxx.trycloudflare.com
```

**這個網址就是其他人可以訪問的公開網址！**

## 重要端點

- **API 文件**: `https://your-tunnel-url.trycloudflare.com/swagger-ui/index.html`
- **文章列表**: `https://your-tunnel-url.trycloudflare.com/api/articles`
- **隨機文章**: `https://your-tunnel-url.trycloudflare.com/api/articles/random?count=3`

## 設置 Swagger 正確顯示外部網址

如果您希望 Swagger UI 顯示正確的外部網址：

1. 取得 cloudflared 提供的網址
2. 編輯 `start-with-tunnel.bat`
3. 取消註釋並修改這一行：
   ```batch
   set SERVER_URL=https://your-actual-tunnel-url.trycloudflare.com
   ```
4. 重新啟動應用

## 故障排除

### Docker 服務無法啟動

```powershell
# 檢查 Docker Desktop 是否運行
docker --version

# 重新啟動服務
docker-compose down
docker-compose up -d
```

### Spring Boot 啟動失敗

- 確認 MySQL 容器已啟動且健康：`docker-compose ps`
- 檢查 3307 端口是否被占用
- 查看應用日誌找出錯誤原因

### 隧道無法創建

- 確認 cloudflared 路徑正確
- 檢查網路連線
- 確認本地 8080 端口的服務正在運行

### 外部用戶無法訪問

- 確認防火牆沒有阻擋
- 檢查 cloudflared 隧道狀態
- 確認使用正確的公開網址

## 停止服務

### 停止 Spring Boot

在運行應用的終端按 `Ctrl+C`

### 停止 Cloudflared 隧道

在運行隧道的終端按 `Ctrl+C`

### 停止 Docker 服務

```powershell
docker-compose down
```

## 安全注意事項

- ⚠️ 這會將您的應用暴露到公共網路
- ⚠️ 請勿在生產環境使用此方法
- ⚠️ Cloudflare 隧道網址會在重啟後改變
- ⚠️ 建議定期監控訪問日誌

## 範例 API 測試

其他人可以使用以下命令測試您的 API：

```bash
# 取得文章列表
curl "https://your-tunnel-url.trycloudflare.com/api/articles?page=0&size=5"

# 取得隨機文章
curl "https://your-tunnel-url.trycloudflare.com/api/articles/random?count=3"

# 查看 API 文件
# 直接在瀏覽器開啟：https://your-tunnel-url.trycloudflare.com/swagger-ui/index.html
```