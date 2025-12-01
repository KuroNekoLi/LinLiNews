package com.linli.news.repository

import com.linli.news.domain.ArticleFeaturedCollectionAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ArticleFeaturedCollectionAssignmentRepository : JpaRepository<ArticleFeaturedCollectionAssignment, Long> {
    fun findByArticle_Id(articleId: Long): List<ArticleFeaturedCollectionAssignment>

    @Modifying
    @Query("DELETE FROM ArticleFeaturedCollectionAssignment a WHERE a.article.id = :articleId")
    fun deleteByArticle_Id(articleId: Long)

    fun findByCollection_Id(collectionId: Long): List<ArticleFeaturedCollectionAssignment>
}
