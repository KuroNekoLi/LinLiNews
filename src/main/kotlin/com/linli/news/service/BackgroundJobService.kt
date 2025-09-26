package com.linli.news.service

import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.annotations.Job
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 背景任務服務 - 展示正確的 JobRunr 使用方式
 *
 * 重要：不要直接提交 Kotlin lambda 表達式或函數引用給 JobRunr！
 * 這會導致 NoSuchBeanDefinitionException: No qualifying bean of type 'kotlin.jvm.functions.Function0<?>'
 *
 * 正確方式：
 * 1. 在 Spring Service 中定義 job 方法
 * 2. 使用方法引用 (::methodName) 而非 lambda 提交任務
 * 3. 確保 job 方法的 class 可以被 Spring 容器解析
 */
@Service
class BackgroundJobService(
    private val feedImportService: FeedImportService,
    private val jobScheduler: JobScheduler
) {

    /**
     * 提交 BBC 新聞匯入背景任務 - 正確方式
     * 使用方法引用 (::) 而非 lambda 表達式
     */
    fun enqueueBbcNewsImport(rssUrl: String, sourceName: String, section: String): JobId {
        return jobScheduler.enqueue<BackgroundJobService> { service ->
            service.executeBbcNewsImport(rssUrl, sourceName, section)
        }
    }

    /**
     * 執行 BBC 新聞匯入任務的實際方法
     * 這個方法會由 JobRunr 在背景執行
     *
     * @Job 註解提供任務的顯示名稱和重試次數
     */
    @Job(name = "Import BBC News from RSS: %0", retries = 3)
    fun executeBbcNewsImport(rssUrl: String, sourceName: String, section: String) {
        try {
            val result = feedImportService.importFromFeed(rssUrl, sourceName, section)
            println("Successfully imported $result articles from $sourceName/$section")
        } catch (e: Exception) {
            println("Failed to import from $sourceName/$section: ${e.message}")
            throw e // 重拋異常以便 JobRunr 可以重試
        }
    }

    /**
     * 示例：錯誤的方式（會導致 Function0 錯誤）
     *
     * fun enqueueWrong() {
     *     // ❌ 錯誤：直接提交 lambda 表達式
     *     jobScheduler.enqueue { feedImportService.importFromFeed("url", "name", "section") }
     *
     *     // ❌ 錯誤：提交 Kotlin 函數引用
     *     jobScheduler.enqueue(::someFunction)
     * }
     */

    /**
     * 示例：正確的方式
     */
    fun enqueueCorrect() {
        // ✅ 正確：使用類型安全的方法引用
        jobScheduler.enqueue<BackgroundJobService> { service ->
            service.executeBbcNewsImport(
                "https://feeds.bbci.co.uk/news/rss.xml",
                "BBC",
                "news"
            )
        }
    }
}