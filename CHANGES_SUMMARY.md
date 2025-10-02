# 更新摘要

## 完成的任務

### 1. ✅ 測試端點在生產環境 Swagger 中完全隱藏

**問題**：雖然使用了 `@Profile("!prod")`，測試端點在生產環境的 Swagger 文件中仍然可見。

**解決方案**：為所有測試/管理端點添加 `@Hidden` 註解

**影響的檔案**：
- `BackgroundJobController.kt` - 整個 controller 添加 `@Hidden`
- `ImportVerificationController.kt` - 整個 controller 添加 `@Hidden`
- `AiArticleController.kt` - 三個測試端點添加 `@Hidden`
  - `/api/ai-articles/{id}/debug-raw-json`
  - `/api/ai-articles/{id}/reprocess-with-debug`
  - `/api/ai-articles/test-json-parsing`
- `ArticleQueryController.kt` - 四個測試/管理端點添加 `@Hidden`
  - `/api/articles/{id}/fetch-content`
  - `/api/articles/fetch-all-missing-content`
  - `/api/articles/cleanup-invalid-articles`
  - `/api/articles/admin/import-bbc`

**結果**：
- 在生產環境（`SPRING_PROFILES_ACTIVE=prod`）：
  - ✅ 測試端點不會出現在 Swagger UI 中
  - ✅ 測試端點無法被調用（返回 404）
- 在開發環境（預設或 `SPRING_PROFILES_ACTIVE=dev`）：
  - ✅ 測試端點正常顯示且可用

---

### 2. ✅ 新增隨機取得文章並自動 AI 處理的端點

**新端點**：`GET /api/ai-articles/random-with-processing`

**功能說明**：
- 從資料庫隨機抽取指定數量的文章
- 檢查每篇文章是否已經過 AI 處理
- 若尚未處理，自動進行 AI 處理並儲存
- 返回處理完成的 AI 文章清單

**參數**：
- `count` - 文章數量（預設 5，最多 20）
- `source` - 來源過濾（可選，例如 BBC）
- `section` - 區塊過濾（可選，例如 news、world）

**回應格式**：
```json
{
  "message": "success",
  "requestedCount": 5,
  "actualCount": 5,
  "totalAvailable": 150,
  "processedCount": 2,
  "filters": {
    "source": null,
    "section": "news"
  },
  "articles": [...]
}
```

其中 `processedCount` 表示本次請求中新處理的文章數量。

**實作細節**：
- 新增 `AiArticleService.randomWithProcessing()` 方法
- 返回 `Triple<List<ArticleAi>, Int, Int>`：
  - 第一個：已處理的 AI 文章清單
  - 第二個：符合條件的可用文章總數
  - 第三個：本次新處理的文章數
- 使用 `@Transactional` 確保資料一致性
- 錯誤處理：若某篇文章處理失敗，會記錄錯誤並繼續處理下一篇

**使用範例**：
```bash
# 隨機取得 3 篇並自動處理
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=3" | jq .

# 指定來源與區塊
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=5&source=BBC&section=world" | jq .
```

---

## 文件更新

### README.md
- ✅ 在「API 文件」章節添加測試端點隱藏說明
- ✅ 在「主要功能」中添加新的 API 端點說明
- ✅ 在「快速開始」中添加新端點的使用範例
- ✅ 優化結構與排版

### KDoc 註解
- ✅ 為 `AiArticleService.random()` 添加完整註解
- ✅ 為 `AiArticleService.randomWithProcessing()` 添加完整註解

---

## 技術細節

### Swagger/OpenAPI 配置
使用 `@Hidden` 註解的優勢：
- 比 `@Profile` 更精確地控制 Swagger 顯示
- 不影響端點的實際可用性（由 `@Profile` 控制）
- 提供更好的 API 文件用戶體驗

### 效能考量
`randomWithProcessing` 方法的效能特性：
- 先隨機選取文章，再逐一檢查是否需要處理
- AI 處理是 I/O 密集型操作，可能需要較長時間
- 建議客戶端設定適當的 timeout（建議 60 秒以上）
- 若需要處理大量文章，考慮分批請求

### 錯誤處理
- AI 配額不足：返回 429 狀態碼
- 文章內容為空：跳過該文章，記錄警告
- AI 處理失敗：跳過該文章，記錄錯誤，繼續處理下一篇
- 所有文章都失敗：返回空陣列但不報錯

---

## 測試建議

### 測試測試端點隱藏功能
```bash
# 1. 啟動開發環境（預設）
./gradlew bootRun

# 檢查 Swagger - 應該看到測試端點
open http://localhost:8080/swagger-ui/index.html

# 2. 啟動生產環境
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun

# 檢查 Swagger - 不應該看到測試端點
open http://localhost:8080/swagger-ui/index.html

# 嘗試調用測試端點 - 應該返回 404
curl -X POST http://localhost:8080/api/ops/import-stats
```

### 測試新的 random-with-processing 端點
```bash
# 基本測試
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=3" | jq .

# 檢查回應中的 processedCount 欄位
# 第一次調用：processedCount 應該 > 0（新處理的文章）
# 第二次調用：processedCount 應該 = 0（已全部處理過）

# 測試過濾功能
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=5&section=news" | jq .

# 測試邊界條件
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=100" | jq .
# 應該最多返回 20 篇（有上限保護）
```

---

## 部署注意事項

### 環境變數設定

**生產環境必須設定**：
```bash
SPRING_PROFILES_ACTIVE=prod
```

這會確保：
- 測試端點被停用
- 測試端點在 Swagger 中隱藏
- 生產環境配置生效

### Zeabur 部署
在 Zeabur 環境變數中添加：
```
SPRING_PROFILES_ACTIVE=prod
```

### 驗證部署
```bash
# 檢查 Swagger 是否乾淨（無測試端點）
curl https://your-domain.zeabur.app/swagger-ui/index.html

# 確認測試端點無法訪問
curl -X POST https://your-domain.zeabur.app/api/ops/import-stats
# 應該返回 404

# 確認正常端點可用
curl https://your-domain.zeabur.app/api/articles?page=0&size=5

# 測試新端點
curl https://your-domain.zeabur.app/api/ai-articles/random-with-processing?count=3
```

---

## 相關文件

- `README.md` - 專案說明（已更新）
- `OpenApiConfiguration.kt` - Swagger 配置
- `AiArticleController.kt` - AI 文章控制器
- `AiArticleService.kt` - AI 文章服務邏輯

---

## 版本資訊

- 版本：1.1.0
- 更新日期：2025-01-30
- Spring Boot：3.3.4
- Kotlin：2.0.20
- OpenAPI：2.6.0
