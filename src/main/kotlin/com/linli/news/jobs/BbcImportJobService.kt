package com.linli.news.jobs

import com.linli.news.service.FeedImportService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.jobrunr.jobs.annotations.Job
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BbcImportJobService(
    private val feedImportService: FeedImportService,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(BbcImportJobService::class.java)

    /**
     * 執行一次匯入並記錄指標與結構化日誌。
     */
    private fun runImport(rssUrl: String, sourceName: String, section: String, jobName: String) {
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

    // --- BBC Main Sections ---

    @Job(name = "BBC - Main News Import", retries = 3)
    fun importBbcNews() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/rss.xml",
        sourceName = "BBC",
        section = "news",
        jobName = "bbc-main"
    )

    @Job(name = "BBC - World Import", retries = 3)
    fun importBbcWorld() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/rss.xml",
        sourceName = "BBC",
        section = "world",
        jobName = "bbc-world"
    )

    @Job(name = "BBC - UK Import", retries = 3)
    fun importBbcUk() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/uk/rss.xml",
        sourceName = "BBC",
        section = "uk",
        jobName = "bbc-uk"
    )

    @Job(name = "BBC - Technology Import", retries = 3)
    fun importBbcTechnology() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/technology/rss.xml",
        sourceName = "BBC",
        section = "technology",
        jobName = "bbc-technology"
    )

    @Job(name = "BBC - Business Import", retries = 3)
    fun importBbcBusiness() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/business/rss.xml",
        sourceName = "BBC",
        section = "business",
        jobName = "bbc-business"
    )

    @Job(name = "BBC - Health Import", retries = 3)
    fun importBbcHealth() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/health/rss.xml",
        sourceName = "BBC",
        section = "health",
        jobName = "bbc-health"
    )

    @Job(name = "BBC - Science & Environment Import", retries = 3)
    fun importBbcScienceEnv() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
        sourceName = "BBC",
        section = "science_and_environment",
        jobName = "bbc-science-env"
    )

    @Job(name = "BBC - Entertainment & Arts Import", retries = 3)
    fun importBbcEntertainmentArts() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
        sourceName = "BBC",
        section = "entertainment_and_arts",
        jobName = "bbc-entertainment-arts"
    )

    @Job(name = "BBC - Politics Import", retries = 3)
    fun importBbcPolitics() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/politics/rss.xml",
        sourceName = "BBC",
        section = "politics",
        jobName = "bbc-politics"
    )

    @Job(name = "BBC - Education Import", retries = 3)
    fun importBbcEducation() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/education/rss.xml",
        sourceName = "BBC",
        section = "education",
        jobName = "bbc-education"
    )

    // --- World sub sections ---

    @Job(name = "BBC - World Africa Import", retries = 3)
    fun importBbcWorldAfrica() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/africa/rss.xml",
        sourceName = "BBC",
        section = "world_africa",
        jobName = "bbc-world-africa"
    )

    @Job(name = "BBC - World Asia Import", retries = 3)
    fun importBbcWorldAsia() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/asia/rss.xml",
        sourceName = "BBC",
        section = "world_asia",
        jobName = "bbc-world-asia"
    )

    @Job(name = "BBC - World Europe Import", retries = 3)
    fun importBbcWorldEurope() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/europe/rss.xml",
        sourceName = "BBC",
        section = "world_europe",
        jobName = "bbc-world-europe"
    )

    @Job(name = "BBC - World Middle East Import", retries = 3)
    fun importBbcWorldMiddleEast() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml",
        sourceName = "BBC",
        section = "world_middle_east",
        jobName = "bbc-world-middle-east"
    )

    @Job(name = "BBC - World US & Canada Import", retries = 3)
    fun importBbcWorldUsCanada() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
        sourceName = "BBC",
        section = "world_us_and_canada",
        jobName = "bbc-world-us-canada"
    )

    @Job(name = "BBC - World Latin America Import", retries = 3)
    fun importBbcWorldLatinAmerica() = runImport(
        rssUrl = "https://feeds.bbci.co.uk/news/world/latin_america/rss.xml",
        sourceName = "BBC",
        section = "world_latin_america",
        jobName = "bbc-world-latin-america"
    )
}
