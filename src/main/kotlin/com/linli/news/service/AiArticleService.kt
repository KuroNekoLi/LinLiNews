package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.ArticleAi
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.Explanation
import com.linli.news.repository.ArticleAiRepository
import com.linli.news.repository.ArticleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.util.regex.Pattern
import org.slf4j.LoggerFactory

@Service
class AiArticleService(
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val aiProcessor: AiArticleProcessorService
) {

    private val logger = LoggerFactory.getLogger(AiArticleService::class.java)
    private val objectMapper = ObjectMapper()
    private val htmlTagPattern: Pattern = Pattern.compile("<[^>]*>")

    fun listProcessed(page: Int, size: Int): Page<ArticleAi> {
        return articleAiRepository.findAll(PageRequest.of(page, size))
    }

    @Transactional
    fun getOrProcess(articleId: Long): ArticleAi {
        val existing = articleAiRepository.findByArticle_Id(articleId)
        if (existing.isPresent) return existing.get()

        val article = articleRepository.findById(articleId).orElseThrow()
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }

        // 使用純文字內容而非 HTML，大幅減少 token
        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        val json: JsonNode = try {
            aiProcessor.process(article.title, textContent)
        } catch (e: AiQuotaException) {
            // bubble up as 429 to controller layer
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val primaryExplanationNode = json.path("explanation")
        val hasPrimaryExplanation =
            !primaryExplanationNode.isMissingNode && !primaryExplanationNode.isNull && primaryExplanationNode.isObject
        val explanationJson = if (hasPrimaryExplanation) primaryExplanationNode.toString() else "{}"

        val entity = ArticleAi(
            article = article,
            title = json.path("title").asText(null),
            titleZh = json.path("title_zh").asText(null),
            cleanedHtml = null, // 不再產生 cleaned_html
            cleanedText = json.path("cleaned_text").asText(null),
            cleanedTextZh = json.path("cleaned_text_zh").asText(null),
            explanation = explanationJson
        )
        return articleAiRepository.save(entity)
    }

    fun random(count: Int, source: String?, section: String?): Pair<List<ArticleAi>, Int> {
        val page = PageRequest.of(0, 10000)
        val pageData = when {
            !source.isNullOrBlank() && !section.isNullOrBlank() ->
                articleAiRepository.findByArticle_Source_NameAndArticle_Source_Section(source, section, page)

            !source.isNullOrBlank() ->
                articleAiRepository.findByArticle_Source_Name(source, page)

            !section.isNullOrBlank() ->
                articleAiRepository.findByArticle_Source_Section(section, page)

            else -> articleAiRepository.findAll(page)
        }
        val all = pageData.content
        if (all.isEmpty()) return Pair(emptyList(), 0)
        val sampled = if (all.size <= count) all else all.shuffled().take(count)
        return Pair(sampled, all.size)
    }

    fun getOrProcessDto(articleId: Long): AiArticleDetail {
        val aiArticle = getOrProcess(articleId)
        return toDto(aiArticle.article, aiArticle)
    }

    @Transactional
    fun processAndOverwrite(articleId: Long): ArticleAi {
        val article = articleRepository.findById(articleId).orElseThrow()
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }

        // 使用純文字內容而非 HTML，大幅減少 token
        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        val json: JsonNode = try {
            aiProcessor.process(article.title, textContent)
        } catch (e: AiQuotaException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val entity = articleAiRepository.findByArticle_Id(articleId).orElse(null)
            ?: ArticleAi(article = article)

        entity.title = json.path("title").asText(null)
        entity.titleZh = json.path("title_zh").asText(null)
        entity.cleanedHtml = null // 不再產生 cleaned_html
        entity.cleanedText = json.path("cleaned_text").asText(null)
        entity.cleanedTextZh = json.path("cleaned_text_zh").asText(null)
        val expNode = json.path("explanation")
        entity.explanation = if (!expNode.isMissingNode && !expNode.isNull && expNode.isObject) {
            expNode.toString()
        } else {
            "{}"
        }

        return articleAiRepository.save(entity)
    }

    fun processAndOverwriteDto(articleId: Long): AiArticleDetail {
        val aiArticle = processAndOverwrite(articleId)
        return toDto(aiArticle.article, aiArticle)
    }

    private fun toDto(article: com.linli.news.domain.Article, aiArticle: ArticleAi): AiArticleDetail {
        val explanationJson = aiArticle.explanation
        val explanation = try {
            if (explanationJson.isNullOrBlank()) {
                logger.warn("Article ${article.id} has null or blank explanation JSON")
                null
            } else {
                logger.debug("Parsing explanation JSON for article ${article.id}: ${explanationJson.take(200)}...")

                // 創建一個更寬容的 ObjectMapper 配置
                val tolerantMapper = objectMapper.copy()
                tolerantMapper.configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )
                tolerantMapper.configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
                    true
                )

                val parsedExplanation = tolerantMapper.readValue(explanationJson, Explanation::class.java)
                logger.info("Successfully parsed explanation for article ${article.id}: vocab=${parsedExplanation.vocabulary?.size}, grammar=${parsedExplanation.grammar?.size}, mcq=${parsedExplanation.comprehensionMcq?.size}")
                parsedExplanation
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse explanation JSON for article ${article.id}: ${e.message}")
            logger.debug("Raw explanation JSON: $explanationJson")

            // 檢查 JSON 內容來提供更具體的錯誤信息
            if (explanationJson?.contains("\"explanation\"") == true) {
                if (!explanationJson.trim().endsWith("}")) {
                    logger.warn("Article ${article.id} explanation JSON appears to be truncated")
                } else {
                    logger.warn("Article ${article.id} explanation JSON structure is invalid")
                }
            }

            // 嘗試手動解析 JSON 樹來診斷問題
            try {
                val jsonNode = objectMapper.readTree(explanationJson)
                logger.debug("JSON fields present: ${jsonNode.fieldNames().asSequence().toList()}")
                if (jsonNode.has("comprehension_mcq")) {
                    val mcqArray = jsonNode.get("comprehension_mcq")
                    if (mcqArray.isArray && mcqArray.size() > 0) {
                        val firstMcq = mcqArray.get(0)
                        if (firstMcq.has("options")) {
                            val options = firstMcq.get("options")
                            logger.debug("MCQ options fields: ${options.fieldNames().asSequence().toList()}")
                        }
                    }
                }
            } catch (treeException: Exception) {
                logger.debug("Could not parse JSON tree for diagnosis: ${treeException.message}")
            }

            null
        }

        return AiArticleDetail(
            // 原文章欄位
            id = article.id!!,
            sourceName = article.source.name,
            section = article.source.section,
            title = article.title,
            publishedAt = article.publishedAt?.toString(),
            originalUrl = article.originalUrl,
            contentHtml = article.contentHtml,
            textContent = extractTextFromHtml(article.contentHtml),

            // AI 欄位
            titleZh = aiArticle.titleZh,
            cleanedText = aiArticle.cleanedText,
            cleanedTextZh = aiArticle.cleanedTextZh,
            explanation = explanation
        )
    }

    private fun extractTextFromHtml(html: String?): String? {
        if (html == null) return null
        return htmlTagPattern.matcher(html).replaceAll("")
    }
}
