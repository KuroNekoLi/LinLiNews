package com.linli.news.controller

import com.linli.news.service.BackgroundJobService
import org.jobrunr.jobs.JobId
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.Hidden

/**
 * 背景任務控制器 - 展示正確的 JobRunr 使用方式
 *
 * 這個控制器展示如何正確地使用 JobRunr 提交背景任務，
 * 避免 kotlin.jvm.functions.Function0 的 NoSuchBeanDefinitionException 錯誤
 *
 * @property backgroundJobService 背景任務服務
 */
@RestController
@RequestMapping("/api/background-jobs")
@Profile("!prod")
@Hidden
@Tag(name = "背景任務測試 API", description = "測試用背景任務端點（僅限非生產環境）")
class BackgroundJobController(
    private val backgroundJobService: BackgroundJobService
) {

    /**
     * 提交 BBC 新聞匯入背景任務
     *
     * @param request 匯入請求參數
     * @return 任務回應結果
     */
    @PostMapping("/import-bbc")
    @Operation(
        summary = "提交 BBC 新聞匯入背景任務",
        description = "將 BBC 新聞匯入任務加入背景佇列執行"
    )
    fun importBbcNews(
        @RequestBody
        @Parameter(description = "匯入請求參數")
        request: ImportRequest
    ): ResponseEntity<JobResponse> {
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
     *
     * @return 任務回應結果
     */
    @PostMapping("/test-bbc-main")
    @Operation(
        summary = "快速測試 BBC 主要新聞匯入",
        description = "快速測試端點，自動提交 BBC 主要新聞區塊的匯入任務"
    )
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
 *
 * @property rssUrl RSS 來源網址
 * @property sourceName 來源名稱（例如 BBC、CNN）
 * @property section 區塊名稱（例如 news、world）
 */
@Schema(description = "匯入請求參數")
data class ImportRequest(
    @field:Schema(description = "RSS 來源網址", example = "https://feeds.bbci.co.uk/news/rss.xml")
    val rssUrl: String,
    @field:Schema(description = "來源名稱", example = "BBC")
    val sourceName: String,
    @field:Schema(description = "區塊名稱", example = "news")
    val section: String
)

/**
 * 任務回應資料類
 *
 * @property jobId 任務唯一識別碼
 * @property message 回應訊息
 * @property status 任務狀態
 */
@Schema(description = "任務回應結果")
data class JobResponse(
    @field:Schema(description = "任務唯一識別碼")
    val jobId: String,
    @field:Schema(description = "回應訊息")
    val message: String,
    @field:Schema(description = "任務狀態", example = "ENQUEUED")
    val status: String
)