package com.linli.news.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * JobRunr 清理服務
 *
 * 提供清理因為 Kotlin Function0 問題而失敗的舊任務的說明
 * 這些任務無法執行，只會持續失敗
 */
@Service
class JobRunrCleanupService {
    private val logger = LoggerFactory.getLogger(JobRunrCleanupService::class.java)

    /**
     * 應用啟動後顯示清理說明
     */
    @EventListener(ApplicationReadyEvent::class)
    fun showCleanupInstructions() {
        logger.info(
            """
            
            ========================================
            JobRunr Function0 Issue - 清理說明
            ========================================
            
            如果你的 JobRunr 數據庫中有失敗的 Function0 任務，請執行以下 SQL 清理：
            
            -- 查看失敗的 Function0 任務
            SELECT jobSignature, state, createdAt 
            FROM jobrunr_jobs 
            WHERE state = 'FAILED' 
            AND jobSignature LIKE '%kotlin.jvm.functions.Function0%';
            
            -- 刪除失敗的 Function0 任務
            DELETE FROM jobrunr_jobs 
            WHERE state = 'FAILED' 
            AND jobSignature LIKE '%kotlin.jvm.functions.Function0%';
            
            現在你可以使用 BackgroundJobService 來正確提交背景任務了！
            ========================================
            
        """.trimIndent()
        )
    }
}