# 立即修復重複記錄問題

## ⚠️ 當前狀況

您的資料庫中存在重複的 `ArticleAi` 記錄，導致應用程式拋出錯誤：

```
Query did not return a unique result: 2 results were returned
```

## ✅ 已完成的程式碼修復

程式碼已經修改完成，現在可以處理重複記錄：

1. ✅ 添加了 `findAllByArticle_Id()` 方法到 Repository
2. ✅ 修改了 `saveAiResult()` 使用新方法
3. ✅ 修改了 `findExistingAiArticle()` 可以處理重複記錄

**但是**，資料庫中仍然存在重複記錄需要清理。

## 🚀 立即執行修復步驟

### 方法 1：快速修復（推薦）

這個方法會自動合併所有重複記錄：

```bash
# 連接到資料庫
mysql -u your_username -p your_database_name

# 執行快速修復腳本
source scripts/quick_fix_duplicates.sql
```

或者直接執行：

```bash
mysql -u your_username -p your_database_name < scripts/quick_fix_duplicates.sql
```

**這個腳本會做什麼**：

1. 找出所有重複的 `ArticleAi` 記錄
2. 對每個重複的 `article_id`，保留 `id` 最小的記錄
3. 將其他記錄的所有 `article_ai_view` 移到保留的記錄上
4. 刪除衝突的 views（相同難度+語言的重複）
5. 刪除重複的 `ArticleAi` 記錄
6. 顯示修復結果

### 方法 2：手動逐一修復（謹慎操作）

如果您想更謹慎地處理，可以手動修復每個重複記錄：

#### 步驟 1：查看重複記錄

```sql
SELECT 
    article_id, 
    COUNT(*) as count,
    GROUP_CONCAT(id ORDER BY id) as article_ai_ids
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;
```

#### 步驟 2：對每個重複的 article_id 執行合併

假設 `article_id = 14` 有兩筆記錄 (id=10 和 id=11)：

```sql
-- 查看兩筆記錄的 views
SELECT 
    ai.id as article_ai_id,
    ai.article_id,
    v.id as view_id,
    v.difficulty,
    v.from_lang,
    v.to_lang
FROM article_ai ai
LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id
WHERE ai.article_id = 14
ORDER BY ai.id, v.difficulty;

-- 將 id=11 的 views 移到 id=10
UPDATE article_ai_view 
SET article_ai_id = 10 
WHERE article_ai_id = 11
  AND (difficulty, from_lang, to_lang) NOT IN (
      SELECT difficulty, from_lang, to_lang 
      FROM article_ai_view 
      WHERE article_ai_id = 10
  );

-- 刪除無法移動的衝突 views
DELETE FROM article_ai_view 
WHERE article_ai_id = 11;

-- 刪除 id=11 的 ArticleAi 記錄
DELETE FROM article_ai WHERE id = 11;

-- 驗證
SELECT * FROM article_ai WHERE article_id = 14;
```

### 方法 3：備份後全面修復

如果資料很重要，建議先備份：

```bash
# 1. 備份資料庫
mysqldump -u your_username -p your_database_name > backup_before_fix_$(date +%Y%m%d_%H%M%S).sql

# 2. 執行修復
mysql -u your_username -p your_database_name < scripts/quick_fix_duplicates.sql

# 3. 驗證結果（應該返回 0）
mysql -u your_username -p your_database_name -e "
SELECT COUNT(*) as duplicate_count
FROM (
    SELECT article_id
    FROM article_ai
    GROUP BY article_id
    HAVING COUNT(*) > 1
) as duplicates;
"
```

## 🔍 修復後驗證

執行以下 SQL 確認沒有重複記錄：

