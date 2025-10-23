# Zeabur 環境變數設定指南

本文件說明如何在 Zeabur 上透過環境變數控制應用程式的功能與資源使用。

---

## 📋 目錄

- [必要環境變數](#必要環境變數)
- [資源控制變數](#資源控制變數)
- [進階配置變數](#進階配置變數)
- [部署場景範例](#部署場景範例)

---

## 必要環境變數

這些是應用程式運行的最低要求：

```bash
# 資料庫連線（由 Zeabur MySQL Add-on 自動提供）
SPRING_DATASOURCE_URL=jdbc:mysql://<MYSQL_HOST>:<PORT>/linli_news?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>

# OpenAI API（AI 功能必要）
OPENAI_API_KEY=<YOUR_OPENAI_API_KEY>
```

---

## 資源控制變數

### 🎛️ 定時任務控制（JobRunr）

控制是否自動抓取 RSS 新聞：

```bash
# 啟用定時任務（預設，自動抓取新聞）
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true

# 停用定時任務（節省 CPU 資源，不自動更新）
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false
```

**影響**：

- ✅ **啟用時**：每 30-60 分鐘自動抓取 BBC 新聞
- ❌ **停用時**：不會自動更新，但 API 查詢功能正常

---

### 🔧 Full-Text RSS 服務控制

控制是否使用 Full-Text RSS 進行全文抓取：

```bash
# 啟用 Full-Text RSS（預設，完整功能）
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<FTR_INTERNAL_HOST>

# 停用 Full-Text RSS（節省記憶體與網路資源）
FULLTEXTRSS_ENABLED=false
# 停用後不需要部署 Full-Text RSS 服務
```

**影響**：

- ✅ **啟用時**：
    - RSS 內容不完整時，自動抓取完整文章內容
    - 需要運行 Full-Text RSS Docker 容器（~200MB 記憶體）

- ❌ **停用時**：
    - 只使用 RSS 原始內容（可能不完整）
    - 不需要部署 Full-Text RSS 服務，節省資源
    - 定時任務會跳過匯入（因為需要 Full-Text RSS）

---

## 進階配置變數

### Flyway 資料庫遷移

```bash
# 啟用自動資料庫遷移（預設，建議保持啟用）
SPRING_FLYWAY_ENABLED=true
```

### OpenAI 進階設定

```bash
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o
OPENAI_MAX_TOKENS=16000
OPENAI_EXPLANATION_FALLBACK_ENABLED=true
```

### JobRunr Dashboard（不建議在生產環境啟用）

```bash
JOBRUNR_DASHBOARD_ENABLED=false
JOBRUNR_DASHBOARD_PORT=8001
```

---

## 部署場景範例

### 🟢 場景 1：全功能運行（推薦用於正式環境）

**適合**：有充足資源，需要自動更新新聞內容

```bash
# 必要
SPRING_DATASOURCE_URL=jdbc:mysql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
OPENAI_API_KEY=...

# 功能控制
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<ftr-service>.zeabur.internal
```

**所需服務**：

- ✅ MySQL (Zeabur Add-on)
- ✅ Full-Text RSS (Docker)
- ✅ Spring Boot App

**資源估計**：

- CPU: ~25%
- Memory: ~800MB
- 網路: 持續使用（定期抓取）

---

### 🟡 場景 2：手動更新模式（節省資源）

**適合**：資源有限，不需要即時更新，偶爾手動觸發

```bash
# 必要
SPRING_DATASOURCE_URL=jdbc:mysql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
OPENAI_API_KEY=...

# 功能控制
JOBRUNR_SCHEDULER_ENABLED=false      # 停用定時任務
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<ftr-service>.zeabur.internal
```

**所需服務**：

- ✅ MySQL (Zeabur Add-on)
- ✅ Full-Text RSS (Docker) - 僅手動觸發時使用
- ✅ Spring Boot App

**資源估計**：

- CPU: ~15%
- Memory: ~600MB
- 網路: 按需使用

**手動觸發方式**：

```bash
# 注意：測試端點在生產環境會被停用
# 需要透過其他方式（如定期腳本）觸發
```

---

### 🔴 場景 3：僅查詢模式（最低資源）

**適合**：只展示現有資料，不新增內容

```bash
# 必要
SPRING_DATASOURCE_URL=jdbc:mysql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
OPENAI_API_KEY=...

# 功能控制
JOBRUNR_SCHEDULER_ENABLED=false      # 停用定時任務
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=false            # 停用 Full-Text RSS
```

**所需服務**：

- ✅ MySQL (Zeabur Add-on) - 含現有資料
- ❌ Full-Text RSS - 不需要
- ✅ Spring Boot App

**資源估計**：

- CPU: ~10%
- Memory: ~400MB
- 網路: 最低使用

**限制**：

- 無法自動更新文章
- 無法匯入新文章
- 只能查詢與 AI 處理現有文章

---

## 🔄 動態切換

您可以隨時在 Zeabur 控制台修改環境變數來切換模式：

1. 進入 Zeabur 專案控制台
2. 選擇您的服務（Spring Boot App）
3. 點擊 "Variables" 標籤
4. 修改相應的環境變數
5. 重啟服務使變更生效

### 切換範例

**從全功能切換到節省資源模式**：

```bash
# 設定這兩個變數為 false
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false

# 可選：如果不需要匯入功能
FULLTEXTRSS_ENABLED=false
```

**從節省模式恢復到全功能**：

```bash
# 設定這兩個變數為 true
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
FULLTEXTRSS_ENABLED=true
```

---

## 📊 資源使用對比表

| 環境變數配置 | CPU | Memory | 網路 | 功能完整度 | 適用場景    |
|--------|-----|--------|----|-------|---------|
| 全功能模式  | 高   | 高      | 持續 | 100%  | 正式運營    |
| 手動更新模式 | 中   | 中      | 按需 | 90%   | 開發/測試   |
| 僅查詢模式  | 低   | 低      | 最低 | 60%   | Demo/展示 |

---

## 🛠️ 故障排查

### 問題：定時任務沒有執行

**檢查**：

```bash
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
```

### 問題：文章內容不完整

**檢查**：

```bash
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<正確的內網主機名>
```

### 問題：記憶體使用過高

**解決**：

```bash
# 停用定時任務
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false

# 停用 Full-Text RSS
FULLTEXTRSS_ENABLED=false
```

---

## 📝 注意事項

1. **環境變數優先級**：環境變數 > application.yml
2. **重啟生效**：修改環境變數後需要重啟服務
3. **Full-Text RSS 依賴**：
    - 如果 `FULLTEXTRSS_ENABLED=false`，定時匯入會跳過
    - API 查詢與 AI 處理功能不受影響
4. **測試端點**：生產環境（`prod` profile）會自動停用測試/管理端點
5. **費用考量**：
    - 停用定時任務可節省 CPU 與網路費用
    - 停用 Full-Text RSS 可節省記憶體費用
    - OpenAI API 依使用量計費

---

## 🔗 相關文件

- [README.md](./README.md) - 專案總覽與本機開發
- [LOCAL_TUNNEL_SETUP.md](./LOCAL_TUNNEL_SETUP.md) - 本機隧道設定

---

**最後更新**：2025-01-14
