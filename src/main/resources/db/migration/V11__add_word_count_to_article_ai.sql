-- V11__add_word_count_to_article_ai.sql
-- 添加 word_count 字段到 article_ai 表，用于存储文章的单词数量

ALTER TABLE article_ai ADD COLUMN word_count INTEGER DEFAULT 0;