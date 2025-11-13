package com.linli.news.jobs

import com.linli.news.service.FeedImportService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service
class BbcImportJobService(
    private val feedImportService: FeedImportService,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(BbcImportJobService::class.java)

    companion object {
        /**
         * 全域匯入互斥鎖：
         * - Spring Scheduler 可能在多個 thread 同時執行不同的 BBC 匯入任務
         * - SQLite 同一時間只能有一個寫入 transaction
         * - 透過 Mutex 保證整個 JVM 內任一時間只有一個匯入流程在寫 DB
         */
        private val importMutex = Mutex()
    }

    /**
     * 執行一次匯入並記錄指標與結構化日誌。
     *
     * 使用 Coroutine + Mutex 確保：
     * - 同一個 JVM 內，任一時間只有一個匯入流程在執行（單一寫入者）
     * - 即使 Scheduler 開了多個 threads，也會被序列化經過這個 critical section
     *
     * 這可以減少 SQLite 在大量寫入時出現 [SQLITE_BUSY] database is locked 的機率。
     */
    private fun runImport(rssUrl: String, sourceName: String, section: String, jobName: String) = runBlocking {
        importMutex.withLock {
            val timerSample = Timer.start(meterRegistry)
            var importedCount = 0
            var status = "success"
            try {
                logger.info(
                    "Starting import job: {}, source={}, section={}, rss={}",
                    jobName, sourceName, section, rssUrl
                )
                importedCount = feedImportService.importFromFeed(rssUrl, sourceName, section)
                logger.info(
                    "Completed import job: {}, imported={}, source={}, section={}",
                    jobName, importedCount, sourceName, section
                )
            } catch (e: Exception) {
                status = "failure"
                logger.error(
                    "Import job failed: {}, source={}, section={}, error={}",
                    jobName, sourceName, section, e.message, e
                )
                throw e
            } finally {
                // 記錄耗時
                timerSample.stop(
                    meterRegistry.timer(
                        "feed.import.duration",
                        "source", sourceName,
                        "section", section,
                        "status", status
                    )
                )
                // 記錄筆數
                meterRegistry.counter(
                    "feed.import.count",
                    "source", sourceName,
                    "section", section,
                    "status", status
                ).increment(importedCount.toDouble())
            }
        }
    }

    // --- BBC Main Sections ---

    fun importBbcNews() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/rss.xml",
        sourceName = "BBC",
        section = "news",
        jobName = "bbc-main"
    )

    fun importBbcWorld() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/rss.xml",
        sourceName = "BBC",
        section = "world",
        jobName = "bbc-world"
    )

    fun importBbcUk() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/uk/rss.xml",
        sourceName = "BBC",
        section = "uk",
        jobName = "bbc-uk"
    )

    fun importBbcTechnology() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/technology/rss.xml",
        sourceName = "BBC",
        section = "technology",
        jobName = "bbc-technology"
    )

    fun importBbcBusiness() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/business/rss.xml",
        sourceName = "BBC",
        section = "business",
        jobName = "bbc-business"
    )

    fun importBbcHealth() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/health/rss.xml",
        sourceName = "BBC",
        section = "health",
        jobName = "bbc-health"
    )

    fun importBbcScienceEnv() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
        sourceName = "BBC",
        section = "science_and_environment",
        jobName = "bbc-science-env"
    )

    fun importBbcEntertainmentArts() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
        sourceName = "BBC",
        section = "entertainment_and_arts",
        jobName = "bbc-entertainment-arts"
    )

    fun importBbcPolitics() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/politics/rss.xml",
        sourceName = "BBC",
        section = "politics",
        jobName = "bbc-politics"
    )

    fun importBbcEducation() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/education/rss.xml",
        sourceName = "BBC",
        section = "education",
        jobName = "bbc-education"
    )

    // --- World sub sections ---

    fun importBbcWorldAfrica() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/africa/rss.xml",
        sourceName = "BBC",
        section = "world_africa",
        jobName = "bbc-world-africa"
    )

    fun importBbcWorldAsia() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/asia/rss.xml",
        sourceName = "BBC",
        section = "world_asia",
        jobName = "bbc-world-asia"
    )

    fun importBbcWorldEurope() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/europe/rss.xml",
        sourceName = "BBC",
        section = "world_europe",
        jobName = "bbc-world-europe"
    )

    fun importBbcWorldMiddleEast() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml",
        sourceName = "BBC",
        section = "world_middle_east",
        jobName = "bbc-world-middle-east"
    )

    fun importBbcWorldUsCanada() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
        sourceName = "BBC",
        section = "world_us_and_canada",
        jobName = "bbc-world-us-canada"
    )

    fun importBbcWorldLatinAmerica() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/latin_america/rss.xml",
        sourceName = "BBC",
        section = "world_latin_america",
        jobName = "bbc-world-latin-america"
    )
}
