package com.linli.news.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "fulltextrss")
data class FullTextRssProperties @ConstructorBinding constructor(
    /**
     * Full-Text RSS 的基礎 URL，例如: http://localhost:8085
     */
    val baseUrl: String
)
