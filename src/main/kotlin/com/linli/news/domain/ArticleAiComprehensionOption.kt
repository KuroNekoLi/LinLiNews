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
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

/**
 * 閱讀理解選項。
 */
@Entity
@Table(
    name = "ai_comprehension_option",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ai_option_label", columnNames = ["mcq_id", "label"])
    ],
    indexes = [Index(name = "idx_ai_option_mcq", columnList = "mcq_id")]
)
class ArticleAiComprehensionOption(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mcq_id", nullable = false)
    var mcq: ArticleAiComprehensionMcq,

    @Column(name = "label", length = 4, nullable = false)
    var label: String,

    @Column(name = "text_from", columnDefinition = "LONGTEXT")
    var textFrom: String? = null,

    @Column(name = "text_to", columnDefinition = "LONGTEXT")
    var textTo: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
