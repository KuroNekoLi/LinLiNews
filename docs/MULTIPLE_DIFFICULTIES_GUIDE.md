# 多難度支援使用指南

## ✅ 修復後的功能

修復後，系統完全支援**同一篇文章存儲多個難度**，每個難度的內容都是獨立生成的。

## 📊 資料結構

### 正確的資料結構（一對多關係）

```
article_ai (id=1, article_id=123)
├── article_ai_view (id=1, difficulty=VERY_EASY, from_lang=en, to_lang=zh-TW)
│   ├── vocabularyItems: [5 個簡單單字]
│   ├── grammarItems: [3 個基礎文法]
│   └── cleanedTextFrom: "簡化的英文內容..."
│
├── article_ai_view (id=2, difficulty=EASY, from_lang=en, to_lang=zh-TW)
│   ├── vocabularyItems: [8 個中階單字]
│   ├── grammarItems: [4 個標準文法]
│   └── cleanedTextFrom: "中階難度的英文內容..."
│
├── article_ai_view (id=3, difficulty=NORMAL, from_lang=en, to_lang=zh-TW)
│   ├── vocabularyItems: [10 個中高階單字]
│   ├── grammarItems: [5 個進階文法]
│   └── cleanedTextFrom: "標準難度的英文內容..."
│
└── article_ai_view (id=4, difficulty=HARD, from_lang=en, to_lang=zh-TW)
    ├── vocabularyItems: [15 個高階單字]
    ├── grammarItems: [8 個複雜文法]
    └── cleanedTextFrom: "高階難度的英文內容..."
```

### 資料庫約束

- **ArticleAi**: 每個 `article_id` **只能有一筆記錄**（唯一約束）
- **ArticleAiView**: 每個 `(article_ai_id, difficulty, from_lang, to_lang)` 組合只能有一筆記錄（唯一約束）

## 🔄 使用流程

### 場景 1：首次處理文章

```bash
# 第一次請求：生成 VERY_EASY 難度
GET /api/ai-articles/random-with-processing?difficulty=VERY_EASY

# 回應
{
  "articles": [{
    "id": 123,
    "requestedDifficulties": ["VERY_EASY"],
    "availableDifficulties": ["VERY_EASY"],  // 目前只有這一個難度
    "content": {
      "difficulty": "VERY_EASY",
      "explanation": { /* 簡單的教學內容 */ }
    }
  }]
}
```

**資料庫狀態**：

```
article_ai (1 筆)
└── article_ai_view (1 筆，VERY_EASY)
```

### 場景 2：為同一篇文章新增不同難度

```bash
# 第二次請求：為同一篇文章新增 NORMAL 難度
GET /api/ai-articles/123?difficulty=NORMAL

# 回應
{
  "id": 123,
  "requestedDifficulties": ["NORMAL"],
  "availableDifficulties": ["VERY_EASY", "NORMAL"],  // 現在有兩個難度！
  "content": {
    "difficulty": "NORMAL",
    "explanation": { /* 中階的教學內容 */ }
  }
}
```

**資料庫狀態**：

```
article_ai (還是 1 筆)
├── article_ai_view (VERY_EASY) ← 保持不變
└── article_ai_view (NORMAL)    ← 新增
```

### 場景 3：查詢已存在的難度

```bash
# 第三次請求：再次查詢 VERY_EASY（不會重新生成）
GET /api/ai-articles/123?difficulty=VERY_EASY

# 直接從資料庫讀取，不調用 AI
```

## 🎯 API 行為

### `GET /api/ai-articles/{articleId}?difficulty={level}`

| 情況 | 行為 |
|------|------|
| 文章從未處理過 | 調用 AI 生成指定難度的內容，創建 ArticleAi + ArticleAiView |
| 文章已處理過，但沒有請求的難度 | 調用 AI 生成新難度的內容，在現有 ArticleAi 上新增 ArticleAiView |
| 文章已處理過，且有請求的難度 | 直接從資料庫讀取，不調用 AI |

### `GET /api/ai-articles/random-with-processing?difficulty={level}`

| 情況 | 行為 |
|------|------|
| 隨機選中未處理的文章 | 調用 AI 生成指定難度的內容 |
| 隨機選中已處理的文章 | 直接使用現有的 AI 內容，不重新處理 |

## 🧪 測試步驟

### 方法 1：使用測試腳本

```bash
# 執行自動化測試
./scripts/test_multiple_difficulties.sh
```

這個腳本會：

1. 生成一篇 VERY_EASY 文章
2. 依序為同一篇文章請求 EASY、NORMAL、HARD 難度
3. 驗證所有難度都能成功生成
4. 檢查 `availableDifficulties` 包含所有已生成的難度

### 方法 2：手動測試

#### 步驟 1：生成初始文章

```bash
curl -X GET "http://localhost:8080/api/ai-articles/random-with-processing?count=1&difficulty=VERY_EASY"
```

記下返回的 `article.id`，假設為 `123`。

#### 步驟 2：請求不同難度

