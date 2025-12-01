package com.linli.news.repository

import com.linli.news.domain.FeaturedCollection
import org.springframework.data.jpa.repository.JpaRepository

interface FeaturedCollectionRepository : JpaRepository<FeaturedCollection, Long> {
    fun findByCollectionId(collectionId: String): FeaturedCollection?
}
