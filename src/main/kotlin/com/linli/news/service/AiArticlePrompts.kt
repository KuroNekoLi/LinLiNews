package com.linli.news.service

/**
 * AI 文章處理的 Prompt 產生器。
 *
 * 用來集中管理所有與 OpenAI 互動時使用的提示文字。
 */
object AiArticlePrompts {

    /**
     * 教學解析任務描述（可在多個 Prompt 中重複使用）。
     */
    private val EXPLANATION_TASK_DESCRIPTION = """
        - Vocabulary（雙語：詞、釋義、例句都提供中英文；含詞性，避免太簡單的單字）
        - Grammar（重點規則與雙語解說、雙語例句，難度需稍高一點）
        - Sentence patterns / Phrases / Idioms（雙語解說與雙語例句，避免過於初級）
        - Comprehension Q&A（4 題四選一，題目與答案提供中英文）
    """.trimIndent()

    /**
     * Explanation 區塊的 JSON 結構描述（可在多個 Prompt 中重複使用）。
     */
    private val EXPLANATION_JSON_SCHEMA = """
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
    """.trimIndent()

    /**
     * HTML 結構與清理規則（供 HTML 優化與重寫場景共用）。
     */
    private val HTML_STRUCTURE_RULES = """
        HTML 結構與清理規則：
        1) 僅保留與標題、段落內容高度相關的正文。
        2) 移除導覽列、頁尾、側邊欄、廣告、推薦閱讀、訂閱提示、社群按鈕、評論區、追蹤 script 等與正文無關的元素。
        3) 僅保留必要的結構標籤，例如 article、section、h2-h6、p、blockquote、ul/ol/li、figure、figcaption、img。
        4) 移除多餘的 class、style、data-* 屬性，保留語意元素與必要的 alt 屬性。
        5) 移除所有超連結 <a>，僅保留文字內容（保留原句，不要刪除文字）。
        6) 只輸出 HTML 片段（fragment），不要包含 <!doctype>、<html>、<head>、<body> 等外層標籤。
        7) 不要添加任何 CSS 樣式，讓客戶端負責樣式渲染。
    """.trimIndent()

    /**
     * 處理純文字內容時使用的 system prompt。
     */
    fun processSystemPrompt(): String = """
        你是專業的英文新聞教學助手。
        任務：
        1) 處理提供的英文純文字內容。
        2) 將英文內容翻譯為繁體中文（保留專有名詞，時間地點數字精確），並且「良好分段」。
        3) 以「英文教學者」身份，撰寫詳盡講解：
${EXPLANATION_TASK_DESCRIPTION.prependIndent("           ")}
        4) 產生雙語摘要：
           - 英文摘要：使用條列式（至少 3 點）
           - 中文摘要：使用條列式（至少 3 點）
        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()

    /**
     * 處理純文字內容時使用的 user prompt。
     */
    fun processUserPrompt(title: String?, textContent: String): String = """
        請處理下列文章：
        標題：${title ?: "(no title)"}
        內文：```text
        $textContent
        ```
        
        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）：
        {
          "title": string,
          "title_zh": string,
          "cleaned_text": string,
          "cleaned_text_zh": string,
          "summary_en": string,
          "summary_zh": string,
${EXPLANATION_JSON_SCHEMA.prependIndent("          ")}
        }
        
        題目難度請「避免過於簡單」：
        - 不要出是非題；
        - 選項需具迷惑性，不可直接複製原句；
        - 優先考察同義字、語境推論、代名詞指涉、時態語氣辨識、轉折語義等。
        摘要格式：
        - 使用 Markdown 風格條列，每一點以 "- " 起始
        - 確保每一語言至少三點摘要
    """.trimIndent()

    /**
     * 進行 HTML 優化與清理時使用的 system prompt。
     */
    fun optimizeHtmlSystemPrompt(): String = """
        你是專業的英文新聞 HTML 清理助手。
        任務：
        1) 解析提供的全文 HTML，萃取並保留與標題、段落內容高度相關的正文。
        2) 依照以下 HTML 結構與清理規則處理內容：
