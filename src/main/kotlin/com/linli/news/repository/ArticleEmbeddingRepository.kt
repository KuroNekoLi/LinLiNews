package com.linli.news.repository

import com.linli.news.domain.ArticleEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ArticleEmbeddingRepository : JpaRepository<ArticleEmbedding, Long> {
    fun findByArticle_Id(articleId: Long): ArticleEmbedding?

    @Query("SELECT ae FROM ArticleEmbedding ae WHERE ae.article.id <> :articleId")
    fun findAllExcludingArticle(articleId: Long): List<ArticleEmbedding>
}
