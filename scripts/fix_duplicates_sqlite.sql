-- ================================================
-- SQLite 版本：修復重複的 ArticleAi 記錄
-- ================================================

-- 1. 檢查重複記錄
SELECT '==== 檢查重複記錄 ====' as step;
SELECT 
    article_id, 
    COUNT(*) as count,
    GROUP_CONCAT(id) as ids
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 2. 對每個重複的 article_id，將 views 移到保留的記錄
UPDATE article_ai_view 
SET article_ai_id = (
    SELECT MIN(id) 
    FROM article_ai 
    WHERE article_id = (
        SELECT article_id 
        FROM article_ai 
        WHERE id = article_ai_view.article_ai_id
    )
)
WHERE article_ai_id IN (
    SELECT ai.id
    FROM article_ai ai
    WHERE ai.id NOT IN (
        SELECT MIN(id) 
        FROM article_ai 
        GROUP BY article_id
    )
)
AND NOT EXISTS (
    -- 避免創建重複的 view
    SELECT 1 
    FROM article_ai_view existing
    WHERE existing.article_ai_id = (
        SELECT MIN(id) 
        FROM article_ai 
        WHERE article_id = (
            SELECT article_id 
            FROM article_ai 
            WHERE id = article_ai_view.article_ai_id
        )
    )
    AND existing.difficulty = article_ai_view.difficulty
    AND existing.from_lang = article_ai_view.from_lang
    AND existing.to_lang = article_ai_view.to_lang
    AND existing.id != article_ai_view.id
);

SELECT '已移動 ' || changes() || ' 個 views' as status;

-- 3. 刪除衝突的 views
DELETE FROM article_ai_view 
WHERE article_ai_id IN (
    SELECT ai.id
    FROM article_ai ai
    WHERE ai.id NOT IN (
        SELECT MIN(id) 
        FROM article_ai 
        GROUP BY article_id
    )
);

SELECT '已刪除 ' || changes() || ' 個衝突 views' as status;

-- 4. 刪除重複的 ArticleAi 記錄
DELETE FROM article_ai
WHERE id NOT IN (
    SELECT MIN(id) 
    FROM article_ai 
    GROUP BY article_id
);

SELECT '已刪除 ' || changes() || ' 筆重複的 ArticleAi 記錄' as status;

-- 5. 驗證結果
SELECT '==== 驗證結果 ====' as step;
SELECT 
    article_id, 
    COUNT(*) as count
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

SELECT '修復完成！' as status;
