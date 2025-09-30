package com.linli.news.repository

import com.linli.news.domain.ArticleAi
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * AI 文章資料庫存取介面
 *
 * 提供 AI 處理文章的 CRUD 操作與查詢功能
 */
interface ArticleAiRepository : JpaRepository<ArticleAi, Long>, PagingAndSortingRepository<ArticleAi, Long> {
    /**
     * 根據文章 ID 查詢 AI 處理結果
     *
     * @param articleId 文章 ID
     * @return AI 處理結果（若存在）
     */
    fun findByArticle_Id(articleId: Long): Optional<ArticleAi>

    /**
     * 根據來源名稱查詢 AI 文章（分頁）
     *
     * @param name 來源名稱
     * @param pageable 分頁參數
     * @return AI 文章分頁資料
     */
    fun findByArticle_Source_Name(name: String, pageable: Pageable): Page<ArticleAi>

    /**
     * 根據區塊查詢 AI 文章（分頁）
     *
     * @param section 區塊名稱
     * @param pageable 分頁參數
     * @return AI 文章分頁資料
     */
    fun findByArticle_Source_Section(section: String, pageable: Pageable): Page<ArticleAi>

    /**
     * 根據來源名稱與區塊查詢 AI 文章（分頁）
     *
     * @param name 來源名稱
     * @param section 區塊名稱
     * @param pageable 分頁參數
     * @return AI 文章分頁資料
     */
    fun findByArticle_Source_NameAndArticle_Source_Section(
        name: String,
        section: String,
        pageable: Pageable
    ): Page<ArticleAi>
}
