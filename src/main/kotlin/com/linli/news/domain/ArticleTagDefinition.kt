package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * Tag 定義表
 *
 * 儲存所有可用的 tag 及其 embedding
 */
@Entity
@Table(name = "article_tag_definition")
class ArticleTagDefinition(
    @Column(name = "name", nullable = false, unique = true, length = 128)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

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
