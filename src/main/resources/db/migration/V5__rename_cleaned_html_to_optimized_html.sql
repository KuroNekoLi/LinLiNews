-- Rename legacy cleaned_html column to optimized_html if it still exists
SET @rename_sql := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'article_ai'
              AND COLUMN_NAME = 'cleaned_html'
        ),
        'ALTER TABLE article_ai RENAME COLUMN cleaned_html TO optimized_html',
        'SELECT 1'
    )
);

PREPARE stmt FROM @rename_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
