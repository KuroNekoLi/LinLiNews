package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleEmbedding
import com.linli.news.repository.ArticleEmbeddingRepository
import com.linli.news.repository.ArticleRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RelatedArticleDto(
    val id: Long,
    val title: String?,
    val excerpt: String?,
    val score: Double
)

@Service
class ArticleEmbeddingService(
    private val articleRepository: ArticleRepository,
    private val articleEmbeddingRepository: ArticleEmbeddingRepository,
    private val openAiEmbeddingService: OpenAiEmbeddingService,
    @Value("\${openai.embedding-model:text-embedding-3-small}") private val embeddingModel: String,
    @Value("\${article.embedding.max-chars:4000}") private val maxChars: Int
) {
    private val logger = LoggerFactory.getLogger(ArticleEmbeddingService::class.java)
    private val objectMapper = ObjectMapper()
    private val htmlTagRegex = Regex("<[^>]*>")

    fun rebuildMissingEmbeddings(limit: Int = Int.MAX_VALUE, force: Boolean = false) {
        val allArticles = articleRepository.findAll()
        val existingIds = articleEmbeddingRepository.findAll().mapNotNull { it.article.id }.toSet()
        var processed = 0
        for (article in allArticles) {
            val articleId = article.id ?: continue
            if (!force && existingIds.contains(articleId)) {
                continue
            }
            try {
                generateEmbeddingForArticle(article, force = true)
                processed++
            } catch (ex: Exception) {
                logger.error("Failed to generate embedding for article ${articleId}: ${ex.message}", ex)
            }
            if (processed >= limit) {
                break
            }
        }
        logger.info("Embedding batch completed. Generated {} embeddings (force={})", processed, force)
    }

    @Transactional
    fun generateEmbeddingForArticle(article: Article, force: Boolean = false): ArticleEmbedding? {
        val articleId = article.id ?: return null
        val existing = articleEmbeddingRepository.findByArticle_Id(articleId)
        if (existing != null && !force) {
            return existing
        }

        val input = buildEmbeddingInput(article)
        if (input.isBlank()) {
            logger.warn("Skip embedding for article {} because content is blank", articleId)
            return existing
        }

        val embedding = openAiEmbeddingService.embed(input, embeddingModel)
        val json = objectMapper.writeValueAsString(embedding)
        val entity = existing?.apply {
            this.embeddingJson = json
            this.model = embeddingModel
            this.updatedAt = OffsetDateTime.now()
        } ?: ArticleEmbedding(article = article, model = embeddingModel, embeddingJson = json)

        return articleEmbeddingRepository.save(entity)
    }

    fun findRelatedArticles(articleId: Long, limit: Int = 5, minScore: Double = 0.6): List<RelatedArticleDto> {
        val targetEmbedding = articleEmbeddingRepository.findByArticle_Id(articleId)
            ?: return emptyList()
        val targetVector = parseEmbedding(targetEmbedding.embeddingJson)
        val others = articleEmbeddingRepository.findAllExcludingArticle(articleId)
        if (others.isEmpty()) {
            return emptyList()
        }

        val candidates = others.mapNotNull { embedding ->
            val otherId = embedding.article.id ?: return@mapNotNull null
            val vector = parseEmbedding(embedding.embeddingJson)
            val score = cosineSimilarity(targetVector, vector)
            otherId to score
        }

        val filtered = candidates
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .take(limit)

        if (filtered.isEmpty()) {
            return emptyList()
        }

        val articleMap = articleRepository.findAllById(filtered.map { it.first })
            .associateBy { it.id!! }

        return filtered.mapNotNull { (id, score) ->
            val article = articleMap[id]
            article?.let {
                RelatedArticleDto(
                    id = id,
                    title = it.title,
                    excerpt = it.excerpt,
                    score = score
                )
            }
        }
    }

    private fun buildEmbeddingInput(article: Article): String {
        val builder = StringBuilder()
        article.title?.takeIf { it.isNotBlank() }?.let {
            builder.append(it.trim()).append('\n')
        }
        article.excerpt?.takeIf { it.isNotBlank() }?.let {
            builder.append(it.trim()).append('\n')
        }
        val plainText = stripHtml(article.contentHtml ?: "")
        if (plainText.isNotBlank()) {
            builder.append(plainText)
        }
        val result = builder.toString().trim()
        return if (result.length <= maxChars) result else result.substring(0, maxChars)
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return htmlTagRegex.replace(html, " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
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
