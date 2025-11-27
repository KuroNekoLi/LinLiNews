package com.linli.news.repository

import com.linli.news.domain.ArticleAi
import com.linli.news.model.DifficultyLevel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.*

/**
 * AI 文章資料庫存取介面
 *
 * 提供 AI 處理文章的 CRUD 操作與查詢功能
 *
 * 注意：article_id 在 article_ai 表中是唯一的（UNIQUE constraint），
 * 因此 findByArticle_Id 只會返回一筆記錄
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

    @Query(
        """
        SELECT DISTINCT ai FROM ArticleAi ai
        JOIN ai.views v
        WHERE LOWER(v.fromLanguage) = LOWER(:fromLanguage)
          AND LOWER(v.toLanguage) = LOWER(:toLanguage)
          AND v.difficulty IN :difficulties
        """
    )
    fun findByViewFilters(
        fromLanguage: String,
        toLanguage: String,
        difficulties: List<DifficultyLevel>,
        pageable: Pageable
    ): Page<ArticleAi>
}
