package com.linli.news.controller

import com.linli.news.service.BackgroundJobService
import org.jobrunr.jobs.JobId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 背景任務控制器 - 展示正確的 JobRunr 使用方式
 *
 * 這個控制器展示如何正確地使用 JobRunr 提交背景任務，
 * 避免 kotlin.jvm.functions.Function0 的 NoSuchBeanDefinitionException 錯誤
 */
@RestController
@RequestMapping("/api/background-jobs")
class BackgroundJobController(
    private val backgroundJobService: BackgroundJobService
) {

    /**
     * 提交 BBC 新聞匯入背景任務
     *
     * 示例 URL: POST /api/background-jobs/import-bbc
     * Body: {
     *   "rssUrl": "https://feeds.bbci.co.uk/news/rss.xml",
     *   "sourceName": "BBC",
     *   "section": "news"
     * }
     */
    @PostMapping("/import-bbc")
    fun importBbcNews(@RequestBody request: ImportRequest): ResponseEntity<JobResponse> {
        val jobId = backgroundJobService.enqueueBbcNewsImport(
            request.rssUrl,
            request.sourceName,
            request.section
        )

        return ResponseEntity.ok(
            JobResponse(
                jobId = jobId.toString(),
                message = "Background job submitted successfully for ${request.sourceName}/${request.section}",
                status = "ENQUEUED"
            )
        )
    }

    /**
     * 快速測試端點 - 提交 BBC 主要新聞匯入任務
     */
    @PostMapping("/test-bbc-main")
    fun testBbcMain(): ResponseEntity<JobResponse> {
        val jobId = backgroundJobService.enqueueBbcNewsImport(
            "https://feeds.bbci.co.uk/news/rss.xml",
            "BBC",
            "news"
        )

        return ResponseEntity.ok(
            JobResponse(
                jobId = jobId.toString(),
                message = "Test BBC main news import job submitted",
                status = "ENQUEUED"
            )
        )
    }
}

/**
 * 匯入請求資料類
 */
data class ImportRequest(
    val rssUrl: String,
    val sourceName: String,
    val section: String
)

/**
 * 任務回應資料類
 */
data class JobResponse(
    val jobId: String,
    val message: String,
    val status: String
)