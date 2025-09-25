package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "article_source",
    uniqueConstraints = [UniqueConstraint(name = "uk_article_source_rss", columnNames = ["rss_url"])]
)
class ArticleSource(
    @Column(name = "name", nullable = false, length = 128)
    var name: String,

    @Column(name = "rss_url", nullable = false, length = 1024)
    var rssUrl: String,

    @Column(name = "section", length = 128)
    var section: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
