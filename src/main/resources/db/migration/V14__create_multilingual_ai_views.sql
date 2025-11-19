CREATE TABLE IF NOT EXISTS article_ai_view (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_id BIGINT NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    from_lang VARCHAR(16) NOT NULL,
    to_lang VARCHAR(16) NOT NULL,
    title_from TEXT NULL,
    title_to TEXT NULL,
    summary_from TEXT NULL,
    summary_to TEXT NULL,
    optimized_html_from TEXT NULL,
    optimized_html_to TEXT NULL,
    cleaned_text_from TEXT NULL,
    cleaned_text_to TEXT NULL,
    legacy_explanation_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_ai_view_article FOREIGN KEY (article_ai_id)
        REFERENCES article_ai(id) ON DELETE CASCADE,
    CONSTRAINT uk_article_ai_view_article_lang_difficulty UNIQUE (article_ai_id, from_lang, to_lang, difficulty)
);

CREATE INDEX IF NOT EXISTS idx_article_ai_view_article ON article_ai_view(article_ai_id);
CREATE INDEX IF NOT EXISTS idx_article_ai_view_difficulty ON article_ai_view(difficulty);
CREATE INDEX IF NOT EXISTS idx_article_ai_view_lang_pair ON article_ai_view(from_lang, to_lang);

CREATE TABLE IF NOT EXISTS ai_vocabulary_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_view_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    pos VARCHAR(32) NULL,
    word_from TEXT NULL,
    word_to TEXT NULL,
    definition_from TEXT NULL,
    definition_to TEXT NULL,
    example_from TEXT NULL,
    example_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_vocabulary_view FOREIGN KEY (article_ai_view_id)
        REFERENCES article_ai_view(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_vocab_view ON ai_vocabulary_item(article_ai_view_id);

CREATE TABLE IF NOT EXISTS ai_grammar_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_view_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    rule_from TEXT NULL,
    rule_to TEXT NULL,
    explanation_from TEXT NULL,
    explanation_to TEXT NULL,
    example_from TEXT NULL,
    example_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_grammar_view FOREIGN KEY (article_ai_view_id)
        REFERENCES article_ai_view(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_grammar_view ON ai_grammar_item(article_ai_view_id);

CREATE TABLE IF NOT EXISTS ai_sentence_pattern_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_view_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    pattern_from TEXT NULL,
    pattern_to TEXT NULL,
    explanation_from TEXT NULL,
    explanation_to TEXT NULL,
    example_from TEXT NULL,
    example_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_sentence_pattern_view FOREIGN KEY (article_ai_view_id)
        REFERENCES article_ai_view(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_sentence_pattern_view ON ai_sentence_pattern_item(article_ai_view_id);

CREATE TABLE IF NOT EXISTS ai_phrase_idiom_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_view_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    phrase_from TEXT NULL,
    phrase_to TEXT NULL,
    explanation_from TEXT NULL,
    explanation_to TEXT NULL,
    example_from TEXT NULL,
    example_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_phrase_idiom_view FOREIGN KEY (article_ai_view_id)
        REFERENCES article_ai_view(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_phrase_idiom_view ON ai_phrase_idiom_item(article_ai_view_id);

CREATE TABLE IF NOT EXISTS ai_comprehension_mcq (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_ai_view_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    question_from TEXT NULL,
    question_to TEXT NULL,
    answer VARCHAR(4) NULL,
    explanation_from TEXT NULL,
    explanation_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_mcq_view FOREIGN KEY (article_ai_view_id)
        REFERENCES article_ai_view(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_mcq_view ON ai_comprehension_mcq(article_ai_view_id);

CREATE TABLE IF NOT EXISTS ai_comprehension_option (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mcq_id BIGINT NOT NULL,
    label VARCHAR(4) NOT NULL,
    text_from TEXT NULL,
    text_to TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_option_mcq FOREIGN KEY (mcq_id)
        REFERENCES ai_comprehension_mcq(id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_option_label UNIQUE (mcq_id, label)
);
CREATE INDEX IF NOT EXISTS idx_ai_option_mcq ON ai_comprehension_option(mcq_id);
