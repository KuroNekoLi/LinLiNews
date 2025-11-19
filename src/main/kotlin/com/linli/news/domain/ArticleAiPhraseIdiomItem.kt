package com.linli.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 片語或成語講解項目。
 */
@Entity
@Table(
    name = "ai_phrase_idiom_item",
    indexes = [Index(name = "idx_ai_phrase_idiom_view", columnList = "article_ai_view_id")]
)
class ArticleAiPhraseIdiomItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_ai_view_id", nullable = false)
    var articleAiView: ArticleAiView,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "phrase_from", columnDefinition = "LONGTEXT")
    var phraseFrom: String? = null,

    @Column(name = "phrase_to", columnDefinition = "LONGTEXT")
    var phraseTo: String? = null,

    @Column(name = "explanation_from", columnDefinition = "LONGTEXT")
    var explanationFrom: String? = null,

    @Column(name = "explanation_to", columnDefinition = "LONGTEXT")
    var explanationTo: String? = null,

    @Column(name = "example_from", columnDefinition = "LONGTEXT")
    var exampleFrom: String? = null,

    @Column(name = "example_to", columnDefinition = "LONGTEXT")
    var exampleTo: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
