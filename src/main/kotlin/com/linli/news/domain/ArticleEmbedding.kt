package com.linli.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "article_embedding")
class ArticleEmbedding(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    var article: Article,

    @Column(name = "model", nullable = false, length = 128)
    var model: String,

    @Column(name = "embedding", columnDefinition = "LONGTEXT", nullable = false)
    var embeddingJson: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
