package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.model.DifficultyLevel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

/**
 * 使用 OpenAI GPT-4o 對文章內容與 HTML 進行後處理的服務。
 *
 * 職責：
 * - 移除非正文區塊（廣告、相關連結、導覽列、電子報訂閱、促銷區塊等）
 * - 移除與內容無關的圖片（Logo、Icon、廣告、社群嵌入等）
 * - 產生清理後的 HTML、純文字與繁體中文翻譯
 * - 產生英文學習用的講解內容（字彙／文法／句型與題目）
 * - 以 OpenAI JSON mode 回傳嚴格的 JSON 物件
 */
@Service
class AiArticleProcessorService(
    @param:Value("\${openai.api-key:}") private val apiKey: String,
    @param:Value("\${openai.base-url:https://api.openai.com/v1}") private val baseUrl: String,
    @param:Value("\${openai.model:gpt-5-mini}") private val model: String,
    @param:Value("\${openai.max-tokens:16000}") private val maxTokens: Int,
) {
    private val logger = LoggerFactory.getLogger(AiArticleProcessorService::class.java)
    private val objectMapper = ObjectMapper()

    // 讀取候選 tags 和 collections（從 JSON 檔案）
    private val availableTags: List<String> by lazy {
        val resource = this::class.java.classLoader.getResourceAsStream("tag-definitions.json")
        resource?.use {
            val tree = objectMapper.readTree(it)
            tree.map { node -> node.path("name").asText() }
        } ?: emptyList()
    }

    private val availableCollections: List<Pair<String, String>> by lazy {
        val resource = this::class.java.classLoader.getResourceAsStream("featured-collections.json")
        resource?.use {
            val tree = objectMapper.readTree(it)
            tree.map { node ->
                node.path("id").asText() to node.path("description").asText()
            }
        } ?: emptyList()
    }

    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer $apiKey")
        .defaultHeader("User-Agent", "LinLiNews/1.0 (+SpringBoot)")
        .build()

    /**
     * 呼叫 OpenAI Chat Completions，並對暫時性錯誤進行少量重試。
     *
     * 規則：
     * - 若回應為 insufficient_quota / 超出額度，則不重試，直接拋出例外
     * - 對 429（Too Many Requests）與 5xx 錯誤實施指數退避重試
     */
    private fun postChatCompletions(body: Map<String, Any>, operation: String): String {
        var attempt = 0
        val maxAttempts = 3
        var delayMs = 1000L
        while (true) {
            try {
                return client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String::class.java)
                    ?: throw IllegalStateException("OpenAI 回傳空白")
            } catch (e: HttpClientErrorException) {
                val status = e.statusCode.value()
                val bodyStr = e.responseBodyAsString ?: ""
                if (bodyStr.contains("\"insufficient_quota\"") || bodyStr.contains("exceeded your current quota")) {
                    logger.warn("[AI] $operation failed: insufficient_quota (no retry)")
                    throw AiQuotaException("OpenAI insufficient quota")
                }
                if (status == 429) {
                    if (++attempt >= maxAttempts) {
                        logger.warn("[AI] $operation failed with 429 after $attempt attempts")
                        throw e
                    }
                    logger.warn("[AI] 429 Too Many Requests on $operation, retrying in ${delayMs}ms (attempt $attempt/$maxAttempts)")
                    Thread.sleep(delayMs)
                    delayMs *= 2
                    continue
                }
                // 401/403 and other 4xx: don't retry
                throw e
            } catch (e: HttpServerErrorException) {
                if (++attempt >= maxAttempts) {
                    logger.warn("[AI] $operation failed with ${e.statusCode} after $attempt attempts")
                    throw e
                }
                logger.warn("[AI] ${e.statusCode} on $operation, retrying in ${delayMs}ms (attempt $attempt/$maxAttempts)")
                Thread.sleep(delayMs)
                delayMs *= 2
            }
        }
    }

    /**
     * 建立使用 JSON 模式的 Chat Completions 請求 body。
     *
     * @param systemPrompt 系統提示詞
     * @param userPrompt 使用者提示詞
     * @param temperature 溫度參數
     */
    private fun buildJsonModeBody(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double
    ): Map<String, Any> = mapOf(
        "model" to model,
        "max_completion_tokens" to maxTokens,
        "response_format" to mapOf("type" to "json_object"),
        "messages" to listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
    )

    /**
     * 從 OpenAI 回應中擷取 content 欄位文字，並進行基本檢查。
     *
     * 若 content 為空，會記錄錯誤並拋出例外。
     *
     * @param responseStr OpenAI 原始回應字串
     * @param operation 目前執行的操作名稱（用於記錄 log）
     * @return Pair 第一個元素為根節點 JsonNode，第二個為 content 文字
     */
    private fun extractContentFromResponse(
        responseStr: String,
        operation: String
    ): Pair<JsonNode, String> {
        val root = objectMapper.readTree(responseStr)
        val content = root.path("choices").path(0).path("message").path("content").asText()
        if (content.isBlank()) {
            logger.error("OpenAI $operation 回傳內容為空，原始: $responseStr")
            throw IllegalStateException("OpenAI $operation 回傳內容為空")
        }
        logger.info("[AI] $operation content length: ${content.length} chars")
        return root to content
    }

    fun process(title: String?, textContent: String?, fromLanguage: String, toLanguage: String): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (textContent.isNullOrBlank()) {
            throw IllegalArgumentException("Article text content is empty; cannot process with AI")
        }

        val systemPrompt = AiArticlePrompts.processSystemPrompt(fromLanguage, toLanguage)
        val userPrompt = AiArticlePrompts.processUserPrompt(title, textContent, fromLanguage, toLanguage)

        val body = buildJsonModeBody(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2
        )

        logger.info("[AI] Requesting OpenAI model='${model}' with maxTokens=${maxTokens} for title='${title ?: "(no title)"}'")
        val responseStr = postChatCompletions(body, operation = "process")
        logger.info("[AI] Raw response (first 400 chars): ${responseStr.take(400)}")

        val (root, content) = extractContentFromResponse(responseStr, "process")
        logger.info("[AI] Parsed content JSON (first 400 chars): ${content.take(400)}")

        // 檢查是否被截斷（結尾不是完整的JSON）
        if (!content.trim().endsWith("}")) {
            logger.warn("[AI] Response appears to be truncated - content doesn't end with '}'")
            logger.info("[AI] Last 200 chars: ${content.takeLast(200)}")

            // 檢查 finish_reason 來確認是否因為 max_tokens 而截斷
            val finishReason = root.path("choices").path(0).path("finish_reason").asText()
            if (finishReason == "length") {
                logger.warn("[AI] Response was truncated due to max_tokens limit. Current: ${maxTokens}")
                throw IllegalStateException("OpenAI 回應因為 max_tokens 限制而被截斷，請增加 openai.max-tokens 設定值")
            }
        }

        // 嘗試解析 JSON，如果失敗則提供更好的錯誤訊息
        try {
            return objectMapper.readTree(content)
        } catch (e: Exception) {
            logger.error("[AI] Failed to parse JSON response. Content length: ${content.length}")
            logger.error("[AI] JSON parse error: ${e.message}")
            logger.error("[AI] Full content: $content")

            // 檢查是否 explanation 欄位存在但不完整
            if (content.contains("\"explanation\"")) {
                logger.warn("[AI] Explanation field found but JSON is malformed - likely truncated")
                throw IllegalStateException("AI 回應的 explanation 部分不完整，可能被截斷。請增加 max-tokens 設定值。")
            }

            throw IllegalStateException("AI 回應不是有效的 JSON 格式: ${e.message}")
        }
    }

    fun optimizeHtml(
        title: String?,
        originalHtml: String,
        fromLanguage: String,
        toLanguage: String
    ): HtmlOptimizationResult {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot optimize with AI")
        }

        val systemPrompt = AiArticlePrompts.optimizeHtmlSystemPrompt(fromLanguage, toLanguage)
        val userPrompt = AiArticlePrompts.optimizeHtmlUserPrompt(title, originalHtml, fromLanguage, toLanguage)

        val body = buildJsonModeBody(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.1
        )

        logger.info("[AI] Requesting OpenAI model='${model}' for HTML optimization (title='${title ?: "(no title)"}')")
        val responseStr = postChatCompletions(body, operation = "optimizeHtml")
        logger.info("[AI] Raw optimizeHtml response (first 400 chars): ${responseStr.take(400)}")

        val (_, content) = extractContentFromResponse(responseStr, "optimizeHtml")
        logger.debug("[AI] optimizeHtml parsed JSON (first 400 chars): ${content.take(400)}")

        val optimizedNode = objectMapper.readTree(content)
        val optimizedHtml = optimizedNode.path("optimized_html_from").asText(
            optimizedNode.path("optimized_html").asText(null)
        )
        if (optimizedHtml.isNullOrBlank()) {
            logger.error("[AI] optimizeHtml JSON 缺少 optimized_html_from 欄位或為空，lang=$fromLanguage")
            throw IllegalStateException("AI 回應缺少 optimized_html 欄位")
        }

        val optimizedZhHtml = optimizedNode.path("optimized_html_to").asText(
            optimizedNode.path("optimized_zh_html").asText(null)
        )
        return HtmlOptimizationResult(
            optimizedHtmlFrom = optimizedHtml,
            optimizedHtmlTo = optimizedZhHtml
        )
    }

    /**
     * Stage 1: 重寫文章
     *
     * @param title 文章標題
     * @param originalHtml 原始HTML內容
     * @return 重寫後的結果，包含優化後的HTML、清理後的文本等
     */
    fun rewriteStage1(
        difficulty: DifficultyLevel,
        title: String?,
        originalHtml: String,
        fromLanguage: String,
        toLanguage: String
    ): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot process with AI")
        }

        val systemPrompt = AiArticlePrompts.rewriteStage1SystemPrompt(difficulty, fromLanguage, toLanguage)
        val userPrompt =
            AiArticlePrompts.rewriteStage1UserPrompt(difficulty, title, originalHtml, fromLanguage, toLanguage)

        val body = buildJsonModeBody(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2
        )

        logger.info("[AI] Requesting OpenAI model='${model}' for rewriteStage1 (title='${title ?: "(no title)"}')")
        val responseStr = postChatCompletions(body, operation = "rewriteStage1")
        logger.info("[AI] Raw response for rewriteStage1 (first 400 chars): ${responseStr.take(400)}")

        val (_, content) = extractContentFromResponse(responseStr, "rewriteStage1")
        logger.debug("[AI] rewriteStage1 parsed JSON (first 400 chars): ${content.take(400)}")

        // 解析 JSON 並直接返回，不進行驗證
        val result = try {
            objectMapper.readTree(content)
        } catch (e: Exception) {
            logger.error("[AI] Failed to parse JSON response from rewriteStage1. Content length: ${content.length}")
            logger.error("[AI] JSON parse error: ${e.message}")
            logger.error("[AI] Full content: $content")
            throw IllegalStateException("AI rewriteStage1回應不是有效的 JSON 格式: ${e.message}")
        }

        logger.info("[AI] rewriteStage1 completed successfully")
        return result
    }

    /**
     * Stage 2: 根據重寫後的文本生成教學解析
     *
     * @param title 文章標題
     * @param rewrittenText 重寫後的文本
     * @return 教學解析結果，包含字彙、文法、題目等
     */
    fun teachingStage2(
        difficulty: DifficultyLevel,
        title: String?,
        rewrittenText: String,
        fromLanguage: String,
        toLanguage: String
    ): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (rewrittenText.isBlank()) {
            throw IllegalArgumentException("Rewritten text is empty; cannot generate teaching materials")
        }

        val systemPrompt = AiArticlePrompts.teachingStage2SystemPrompt(
            difficulty,
            fromLanguage,
            toLanguage,
            availableTags,
            availableCollections
        )
        val userPrompt = AiArticlePrompts
            .teachingStage2UserPrompt(difficulty, title ?: "(no title)", rewrittenText, fromLanguage, toLanguage)

        val body = buildJsonModeBody(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2
        )

        logger.info("[AI] Requesting OpenAI model='${model}' for teachingStage2")
        logger.debug("[AI] teachingStage2 systemPrompt: $systemPrompt")
        logger.debug("[AI] teachingStage2 userPrompt: $userPrompt")
        val responseStr = postChatCompletions(body, operation = "teachingStage2")
        logger.info("[AI] Raw response for teachingStage2 (first 400 chars): ${responseStr.take(400)}")

        val (_, content) = extractContentFromResponse(responseStr, "teachingStage2")
        logger.debug("[AI] teachingStage2 parsed JSON (first 400 chars): ${content.take(400)}")

        try {
            val result = objectMapper.readTree(content)
            logger.debug("[AI] teachingStage2 result: $result")
            return result
        } catch (e: Exception) {
            logger.error("[AI] Failed to parse JSON response from teachingStage2. Content length: ${content.length}")
            logger.error("[AI] JSON parse error: ${e.message}")
            logger.error("[AI] Full content: $content")
            throw IllegalStateException("AI teachingStage2回應不是有效的 JSON 格式: ${e.message}")
        }
    }

}
