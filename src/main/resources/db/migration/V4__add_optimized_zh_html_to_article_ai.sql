-- Add column for AI-generated traditional Chinese HTML
ALTER TABLE article_ai
    ADD COLUMN optimized_zh_html LONGTEXT NULL;
