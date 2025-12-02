package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleFeaturedCollectionAssignment
import com.linli.news.domain.FeaturedCollection
import com.linli.news.model.FeaturedCollectionDefinitionDto
import com.linli.news.repository.ArticleEmbeddingRepository
import com.linli.news.repository.ArticleFeaturedCollectionAssignmentRepository
import com.linli.news.repository.FeaturedCollectionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FeaturedCollectionDto(
    val id: Long,
    val collectionId: String,
    val title: String,
    val subtitle: String?,
    val colorStart: String?,
    val colorEnd: String?,
    val score: Double
)

@Service
class FeaturedCollectionService(
    private val featuredCollectionRepository: FeaturedCollectionRepository,
    private val articleFeaturedCollectionAssignmentRepository: ArticleFeaturedCollectionAssignmentRepository,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val articleRepository: com.linli.news.repository.ArticleRepository,
    private val openAiEmbeddingService: OpenAiEmbeddingService,
    @Value("\${openai.embedding-model:text-embedding-3-small}") private val embeddingModel: String,
    @Value("\${article.collection.max-collections:2}") private val maxCollections: Int,
    @Value("\${article.collection.min-score:0.6}") private val minScore: Double
) {
    private val logger = LoggerFactory.getLogger(FeaturedCollectionService::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * 為精選專題生成 embedding
     */
    @Transactional
    fun generateCollectionEmbedding(
        collectionId: String,
        title: String,
        subtitle: String?,
        description: String?,
        colorStart: String?,
        colorEnd: String?
    ): FeaturedCollection {
        val existing = featuredCollectionRepository.findByCollectionId(collectionId)
        val input = description ?: "$title $subtitle"
        val embedding = openAiEmbeddingService.embed(input, embeddingModel)
        val json = objectMapper.writeValueAsString(embedding)

        return if (existing != null) {
            existing.apply {
                this.title = title
                this.subtitle = subtitle
                this.description = description
                this.colorStart = colorStart
                this.colorEnd = colorEnd
                this.embeddingJson = json
                this.model = embeddingModel
                this.updatedAt = OffsetDateTime.now()
            }
            featuredCollectionRepository.save(existing)
        } else {
            featuredCollectionRepository.save(
                FeaturedCollection(
                    collectionId = collectionId,
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    colorStart = colorStart,
                    colorEnd = colorEnd,
                    embeddingJson = json,
                    model = embeddingModel
                )
            )
        }
    }

    /**
     * 為單篇文章配對精選專題
     */
    @Transactional
    fun assignCollectionsToArticle(article: Article) {
        val articleId = article.id ?: return
        val articleEmbedding = articleEmbeddingRepository.findByArticle_Id(articleId) ?: run {
            logger.warn("Article {} has no embedding, skip collection assignment", articleId)
            return
        }

        val articleVector = parseEmbedding(articleEmbedding.embeddingJson)
        val allCollections = featuredCollectionRepository.findAll()
        if (allCollections.isEmpty()) {
            logger.warn("No featured collections available")
            return
        }

        val scores = allCollections.mapNotNull { collection ->
            val collectionVector = parseEmbedding(collection.embeddingJson)
            val score = cosineSimilarity(articleVector, collectionVector)
            if (score >= minScore) collection to score else null
        }
            .sortedByDescending { it.second }
            .take(maxCollections)

        // 刪除舊的配對
        articleFeaturedCollectionAssignmentRepository.deleteByArticle_Id(articleId)

        // 新增新的配對
        scores.forEach { (collection, score) ->
            articleFeaturedCollectionAssignmentRepository.save(
                ArticleFeaturedCollectionAssignment(
                    article = article,
                    collection = collection,
                    score = score
                )
            )
        }

        logger.info("Assigned {} collections to article {}", scores.size, articleId)
    }

    /**
     * 取得文章的精選專題
     */
    fun getArticleCollections(articleId: Long): List<FeaturedCollectionDto> {
        return articleFeaturedCollectionAssignmentRepository.findByArticle_Id(articleId)
            .mapNotNull { assignment ->
                val collection = assignment.collection
                val collectionDbId = collection.id ?: return@mapNotNull null
                FeaturedCollectionDto(
                    id = collectionDbId,
                    collectionId = collection.collectionId,
                    title = collection.title,
                    subtitle = collection.subtitle,
                    colorStart = collection.colorStart,
                    colorEnd = collection.colorEnd,
                    score = assignment.score
                )
            }
            .sortedByDescending { it.score }
    }

    /**
     * 根據專題 ID 列表直接配對文章（由 AI 生成時調用）
     * @param articleId 文章 ID
     * @param collectionIds AI 回傳的專題 ID 列表
     */
    @Transactional
    fun assignCollectionsById(articleId: Long, collectionIds: List<String>) {
        val article = articleRepository.findById(articleId).orElse(null)
        if (article == null) {
            logger.warn("Article {} not found, cannot assign collections", articleId)
            return
        }

        // 刪除舊的配對
        articleFeaturedCollectionAssignmentRepository.deleteByArticle_Id(articleId)

        // 根據 collectionId 查找專題並配對
        var assigned = 0
        collectionIds.forEach { collectionId ->
            val collection = featuredCollectionRepository.findByCollectionId(collectionId)
            if (collection != null) {
                articleFeaturedCollectionAssignmentRepository.save(
                    ArticleFeaturedCollectionAssignment(
                        article = article,
                        collection = collection,
                        score = 1.0 // AI 判斷的專題，給予滿分
                    )
                )
                assigned++
            } else {
                logger.warn("Collection '{}' not found in database", collectionId)
            }
        }

        logger.info("Assigned {} AI-generated collections to article {}", assigned, articleId)
    }

    /**
     * 批次為所有文章配對精選專題
     */
    @Transactional
    fun assignCollectionsToAllArticles() {
        val allEmbeddings = articleEmbeddingRepository.findAll()
        var processed = 0
        allEmbeddings.forEach { embedding ->
            try {
                assignCollectionsToArticle(embedding.article)
                processed++
            } catch (ex: Exception) {
                logger.error("Failed to assign collections to article {}: {}", embedding.article.id, ex.message)
            }
        }
        logger.info("Collection assignment batch completed. Processed {} articles", processed)
    }

    /**
     * 取得所有可用的精選專題
     */
    fun getAllCollections(): List<FeaturedCollectionDefinitionDto> {
        return featuredCollectionRepository.findAll()
            .map { collection ->
                FeaturedCollectionDefinitionDto(
                    id = collection.id ?: 0,
                    collectionId = collection.collectionId,
                    title = collection.title,
                    subtitle = collection.subtitle,
                    description = collection.description,
                    colorStart = collection.colorStart,
                    colorEnd = collection.colorEnd
                )
            }
            .sortedBy { it.collectionId }
    }

    private fun parseEmbedding(json: String): DoubleArray {
        return objectMapper.readValue(json, DoubleArray::class.java)
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val length = min(a.size, b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until length) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(max(normA, 1e-12)) * sqrt(max(normB, 1e-12))
        return if (denominator == 0.0) 0.0 else dot / denominator
    }
}
