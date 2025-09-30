package com.linli.news.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/**
 * Full-Text RSS 服務配置屬性
 *
 * 從 application.yml 讀取 fulltextrss 開頭的配置
 *
 * @property baseUrl Full-Text RSS 服務的基礎 URL（例如：http://localhost:8085）
 */
@ConfigurationProperties(prefix = "fulltextrss")
data class FullTextRssProperties @ConstructorBinding constructor(
    /**
     * Full-Text RSS 的基礎 URL，例如: http://localhost:8085
     */
    val baseUrl: String
)
