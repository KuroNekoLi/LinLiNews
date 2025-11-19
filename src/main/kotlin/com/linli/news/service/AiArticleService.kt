package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleAi
import com.linli.news.domain.ArticleAiComprehensionMcq
import com.linli.news.domain.ArticleAiComprehensionOption
import com.linli.news.domain.ArticleAiGrammarItem
import com.linli.news.domain.ArticleAiPhraseIdiomItem
import com.linli.news.domain.ArticleAiSentencePatternItem
import com.linli.news.domain.ArticleAiView
import com.linli.news.domain.ArticleAiVocabularyItem
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.AiViewAvailability
import com.linli.news.model.ComprehensionMcqItem
import com.linli.news.model.DifficultyContent
import com.linli.news.model.DifficultyLevel
import com.linli.news.model.Explanation
import com.linli.news.model.GrammarItem
import com.linli.news.model.McqOption
import com.linli.news.model.PhraseIdiomItem
import com.linli.news.model.SentencePatternItem
import com.linli.news.model.VocabularyItem
import com.linli.news.repository.ArticleAiRepository
import com.linli.news.repository.ArticleRepository
import com.linli.news.service.HtmlOptimizationResult
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
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Service
class AiArticleService(
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val aiProcessor: AiArticleProcessorService
) {

    companion object {
        private const val DEFAULT_FROM_LANGUAGE = "en"
        private const val DEFAULT_TO_LANGUAGE = "zh-TW"
    }

    private val logger = LoggerFactory.getLogger(AiArticleService::class.java)
    private val objectMapper = ObjectMapper()
    private val htmlTagPattern: Pattern = Pattern.compile("<[^>]*>")

    private fun normalizeLanguage(language: String?, defaultValue: String): String {
        val fallback = language?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue
        val parts = fallback.split('-', limit = 2)
        return if (parts.size == 2) {
            "${parts[0].lowercase()}-${parts[1].uppercase()}"
        } else {
            fallback.lowercase()
        }
    }

    private fun normalizeLanguagePair(fromLanguage: String?, toLanguage: String?): Pair<String, String> {
        val normalizedFrom = normalizeLanguage(fromLanguage, DEFAULT_FROM_LANGUAGE)
        val normalizedTo = normalizeLanguage(toLanguage, DEFAULT_TO_LANGUAGE)
        return normalizedFrom to normalizedTo
    }

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
            val result = aiProcessor.process(
                article.title,
                textContent,
                DEFAULT_FROM_LANGUAGE,
                DEFAULT_TO_LANGUAGE
            )
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
            val result = aiProcessor.optimizeHtml(
                article.title,
                article.contentHtml!!,
                DEFAULT_FROM_LANGUAGE,
                DEFAULT_TO_LANGUAGE
            )
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
     * 優先使用直接插入的方式寫入 ArticleAi 紀錄，若失敗則改用查詢+更新的方式
     * 以減少交易鎖定的爭奪
     */
    private fun ArticleAi.applyFrom(
        json: JsonNode,
        optimizedResult: HtmlOptimizationResult,
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): ArticleAi {
        val titleFrom = json.path("title_from").asText(null) ?: json.path("title").asText(null)
        val titleTo = json.path("title_to").asText(null) ?: json.path("title_zh").asText(null)
        val summaryFrom = json.path("summary_from").asText(null) ?: json.path("summary_en").asText(null)
        val summaryTo = json.path("summary_to").asText(null) ?: json.path("summary_zh").asText(null)

        title = titleFrom
        titleZh = titleTo
        summaryEn = summaryFrom
        summaryZh = summaryTo

        val targetView = findOrCreateView(difficulty, fromLanguage, toLanguage)
        targetView.titleFrom = titleFrom
        targetView.titleTo = titleTo
        targetView.summaryFrom = summaryFrom
        targetView.summaryTo = summaryTo

        val optimizedFrom = optimizedResult.optimizedHtmlFrom
            ?: json.path("optimized_html_from").asText(null)
            ?: json.path("optimized_html").asText(null)
        val optimizedTo = optimizedResult.optimizedHtmlTo
            ?: json.path("optimized_html_to").asText(null)
            ?: json.path("optimized_zh_html").asText(null)

        targetView.optimizedHtmlFrom = optimizedFrom
        targetView.optimizedHtmlTo = optimizedTo
        targetView.cleanedTextFrom = json.path("cleaned_text_from").asText(null)
            ?: json.path("cleaned_text").asText(null)
        targetView.cleanedTextTo = json.path("cleaned_text_to").asText(null)
            ?: json.path("cleaned_text_zh").asText(null)

        val explanationNode = json.path("explanation")
        val explanationModel = if (!explanationNode.isMissingNode && !explanationNode.isNull && explanationNode.isObject) {
            parseExplanationNode(explanationNode)
        } else {
            null
        }
        targetView.applyExplanation(explanationModel, explanationNode?.toString())
        targetView.updatedAt = OffsetDateTime.now()

        return this
    }

    private fun ArticleAi.findOrCreateView(
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): ArticleAiView {
        return views.firstOrNull {
            it.difficulty == difficulty &&
                it.fromLanguage.equals(fromLanguage, ignoreCase = true) &&
                it.toLanguage.equals(toLanguage, ignoreCase = true)
        } ?: ArticleAiView(
            articleAi = this,
            difficulty = difficulty,
            fromLanguage = fromLanguage,
            toLanguage = toLanguage
        ).also { views.add(it) }
    }

    private fun ArticleAiView.applyExplanation(explanation: Explanation?, legacyJson: String?) {
        vocabularyItems.clear()
        grammarItems.clear()
        sentencePatternItems.clear()
        phraseIdiomItems.clear()
        comprehensionQuestions.clear()

        explanation?.vocabulary?.forEachIndexed { index, item ->
            vocabularyItems.add(
                ArticleAiVocabularyItem(
                    articleAiView = this,
                    sortOrder = index,
                    pos = item.pos,
                    wordFrom = item.wordFrom,
                    wordTo = item.wordTo,
                    definitionFrom = item.definitionFrom,
                    definitionTo = item.definitionTo,
                    exampleFrom = item.exampleFrom,
                    exampleTo = item.exampleTo
                )
            )
        }

        explanation?.grammar?.forEachIndexed { index, item ->
            grammarItems.add(
                ArticleAiGrammarItem(
                    articleAiView = this,
                    sortOrder = index,
                    ruleFrom = item.ruleFrom,
                    ruleTo = item.ruleTo,
                    explanationFrom = item.explanationFrom,
                    explanationTo = item.explanationTo,
                    exampleFrom = item.exampleFrom,
                    exampleTo = item.exampleTo
                )
            )
        }

        explanation?.sentencePatterns?.forEachIndexed { index, item ->
            sentencePatternItems.add(
                ArticleAiSentencePatternItem(
                    articleAiView = this,
                    sortOrder = index,
                    patternFrom = item.patternFrom,
                    patternTo = item.patternTo,
                    explanationFrom = item.explanationFrom,
                    explanationTo = item.explanationTo,
                    exampleFrom = item.exampleFrom,
                    exampleTo = item.exampleTo
                )
            )
        }

        explanation?.phrasesIdioms?.forEachIndexed { index, item ->
            phraseIdiomItems.add(
                ArticleAiPhraseIdiomItem(
                    articleAiView = this,
                    sortOrder = index,
                    phraseFrom = item.phraseFrom,
                    phraseTo = item.phraseTo,
                    explanationFrom = item.explanationFrom,
                    explanationTo = item.explanationTo,
                    exampleFrom = item.exampleFrom,
                    exampleTo = item.exampleTo
                )
            )
        }

        explanation?.comprehensionMcq?.forEachIndexed { index, item ->
            val mcqEntity = ArticleAiComprehensionMcq(
                articleAiView = this,
                sortOrder = index,
                questionFrom = item.questionFrom,
                questionTo = item.questionTo,
                answer = item.answer,
                explanationFrom = item.explanationFrom,
                explanationTo = item.explanationTo
            )
            item.options?.forEach { option ->
                mcqEntity.options.add(
                    ArticleAiComprehensionOption(
                        mcq = mcqEntity,
                        label = option.label,
                        textFrom = option.textFrom,
                        textTo = option.textTo
                    )
                )
            }
            comprehensionQuestions.add(mcqEntity)
        }

        legacyExplanationJson = legacyJson
    }

    private fun ArticleAiView.buildExplanationModel(): Explanation? {
        val vocab = vocabularyItems
            .sortedBy { it.sortOrder }
            .map {
                VocabularyItem(
                    pos = it.pos,
                    wordFrom = it.wordFrom,
                    wordTo = it.wordTo,
                    definitionFrom = it.definitionFrom,
                    definitionTo = it.definitionTo,
                    exampleFrom = it.exampleFrom,
                    exampleTo = it.exampleTo
                )
            }
            .takeIf { it.isNotEmpty() }

        val grammar = grammarItems
            .sortedBy { it.sortOrder }
            .map {
                GrammarItem(
                    ruleFrom = it.ruleFrom,
                    ruleTo = it.ruleTo,
                    explanationFrom = it.explanationFrom,
                    explanationTo = it.explanationTo,
                    exampleFrom = it.exampleFrom,
                    exampleTo = it.exampleTo
                )
            }
            .takeIf { it.isNotEmpty() }

        val sentencePatterns = sentencePatternItems
            .sortedBy { it.sortOrder }
            .map {
                SentencePatternItem(
                    patternFrom = it.patternFrom,
                    patternTo = it.patternTo,
                    explanationFrom = it.explanationFrom,
                    explanationTo = it.explanationTo,
                    exampleFrom = it.exampleFrom,
                    exampleTo = it.exampleTo
                )
            }
            .takeIf { it.isNotEmpty() }

        val phrasesIdioms = phraseIdiomItems
            .sortedBy { it.sortOrder }
            .map {
                PhraseIdiomItem(
                    phraseFrom = it.phraseFrom,
                    phraseTo = it.phraseTo,
                    explanationFrom = it.explanationFrom,
                    explanationTo = it.explanationTo,
                    exampleFrom = it.exampleFrom,
                    exampleTo = it.exampleTo
                )
            }
            .takeIf { it.isNotEmpty() }

        val comprehensionMcq = comprehensionQuestions
            .sortedBy { it.sortOrder }
            .map { mcq ->
                ComprehensionMcqItem(
                    questionFrom = mcq.questionFrom,
                    questionTo = mcq.questionTo,
                    answer = mcq.answer,
                    explanationFrom = mcq.explanationFrom,
                    explanationTo = mcq.explanationTo,
                    options = mcq.options
                        .sortedBy { it.label }
                        .map { option ->
                            McqOption(
                                label = option.label,
                                textFrom = option.textFrom,
                                textTo = option.textTo
                            )
                        }
                )
            }
            .takeIf { it.isNotEmpty() }

        if (vocab == null && grammar == null && sentencePatterns == null && phrasesIdioms == null && comprehensionMcq == null) {
            return legacyExplanationJson?.let { parseLegacyExplanation(it) }
        }

        return Explanation(
            vocabulary = vocab,
            grammar = grammar,
            sentencePatterns = sentencePatterns,
            phrasesIdioms = phrasesIdioms,
            comprehensionMcq = comprehensionMcq
        )
    }

    /**
     * 將資料庫視圖轉成 API 用的難度內容模型。
     */
    private fun ArticleAiView.toDifficultyContent(): DifficultyContent {
        return DifficultyContent(
            difficulty = difficulty,
            fromLanguage = fromLanguage,
            toLanguage = toLanguage,
            optimizedHtmlFrom = optimizedHtmlFrom,
            optimizedHtmlTo = optimizedHtmlTo,
            cleanedTextFrom = cleanedTextFrom,
            cleanedTextTo = cleanedTextTo,
            explanation = buildExplanationModel()
        )
    }

    @Transactional
    fun saveAiResult(
        article: Article,
        json: JsonNode,
        optimizedResult: HtmlOptimizationResult,
        difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
        fromLanguage: String = DEFAULT_FROM_LANGUAGE,
        toLanguage: String = DEFAULT_TO_LANGUAGE
    ): ArticleAi {
        val startTime = System.currentTimeMillis()
        logger.info("Saving AI result for article ID {}", article.id)
        val normalizedFrom = normalizeLanguage(fromLanguage, DEFAULT_FROM_LANGUAGE)
        val normalizedTo = normalizeLanguage(toLanguage, DEFAULT_TO_LANGUAGE)

        try {
            // 直接插入
            val entity = ArticleAi(article = article).applyFrom(
                json,
                optimizedResult,
                difficulty,
                normalizedFrom,
                normalizedTo
            )
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
            ).applyFrom(json, optimizedResult, difficulty, normalizedFrom, normalizedTo)

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
    fun random(
        count: Int,
        source: String?,
        section: String?,
        difficulty: DifficultyLevel?,
        fromLanguage: String?,
        toLanguage: String?
    ): Pair<List<ArticleAi>, Int> {
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

        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val eligibleByLang = all.filter { article ->
            article.views.any {
                it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                    it.toLanguage.equals(normalizedTo, ignoreCase = true)
            }
        }
        val eligible = if (difficulty != null) {
            eligibleByLang.filter { article ->
                article.views.any {
                    it.difficulty == difficulty &&
                        it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                        it.toLanguage.equals(normalizedTo, ignoreCase = true)
                }
            }
        } else {
            if (eligibleByLang.isNotEmpty()) eligibleByLang else all
        }

        if (eligible.isEmpty()) {
            return Pair(emptyList(), 0)
        }

        val sampled = if (eligible.size <= count) eligible else eligible.shuffled().take(count)
        return Pair(sampled, eligible.size)
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
    fun randomWithProcessing(
        count: Int,
        source: String?,
        section: String?,
        difficulty: DifficultyLevel?,
        fromLanguage: String?,
        toLanguage: String?
    ): Triple<List<ArticleAi>, Int, Int> {
        val allArticles = loadProcessableArticles(source, section)

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = allArticles.pickRandom(count)
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)

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
                                val result = aiProcessor.process(
                                    fullArticle.title,
                                    textContent,
                                    normalizedFrom,
                                    normalizedTo
                                )
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
                                val r = aiProcessor.optimizeHtml(
                                    fullArticle.title,
                                    fullArticle.contentHtml!!,
                                    normalizedFrom,
                                    normalizedTo
                                )
                                logger.info(
                                    "HTML optimization completed in {}ms for article ID {} (randomWithProcessing)",
                                    System.currentTimeMillis() - htmlStart,
                                    articleId
                                )
                                r
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
                                saveAiResult(
                                    fullArticle,
                                    json,
                                    optimizedResult,
                                    difficulty ?: DifficultyLevel.NORMAL,
                                    normalizedFrom,
                                    normalizedTo
                                )
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
        difficulty: DifficultyLevel?,
        fromLanguage: String?,
        toLanguage: String?
    ): Triple<List<ArticleAi>, Int, Int> {
        // 實現與 randomWithProcessing 類似的邏輯，但包含 AI 重寫和處理
        val allArticles = loadProcessableArticles(source, section)

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = allArticles.pickRandom(count)
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)

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
                                val stage1Result = aiProcessor.rewriteStage1(
                                    difficultyLevel,
                                    fullArticle.title,
                                    fullArticle.contentHtml!!,
                                    normalizedFrom,
                                    normalizedTo
                                )
                                logger.info(
                                    "AI rewrite stage 1 completed in {}ms for article ID {} (randomWithRewriteAndProcessing)",
                                    System.currentTimeMillis() - aiStart,
                                    articleId
                                )

                                // Validate rewrite stage 1 result
                                val stage1CleanedText = stage1Result.path("cleaned_text_from").asText(
                                    stage1Result.path("cleaned_text").asText("")
                                )
                                val stage1Title = stage1Result.path("title_from").asText(
                                    stage1Result.path("title").asText("")
                                )
                                val wordCountStage1 = countEnglishWords(stage1CleanedText)
                                val hasEnoughPara = hasEnoughParagraphs(
                                    stage1Result.path("optimized_html_from").asText(
                                        stage1Result.path("optimized_html").asText("")
                                    )
                                )

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
                                    stage1CleanedText,
                                    normalizedFrom,
                                    normalizedTo
                                )
                                logger.info(
                                    "AI teaching stage 2 completed in {}ms for article ID {} (randomWithRewriteAndProcessing)",
                                    System.currentTimeMillis() - aiStart,
                                    articleId
                                )
                                // Combine results
                                val combined = objectMapper.createObjectNode()

                                // Stage1 的欄位：文章內容、HTML 與英文標題
                                combined.put(
                                    "title_from",
                                    stage1Result.path("title_from").asText(stage1Result.path("title").asText(null))
                                )
                                combined.put(
                                    "title_to",
                                    stage1Result.path("title_to").asText(stage2Result.path("title_to").asText(null))
                                )
                                combined.put(
                                    "cleaned_text_from",
                                    stage1Result.path("cleaned_text_from").asText(stage1Result.path("cleaned_text").asText(null))
                                )
                                combined.put(
                                    "cleaned_text_to",
                                    stage1Result.path("cleaned_text_to").asText(stage1Result.path("cleaned_text_zh").asText(null))
                                )
                                combined.put(
                                    "optimized_html_from",
                                    stage1Result.path("optimized_html_from").asText(stage1Result.path("optimized_html").asText(null))
                                )
                                combined.put(
                                    "optimized_html_to",
                                    stage1Result.path("optimized_html_to").asText(stage1Result.path("optimized_zh_html").asText(null))
                                )
                                combined.put("word_count", stage1Result.path("word_count").asInt(wordCountStage1))

                                // Stage2 的欄位：雙語標題、雙語摘要與解析
                                combined.put(
                                    "title_from",
                                    stage2Result.path("title_from").asText(combined.path("title_from").asText(null))
                                )
                                combined.put(
                                    "title_to",
                                    stage2Result.path("title_to").asText(combined.path("title_to").asText(null))
                                )
                                combined.put("summary_from", stage2Result.path("summary_from").asText(null))
                                combined.put("summary_to", stage2Result.path("summary_to").asText(null))
                                combined.set<JsonNode>("explanation", stage2Result.path("explanation"))

                                if (combined.path("title_from").isNull && !stage2Result.path("title_from").isNull) {
                                    combined.put("title_from", stage2Result.path("title_from").asText(null))
                                    logger.info("Fallback to title_from from Stage2")
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
                            val optimizedFrom = combinedResult.path("optimized_html_from").asText(
                                combinedResult.path("optimized_html").asText(null)
                            )
                            if (optimizedFrom.isNullOrBlank()) {
                                throw IllegalStateException("AI 回應缺少 optimized_html_from 欄位")
                            }
                            val optimizedTo = combinedResult.path("optimized_html_to").asText(
                                combinedResult.path("optimized_zh_html").asText(null)
                            )
                            val optimizedResult = HtmlOptimizationResult(
                                optimizedHtmlFrom = optimizedFrom,
                                optimizedHtmlTo = optimizedTo
                            )

                            // 驗證 AI 重寫與處理結果（註解改為繁體中文）
                            if (!validateRewriteAndProcessResult(combinedResult, difficultyLevel)) {
                                logger.error(
                                    "AI 重寫與處理結果驗證失敗，文章 ID {}",
                                    articleId
                                )
                                throw Exception("AI 重寫與處理結果驗證失敗")
                            }

                            // 5. 寫入：透過 Semaphore 確保同一時間只有一個寫入
                            writeSemaphore.withPermit {
                                saveAiResult(
                                    fullArticle,
                                    combinedResult,
                                    optimizedResult,
                                    difficultyLevel,
                                    normalizedFrom,
                                    normalizedTo
                                )
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
    private fun validateRewriteAndProcessResult(result: JsonNode, difficulty: DifficultyLevel): Boolean {
        try {
            // 1. 檢查字數
            val cleanedText = result.path("cleaned_text_from").asText(
                result.path("cleaned_text").asText("")
            )
            val ourCount = countEnglishWords(cleanedText)
            val minWords = difficulty.minWords
            val maxWords = difficulty.maxWords
            logger.debug("[AI] 計算出的字數：$ourCount，難度：${difficulty.label}(${difficulty.cefrCode})，預期範圍：${maxWords?.let { "$minWords-$it" } ?: "$minWords+"}")

            val validWordCount = if (maxWords != null) ourCount in minWords..maxWords else ourCount >= minWords
            if (!validWordCount) {
                logger.warn("[AI] Article word count = $ourCount, not in required range for difficulty ${difficulty.label}")
                return false
            }

            // 2. 檢查段落數
            val optimizedHtml = result.path("optimized_html_from").asText(
                result.path("optimized_html").asText("")
            )
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

    fun getOrProcessDto(
        articleId: Long,
        difficulty: DifficultyLevel?,
        fromLanguage: String?,
        toLanguage: String?
    ): AiArticleDetail {
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val aiArticle = getOrProcess(articleId)
        return toDto(aiArticle.article, aiArticle, difficulty, normalizedFrom, normalizedTo)
    }

    @Transactional
    fun processAndOverwrite(
        articleId: Long,
        difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
        fromLanguage: String = DEFAULT_FROM_LANGUAGE,
        toLanguage: String = DEFAULT_TO_LANGUAGE
    ): ArticleAi {
        val article = articleRepository.findById(articleId).orElseThrow()
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }

        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        val json: JsonNode = try {
            aiProcessor.process(article.title, textContent, fromLanguage, toLanguage)
        } catch (e: AiQuotaException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val optimizedResult = try {
            aiProcessor.optimizeHtml(article.title, article.contentHtml!!, fromLanguage, toLanguage)
        } catch (e: AiQuotaException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        }

        val entity = articleAiRepository.findByArticle_Id(articleId).orElse(null)
            ?: ArticleAi(article = article)

        entity.applyFrom(
            json,
            optimizedResult,
            difficulty,
            fromLanguage,
            toLanguage
        )

        return articleAiRepository.save(entity)
    }

    fun processAndOverwriteDto(
        articleId: Long,
        difficulty: DifficultyLevel?,
        fromLanguage: String?,
        toLanguage: String?
    ): AiArticleDetail {
        val targetDifficulty = difficulty ?: DifficultyLevel.NORMAL
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val aiArticle = processAndOverwrite(articleId, targetDifficulty, normalizedFrom, normalizedTo)
        return toDto(aiArticle.article, aiArticle, targetDifficulty, normalizedFrom, normalizedTo)
    }

    private fun toDto(
        article: Article,
        aiArticle: ArticleAi,
        requestedDifficulty: DifficultyLevel?,
        fromLanguage: String,
        toLanguage: String
    ): AiArticleDetail {
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        val selectedView = selectView(aiArticle, requestedDifficulty, fromLanguage, toLanguage)
        val difficultyContent = selectedView?.toDifficultyContent()
        val availableForPair = aiArticle.views
            .filter {
                it.fromLanguage.equals(fromLanguage, ignoreCase = true) &&
                    it.toLanguage.equals(toLanguage, ignoreCase = true)
            }
            .map { it.difficulty }
            .distinct()
            .sortedBy { it.ordinal }
        val availableViews = aiArticle.views
            .map { AiViewAvailability(it.fromLanguage, it.toLanguage, it.difficulty) }
            .sortedWith(compareBy<AiViewAvailability> { it.fromLanguage }.thenBy { it.toLanguage }.thenBy { it.difficulty.ordinal })

        val finalFromLang = selectedView?.fromLanguage ?: fromLanguage
        val finalToLang = selectedView?.toLanguage ?: toLanguage
        val titleFrom = selectedView?.titleFrom ?: aiArticle.title ?: article.title
        val titleTo = selectedView?.titleTo ?: aiArticle.titleZh
        val summaryFrom = selectedView?.summaryFrom ?: aiArticle.summaryEn
        val summaryTo = selectedView?.summaryTo ?: aiArticle.summaryZh

        return AiArticleDetail(
            id = articleId,
            sourceName = article.source.name,
            section = article.source.section,
            title = article.title,
            publishedAt = article.publishedAt?.toString(),
            originalUrl = article.originalUrl,
            contentHtml = difficultyContent?.optimizedHtmlFrom ?: article.contentHtml,
            titleFrom = titleFrom,
            titleTo = titleTo,
            titleZh = titleTo,
            summaryFrom = summaryFrom,
            summaryTo = summaryTo,
            summaryEn = summaryFrom,
            summaryZh = summaryTo,
            requestedDifficulty = requestedDifficulty,
            fromLanguage = finalFromLang,
            toLanguage = finalToLang,
            content = difficultyContent,
            availableDifficulties = availableForPair,
            availableViews = availableViews
        )
    }

    fun toDetail(
        aiArticle: ArticleAi,
        requestedDifficulty: DifficultyLevel?,
        fromLanguage: String,
        toLanguage: String
    ): AiArticleDetail {
        return toDto(aiArticle.article, aiArticle, requestedDifficulty, fromLanguage, toLanguage)
    }

    private fun selectView(
        aiArticle: ArticleAi,
        requestedDifficulty: DifficultyLevel?,
        fromLanguage: String,
        toLanguage: String
    ): ArticleAiView? {
        if (aiArticle.views.isEmpty()) {
            return null
        }

        val normalizedFrom = fromLanguage
        val normalizedTo = toLanguage
        val matching = aiArticle.views.filter {
            it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                it.toLanguage.equals(normalizedTo, ignoreCase = true)
        }

        val candidates = if (matching.isNotEmpty()) matching else aiArticle.views

        if (requestedDifficulty != null) {
            candidates.firstOrNull { it.difficulty == requestedDifficulty }?.let { return it }
        }

        val priority = listOf(
            DifficultyLevel.NORMAL,
            DifficultyLevel.HARD,
            DifficultyLevel.VERY_HARD,
            DifficultyLevel.EASY
        )

        return priority.firstNotNullOfOrNull { level ->
            candidates.firstOrNull { it.difficulty == level }
        } ?: candidates.firstOrNull()
    }

    /**
     * 從 JsonNode 轉換 Explanation，並容忍舊欄位命名。
     */
    private fun parseExplanationNode(node: JsonNode): Explanation? {
        if (!node.isObject) {
            return null
        }

        val vocabulary = node.arrayOrNull("vocabulary")?.mapNotNull { item ->
            VocabularyItem(
                pos = item.textValueOrNull("pos"),
                wordFrom = item.textValueOrNull("word_from", "word_en"),
                wordTo = item.textValueOrNull("word_to", "word_zh"),
                definitionFrom = item.textValueOrNull("definition_from", "definition_en"),
                definitionTo = item.textValueOrNull("definition_to", "definition_zh"),
                exampleFrom = item.textValueOrNull("example_from", "example_en"),
                exampleTo = item.textValueOrNull("example_to", "example_zh")
            )
        }?.takeIf { it.isNotEmpty() }

        val grammar = node.arrayOrNull("grammar")?.mapNotNull { item ->
            GrammarItem(
                ruleFrom = item.textValueOrNull("rule_from", "rule_en"),
                ruleTo = item.textValueOrNull("rule_to"),
                explanationFrom = item.textValueOrNull("explanation_from", "explanation_en"),
                explanationTo = item.textValueOrNull("explanation_to", "explanation_zh"),
                exampleFrom = item.textValueOrNull("example_from", "example_en"),
                exampleTo = item.textValueOrNull("example_to", "example_zh")
            )
        }?.takeIf { it.isNotEmpty() }

        val sentencePatterns = node.arrayOrNull("sentence_patterns")?.mapNotNull { item ->
            SentencePatternItem(
                patternFrom = item.textValueOrNull("pattern_from", "pattern_en"),
                patternTo = item.textValueOrNull("pattern_to"),
                explanationFrom = item.textValueOrNull("explanation_from", "explanation_en"),
                explanationTo = item.textValueOrNull("explanation_to", "explanation_zh"),
                exampleFrom = item.textValueOrNull("example_from", "example_en"),
                exampleTo = item.textValueOrNull("example_to", "example_zh")
            )
        }?.takeIf { it.isNotEmpty() }

        val phrasesIdioms = node.arrayOrNull("phrases_idioms")?.mapNotNull { item ->
            PhraseIdiomItem(
                phraseFrom = item.textValueOrNull("phrase_from", "phrase_en"),
                phraseTo = item.textValueOrNull("phrase_to"),
                explanationFrom = item.textValueOrNull("explanation_from", "explanation_en"),
                explanationTo = item.textValueOrNull("explanation_to", "explanation_zh"),
                exampleFrom = item.textValueOrNull("example_from", "example_en"),
                exampleTo = item.textValueOrNull("example_to", "example_zh")
            )
        }?.takeIf { it.isNotEmpty() }

        val comprehensionMcq = node.arrayOrNull("comprehension_mcq")?.mapNotNull { item ->
            val optionsNode = item.path("options")
            val options = when {
                optionsNode.isArray -> optionsNode.mapNotNull { optNode ->
                    val label = optNode.textValueOrNull("label") ?: return@mapNotNull null
                    McqOption(
                        label = label,
                        textFrom = optNode.textValueOrNull("text_from", "text_en"),
                        textTo = optNode.textValueOrNull("text_to", "text_zh")
                    )
                }
                optionsNode.isObject -> listOf("A", "B", "C", "D").mapNotNull { label ->
                    optionsNode.textValueOrNull(label)?.let { text ->
                        McqOption(
                            label = label,
                            textFrom = text,
                            textTo = optionsNode.textValueOrNull("${label}_to", "${label}_zh")
                        )
                    }
                }
                else -> null
            }?.takeIf { it.isNotEmpty() }

            ComprehensionMcqItem(
                questionFrom = item.textValueOrNull("question_from", "question_en"),
                questionTo = item.textValueOrNull("question_to", "question_zh"),
                options = options,
                answer = item.textValueOrNull("answer"),
                explanationFrom = item.textValueOrNull("explanation_from"),
                explanationTo = item.textValueOrNull("explanation_to", "explanation_zh")
            )
        }?.takeIf { it.isNotEmpty() }

        if (vocabulary == null && grammar == null && sentencePatterns == null && phrasesIdioms == null && comprehensionMcq == null) {
            return null
        }

        return Explanation(
            vocabulary = vocabulary,
            grammar = grammar,
            sentencePatterns = sentencePatterns,
            phrasesIdioms = phrasesIdioms,
            comprehensionMcq = comprehensionMcq
        )
    }

    private fun JsonNode.arrayOrNull(name: String): JsonNode? {
        val child = this.path(name)
        return if (child.isArray && child.size() > 0) child else null
    }

    private fun JsonNode.textValueOrNull(vararg fieldNames: String): String? {
        fieldNames.forEach { name ->
            val node = this.path(name)
            if (!node.isMissingNode && !node.isNull) {
                val value = node.asText(null)
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        }
        return null
    }

    private fun parseLegacyExplanation(json: String): Explanation? =
        runCatching { objectMapper.readTree(json) }.getOrNull()?.let { parseExplanationNode(it) }

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
