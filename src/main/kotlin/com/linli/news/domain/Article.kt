package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * 文章實體
 *
 * 儲存從 RSS 來源匯入的新聞文章資料
 *
 * @property source 文章來源
 * @property title 文章標題
 * @property excerpt 文章摘要
 * @property author 作者
 * @property language 語言代碼
 * @property domain 來源網域
 * @property originalUrl 原始文章網址（唯一）
 * @property effectiveUrl 有效網址（重新導向後的實際網址）
 * @property contentHtml 文章 HTML 內容
 * @property wordCount 字數統計
 * @property publishedAt 發布時間
 * @property id 文章唯一識別碼
 * @property createdAt 建立時間
 * @property updatedAt 更新時間
 */
@Entity
@Table(
    name = "article",
    indexes = [
        Index(name = "idx_article_published_at", columnList = "published_at"),
        Index(name = "idx_article_source_id", columnList = "source_id")
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_article_original_url", columnNames = ["original_url"])]
)
class Article(
    /** 文章來源（多對一關聯） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    var source: ArticleSource,

    /** 文章標題（最長 512 字元） */
    @Column(name = "title", length = 512)
    var title: String? = null,

    /** 文章摘要（TEXT 欄位） */
    @Column(name = "excerpt", columnDefinition = "TEXT")
    var excerpt: String? = null,

    /** 作者姓名（最長 255 字元） */
    @Column(name = "author", length = 255)
    var author: String? = null,

    /** 語言代碼（最長 32 字元） */
    @Column(name = "language", length = 32)
    var language: String? = null,

    /** 來源網域（最長 255 字元） */
    @Column(name = "domain", length = 255)
    var domain: String? = null,

    /** 原始文章網址（唯一，不可空，最長 1024 字元） */
    @Column(name = "original_url", nullable = false, length = 1024)
    var originalUrl: String,

    /** 有效網址（重新導向後，最長 1024 字元） */
    @Column(name = "effective_url", length = 1024)
    var effectiveUrl: String? = null,

    /** 文章 HTML 內容（LONGTEXT 欄位） */
    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    var contentHtml: String? = null,

    /** 字數統計 */
    @Column(name = "word_count")
    var wordCount: Int? = null,

    /** 發布時間（帶時區） */
    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null
) {
    /** 文章唯一識別碼（自動遞增） */
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
