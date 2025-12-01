# 補充缺失的 Tags 和精選專題

## 概述

`POST /api/ai-articles/backfill-tags-and-collections` 端點用於檢查所有 AI 文章，並為缺失 tags 和精選專題的文章重新生成這些資訊。

## 使用場景

1. **初次部署**：現有的 AI 文章沒有 tags 和精選專題
2. **資料遷移**：從舊版本升級後需要補充新欄位
3. **修正錯誤**：某些文章的 tags/collections 配對失敗需要重新生成
4. **更新邏輯**：改進了 prompt 或候選清單後，想更新現有文章的配對

## API 規格

### 端點

```
POST /api/ai-articles/backfill-tags-and-collections
```

### 參數

| 參數 | 類型 | 預設值 | 說明 |
|------|------|--------|------|
| `force` | boolean | false | 是否強制重新生成（即使已有 tags/collections） |
| `limit` | int | 0 | 處理文章數量限制（0 表示處理全部） |

### 回應格式

```json
{
  "totalChecked": 71,
  "needsBackfill": 50,
  "successCount": 48,
  "failedCount": 2,
  "skippedCount": 21,
  "details": [
    {
      "articleId": 62,
      "title": "Twitter hack: Cyber-attack hits accounts...",
      "status": "success",
      "tagsAssigned": 3,
      "collectionsAssigned": 1,
      "errorMessage": null
    },
    {
      "articleId": 14,
      "title": "...",
      "status": "skipped",
      "tagsAssigned": 2,
      "collectionsAssigned": 1,
      "errorMessage": null
    },
    {
      "articleId": 99,
      "title": "...",
      "status": "failed",
      "tagsAssigned": 0,
      "collectionsAssigned": 0,
      "errorMessage": "AI quota exceeded"
    }
  ]
}
```

### 狀態說明

- **success**：成功補充 tags 和 collections
- **skipped**：跳過（已有 tags/collections 且非 force 模式，或缺少必要資料）
- **failed**：處理失敗（AI API 錯誤、網路問題等）

## 使用範例

### 1. 補充缺失的 tags/collections（預設）

```bash
curl -X POST "http://localhost:8080/api/ai-articles/backfill-tags-and-collections"
```

- 只處理「tags 和 collections 都為空」的文章
- 已有任一欄位的文章會被跳過
- 處理所有符合條件的文章

### 2. 強制重新生成全部文章

```bash
curl -X POST "http://localhost:8080/api/ai-articles/backfill-tags-and-collections?force=true"
```

- 忽略現有的 tags/collections
- 為所有 AI 文章重新生成並覆寫

### 3. 限制處理數量（測試用）

```bash
curl -X POST "http://localhost:8080/api/ai-articles/backfill-tags-and-collections?limit=10"
```

- 只處理前 10 篇需要補充的文章
- 適合測試或分批執行

### 4. 強制重新生成前 5 篇

```bash
curl -X POST "http://localhost:8080/api/ai-articles/backfill-tags-and-collections?force=true&limit=5"
```

## 處理流程

1. **取得所有 AI 文章**：從 `article_ai` 表查詢
2. **檢查每篇文章**：
    - 查詢是否有 tags（`article_tag` 表）
    - 查詢是否有 collections（`article_featured_collection_assignment` 表）
3. **判斷是否需要處理**：
    - `force=false`：只處理「tags 和 collections 都為空」的文章
    - `force=true`：處理所有文章
4. **調用 AI 生成**：
    - 使用文章的 `cleanedTextFrom` 和其他資訊
    - 呼叫 `AiProcessor.teachingStage2` 生成 tags 和 collections
5. **配對並儲存**：
    - 從 AI 回應的 `tags` 陣列中提取 tag 名稱
    - 從 AI 回應的 `featured_collections` 陣列中提取專題 ID
    - 呼叫 `ArticleTagService.assignTagsByName` 配對 tags
    - 呼叫 `FeaturedCollectionService.assignCollectionsById` 配對專題

## 注意事項

### 1. API 配額

- 每篇文章需要呼叫一次 OpenAI API（`teachingStage2`）
- 如果文章數量很多，建議分批執行（使用 `limit` 參數）
- 注意 OpenAI API 的速率限制和配額

### 2. 執行時間

- 每篇文章約需 3-10 秒（取決於 AI 回應時間）
- 71 篇文章可能需要 3-10 分鐘
- 建議在低峰時段執行

### 3. 錯誤處理

- 單篇文章失敗不會中斷整個流程
- 失敗的文章會在 `details` 中標記為 `failed`
- 可以查看 `errorMessage` 了解失敗原因

### 4. 資料完整性

- 文章必須有 `cleanedTextFrom` 才能處理
- 文章必須至少有一個 `ArticleAiView`
- 缺少這些資料的文章會被跳過

## 監控與日誌

查看 application log 可以看到詳細的處理過程：

```
2025-12-01 16:00:00 INFO  AiArticleService - Starting backfill tags and collections (force=false, limit=0)
2025-12-01 16:00:01 INFO  AiArticleService - Backfilling tags and collections for article 62
2025-12-01 16:00:05 INFO  AiArticleService - Assigned 3 tags to article 62
2025-12-01 16:00:05 INFO  AiArticleService - Assigned 1 collections to article 62
2025-12-01 16:10:00 INFO  AiArticleService - Backfill completed in 600000ms: checked=71, needs=50, success=48, failed=2, skipped=21
```

## 後續驗證

執行完成後，可以驗證結果：

### 1. 檢查資料庫

```sql
-- 檢查有 tags 的文章數量
SELECT COUNT(DISTINCT article_id) FROM article_tag;

-- 檢查有 collections 的文章數量
SELECT COUNT(DISTINCT article_id) FROM article_featured_collection_assignment;

-- 檢查特定文章
SELECT at.article_id, atd.name, at.score
FROM article_tag at
JOIN article_tag_definition atd ON at.tag_id = atd.id
WHERE at.article_id = 62;
```

### 2. 查詢 API

```bash
curl "http://localhost:8080/api/ai-articles/62" | jq '.tags, .featuredCollections'
```

應該能看到 `tags` 和 `featuredCollections` 陣列有內容。

## 常見問題

### Q: 為什麼某些文章被跳過？

A: 可能的原因：

- 已有 tags 或 collections（`force=false` 模式）
- 文章沒有 `ArticleAiView`
- 文章的 `cleanedTextFrom` 為空

### Q: 可以只補充 tags 或只補充 collections 嗎？

A: 目前的實作是同時生成兩者。如果只想補充其中一項，建議：

- 使用 `force=false` 模式（已有任一項就跳過）
- 或者修改程式碼增加更細緻的控制

### Q: 補充後的 score 為何都是 1.0？

A: AI 直接判斷的 tags/collections 被視為「100% 相關」，因此 score 設為 1.0（滿分）。這與向量相似度計算的 score（0-1 之間的浮點數）不同。

### Q: 執行失敗時資料會不會損壞？

A: 不會。每篇文章的處理都是獨立的事務，失敗的文章不會影響其他文章。已成功處理的文章資料會保留。

## 相關文件

- [AI Tag & Collection Assignment](./AI_TAG_COLLECTION_ASSIGNMENT.md)：了解 tags 和精選專題的自動配對機制
