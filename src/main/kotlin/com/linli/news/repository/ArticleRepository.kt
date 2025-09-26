package com.linli.news.repository

import com.linli.news.domain.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ArticleRepository : JpaRepository<Article, Long> {
    fun findByOriginalUrl(originalUrl: String): Article?
    fun findBySource_Section(section: String, pageable: Pageable): Page<Article>
    fun findBySource_Name(sourceName: String, pageable: Pageable): Page<Article>
    fun findBySource_NameAndSource_Section(sourceName: String, section: String, pageable: Pageable): Page<Article>

    // 驗證與統計用（快速查詢）
    fun countBySource_Name(sourceName: String): Long
    fun countBySource_NameAndSource_Section(sourceName: String, section: String): Long
    fun countBySource_NameAndCreatedAtAfter(sourceName: String, createdAt: OffsetDateTime): Long
    fun countBySource_NameAndSource_SectionAndCreatedAtAfter(
        sourceName: String,
        section: String,
        createdAt: OffsetDateTime
    ): Long
}
