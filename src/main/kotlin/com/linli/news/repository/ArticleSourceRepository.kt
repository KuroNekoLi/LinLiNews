package com.linli.news.repository

import com.linli.news.domain.ArticleSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 文章來源資料庫存取介面
 *
 * 提供文章來源的 CRUD 操作與查詢功能
 */
@Repository
interface ArticleSourceRepository : JpaRepository<ArticleSource, Long> {
    /**
     * 根據 RSS 網址查詢文章來源
     *
     * @param rssUrl RSS 來源網址
     * @return 文章來源實體（若存在）
     */
    fun findByRssUrl(rssUrl: String): ArticleSource?
}
