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
    @param:Value("\${openai.max-tokens:12000}") private val maxTokens: Int,
) {
    private val logger = LoggerFactory.getLogger(AiArticleProcessorService::class.java)
    private val objectMapper = ObjectMapper()

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

    fun process(title: String?, textContent: String?): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (textContent.isNullOrBlank()) {
            throw IllegalArgumentException("Article text content is empty; cannot process with AI")
        }

        val systemPrompt = AiArticlePrompts.processSystemPrompt()
        val userPrompt = AiArticlePrompts.processUserPrompt(title, textContent)

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
        targetLanguage: HtmlOptimizationLanguage = HtmlOptimizationLanguage.ENGLISH
    ): HtmlOptimizationResult {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot optimize with AI")
        }

        val systemPrompt = AiArticlePrompts.optimizeHtmlSystemPrompt()
        val userPrompt = AiArticlePrompts.optimizeHtmlUserPrompt(title, originalHtml)

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
        val optimizedHtml = optimizedNode.path("optimized_html").asText(null)
        if (optimizedHtml.isNullOrBlank()) {
            logger.error("[AI] optimizeHtml JSON 缺少 optimized_html 欄位或為空，target=${targetLanguage}")
            throw IllegalStateException("AI 回應缺少 optimized_html 欄位")
        }

        val optimizedZhHtml = optimizedNode.path("optimized_zh_html").asText(null)
        return HtmlOptimizationResult(
            optimizedHtml = optimizedHtml,
            optimizedZhHtml = optimizedZhHtml
        )
    }

    /**
     * Stage 1: 重寫文章為400-600字的托福難度短文
     *
     * @param title 文章標題
     * @param originalHtml 原始HTML內容
     * @return 重寫後的結果，包含優化後的HTML、清理後的文本等
     */
    fun rewriteStage1(difficulty: DifficultyLevel, title: String?, originalHtml: String): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot process with AI")
        }

        val maxAttempts = 3
        var lastResult: JsonNode? = null
        var lastWordCount = 0
        var lastException: Exception? = null

        for (attempt in 0 until maxAttempts) {
            try {
                // 第一次使用原始提示詞，後續嘗試時增加字數相關指示
                val additionalInstructions = if (attempt == 0) "" else """
                    |目前的文章約有 $lastWordCount 個英文單字，請在不改變主題與結構的前提下，
                    |將原本的文章盡可能地擴寫成更多字。
                    |
                """.trimMargin()

                val systemPrompt = AiArticlePrompts.rewriteStage1SystemPrompt(difficulty)
                val userPrompt = AiArticlePrompts.rewriteStage1UserPrompt(difficulty, title, originalHtml) + additionalInstructions

                val body = buildJsonModeBody(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = 0.2
                )

                logger.info("[AI] Requesting OpenAI model='${model}' for rewriteStage1 (attempt=${attempt + 1}/${maxAttempts}, title='${title ?: "(no title)"}')")
                if (attempt > 0) {
                    logger.debug("[AI] rewriteStage1 retry with additional instructions: $additionalInstructions")
                }

                val responseStr = postChatCompletions(body, operation = "rewriteStage1")
                logger.info("[AI] Raw response for rewriteStage1 (first 400 chars): ${responseStr.take(400)}")

                val (_, content) = extractContentFromResponse(responseStr, "rewriteStage1")
                logger.debug("[AI] rewriteStage1 parsed JSON (first 400 chars): ${content.take(400)}")

                // 先嘗試解析 JSON
                val result = try {
                    objectMapper.readTree(content)
                } catch (e: Exception) {
                    logger.error("[AI] Failed to parse JSON response from rewriteStage1. Content length: ${content.length}")
                    logger.error("[AI] JSON parse error: ${e.message}")
                    logger.error("[AI] Full content: $content")
                    throw IllegalStateException("AI rewriteStage1回應不是有效的 JSON 格式: ${e.message}")
                }

                // 保存當前結果和字數
                lastResult = result
                val cleanedText = result.path("cleaned_text").asText("")
                lastWordCount = countEnglishWords(cleanedText)
                // 驗證字數和段落
                logger.info("[AI] Validating rewriteStage1 result: wordCount=$lastWordCount, difficulty=${difficulty.label}")
                if (!validateRewriteAndProcessResult(result, difficulty)) {
                    logger.warn("[AI] rewriteStage1 attempt ${attempt + 1}/$maxAttempts failed validation for ${difficulty.label}: wordCount=$lastWordCount or not enough paragraphs")
                    // 如果不是最後一次嘗試，則繼續下一次
                    if (attempt < maxAttempts - 1) {
                        continue
                    } else {
                        throw IllegalStateException("AI rewriteStage1 回應不符合字數或段落要求，最後字數: $lastWordCount")
                    }
                }

                logger.info("[AI] rewriteStage1 success on attempt ${attempt + 1}/$maxAttempts, wordCount=$lastWordCount")
                return result

            } catch (e: Exception) {
                lastException = e
                if (e is AiQuotaException || e is IllegalArgumentException) {
                    // 這些例外不需要重試
                    throw e
                }
                // 其他例外，如果不是最後一次嘗試，則繼續
                if (attempt < maxAttempts - 1) {
                    logger.warn("[AI] rewriteStage1 attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
                    continue
                } else {
                    throw e
                }
            }
        }

        // 如果到達這裡，表示所有嘗試都失敗了
        throw lastException
            ?: IllegalStateException("rewriteStage1 無法在 $maxAttempts 次內達到 ${difficulty.label} 字數要求（最後一次 $lastWordCount 字）")
    }

    /**
     * Stage 2: 根據重寫後的文本生成教學解析
     *
     * @param title 文章標題
     * @param rewrittenText 重寫後的文本
     * @return 教學解析結果，包含字彙、文法、題目等
     */
    fun teachingStage2(difficulty: DifficultyLevel, title: String?, rewrittenText: String): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (rewrittenText.isBlank()) {
            throw IllegalArgumentException("Rewritten text is empty; cannot generate teaching materials")
        }

        val systemPrompt = AiArticlePrompts.teachingStage2SystemPrompt(difficulty)
        val userPrompt = AiArticlePrompts.teachingStage2UserPrompt(difficulty, title ?: "(no title)", rewrittenText)

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

    /**
     * 將文章重寫為 400~600 字的托福難度短文，並在單一請求中完成所有 AI 處理。
     *
     * 流程：
     * 1. 將文章標題與原始 HTML 內容送交 AI
     * 2. 請 AI 重寫為 400~600 字的托福難度短文（英文與中文版本）
     * 3. 產生標題、摘要、清理後純文字內容（中英文）
     * 4. 產生詳細的教學解說（字彙、文法、句型、題目等）
     *
     * @param title 文章標題
     * @param originalHtml 原始 HTML 內容
     * @return 包含所有處理結果的 JSON 物件
     */
    @Deprecated("Use rewriteStage1 and teachingStage2 instead for a two-stage approach")
    fun rewriteAndProcess(title: String?, originalHtml: String): JsonNode {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot process with AI")
        }

        val stage1Result = rewriteStage1(title, originalHtml)
        val rewrittenText = stage1Result.path("cleaned_text").asText()
        val teachingResult = teachingStage2(title, rewrittenText)

        // Build a new ObjectNode to properly combine the two results
        val combinedResult = objectMapper.createObjectNode()
        stage1Result.fields().forEach { (key, value) ->
            combinedResult.set<JsonNode>(key, value)
        }
        // If teaching_result has explanation, extract it to top-level
        val explanationNode = teachingResult.path("explanation")
        if (!explanationNode.isMissingNode && !explanationNode.isNull) {
            combinedResult.set<JsonNode>("explanation", explanationNode)
        }
        combinedResult.set<JsonNode>("teaching_result", teachingResult)
        return combinedResult
    }

    /**
     * 計算英文文章的字數
     *
     * @param text 文章內容
     * @return 英文單字數量
     */
    private fun countEnglishWords(text: String): Int {
        if (text.isBlank()) return 0

        // 1. 先移除所有HTML 標籤
        val cleanedFromHtml = text.replace(Regex("<[^>]*>"), " ")

        // 2. 檢查是否包含 Unicode 編碼的字元 (\uXXXX)
        val normalizedText = if (cleanedFromHtml.contains("\\u")) {
            try {
                val unescaped = cleanedFromHtml.replace(Regex("\\\\u([0-9a-fA-F]{4})"), { result ->
                    val hex = result.groupValues[1]
                    val code = Integer.parseInt(hex, 16)
                    String(Character.toChars(code))
                })
                unescaped
            } catch (e: Exception) {
                // 如遇到錯誤，直接使用原始內容
                cleanedFromHtml
            }
        } else {
            cleanedFromHtml
        }

        // 3. 移除非英文字符和標點符號
        return normalizedText
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")  // 移除非英文字符和標點符號
            .trim()
            .split(Regex("\\s+"))
            .filter { word ->
                // 只保留包含至少一個英文字符的單詞
                word.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' }
            }
            .count()
    }

    /**
     * 檢查 HTML 是否有足夠的段落
     *
     * @param html HTML 內容
     * @param minParagraphs 最少段落數量
     * @return true 如果段落數量足夠，否則 false
     */
    private fun hasEnoughParagraphs(html: String?, minParagraphs: Int = 4): Boolean {
        if (html == null || html.isBlank()) {
            return false
        }
        // 使用更複雜的正則表達式來匹配段落
        val paragraphPattern = Regex("<p[^>]*>.*?</p>", RegexOption.DOT_MATCHES_ALL)
        val paragraphs = paragraphPattern.findAll(html).toList()
        val paragraphCount = paragraphs.size

        // 檢查段落是否包含實際內容
        val nonEmptyParagraphs = paragraphs.count {
            // 移除 HTML 標籤，檢查段落是否包含至少兩個單詞
            val content = it.value.replace(Regex("<[^>]*>"), "")
            content.trim().split(Regex("\\s+")).size >= 2
        }

        logger.info("[AI] Found $paragraphCount paragraphs in HTML ($nonEmptyParagraphs non-empty with content)")
        return nonEmptyParagraphs >= minParagraphs
    }

    /**
     * 驗證 AI 重寫和處理的結果
     *
     * @param result AI 回應的 JSON 結果
     * @return true 如果結果有效，否則 false
     */
    private fun validateRewriteAndProcessResult(result: JsonNode, difficulty: DifficultyLevel): Boolean {
        try {
            // 1. 檢查字數
            val cleanedText = result.path("cleaned_text").asText("")
            val wordCount = countEnglishWords(cleanedText)
            logger.debug("[AI] Calculated word count from cleaned_text: $wordCount")
            val minWords = difficulty.minWords
            val maxWords = difficulty.maxWords
            logger.info("[AI] Validating word count: $wordCount (expected: ${maxWords?.let { "$minWords-$it" } ?: "$minWords+"})")

            val validWordCount = if (maxWords != null) wordCount in minWords..maxWords else wordCount >= minWords
            if (!validWordCount) {
                logger.warn("[AI] Article word count = $wordCount, not in required range for ${difficulty.label}")
                return false
            }

            // 2. 檢查段落數量
            val optimizedHtml = result.path("optimized_html").asText("")
            logger.info("[AI] Validating paragraph count in optimized HTML")

            val hasEnoughParagraphs = hasEnoughParagraphs(optimizedHtml)
            if (!hasEnoughParagraphs) {
                logger.warn("[AI] Article does not have enough paragraphs (minimum 4 required)")
                return false
            }

            // 所有驗證通過
            logger.info("[AI] Validation successful: word count ($wordCount) and paragraph count meet requirements")
            return true
        } catch (e: Exception) {
            logger.error("[AI] Error validating rewrite results: ${e.message}", e)
            return false
        }
    }
}
