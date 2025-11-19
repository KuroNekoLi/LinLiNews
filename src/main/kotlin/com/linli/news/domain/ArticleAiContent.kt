package com.linli.news.domain

import com.linli.news.model.DifficultyLevel
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
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

/**
 * 依難度儲存的 AI 內容。
 *
 * @property articleAi 關聯的 AI 文章
 * @property difficulty 對應的難度
 * @property optimizedHtml 重寫後的英文 HTML
 * @property optimizedZhHtml 重寫後的繁體中文 HTML
 * @property cleanedText 重寫後的英文純文字
 * @property cleanedTextZh 重寫後的中文純文字
 * @property explanation 教學解析 JSON
 */
@Entity
@Table(
    name = "article_ai_content",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_article_ai_content_article_difficulty",
            columnNames = ["article_ai_id", "difficulty"]
        )
    ],
    indexes = [
        Index(name = "idx_article_ai_content_article", columnList = "article_ai_id"),
        Index(name = "idx_article_ai_content_difficulty", columnList = "difficulty")
    ]
)
class ArticleAiContent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_ai_id", nullable = false)
    var articleAi: ArticleAi,

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    var difficulty: DifficultyLevel,

    @Column(name = "optimized_html", columnDefinition = "LONGTEXT")
    var optimizedHtml: String? = null,

    @Column(name = "optimized_zh_html", columnDefinition = "LONGTEXT")
    var optimizedZhHtml: String? = null,

    @Column(name = "cleaned_text", columnDefinition = "LONGTEXT")
    var cleanedText: String? = null,

    @Column(name = "cleaned_text_zh", columnDefinition = "LONGTEXT")
    var cleanedTextZh: String? = null,

    @Column(name = "explanation", columnDefinition = "JSON")
    var explanation: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
