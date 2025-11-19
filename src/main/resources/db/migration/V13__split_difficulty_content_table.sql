CREATE TABLE IF NOT EXISTS article_ai_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_ai_id BIGINT NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    optimized_html LONGTEXT NULL,
    optimized_zh_html LONGTEXT NULL,
    cleaned_text LONGTEXT NULL,
    cleaned_text_zh LONGTEXT NULL,
    explanation JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_ai_content_article FOREIGN KEY (article_ai_id)
        REFERENCES article_ai(id) ON DELETE CASCADE,
    CONSTRAINT uk_article_ai_content_article_difficulty UNIQUE (article_ai_id, difficulty)
);

-- 將原有 JSON 欄位資料拆出成獨立表格
INSERT INTO article_ai_content (
    article_ai_id,
    difficulty,
    optimized_html,
    optimized_zh_html,
    cleaned_text,
    cleaned_text_zh,
    explanation
)
SELECT
    id,
    'EASY',
    JSON_UNQUOTE(JSON_EXTRACT(content_easy, '$.optimized_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_easy, '$.optimized_zh_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_easy, '$.cleaned_text')),
    JSON_UNQUOTE(JSON_EXTRACT(content_easy, '$.cleaned_text_zh')),
    JSON_EXTRACT(content_easy, '$.explanation')
FROM article_ai
WHERE content_easy IS NOT NULL;

INSERT INTO article_ai_content (
    article_ai_id,
    difficulty,
    optimized_html,
    optimized_zh_html,
    cleaned_text,
    cleaned_text_zh,
    explanation
)
SELECT
    id,
    'NORMAL',
    JSON_UNQUOTE(JSON_EXTRACT(content_normal, '$.optimized_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_normal, '$.optimized_zh_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_normal, '$.cleaned_text')),
    JSON_UNQUOTE(JSON_EXTRACT(content_normal, '$.cleaned_text_zh')),
    JSON_EXTRACT(content_normal, '$.explanation')
FROM article_ai
WHERE content_normal IS NOT NULL;

INSERT INTO article_ai_content (
    article_ai_id,
    difficulty,
    optimized_html,
    optimized_zh_html,
    cleaned_text,
    cleaned_text_zh,
    explanation
)
SELECT
    id,
    'HARD',
    JSON_UNQUOTE(JSON_EXTRACT(content_hard, '$.optimized_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_hard, '$.optimized_zh_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_hard, '$.cleaned_text')),
    JSON_UNQUOTE(JSON_EXTRACT(content_hard, '$.cleaned_text_zh')),
    JSON_EXTRACT(content_hard, '$.explanation')
FROM article_ai
WHERE content_hard IS NOT NULL;

INSERT INTO article_ai_content (
    article_ai_id,
    difficulty,
    optimized_html,
    optimized_zh_html,
    cleaned_text,
    cleaned_text_zh,
    explanation
)
SELECT
    id,
    'VERY_HARD',
    JSON_UNQUOTE(JSON_EXTRACT(content_very_hard, '$.optimized_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_very_hard, '$.optimized_zh_html')),
    JSON_UNQUOTE(JSON_EXTRACT(content_very_hard, '$.cleaned_text')),
    JSON_UNQUOTE(JSON_EXTRACT(content_very_hard, '$.cleaned_text_zh')),
    JSON_EXTRACT(content_very_hard, '$.explanation')
FROM article_ai
WHERE content_very_hard IS NOT NULL;

-- 清除舊欄位
ALTER TABLE article_ai
    DROP COLUMN optimized_html,
    DROP COLUMN optimized_zh_html,
    DROP COLUMN cleaned_text,
    DROP COLUMN cleaned_text_zh,
    DROP COLUMN explanation,
    DROP COLUMN content_easy,
    DROP COLUMN content_normal,
    DROP COLUMN content_hard,
    DROP COLUMN content_very_hard;
