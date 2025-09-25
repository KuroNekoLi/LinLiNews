package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.ZonedDateTime

@Entity
@Table(
    name = "article",
    indexes = [
        Index(name = "idx_article_published_at", columnList = "published_at"),
        Index(name = "idx_article_source_id", columnList = "source_id")
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_article_original_url", columnNames = ["original_url"])]
)
class Article(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    var source: ArticleSource,

    @Column(name = "title", length = 512)
    var title: String? = null,

    @Column(name = "excerpt", columnDefinition = "TEXT")
    var excerpt: String? = null,

    @Column(name = "author", length = 255)
    var author: String? = null,

    @Column(name = "language", length = 32)
    var language: String? = null,

    @Column(name = "domain", length = 255)
    var domain: String? = null,

    @Column(name = "original_url", nullable = false, length = 1024)
    var originalUrl: String,

    @Column(name = "effective_url", length = 1024)
    var effectiveUrl: String? = null,

    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    var contentHtml: String? = null,

    @Column(name = "word_count")
    var wordCount: Int? = null,

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
