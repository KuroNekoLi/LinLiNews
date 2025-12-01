-- ================================================
-- 驗證同一篇文章的多個難度是否正確儲存
-- ================================================

-- 1. 查看每篇文章有多少個 ArticleAi 記錄（應該每篇只有 1 個）
SELECT 
    article_id,
    COUNT(*) as article_ai_count,
    GROUP_CONCAT(id) as article_ai_ids
FROM article_ai
GROUP BY article_id
ORDER BY article_ai_count DESC, article_id;

-- 2. 查看每個 ArticleAi 有多少個不同難度的 views
SELECT 
    ai.id as article_ai_id,
    ai.article_id,
    COUNT(DISTINCT v.id) as total_views,
    COUNT(DISTINCT v.difficulty) as difficulty_count,
    GROUP_CONCAT(DISTINCT v.difficulty ORDER BY v.difficulty) as difficulties
FROM article_ai ai
LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id
GROUP BY ai.id, ai.article_id
ORDER BY difficulty_count DESC, ai.article_id;

-- 3. 詳細查看某篇文章的所有 views（替換 YOUR_ARTICLE_ID）
-- SELECT 
--     ai.id as article_ai_id,
--     ai.article_id,
--     v.id as view_id,
--     v.difficulty,
--     v.from_lang,
--     v.to_lang,
--     LENGTH(v.optimized_html_from) as html_from_length,
--     LENGTH(v.optimized_html_to) as html_to_length,
--     (SELECT COUNT(*) FROM article_ai_vocabulary_item WHERE article_ai_view_id = v.id) as vocab_count,
--     (SELECT COUNT(*) FROM article_ai_grammar_item WHERE article_ai_view_id = v.id) as grammar_count,
--     v.created_at,
--     v.updated_at
-- FROM article_ai ai
-- INNER JOIN article_ai_view v ON ai.id = v.article_ai_id
-- WHERE ai.article_id = YOUR_ARTICLE_ID
-- ORDER BY v.difficulty;

-- 4. 找出有重複 ArticleAi 記錄的文章（應該返回空結果）
SELECT 
    article_id,
    COUNT(*) as duplicate_count,
    GROUP_CONCAT(id) as duplicate_ids
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 5. 找出有多個難度的文章（這是正常的）
SELECT 
    ai.article_id,
    COUNT(DISTINCT v.difficulty) as difficulty_count,
    GROUP_CONCAT(DISTINCT v.difficulty ORDER BY 
        CASE v.difficulty
            WHEN 'VERY_EASY' THEN 1
            WHEN 'EASY' THEN 2
            WHEN 'NORMAL' THEN 3
            WHEN 'HARD' THEN 4
            WHEN 'VERY_HARD' THEN 5
        END
    ) as difficulties
FROM article_ai ai
INNER JOIN article_ai_view v ON ai.id = v.article_ai_id
GROUP BY ai.article_id
HAVING difficulty_count > 1
ORDER BY difficulty_count DESC, ai.article_id
LIMIT 20;

-- 6. 統計每個難度有多少文章
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

-- 7. 檢查 unique constraint 是否存在
SELECT 
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE,
    TABLE_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'article_ai'
  AND CONSTRAINT_TYPE = 'UNIQUE';
