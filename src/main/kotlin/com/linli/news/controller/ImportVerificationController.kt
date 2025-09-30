package com.linli.news.controller

import com.linli.news.repository.ArticleRepository
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.Hidden

/**
 * 匯入驗證控制器
 *
 * 提供運維用的驗證端點，用於檢查資料匯入狀態與內容品質
 * 僅限非生產環境使用
 *
 * @property articleRepository 文章資料庫存取物件
 */
@RestController
@RequestMapping("/api/ops")
@Profile("!prod")
@Hidden
@Tag(name = "運維驗證 API", description = "運維用驗證端點（僅限非生產環境）")
class ImportVerificationController(
    private val articleRepository: ArticleRepository
) {

    /**
     * 驗證匯入狀態的簡易端點
     *
     * @param source 來源名稱（預設為 BBC）
     * @param section 區塊名稱（選填）
     * @param hours 統計時間範圍（小時，預設 24）
     * @return 匯入統計資料
     */
    @GetMapping("/import-stats")
    @Operation(
        summary = "取得匯入統計資料",
        description = "檢視指定來源與區塊在特定時間範圍內的匯入統計"
    )
    fun importStats(
        @RequestParam(defaultValue = "BBC")
        @Parameter(description = "來源名稱", example = "BBC")
        source: String,
        @RequestParam(required = false)
        @Parameter(description = "區塊名稱（選填）", example = "news")
        section: String?,
        @RequestParam(defaultValue = "24")
        @Parameter(description = "統計時間範圍（小時）", example = "24")
        hours: Int
    ): ResponseEntity<Map<String, Any>> {
        val now = OffsetDateTime.now()
        val after = now.minusHours(hours.toLong())

        val totalCount = if (section.isNullOrBlank()) {
            articleRepository.countBySource_Name(source)
        } else {
            articleRepository.countBySource_NameAndSource_Section(source, section)
        }

        val recentCount = if (section.isNullOrBlank()) {
            articleRepository.countBySource_NameAndCreatedAtAfter(source, after)
        } else {
            articleRepository.countBySource_NameAndSource_SectionAndCreatedAtAfter(source, section, after)
        }

        val body = mapOf(
            "source" to source,
            "section" to (section ?: "ALL"),
            "hours" to hours,
            "totalCount" to totalCount,
            "recentCount" to recentCount,
            "serverTime" to now.toString()
        )
        return ResponseEntity.ok(body)
    }

    /**
     * 取樣最近的文章檢視內容長度與片段
     *
     * @param source 來源名稱（預設為 BBC）
     * @param section 區塊名稱（選填）
     * @param size 取樣數量（預設 5，最多 50）
     * @return 文章內容取樣資料
     */
    @GetMapping("/content-sample")
    @Operation(
        summary = "取得文章內容取樣",
        description = "檢視最近文章的內容長度與片段，用於驗證內容品質"
    )
    fun contentSample(
        @RequestParam(defaultValue = "BBC")
        @Parameter(description = "來源名稱", example = "BBC")
        source: String,
        @RequestParam(required = false)
        @Parameter(description = "區塊名稱（選填）", example = "news")
        section: String?,
        @RequestParam(defaultValue = "5")
        @Parameter(description = "取樣數量", example = "5")
        size: Int
    ): ResponseEntity<Map<String, Any>> {
        val pageSize = size.coerceIn(1, 50)
        val pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val page = if (section.isNullOrBlank()) {
            articleRepository.findBySource_Name(source, pageable)
        } else {
            articleRepository.findBySource_NameAndSource_Section(source, section, pageable)
        }

        val items = page.content.map { a ->
            val content = a.contentHtml ?: ""
            mapOf(
                "id" to (a.id ?: -1),
                "title" to (a.title ?: ""),
                "publishedAt" to (a.publishedAt?.toString() ?: ""),
                "contentLength" to content.length,
                "hasFullContent" to (content.length > 200),
                "url" to a.effectiveUrl.orEmpty().ifBlank { a.originalUrl },
                "snippet" to content.take(300)
            )
        }

        val body = mapOf(
            "source" to source,
            "section" to (section ?: "ALL"),
            "sampleSize" to items.size,
            "items" to items
        )
        return ResponseEntity.ok(body)
    }

    /**
     * 近 N 小時的內容摘要（依取樣統計）
     *
     * @param source 來源名稱（預設為 BBC）
     * @param section 區塊名稱（選填）
     * @param hours 統計時間範圍（小時，預設 24）
     * @param limit 取樣上限（預設 500，最多 2000）
     * @return 內容品質摘要統計
     */
    @GetMapping("/content-summary")
    @Operation(
        summary = "取得內容品質摘要",
        description = "統計指定時間範圍內的文章內容品質指標"
    )
    fun contentSummary(
        @RequestParam(defaultValue = "BBC")
        @Parameter(description = "來源名稱", example = "BBC")
        source: String,
        @RequestParam(required = false)
        @Parameter(description = "區塊名稱（選填）", example = "news")
        section: String?,
        @RequestParam(defaultValue = "24")
        @Parameter(description = "統計時間範圍（小時）", example = "24")
        hours: Int,
        @RequestParam(defaultValue = "500")
        @Parameter(description = "取樣上限", example = "500")
        limit: Int
    ): ResponseEntity<Map<String, Any>> {
        val pageSize = limit.coerceIn(50, 2000)
        val pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val page = if (section.isNullOrBlank()) {
            articleRepository.findBySource_Name(source, pageable)
        } else {
            articleRepository.findBySource_NameAndSource_Section(source, section, pageable)
        }

        val items = page.content
        val now = java.time.OffsetDateTime.now()
        val after = now.minusHours(hours.toLong())
        val recent = items.filter { it.createdAt.isAfter(after) }
        val withContent = recent.filter { (it.contentHtml ?: "").length > 0 }
        val gt200 = recent.filter { (it.contentHtml ?: "").length > 200 }
        val gt1000 = recent.filter { (it.contentHtml ?: "").length > 1000 }

        val body = mapOf(
            "source" to source,
            "section" to (section ?: "ALL"),
            "windowHours" to hours,
            "sampled" to items.size,
            "recent" to recent.size,
            "withContent" to withContent.size,
            "gt200" to gt200.size,
            "gt1000" to gt1000.size
        )
        return ResponseEntity.ok(body)
    }
}
