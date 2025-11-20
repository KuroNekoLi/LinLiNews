package com.linli.news.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 閱讀理解題目。
 */
@Entity
@Table(
    name = "ai_comprehension_mcq",
    indexes = [Index(name = "idx_ai_mcq_view", columnList = "article_ai_view_id")]
)
class ArticleAiComprehensionMcq(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_ai_view_id", nullable = false)
    var articleAiView: ArticleAiView,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "question_from", columnDefinition = "LONGTEXT")
    var questionFrom: String? = null,

    @Column(name = "question_to", columnDefinition = "LONGTEXT")
    var questionTo: String? = null,

    @Column(name = "answer", length = 4)
    var answer: String? = null,

    @Column(name = "explanation_from", columnDefinition = "LONGTEXT")
    var explanationFrom: String? = null,

    @Column(name = "explanation_to", columnDefinition = "LONGTEXT")
    var explanationTo: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    @OneToMany(
        mappedBy = "mcq",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var options: MutableList<ArticleAiComprehensionOption> = mutableListOf()
}
