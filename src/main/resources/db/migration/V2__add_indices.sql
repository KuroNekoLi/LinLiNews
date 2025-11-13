-- V2__add_indices.sql
-- u65b0u589e SQL u7d22u5f15u4ee5u63d0u5347u6548u80fd

-- article u8868u76f8u95dc u7d22u5f15
CREATE INDEX IF NOT EXISTS idx_article_title ON article(title);
CREATE INDEX IF NOT EXISTS idx_article_published_at ON article(published_at);
CREATE INDEX IF NOT EXISTS idx_article_source_id ON article(source_id);
CREATE INDEX IF NOT EXISTS idx_article_original_url ON article(original_url);

-- article_ai u8868u76f8u95dc u7d22u5f15
CREATE INDEX IF NOT EXISTS idx_article_ai_article_id ON article_ai(article_id);

-- article_source u8868u76f8u95dcu7d22u5f15
CREATE INDEX IF NOT EXISTS idx_article_source_name ON article_source(name);
CREATE INDEX IF NOT EXISTS idx_article_source_section ON article_source(section);
CREATE UNIQUE INDEX IF NOT EXISTS idx_article_source_rss_url ON article_source(rss_url);