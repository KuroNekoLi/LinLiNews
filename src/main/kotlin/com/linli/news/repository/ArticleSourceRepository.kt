package com.linli.news.repository

import com.linli.news.domain.ArticleSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ArticleSourceRepository : JpaRepository<ArticleSource, Long> {
    fun findByRssUrl(rssUrl: String): ArticleSource?
}
