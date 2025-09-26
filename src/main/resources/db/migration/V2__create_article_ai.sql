-- Create table to store AI-processed article outputs
CREATE TABLE IF NOT EXISTS article_ai (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL,
  title VARCHAR(512),
  title_zh VARCHAR(512),
  cleaned_html LONGTEXT,
  cleaned_text LONGTEXT,
  cleaned_text_zh LONGTEXT,
  explanation JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_article_ai_article UNIQUE (article_id),
  CONSTRAINT fk_article_ai_article FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE INDEX idx_article_ai_article_id ON article_ai(article_id);
