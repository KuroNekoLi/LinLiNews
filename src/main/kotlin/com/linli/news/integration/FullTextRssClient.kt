package com.linli.news.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.config.FullTextRssProperties
import com.linli.news.model.FullTextArticleResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.net.URLEncoder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
@EnableConfigurationProperties(FullTextRssProperties::class)
class FullTextRssClient(
    private val properties: FullTextRssProperties
) {
    private val restClient: RestClient = RestClient.builder()
        .defaultHeader(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )
        .build()
    private val logger = LoggerFactory.getLogger(FullTextRssClient::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * 取得單篇新聞（網頁 URL）的全文內容。
     *
     * @param articleUrl 目標新聞或文章的原始 URL
     * @return 全文抽取結果（標題、HTML 內容、語言、作者等）
     */
    fun fetchArticleFullText(articleUrl: String): FullTextArticleResponse {
        val encodedUrl = URLEncoder.encode(articleUrl, StandardCharsets.UTF_8)
        val requestUrl = "${properties.baseUrl.removeSuffix("/")}/extract.php?url=$encodedUrl"
        logger.info("Calling Full-Text RSS extract API: $requestUrl")

        var lastException: Exception? = null

        // 嘗試使用 Spring RestClient
        try {
            val responseBody = restClient.get()
                .uri(requestUrl)
                .header("Accept", "application/json, text/html, application/xhtml+xml, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .retrieve()
                .body(String::class.java)
                ?: throw Exception("Full-Text RSS 無回應內容")

            logger.info("Full-Text RSS extract (RestClient) response (first 200 chars): ${responseBody.take(200)}")

            // 檢查是否是 JSON 格式並直接解析
            if (responseBody.trim().startsWith("{")) {
                return objectMapper.readValue(responseBody, FullTextArticleResponse::class.java)
            } else {
                throw Exception("RestClient returned non-JSON response")
            }

        } catch (ex: Exception) {
            lastException = ex
            logger.warn("RestClient failed for extract, trying curl fallback: ${ex.message}")
        }

        // 使用 curl 作為備援 (我們知道 curl 可以正常工作)
        try {
            logger.info("Trying curl fallback for extract...")
            val processBuilder = ProcessBuilder(
                "curl",
                "-s",
                "-L",
                "-H",
                "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                "-H",
                "Accept: application/json, text/html, */*",
                requestUrl
            )
            val process = processBuilder.start()
            val responseBody = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            logger.info("Full-Text RSS extract (curl) exit code: $exitCode")
            logger.info("Full-Text RSS extract (curl) response (first 200 chars): ${responseBody.take(200)}")

            if (exitCode == 0 && responseBody.trim().startsWith("{")) {
                return objectMapper.readValue(responseBody, FullTextArticleResponse::class.java)
            } else {
                throw Exception("curl extract failed with exit code: $exitCode")
            }

        } catch (ex: Exception) {
            logger.error("All methods failed for extract: ${ex.message}")
            // 如果所有方法都失敗，拋出原始例外
            if (lastException != null) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "呼叫 Full-Text RSS extract 失敗 (All methods failed): ${lastException.message}",
                    lastException
                )
            } else {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "呼叫 Full-Text RSS extract 失敗: ${ex.message}",
                    ex
                )
            }
        }
    }

    /**
     * 取得 RSS 轉全文之 JSON feed，回傳原始 JsonNode 以利彈性解析。
     *
     * @param feedUrl RSS 來源 URL
     */
    fun fetchFeedJson(feedUrl: String): JsonNode {
        logger.info("Original feed URL: $feedUrl")

        // 建構完整的請求 URL (移除 accept=feed&exc=1 參數，這些會導致 Invalid URL supplied 錯誤)
        val encodedUrl = URLEncoder.encode(feedUrl, StandardCharsets.UTF_8)
        val requestUrl =
            "${properties.baseUrl.removeSuffix("/")}/makefulltextfeed.php?url=$encodedUrl&format=json"
        logger.info("Calling Full-Text RSS feed API: $requestUrl")

        var lastException: Exception? = null

        // 嘗試使用 Spring RestClient
        try {
            val responseBody = restClient.get()
                .uri(requestUrl)
                .header(
                    "Accept",
                    "application/json, text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8"
                )
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .retrieve()
                .body(String::class.java)
                ?: throw Exception("Full-Text RSS 無回應內容")

            logger.info("Full-Text RSS (RestClient) response status: OK")
            logger.info("Full-Text RSS (RestClient) raw response (first 500 chars): ${responseBody.take(500)}")

            // 檢查是否是 JSON 格式
            if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                try {
                    val result = objectMapper.readTree(responseBody)
                    logger.info("Successfully parsed JSON response with ${result.size()} items (RestClient)")
                    return result
                } catch (jsonEx: Exception) {
                    logger.error("JSON parsing failed (RestClient): ${jsonEx.message}")
                    throw Exception("RestClient JSON parsing failed: ${jsonEx.message}")
                }
            } else {
                logger.error("Full-Text RSS (RestClient) 回應不是有效的 JSON. Full response: $responseBody")
                throw Exception("RestClient returned non-JSON response: $responseBody")
            }

        } catch (ex: Exception) {
            lastException = ex
            logger.warn("RestClient failed, trying HttpClient fallback: ${ex.message}")
        }

        // 使用 Java HttpClient 作為備援
        try {
            logger.info("Trying HttpClient fallback...")
            val httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(15))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                )
                .header(
                    "Accept",
                    "application/json, text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8"
                )
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            logger.info("Full-Text RSS (HttpClient) HTTP status: ${httpResponse.statusCode()}")
            val responseBody = httpResponse.body()
            logger.info("Full-Text RSS (HttpClient) raw response (first 500 chars): ${responseBody.take(500)}")

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                // 檢查是否是 JSON 格式
                if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                    try {
                        val result = objectMapper.readTree(responseBody)
                        logger.info("Successfully parsed JSON response with ${result.size()} items (HttpClient)")
                        return result
                    } catch (jsonEx: Exception) {
                        logger.error("JSON parsing failed (HttpClient): ${jsonEx.message}")
                        throw Exception("HttpClient JSON parsing failed: ${jsonEx.message}")
                    }
                } else {
                    logger.error("Full-Text RSS (HttpClient) 回應不是有效的 JSON. Full response: $responseBody")
                    throw Exception("HttpClient returned non-JSON response: $responseBody")
                }
            } else {
                logger.error("Full-Text RSS (HttpClient) HTTP response not OK: ${httpResponse.statusCode()}")
                throw Exception("HttpClient HTTP response not OK: ${httpResponse.statusCode()}")
            }

        } catch (ex: Exception) {
            logger.warn("HttpClient failed, trying curl fallback: ${ex.message}")
        }

        // 最後備援：使用 curl 命令行工具 (因為我們知道 curl 可以正常工作)
        try {
            logger.info("Trying curl fallback as last resort...")
            val processBuilder = ProcessBuilder(
                "curl",
                "-s",
                "-L",
                "-H",
                "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "-H",
                "Accept: application/json, text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8",
                requestUrl
            )
            val process = processBuilder.start()
            val responseBody = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            logger.info("Full-Text RSS (curl) exit code: $exitCode")
            logger.info("Full-Text RSS (curl) raw response (first 500 chars): ${responseBody.take(500)}")

            if (exitCode == 0) {
                // 檢查是否是 JSON 格式
                if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                    try {
                        val result = objectMapper.readTree(responseBody)
                        logger.info("Successfully parsed JSON response with ${result.size()} items (curl)")
                        return result
                    } catch (jsonEx: Exception) {
                        logger.error("JSON parsing failed (curl): ${jsonEx.message}")
                        throw Exception("curl JSON parsing failed: ${jsonEx.message}")
                    }
                } else {
                    logger.error("Full-Text RSS (curl) 回應不是有效的 JSON. Full response: $responseBody")
                    throw Exception("curl returned non-JSON response: $responseBody")
                }
            } else {
                logger.error("Full-Text RSS (curl) command failed with exit code: $exitCode")
                throw Exception("curl command failed with exit code: $exitCode")
            }

        } catch (ex: Exception) {
            logger.error("All HTTP methods failed, throwing exception: ${ex.message}")
            // 如果所有方法都失敗，拋出原始 RestClient 例外
            if (lastException != null) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "呼叫 Full-Text RSS feed 失敗 (All methods failed): ${lastException.message}",
                    lastException
                )
            } else {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "呼叫 Full-Text RSS feed 失敗: ${ex.message}", ex)
            }
        }
    }
}
