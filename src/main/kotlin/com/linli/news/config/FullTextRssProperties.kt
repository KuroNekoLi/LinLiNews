package com.linli.news.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/**
 * Full-Text RSS 服務配置屬性
 *
 * 從 application.yml 讀取 fulltextrss 開頭的配置
 *
 * @property baseUrl Full-Text RSS 服務的基礎 URL（例如：http://localhost:8085）
 * @property enabled 是否啟用 Full-Text RSS 服務（false 時會跳過全文抓取）
 */
@ConfigurationProperties(prefix = "fulltextrss")
data class FullTextRssProperties @ConstructorBinding constructor(
    /**
     * Full-Text RSS 的基礎 URL，例如: http://localhost:8085
     */
    val baseUrl: String,

    /**
     * 是否啟用 Full-Text RSS 服務
     * - true: 正常使用 Full-Text RSS 抓取全文（預設）
     * - false: 跳過 Full-Text RSS，只使用 RSS 原始內容
     */
    val enabled: Boolean = true
)
