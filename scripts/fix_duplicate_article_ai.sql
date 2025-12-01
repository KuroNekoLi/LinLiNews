-- ================================================
-- 修復 article_ai 表中的重複記錄
-- ================================================
-- 此腳本會合併同一個 article_id 的多筆 ArticleAi 記錄
-- 保留最舊的記錄（id 最小），並將所有 views 合併到該記錄中
-- ================================================

-- 1. 檢查重複記錄
SELECT 
    article_id, 
    COUNT(*) as count
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 2. 對於每個有重複的 article_id，執行以下操作：
-- (需要根據實際情況逐一處理)

-- 範例：假設 article_id = 123 有兩筆記錄 (id=1 和 id=2)
-- 
-- a) 將 id=2 的所有 views 更新為指向 id=1
-- UPDATE article_ai_view 
-- SET article_ai_id = 1 
-- WHERE article_ai_id = 2;
--
-- b) 刪除 id=2 的 ArticleAi 記錄
-- DELETE FROM article_ai WHERE id = 2;

-- 3. 自動化腳本（適用於 MySQL）
-- 為每個重複的 article_id 保留最小的 id

-- 創建臨時表存儲要刪除的記錄
CREATE TEMPORARY TABLE IF NOT EXISTS article_ai_to_delete AS
SELECT 
    ai.id as duplicate_id,
    ai.article_id,
    (SELECT MIN(id) FROM article_ai WHERE article_id = ai.article_id) as keep_id
FROM article_ai ai
WHERE ai.id NOT IN (
    SELECT MIN(id) 
    FROM article_ai 
    GROUP BY article_id
);

-- 檢視將要處理的記錄
SELECT * FROM article_ai_to_delete;

-- 更新所有 views 指向要保留的記錄
UPDATE article_ai_view aiv
INNER JOIN article_ai_to_delete d ON aiv.article_ai_id = d.duplicate_id
SET aiv.article_ai_id = d.keep_id;

-- 刪除重複的 ArticleAi 記錄
DELETE ai FROM article_ai ai
INNER JOIN article_ai_to_delete d ON ai.id = d.duplicate_id;

-- 清理臨時表
DROP TEMPORARY TABLE IF EXISTS article_ai_to_delete;

-- 4. 驗證結果
SELECT 
    article_id, 
    COUNT(*) as count
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 5. 確保唯一約束存在
ALTER TABLE article_ai 
ADD CONSTRAINT uk_article_ai_article UNIQUE (article_id);
