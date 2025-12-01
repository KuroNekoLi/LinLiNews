-- ================================================
-- 快速修復重複的 ArticleAi 記錄
-- 這個腳本會直接合併重複記錄，無需手動確認
-- ================================================

SET @row_count = 0;

-- 1. 先查看有多少重複記錄
SELECT 
    '發現重複記錄' as status,
    COUNT(*) as duplicate_article_count
FROM (
    SELECT article_id
    FROM article_ai
    GROUP BY article_id
    HAVING COUNT(*) > 1
) as duplicates;

-- 2. 對每個重複的 article_id，將所有 views 合併到第一筆記錄
-- 然後刪除其他記錄

-- Step 2.1: 更新 views 指向保留的記錄
UPDATE article_ai_view aiv
INNER JOIN (
    -- 找出所有要刪除的記錄
    SELECT 
        ai.id as duplicate_id,
        ai.article_id,
        (SELECT MIN(id) FROM article_ai WHERE article_id = ai.article_id) as keep_id
    FROM article_ai ai
    WHERE ai.id NOT IN (
        SELECT MIN(id) FROM article_ai GROUP BY article_id
    )
) as mapping ON aiv.article_ai_id = mapping.duplicate_id
SET aiv.article_ai_id = mapping.keep_id
WHERE NOT EXISTS (
    -- 避免創建重複的 view
    SELECT 1 FROM article_ai_view existing
    WHERE existing.article_ai_id = mapping.keep_id
      AND existing.difficulty = aiv.difficulty
      AND existing.from_lang = aiv.from_lang
      AND existing.to_lang = aiv.to_lang
);

SELECT ROW_COUNT() INTO @row_count;
SELECT CONCAT('已移動 ', @row_count, ' 個 views') as status;

-- Step 2.2: 刪除無法合併的衝突 views
DELETE aiv FROM article_ai_view aiv
INNER JOIN (
    SELECT 
        ai.id as duplicate_id,
        ai.article_id,
        (SELECT MIN(id) FROM article_ai WHERE article_id = ai.article_id) as keep_id
    FROM article_ai ai
    WHERE ai.id NOT IN (
        SELECT MIN(id) FROM article_ai GROUP BY article_id
    )
) as mapping ON aiv.article_ai_id = mapping.duplicate_id
WHERE EXISTS (
    SELECT 1 FROM article_ai_view existing
    WHERE existing.article_ai_id = mapping.keep_id
      AND existing.difficulty = aiv.difficulty
      AND existing.from_lang = aiv.from_lang
      AND existing.to_lang = aiv.to_lang
);

SELECT ROW_COUNT() INTO @row_count;
SELECT CONCAT('已刪除 ', @row_count, ' 個衝突的 views') as status;

-- Step 2.3: 刪除重複的 ArticleAi 記錄
DELETE FROM article_ai
WHERE id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) as min_id FROM article_ai GROUP BY article_id
    ) as keeper
);

SELECT ROW_COUNT() INTO @row_count;
SELECT CONCAT('已刪除 ', @row_count, ' 筆重複的 ArticleAi 記錄') as status;

-- 3. 驗證結果
SELECT 
    '驗證：剩餘的重複記錄' as status,
    COUNT(*) as duplicate_count
FROM (
    SELECT article_id
    FROM article_ai
    GROUP BY article_id
    HAVING COUNT(*) > 1
) as still_duplicates;

-- 4. 顯示統計
SELECT 
    '統計：每個難度的文章數' as status,
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

SELECT '修復完成！' as status;
