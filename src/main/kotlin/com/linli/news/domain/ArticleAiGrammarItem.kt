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
 * 文法講解項目。
 */
@Entity
@Table(
    name = "ai_grammar_item",
    indexes = [Index(name = "idx_ai_grammar_view", columnList = "article_ai_view_id")]
)
class ArticleAiGrammarItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_ai_view_id", nullable = false)
    var articleAiView: ArticleAiView,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "rule_from", columnDefinition = "LONGTEXT")
    var ruleFrom: String? = null,

    @Column(name = "rule_to", columnDefinition = "LONGTEXT")
    var ruleTo: String? = null,

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
