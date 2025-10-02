package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

/**
 * Service that calls OpenAI GPT-4o to post-process article HTML.
 *
 * Responsibilities:
 * - Remove non-article parts (ads, related links, navigation, newsletter signups, promos)
 * - Drop non-content images (logos, icons, ads, social embeds)
 * - Produce cleaned HTML, plain text, zh-TW translation
 * - Produce English-learning explanations (vocabulary/grammar/sentence patterns)
 * - Return a strict JSON object (using OpenAI JSON mode)
 */
@Service
class AiArticleProcessorService(
    @Value("\${openai.api-key:}") private val apiKey: String,
    @Value("\${openai.base-url:https://api.openai.com/v1}") private val baseUrl: String,
    @Value("\${openai.model:gpt-4o}") private val model: String,
    @Value("\${openai.max-tokens:12000}") private val maxTokens: Int,
) {
    private val logger = LoggerFactory.getLogger(AiArticleProcessorService::class.java)
    private val objectMapper = ObjectMapper()

    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer $apiKey")
        .defaultHeader("User-Agent", "LinLiNews/1.0 (+SpringBoot)")
        .build()

    /**
     * Call OpenAI with small retry for transient errors. Do not retry on insufficient_quota.
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

    fun process(title: String?, textContent: String?): JsonNode {
        require(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (textContent.isNullOrBlank()) {
            throw IllegalArgumentException("Article text content is empty; cannot process with AI")
        }

        val systemPrompt = """
            你是專業的英文新聞教學助手。
            任務：
            1) 處理提供的英文純文字內容。
            2) 將英文內容翻譯為繁體中文（保留專有名詞，時間地點數字精確），並且「良好分段」：每個自然段使用\n\n分隔，不要長篇大段；遇到小標、清單可保留結構語意（以文字方式表達）。
            3) 以「英文教學者」身份，撰寫詳盡講解：
               - Vocabulary（雙語：詞、釋義、例句都提供中英文；含詞性）不要挑太簡單的單字
               - Grammar（重點規則與雙語解說、雙語例句）應該要有一點難度的文法
               - Sentence patterns / Phrases / Idioms（雙語解說與雙語例句）不要太簡單
               - Comprehension Q&A（4 題，題目與答案也請提供中英文）
            僅輸出 JSON 物件，不要多餘文字。
        """.trimIndent()

        // 使用 JSON 模式，要求固定鍵名（英文）
        val userPrompt = """
            請處理下列文章：
            標題：${title ?: "(no title)"}
            內文：```text
            $textContent
            ```
            
            請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）。嚴格遵守結構，避免遺漏或改動 key 名稱：
             {
               "title": string,
               "title_zh": string,
               "cleaned_text": string,
               "cleaned_text_zh": string,  // 需良好分段，每段以\n\n分隔
               "explanation": {
                 "vocabulary": [
                   {
                     "pos": string,
                     "word_en": string,
                     "word_zh": string,
                     "definition_en": string,
                     "definition_zh": string,
                     "example_en": string,
                     "example_zh": string
                   }
                 ],
                 "grammar": [
                   {
                     "rule_en": string,
                     "explanation_en": string,
                     "explanation_zh": string,
                     "example_en": string,
                     "example_zh": string
                   }
                 ],
                 "sentence_patterns": [
                   {
                     "pattern_en": string,
                     "explanation_en": string,
                     "explanation_zh": string,
                     "example_en": string,
                     "example_zh": string
                   }
                 ],
                 "phrases_idioms": [
                   {
                     "phrase_en": string,
                     "explanation_en": string,
                     "explanation_zh": string,
                     "example_en": string,
                     "example_zh": string
                   }
                 ],
                 "comprehension_mcq": [
                   {
                     "question_en": string,
                     "question_zh": string,
                     "options": {"A": string, "B": string, "C": string, "D": string},
                     "answer": string,              // 僅能是 "A"|"B"|"C"|"D"
                     "explanation_zh": string       // 詳細中文解析（不要太簡單）
                   }
                 ]
              }
             }
            
            題目難度請「避免過於簡單」：
            - 不要出是非題；
            - 選項需具迷惑性，不可直接複製原句；
            - 優先考察同義字、語境推論、代名詞指涉、時態語氣辨識、轉折語義等。
        """.trimIndent()

        val body = mapOf(
            "model" to model,
            "temperature" to 0.2,
            "max_tokens" to maxTokens,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        logger.info("[AI] Requesting OpenAI model='${model}' with maxTokens=${maxTokens} for title='${title ?: "(no title)"}'")
        val responseStr = postChatCompletions(body, operation = "process")
        logger.info("[AI] Raw response (first 400 chars): ${responseStr.take(400)}")

        val root = objectMapper.readTree(responseStr)
        val content = root.path("choices").path(0).path("message").path("content").asText()
        if (content.isBlank()) {
            logger.error("OpenAI 回傳內容為空，原始: $responseStr")
            throw IllegalStateException("OpenAI 回傳內容為空")
        }

        logger.info("[AI] Content length: ${content.length} chars")
        logger.info("[AI] Parsed content JSON (first 400 chars): ${content.take(400)}")

        // 檢查是否被截斷（結尾不是完整的JSON）
        if (!content.trim().endsWith("}")) {
            logger.warn("[AI] Response appears to be truncated - content doesn't end with '}'")
            logger.info("[AI] Last 200 chars: ${content.takeLast(200)}")

            // 檢查 finish_reason 來確認是否因為 max_tokens 而截斷
            val finishReason = root.path("choices").path(0).path("finish_reason").asText()
            if (finishReason == "length") {
                logger.warn("[AI] Response was truncated due to max_tokens limit. Current: ${maxTokens}")
                // 嘗試修復 JSON 或提供降級方案
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

    fun optimizeHtml(title: String?, originalHtml: String): String {
        require(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY / openai.api-key is required" }
        if (originalHtml.isBlank()) {
            throw IllegalArgumentException("Article HTML content is empty; cannot optimize with AI")
        }

        val systemPrompt = """
    你是專業的英文新聞 HTML 清理助手。
    任務：
    1) 解析提供的全文 HTML，萃取並保留與標題、段落內容高度相關的正文。
    2) 移除導覽列、頁尾、側邊欄、廣告、推薦閱讀、訂閱提示、社群按鈕、評論區、追蹤 script 等與正文無關的元素。
    3) 僅保留必要的結構標籤，例如 article、section、h2-h6、p、blockquote、ul/ol/li、figure、figcaption、img。
    4) 移除多餘的 class、style、data-* 屬性，保留語意元素與必要的 alt 屬性。
    5) **只輸出 HTML 片段（fragment），不要包含 <!doctype>、<html>、<head>、<body> 等外層標籤。**
    6) **不要添加任何 CSS 樣式，讓客戶端負責樣式渲染。**
    僅輸出 JSON 物件，不要加入額外說明文字。
""".trimIndent()

        val userPrompt = """
    請清理下列文章 HTML，只保留與新聞主題直接相關的正文內容：
    標題：${title ?: "(no title)"}
    原始 HTML：
    ```html
    $originalHtml
    ```
    
    請產生 JSON：
    {
      "optimized_html": string
    }
    
    產出要求：
    - **只輸出 HTML 片段，從 <article> 或第一個內容標籤開始**
    - **不要包含 <!doctype>、<html>、<head>、<style>、<body> 等標籤**
    - 以語意標籤包裹正文：<article>、<section>、<h2>/<h3>、<p>、<ul>/<ol>、<blockquote>
    - 僅保留與內容相關的媒體（figure、img、figcaption），確保 img 具有 alt 屬性
    - 移除所有 class、style、data-* 屬性
    - 移除與內文無關或重複的元素、追蹤程式碼
    - 保持內容的原始順序與完整性
""".trimIndent()


        val body = mapOf(
            "model" to model,
            "temperature" to 0.1,
            "max_tokens" to maxTokens,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        logger.info("[AI] Requesting OpenAI model='${model}' for HTML optimization (title='${title ?: "(no title)"}')")
        val responseStr = postChatCompletions(body, operation = "optimizeHtml")
        logger.info("[AI] Raw optimizeHtml response (first 400 chars): ${responseStr.take(400)}")

        val root = objectMapper.readTree(responseStr)
        val content = root.path("choices").path(0).path("message").path("content").asText()
        if (content.isBlank()) {
            logger.error("OpenAI optimizeHtml 回傳內容為空，原始: $responseStr")
            throw IllegalStateException("OpenAI optimizeHtml 回傳內容為空")
        }

        logger.info("[AI] optimizeHtml content length: ${content.length} chars")
        logger.debug("[AI] optimizeHtml parsed JSON (first 400 chars): ${content.take(400)}")

        val optimizedNode = objectMapper.readTree(content)
        val optimizedHtml = optimizedNode.path("optimized_html").asText(null)
        if (optimizedHtml.isNullOrBlank()) {
            logger.error("[AI] optimizeHtml JSON 缺少 optimized_html 欄位或為空")
            throw IllegalStateException("AI 回應缺少 optimized_html 欄位")
        }

        return optimizedHtml
    }

    // 只生成 explanation（補救用）：輸入標題與英文純文字，回傳 { "explanation": { ...固定結構... } }
    fun generateExplanationOnly(title: String?, cleanedText: String): JsonNode {
        require(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY / openai.api-key is required" }

        val systemPrompt = """
            你是專業英文教學者。僅根據提供的英文內文，產生教學用的 explanation 區塊，難度避免過於簡單：
            - Vocabulary：雙語、含詞性、英英釋義與雙語例句
            - Grammar：重點規則、雙語解說與雙語例句
            - Sentence patterns / Phrases / Idioms：雙語解說與雙語例句
            - Comprehension MCQ：4 選 1（A/B/C/D 英文選項），附中文詳解，題目避免太簡單
            僅輸出 JSON 物件，不要多餘文字。
        """.trimIndent()

        val userPrompt = """
            標題：${title ?: "(no title)"}
            英文內文：
            ```text
            $cleanedText
            ```
            
            請回覆：
            {
              "explanation": {
                 "vocabulary": [
                   {"pos": string, "word_en": string, "word_zh": string, "definition_en": string, "definition_zh": string, "example_en": string, "example_zh": string}
                 ],
                 "grammar": [
                   {"rule_en": string, "explanation_en": string, "explanation_zh": string, "example_en": string, "example_zh": string}
                 ],
                 "sentence_patterns": [
                   {"pattern_en": string, "explanation_en": string, "explanation_zh": string, "example_en": string, "example_zh": string}
                 ],
                 "phrases_idioms": [
                   {"phrase_en": string, "explanation_en": string, "explanation_zh": string, "example_en": string, "example_zh": string}
                 ],
                 "comprehension_mcq": [
                   {"question_en": string, "question_zh": string, "options": {"A": string, "B": string, "C": string, "D": string}, "answer": string, "explanation_zh": string}
                 ]
              }
            }
        """.trimIndent()

        val body = mapOf(
            "model" to model,
            "temperature" to 0.2,
            "max_tokens" to maxTokens,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        val responseStr = postChatCompletions(body, operation = "generateExplanationOnly")

        val root = objectMapper.readTree(responseStr)
        val content = root.path("choices").path(0).path("message").path("content").asText()
        if (content.isBlank()) throw IllegalStateException("OpenAI 回傳內容為空")
        return objectMapper.readTree(content)
    }
}
