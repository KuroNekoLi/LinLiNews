package com.linli.news.repository

import com.linli.news.domain.ArticleTagDefinition
import org.springframework.data.jpa.repository.JpaRepository

interface ArticleTagDefinitionRepository : JpaRepository<ArticleTagDefinition, Long> {
    fun findByName(name: String): ArticleTagDefinition?
}
