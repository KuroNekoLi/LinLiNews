package com.linli.news.domain

import jakarta.persistence.*

/**
 * 文章與 Tag 的配對關係
 */
@Entity
@Table(
    name = "article_tag",
    indexes = [
        Index(name = "idx_article_tag_article_id", columnList = "article_id"),
        Index(name = "idx_article_tag_tag_id", columnList = "tag_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_article_tag", columnNames = ["article_id", "tag_id"])
    ]
)
class ArticleTagAssignment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    var article: Article,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    var tag: ArticleTagDefinition,

    @Column(name = "score", nullable = false)
    var score: Double
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
