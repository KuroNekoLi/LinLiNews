-- 建立新聞來源表
CREATE TABLE IF NOT EXISTS article_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    rss_url VARCHAR(1024) NOT NULL,
    section VARCHAR(128) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 建立文章表
CREATE TABLE IF NOT EXISTS article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    title VARCHAR(512) NULL,
    excerpt TEXT NULL,
    author VARCHAR(255) NULL,
    language VARCHAR(32) NULL,
    domain VARCHAR(255) NULL,
    original_url VARCHAR(1024) NOT NULL,
    effective_url VARCHAR(1024) NULL,
    content_html LONGTEXT NULL,
    word_count INT NULL,
    published_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_source FOREIGN KEY (source_id) REFERENCES article_source(id)
);

-- 索引
CREATE INDEX idx_article_published_at ON article(published_at);
CREATE INDEX idx_article_source_id ON article(source_id);
CREATE UNIQUE INDEX uk_article_source_rss ON article_source(rss_url(512));
CREATE UNIQUE INDEX uk_article_original_url ON article(original_url(512));
