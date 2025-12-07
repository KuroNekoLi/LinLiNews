package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.*
import com.linli.news.model.*
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
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Service
class AiArticleService(
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val aiProcessor: AiArticleProcessorService,
    private val articleTagService: ArticleTagService,
    private val featuredCollectionService: FeaturedCollectionService
) {

    companion object {
        private const val DEFAULT_FROM_LANGUAGE = "en"
        private const val DEFAULT_TO_LANGUAGE = "zh-TW"
    }

    private val logger = LoggerFactory.getLogger(AiArticleService::class.java)
    private val objectMapper = ObjectMapper()
    private val htmlTagPattern: Pattern = Pattern.compile("<[^>]*>")

    /**
     * 標準化難度清單，預設回傳包含 NORMAL，並移除重複。
     */
    private fun normalizeDifficulties(difficulties: List<DifficultyLevel>?): List<DifficultyLevel> {
        val unique = difficulties?.let { LinkedHashSet(it) }?.toList().orEmpty()
        return if (unique.isEmpty()) listOf(DifficultyLevel.NORMAL) else unique
    }

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

    @Transactional(readOnly = true)
    fun listProcessedFiltered(
        page: Int,
        size: Int,
        difficulties: List<DifficultyLevel>?,
        fromLanguage: String?,
        toLanguage: String?
    ): Page<ArticleAi> {
        val difficultySelection = normalizeDifficulties(difficulties)
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val pageable = PageRequest.of(page, size)
        return articleAiRepository.findByViewFilters(normalizedFrom, normalizedTo, difficultySelection, pageable)
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
        // article_id 是唯一的，所以只會返回一筆記錄
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
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        logger.info("Saving AI result for article ID {}", articleId)
        val normalizedFrom = normalizeLanguage(fromLanguage, DEFAULT_FROM_LANGUAGE)
        val normalizedTo = normalizeLanguage(toLanguage, DEFAULT_TO_LANGUAGE)

        // 總是先查詢，避免創建重複記錄
        // article_id 是唯一的，所以只會有 0 或 1 筆記錄
        val existingList = articleAiRepository.findByArticle_Id(articleId).map { listOf(it) }.orElse(emptyList())

        val entity = if (existingList.isNotEmpty()) {
            // 如果有多筆記錄，使用第一筆並記錄警告
            if (existingList.size > 1) {
                logger.error(
                    "Found {} duplicate ArticleAi records for article {}, using the first one",
                    existingList.size,
                    articleId
                )
            }
            existingList.first()
        } else {
            // 創建新記錄
            ArticleAi(article = article)
        }

        // 應用新的內容
        entity.applyFrom(json, optimizedResult, difficulty, normalizedFrom, normalizedTo)

        val savedEntity = articleAiRepository.save(entity)

        // 從 AI 回應中提取並保存 tags 和 collections
        try {
            val tagsArray = json.path("tags")
            if (tagsArray.isArray && tagsArray.size() > 0) {
                val tagNames = tagsArray.map { it.asText() }.filter { it.isNotBlank() }
                if (tagNames.isNotEmpty()) {
                    articleTagService.assignTagsByName(articleId, tagNames)
                    logger.info("Assigned {} tags to article {}: {}", tagNames.size, articleId, tagNames)
                }
            }

            val collectionsArray = json.path("featured_collections")
            if (collectionsArray.isArray && collectionsArray.size() > 0) {
                val collectionIds = collectionsArray.map { it.asText() }.filter { it.isNotBlank() }
                if (collectionIds.isNotEmpty()) {
                    featuredCollectionService.assignCollectionsById(articleId, collectionIds)
                    logger.info(
                        "Assigned {} collections to article {}: {}",
                        collectionIds.size,
                        articleId,
                        collectionIds
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to assign tags/collections to article {}: {}", articleId, e.message)
        }

        logger.info(
            "Save successful for article ID {} in {}ms (existing={}, views={})",
            articleId,
            System.currentTimeMillis() - startTime,
            existingList.isNotEmpty(),
            savedEntity.views.size
        )
        return savedEntity
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
        difficulties: List<DifficultyLevel>?,
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
        val difficultySelection = normalizeDifficulties(difficulties)
        val eligibleByLang = all.filter { article ->
            article.views.any {
                it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                    it.toLanguage.equals(normalizedTo, ignoreCase = true)
            }
        }
        val filteredByDifficulty = eligibleByLang.filter { article ->
            article.views.any {
                difficultySelection.contains(it.difficulty) &&
                    it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                    it.toLanguage.equals(normalizedTo, ignoreCase = true)
            }
        }
        val eligible = when {
            filteredByDifficulty.isNotEmpty() -> filteredByDifficulty
            eligibleByLang.isNotEmpty() -> eligibleByLang
            else -> all
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
        difficulties: List<DifficultyLevel>?,
        fromLanguage: String?,
        toLanguage: String?
    ): Triple<List<ArticleAi>, Int, Int> {
        val allArticles = loadProcessableArticles(source, section)

        if (allArticles.isEmpty()) {
            return Triple(emptyList(), 0, 0)
        }

        val selectedArticles = allArticles.pickRandom(count)
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val difficultySelection = normalizeDifficulties(difficulties)

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
                        var processedThisArticle = false
                        // 1. 先查是否已經有 AI 結果（readOnly transaction，可並行）
                        val existing = findExistingAiArticle(articleId)
                        val aiArticle: ArticleAi = if (existing != null) {
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
                            val savedArticle = writeSemaphore.withPermit {
                                saveAiResult(
                                    fullArticle,
                                    json,
                                    optimizedResult,
                                    difficultySelection.first(),
                                    normalizedFrom,
                                    normalizedTo
                                )
                            }
                            processedThisArticle = true
                            savedArticle
                        }

                        synchronized(result) {
                            result.add(aiArticle)
                            if (processedThisArticle) {
                                processedCount++
                            }
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
        difficulties: List<DifficultyLevel>?,
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
        val difficultySelection = normalizeDifficulties(difficulties)

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
                        var processedThisArticle = false
                        // 1. 先查是否已经有 AI 结果（readOnly transaction，可并行）
                        val existing = findExistingAiArticle(articleId)
                        val aiArticle: ArticleAi = if (existing != null) {
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
                            val difficultyLevel = difficultySelection.first()
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

                                // 提取 Stage 1 的結果用於 Stage 2
                                val stage1CleanedText = stage1Result.path("cleaned_text_from").asText(
                                    stage1Result.path("cleaned_text").asText("")
                                )
                                val stage1Title = stage1Result.path("title_from").asText(
                                    stage1Result.path("title").asText("")
                                )

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
                                combined.put("word_count", stage1Result.path("word_count").asInt(0))

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
                                combined.set<JsonNode>("tags", stage2Result.path("tags"))
                                combined.set<JsonNode>(
                                    "featured_collections",
                                    stage2Result.path("featured_collections")
                                )

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

                            // 5. 寫入：透過 Semaphore 確保同一時間只有一個寫入
                            val savedArticle = writeSemaphore.withPermit {
                                saveAiResult(
                                    fullArticle,
                                    combinedResult,
                                    optimizedResult,
                                    difficultyLevel,
                                    normalizedFrom,
                                    normalizedTo
                                )
                            }
                            processedThisArticle = true
                            savedArticle
                        }

                        synchronized(result) {
                            result.add(aiArticle)
                            if (processedThisArticle) {
                                processedCount++
                            }
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



    fun getOrProcessDto(
        articleId: Long,
        difficulties: List<DifficultyLevel>?,
        fromLanguage: String?,
        toLanguage: String?
    ): AiArticleDetail {
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val difficultySelection = normalizeDifficulties(difficulties)
        val targetDifficulty = difficultySelection.first()

        // 檢查該難度是否存在
        // 先查詢 ArticleAi，然後檢查其 views 中是否有該難度
        val existingAi = findExistingAiArticle(articleId)
        val difficultyExists = existingAi?.views?.any { view ->
            view.difficulty == targetDifficulty &&
                    view.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                    view.toLanguage.equals(normalizedTo, ignoreCase = true)
        } ?: false

        val finalAiArticle = if (!difficultyExists) {
            // 該難度不存在，需要處理
            logger.info(
                "Requested difficulty {} not found for article {}, generating new view",
                targetDifficulty,
                articleId
            )

            // 先檢查文章是否有任何 AI 處理過的記錄
            val existingAi = findExistingAiArticle(articleId)
            if (existingAi != null) {
                // 已有 ArticleAi，只需添加新難度的 view
                processSpecificDifficulty(existingAi.article, targetDifficulty, normalizedFrom, normalizedTo)
            } else {
                // 完全沒處理過，需要完整處理
                getOrProcess(articleId)
                // 處理後再次檢查，如果不是請求的難度，則生成該難度
                val processed = findExistingAiArticle(articleId)!!
                if (!processed.views.any {
                        it.difficulty == targetDifficulty &&
                                it.fromLanguage.equals(normalizedFrom, ignoreCase = true) &&
                                it.toLanguage.equals(normalizedTo, ignoreCase = true)
                    }) {
                    processSpecificDifficulty(processed.article, targetDifficulty, normalizedFrom, normalizedTo)
                } else {
                    processed
                }
            }
        } else {
            // 該難度已存在，直接獲取
            findExistingAiArticle(articleId)!!
        }

        return toDto(finalAiArticle.article, finalAiArticle, difficultySelection, normalizedFrom, normalizedTo)
    }

    /**
     * 為特定文章生成特定難度的 AI 內容
     * 如果該難度已存在，則更新；否則新增
     */
    @Transactional
    fun processSpecificDifficulty(
        article: Article,
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): ArticleAi {
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        require(!article.contentHtml.isNullOrBlank()) { "Article has no contentHtml; please fetch full content first" }

        val textContent = extractTextFromHtml(article.contentHtml)
        require(!textContent.isNullOrBlank()) { "Article textContent is empty after HTML extraction" }

        logger.info(
            "Processing article {} with difficulty {} (from={}, to={})",
            articleId,
            difficulty,
            fromLanguage,
            toLanguage
        )

        // 使用兩階段 AI 處理：重寫 + 教學
        val combinedResult: JsonNode = try {
            val aiStart = System.currentTimeMillis()

            // Stage 1: 重寫文章
            val stage1Result = aiProcessor.rewriteStage1(
                difficulty,
                article.title,
                article.contentHtml!!,
                fromLanguage,
                toLanguage
            )
            logger.info(
                "AI rewrite stage 1 completed in {}ms for article ID {}",
                System.currentTimeMillis() - aiStart,
                articleId
            )

            // 提取 Stage 1 的結果用於 Stage 2
            val stage1CleanedText = stage1Result.path("cleaned_text_from").asText(
                stage1Result.path("cleaned_text").asText("")
            )
            val stage1Title = stage1Result.path("title_from").asText(
                stage1Result.path("title").asText("")
            )

            // Stage 2: 生成教學內容
            val stage2Result = aiProcessor.teachingStage2(
                difficulty,
                stage1Title,
                stage1CleanedText,
                fromLanguage,
                toLanguage
            )
            logger.info(
                "AI teaching stage 2 completed in {}ms for article ID {}",
                System.currentTimeMillis() - aiStart,
                articleId
            )

            // 合併結果
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
            combined.put("word_count", stage1Result.path("word_count").asInt(0))

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
            combined.set<JsonNode>("tags", stage2Result.path("tags"))
            combined.set<JsonNode>("featured_collections", stage2Result.path("featured_collections"))

            if (combined.path("title_from").isNull && !stage2Result.path("title_from").isNull) {
                combined.put("title_from", stage2Result.path("title_from").asText(null))
                logger.info("Fallback to title_from from Stage2")
            }

            combined
        } catch (e: AiQuotaException) {
            logger.error("AI quota exceeded for article ID {}: {}", articleId, e.message)
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message, e)
        } catch (e: Exception) {
            logger.error(
                "Error during AI processing for article ID {}: {}",
                articleId,
                e.message,
                e
            )
            throw e
        }

        // 建立優化後的 HTML 結果物件
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

        // 儲存結果
        return saveAiResult(
            article,
            combinedResult,
            optimizedResult,
            difficulty,
            fromLanguage,
            toLanguage
        )
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
        difficulties: List<DifficultyLevel>?,
        fromLanguage: String?,
        toLanguage: String?
    ): AiArticleDetail {
        val difficultySelection = normalizeDifficulties(difficulties)
        val targetDifficulty = difficultySelection.first()
        val (normalizedFrom, normalizedTo) = normalizeLanguagePair(fromLanguage, toLanguage)
        val aiArticle = processAndOverwrite(articleId, targetDifficulty, normalizedFrom, normalizedTo)
        return toDto(aiArticle.article, aiArticle, difficultySelection, normalizedFrom, normalizedTo)
    }

    private fun toDto(
        article: Article,
        aiArticle: ArticleAi,
        requestedDifficulties: List<DifficultyLevel>,
        fromLanguage: String,
        toLanguage: String
    ): AiArticleDetail {
        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        val normalizedRequested = normalizeDifficulties(requestedDifficulties)
        val selectedView = selectView(aiArticle, normalizedRequested, fromLanguage, toLanguage)
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
        val titleTo = selectedView?.titleTo ?: selectedView?.titleFrom
        val summaryFrom = selectedView?.summaryFrom
        val summaryTo = selectedView?.summaryTo

        // 取得 tags 和 collections
        val tags = try {
            articleTagService.getArticleTags(articleId).map { tag ->
                ArticleTagSummary(
                    id = tag.id,
                    name = tag.name,
                    description = tag.description,
                    score = tag.score
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }

        val collections = try {
            featuredCollectionService.getArticleCollections(articleId).map { coll ->
                FeaturedCollectionSummary(
                    collectionId = coll.collectionId,
                    title = coll.title,
                    subtitle = coll.subtitle,
                    colorStart = coll.colorStart,
                    colorEnd = coll.colorEnd,
                    score = coll.score
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }

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
            requestedDifficulties = normalizedRequested,
            fromLanguage = finalFromLang,
            toLanguage = finalToLang,
            content = difficultyContent,
            availableDifficulties = availableForPair,
            availableViews = availableViews,
            tags = tags,
            featuredCollections = collections
        )
    }

    fun toDetail(
        aiArticle: ArticleAi,
        requestedDifficulties: List<DifficultyLevel>,
        fromLanguage: String,
        toLanguage: String
    ): AiArticleDetail {
        return toDto(aiArticle.article, aiArticle, requestedDifficulties, fromLanguage, toLanguage)
    }

    private fun selectView(
        aiArticle: ArticleAi,
        requestedDifficulties: List<DifficultyLevel>,
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

        requestedDifficulties.forEach { level ->
            candidates.firstOrNull { it.difficulty == level }?.let { return it }
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
     * 補充缺失的 Tags 和精選專題
     *
     * 檢查所有 AI 文章，如果 tags 和精選專題都為空，則重新調用 AI 生成並配對
     * 注意：同一個 article_id 的所有 views（不同語言/難度）共用同一組 tags 和 collections
     *
     * 處理邏輯：
     * 1. 按 article_id 分組，每個 ID 只處理一次
     * 2. 如果該 ID 已有 tags/collections，跳過（除非 force=true）
     * 3. 如果沒有，才調用 AI 生成
     *
     * @param force 是否強制重新生成（即使已有 tags/collections）
     * @param limit 處理文章數量限制（0 表示全部）
     * @return 補充結果統計
     */
    @Transactional
    fun backfillTagsAndCollections(force: Boolean, limit: Int): BackfillResponse {
        val startTime = System.currentTimeMillis()
        logger.info("Starting backfill tags and collections (force=$force, limit=$limit)")

        // 取得所有 AI 文章（按 article_id 去重，因為同 id 不同語言/難度共用 tags）
        val allAiArticles = articleAiRepository.findAll()
        val uniqueArticles = allAiArticles.distinctBy { it.article.id }
        val articlesToCheck = if (limit > 0) uniqueArticles.take(limit) else uniqueArticles

        logger.info(
            "Total AI articles: {}, unique articles: {}, to check: {}",
            allAiArticles.size, uniqueArticles.size, articlesToCheck.size
        )

        val details = mutableListOf<BackfillDetail>()
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        var needsBackfill = 0

        articlesToCheck.forEach { aiArticle ->
            val articleId = aiArticle.article.id ?: return@forEach
            val title = aiArticle.title ?: aiArticle.article.title ?: "Untitled"

            try {
                // 檢查該 article_id 是否已有 tags 和 collections
                val existingTags = try {
                    articleTagService.getArticleTags(articleId)
                } catch (e: Exception) {
                    logger.warn("Failed to get tags for article {}: {}", articleId, e.message)
                    emptyList()
                }

                val existingCollections = try {
                    featuredCollectionService.getArticleCollections(articleId)
                } catch (e: Exception) {
                    logger.warn("Failed to get collections for article {}: {}", articleId, e.message)
                    emptyList()
                }

                val hasTags = existingTags.isNotEmpty()
                val hasCollections = existingCollections.isNotEmpty()

                if (!force && (hasTags || hasCollections)) {
                    // 已有 tags 或 collections，跳過
                    skippedCount++
                    details.add(
                        BackfillDetail(
                            articleId = articleId,
                            title = title,
                            status = "skipped",
                            tagsAssigned = existingTags.size,
                            collectionsAssigned = existingCollections.size,
                            errorMessage = "Already has tags/collections"
                        )
                    )
                    logger.debug(
                        "Article {} already has tags ({}) or collections ({}), skipping",
                        articleId, existingTags.size, existingCollections.size
                    )
                    return@forEach
                }

                // 需要處理
                needsBackfill++

                // 取得文章的第一個 view 來重新生成 tags 和 collections
                val firstView = aiArticle.views.firstOrNull()
                if (firstView == null) {
                    logger.warn("Article {} has no views, skipping", articleId)
                    skippedCount++
                    details.add(
                        BackfillDetail(
                            articleId = articleId,
                            title = title,
                            status = "skipped",
                            tagsAssigned = 0,
                            collectionsAssigned = 0,
                            errorMessage = "No views available"
                        )
                    )
                    return@forEach
                }

                // 使用 cleanedTextFrom 來重新生成 tags 和 collections
                val cleanedText = firstView.cleanedTextFrom
                if (cleanedText.isNullOrBlank()) {
                    logger.warn("Article {} has no cleaned text, skipping", articleId)
                    skippedCount++
                    details.add(
                        BackfillDetail(
                            articleId = articleId,
                            title = title,
                            status = "skipped",
                            tagsAssigned = 0,
                            collectionsAssigned = 0,
                            errorMessage = "No cleaned text available"
                        )
                    )
                    return@forEach
                }

                // 調用 AI generateTagsAndCollections 來生成 tags 和 collections
                logger.info(
                    "Generating tags/collections for article {} (using light-weight prompt)",
                    articleId
                )
                val aiResult = aiProcessor.generateTagsAndCollections(
                    firstView.titleFrom ?: title,
                    cleanedText
                )

                // 從 AI 回應中提取並保存 tags 和 collections
                var tagsAssigned = 0
                var collectionsAssigned = 0

                // 處理 tags
                val tagsArray = aiResult.path("tags")
                if (tagsArray.isArray && tagsArray.size() > 0) {
                    val tagNames = tagsArray.map { it.asText() }.filter { it.isNotBlank() }
                    if (tagNames.isNotEmpty()) {
                        articleTagService.assignTagsByName(articleId, tagNames)
                        tagsAssigned = tagNames.size
                        logger.info("Assigned {} tags to article {}: {}", tagsAssigned, articleId, tagNames)
                    }
                }

                // 處理 collections
                val collectionsArray = aiResult.path("featured_collections")
                if (collectionsArray.isArray && collectionsArray.size() > 0) {
                    val collectionIds = collectionsArray.map { it.asText() }.filter { it.isNotBlank() }
                    if (collectionIds.isNotEmpty()) {
                        featuredCollectionService.assignCollectionsById(articleId, collectionIds)
                        collectionsAssigned = collectionIds.size
                        logger.info(
                            "Assigned {} collections to article {}: {}",
                            collectionsAssigned,
                            articleId,
                            collectionIds
                        )
                    }
                }

                // 標記為成功（即使沒有分配到任何 tags/collections）
                successCount++
                details.add(
                    BackfillDetail(
                        articleId = articleId,
                        title = title,
                        status = "success",
                        tagsAssigned = tagsAssigned,
                        collectionsAssigned = collectionsAssigned,
                        errorMessage = if (tagsAssigned == 0 && collectionsAssigned == 0) {
                            "AI did not suggest any tags or collections"
                        } else null
                    )
                )

            } catch (e: Exception) {
                logger.error("Failed to backfill article {}: {}", articleId, e.message, e)
                failedCount++
                details.add(
                    BackfillDetail(
                        articleId = articleId,
                        title = title,
                        status = "failed",
                        tagsAssigned = 0,
                        collectionsAssigned = 0,
                        errorMessage = e.message ?: "Unknown error"
                    )
                )
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        logger.info(
            "Backfill completed in {}ms: checked={}, needs={}, success={}, failed={}, skipped={}",
            totalTime, articlesToCheck.size, needsBackfill, successCount, failedCount, skippedCount
        )

        return BackfillResponse(
            totalChecked = articlesToCheck.size,
            needsBackfill = needsBackfill,
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = skippedCount,
            details = details
        )
    }
}
