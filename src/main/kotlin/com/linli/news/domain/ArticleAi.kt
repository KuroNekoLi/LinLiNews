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
 * @property titleZh 中文標題（AI 翻譯）
 * @property optimizedHtml 最佳化後的英文 HTML 內容
 * @property optimizedZhHtml 最佳化後的繁體中文 HTML 內容
 * @property cleanedText 清理後的純文字內容（英文）
 * @property cleanedTextZh 清理後的純文字內容（繁體中文翻譯）
 * @property summaryEn AI 產生的英文摘要
 * @property summaryZh AI 產生的中文摘要
 * @property explanation 教學解說（JSON 格式，包含單字、文法、句型等）
 * @property wordCount 文章字數
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

    /** 中文標題（AI 翻譯，最長 512 字元） */
    @Column(name = "title_zh", length = 512)
    var titleZh: String? = null,

    /** 最佳化後的 HTML 內容（英文，LONGTEXT 欄位） */
    @Column(name = "optimized_html", columnDefinition = "LONGTEXT")
    var optimizedHtml: String? = null,

    /** 清理後的 HTML 內容（繁體中文，LONGTEXT 欄位） */
    @Column(name = "optimized_zh_html", columnDefinition = "LONGTEXT")
    var optimizedZhHtml: String? = null,

    /** 清理後的純文字內容（英文，LONGTEXT 欄位） */
    @Column(name = "cleaned_text", columnDefinition = "LONGTEXT")
    var cleanedText: String? = null,

    /** 清理後的純文字內容（繁體中文翻譯，LONGTEXT 欄位） */
    @Column(name = "cleaned_text_zh", columnDefinition = "LONGTEXT")
    var cleanedTextZh: String? = null,

    /** 英文摘要（AI 產生，LONGTEXT 欄位） */
    @Column(name = "summary_en", columnDefinition = "LONGTEXT")
    var summaryEn: String? = null,

    /** 中文摘要（AI 產生，LONGTEXT 欄位） */
    @Column(name = "summary_zh", columnDefinition = "LONGTEXT")
    var summaryZh: String? = null,

    /** 教學解說（JSON 格式，包含單字、文法、句型、片語、閱讀測驗等） */
    @Column(name = "explanation", columnDefinition = "JSON")
    var explanation: String? = null,

    /** 文章字數 */
    @Column(name = "word_count")
    var wordCount: Int = 0
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
