package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleTagAssignment
import com.linli.news.domain.ArticleTagDefinition
import com.linli.news.repository.ArticleEmbeddingRepository
import com.linli.news.repository.ArticleTagAssignmentRepository
import com.linli.news.repository.ArticleTagDefinitionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ArticleTagDto(
    val id: Long,
    val name: String,
    val description: String?,
    val score: Double
)

@Service
class ArticleTagService(
    private val articleTagDefinitionRepository: ArticleTagDefinitionRepository,
    private val articleTagAssignmentRepository: ArticleTagAssignmentRepository,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val articleRepository: com.linli.news.repository.ArticleRepository,
    private val openAiEmbeddingService: OpenAiEmbeddingService,
    @Value("\${openai.embedding-model:text-embedding-3-small}") private val embeddingModel: String,
    @Value("\${article.tag.max-tags:5}") private val maxTags: Int,
    @Value("\${article.tag.min-score:0.6}") private val minScore: Double
) {
    private val logger = LoggerFactory.getLogger(ArticleTagService::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * 為 tag 定義生成 embedding
     */
    @Transactional
    fun generateTagEmbedding(name: String, description: String?): ArticleTagDefinition {
        val existing = articleTagDefinitionRepository.findByName(name)
        val input = if (description.isNullOrBlank()) name else "$name | $description"
        val embedding = openAiEmbeddingService.embed(input, embeddingModel)
        val json = objectMapper.writeValueAsString(embedding)

        return if (existing != null) {
            existing.apply {
                this.description = description
                this.embeddingJson = json
                this.model = embeddingModel
                this.updatedAt = OffsetDateTime.now()
            }
            articleTagDefinitionRepository.save(existing)
        } else {
            articleTagDefinitionRepository.save(
                ArticleTagDefinition(
                    name = name,
                    description = description,
                    embeddingJson = json,
                    model = embeddingModel
                )
            )
        }
    }

    /**
     * 為單篇文章配對 tags
     */
    @Transactional
    fun assignTagsToArticle(article: Article) {
        val articleId = article.id ?: return
        val articleEmbedding = articleEmbeddingRepository.findByArticle_Id(articleId) ?: run {
            logger.warn("Article {} has no embedding, skip tag assignment", articleId)
            return
        }

        val articleVector = parseEmbedding(articleEmbedding.embeddingJson)
        val allTags = articleTagDefinitionRepository.findAll()
        if (allTags.isEmpty()) {
            logger.warn("No tag definitions available")
            return
        }

        val scores = allTags.mapNotNull { tag ->
            val tagVector = parseEmbedding(tag.embeddingJson)
            val score = cosineSimilarity(articleVector, tagVector)
            if (score >= minScore) tag to score else null
        }
            .sortedByDescending { it.second }
            .take(maxTags)

        // 刪除舊的配對
        articleTagAssignmentRepository.deleteByArticle_Id(articleId)

        // 新增新的配對
        scores.forEach { (tag, score) ->
            articleTagAssignmentRepository.save(
                ArticleTagAssignment(
                    article = article,
                    tag = tag,
                    score = score
                )
            )
        }

        logger.info("Assigned {} tags to article {}", scores.size, articleId)
    }

    /**
     * 取得文章的 tags
     */
    fun getArticleTags(articleId: Long): List<ArticleTagDto> {
        return articleTagAssignmentRepository.findByArticle_Id(articleId)
            .mapNotNull { assignment ->
                val tag = assignment.tag
                val tagId = tag.id ?: return@mapNotNull null
                ArticleTagDto(
                    id = tagId,
                    name = tag.name,
                    description = tag.description,
                    score = assignment.score
                )
            }
            .sortedByDescending { it.score }
    }

    /**
     * 根據 tag 名稱列表直接配對文章（由 AI 生成時調用）
     * @param articleId 文章 ID
     * @param tagNames AI 回傳的 tag 名稱列表
     */
    @Transactional
    fun assignTagsByName(articleId: Long, tagNames: List<String>) {
        val article = articleRepository.findById(articleId).orElse(null)
        if (article == null) {
            logger.warn("Article {} not found, cannot assign tags", articleId)
            return
        }

        // 刪除舊的配對
        articleTagAssignmentRepository.deleteByArticle_Id(articleId)

        // 根據名稱查找 tag 定義並配對
        var assigned = 0
        tagNames.forEach { tagName ->
            val tag = articleTagDefinitionRepository.findByName(tagName)
            if (tag != null) {
                articleTagAssignmentRepository.save(
                    ArticleTagAssignment(
                        article = article,
                        tag = tag,
                        score = 1.0 // AI 判斷的 tag，給予滿分
                    )
                )
                assigned++
            } else {
                logger.warn("Tag '{}' not found in database", tagName)
            }
        }

        logger.info("Assigned {} AI-generated tags to article {}", assigned, articleId)
    }

    /**
     * 批次為所有文章配對 tags
     */
    @Transactional
    fun assignTagsToAllArticles() {
        val allEmbeddings = articleEmbeddingRepository.findAll()
        var processed = 0
        allEmbeddings.forEach { embedding ->
            try {
                assignTagsToArticle(embedding.article)
                processed++
            } catch (ex: Exception) {
                logger.error("Failed to assign tags to article {}: {}", embedding.article.id, ex.message)
            }
        }
        logger.info("Tag assignment batch completed. Processed {} articles", processed)
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
