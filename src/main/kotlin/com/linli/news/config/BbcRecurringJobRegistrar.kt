package com.linli.news.config

import com.linli.news.jobs.BbcImportJobService
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * BBC 定期任務註冊器
 *
 * 在應用程式啟動時註冊 BBC 各區塊的定期匯入任務
 *
 * @property jobScheduler JobRunr 任務排程器
 */
@Component
@ConditionalOnProperty(
    prefix = "org.jobrunr.job-scheduler",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true  // 預設為 true，保持向後兼容
)
class BbcRecurringJobRegistrar(
    private val jobScheduler: JobScheduler
) {
    private val logger = LoggerFactory.getLogger(BbcRecurringJobRegistrar::class.java)

    /**
     * 啟動時註冊 JobRunr 的定期任務
     *
     * 改為一週執行一次：
     * - 所有 BBC 主分類與子分類，每 7 天自動匯入一次（用於節省資源）
     */
    @PostConstruct
    fun registerRecurringJobs() {
        logger.info("Registering BBC recurring jobs with JobRunr ...")

        // 設定一週（7 天）為間隔
        val jobInterval = Duration.ofDays(7)

        // Main sections (every 7 days)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcNews",
            jobInterval
        ) { s -> s.importBbcNews() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorld",
            jobInterval
        ) { s -> s.importBbcWorld() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>("bbcUk", jobInterval) { s -> s.importBbcUk() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcTechnology",
            jobInterval
        ) { s -> s.importBbcTechnology() }

        // Sub sections (every 7 days)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcBusiness",
            jobInterval
        ) { s -> s.importBbcBusiness() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcHealth",
            jobInterval
        ) { s -> s.importBbcHealth() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcScienceAndEnvironment",
            jobInterval
        ) { s -> s.importBbcScienceEnv() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcEntertainmentAndArts",
            jobInterval
        ) { s -> s.importBbcEntertainmentArts() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcPolitics",
            jobInterval
        ) { s -> s.importBbcPolitics() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcEducation",
            jobInterval
        ) { s -> s.importBbcEducation() }

        // World sub sections (every 7 days)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldAfrica",
            jobInterval
        ) { s -> s.importBbcWorldAfrica() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldAsia",
            jobInterval
        ) { s -> s.importBbcWorldAsia() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldEurope",
            jobInterval
        ) { s -> s.importBbcWorldEurope() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldMiddleEast",
            jobInterval
        ) { s -> s.importBbcWorldMiddleEast() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldUsAndCanada",
            jobInterval
        ) { s -> s.importBbcWorldUsCanada() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldLatinAmerica",
            jobInterval
        ) { s -> s.importBbcWorldLatinAmerica() }

        logger.info("BBC recurring jobs registered.")
    }
}
