package com.linli.news.repository

import com.linli.news.domain.ArticleAi
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ArticleAiRepository : JpaRepository<ArticleAi, Long>, PagingAndSortingRepository<ArticleAi, Long> {
    fun findByArticle_Id(articleId: Long): Optional<ArticleAi>
    fun findByArticle_Source_Name(name: String, pageable: Pageable): Page<ArticleAi>
    fun findByArticle_Source_Section(section: String, pageable: Pageable): Page<ArticleAi>
    fun findByArticle_Source_NameAndArticle_Source_Section(
        name: String,
        section: String,
        pageable: Pageable
    ): Page<ArticleAi>
}
