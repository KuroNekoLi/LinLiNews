package com.linli.news.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "embedding.batch", name = ["enabled"], havingValue = "true")
@Order(2)
class ArticleEmbeddingBatchRunner(
    private val articleEmbeddingService: ArticleEmbeddingService,
    private val articleTagService: ArticleTagService,
    private val featuredCollectionService: FeaturedCollectionService,
    @Value("\${embedding.batch.limit:0}") private val limit: Int,
    @Value("\${embedding.batch.force:false}") private val force: Boolean,
    @Value("\${embedding.batch.assign-tags:true}") private val assignTags: Boolean,
    @Value("\${embedding.batch.assign-collections:true}") private val assignCollections: Boolean
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(ArticleEmbeddingBatchRunner::class.java)

    override fun run(vararg args: String?) {
        logger.info(
            "Starting embedding batch runner (limit={}, force={}, assignTags={}, assignCollections={})",
            limit, force, assignTags, assignCollections
        )

        // 第一步：生成 embeddings
        articleEmbeddingService.rebuildMissingEmbeddings(limit = if (limit > 0) limit else Int.MAX_VALUE, force = force)

        // 第二步：配對 tags
        if (assignTags) {
            logger.info("Starting tag assignment for all articles...")
            articleTagService.assignTagsToAllArticles()
        }

        // 第三步：配對精選專題
        if (assignCollections) {
            logger.info("Starting collection assignment for all articles...")
            featuredCollectionService.assignCollectionsToAllArticles()
        }

        logger.info("Embedding batch runner finished")
    }
}
