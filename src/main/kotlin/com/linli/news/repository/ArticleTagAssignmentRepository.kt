package com.linli.news.repository

import com.linli.news.domain.ArticleTagAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ArticleTagAssignmentRepository : JpaRepository<ArticleTagAssignment, Long> {
    fun findByArticle_Id(articleId: Long): List<ArticleTagAssignment>

    @Modifying
    @Query("DELETE FROM ArticleTagAssignment a WHERE a.article.id = :articleId")
    fun deleteByArticle_Id(articleId: Long)
}
