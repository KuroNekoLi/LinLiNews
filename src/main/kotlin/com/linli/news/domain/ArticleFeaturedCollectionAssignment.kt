package com.linli.news.domain

import jakarta.persistence.*

/**
 * 文章與精選專題的配對關係
 */
@Entity
@Table(
    name = "article_featured_collection",
    indexes = [
        Index(name = "idx_afc_article_id", columnList = "article_id"),
        Index(name = "idx_afc_collection_id", columnList = "collection_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_article_featured_collection", columnNames = ["article_id", "collection_id"])
    ]
)
class ArticleFeaturedCollectionAssignment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    var article: Article,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    var collection: FeaturedCollection,

    @Column(name = "score", nullable = false)
    var score: Double
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
