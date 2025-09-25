package com.linli.news.jobs

import com.linli.news.service.FeedImportService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BbcRecurringJobs(
    private val feedImportService: FeedImportService
) {
    private val logger = LoggerFactory.getLogger(BbcRecurringJobs::class.java)

    @Scheduled(cron = "0 * * * * *") // 每分鐘執行一次（測試用）
    fun importBbcNews() {
        logger.info("執行 BBC 新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/rss.xml",
                sourceName = "BBC",
                section = "news"
            )
            logger.info("BBC 新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // 每15分鐘執行一次
    fun importBbcWorld() {
        logger.info("執行 BBC 世界新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/rss.xml",
                sourceName = "BBC",
                section = "world"
            )
            logger.info("BBC 世界新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // 每15分鐘執行一次
    fun importBbcUk() {
        logger.info("執行 BBC 英國新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/uk/rss.xml",
                sourceName = "BBC",
                section = "uk"
            )
            logger.info("BBC 英國新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 英國新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // 每15分鐘執行一次
    fun importBbcTechnology() {
        logger.info("執行 BBC 科技新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/technology/rss.xml",
                sourceName = "BBC",
                section = "technology"
            )
            logger.info("BBC 科技新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 科技新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcBusiness() {
        logger.info("執行 BBC 商業新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/business/rss.xml",
                sourceName = "BBC",
                section = "business"
            )
            logger.info("BBC 商業新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 商業新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // 每15分鐘執行一次
    fun importBbcHealth() {
        logger.info("執行 BBC 健康醫療新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/health/rss.xml",
                sourceName = "BBC",
                section = "health"
            )
            logger.info("BBC 健康醫療新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 健康醫療新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcScienceEnv() {
        logger.info("執行 BBC 科學與環境新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
                sourceName = "BBC",
                section = "science_and_environment"
            )
            logger.info("BBC 科學與環境新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 科學與環境新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcEntertainmentArts() {
        logger.info("執行 BBC 娛樂與藝術新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
                sourceName = "BBC",
                section = "entertainment_and_arts"
            )
            logger.info("BBC 娛樂與藝術新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 娛樂與藝術新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcPolitics() {
        logger.info("執行 BBC 政治新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/politics/rss.xml",
                sourceName = "BBC",
                section = "politics"
            )
            logger.info("BBC 政治新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 政治新聞匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */30 * * * *") // 每30分鐘執行一次
    fun importBbcEducation() {
        logger.info("執行 BBC 教育新聞排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/education/rss.xml",
                sourceName = "BBC",
                section = "education"
            )
            logger.info("BBC 教育新聞匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 教育新聞匯入失敗: ${e.message}", e)
        }
    }

    // --- World 子分類 ---

    @Scheduled(cron = "0 */30 * * * *") // 每30分鐘執行一次
    fun importBbcWorldAfrica() {
        logger.info("執行 BBC 世界/非洲 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/africa/rss.xml",
                sourceName = "BBC",
                section = "world_africa"
            )
            logger.info("BBC 世界/非洲 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/非洲 匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcWorldAsia() {
        logger.info("執行 BBC 世界/亞洲 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/asia/rss.xml",
                sourceName = "BBC",
                section = "world_asia"
            )
            logger.info("BBC 世界/亞洲 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/亞洲 匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */20 * * * *") // 每20分鐘執行一次
    fun importBbcWorldEurope() {
        logger.info("執行 BBC 世界/歐洲 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/europe/rss.xml",
                sourceName = "BBC",
                section = "world_europe"
            )
            logger.info("BBC 世界/歐洲 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/歐洲 匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */30 * * * *") // 每30分鐘執行一次
    fun importBbcWorldMiddleEast() {
        logger.info("執行 BBC 世界/中東 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml",
                sourceName = "BBC",
                section = "world_middle_east"
            )
            logger.info("BBC 世界/中東 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/中東 匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */15 * * * *") // 每15分鐘執行一次
    fun importBbcWorldUsCanada() {
        logger.info("執行 BBC 世界/美加 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
                sourceName = "BBC",
                section = "world_us_and_canada"
            )
            logger.info("BBC 世界/美加 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/美加 匯入失敗: ${e.message}", e)
        }
    }

    @Scheduled(cron = "0 */30 * * * *") // 每30分鐘執行一次
    fun importBbcWorldLatinAmerica() {
        logger.info("執行 BBC 世界/拉丁美洲 排程任務...")
        try {
            val result = feedImportService.importFromFeed(
                "https://feeds.bbci.co.uk/news/world/latin_america/rss.xml",
                sourceName = "BBC",
                section = "world_latin_america"
            )
            logger.info("BBC 世界/拉丁美洲 匯入完成: $result 篇文章")
        } catch (e: Exception) {
            logger.error("BBC 世界/拉丁美洲 匯入失敗: ${e.message}", e)
        }
    }
}
