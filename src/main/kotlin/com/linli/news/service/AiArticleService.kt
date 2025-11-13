package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleAi
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.Explanation
import com.linli.news.repository.ArticleAiRepository
import com.linli.news.repository.ArticleRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Service
class AiArticleService(
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val aiProcessor: AiArticleProcessorService
) {

    private val logger = LoggerFactory.getLogger(AiArticleService::class.java)
    private val objectMapper = ObjectMapper()
    private val htmlTagPattern: Pattern = Pattern.compile("<[^>]*>")

    @Transactional(readOnly = true)
    fun listProcessed(page: Int, size: Int): Page<ArticleAi> {
        return articleAiRepository.findAll(PageRequest.of(page, size))
    }

    /**
     * 用於同一篇 article 處理時互斥（避免同時寫入同一ID）
     */
    private val locks = ConcurrentHashMap<Long, Any>()

    fun getOrProcess(articleId: Long): ArticleAi = getOrProcessThreadSafe(articleId)

    fun getOrProcessThreadSafe(articleId: Long): ArticleAi {
        val lock = locks.computeIfAbsent(articleId) { Any() }
        synchronized(lock) {
            try {
                return getOrProcessNoLock(articleId)
            } finally {
                // 移除使用過的鎖，避免 map 不必要的擴大
                locks.remove(articleId)
            }
        }
    }

    /**
     * 查詢 ArticleAi 記錄（使用 readOnly = true 減少事務鎖）
     */
    @Transactional(readOnly = true)
    fun findExistingAiArticle(articleId: Long): ArticleAi? {
        logger.debug("Finding existing AI article for ID {}", articleId)
        return articleAiRepository.findByArticle_Id(articleId).orElse(null)
    }

    /**
     * 查詢 Article 記錄（使用 readOnly = true 減少事務鎖）
     */
    @Transactional(readOnly = true)
    fun findArticleById(articleId: Long): Article {
        logger.debug("Finding article by ID {}", articleId)
        return articleRepository.findById(articleId).orElseThrow {
            logger.warn("Article not found with ID: {}", articleId)
            IllegalArgumentException("Article not found with id: $articleId")
        }
    }

    fun getOrProcessNoLock(articleId: Long): ArticleAi {
        val startTime = System.currentTimeMillis()
        logger.info("Starting AI processing for article ID {}", articleId)

        // 1. 先查有無現成結果（DB簡短 transaction）
        val existing = findExistingAiArticle(articleId)
        if (existing != null) {
            logger.info("Found existing AI processed article for ID {}", articleId)
            return existing
        }

        // 2. 取得文章內容
        val article = findArticleById(articleId)
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }
        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        // 3. AI 處理部分（長流程，但不涉及 DB transaction）
        logger.info("Calling AI processor for article ID {}: '{}'", articleId, article.title)
        val json: JsonNode = try {
            val aiStart = System.currentTimeMillis()
            val result = aiProcessor.process(article.title, textContent)
            logger.info(
                "AI processing completed in {}ms for article ID {}",
                System.currentTimeMillis() - aiStart,
                articleId
            )
            result
        } catch (e: AiQuotaException) {
            logger.error("AI quota exceeded for article ID {}: {}", articleId, e.message)
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        } catch (e: Exception) {
            logger.error("Error during AI processing for article ID {}: {}", articleId, e.message, e)
            throw e
        }

        val optimizedResult = try {
            val htmlStart = System.currentTimeMillis()
            val result = aiProcessor.optimizeHtml(article.title, article.contentHtml!!)
            logger.info(
                "HTML optimization completed in {}ms for article ID {}",
                System.currentTimeMillis() - htmlStart,
                articleId
            )
            result
        } catch (e: AiQuotaException) {
            logger.error("AI quota exceeded during HTML optimization for article ID {}: {}", articleId, e.message)
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        } catch (e: Exception) {
            logger.error("Error during HTML optimization for article ID {}: {}", articleId, e.message, e)
            throw e
        }

        // 4. 短 transaction DB 寫入
        val result = saveAiResult(article, json, optimizedResult)

        val totalTime = System.currentTimeMillis() - startTime
        logger.info("Total AI processing and saving completed in {}ms for article ID {}", totalTime, articleId)

        return result
    }

    /**
     * 優先使用直接插入的方式寫入 ArticleAi 記錄，若失敗則改用查詢+更新的方式
     * 以減少事務鎖的爭奪
     */
    @Transactional
    fun saveAiResult(
        article: Article,
        json: JsonNode,
        optimizedResult: AiArticleProcessorService.HtmlOptimizationResult
    ): ArticleAi {
        val startTime = System.currentTimeMillis()
        logger.info("Saving AI result for article ID {}", article.id)

        try {
            // 直接插入
            val entity = ArticleAi(article = article).apply {
                title = json.path("title").asText(null)
                titleZh = json.path("title_zh").asText(null)
                optimizedHtml = optimizedResult.optimizedHtml
                optimizedZhHtml = optimizedResult.optimizedZhHtml
                summaryEn = json.path("summary_en").asText(null)
                summaryZh = json.path("summary_zh").asText(null)
                cleanedText = json.path("cleaned_text").asText(null)
                cleanedTextZh = json.path("cleaned_text_zh").asText(null)
                val expNode = json.path("explanation")
                explanation = if (!expNode.isMissingNode && !expNode.isNull && expNode.isObject) {
                    expNode.toString()
                } else {
                    "{}"
                }
            }

            val savedEntity = articleAiRepository.save(entity)
            logger.info(
                "Direct save successful for article ID {} in {}ms",
                article.id,
                System.currentTimeMillis() - startTime
            )
            return savedEntity

        } catch (e: Exception) {
            logger.warn("Direct save failed, trying update approach for article ID {}: {}", article.id, e.message)

            // 查詢+更新
            val existing = articleAiRepository.findByArticle_Id(article.id!!).orElse(
                ArticleAi(article = article)
            ).apply {
                title = json.path("title").asText(null)
                titleZh = json.path("title_zh").asText(null)
                optimizedHtml = optimizedResult.optimizedHtml
                optimizedZhHtml = optimizedResult.optimizedZhHtml
                summaryEn = json.path("summary_en").asText(null)
                summaryZh = json.path("summary_zh").asText(null)
                cleanedText = json.path("cleaned_text").asText(null)
                cleanedTextZh = json.path("cleaned_text_zh").asText(null)
                val expNode = json.path("explanation")
                explanation = if (!expNode.isMissingNode && !expNode.isNull && expNode.isObject) {
                    expNode.toString()
                } else {
                    "{}"
                }
            }

            val savedEntity = articleAiRepository.save(existing)
            logger.info(
                "Fallback save successful for article ID {} in {}ms",
                article.id,
                System.currentTimeMillis() - startTime
            )
            return savedEntity
        }
    }

    // 隨機取得 n 篇 AI 文章
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

    /**
     * 隨機取得 n 篇文章並自動進行 AI 處理
     *
     * 從資料庫隨機抽取指定數量的文章，若文章尚未經 AI 處理，會自動進行處理並儲存。
     * 此方法適用於需要確保取得的文章都有 AI 解析結果的場景。
     *
     * @param count 要取得的文章數量（實際數量可能少於請求數量，如處理失敗或無可用文章）
     * @param source 來源名稱過濾（可選，例如 BBC、CNN）
     * @param section 區塊名稱過濾（可選，例如 news、world、technology）
     * @return Triple of (已處理的 AI 文章清單, 符合條件的可用文章總數, 本次新處理的文章數)
     */
    fun randomWithProcessing(count: Int, source: String?, section: String?): Triple<List<ArticleAi>, Int, Int> {
        val page = PageRequest.of(0, 10000)
        val allArticles = when {
            !source.isNullOrBlank() && !section.isNullOrBlank() ->
                articleRepository.findBySource_NameAndSource_Section(source, section, page).content

            !source.isNullOrBlank() ->
                articleRepository.findBySource_Name(source, page).content

            !section.isNullOrBlank() ->
                articleRepository.findBySource_Section(section, page).content

            else -> articleRepository.findAll().toList()
        }.filter { !it.contentHtml.isNullOrBlank() }

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = if (allArticles.size <= count) {
            allArticles
        } else {
            allArticles.shuffled().take(count)
        }

        val result = mutableListOf<ArticleAi>()
        var processedCount = 0

        logger.info(
            "Starting random processing of {} articles (source={}, section={})",
            selectedArticles.size, source ?: "any", section ?: "any"
        )

        for (article in selectedArticles) {
            try {
                val aiArticle = getOrProcessThreadSafe(requireNotNull(article.id) { "Article must have an ID" })
                result.add(aiArticle)
                processedCount++
            } catch (e: Exception) {
                logger.error("Failed to process article ${article.id}: ${e.message}", e)
            }
        }

        logger.info("Completed random processing: processed {}/{} articles", processedCount, selectedArticles.size)
        return Triple(result, allArticles.size, processedCount)
    }

    fun getOrProcessDto(articleId: Long): AiArticleDetail {
        val aiArticle = getOrProcess(articleId)
        return toDto(aiArticle.article, aiArticle)
    }

    @Transactional
    fun processAndOverwrite(articleId: Long): ArticleAi {
        val article = articleRepository.findById(articleId).orElseThrow()
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }

        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        val json: JsonNode = try {
            aiProcessor.process(article.title, textContent)
        } catch (e: AiQuotaException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val optimizedResult = try {
            aiProcessor.optimizeHtml(article.title, article.contentHtml!!)
        } catch (e: AiQuotaException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val entity = articleAiRepository.findByArticle_Id(articleId).orElse(null)
            ?: ArticleAi(article = article)

        entity.title = json.path("title").asText(null)
        entity.titleZh = json.path("title_zh").asText(null)
        entity.optimizedHtml = optimizedResult.optimizedHtml
        entity.optimizedZhHtml = optimizedResult.optimizedZhHtml
        entity.summaryEn = json.path("summary_en").asText(null)
        entity.summaryZh = json.path("summary_zh").asText(null)
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

    private fun toDto(article: Article, aiArticle: ArticleAi): AiArticleDetail {
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        val explanationJson = aiArticle.explanation
        val explanation = try {
            if (explanationJson.isNullOrBlank()) {
                logger.warn("Article $articleId has null or blank explanation JSON")
                null
            } else {
                logger.debug("Parsing explanation JSON for article $articleId: ${explanationJson.take(200)}...")

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
                logger.info("Successfully parsed explanation for article $articleId: vocab=${parsedExplanation.vocabulary?.size}, grammar=${parsedExplanation.grammar?.size}, mcq=${parsedExplanation.comprehensionMcq?.size}")
                parsedExplanation
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse explanation JSON for article $articleId: ${e.message}")
            logger.debug("Raw explanation JSON: $explanationJson")

            // 檢查 JSON 內容來提供更具體的錯誤信息
            if (explanationJson?.contains("\"explanation\"") == true) {
                if (!explanationJson.trim().endsWith("}")) {
                    logger.warn("Article $articleId explanation JSON appears to be truncated")
                } else {
                    logger.warn("Article $articleId explanation JSON structure is invalid")
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
            id = articleId,
            sourceName = article.source.name,
            section = article.source.section,
            title = article.title,
            publishedAt = article.publishedAt?.toString(),
            originalUrl = article.originalUrl,
            contentHtml = aiArticle.optimizedHtml ?: article.contentHtml,
            optimizedHtml = aiArticle.optimizedHtml,
            optimizedZhHtml = aiArticle.optimizedZhHtml,
            titleZh = aiArticle.titleZh,
            summaryEn = aiArticle.summaryEn,
            summaryZh = aiArticle.summaryZh,
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
