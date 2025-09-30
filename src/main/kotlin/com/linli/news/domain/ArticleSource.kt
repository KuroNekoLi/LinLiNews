package com.linli.news.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * 文章來源實體
 *
 * 儲存文章來源的 RSS 資訊與狀態
 *
 * @property name 來源名稱（例如 BBC、CNN）
 * @property rssUrl RSS 來源網址（唯一）
 * @property section 來源區塊（例如 news、world、technology）
 * @property enabled 是否啟用此來源
 * @property id 來源唯一識別碼
 * @property createdAt 建立時間
 * @property updatedAt 更新時間
 */
@Entity
@Table(
    name = "article_source",
    uniqueConstraints = [UniqueConstraint(name = "uk_article_source_rss", columnNames = ["rss_url"])]
)
class ArticleSource(
    /** 來源名稱（不可空，最長 128 字元） */
    @Column(name = "name", nullable = false, length = 128)
    var name: String,

    /** RSS 來源網址（唯一，不可空，最長 1024 字元） */
    @Column(name = "rss_url", nullable = false, length = 1024)
    var rssUrl: String,

    /** 來源區塊（例如 news、world、technology，最長 128 字元） */
    @Column(name = "section", length = 128)
    var section: String? = null,

    /** 是否啟用此來源（預設為啟用） */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true
) {
    /** 來源唯一識別碼（自動遞增） */
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
