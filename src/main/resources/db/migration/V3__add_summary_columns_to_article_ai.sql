-- Add columns for AI-generated summaries
ALTER TABLE article_ai
    ADD COLUMN summary_en LONGTEXT NULL,
    ADD COLUMN summary_zh LONGTEXT NULL;
