package com.linli.news.controller

import com.linli.news.repository.ArticleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/ops")
class ImportVerificationController(
    private val articleRepository: ArticleRepository
) {

    /**
     * 驗證匯入狀態的簡易端點。
     * 例：GET /api/ops/import-stats?source=BBC&section=news&hours=24
     */
    @GetMapping("/import-stats")
    fun importStats(
        @RequestParam(defaultValue = "BBC") source: String,
        @RequestParam(required = false) section: String?,
        @RequestParam(defaultValue = "24") hours: Int
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
     * 取樣最近的文章檢視內容長度與片段。
     * 例：GET /api/ops/content-sample?source=BBC&section=news&size=5
     */
    @GetMapping("/content-sample")
    fun contentSample(
        @RequestParam(defaultValue = "BBC") source: String,
        @RequestParam(required = false) section: String?,
        @RequestParam(defaultValue = "5") size: Int
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
     * 近 N 小時的內容摘要（依取樣統計）。
     * 例：GET /api/ops/content-summary?source=BBC&section=news&hours=24&limit=500
     */
    @GetMapping("/content-summary")
    fun contentSummary(
        @RequestParam(defaultValue = "BBC") source: String,
        @RequestParam(required = false) section: String?,
        @RequestParam(defaultValue = "24") hours: Int,
        @RequestParam(defaultValue = "500") limit: Int
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
