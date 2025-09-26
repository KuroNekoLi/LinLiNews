package com.linli.news.config

import org.jobrunr.jobs.mappers.JobMapper
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.StorageProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * JobRunr 配置類 - 確保與 Spring IoC 正確整合
 *
 * 這個配置解決了 Kotlin Function0 的 NoSuchBeanDefinitionException 問題
 * 通過確保 JobRunr 使用正確的 Spring ApplicationContext 來解析 Job beans
 */
@Configuration
class JobRunrConfiguration {

    /**
     * 配置 JobActivator - 這是 JobRunr 與 Spring IoC 整合的關鍵組件
     * 它告訴 JobRunr 如何從 Spring 容器中解析和創建 job 實例
     */
    @Bean
    @ConditionalOnMissingBean
    fun jobActivator(applicationContext: ApplicationContext): JobActivator =
        object : JobActivator {
            override fun <T> activateJob(type: Class<T>): T = applicationContext.getBean(type)
        }
}