```sql
-- 1. 檢查重複記錄（應該返回空結果）
SELECT 
    article_id, 
    COUNT(*) as count
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 2. 檢查每篇文章的 views 數量
SELECT 
    ai.article_id,
    COUNT(DISTINCT v.id) as total_views,
    COUNT(DISTINCT v.difficulty) as difficulty_count,
    GROUP_CONCAT(DISTINCT v.difficulty ORDER BY v.difficulty) as difficulties
FROM article_ai ai
LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id
GROUP BY ai.article_id
ORDER BY difficulty_count DESC
LIMIT 20;

-- 3. 統計每個難度的文章數
SELECT 
    v.difficulty,
    COUNT(DISTINCT ai.article_id) as article_count
FROM article_ai ai
INNER JOIN article_ai_view v ON ai.id = v.article_ai_id
GROUP BY v.difficulty
ORDER BY 
    CASE v.difficulty
        WHEN 'VERY_EASY' THEN 1
        WHEN 'EASY' THEN 2
        WHEN 'NORMAL' THEN 3
        WHEN 'HARD' THEN 4
        WHEN 'VERY_HARD' THEN 5
    END;
```

## 🎯 修復後測試

### 1. 重啟應用程式

```bash
# 停止應用
# (根據您的部署方式)

# 重啟應用
./gradlew bootRun
```

### 2. 測試 API

```bash
# 測試之前出錯的文章（例如 article_id=14）
curl -X GET "http://localhost:8080/api/ai-articles/14?difficulty=NORMAL" | jq '.availableDifficulties'

# 應該成功返回，不再拋出異常
```

### 3. 測試新增不同難度

```bash
# 為同一篇文章請求不同難度
curl -X GET "http://localhost:8080/api/ai-articles/14?difficulty=EASY" | jq '.availableDifficulties'
curl -X GET "http://localhost:8080/api/ai-articles/14?difficulty=HARD" | jq '.availableDifficulties'

# 應該看到 availableDifficulties 列表增加
```

## 🛡️ 防止再次發生

### 1. 確保資料庫約束存在

```sql
-- 檢查唯一約束
SELECT 
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'article_ai'
  AND TABLE_SCHEMA = DATABASE()
  AND CONSTRAINT_TYPE = 'UNIQUE';

-- 如果沒有，添加約束
ALTER TABLE article_ai 
ADD CONSTRAINT uk_article_ai_article UNIQUE (article_id);
```

### 2. 監控日誌

關注應用程式日誌中的警告：

```
[ERROR] Multiple ArticleAi records found for article X: Y records exist
```

如果看到這個警告，立即執行修復腳本。

### 3. 定期檢查

建立定期任務檢查重複記錄：

```bash
# 添加到 cron (每天檢查一次)
0 2 * * * mysql -u user -ppass database -e "SELECT COUNT(*) FROM (SELECT article_id FROM article_ai GROUP BY article_id HAVING COUNT(*) > 1) as d" | mail -s "Duplicate Check" admin@example.com
```

## 📊 預期結果

修復後：

✅ 每個 `article_id` 只有一筆 `ArticleAi` 記錄  
✅ 所有不同難度的 views 都保留在同一個 `ArticleAi` 下  
✅ API 可以正常查詢不同難度而不拋出異常  
✅ 新的請求不會創建重複記錄

## ❓ 常見問題

### Q: 修復會丟失資料嗎？

A: 不會。腳本會：

- 保留所有可以合併的 views
- 只刪除完全重複的 views（相同難度+語言）
- 保留最早創建的 `ArticleAi` 記錄

### Q: 如果修復出錯怎麼辦？

A:

1. 如果使用了事務腳本，可以執行 `ROLLBACK;`
2. 如果已經提交，從備份恢復：
   ```bash
   mysql -u username -p database < backup_file.sql
   ```

### Q: 為什麼會產生重複記錄？

A: 之前的程式碼在並發情況下會先嘗試插入新記錄，如果兩個請求同時處理同一篇文章的不同難度，就可能創建重複記錄。現在已經修復。

### Q: 修復後還會出現重複嗎？

A: 不會。修改後的程式碼總是先查詢再更新，並且使用了資料庫層面的唯一約束，可以防止再次創建重複記錄。

## 📞 需要幫助？

如果遇到問題：

1. 檢查錯誤日誌
2. 查看 `scripts/verify_multiple_difficulties.sql` 的輸出
3. 確保資料庫備份存在
4. 如有疑問，先在測試環境執行

## 📝 執行清單

- [ ] 備份資料庫
- [ ] 執行快速修復腳本
- [ ] 驗證沒有重複記錄
- [ ] 重啟應用程式
- [ ] 測試 API 功能
- [ ] 確認唯一約束存在
- [ ] 設置監控告警
