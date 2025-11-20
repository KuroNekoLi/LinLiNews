package com.linli.news.domain

import com.linli.news.model.DifficultyLevel
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

/**
 * 依語言與難度儲存的 AI 視圖內容。
 *
 * @property articleAi 關聯的 AI 文章
 * @property difficulty 對應的難度
 * @property fromLanguage 原文語言碼
 * @property toLanguage 目標語言碼
 * @property titleFrom 原文標題
 * @property titleTo 目標語言標題
 * @property summaryFrom 原文摘要
 * @property summaryTo 目標語言摘要
 * @property optimizedHtmlFrom 重寫後的原文 HTML
 * @property optimizedHtmlTo 重寫後的目標語言 HTML
 * @property cleanedTextFrom 重寫後的原文純文字
 * @property cleanedTextTo 重寫後的目標語言純文字
 * @property legacyExplanationJson 備援用的解析 JSON
 */
@Entity
@Table(
    name = "article_ai_view",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_article_ai_view_article_lang_difficulty",
            columnNames = ["article_ai_id", "from_lang", "to_lang", "difficulty"]
        )
    ],
    indexes = [
        Index(name = "idx_article_ai_view_article", columnList = "article_ai_id"),
        Index(name = "idx_article_ai_view_difficulty", columnList = "difficulty"),
        Index(name = "idx_article_ai_view_lang_pair", columnList = "from_lang, to_lang")
    ]
)
class ArticleAiView(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_ai_id", nullable = false)
    var articleAi: ArticleAi,

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    var difficulty: DifficultyLevel,

    @Column(name = "from_lang", nullable = false, length = 16)
    var fromLanguage: String,

    @Column(name = "to_lang", nullable = false, length = 16)
    var toLanguage: String,

    @Column(name = "title_from", length = 512)
    var titleFrom: String? = null,

    @Column(name = "title_to", length = 512)
    var titleTo: String? = null,

    @Column(name = "summary_from", columnDefinition = "LONGTEXT")
    var summaryFrom: String? = null,

    @Column(name = "summary_to", columnDefinition = "LONGTEXT")
    var summaryTo: String? = null,

    @Column(name = "optimized_html_from", columnDefinition = "LONGTEXT")
    var optimizedHtmlFrom: String? = null,

    @Column(name = "optimized_html_to", columnDefinition = "LONGTEXT")
    var optimizedHtmlTo: String? = null,

    @Column(name = "cleaned_text_from", columnDefinition = "LONGTEXT")
    var cleanedTextFrom: String? = null,

    @Column(name = "cleaned_text_to", columnDefinition = "LONGTEXT")
    var cleanedTextTo: String? = null,

    @Column(name = "legacy_explanation_json", columnDefinition = "LONGTEXT")
    var legacyExplanationJson: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    @OneToMany(
        mappedBy = "articleAiView",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var vocabularyItems: MutableList<ArticleAiVocabularyItem> = mutableListOf()

    @OneToMany(
        mappedBy = "articleAiView",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var grammarItems: MutableList<ArticleAiGrammarItem> = mutableListOf()

    @OneToMany(
        mappedBy = "articleAiView",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var sentencePatternItems: MutableList<ArticleAiSentencePatternItem> = mutableListOf()

    @OneToMany(
        mappedBy = "articleAiView",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var phraseIdiomItems: MutableList<ArticleAiPhraseIdiomItem> = mutableListOf()

    @OneToMany(
        mappedBy = "articleAiView",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var comprehensionQuestions: MutableList<ArticleAiComprehensionMcq> = mutableListOf()
}
