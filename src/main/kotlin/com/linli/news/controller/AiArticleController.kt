package com.linli.news.controller

import com.linli.news.domain.ArticleAi
import com.linli.news.service.AiArticleService
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.Explanation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.http.ResponseEntity

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

    // 使用正確配置的 ObjectMapper，支援 Kotlin 和 JsonProperty 註解
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val htmlTagPattern: Pattern = Pattern.compile("<[^>]*>")

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
        @RequestParam(name = "size", defaultValue = "20") size: Int
    ): Page<AiArticleDetail> {
        return aiArticleService.listProcessed(page, size).map { toDto(it) }
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
    fun getOrProcess(@PathVariable articleId: Long): AiArticleDetail {
        val ai = aiArticleService.getOrProcess(articleId)
        return toDto(ai)
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
    fun random(
        @Parameter(description = "文章數量（最多 20）")
        @RequestParam(name = "count", defaultValue = "5") count: Int,
        @Parameter(description = "來源過濾，例如 BBC、CNN")
        @RequestParam(name = "source", required = false) source: String?,
        @Parameter(description = "區塊過濾，例如 news、world、technology")
        @RequestParam(name = "section", required = false) section: String?
    ): Map<String, Any> {
        val limited = kotlin.math.min(count, 20)
        val (aiList, total) = aiArticleService.random(limited, source, section)
        val items = aiList.map { toDto(it) }
        return mapOf(
            "message" to "success",
            "requestedCount" to limited,
            "actualCount" to items.count(),
            "totalAvailable" to total,
            "filters" to mapOf("source" to source, "section" to section),
            "articles" to items
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
    @Operation(
        summary = "[測試] 查看 AI 原始 JSON",
        description = "調試用端點，檢視 AI 產生的原始 JSON 資料（僅限非生產環境）"
    )
    fun debugRawJson(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            val aiArticle = aiArticleService.listProcessed(0, 1000).content
                .find { it.article.id == id }
                ?: return ResponseEntity.notFound().build()

            ResponseEntity.ok(
                mapOf(
                    "articleId" to id,
                    "explanationJson" to (aiArticle.explanation ?: "null"),
                    "explanationLength" to (aiArticle.explanation?.length ?: 0),
                    "hasExplanation" to !aiArticle.explanation.isNullOrBlank(),
                    "titleZh" to aiArticle.titleZh,
                    "cleanedTextLength" to (aiArticle.cleanedText?.length ?: 0)
                ) as Map<String, Any>
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                mapOf(
                    "error" to e.message,
                    "errorType" to e.javaClass.simpleName
                ) as Map<String, Any>
            )
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
    @Operation(
        summary = "[測試] 重新處理並診斷",
        description = "測試用端點，重新處理文章並回傳詳細診斷信息（僅限非生產環境）"
    )
    fun reprocessArticleWithDebug(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            val result = aiArticleService.processAndOverwriteDto(id)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "articleId" to id,
                    "hasExplanation" to (result.explanation != null),
                    "explanationFields" to if (result.explanation != null) {
                        mapOf(
                            "hasVocabulary" to (result.explanation!!.vocabulary?.isNotEmpty() == true),
                            "hasGrammar" to (result.explanation!!.grammar?.isNotEmpty() == true),
                            "hasSentencePatterns" to (result.explanation!!.sentencePatterns?.isNotEmpty() == true),
                            "hasPhrasesIdioms" to (result.explanation!!.phrasesIdioms?.isNotEmpty() == true),
                            "hasComprehensionMcq" to (result.explanation!!.comprehensionMcq?.isNotEmpty() == true),
                            "vocabularyCount" to (result.explanation!!.vocabulary?.size ?: 0),
                            "grammarCount" to (result.explanation!!.grammar?.size ?: 0),
                            "mcqCount" to (result.explanation!!.comprehensionMcq?.size ?: 0)
                        )
                    } else null,
                    "message" to "文章已重新處理",
                    "titleZh" to result.titleZh,
                    "cleanedTextLength" to (result.cleanedText?.length ?: 0),
                    "cleanedTextZhLength" to (result.cleanedTextZh?.length ?: 0)
                ) as Map<String, Any>
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "articleId" to id,
                    "error" to e.message,
                    "errorType" to e.javaClass.simpleName
                ) as Map<String, Any>
            )
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
    @Operation(
        summary = "[測試] 測試 JSON 解析",
        description = "測試用端點，驗證 Explanation JSON 的解析功能（僅限非生產環境）"
    )
    fun testJsonParsing(@RequestBody jsonString: String): ResponseEntity<Map<String, Any>> {
        return try {
            val explanation = objectMapper.readValue(jsonString, Explanation::class.java)
            ResponseEntity.ok(
                mapOf(
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
                ) as Map<String, Any>
            )
        } catch (e: Exception) {
            ResponseEntity.status(400).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorType" to e.javaClass.simpleName,
                    "detailedMessage" to e.toString()
                ) as Map<String, Any>
            )
        }
    }

    /**
     * 將 ArticleAi 實體轉換為 DTO
     *
     * @param ai AI 文章實體
     * @return AI 文章詳細資料 DTO
     */
    private fun toDto(ai: ArticleAi): AiArticleDetail {
        val a = ai.article
        val text = a.contentHtml?.let { htmlTagPattern.matcher(it).replaceAll("") }
        val explanationObj: Explanation? = ai.explanation?.let { explanationJson ->
            try {
                val parsed = objectMapper.readValue(explanationJson, Explanation::class.java)
                parsed
            } catch (e: Exception) {
                // 記錄解析失敗，但返回 null 而不是空對象
                println("Failed to parse explanation in controller for article ${ai.article.id}: ${e.message}")
                null
            }
        }

        return AiArticleDetail(
            id = a.id!!,
            sourceName = a.source.name,
            section = a.source.section,
            title = a.title,
            publishedAt = a.publishedAt?.toString(),
            originalUrl = a.originalUrl,
            contentHtml = a.contentHtml,
            textContent = text,
            titleZh = ai.titleZh,
            cleanedText = ai.cleanedText,
            cleanedTextZh = ai.cleanedTextZh,
            explanation = explanationObj
        )
    }
}
