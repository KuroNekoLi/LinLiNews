package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

data class TagDefinitionJson(
    val name: String,
    val description: String?
)

data class FeaturedCollectionJson(
    val id: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val colorStart: String?,
    val colorEnd: String?
)

/**
 * 初次啟動時載入 tag 定義與精選專題定義
 */
@Component
@ConditionalOnProperty(
    prefix = "bootstrap",
    name = ["tags-and-collections"],
    havingValue = "true",
    matchIfMissing = false
)
@Order(1)
class TagAndCollectionBootstrapRunner(
    private val articleTagService: ArticleTagService,
    private val featuredCollectionService: FeaturedCollectionService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(TagAndCollectionBootstrapRunner::class.java)
    private val objectMapper = jacksonObjectMapper()

    override fun run(vararg args: String?) {
        logger.info("Starting tag and collection bootstrap...")

        try {
            loadTags()
        } catch (ex: Exception) {
            logger.error("Failed to load tags: ${ex.message}", ex)
        }

        try {
            loadCollections()
        } catch (ex: Exception) {
            logger.error("Failed to load collections: ${ex.message}", ex)
        }

        logger.info("Tag and collection bootstrap completed")
    }

    private fun loadTags() {
        val resource = ClassPathResource("tag-definitions.json")
        if (!resource.exists()) {
            logger.warn("tag-definitions.json not found, skipping tag bootstrap")
            return
        }

        val tags: List<TagDefinitionJson> = objectMapper.readValue(resource.inputStream)
        logger.info("Loading {} tags from tag-definitions.json", tags.size)

        var loaded = 0
        tags.forEach { tagDef ->
            try {
                articleTagService.generateTagEmbedding(tagDef.name, tagDef.description)
                loaded++
                if (loaded % 10 == 0) {
                    logger.info("Loaded {} / {} tags", loaded, tags.size)
                }
            } catch (ex: Exception) {
                logger.error("Failed to load tag '{}': {}", tagDef.name, ex.message)
            }
        }

        logger.info("Tag bootstrap completed: {} / {} tags loaded", loaded, tags.size)
    }

    private fun loadCollections() {
        val resource = ClassPathResource("featured-collections.json")
        if (!resource.exists()) {
            logger.warn("featured-collections.json not found, skipping collection bootstrap")
            return
        }

        val collections: List<FeaturedCollectionJson> = objectMapper.readValue(resource.inputStream)
        logger.info("Loading {} collections from featured-collections.json", collections.size)

        var loaded = 0
        collections.forEach { collDef ->
            try {
                featuredCollectionService.generateCollectionEmbedding(
                    collectionId = collDef.id,
                    title = collDef.title,
                    subtitle = collDef.subtitle,
                    description = collDef.description,
                    colorStart = collDef.colorStart,
                    colorEnd = collDef.colorEnd
                )
                loaded++
            } catch (ex: Exception) {
                logger.error("Failed to load collection '{}': {}", collDef.id, ex.message)
            }
        }

        logger.info("Collection bootstrap completed: {} / {} collections loaded", loaded, collections.size)
    }
}
