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
     * 統一頻率設定：
     * - 主分類（news、world、uk、technology）：每 30 分鐘
     * - 子分類（其他區塊）：每 60 分鐘
     */
    @PostConstruct
    fun registerRecurringJobs() {
        logger.info("Registering BBC recurring jobs with JobRunr ...")

        // Main sections (every 30 minutes)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcNews",
            Duration.ofMinutes(30)
        ) { s -> s.importBbcNews() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorld",
            Duration.ofMinutes(30)
        ) { s -> s.importBbcWorld() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>("bbcUk", Duration.ofMinutes(30)) { s -> s.importBbcUk() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcTechnology",
            Duration.ofMinutes(30)
        ) { s -> s.importBbcTechnology() }

        // Sub sections (every 60 minutes)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcBusiness",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcBusiness() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcHealth",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcHealth() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcScienceAndEnvironment",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcScienceEnv() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcEntertainmentAndArts",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcEntertainmentArts() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcPolitics",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcPolitics() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcEducation",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcEducation() }

        // World sub sections (every 60 minutes)
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldAfrica",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldAfrica() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldAsia",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldAsia() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldEurope",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldEurope() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldMiddleEast",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldMiddleEast() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldUsAndCanada",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldUsCanada() }
        jobScheduler.scheduleRecurrently<BbcImportJobService>(
            "bbcWorldLatinAmerica",
            Duration.ofMinutes(60)
        ) { s -> s.importBbcWorldLatinAmerica() }

        logger.info("BBC recurring jobs registered.")
    }
}
