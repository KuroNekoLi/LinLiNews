package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleAi
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.DifficultyContent
import com.linli.news.model.DifficultyLevel
import com.linli.news.model.Explanation
import com.linli.news.repository.ArticleAiRepository
import com.linli.news.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    /**
     * HTML 優化結果資料型別
     */
    data class HtmlOptimizationResult(
        val optimizedHtml: String,
        val optimizedZhHtml: String?
    )

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
                // 移除使用过的锁，避免 map 不必要的扩大
                locks.remove(articleId)
            }
        }
    }

    /**
     * 查詢 ArticleAi 紀錄（使用 readOnly = true 減少交易鎖定）
     */
    @Transactional(readOnly = true)
    fun findExistingAiArticle(articleId: Long): ArticleAi? {
        logger.debug("Finding existing AI article for ID {}", articleId)
        return articleAiRepository.findByArticle_Id(articleId).orElse(null)
    }

    /**
     * 查詢 Article 紀錄（使用 readOnly = true 減少交易鎖定）
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

        // 1. 先查有無現成結果（DB 簡短 transaction）
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
            HtmlOptimizationResult(
                optimizedHtml = result.optimizedHtml,
                optimizedZhHtml = result.optimizedZhHtml
            )
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
     * 優先使用直接插入的方式寫入 ArticleAi 紀錄，若失敗則改用查詢+更新的方式
     * 以減少交易鎖定的爭奪
     */
    private fun ArticleAi.applyFrom(
        json: JsonNode,
        optimizedResult: HtmlOptimizationResult,
        difficulty: DifficultyLevel
    ): ArticleAi {
        title = json.path("title").asText(null)
        titleZh = json.path("title_zh").asText(null)
        optimizedHtml = optimizedResult.optimizedHtml
        optimizedZhHtml = optimizedResult.optimizedZhHtml
        cleanedText = json.path("cleaned_text").asText(null)
        cleanedTextZh = json.path("cleaned_text_zh").asText(null)
        summaryEn = json.path("summary_en").asText(null)
        summaryZh = json.path("summary_zh").asText(null)
        val expNode = json.path("explanation")
        explanation = if (!expNode.isMissingNode && !expNode.isNull && expNode.isObject) {
            expNode.toString()
        } else {
            explanation
        }
        when (difficulty) {
            DifficultyLevel.EASY -> contentEasy = buildDifficultyContentJson(difficulty, optimizedResult, json)
            DifficultyLevel.NORMAL -> contentNormal = buildDifficultyContentJson(difficulty, optimizedResult, json)
            DifficultyLevel.HARD -> contentHard = buildDifficultyContentJson(difficulty, optimizedResult, json)
            DifficultyLevel.VERY_HARD -> contentVeryHard = buildDifficultyContentJson(difficulty, optimizedResult, json)
        }
        return this
    }

    /**
     * 建立難度對應的內容 JSON 字串。
     */
    private fun buildDifficultyContentJson(
        difficulty: DifficultyLevel,
        optimizedResult: HtmlOptimizationResult,
        json: JsonNode
    ): String {
        val node = objectMapper.createObjectNode()
        node.put("difficulty", difficulty.name)
        node.put("optimized_html", optimizedResult.optimizedHtml)
        node.put("optimized_zh_html", optimizedResult.optimizedZhHtml)
        node.put("cleaned_text", json.path("cleaned_text").asText(null))
        node.put("cleaned_text_zh", json.path("cleaned_text_zh").asText(null))
        val expNode = json.path("explanation")
        if (!expNode.isMissingNode && !expNode.isNull) {
            node.set<JsonNode>("explanation", expNode)
        }
        return node.toString()
    }

    /**
     * 將 JSON 字串轉成難度內容資料模型。
     */
    private fun parseDifficultyContent(json: String?, fallbackDifficulty: DifficultyLevel): DifficultyContent? {
        if (json.isNullOrBlank()) {
            return null
        }
        return try {
            val node = objectMapper.readTree(json)
            val difficultyValue = node.path("difficulty").asText(fallbackDifficulty.name)
            val difficulty = runCatching { DifficultyLevel.valueOf(difficultyValue) }.getOrDefault(fallbackDifficulty)
            val explanationNode = node.path("explanation")
            val explanation = if (!explanationNode.isMissingNode && !explanationNode.isNull) {
                parseExplanationNode(explanationNode)
            } else {
                null
            }
            DifficultyContent(
                difficulty = difficulty,
                optimizedHtml = node.path("optimized_html").asText(null),
                optimizedZhHtml = node.path("optimized_zh_html").asText(null),
                cleanedText = node.path("cleaned_text").asText(null),
                cleanedTextZh = node.path("cleaned_text_zh").asText(null),
                explanation = explanation
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse difficulty content JSON: ${e.message}")
            null
        }
    }

    @Transactional
    fun saveAiResult(
        article: Article,
        json: JsonNode,
        optimizedResult: HtmlOptimizationResult,
        difficulty: DifficultyLevel = DifficultyLevel.NORMAL
    ): ArticleAi {
        val startTime = System.currentTimeMillis()
        logger.info("Saving AI result for article ID {}", article.id)

        try {
            // 直接插入
            val entity = ArticleAi(article = article).applyFrom(json, optimizedResult, difficulty)
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
            ).applyFrom(json, optimizedResult, difficulty)

            val savedEntity = articleAiRepository.save(existing)
            logger.info(
                "Fallback save successful for article ID {} in {}ms",
                article.id,
                System.currentTimeMillis() - startTime
            )
            return savedEntity
        }
    }

    private fun loadProcessableArticles(source: String?, section: String?): List<Article> {
        val page = PageRequest.of(0, 10000)
        val articles = when {
            !source.isNullOrBlank() && !section.isNullOrBlank() ->
                articleRepository.findBySource_NameAndSource_Section(source, section, page).content

            !source.isNullOrBlank() ->
                articleRepository.findBySource_Name(source, page).content

            !section.isNullOrBlank() ->
                articleRepository.findBySource_Section(section, page).content

            else -> articleRepository.findAll().toList()
        }
        return articles.filter { !it.contentHtml.isNullOrBlank() }
    }

    private fun List<Article>.pickRandom(count: Int): List<Article> =
        if (size <= count) this else shuffled().take(count)

    // 随机取得 n 篇 AI 文章
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
     * 随机取得 n 篇文章并自动进行 AI 处理（使用 Coroutine 优化）
     *
     * 从数据库随机抽取指定数量的文章：
     * - 查询阶段：可同时多个并行查询（readOnly transaction）
     * - AI 处理阶段：可并行呼叫 OpenAI / HTML 优化（不占用 DB 连线）
     * - 写入阶段：透过 Semaphore 确保同一时间只有「一个」写入发生（单写入、多读取）
     *
     * @param count 要取得的文章数量（实际数量可能少于请求数量，如处理失败或无可用文章）
     * @param source 来源名称过滤（可选，例如 BBC、CNN）
     * @param section 区块名称过滤（可选，例如 news、world、technology）
     * @return Triple of (已处理的 AI 文章清单, 符合条件的可用文章总数, 本次成功处理的文章数)
     */
    fun randomWithProcessing(count: Int, source: String?, section: String?): Triple<List<ArticleAi>, Int, Int> {
        val allArticles = loadProcessableArticles(source, section)

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = allArticles.pickRandom(count)

        logger.info(
            "Starting random processing of {} articles (source={}, section={})",
            selectedArticles.size, source ?: "any", section ?: "any"
        )

        val result = mutableListOf<ArticleAi>()
        var processedCount = 0

        // 單寫入、多查詢：寫入透過 Semaphore 控制，同一時間只允許一個 DB 寫入
        runBlocking {
            val writeSemaphore = Semaphore(1)

            val jobs = selectedArticles.map { article ->
                async(Dispatchers.IO) {
                    val articleId = requireNotNull(article.id) { "Article must have an ID" }
                    try {
                        // 1. 先查是否已經有 AI 結果（readOnly transaction，可並行）
                        val existing = findExistingAiArticle(articleId)
                        val aiArticle = if (existing != null) {
                            existing
                        } else {
                            // 2. 查原始文章內容（readOnly transaction，可並行）
                            val fullArticle = findArticleById(articleId)
                            require(!fullArticle.contentHtml.isNullOrBlank()) {
                                "Article has no contentHtml; please fetch full content first"
                            }
                            val textContent = extractTextFromHtml(fullArticle.contentHtml)
                            require(!textContent.isNullOrBlank()) {
                                "Article textContent is empty after HTML extraction"
                            }

                            // 3. AI 處理（不在 DB 交易內，可並行）
                            val json: JsonNode = try {
                                val aiStart = System.currentTimeMillis()
                                val result = aiProcessor.process(fullArticle.title, textContent)
                                logger.info(
                                    "AI processing completed in {}ms for article ID {} (randomWithProcessing)",
                                    System.currentTimeMillis() - aiStart,
                                    articleId
                                )
                                result
                            } catch (e: AiQuotaException) {
                                logger.error("AI quota exceeded for article ID {}: {}", articleId, e.message)
                                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
                            } catch (e: Exception) {
                                logger.error(
                                    "Error during AI processing for article ID {} (randomWithProcessing): {}",
                                    articleId, e.message, e
                                )
                                throw e
                            }

                            val optimizedResult = try {
                                val htmlStart = System.currentTimeMillis()
                                val r = aiProcessor.optimizeHtml(fullArticle.title, fullArticle.contentHtml!!)
                                logger.info(
                                    "HTML optimization completed in {}ms for article ID {} (randomWithProcessing)",
                                    System.currentTimeMillis() - htmlStart,
                                    articleId
                                )
                                HtmlOptimizationResult(
                                    optimizedHtml = r.optimizedHtml,
                                    optimizedZhHtml = r.optimizedZhHtml
                                )
                            } catch (e: AiQuotaException) {
                                logger.error(
                                    "AI quota exceeded during HTML optimization for article ID {}: {}",
                                    articleId,
                                    e.message
                                )
                                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
                            } catch (e: Exception) {
                                logger.error(
                                    "Error during HTML optimization for article ID {} (randomWithProcessing): {}",
                                    articleId,
                                    e.message,
                                    e
                                )
                                throw e
                            }

                            // 4. 寫入：透過 Semaphore 確保同一時間只有一個寫入
                            writeSemaphore.withPermit {
                                saveAiResult(fullArticle, json, optimizedResult)
                            }
                        }

                        synchronized(result) {
                            result.add(aiArticle)
                            processedCount++
                        }
                    } catch (e: ResponseStatusException) {
                        // 保留 429 等 HTTP 状态错误给上一层处理
                        logger.error(
                            "Failed to process article {} with HTTP status {}: {}",
                            articleId,
                            e.statusCode,
                            e.reason,
                            e
                        )
                        throw e
                    } catch (e: Exception) {
                        logger.error("Failed to process article {}: {}", articleId, e.message, e)
                    }
                }
            }

            jobs.forEach { job -> job.await() }
        }

        logger.info("Completed random processing: processed {}/{} articles", processedCount, selectedArticles.size)
        return Triple(result, allArticles.size, processedCount)
    }

    // 新增的方法
    fun randomWithRewriteAndProcessing(
        count: Int,
        source: String?,
        section: String?,
        difficulty: DifficultyLevel?
    ): Triple<List<ArticleAi>, Int, Int> {
        // 實現與 randomWithProcessing 類似的邏輯，但包含 AI 重寫和處理
        val allArticles = loadProcessableArticles(source, section)

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = allArticles.pickRandom(count)

        logger.info(
            "Starting random processing of {} articles (source={}, section={})",
            selectedArticles.size, source ?: "any", section ?: "any"
        )

        val result = mutableListOf<ArticleAi>()
        var processedCount = 0

        // 单写入、多查询：写入通过 Semaphore 控制，同一时间只允许一个 DB 写入
        runBlocking {
            val writeSemaphore = Semaphore(1)

            val jobs = selectedArticles.map { article ->
                async(Dispatchers.IO) {
                    val articleId = requireNotNull(article.id) { "Article must have an ID" }
                    try {
                        // 1. 先查是否已经有 AI 结果（readOnly transaction，可并行）
                        val existing = findExistingAiArticle(articleId)
                        val aiArticle = if (existing != null) {
                            existing
                        } else {
                            // 2. 查原始文章内容（readOnly transaction，可并行）
                            val fullArticle = findArticleById(articleId)
                            require(!fullArticle.contentHtml.isNullOrBlank()) {
                                "Article has no contentHtml; please fetch full content first"
                            }
                            val textContent = extractTextFromHtml(fullArticle.contentHtml)
                            require(!textContent.isNullOrBlank()) {
                                "Article textContent is empty after HTML extraction"
                            }

                            // 3. AI 處理（不在 DB 交易內，可並行）
                            // Two-stage AI processing: rewrite and explain
                            val difficultyLevel = difficulty ?: DifficultyLevel.NORMAL
                            val combinedResult: JsonNode = try {
                                val aiStart = System.currentTimeMillis()
                                val stage1Result =
                                    aiProcessor.rewriteStage1(difficultyLevel, fullArticle.title, fullArticle.contentHtml!!)
                                logger.info(
                                    "AI rewrite stage 1 completed in {}ms for article ID {} (randomWithRewriteAndProcessing)",
                                    System.currentTimeMillis() - aiStart,
                                    articleId
                                )

                                // Validate rewrite stage 1 result
                                val stage1CleanedText = stage1Result.path("cleaned_text").asText("")
                                val stage1Title = stage1Result.path("title").asText("")
                                val wordCountStage1 = countEnglishWords(stage1CleanedText)
                                val hasEnoughPara = hasEnoughParagraphs(stage1Result.path("optimized_html").asText(""))

                                logger.info(
                                    "Stage 1 validation: word count = {}, title present = {}, has enough paragraphs = {}",
                                    wordCountStage1,
                                    stage1Title.isNotBlank(),
                                    hasEnoughPara
                                )

                                // rewriteStage1 已經內置重試機制和驗證，所以這裡不需要再額外檢查
                                // 如果能到這裡，表示 stage1Result 已經通過驗證

                                val stage2Result = aiProcessor.teachingStage2(
                                    difficultyLevel,
                                    stage1Title,
                                    stage1CleanedText
                                )
                                logger.info(
                                    "AI teaching stage 2 completed in {}ms for article ID {} (randomWithRewriteAndProcessing)",
                                    System.currentTimeMillis() - aiStart,
                                    articleId
                                )
                                // Combine results
                                val combined = objectMapper.createObjectNode()

                                // Stage1 的欄位：文章內容、HTML 與英文標題
                                combined.put("title", stage1Result.path("title").asText(null))
                                combined.put("cleaned_text", stage1Result.path("cleaned_text").asText(null))
                                combined.put("cleaned_text_zh", stage1Result.path("cleaned_text_zh").asText(null))
                                combined.put("optimized_html", stage1Result.path("optimized_html").asText(null))
                                combined.put("optimized_zh_html", stage1Result.path("optimized_zh_html").asText(null))
                                combined.put("word_count", stage1Result.path("word_count").asInt(wordCountStage1))

                                // Stage2 的欄位：雙語標題、雙語摘要與解析
                                combined.put("title_zh", stage2Result.path("title_zh").asText(null))
                                combined.put("summary_en", stage2Result.path("summary_en").asText(null))
                                combined.put("summary_zh", stage2Result.path("summary_zh").asText(null))
                                combined.set<JsonNode>("explanation", stage2Result.path("explanation"))

                                // 在特殊情況下，如果 Stage1 沒有提供標題，則使用 Stage2 的標題
                                if (combined.path("title").isNull && !stage2Result.path("title").isNull) {
                                    combined.put("title", stage2Result.path("title").asText(null))
                                    logger.info("Fallback to title from Stage2")
                                }

                                logger.info(
                                    "Combined result fields: title={}, title_zh={}, summary_en={}, summary_zh={}, explanation={}",
                                    !combined.path("title").isNull,
                                    !combined.path("title_zh").isNull,
                                    !combined.path("summary_en").isNull,
                                    !combined.path("summary_zh").isNull,
                                    !combined.path("explanation").isNull
                                )

                                combined
                            } catch (e: AiQuotaException) {
                                logger.error("AI quota exceeded for article ID {}: {}", articleId, e.message)
                                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
                            } catch (e: Exception) {
                                logger.error(
                                    "Error during AI two-stage processing for article ID {} (randomWithRewriteAndProcessing): {}",
                                    articleId, e.message, e
                                )
                                throw e
                            }

                            // 4. 建立優化後的 HTML 結果物件
                            val optimizedResult = HtmlOptimizationResult(
                                optimizedHtml = combinedResult.path("optimized_html").asText(null),
                                optimizedZhHtml = combinedResult.path("optimized_zh_html").asText(null)
                            )

                            // 驗證 AI 重寫與處理結果（註解改為繁體中文）
                            if (!validateRewriteAndProcessResult(combinedResult)) {
                                logger.error(
                                    "AI 重寫與處理結果驗證失敗，文章 ID {}",
                                    articleId
                                )
                                throw Exception("AI 重寫與處理結果驗證失敗")
                            }

                            // 5. 寫入：透過 Semaphore 確保同一時間只有一個寫入
                            writeSemaphore.withPermit {
                                saveAiResult(fullArticle, combinedResult, optimizedResult, difficultyLevel)
                            }
                        }

                        synchronized(result) {
                            result.add(aiArticle)
                            processedCount++
                        }
                    } catch (e: ResponseStatusException) {
                        // 保留 429 等 HTTP 状态错误给上一层处理
                        logger.error(
                            "Failed to process article {} with HTTP status {}: {}",
                            articleId,
                            e.statusCode,
                            e.reason,
                            e
                        )
                        throw e
                    } catch (e: Exception) {
                        logger.error("Failed to process article {}: {}", articleId, e.message, e)
                    }
                }
            }

            jobs.forEach { job -> job.await() }
        }

        logger.info("Completed random processing: processed {}/{} articles", processedCount, selectedArticles.size)
        return Triple(result, allArticles.size, processedCount)
    }

    data class RewriteAndProcessResult(
        val optimizedHtml: String,
        val optimizedZhHtml: String?,
        val title: String?,
        val titleZh: String?,
        val summaryEn: String?,
        val summaryZh: String?,
        val cleanedText: String?,
        val cleanedTextZh: String?,
        val explanation: JsonNode
    )

    /**
     * 檢查 HTML 是否有足夠的段落
     *
     * @param html HTML 文字
     * @param minParagraphs 最小段落數，預設為 4
     * @return 如果段落數達到最小要求則回傳 true，否則回傳 false
     */
    private fun hasEnoughParagraphs(html: String?, minParagraphs: Int = 4): Boolean {
        if (html == null || html.isBlank()) {
            return false
        }
        // 使用更嚴謹的正則檢索 <p> 標籤，避免簡單 split 導致計數錯誤
        val paragraphPattern = Regex("<p[^>]*>", RegexOption.IGNORE_CASE)
        val paragraphCount = paragraphPattern.findAll(html).count()
        return paragraphCount >= minParagraphs
    }

    /**
     * 檢查並驗證 AI 重寫與處理的結果
     *
     * @param result AI 回應的 JSON 結果
     * @return 驗證成功回傳 true，失敗回傳 false
     */
    private fun validateRewriteAndProcessResult(result: JsonNode): Boolean {
        try {
            // 1. 檢查字數
            val cleanedText = result.path("cleaned_text").asText("")
            val aiReportedCount = result.path("word_count").asInt(-1)
            val ourCount = countEnglishWords(cleanedText)
            logger.debug("[AI] Reported word count: $aiReportedCount; Locally calculated word count: $ourCount")

            if (ourCount !in 400..600) {
                logger.warn("[AI] Article word count = $ourCount, not in required range 400..600")
                return false
            }

            // 2. 檢查段落數
            val optimizedHtml = result.path("optimized_html").asText("")
            if (!hasEnoughParagraphs(optimizedHtml)) {
                logger.warn("[AI] Article does not have enough paragraphs (minimum 4 required)")
                return false
            }

            // 所有驗證通過
            return true
        } catch (e: Exception) {
            logger.error("Error validating AI rewrite and process result: {}", e.message, e)
            return false
        }
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

        entity.applyFrom(
            json,
            HtmlOptimizationResult(optimizedResult.optimizedHtml, optimizedResult.optimizedZhHtml),
            DifficultyLevel.NORMAL
        )

        return articleAiRepository.save(entity)
    }

    fun processAndOverwriteDto(articleId: Long): AiArticleDetail {
        val aiArticle = processAndOverwrite(articleId)
        return toDto(aiArticle.article, aiArticle)
    }

    private fun toDto(article: Article, aiArticle: ArticleAi): AiArticleDetail {
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        val easyContent = parseDifficultyContent(aiArticle.contentEasy, DifficultyLevel.EASY)
        val normalContent = parseDifficultyContent(aiArticle.contentNormal, DifficultyLevel.NORMAL)
        val hardContent = parseDifficultyContent(aiArticle.contentHard, DifficultyLevel.HARD)
        val veryHardContent = parseDifficultyContent(aiArticle.contentVeryHard, DifficultyLevel.VERY_HARD)
        return AiArticleDetail(
            id = articleId,
            sourceName = article.source.name,
            section = article.source.section,
            title = article.title,
            publishedAt = article.publishedAt?.toString(),
            originalUrl = article.originalUrl,
            contentHtml = normalContent?.optimizedHtml ?: article.contentHtml,
            titleZh = aiArticle.titleZh,
            summaryEn = aiArticle.summaryEn,
            summaryZh = aiArticle.summaryZh,
            easy = easyContent,
            normal = normalContent,
            hard = hardContent,
            veryHard = veryHardContent
        )
    }

    /**
     * 從 JsonNode 轉換 Explanation，並容忍未知欄位。
     */
    private fun parseExplanationNode(node: JsonNode): Explanation? {
        val tolerantMapper = objectMapper.copy()
        tolerantMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )
        return try {
            tolerantMapper.treeToValue(node, Explanation::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to parse explanation node: ${e.message}")
            null
        }
    }

    // 修復匯入問題：使用更寬容的解析方案，並相容常見的 explanation 結構異常
    private fun parseExplanationWithFallback(explanationJson: String?, articleId: Long): Explanation? {
        if (explanationJson.isNullOrBlank()) {
            logger.warn("Article $articleId has null or blank explanation JSON")
            return null
        }

        val tolerantMapper = objectMapper.copy()
        tolerantMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )
        tolerantMapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
            true
        )

        // 第一步：嘗試常規反序列化
        try {
            val parsedExplanation = tolerantMapper.readValue(explanationJson, Explanation::class.java)
            logger.info("Successfully parsed explanation for article $articleId: vocab=${parsedExplanation.vocabulary?.size}, grammar=${parsedExplanation.grammar?.size}, mcq=${parsedExplanation.comprehensionMcq?.size}")
            return parsedExplanation
        } catch (e: Exception) {
            logger.warn("Failed to parse explanation JSON for article $articleId: ${e.message}")
            logger.debug("Raw explanation JSON: $explanationJson")
        }

        // 第二步：如果 explanation 欄位巢狀，嘗試提取 explanation 根節點（修復匯入常見問題）
        try {
            val root = tolerantMapper.readTree(explanationJson)
            if (root.has("explanation")) {
                val explanationNode = root.get("explanation")
                val explanationStr = explanationNode.toString()
                try {
                    val parsedExplanation = tolerantMapper.readValue(explanationStr, Explanation::class.java)
                    logger.info("Successfully parsed nested explanation for article $articleId: vocab=${parsedExplanation.vocabulary?.size}, grammar=${parsedExplanation.grammar?.size}, mcq=${parsedExplanation.comprehensionMcq?.size}")
                    return parsedExplanation
                } catch (e: Exception) {
                    logger.warn("Nested explanation parsing failed for article $articleId: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not parse explanation as JSON tree for article $articleId: ${e.message}")
        }

        // 第三步（可選）：AI 匯出時不完整或格式異常
        try {
            if (!explanationJson.trim().endsWith("}")) {
                logger.warn("Article $articleId explanation JSON appears to be truncated")
            }
        } catch (_: Exception) {
        }

        logger.warn("Returning null for explanation of article $articleId due to unrecoverable parse errors")
        return null
    }

    private fun extractTextFromHtml(html: String?): String? {
        if (html == null) return null
        return htmlTagPattern.matcher(html).replaceAll("")
    }

    /**
     * 計算英文文本的單字數量
     */
    private fun countEnglishWords(text: String): Int =
        text.split(Regex("\\s+"))
            .count { token ->
                token.any { ch -> ch.isLetter() }
            }
}
