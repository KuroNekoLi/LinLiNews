package com.linli.news.config

import com.linli.news.jobs.BbcImportJobService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * BBC 定期排程任務
 *
 * 使用 Spring Scheduling 代替 JobRunr 進行定期任務排程
 * 每個區塊的 RSS 匯入都設定為 7 天執行一次
 *
 * @property bbcImportJobService BBC 匯入任務服務
 */
@Component
@ConditionalOnProperty(name = ["scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class BbcScheduler(
    private val bbcImportJobService: BbcImportJobService
) {
    private val logger = LoggerFactory.getLogger(BbcScheduler::class.java)

    /**
     * 主要新聞區塊 (每 7 天)
     */
    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 10000)
    fun importBbcNews() {
        logger.info("Scheduled task: BBC News import")
        bbcImportJobService.importBbcNews()
    }

    /**
     * 世界新聞 (每 7 天)
     */
    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 20000)
    fun importBbcWorld() {
        logger.info("Scheduled task: BBC World import")
        bbcImportJobService.importBbcWorld()
    }

//    /**
//     * 英國新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 30000)
//    fun importBbcUk() {
//        logger.info("Scheduled task: BBC UK import")
//        bbcImportJobService.importBbcUk()
//    }

    /**
     * 科技新聞 (每 7 天)
     */
    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 40000)
    fun importBbcTechnology() {
        logger.info("Scheduled task: BBC Technology import")
        bbcImportJobService.importBbcTechnology()
    }
//
//    /**
//     * 商業新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 50000)
//    fun importBbcBusiness() {
//        logger.info("Scheduled task: BBC Business import")
//        bbcImportJobService.importBbcBusiness()
//    }
//
//    /**
//     * 健康新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 60000)
//    fun importBbcHealth() {
//        logger.info("Scheduled task: BBC Health import")
//        bbcImportJobService.importBbcHealth()
//    }
//
//    /**
//     * 科學與環境新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 70000)
//    fun importBbcScienceEnvironment() {
//        logger.info("Scheduled task: BBC Science & Environment import")
//        bbcImportJobService.importBbcScienceEnv()
//    }
//
//    /**
//     * 娛樂與藝術新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 80000)
//    fun importBbcEntertainmentArts() {
//        logger.info("Scheduled task: BBC Entertainment & Arts import")
//        bbcImportJobService.importBbcEntertainmentArts()
//    }
//
//    /**
//     * 政治新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 90000)
//    fun importBbcPolitics() {
//        logger.info("Scheduled task: BBC Politics import")
//        bbcImportJobService.importBbcPolitics()
//    }
//
//    /**
//     * 教育新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 100000)
//    fun importBbcEducation() {
//        logger.info("Scheduled task: BBC Education import")
//        bbcImportJobService.importBbcEducation()
//    }
//
//    /**
//     * 世界子區塊 - 非洲新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 110000)
//    fun importBbcWorldAfrica() {
//        logger.info("Scheduled task: BBC World Africa import")
//        bbcImportJobService.importBbcWorldAfrica()
//    }
//
//    /**
//     * 世界子區塊 - 亞洲新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 120000)
//    fun importBbcWorldAsia() {
//        logger.info("Scheduled task: BBC World Asia import")
//        bbcImportJobService.importBbcWorldAsia()
//    }
//
//    /**
//     * 世界子區塊 - 歐洲新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 130000)
//    fun importBbcWorldEurope() {
//        logger.info("Scheduled task: BBC World Europe import")
//        bbcImportJobService.importBbcWorldEurope()
//    }
//
//    /**
//     * 世界子區塊 - 中東新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 140000)
//    fun importBbcWorldMiddleEast() {
//        logger.info("Scheduled task: BBC World Middle East import")
//        bbcImportJobService.importBbcWorldMiddleEast()
//    }
//
//    /**
//     * 世界子區塊 - 美國與加拿大新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 150000)
//    fun importBbcWorldUsCanada() {
//        logger.info("Scheduled task: BBC World US & Canada import")
//        bbcImportJobService.importBbcWorldUsCanada()
//    }
//
//    /**
//     * 世界子區塊 - 拉丁美洲新聞 (每 7 天)
//     */
//    @Scheduled(fixedDelayString = "${7 * 24 * 60 * 60 * 1000}", initialDelay = 160000)
//    fun importBbcWorldLatinAmerica() {
//        logger.info("Scheduled task: BBC World Latin America import")
//        bbcImportJobService.importBbcWorldLatinAmerica()
//    }
}