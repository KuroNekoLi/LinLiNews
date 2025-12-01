-- ================================================
-- 智能修復 article_ai 表中的重複記錄
-- 此腳本會自動合併重複記錄並保留所有 views
-- ================================================

-- 開始事務
START TRANSACTION;

-- 1. 檢查重複記錄
SELECT '==== 重複記錄檢查 ====' as step;
SELECT 
    article_id, 
    COUNT(*) as count,
    GROUP_CONCAT(id ORDER BY id) as ids
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 2. 為每個重複的 article_id 保留最小的 id，準備刪除其他記錄
SELECT '==== 準備合併 ====' as step;

-- 創建臨時表記錄要保留和要刪除的 ID
DROP TEMPORARY TABLE IF EXISTS duplicate_mapping;
CREATE TEMPORARY TABLE duplicate_mapping AS
SELECT 
    article_id,
    MIN(id) as keep_id,
    id as duplicate_id
FROM article_ai
WHERE article_id IN (
    SELECT article_id 
    FROM article_ai 
    GROUP BY article_id 
    HAVING COUNT(*) > 1
)
GROUP BY article_id, id
HAVING id != MIN(id) OVER (PARTITION BY article_id);

-- 如果上面的查詢不支持 WINDOW 函數，使用這個替代方案
DROP TEMPORARY TABLE IF EXISTS duplicate_mapping;
CREATE TEMPORARY TABLE duplicate_mapping AS
SELECT 
    d.article_id,
    (SELECT MIN(id) FROM article_ai WHERE article_id = d.article_id) as keep_id,
    d.id as duplicate_id
FROM article_ai d
WHERE d.id NOT IN (
    SELECT MIN(id) 
    FROM article_ai 
    GROUP BY article_id
);

-- 檢視將要處理的記錄
SELECT 
    dm.*,
    (SELECT COUNT(*) FROM article_ai_view WHERE article_ai_id = dm.duplicate_id) as views_to_move
FROM duplicate_mapping dm;

-- 3. 將重複記錄的所有 views 移到保留的記錄上
SELECT '==== 合併 Views ====' as step;

UPDATE article_ai_view aiv
INNER JOIN duplicate_mapping dm ON aiv.article_ai_id = dm.duplicate_id
SET aiv.article_ai_id = dm.keep_id
WHERE NOT EXISTS (
    -- 確保不會創建重複的 view（相同的 difficulty, from_lang, to_lang）
    SELECT 1 FROM article_ai_view existing
    WHERE existing.article_ai_id = dm.keep_id
      AND existing.difficulty = aiv.difficulty
      AND existing.from_lang = aiv.from_lang
      AND existing.to_lang = aiv.to_lang
);

-- 4. 刪除無法合併的重複 views（相同 difficulty + language 的衝突）
SELECT '==== 刪除衝突的 Views ====' as step;

-- 記錄將要刪除的 views
SELECT 
    'Deleting duplicate view:' as action,
    aiv.id,
    aiv.difficulty,
    aiv.from_lang,
    aiv.to_lang,
    aiv.article_ai_id
FROM article_ai_view aiv
INNER JOIN duplicate_mapping dm ON aiv.article_ai_id = dm.duplicate_id
WHERE EXISTS (
    SELECT 1 FROM article_ai_view existing
    WHERE existing.article_ai_id = dm.keep_id
      AND existing.difficulty = aiv.difficulty
      AND existing.from_lang = aiv.from_lang
      AND existing.to_lang = aiv.to_lang
);

-- 刪除衝突的 views（保留在 keep_id 上的，刪除在 duplicate_id 上的）
DELETE aiv FROM article_ai_view aiv
INNER JOIN duplicate_mapping dm ON aiv.article_ai_id = dm.duplicate_id
WHERE EXISTS (
    SELECT 1 FROM article_ai_view existing
    WHERE existing.article_ai_id = dm.keep_id
      AND existing.difficulty = aiv.difficulty
      AND existing.from_lang = aiv.from_lang
      AND existing.to_lang = aiv.to_lang
);

-- 5. 刪除重複的 ArticleAi 記錄
SELECT '==== 刪除重複的 ArticleAi 記錄 ====' as step;

DELETE ai FROM article_ai ai
INNER JOIN duplicate_mapping dm ON ai.id = dm.duplicate_id;

-- 6. 驗證結果
SELECT '==== 驗證結果 ====' as step;

-- 應該返回空結果
SELECT 
    article_id, 
    COUNT(*) as count
FROM article_ai
GROUP BY article_id
HAVING COUNT(*) > 1;

-- 統計每篇文章的 views 數量
SELECT 
    ai.article_id,
    COUNT(DISTINCT v.difficulty) as difficulty_count,
    GROUP_CONCAT(DISTINCT v.difficulty ORDER BY v.difficulty) as difficulties
FROM article_ai ai
LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id
GROUP BY ai.article_id
ORDER BY difficulty_count DESC
LIMIT 20;

-- 清理臨時表
DROP TEMPORARY TABLE IF EXISTS duplicate_mapping;

-- 提交事務（如果一切正常）
-- COMMIT;

-- 如果發現問題，可以回滾
-- ROLLBACK;

SELECT '==== 完成！請檢查結果，如果滿意請執行 COMMIT; 否則執行 ROLLBACK; ====' as final_message;