${HTML_STRUCTURE_RULES.prependIndent("       ")}
        3) 產生兩份輸出：
           - `optimized_html`：保留英文原文的最佳化 HTML
           - `optimized_zh_html`：在結構不變的情況下翻譯為繁體中文，保留專有名詞、數字與時間資訊
        僅輸出 JSON 物件，不要加入額外說明文字。
    """.trimIndent()

    /**
     * 進行 HTML 優化與清理時使用的 user prompt。
     */
    fun optimizeHtmlUserPrompt(title: String?, originalHtml: String): String = """
        請清理下列文章 HTML，只保留與新聞主題直接相關的正文內容：
        標題：${title ?: "(no title)"}
        原始 HTML：
        ```html
        $originalHtml
        ```

        請產生 JSON：
        {
          "optimized_html": string,
          "optimized_zh_html": string
        }

        產出要求：
        - **移除所有 <a> 標籤，保留其中的文字內容**
        - **只輸出 HTML 片段，從 <article> 或第一個內容標籤開始**
        - **不要包含 <!doctype>、<html>、<head>、<style>、<body> 等標籤**
        - 以語意標籤包裹正文：<article>、<section>、<h2>/<h3>、<p>、<ul>/<ol>、<blockquote>
        - 僅保留與內容相關的媒體（figure、img、figcaption），確保 img 具有 alt 屬性
        - 移除所有 class、style、data-* 屬性
        - 移除與內文無關或重複的元素、追蹤程式碼
        - 保持內容的原始順序與完整性
        - `optimized_html` 維持英文原文；`optimized_zh_html` 需翻譯為繁體中文並保持對應段落
    """.trimIndent()

    /**
     * 完全重寫 rewriteAndProcessSystemPrompt，按照可用的所有規則輸出。
     */
    fun rewriteAndProcessSystemPrompt(): String = """
        你是專業的英文新聞教學助手。
        任務：
        1) 先重寫提供的英文文章正文，產出一篇 **介於 400 至 600 個英文單字（English words）的托福難度英文短文**。
           - 只計算英文正文的單字數，不包含標題、摘要與教學解析。
           - 如果字數少於 400 或多於 600，你必須自行修改與補充內容，直到介於 400~600 單字之間。
        2) 將重寫後的英文正文翻譯為繁體中文（保留專有名詞、時間、地點與數字，避免故意誤譯）。
        3) 以「英文教學者」身份，撰寫詳盡講解：
${EXPLANATION_TASK_DESCRIPTION.prependIndent("           ")}
        4) 產生雙語摘要與標題。
        5) HTML 結構必須遵守以下規則：
