package com.linli.news.controller

import com.linli.news.domain.Article
import com.linli.news.repository.ArticleRepository
import com.linli.news.integration.FullTextRssClient
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import java.util.regex.Pattern
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.Hidden
import com.linli.news.service.FeedImportService
import com.linli.news.service.AiArticleService
import com.linli.news.model.AiArticleDetail
import com.linli.news.model.DifficultyLevel
import org.slf4j.LoggerFactory

/**
 * 文章資料結構
 *
 * @property id 文章 ID
 * @property sourceName 來源名稱，例如 BBC、CNN
 * @property section 來源區塊，例如 news、world、technology
 * @property title 文章標題
 * @property publishedAt 發布時間（ISO-8601 字串）
 * @property originalUrl 原始文章連結（可跳轉到原站）
 * @property contentHtml 可直接渲染的完整 HTML 內容
 * @property textContent 自 HTML 萃取的純文字（便於搜尋/摘要）
 */
@Schema(name = "Article", description = "文章資料（所有端點統一結構）")
data class ArticleDetail(
    @Schema(description = "文章ID")
    val id: Long,
    @Schema(description = "來源名稱，例如 BBC、CNN")
    val sourceName: String?,
    @Schema(description = "來源區塊，例如 news、world、technology")
    val section: String?,
    @Schema(description = "文章標題")
    val title: String?,
    @Schema(description = "發布時間（ISO-8601 字串）")
    val publishedAt: String?,
    @Schema(description = "原始文章連結（可跳轉到原站）")
    val originalUrl: String,
    @Schema(description = "可直接渲染的完整 HTML 內容")
    val contentHtml: String?,
    @Schema(description = "自 HTML 萃取的純文字（便於搜尋/摘要）")
    val textContent: String?
)

/**
 * 文章查詢控制器
 *
 * 提供文章列表、單篇詳情、隨機抽取與內容補齊等功能
 *
 * @property articleRepository 文章資料庫存取物件
 * @property fullTextRssClient Full-Text RSS 客戶端
 * @property feedImportService RSS 匯入服務
 * @property aiArticleService AI 文章服務
 */
