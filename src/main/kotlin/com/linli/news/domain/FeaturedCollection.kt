package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * 精選專題定義
 */
@Entity
@Table(name = "featured_collection")
class FeaturedCollection(
    @Column(name = "collection_id", nullable = false, unique = true, length = 64)
    var collectionId: String,

    @Column(name = "title", nullable = false, length = 256)
    var title: String,

    @Column(name = "subtitle", length = 256)
    var subtitle: String? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "color_start", length = 32)
    var colorStart: String? = null,

    @Column(name = "color_end", length = 32)
    var colorEnd: String? = null,

    @Column(name = "embedding", columnDefinition = "LONGTEXT", nullable = false)
    var embeddingJson: String,

    @Column(name = "model", nullable = false, length = 128)
    var model: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
