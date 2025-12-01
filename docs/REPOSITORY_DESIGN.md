# Repository 設計說明

## 設計原則

### ✅ 正確的做法

1. **Repository 應該保持簡潔**：只包含基本的 CRUD 和查詢方法
2. **使用 Spring Data JPA 的命名約定**：讓 Spring 自動生成實作
3. **不在 Repository 中使用 @Query**：特殊查詢邏輯應該在 Service 層實現
4. **關注點分離**：Repository 只負責資料存取，業務邏輯在 Service 層

### ❌ 錯誤的做法

1. ~~在 Repository interface 中寫 @Query~~
2. ~~在 Repository 中實現業務邏輯~~
3. ~~創建不必要的查詢方法~~

## 當前設計

### ArticleAiRepository

```kotlin
interface ArticleAiRepository : JpaRepository<ArticleAi, Long> {
    /**
     * 根據文章 ID 查詢 AI 處理結果
     * 
     * article_id 在 article_ai 表中是唯一的（UNIQUE constraint）
     * 因此只會返回 0 或 1 筆記錄
     */
    fun findByArticle_Id(articleId: Long): Optional<ArticleAi>
    
    // ... 其他基本查詢方法
}
```

**為什麼只需要一個方法？**

- `article_id` 有 UNIQUE constraint，一個 article 只會有一筆 ArticleAi 記錄
- 不需要 `findAllByArticle_Id`，因為不會有多筆記錄
- 查詢特定難度的邏輯在 Service 層處理

### ArticleAiViewRepository（可選）

如果需要直接查詢特定難度的 view，可以使用專門的 Repository：

```kotlin
interface ArticleAiViewRepository : JpaRepository<ArticleAiView, Long> {
    /**
     * 查詢特定的 view
     * UNIQUE(article_ai_id, difficulty, from_lang, to_lang) 保證唯一性
     */
    fun findByArticleAi_IdAndDifficultyAndFromLanguageIgnoreCaseAndToLanguageIgnoreCase(
        articleAiId: Long,
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): Optional<ArticleAiView>
    
    /**
     * 檢查特定 view 是否存在
     */
    fun existsByArticleAi_IdAndDifficultyAndFromLanguageIgnoreCaseAndToLanguageIgnoreCase(
        articleAiId: Long,
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): Boolean
}
```

## 查詢流程

### 方案 1：當前實作（Service 層處理）

```kotlin
// Service 層
fun getOrProcessDto(articleId: Long, difficulty: DifficultyLevel, ...): AiArticleDetail {
    // 1. 查詢 ArticleAi
    val articleAi = articleAiRepository.findByArticle_Id(articleId).orElse(null)
    
    // 2. 在內存中檢查 views 是否包含該難度
    val difficultyExists = articleAi?.views?.any { 
        it.difficulty == difficulty && 
        it.fromLanguage == fromLang && 
        it.toLanguage == toLang 
    } ?: false
    
    // 3. 如果不存在，調用 AI 生成
    if (!difficultyExists) {
        processSpecificDifficulty(...)
    }
    
    return toDto(...)
}
```

**優點**：

- Repository 保持簡潔
- 業務邏輯集中在 Service
- 利用 JPA 的 lazy loading（views 只在需要時載入）

**缺點**：

- 需要載入 ArticleAi 及其 views
- 對於只是檢查存在性的場景，稍微效率低

### 方案 2：使用 ArticleAiViewRepository（更優化）

```kotlin
// Service 層
fun getOrProcessDto(articleId: Long, difficulty: DifficultyLevel, ...): AiArticleDetail {
    // 1. 先查詢 ArticleAi（不載入 views）
    val articleAi = articleAiRepository.findByArticle_Id(articleId).orElse(null)
    
    if (articleAi == null) {
        // 完全沒處理過，需要完整處理
        return processNewArticle(articleId, difficulty, ...)
    }
    
    // 2. 檢查特定 view 是否存在（只查詢 article_ai_view 表）
    val viewExists = articleAiViewRepository.existsByArticleAi_IdAndDifficultyAndFromLanguageIgnoreCaseAndToLanguageIgnoreCase(
        articleAi.id!!,
        difficulty,
        fromLang,
        toLang
    )
    
    // 3. 如果不存在，調用 AI 生成
    if (!viewExists) {
        processSpecificDifficulty(articleAi.article, difficulty, ...)
    }
    
    return toDto(...)
}
```

**優點**：

- 更高效：直接查詢 article_ai_view 表
- 不需要載入所有 views
- 適合檢查存在性的場景

**缺點**：

- 需要額外的 Repository
- 稍微複雜一點

## SQL 查詢對照

### 當前實作（方案 1）

```sql
-- 1. 查詢 ArticleAi（會同時載入 views，因為 @OneToMany(fetch = EAGER)）
SELECT ai.*, v.* 
FROM article_ai ai
LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id
WHERE ai.article_id = ?

-- 2. 在 Java/Kotlin 內存中過濾 views
```

### 優化實作（方案 2）

```sql
-- 1. 只查詢 ArticleAi（不載入 views）
SELECT * FROM article_ai WHERE article_id = ?

-- 2. 檢查特定 view 是否存在
SELECT EXISTS(
    SELECT 1 
    FROM article_ai_view 
    WHERE article_ai_id = ? 
      AND difficulty = ? 
      AND from_lang = ? 
      AND to_lang = ?
)
```

## 建議

### 目前階段

使用**方案 1**即可，因為：

- 程式碼簡潔易懂
- ArticleAi 的 views 通常不會很多（最多 5 個難度）
- 效能差異不明顯

### 未來優化

如果遇到效能問題，可以考慮：

1. 使用 ArticleAiViewRepository（方案 2）
2. 將 `@OneToMany(fetch = EAGER)` 改為 `LAZY`
3. 添加適當的快取

## 資料庫約束

### article_ai 表

```sql
-- 唯一約束：一個 article 只能有一筆 ArticleAi
CONSTRAINT uk_article_ai_article UNIQUE (article_id)
```

### article_ai_view 表

```sql
-- 唯一約束：同一個 ArticleAi 的同一難度+語言組合只能有一筆
CONSTRAINT uk_article_ai_view_article_lang_difficulty 
    UNIQUE (article_ai_id, from_lang, to_lang, difficulty)
```

這些約束保證了資料的一致性，也讓我們可以安全地使用 `Optional<T>` 而不是 `List<T>`。