@RestController
@RequestMapping("/api/articles")
@Tag(name = "文章 API", description = "提供文章列表、單篇詳情、隨機抽取與內容補齊等功能")
class ArticleQueryController(
    private val articleRepository: ArticleRepository,
    private val fullTextRssClient: FullTextRssClient,
    private val feedImportService: FeedImportService,
    private val aiArticleService: AiArticleService
) {
    private val logger = LoggerFactory.getLogger(ArticleQueryController::class.java)

    /**
     * 分頁查詢最新文章，可依來源與區塊過濾
     *
     * @param page 頁碼（0 起算）
     * @param size 每頁筆數（預設 20）
     * @param source 來源過濾（如 BBC、CNN）
     * @param section 區塊過濾（如 world、technology）
     * @return 文章分頁資料
     */
    @GetMapping
    @Operation(
        summary = "取得文章列表（分頁）",
        description = "回傳的每一筆皆為完整文章結構（與單篇/隨機相同）。可用來源與區塊過濾。支援 section 範例：news、world、uk、technology、business、health、science_and_environment、entertainment_and_arts、politics、education、world_africa、world_asia、world_europe、world_middle_east、world_us_and_canada、world_latin_america"
    )
    fun list(
        @Parameter(description = "頁碼（0 起算）")
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @Parameter(description = "每頁筆數（預設 20）")
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @Parameter(description = "來源過濾，例如 BBC、CNN")
        @RequestParam(name = "source", required = false) source: String?,
        @Parameter(description = "區塊過濾，例如 news、world、technology")
        @RequestParam(name = "section", required = false) section: String?
    ): Page<ArticleDetail> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"))
        val pageData: Page<Article> = when {
            source != null && section != null -> articleRepository.findBySource_NameAndSource_Section(
                source,
                section,
                pageable
            )

            source != null -> articleRepository.findBySource_Name(source, pageable)
            section != null -> articleRepository.findBySource_Section(section, pageable)
            else -> articleRepository.findAll(pageable)
        }
        return pageData.map { a ->
            val articleId = requireNotNull(a.id) { "Article must have an ID" }
            ArticleDetail(
                id = articleId,
                sourceName = a.source.name,
                section = a.source.section,
                title = a.title,
                publishedAt = a.publishedAt?.toString(),
                originalUrl = a.originalUrl,
                contentHtml = a.contentHtml,
                textContent = extractTextFromHtml(a.contentHtml)
            )
        }
    }

    /**
     * 取得單篇文章詳細資訊
     *
     * @param id 文章 ID
     * @return 文章詳細資料
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "取得單篇文章詳情",
        description = "回傳完整文章結構，包含可直接渲染的 contentHtml 與純文字 textContent"
    )
    fun getArticle(@PathVariable id: Long): ResponseEntity<ArticleDetail> {
        val article = articleRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val articleId = requireNotNull(article.id) { "Article must have an ID" }
        val textContent = extractTextFromHtml(article.contentHtml)

        return ResponseEntity.ok(
            ArticleDetail(
                id = articleId,
                sourceName = article.source.name,
                section = article.source.section,
                title = article.title,
                publishedAt = article.publishedAt?.toString(),
                originalUrl = article.originalUrl,
                contentHtml = article.contentHtml,
                textContent = textContent
            )
        )
    }

    /**
     * 為指定文章補充完整的 HTML 內容
     *
     * @param id 文章 ID
     * @return 補充結果
     */
    @PostMapping("/{id}/fetch-content")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[測試] 補齊指定文章的全文內容",
        description = "使用 Full-Text RSS 提取內容並更新資料庫（僅限非生產環境）"
    )
    @Transactional
    fun fetchFullContent(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        val article = articleRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        try {
            // 使用 Full-Text RSS 的 extract API 取得完整文章內容
            val fullTextResponse = fullTextRssClient.fetchArticleFullText(article.originalUrl)

            // 更新文章內容
            article.contentHtml = fullTextResponse.content
            articleRepository.save(article)

            return ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "message" to "文章內容已成功更新",
                    "contentLength" to (fullTextResponse.content?.length ?: 0)
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "取得文章內容失敗: ${e.message}"
                )
            )
        }
    }

    /**
     * 批次為所有沒有完整內容的文章補充 HTML 內容
     *
     * @return 批次處理結果
     */
    @PostMapping("/fetch-all-missing-content")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[測試] 批次補齊缺少全文的文章",
        description = "找出所有 contentHtml 為空的文章並嘗試補齊（僅限非生產環境）"
    )
    @Transactional
    fun fetchAllMissingContent(): ResponseEntity<Map<String, Any>> {
        // 查找所有沒有完整內容的文章
        val articlesWithoutContent = articleRepository.findAll().filter {
            it.contentHtml.isNullOrBlank()
        }

        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()

        articlesWithoutContent.forEach { article ->
            try {
                val fullTextResponse = fullTextRssClient.fetchArticleFullText(article.originalUrl)

                article.contentHtml = fullTextResponse.content
                articleRepository.save(article)
                successCount++

                logger.info("已更新文章: ${article.title}")
            } catch (e: Exception) {
                failureCount++
                val errorMsg = "文章 ID ${article.id} (${article.title}): ${e.message}"
                errors.add(errorMsg)
                logger.error("更新文章失敗: $errorMsg")
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "totalArticles" to articlesWithoutContent.size,
                "successCount" to successCount,
                "failureCount" to failureCount,
                "errors" to errors
            )
        )
    }

    /**
     * 隨機取得 n 篇有完整內容的文章
     *
     * @param count 要取得的文章數量（預設 5 篇，最多 20 篇）
     * @param source 來源過濾（如 BBC、CNN）
     * @param section 區塊過濾（如 world、technology）
     * @return 隨機文章清單與統計資訊
     */
    @GetMapping("/random")
    @Operation(
        summary = "隨機取得 n 篇文章",
        description = "可選擇以來源、區塊過濾。回傳的每一筆皆為完整文章結構（與列表/單篇相同）。"
    )
    fun getRandomArticles(
        @Parameter(description = "文章數量（最多 20）")
        @RequestParam(name = "count", defaultValue = "5") count: Int,
        @Parameter(description = "來源過濾，例如 BBC、CNN")
        @RequestParam(name = "source", required = false) source: String?,
        @Parameter(description = "區塊過濾，例如 news、world、technology")
        @RequestParam(name = "section", required = false) section: String?
    ): ResponseEntity<Map<String, Any>> {
        // 限制最多 20 篇文章
        val actualCount = minOf(count, 20)

        try {
            // 取得所有有完整內容的文章
            val allArticles = when {
                source != null && section != null ->
                    articleRepository.findBySource_NameAndSource_Section(
                        source,
                        section,
                        PageRequest.of(0, Int.MAX_VALUE)
                    ).content

                source != null ->
                    articleRepository.findBySource_Name(source, PageRequest.of(0, Int.MAX_VALUE)).content

                section != null ->
                    articleRepository.findBySource_Section(section, PageRequest.of(0, Int.MAX_VALUE)).content

                else ->
                    articleRepository.findAll().toList()
            }.filter { !it.contentHtml.isNullOrBlank() }

            if (allArticles.isEmpty()) {
                return ResponseEntity.ok(
                    mapOf<String, Any>(
                        "message" to "沒有找到有完整內容的文章",
                        "totalAvailable" to 0,
                        "articles" to emptyList<Any>()
                    )
                )
            }

            // 隨機選擇文章
            val randomArticles = if (allArticles.size <= actualCount) {
                allArticles
            } else {
                allArticles.shuffled().take(actualCount)
            }

            // 轉換為詳細格式
            val articleDetails = randomArticles.map { article ->
                val articleId = requireNotNull(article.id) { "Article must have an ID" }
                val textContent = extractTextFromHtml(article.contentHtml)
                ArticleDetail(
                    id = articleId,
                    sourceName = article.source.name,
                    section = article.source.section,
                    title = article.title,
                    publishedAt = article.publishedAt?.toString(),
                    originalUrl = article.originalUrl,
                    contentHtml = article.contentHtml,
                    textContent = textContent
                )
            }

            return ResponseEntity.ok(
                mapOf<String, Any>(
                    "message" to "成功取得 ${articleDetails.size} 篇隨機文章",
                    "requestedCount" to actualCount,
                    "actualCount" to articleDetails.size,
                    "totalAvailable" to allArticles.size,
                    "filters" to mapOf(
                        "source" to source,
                        "section" to section
                    ),
                    "articles" to articleDetails
                )
            )

        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "message" to "取得隨機文章失敗: ${e.message}"
                )
            )
        }
    }

    /**
     * 清理無效文章（如報紙頭版合集、音頻視頻連結等）
     *
     * @return 清理結果統計
     */
    @PostMapping("/cleanup-invalid-articles")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[管理] 清理無效文章",
        description = "移除報紙頭版合集、音頻/影片入口等非新聞文章（僅限非生產環境）"
    )
    @Transactional
    fun cleanupInvalidArticles(): ResponseEntity<Map<String, Any>> {
        val invalidPatterns = listOf(
            "the papers",
            "paper review",
            "front page",
            "sounds play",
            "iplayer",
            "play now"
        )

        val invalidUrlPatterns = listOf(
            "sounds/play",
            "iplayer/episode",
            "/video/",
            "/videos/"
        )

        val allArticles = articleRepository.findAll()
        val invalidArticles = allArticles.filter { article ->
            val titleLower = article.title?.lowercase() ?: ""
            val urlLower = article.originalUrl.lowercase()

            invalidPatterns.any { pattern -> titleLower.contains(pattern) } ||
                    invalidUrlPatterns.any { pattern -> urlLower.contains(pattern) }
        }

        val deletedTitles = mutableListOf<String>()
        invalidArticles.forEach { article ->
            deletedTitles.add(article.title ?: "無標題")
            articleRepository.delete(article)
        }

        return ResponseEntity.ok(
            mapOf<String, Any>(
                "success" to true,
                "message" to "清理完成",
                "totalDeleted" to invalidArticles.size,
                "deletedTitles" to deletedTitles.take(10), // 只顯示前10個
                "remainingArticles" to (allArticles.size - invalidArticles.size)
            )
        )
    }

    /**
     * 立即匯入 BBC RSS（用於測試排程的來源）
     *
     * @param section 要匯入的 BBC 區塊/子分類
     * @return 匯入結果
     */
    @PostMapping("/admin/import-bbc")
    @Profile("!prod")
    @Hidden
    @Operation(
        summary = "[管理] 立即匯入 BBC RSS（用於測試排程的來源）",
        description = "依 section 觸發對應 BBC RSS 的一次性匯入。支援：news、world、uk、technology、business、health、science_and_environment、entertainment_and_arts、politics、education、world_africa、world_asia、world_europe、world_middle_east、world_us_and_canada、world_latin_america（僅限非生產環境）"
    )
    @Transactional
    fun adminImportBbc(
        @Parameter(description = "要匯入的 BBC 區塊/子分類")
        @RequestParam("section") section: String
    ): ResponseEntity<Map<String, Any>> {
        val urlMap = mapOf(
            "news" to "https://feeds.bbci.co.uk/news/rss.xml",
            "world" to "https://feeds.bbci.co.uk/news/world/rss.xml",
            "uk" to "https://feeds.bbci.co.uk/news/uk/rss.xml",
            "technology" to "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "business" to "https://feeds.bbci.co.uk/news/business/rss.xml",
            "health" to "https://feeds.bbci.co.uk/news/health/rss.xml",
            "science_and_environment" to "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "entertainment_and_arts" to "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            "politics" to "https://feeds.bbci.co.uk/news/politics/rss.xml",
            "education" to "https://feeds.bbci.co.uk/news/education/rss.xml",
            "world_africa" to "https://feeds.bbci.co.uk/news/world/africa/rss.xml",
            "world_asia" to "https://feeds.bbci.co.uk/news/world/asia/rss.xml",
            "world_europe" to "https://feeds.bbci.co.uk/news/world/europe/rss.xml",
            "world_middle_east" to "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml",
            "world_us_and_canada" to "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
            "world_latin_america" to "https://feeds.bbci.co.uk/news/world/latin_america/rss.xml",
        )

        val feedUrl = urlMap[section]
            ?: return ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to "不支援的 section: $section"
                )
            )

        val imported = feedImportService.importFromFeed(
            rssUrl = feedUrl,
            sourceName = "BBC",
            section = section
        )

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "已匯入 $imported 篇（可能因去重而小於 feed 項目數）",
                "section" to section,
                "rssUrl" to feedUrl,
                "imported" to imported
            )
        )
    }

    /**
     * 使用 GPT-4o 解析並落地後回傳（統一文章物件）
     *
     * @param id 文章 ID
     * @return AI 處理後的文章詳細資料
     */
    @PostMapping("/{id}/ai/process")
    @Operation(
        summary = "使用 GPT-4o 解析並落地後回傳（統一文章物件）",
        description = "輸入文章 ID；若未解析則即時解析並存庫，最後回傳統一文章物件（含 AI 欄位）。"
    )
    fun aiProcessArticle(
        @PathVariable id: Long,
        @Parameter(
            description = "指定文章輸出的難度等級（可複選；預設 Normal；Easy=B1、Normal=B2、Hard=C1、VeryHard=C2）",
            schema = Schema(implementation = DifficultyLevel::class)
        )
        @RequestParam(name = "difficulty", required = false) difficulties: List<DifficultyLevel>?,
        @Parameter(description = "原文語言碼（預設 en）")
        @RequestParam(name = "fromLanguage", defaultValue = "en") fromLanguage: String,
        @Parameter(description = "目標語言碼（預設 zh-TW）")
        @RequestParam(name = "toLanguage", defaultValue = "zh-TW") toLanguage: String
    ): ResponseEntity<AiArticleDetail> {
        val article = articleRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (article.contentHtml.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        return try {
            val difficultySelection = difficulties?.takeIf { it.isNotEmpty() } ?: listOf(DifficultyLevel.NORMAL)
            val dto = aiArticleService.processAndOverwriteDto(id, difficultySelection, fromLanguage, toLanguage)
            ResponseEntity.ok(dto)
        } catch (e: com.linli.news.service.AiQuotaException) {
            // Map to 429; client should stop retrying until quota is fixed.
            ResponseEntity.status(429)
                .header("X-Error", "AI quota exceeded; please check billing")
                .build()
        }
    }

    /**
     * 從 HTML 中萃取純文字內容
     *
     * @param html HTML 內容
     * @return 純文字內容（移除所有 HTML 標籤）
     */
    private fun extractTextFromHtml(html: String?): String? {
        if (html == null) {
            return null
        }
        return Pattern.compile("<[^>]*>").matcher(html).replaceAll("")
    }
}