```bash
# EASY 難度
curl -X GET "http://localhost:8080/api/ai-articles/123?difficulty=EASY" | jq '.availableDifficulties'
# 預期輸出: ["VERY_EASY", "EASY"]

# NORMAL 難度
curl -X GET "http://localhost:8080/api/ai-articles/123?difficulty=NORMAL" | jq '.availableDifficulties'
# 預期輸出: ["VERY_EASY", "EASY", "NORMAL"]

# HARD 難度
curl -X GET "http://localhost:8080/api/ai-articles/123?difficulty=HARD" | jq '.availableDifficulties'
# 預期輸出: ["VERY_EASY", "EASY", "NORMAL", "HARD"]
```

#### 步驟 3：驗證資料庫

```sql
-- 檢查該文章只有一筆 ArticleAi 記錄
SELECT COUNT(*) FROM article_ai WHERE article_id = 123;
-- 預期: 1

-- 檢查有多少個 views
SELECT 
    v.difficulty,
    LENGTH(v.optimized_html_from) as content_length,
    (SELECT COUNT(*) FROM article_ai_vocabulary_item WHERE article_ai_view_id = v.id) as vocab_count
FROM article_ai ai
INNER JOIN article_ai_view v ON ai.id = v.article_ai_id
WHERE ai.article_id = 123
ORDER BY 
    CASE v.difficulty
        WHEN 'VERY_EASY' THEN 1
        WHEN 'EASY' THEN 2
        WHEN 'NORMAL' THEN 3
        WHEN 'HARD' THEN 4
    END;
```

預期結果：

```
difficulty   | content_length | vocab_count
-------------|----------------|-------------
VERY_EASY    | 2500          | 5
EASY         | 3200          | 8
NORMAL       | 4100          | 10
HARD         | 5800          | 15
```

## 🔍 常見問題

### Q1: 不同難度的內容有什麼不同？

**A**: AI 會根據難度調整：

- **文章長度**：VERY_EASY 最短，VERY_HARD 最長
- **句型複雜度**：從簡單句到複雜句
- **詞彙難度**：從日常詞彙到專業術語
- **教學深度**：
    - VERY_EASY: 5-8 個單字，3-5 個文法點
    - EASY: 8-12 個單字，5-7 個文法點
    - NORMAL: 10-15 個單字，7-10 個文法點
    - HARD: 15-20 個單字，10-15 個文法點

### Q2: 可以覆蓋已存在的難度嗎？

**A**: 目前的設計是**保留現有內容**。如果需要重新生成，可以使用：

```bash
# 使用 processAndOverwrite 端點（僅限測試環境）
POST /api/ai-articles/123/reprocess-with-debug?difficulty=NORMAL
```

或者手動刪除資料庫中的 view：

```sql
-- 刪除特定難度的 view
DELETE FROM article_ai_view 
WHERE article_ai_id = (SELECT id FROM article_ai WHERE article_id = 123)
  AND difficulty = 'NORMAL';
```

然後重新請求該難度。

### Q3: 為什麼第二次請求同一個難度很快？

**A**: 因為系統會檢查資料庫中是否已有該難度的內容，如果有就直接返回，不會重新調用 AI。這樣可以：

- 節省 AI API 配額
- 加快響應速度
- 保持內容一致性

### Q4: 可以一次請求多個難度嗎？

**A**: 目前 API 支援傳遞多個 `difficulty` 參數，但只會生成第一個缺失的難度。例如：

```bash
GET /api/ai-articles/123?difficulty=EASY&difficulty=NORMAL&difficulty=HARD
```

系統會：

1. 檢查 EASY 是否存在，如果不存在就生成
2. 返回結果
3. 下次請求時再生成 NORMAL 和 HARD

如果需要一次生成多個難度，建議使用循環：

```bash
for difficulty in EASY NORMAL HARD; do
  curl -X GET "http://localhost:8080/api/ai-articles/123?difficulty=$difficulty"
done
```

## 📈 監控和維護

### 檢查資料完整性

定期運行：

```bash
mysql -u username -p database_name < scripts/verify_multiple_difficulties.sql
```

確保：

- 沒有重複的 ArticleAi 記錄
- 每個 view 都有完整的內容（optimizedHtml、explanation 等）
- 唯一約束正確設置

### 日誌監控

關注以下日誌：

```
# 成功生成新難度
[INFO] Requested difficulty NORMAL not found for article 123, generating new view
[INFO] Save successful for article ID 123 in 2341ms (existing=true, views=2)

# 警告：發現重複記錄
[ERROR] Found 2 duplicate ArticleAi records for article 123, using the first one
```

如果看到重複記錄警告，執行清理腳本：

```bash
mysql -u username -p database_name < scripts/fix_duplicate_article_ai.sql
```

## 🎓 最佳實踐

1. **按需生成**：只為用戶實際請求的難度生成內容，避免浪費 AI 配額
2. **快取策略**：已生成的難度會永久保存，無需擔心重複生成
3. **難度選擇**：
    - 初學者：VERY_EASY (A2)
    - 中階學習者：EASY (B1) 或 NORMAL (B2)
    - 高階學習者：HARD (C1) 或 VERY_HARD (C2)
4. **內容一致性**：同一篇文章的不同難度內容都來自同一原始文章，只是表達方式不同

## 📚 相關文件

- [重複記錄修復指南](DUPLICATE_ARTICLE_AI_FIX.md)
- 測試腳本：`scripts/test_multiple_difficulties.sh`
- 驗證腳本：`scripts/verify_multiple_difficulties.sql`
- 修復腳本：`scripts/fix_duplicate_article_ai.sql`
