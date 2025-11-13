-- V10__fresh_start_schema.sql
-- A complete schema definition in one file, allowing for a clean start

-- Create article source table
CREATE TABLE IF NOT EXISTS article_source (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  rss_url TEXT NOT NULL,
  section TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- Create article table
CREATE TABLE IF NOT EXISTS article (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT,
  published_at TIMESTAMP,
  content_html TEXT,
  original_url TEXT NOT NULL,
  source_id INTEGER,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  excerpt TEXT,
  author TEXT,
  language TEXT,
  domain TEXT,
  effective_url TEXT,
  word_count INTEGER,
  FOREIGN KEY(source_id) REFERENCES article_source(id)
);

-- Create article_ai table with all columns
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

-- Add indices
CREATE INDEX IF NOT EXISTS idx_article_original_url ON article(original_url);
CREATE INDEX IF NOT EXISTS idx_article_title ON article(title);
CREATE INDEX IF NOT EXISTS idx_article_published_at ON article(published_at);
CREATE INDEX IF NOT EXISTS idx_article_source_id ON article(source_id);
CREATE INDEX IF NOT EXISTS idx_article_source_rss_url ON article_source(rss_url);

-- Drop any JobRunr tables
DROP TABLE IF EXISTS jobrunr_recurring_jobs;
DROP TABLE IF EXISTS jobrunr_jobs;
DROP TABLE IF EXISTS jobrunr_metadata;
DROP TABLE IF EXISTS jobrunr_backgroundjobservers;