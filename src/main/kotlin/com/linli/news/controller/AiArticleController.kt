package com.linli.news.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.DifficultyLevel
import com.linli.news.model.Explanation
import com.linli.news.model.RandomArticlesFilters
import com.linli.news.model.RandomArticlesResponse
import com.linli.news.model.RandomWithProcessingResponse
import com.linli.news.service.AiArticleService
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * AI 文章控制器
 *
 * 提供 AI 已處理文章的查詢功能，以及按需處理單篇文章並儲存的端點
 *
 * @property aiArticleService AI 文章服務
 */
@RestController
@RequestMapping("/api/ai-articles")
@Tag(name = "AI 文章 API", description = "取得 AI 已處理的文章、或按需處理單篇並落地")
class AiArticleController(
    private val aiArticleService: AiArticleService
) {
    private val logger = LoggerFactory.getLogger(AiArticleController::class.java)

    // 使用正確配置的 ObjectMapper，支援 Kotlin 和 JsonProperty 註解
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /**
     * 列出已處理文章（分頁）
     *
     * @param page 頁碼（0 起算）
     * @param size 每頁筆數（預設 20）
     * @return 已處理的 AI 文章分頁資料
     */
    @GetMapping
    @Operation(summary = "列出已處理文章（分頁）", description = "取得所有已由 AI 處理過的文章清單")
    fun list(
        @Parameter(description = "頁碼（0 起算）")
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @Parameter(description = "每頁筆數（預設 20）")
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @Parameter(
            description = "指定文章輸出的難度等級（可複選；預設 Normal；Easy=B1、Normal=B2、Hard=C1、VeryHard=C2）",
            schema = Schema(implementation = DifficultyLevel::class)
        )
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @Parameter(description = "原文語言碼（預設 en）")
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @Parameter(description = "目標語言碼（預設 zh-TW）")
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): Page<AiArticleDetail> {
        val difficultySelection = difficulties?.takeIf { it.isNotEmpty() } ?: listOf(DifficultyLevel.NORMAL)
        return aiArticleService.listProcessed(page, size)
            .map { aiArticleService.toDetail(it, difficultySelection, fromLanguage, toLanguage) }
    }

    /**
     * 取得單篇 AI 文章（無則即時處理並落地）
     *
     * @param articleId 文章 ID
     * @return AI 處理後的文章詳細資料
     */
    @GetMapping("/{articleId}")
    @Operation(
        summary = "取得單篇 AI 文章（無則即時處理並落地）",
        description = "若文章尚未處理，會立即進行 AI 處理並儲存"
    )
    fun getOrProcess(
        @PathVariable articleId: Long,
        @Parameter(
            description = "指定文章輸出的難度等級（可複選；預設 Normal；Easy=B1、Normal=B2、Hard=C1、VeryHard=C2）",
            schema = Schema(implementation = DifficultyLevel::class)
        )
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @Parameter(description = "原文語言碼（預設 en）")
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @Parameter(description = "目標語言碼（預設 zh-TW）")
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): AiArticleDetail {
        return aiArticleService.getOrProcessDto(
            articleId,
            difficulties,
            fromLanguage,
            toLanguage
        )
    }

    /**
     * 隨機取得 n 篇 AI 文章
     *
     * @param count 文章數量（最多 20）
     * @param source 來源過濾，例如 BBC、CNN
     * @param section 區塊過濾，例如 news、world、technology
     * @return 隨機 AI 文章清單與統計資訊
     */
    @GetMapping("/random")
    @Operation(
        summary = "隨機取得 n 篇 AI 文章",
        description = "可選擇以來源/區塊過濾。若庫內沒有資料則回傳空陣列，不會即時解析。"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "成功隨機取得已處理文章",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = RandomArticlesResponse::class))]
            )
        ]
    )
    fun random(
        @Parameter(description = "文章數量（最多 20）")
        @RequestParam(name = "count", defaultValue = "5") count: Int,
        @Parameter(description = "來源過濾，例如 BBC、CNN")
        @RequestParam(name = "source", required = false) source: String?,
        @Parameter(description = "區塊過濾，例如 news、world、technology")
        @RequestParam(name = "section", required = false) section: String?,
        @Parameter(
            description = "指定文章輸出的難度等級（可複選；預設 Normal；Easy=B1、Normal=B2、Hard=C1、VeryHard=C2）",
            schema = Schema(implementation = DifficultyLevel::class)
        )
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @Parameter(description = "原文語言碼（預設 en）")
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @Parameter(description = "目標語言碼（預設 zh-TW）")
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): RandomArticlesResponse {
        val limited = kotlin.math.min(count, 20)
        val difficultySelection = difficulties?.takeIf { it.isNotEmpty() } ?: listOf(DifficultyLevel.NORMAL)
        val (aiList, total) = aiArticleService.random(limited, source, section, difficultySelection, fromLanguage, toLanguage)
        val items = aiList.map { aiArticleService.toDetail(it, difficultySelection, fromLanguage, toLanguage) }
        return RandomArticlesResponse(
            requestedCount = limited,
            actualCount = items.count(),
            totalAvailable = total,
            filters = RandomArticlesFilters(
                source = source,
                section = section,
                difficulties = difficultySelection,
                fromLanguage = fromLanguage,
                toLanguage = toLanguage
            ),
            articles = items
        )
    }

    /**
     * 隨機取得 n 篇文章並自動進行 AI 處理
     *
     * 若文章尚未經 AI 處理，會自動進行處理並儲存
     *
     * @param count 文章數量（最多 20）
     * @param source 來源過濾，例如 BBC、CNN
     * @param section 區塊過濾，例如 news、world、technology
     * @return 隨機 AI 文章清單與統計資訊
     */
    @GetMapping("/random-with-processing")
    @Operation(
        summary = "隨機取得 n 篇文章並自動進行 AI 處理",
        description = "從資料庫隨機抽取文章，若尚未經 AI 處理則自動處理並儲存。可選擇以來源/區塊過濾，並可指定 AI 輸出的難度等級。"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "成功取得或處理隨機文章",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RandomWithProcessingResponse::class)
                    )
                ]
            ),
            ApiResponse(responseCode = "429", description = "AI 配額或速率限制"),
            ApiResponse(responseCode = "500", description = "伺服器處理錯誤")
        ]
    )
    fun randomWithProcessing(
        @Parameter(description = "文章數量（最多 20）")
        @RequestParam(name = "count", defaultValue = "5") count: Int,
        @Parameter(description = "來源過濾，例如 BBC、CNN")
        @RequestParam(name = "source", required = false) source: String?,
        @Parameter(description = "區塊過濾，例如 news、world、technology")
        @RequestParam(name = "section", required = false) section: String?,
        @Parameter(
            description = "指定文章輸出的難度等級（可複選；預設 Normal；Easy=B1、Normal=B2、Hard=C1、VeryHard=C2）",
            schema = Schema(implementation = DifficultyLevel::class)
        )
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @Parameter(description = "原文語言碼（預設 en）")
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @Parameter(description = "目標語言碼（預設 zh-TW）")
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): RandomWithProcessingResponse {
        val limited = kotlin.math.min(count, 20)
        val difficultySelection = difficulties?.takeIf { it.isNotEmpty() } ?: listOf(DifficultyLevel.NORMAL)
        val (aiList, total, processedCount) =
            aiArticleService.randomWithRewriteAndProcessing(limited, source, section, difficultySelection, fromLanguage, toLanguage)
        val items = aiList.map { aiArticleService.toDetail(it, difficultySelection, fromLanguage, toLanguage) }
        return RandomWithProcessingResponse(
            requestedCount = limited,
            actualCount = items.count(),
            totalAvailable = total,
            processedCount = processedCount,
            filters = RandomArticlesFilters(
                source = source,
                section = section,
                difficulties = difficultySelection,
                fromLanguage = fromLanguage,
                toLanguage = toLanguage
            ),
            articles = items
        )
    }

    /**
     * 調試端點：查看 AI 產生的原始 JSON
     *
     * @param id 文章 ID
     * @return AI 產生的原始 JSON 資料
     */
    @GetMapping("/{id}/debug-raw-json")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[測試] 查看 AI 原始 JSON",
        description = "調試用端點，檢視 AI 產生的原始 JSON 資料（僅限非生產環境）"
    )
    fun debugRawJson(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        return try {
            val aiArticle = aiArticleService.listProcessed(0, 1000).content
                .find { it.article.id == id }
                ?: return ResponseEntity.notFound().build()

            val contentSummary = aiArticle.views.associate { view ->
                val key = "${view.fromLanguage}->${view.toLanguage}:${view.difficulty.name}"
                val hasExplanation =
                    view.vocabularyItems.isNotEmpty() ||
                        view.grammarItems.isNotEmpty() ||
                        view.sentencePatternItems.isNotEmpty() ||
                        view.phraseIdiomItems.isNotEmpty() ||
                        view.comprehensionQuestions.isNotEmpty() ||
                        !view.legacyExplanationJson.isNullOrBlank()

                key to mapOf(
                    "difficulty" to view.difficulty.name,
                    "fromLanguage" to view.fromLanguage,
                    "toLanguage" to view.toLanguage,
                    "hasExplanation" to hasExplanation,
                    "cleanedTextFromLength" to (view.cleanedTextFrom?.length ?: 0),
                    "cleanedTextToLength" to (view.cleanedTextTo?.length ?: 0)
                )
            }

            val representativeTitle = aiArticle.views
                .firstOrNull { it.toLanguage.equals("zh-TW", ignoreCase = true) && !it.titleTo.isNullOrBlank() }
                ?.titleTo
                ?: aiArticle.views.firstOrNull { !it.titleTo.isNullOrBlank() }?.titleTo
                ?: aiArticle.views.firstOrNull { !it.titleFrom.isNullOrBlank() }?.titleFrom
                ?: aiArticle.title
                ?: aiArticle.article.title

            val responseBody: Map<String, Any?> = mapOf(
                "articleId" to id,
                "titleZh" to representativeTitle,
                "availableViews" to aiArticle.views.map {
                    mapOf(
                        "difficulty" to it.difficulty.name,
                        "fromLanguage" to it.fromLanguage,
                        "toLanguage" to it.toLanguage
                    )
                },
                "contentSummary" to contentSummary
            )

            ResponseEntity.ok(responseBody)
        } catch (e: Exception) {
            val errorBody: Map<String, Any?> = mapOf(
                "error" to (e.message ?: "Unknown error"),
                "errorType" to e.javaClass.simpleName
            )
            ResponseEntity.status(500).body(errorBody)
        }
    }

    /**
     * 測試端點：重新處理並查看詳細診斷信息
     *
     * @param id 文章 ID
     * @return 重新處理後的診斷資訊
     */
    @PostMapping("/{id}/reprocess-with-debug")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[測試] 重新處理並診斷",
        description = "測試用端點，重新處理文章並回傳詳細診斷信息（僅限非生產環境）"
    )
    fun reprocessArticleWithDebug(
        @PathVariable id: Long,
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            val difficultySelection = difficulties?.takeIf { it.isNotEmpty() } ?: listOf(DifficultyLevel.NORMAL)
            val result = aiArticleService.processAndOverwriteDto(id, difficultySelection, fromLanguage, toLanguage)
            val content = result.content
            val explanation = content?.explanation
            val responseBody = buildMap<String, Any?> {
                put("success", true)
                put("articleId", id)
                put("hasExplanation", explanation != null)
                explanation?.let { exp ->
                    put(
                        "explanationFields",
                        mapOf(
                            "hasVocabulary" to (exp.vocabulary?.isNotEmpty() == true),
                            "hasGrammar" to (exp.grammar?.isNotEmpty() == true),
                            "hasSentencePatterns" to (exp.sentencePatterns?.isNotEmpty() == true),
                            "hasPhrasesIdioms" to (exp.phrasesIdioms?.isNotEmpty() == true),
                            "hasComprehensionMcq" to (exp.comprehensionMcq?.isNotEmpty() == true),
                            "vocabularyCount" to (exp.vocabulary?.size ?: 0),
                            "grammarCount" to (exp.grammar?.size ?: 0),
                            "mcqCount" to (exp.comprehensionMcq?.size ?: 0)
                        )
                    )
                }
                put("message", "文章已重新處理")
                put("titleZh", result.titleZh)
                put("fromLanguage", result.fromLanguage)
                put("toLanguage", result.toLanguage)
                put("cleanedTextFromLength", content?.cleanedTextFrom?.length ?: 0)
                put("cleanedTextToLength", content?.cleanedTextTo?.length ?: 0)
            }

            ResponseEntity.ok(responseBody)
        } catch (e: Exception) {
            val errorBody: Map<String, Any?> = mapOf(
                "success" to false,
                "articleId" to id,
                "error" to (e.message ?: "Unknown error"),
                "errorType" to e.javaClass.simpleName
            )
            ResponseEntity.status(500).body(errorBody)
        }
    }

    /**
     * 測試端點：測試 JSON 解析
     *
     * @param jsonString JSON 字串
     * @return JSON 解析結果
     */
    @PostMapping("/test-json-parsing")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[測試] 測試 JSON 解析",
        description = "測試用端點，驗證 Explanation JSON 的解析功能（僅限非生產環境）"
    )
    fun testJsonParsing(@RequestBody jsonString: String): ResponseEntity<Map<String, Any?>> {
        return try {
            val explanation = objectMapper.readValue(jsonString, Explanation::class.java)
            val responseBody: Map<String, Any?> = mapOf(
                "success" to true,
                "vocabularyCount" to (explanation.vocabulary?.size ?: 0),
                "grammarCount" to (explanation.grammar?.size ?: 0),
                "sentencePatternsCount" to (explanation.sentencePatterns?.size ?: 0),
                "phrasesIdiomsCount" to (explanation.phrasesIdioms?.size ?: 0),
                "comprehensionMcqCount" to (explanation.comprehensionMcq?.size ?: 0),
                "hasCompleteStructure" to (
                    explanation.vocabulary != null &&
                        explanation.grammar != null &&
                        explanation.sentencePatterns != null &&
                        explanation.phrasesIdioms != null &&
                        explanation.comprehensionMcq != null
                    ),
                "parsedExplanation" to explanation
            )

            ResponseEntity.ok(responseBody)
        } catch (e: Exception) {
            val errorBody: Map<String, Any?> = mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error"),
                "errorType" to e.javaClass.simpleName,
                "detailedMessage" to e.toString()
            )
            ResponseEntity.status(400).body(errorBody)
        }
    }

}
