-- V1__initial_schema.sql
-- 初始資料庫建表（對應 article 與 article_ai)

CREATE TABLE IF NOT EXISTS article_source (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  rss_url TEXT NOT NULL,
  section TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS article (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT,
  published_at TIMESTAMP,
  content_html TEXT,
  original_url TEXT NOT NULL,
  source_id INTEGER,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  FOREIGN KEY(source_id) REFERENCES article_source(id)
);

CREATE TABLE IF NOT EXISTS article_ai (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  article_id INTEGER NOT NULL,
  cleaned_text TEXT,
  cleaned_text_zh TEXT,
  explanation TEXT,
  optimized_html TEXT,
  optimized_zh_html TEXT,
  summary_en TEXT,
  summary_zh TEXT,
  title TEXT,
  title_zh TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  FOREIGN KEY(article_id) REFERENCES article(id)
);

-- Flyway schema history table will be created automatically by Flyway