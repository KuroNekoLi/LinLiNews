# ArticleAi 重複記錄問題修復指南

## 問題描述

在使用 `/api/ai-articles/random-with-processing` 和 `/api/ai-articles/{articleId}` API 時，發現以下錯誤：

```
Query did not return a unique result: 2 results were returned
at com.linli.news.service.AiArticleService.findExistingAiArticle
```

### 根本原因

1. **資料庫層面**：同一個 `article_id` 在 `article_ai` 表中存在多筆記錄
2. **應用層面**：並發情況下，`saveAiResult` 方法的「先嘗試插入，失敗後再查詢更新」邏輯導致條件競爭
3. **觸發條件**：當用戶對同一篇文章請求不同難度時，如果兩個請求幾乎同時到達，可能創建重複記錄

### 資料結構說明

正確的資料結構應該是：

```
article_ai (一篇文章只有一筆記錄)
├── article_ai_view (difficulty=VERY_EASY, from=en, to=zh-TW)
├── article_ai_view (difficulty=NORMAL, from=en, to=zh-TW)
└── article_ai_view (difficulty=HARD, from=en, to=zh-TW)
```

錯誤的狀況（需要修復）：

```
article_ai (id=1, article_id=123)
└── article_ai_view (difficulty=VERY_EASY)

article_ai (id=2, article_id=123)  ← 重複！
└── article_ai_view (difficulty=NORMAL)
```

## 解決方案

### 1. 應用程式修復（已完成）

#### 修改 `saveAiResult` 方法

**原邏輯（有問題）**：

```kotlin
// 先嘗試插入新記錄
val entity = ArticleAi(article = article).applyFrom(...)
return articleAiRepository.save(entity)
// 失敗後才查詢現有記錄
```

**新邏輯（已修復）**：

```kotlin
// 總是先查詢，避免創建重複記錄
val existingList = articleAiRepository.findByArticle_Id(articleId)
val entity = if (existingList.isNotEmpty()) {
    existingList.first()
} else {
    ArticleAi(article = article)
}
entity.applyFrom(...)
return articleAiRepository.save(entity)
```

#### 修改 `findExistingAiArticle` 方法

增加錯誤處理，當遇到重複記錄時返回第一筆：

```kotlin
@Transactional(readOnly = true)
fun findExistingAiArticle(articleId: Long): ArticleAi? {
    return try {
        articleAiRepository.findByArticle_Id(articleId).orElse(null)
    } catch (e: Exception) {
        logger.error("Multiple ArticleAi records found for article {}", articleId, e)
        articleAiRepository.findAll().firstOrNull { it.article.id == articleId }
    }
}
```

### 2. 資料庫清理（需執行）

使用提供的 SQL 腳本修復現有的重複記錄：

```bash
# 執行清理腳本
mysql -u your_username -p your_database < scripts/fix_duplicate_article_ai.sql
```

**腳本做的事情**：

1. 識別所有重複的 `article_id`
2. 對每個重複的 `article_id`，保留 `id` 最小的記錄
3. 將其他記錄的所有 `article_ai_view` 重新指向保留的記錄
4. 刪除重複的 `ArticleAi` 記錄
5. 驗證唯一約束已正確設置

### 3. 驗證修復

執行以下 SQL 確認沒有重複記錄：

```sql
SELECT 
    article_id, 
    COUNT(*) as count,
    GROUP_CONCAT(id) as ids
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;
```

**預期結果**：應該返回空結果集。

## 預防措施

### 資料庫約束

確保 `article_ai` 表有唯一約束：

```sql
ALTER TABLE article_ai 
ADD CONSTRAINT uk_article_ai_article UNIQUE (article_id);
```

這個約束已在 Entity 定義中聲明：

```kotlin
@Table(
    name = "article_ai",
    uniqueConstraints = [UniqueConstraint(name = "uk_article_ai_article", columnNames = ["article_id"])]
)
```

但需要確保資料庫中實際創建了這個約束。

### 應用層鎖定

`getOrProcessThreadSafe` 方法使用 `ConcurrentHashMap` 的鎖來防止同一篇文章的並發處理：

```kotlin
private val locks = ConcurrentHashMap<Long, Any>()

fun getOrProcessThreadSafe(articleId: Long): ArticleAi {
    val lock = locks.computeIfAbsent(articleId) { Any() }
    synchronized(lock) {
        // ... 處理邏輯
    }
}
```

## 測試

### 測試場景 1：正常流程

```bash
# 1. 請求 VERY_EASY 難度
curl "http://localhost:8080/api/ai-articles/random-with-processing?difficulty=VERY_EASY"

# 2. 請求 NORMAL 難度（應該在現有記錄上新增 view）
curl "http://localhost:8080/api/ai-articles/123?difficulty=NORMAL"

# 3. 驗證只有一筆 ArticleAi 記錄，但有兩個 views
```

### 測試場景 2：並發請求

```bash
# 使用並發工具（如 Apache Bench）測試
ab -n 10 -c 5 "http://localhost:8080/api/ai-articles/123?difficulty=NORMAL"
ab -n 10 -c 5 "http://localhost:8080/api/ai-articles/123?difficulty=HARD"

# 驗證沒有創建重複記錄
```

## 監控

建議添加監控告警，當檢測到重複記錄時發出警告：

```sql
-- 定期執行這個查詢
SELECT COUNT(*) as duplicate_count
FROM (
    SELECT article_id
    FROM article_ai
    GROUP BY article_id
    HAVING COUNT(*) > 1
) as duplicates;
```

如果 `duplicate_count > 0`，觸發告警。

## 回滾計畫

如果修復後出現問題，可以：

1. 回滾到之前的程式碼版本
2. 從資料庫備份恢復（確保定期備份）
3. 手動檢查並修復資料一致性

## 相關文件

- `src/main/kotlin/com/linli/news/service/AiArticleService.kt`
- `src/main/kotlin/com/linli/news/domain/ArticleAi.kt`
- `src/main/kotlin/com/linli/news/domain/ArticleAiView.kt`
- `scripts/fix_duplicate_article_ai.sql`
