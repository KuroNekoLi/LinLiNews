package com.linli.news.repository

import com.linli.news.domain.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ArticleRepository : JpaRepository<Article, Long> {
    fun findByOriginalUrl(originalUrl: String): Article?
    fun findBySource_Section(section: String, pageable: Pageable): Page<Article>
    fun findBySource_Name(sourceName: String, pageable: Pageable): Page<Article>
    fun findBySource_NameAndSource_Section(sourceName: String, section: String, pageable: Pageable): Page<Article>
}
