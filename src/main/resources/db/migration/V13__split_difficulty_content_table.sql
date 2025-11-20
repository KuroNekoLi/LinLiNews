CREATE TABLE IF NOT EXISTS article_ai_content (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_id BIGINT NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    optimized_html TEXT NULL,
    optimized_zh_html TEXT NULL,
    cleaned_text TEXT NULL,
    cleaned_text_zh TEXT NULL,
    explanation TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    json_extract(content_easy, '$.optimized_html'),
    json_extract(content_easy, '$.optimized_zh_html'),
    json_extract(content_easy, '$.cleaned_text'),
    json_extract(content_easy, '$.cleaned_text_zh'),
    json_extract(content_easy, '$.explanation')
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
    json_extract(content_normal, '$.optimized_html'),
    json_extract(content_normal, '$.optimized_zh_html'),
    json_extract(content_normal, '$.cleaned_text'),
    json_extract(content_normal, '$.cleaned_text_zh'),
    json_extract(content_normal, '$.explanation')
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
    json_extract(content_hard, '$.optimized_html'),
    json_extract(content_hard, '$.optimized_zh_html'),
    json_extract(content_hard, '$.cleaned_text'),
    json_extract(content_hard, '$.cleaned_text_zh'),
    json_extract(content_hard, '$.explanation')
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
    json_extract(content_very_hard, '$.optimized_html'),
    json_extract(content_very_hard, '$.optimized_zh_html'),
    json_extract(content_very_hard, '$.cleaned_text'),
    json_extract(content_very_hard, '$.cleaned_text_zh'),
    json_extract(content_very_hard, '$.explanation')
FROM article_ai
WHERE content_very_hard IS NOT NULL;

ALTER TABLE article_ai DROP COLUMN optimized_html;
ALTER TABLE article_ai DROP COLUMN optimized_zh_html;
ALTER TABLE article_ai DROP COLUMN cleaned_text;
ALTER TABLE article_ai DROP COLUMN cleaned_text_zh;
ALTER TABLE article_ai DROP COLUMN explanation;
ALTER TABLE article_ai DROP COLUMN content_easy;
ALTER TABLE article_ai DROP COLUMN content_normal;
ALTER TABLE article_ai DROP COLUMN content_hard;
ALTER TABLE article_ai DROP COLUMN content_very_hard;
