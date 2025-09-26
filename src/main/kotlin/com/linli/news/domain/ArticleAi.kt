package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "article_ai",
    uniqueConstraints = [UniqueConstraint(name = "uk_article_ai_article", columnNames = ["article_id"])],
    indexes = [Index(name = "idx_article_ai_article_id", columnList = "article_id")]
)
class ArticleAi(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    var article: Article,

    @Column(name = "title", length = 512)
    var title: String? = null,

    @Column(name = "title_zh", length = 512)
    var titleZh: String? = null,

    @Column(name = "cleaned_html", columnDefinition = "LONGTEXT")
    var cleanedHtml: String? = null,

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