${HTML_STRUCTURE_RULES.prependIndent("           ")}
        6) 分段與 HTML 要求：
           - 英文正文必須拆成至少 4 個自然段，每段 2–4 句。
           - 使用 <p> 標籤包裹每一段落，不得將全文包在單一 <p> 之中。
           - 中文正文的段落結構必須與英文正文一一對應。
        7) 所有長度與分段要求為最高優先順序，若與其他要求衝突，以長度與分段要求為主。

        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()

    /**
     * 完全重寫 rewriteAndProcessUserPrompt，按照最新規則精確指定所有內容。
     */
    fun rewriteAndProcessUserPrompt(title: String?, originalHtml: String): String = """
        請處理下列文章，將其重寫為 **400~600 個英文單字** 的托福難度文章，並確保至少 4 個段落，每段 2–4 句：
        標題：${title ?: "(no title)"}
        原始HTML：```html
        $originalHtml
        ```
        
        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）：
        {
          "optimized_html": string,
          "optimized_zh_html": string,
          "title": string,
          "title_zh": string,
          "cleaned_text": string,
          "cleaned_text_zh": string,
          "word_count": number,             // 英文正文的單字數
          "summary_en": string,
          "summary_zh": string,
${EXPLANATION_JSON_SCHEMA.prependIndent("          ")}
        }
        
        重要要求：
        1. 重寫的文章長度必須控制在 400~600 個英文單字之間，托福難度。
        2. HTML 結構必須簡潔且語意化，遵守上述 HTML 規則。
        3. 重寫後的英文與中文 HTML 要保持相同結構。
        4. 文章段落必須至少 4 段，且每段都用 <p> 標籤包裹。
        5. 每段文字必須含 2-4 個句子，確保文章結構良好。
        6. 題目難度適中，選項需有辨識度。
    """.trimIndent()

    /**
     * Stage 1 Rewrite System Prompt.
     */
    fun rewriteStage1SystemPrompt(): String = """
        你是專業的英文新聞重寫助手。
        任務：
        1) 先重寫提供的英文文章正文，產出一篇 **介於 400 至 600 個英文單字（English words）的托福難度英文短文**。
           - 我會在伺服器端重新計算 cleaned_text 的實際英文單字數，若不在 400–600 之間就視為失敗。
           - 因此你必須增加足夠細節、例子與解釋，確保實際單字數落在 400–600 之間，請勿隨意估計 word_count。
        2) 將重寫後的英文正文翻譯為繁體中文（保留專有名詞、時間、地點與數字，避免故意誤譯）。
        3) 英文正文建議拆成 **6–8 個自然段**，每段 **3–5 句**。
           - 每句可稍微寫長一點（約 20–30 個英文單字），以便達到總字數要求。
           - 每個段落都必須用 <p> 標籤包裹。
        4) 中文正文的段落結構必須與英文正文一一對應。
        5) 為重寫後的文章創作適當的英文標題。
        6) HTML 結構必須遵守以下規則：
${HTML_STRUCTURE_RULES.prependIndent("           ")}
        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()

    /**
     * Stage 1 Rewrite User Prompt.
     */
    fun rewriteStage1UserPrompt(title: String?, originalHtml: String): String = """
        請重寫下列文章，將其重寫為 **400~600 個英文單字** 的托福難度文章，並確保至少 4 個段落，每段 2–4 句：
        標題：${title ?: "(no title)"}
        原始HTML：```html
        $originalHtml
        ```
        
        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）：
        {
          "optimized_html": string,
          "optimized_zh_html": string,
          "title": string,
          "cleaned_text": string,
          "cleaned_text_zh": string,
          "word_count": number             // 英文正文的單字數
        }
        
        重要要求：
        1. 重寫的文章長度必須控制在 400~600 個英文單字之間，托福難度。
        2. 文章段落必須至少 4 段，且每段都用 <p> 標籤包裹。
        3. 每段文字必須含 2-4 個句子，確保文章結構良好。
        4. HTML 結構必須簡潔且語意化，遵守上述 HTML 規則。
        5. 提供重寫後文章的適當英文標題。
    """.trimIndent()

    /**
     * Stage 2 Teaching System Prompt.
     */
    fun teachingStage2SystemPrompt(): String = """
        你是專業的英文新聞教學助手。
        任務：
        1) 以「英文教學者」身份，撰寫詳盡講解：
${EXPLANATION_TASK_DESCRIPTION.prependIndent("           ")}
        2) 請針對重寫後的文章提供適當的英文標題 (title) 與其繁體中文翻譯 (title_zh)，
           - 英文標題可根據內容創作，不一定使用原標題；
           - 中文標題需忠實翻譯英文標題。
        3) 生成雙語摘要 (summary_en, summary_zh)：
           - 英文摘要 summary_en 請使用 Markdown 條列式（每一點以 "- " 起始，至少三點），簡潔明確。
           - 中文摘要 summary_zh 請忠實翻譯英文摘要，亦需條列（至少三點）。
        4) HTML 結構必須遵守以下規則：
${HTML_STRUCTURE_RULES.prependIndent("           ")}
        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()

    /**
     * Stage 2 Teaching User Prompt.
     */
    fun teachingStage2UserPrompt(title: String, rewrittenText: String): String = """
        請教學下列重寫後的文章：
        標題：$title
        重寫後的英文內文：```text
        $rewrittenText
        ```
        
        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）：
        {
          "title": string,              // 可以使用原標題或為內容創作更適合的新標題
          "title_zh": string,           // 標題的繁體中文翻譯
          "summary_en": string,         // 英文摘要（條列式，至少 3 點）
          "summary_zh": string,         // 繁體中文摘要（條列式，至少 3 點）
${EXPLANATION_JSON_SCHEMA.prependIndent("          ")}
        }
        
        重要要求：
        1. 教學內容必須簡潔且語意化，遵守上述 HTML 規則。
        2. 確保提供雙語標題（title 與 title_zh）。
        3. 確保提供英文與繁體中文摘要，各至少 3 點，使用條列式列出。
    """.trimIndent()
}