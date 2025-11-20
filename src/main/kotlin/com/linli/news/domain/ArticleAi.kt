package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * AI 處理文章實體
 *
 * 儲存文章經 AI 處理後的結果，包含翻譯、清理後內容與教學解說
 *
 * @property article 關聯的原始文章（一對一）
 * @property title 文章標題（AI 可能的調整版本）
 * @property views 依語言與難度切分的視圖內容
 * @property id AI 文章唯一識別碼
 * @property createdAt 建立時間
 * @property updatedAt 更新時間
 */
@Entity
@Table(
    name = "article_ai",
    uniqueConstraints = [UniqueConstraint(name = "uk_article_ai_article", columnNames = ["article_id"])],
    indexes = [Index(name = "idx_article_ai_article_id", columnList = "article_id")]
)
class ArticleAi(
    /** 關聯的原始文章（一對一，延遲載入） */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    var article: Article,

    /** 文章標題（最長 512 字元） */
    @Column(name = "title", length = 512)
    var title: String? = null,

    /** 依語言與難度儲存的視圖內容 */
    @OneToMany(mappedBy = "articleAi", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var views: MutableSet<ArticleAiView> = mutableSetOf()
) {
    /** AI 文章唯一識別碼（自動遞增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 建立時間（不可空） */
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    /** 更新時間（不可空） */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
