package com.linli.news.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI (Swagger) 配置
 *
 * 配置 API 文件的基本資訊與伺服器設定
 */
@Configuration
class OpenApiConfiguration {

    /**
     * 配置 OpenAPI 文件資訊
     *
     * @param serverUrl 伺服器網址（從環境變數或配置檔讀取）
     * @return OpenAPI 配置物件
     */
    @Bean
    fun customOpenAPI(
        @Value("\${server.url:http://localhost:8080}") serverUrl: String
    ): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("LinLi News API")
                    .description("""
                        # LinLi News API 文件
                        
                        提供 BBC 新聞文章的查詢、AI 處理與教學解說功能。
                        
                        ## 主要功能
                        
                        - **文章查詢**：分頁查詢、單篇詳情、隨機抽取
                        - **AI 處理**：使用 GPT-4o 進行內容清理、翻譯與教學解說生成
                        - **多語言支援**：提供英文原文與繁體中文翻譯
                        - **教學解說**：單字、文法、句型、片語、閱讀測驗
                        
                        ## 注意事項
                        
                        - 測試/管理端點僅在非生產環境可用
                        - AI 處理功能需要有效的 OpenAI API 金鑰
                        - 所有時間皆使用 ISO-8601 格式
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("LinLi News Team")
                            .email("support@linlinews.com")
                    )
                    .license(
                        License()
                            .name("Internal Use")
                            .url("https://linlinews.com/license")
                    )
            )
            .addServersItem(
                Server()
                    .url(serverUrl)
                    .description("API 伺服器")
            )
    }
}
