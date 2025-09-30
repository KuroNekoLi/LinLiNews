package com.linli.news.repository

import com.linli.news.domain.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

/**
 * 文章資料庫存取介面
 *
 * 提供文章的 CRUD 操作與查詢功能
 */
@Repository
interface ArticleRepository : JpaRepository<Article, Long> {
    /**
     * 根據原始網址查詢文章（用於去重）
     *
     * @param originalUrl 原始文章網址
     * @return 文章實體（若存在）
     */
    fun findByOriginalUrl(originalUrl: String): Article?

    /**
     * 根據區塊查詢文章（分頁）
     *
     * @param section 區塊名稱
     * @param pageable 分頁參數
     * @return 文章分頁資料
     */
    fun findBySource_Section(section: String, pageable: Pageable): Page<Article>

    /**
     * 根據來源名稱查詢文章（分頁）
     *
     * @param sourceName 來源名稱
     * @param pageable 分頁參數
     * @return 文章分頁資料
     */
    fun findBySource_Name(sourceName: String, pageable: Pageable): Page<Article>

    /**
     * 根據來源名稱與區塊查詢文章（分頁）
     *
     * @param sourceName 來源名稱
     * @param section 區塊名稱
     * @param pageable 分頁參數
     * @return 文章分頁資料
     */
    fun findBySource_NameAndSource_Section(sourceName: String, section: String, pageable: Pageable): Page<Article>

    // 驗證與統計用（快速查詢）

    /**
     * 統計指定來源的文章總數
     *
     * @param sourceName 來源名稱
     * @return 文章總數
     */
    fun countBySource_Name(sourceName: String): Long

    /**
     * 統計指定來源與區塊的文章總數
     *
     * @param sourceName 來源名稱
     * @param section 區塊名稱
     * @return 文章總數
     */
    fun countBySource_NameAndSource_Section(sourceName: String, section: String): Long

    /**
     * 統計指定來源在特定時間後建立的文章數
     *
     * @param sourceName 來源名稱
     * @param createdAt 建立時間門檻
     * @return 文章數量
     */
    fun countBySource_NameAndCreatedAtAfter(sourceName: String, createdAt: OffsetDateTime): Long

    /**
     * 統計指定來源與區塊在特定時間後建立的文章數
     *
     * @param sourceName 來源名稱
     * @param section 區塊名稱
     * @param createdAt 建立時間門檻
     * @return 文章數量
     */
    fun countBySource_NameAndSource_SectionAndCreatedAtAfter(
        sourceName: String,
        section: String,
        createdAt: OffsetDateTime
    ): Long
}
