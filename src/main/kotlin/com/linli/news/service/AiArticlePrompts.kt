package com.linli.news.service

import com.linli.news.model.DifficultyLevel

/**
 * AI 文章處理的 Prompt 產生器。
 *
 * 用來集中管理所有與 OpenAI 互動時使用的提示文字。
 */
object AiArticlePrompts {

    /**
     * 將語言代碼轉換為中文顯示名稱。
     */
    private fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode.lowercase().substringBefore('-')) {
            "en" -> "英文"
            "zh" -> "中文"
            "ja" -> "日文"
            "ko" -> "韓文"
            "fr" -> "法文"
            "de" -> "德文"
            "es" -> "西班牙文"
            "it" -> "義大利文"
            "pt" -> "葡萄牙文"
            "ru" -> "俄文"
            "ar" -> "阿拉伯文"
            "vi" -> "越南文"
            "th" -> "泰文"
            "nl" -> "荷蘭文"
            "pl" -> "波蘭文"
            "tr" -> "土耳其文"
            "sv" -> "瑞典文"
            "da" -> "丹麥文"
            "fi" -> "芬蘭文"
            "no" -> "挪威文"
            "cs" -> "捷克文"
            "el" -> "希臘文"
            "he" -> "希伯來文"
            "hi" -> "印地文"
            "id" -> "印尼文"
            "ms" -> "馬來文"
            else -> languageCode.uppercase() // 回傳大寫代碼作為預設值
        }
    }

    /**
     * 教學解析任務描述（可在多個 Prompt 中重複使用）。
     */
    private val EXPLANATION_TASK_DESCRIPTION = """
        - Vocabulary（雙語：詞、釋義、例句都提供雙語；含詞性，避免太簡單的單字）
        - Grammar（重點規則與雙語解說、雙語例句，難度需稍高一點）
        - Sentence patterns / Phrases / Idioms（雙語解說與雙語例句，避免過於初級）
        - Comprehension Q&A（4 題四選一，題目與答案提供雙語）
    """.trimIndent()

    /**
     * Explanation 區塊的 JSON 結構描述（可在多個 Prompt 中重複使用）。
     */
    private val EXPLANATION_JSON_SCHEMA = """
        "explanation": {
            "vocabulary": [
              {
                "pos": string,
                "word_from": string,
                "word_to": string,
                "definition_from": string,
                "definition_to": string,
                "example_from": string,
                "example_to": string
              }
            ],
            "grammar": [
              {
                "rule_from": string,
                "rule_to": string,
                "explanation_from": string,
                "explanation_to": string,
                "example_from": string,
                "example_to": string
              }
            ],
            "sentence_patterns": [
              {
                "pattern_from": string,
                "pattern_to": string,
                "explanation_from": string,
                "explanation_to": string,
                "example_from": string,
                "example_to": string
              }
            ],
            "phrases_idioms": [
              {
                "phrase_from": string,
                "phrase_to": string,
                "explanation_from": string,
                "explanation_to": string,
                "example_from": string,
                "example_to": string
              }
            ],
            "comprehension_mcq": [
              {
                "question_from": string,
                "question_to": string,
                "options": [
                    {"label": "A", "text_from": string, "text_to": string},
                    {"label": "B", "text_from": string, "text_to": string},
                    {"label": "C", "text_from": string, "text_to": string},
                    {"label": "D", "text_from": string, "text_to": string}
                ],
                "answer": string,              // 僅能是 "A"|"B"|"C"|"D"
                "explanation_from": string,
                "explanation_to": string
              }
            ]
        },
        "tags": [string],                      // 文章相關的主題標籤（3-5個）
        "featured_collections": [string]       // 文章適合的精選專題ID（0-2個）
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
    fun processSystemPrompt(fromLanguage: String, toLanguage: String): String {
        val fromLangName = getLanguageDisplayName(fromLanguage)
        return """
        你是專業的雙語新聞教學助手。
        - 原文語言：$fromLanguage
        - 目標語言：$toLanguage
        任務：
        1) 清理並重新分段原文，輸出 `cleaned_text_from`（使用 $fromLanguage）。
        2) 忠實翻譯全文，輸出 `cleaned_text_to`（使用 $toLanguage，保留專有名詞、數字、時間）。
        3) 以「${fromLangName}教學者」身份撰寫解析：
${EXPLANATION_TASK_DESCRIPTION.prependIndent("           ")}
        4) 產生雙語摘要：
           - `summary_from`：以 Markdown 條列呈現的 $fromLanguage 摘要（至少 3 點）。
           - `summary_to`：對應的 $toLanguage 翻譯（至少 3 點）。
        所有 *_from 欄位必須使用 $fromLanguage，*_to 欄位必須使用 $toLanguage。
        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()
    }

    /**
     * 處理純文字內容時使用的 user prompt。
     */
    fun processUserPrompt(title: String?, textContent: String, fromLanguage: String, toLanguage: String): String = """
        請處理下列文章：
        標題：${title ?: "(no title)"}
        內文：```text
        $textContent
        ```

        請以 JSON 物件回覆，鍵名固定如下（使用英文 key）：
        {
          "title_from": string,      // 使用 $fromLanguage
          "title_to": string,        // 翻譯成 $toLanguage
          "cleaned_text_from": string,
          "cleaned_text_to": string,
          "summary_from": string,    // Markdown 條列，至少 3 點
          "summary_to": string,      // 對應翻譯
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
    fun optimizeHtmlSystemPrompt(fromLanguage: String, toLanguage: String): String = """
        你是專業的雙語新聞 HTML 清理助手。
        - 原文語言：$fromLanguage
        - 目標語言：$toLanguage
        任務：
        1) 解析提供的全文 HTML，萃取並保留與標題、段落內容高度相關的正文。
        2) 依照以下 HTML 結構與清理規則處理內容：
${HTML_STRUCTURE_RULES.prependIndent("       ")}
        3) 產生兩份輸出：
           - `optimized_html_from`：使用 $fromLanguage 的最佳化 HTML（保留原文）。
           - `optimized_html_to`：在結構不變的情況下翻譯為 $toLanguage，保留專有名詞、數字與時間資訊。
        僅輸出 JSON 物件，不要加入額外說明文字。
    """.trimIndent()

    /**
     * 進行 HTML 優化與清理時使用的 user prompt。
     */
    fun optimizeHtmlUserPrompt(title: String?, originalHtml: String, fromLanguage: String, toLanguage: String): String = """
        請清理下列文章 HTML，只保留與新聞主題直接相關的正文內容：
        標題：${title ?: "(no title)"}
        原始 HTML：
        ```html
        $originalHtml
        ```

        請產生 JSON：
        {
          "optimized_html_from": string,
          "optimized_html_to": string
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
        - `optimized_html_from` 維持 $fromLanguage 原文；`optimized_html_to` 需翻譯為 $toLanguage 並保持對應段落
    """.trimIndent()

    /**
     * Stage 1 Rewrite System Prompt，依照難度調整篇幅與語域。
     */
    fun rewriteStage1SystemPrompt(
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): String {
        val difficultyInstruction = difficultyWritingInstruction(difficulty, fromLanguage)
        return """
        你是專業的新聞重寫助手，需根據指定難度輸出。
        - 原文語言：$fromLanguage
        - 目標語言：$toLanguage
        難度設定：${difficulty.label}（${difficulty.cefrCode}）
        $difficultyInstruction
        通用要求：
        - 保留原始 HTML 中的所有 <img> 標籤與對應的 caption，不得移除圖片；如需可微調 alt 文字。
        - ${toLanguage}與${fromLanguage}版本的段落結構需一一對應。
        - **所有 *_from 欄位必須僅包含 $fromLanguage 內容，不得包含 $toLanguage。**
        - **所有 *_to 欄位必須僅包含 $toLanguage 內容，不得包含 $fromLanguage。**
        - **嚴禁產生雙語對照格式（例如：英文段落後緊接中文段落）。**
        - HTML 結構必須遵守以下規則：
${HTML_STRUCTURE_RULES.prependIndent("          ")}
        僅輸出 JSON 物件，不要多餘文字。
        """.trimIndent()
    }

    /**
     * Stage 1 Rewrite User Prompt，包含保留圖片的指示。
     */
    fun rewriteStage1UserPrompt(
        difficulty: DifficultyLevel,
        title: String?,
        originalHtml: String,
        fromLanguage: String,
        toLanguage: String
    ): String {
        val fromLangName = getLanguageDisplayName(fromLanguage)
        val difficultyInstruction = difficultyWritingInstruction(difficulty, fromLanguage)
        return """
        請依照 ${difficulty.label}（${difficulty.cefrCode}）難度，使用 $fromLanguage 原文與 $toLanguage 譯文重寫下列文章：
        標題：${title ?: "(no title)"}
        原始HTML：```html
        $originalHtml
        ```

        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有中文請一律使用「繁體中文」）：
        {
          "optimized_html_from": string,    // 純 $fromLanguage HTML，不得包含 $toLanguage 內容
          "optimized_html_to": string,      // 純 $toLanguage HTML，段落與 optimized_html_from 對應
          "title_from": string,             // 純 $fromLanguage 標題
          "title_to": string,               // 純 $toLanguage 標題
          "cleaned_text_from": string,      // 純 $fromLanguage 文本，不得包含 $toLanguage 內容
          "cleaned_text_to": string         // 純 $toLanguage 文本，不得包含 $fromLanguage 內容
        }

        難度與寫作指引：
        $difficultyInstruction
        **重要限制**：
        1. 文章段落與結構請嚴格遵守上述「難度設定」要求，每段使用 <p> 包裹。
        2. 必須保留原始 HTML 內所有圖片相關元素（figure、img、figcaption），位置盡量對應。
        3. 產出${fromLangName}標題。
        4. **`cleaned_text_from` 必須是完整的純 $fromLanguage 文本，不得包含任何 $toLanguage 內容或雙語對照格式。**
        5. **`cleaned_text_to` 必須是完整的純 $toLanguage 文本，不得包含任何 $fromLanguage 內容或雙語對照格式。**
        6. **`optimized_html_from` 和 `optimized_html_to` 同樣必須各自保持單一語言，不得混雜。**
    """.trimIndent()
    }

    /**
     * Stage 2 Teaching System Prompt，根據難度設定解說深度。
     */
    fun teachingStage2SystemPrompt(
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String,
        availableTags: List<String>,
        availableCollections: List<Pair<String, String>> // (id, description)
    ): String {
        val fromLangName = getLanguageDisplayName(fromLanguage)
        val explanationTask = explanationTaskDescription(difficulty, fromLanguage, toLanguage)

        val tagsInstruction = """
        5) 從以下標籤清單中選擇 3-5 個最符合文章主題的標籤：
           ${availableTags.joinToString(", ")}
           選擇標準：標籤必須與文章的核心主題高度相關
        """.trimIndent()

        val collectionsInstruction = """
        6) 從以下精選專題中選擇 0-2 個最適合的專題ID（可以不選）：
           ${availableCollections.joinToString("\n           ") { "${it.first}: ${it.second}" }}
           選擇標準：文章內容必須與專題描述高度吻合（相似度 >= 60%）
        """.trimIndent()

        return """
        你是專業的${fromLangName}新聞教學助手，需依 ${difficulty.label}（${difficulty.cefrCode}）難度撰寫教學。
        - 原文語言：$fromLanguage
        - 目標語言：$toLanguage
        任務：
        1) 以「${fromLangName}教學者」身份，撰寫詳盡講解：
$explanationTask
        2) 針對重寫後的文章提供雙語標題：`title_from` (使用 $fromLanguage) 與 `title_to` (使用 $toLanguage)。
        3) 生成雙語摘要 (`summary_from`、`summary_to`)：
           - `summary_from`：使用 $fromLanguage，Markdown 條列式（至少三點）。
           - `summary_to`：`summary_from` 的 $toLanguage 翻譯。
        4) HTML 結構必須遵守以下規則：
${HTML_STRUCTURE_RULES.prependIndent("           ")}
        $tagsInstruction
        $collectionsInstruction
        僅輸出 JSON 物件，不要多餘文字。
    """.trimIndent()
    }

    /**
     * Stage 2 Teaching User Prompt.
     */
    fun teachingStage2UserPrompt(
        difficulty: DifficultyLevel,
        title: String,
        rewrittenText: String,
        fromLanguage: String,
        toLanguage: String
    ): String {
        val fromLangName = getLanguageDisplayName(fromLanguage)
        val toLangName = getLanguageDisplayName(toLanguage)
        val explanationTask = explanationTaskDescription(difficulty, fromLanguage, toLanguage)
        return """
        請依 ${difficulty.label}（${difficulty.cefrCode}）難度，使用 $fromLanguage/$toLanguage 雙語教學下列文章：
        標題：$title
        重寫後的${fromLangName}內文：```text
        $rewrittenText
        ```

        請以 JSON 物件回覆，鍵名固定如下（使用英文 key，所有${toLangName}請一律使用「繁體中文」）：
        {
          "title_from": string,         // 使用 $fromLanguage，可自訂
          "title_to": string,           // 翻譯為 $toLanguage
          "summary_from": string,       // $fromLanguage 條列摘要（至少 3 點）
          "summary_to": string,         // $toLanguage 對應翻譯
${EXPLANATION_JSON_SCHEMA.prependIndent("          ")}
        }

        難度提示：
        $explanationTask
        重要要求：
        1. 教學內容必須簡潔且語意化，遵守上述 HTML 規則。
        2. 確保提供雙語標題（title_from 與 title_to）。
        3. 確保提供 $fromLangName 與 $toLangName 摘要，各至少 3 點，使用條列式列出。
        4. 單字、文法、片語、例句不需要html格式，像是不需要<p>
        5. 必須選擇 3-5 個最相關的 tags（從系統提示的清單中選擇）
        6. 選擇 0-2 個最適合的 featured_collections ID（若無特別合適可為空陣列）
    """.trimIndent()
    }

    /**
     * 依難度提供寫作指引文字。
     */
    private fun difficultyWritingInstruction(difficulty: DifficultyLevel, fromLanguage: String = "en"): String {
        return when (difficulty) {
            DifficultyLevel.VERY_EASY -> """
            1. Target Audience: This version is for A2 learners / beginning-level readers.
            2. Language Control: Use basic vocabulary and simple sentence structures (Subject + Verb + Object). Avoid subordinate clauses and complex conjunctions.
            3. Structure: 2–4 paragraphs, each containing 1–3 sentences.
            4. Tone: Informal, simple, and clear.
        """.trimIndent()

            DifficultyLevel.EASY -> """
            1. Target Audience: This version is for B1 learners.
            2. Language Control: Use simple narration and common vocabulary. Include simple conjunctions and a few complex sentences, ensuring the text remains coherent and easy to understand.
            3. Structure: 3–5 paragraphs, each containing 2–4 sentences.
            4. Tone: Conversational, simple, and clear.
        """.trimIndent()

            DifficultyLevel.NORMAL -> """
            1. Target Audience: This version is for B2 learners.
            2. Language Control: Use intermediate vocabulary and moderately complex sentences (including relative clauses). Use simple transitions and provide adequate background or explanation.
            3. Structure: 4–6 paragraphs, each containing 3–5 sentences.
            4. Tone: Moderately formal / standard news tone.
        """.trimIndent()

            DifficultyLevel.HARD -> """
            1. Target Audience: This version is for C1 learners.
            2. Language Control: Use complex sentence structures (including passive voice) and high-level vocabulary. Include detailed background analysis, quotes, or data.
            3. Structure: 5 or more paragraphs, each containing 4–6 sentences. You may use subheadings if appropriate.
            4. Tone: Formal, analytical, and professional.
        """.trimIndent()

            DifficultyLevel.VERY_HARD -> """
            1. Target Audience: This version is for C2 learners / Native-level readers.
            2. Language Control: Use professional vocabulary, complex syntax, and abstract concepts. Include in-depth analysis, critique, or multiple perspectives.
            3. Structure: Flexible structure (Background -> Body -> Analysis -> Conclusion). Paragraphs and sentences can be longer and more complex.
            4. Tone: Highly formal, academic, or professional.
        """.trimIndent()
        }
    }

    /**
     * 依難度調整教學解析的說明文字.
     */
    private fun explanationTaskDescription(
        difficulty: DifficultyLevel,
        fromLanguage: String,
        toLanguage: String
    ): String {
        val fromLangName = getLanguageDisplayName(fromLanguage)
        val toLangName = getLanguageDisplayName(toLanguage)
        val bilingualDesc = "${fromLangName}${toLangName}"

        return when (difficulty) {
            DifficultyLevel.VERY_EASY -> """
            - Vocabulary：挑選 A2 層級的基礎字彙（如日常、學校、家庭），提供詞性、${bilingualDesc}釋義與極簡短的例句。
            - Grammar：聚焦基礎時態（現在/過去簡單式）與 SVO 句構，${bilingualDesc}解說需極為淺白。
            - Sentence patterns / Phrases：極基礎句型（如 There is/are, I like...），附${bilingualDesc}解說與生活化例句。
            - Comprehension Q&A：4 題四選一，題目、選項與答案皆提供雙語，問題應直觀且答案明顯。
        """.trimIndent()

            DifficultyLevel.EASY -> """
            - Vocabulary：挑選符合 B1 的中階字彙，提供詞性、${bilingualDesc}釋義與生活情境例句，避免過於初級或過度專業的用詞。
            - Grammar：聚焦文章相關的核心時態或句型，給出雙語重點與例句，解說務必直白清晰且可立即運用。
            - Sentence patterns / Phrases / Idioms：介紹實用句型與片語，附上雙語解說與例句，句子不要過長，避免太艱澀的習語。
            - Comprehension Q&A：4 題四選一，題目、選項與答案都提供雙語，解析用淺白${toLangName}說明常見迷思或易混淆處。
        """.trimIndent()

            DifficultyLevel.NORMAL -> """
            - Vocabulary：選用 B2 層級的常見但具挑戰性的單字，提供詞性、精確${bilingualDesc}釋義與雙語例句，點出語境或搭配詞。
            - Grammar：說明文章中較進階的語法（如虛擬語氣、分詞構句），給予雙語重點與例句，補充常見錯誤提醒。
            - Sentence patterns / Phrases / Idioms：挑選能展開論述的句型或片語，附雙語解說與例句，可示範轉折、對比等連接。
            - Comprehension Q&A：4 題四選一，雙語題幹與選項，答案後以${toLangName}解析邏輯並強調關鍵線索，難度對應 B2。
        """.trimIndent()

            DifficultyLevel.HARD -> """
            - Vocabulary：使用 C1 層級字彙，涵蓋抽象或專業概念，提供詞性、${bilingualDesc}釋義、語氣/註冊說明與雙語例句，能展示語義層次。
            - Grammar：聚焦複合句、強調句、倒裝等高階結構，雙語解說需指出語用效果並給出多樣例句。
            - Sentence patterns / Phrases / Idioms：選擇能呈現批判或分析的句型與慣用語，雙語解說，例句可加入比喻或轉折。
            - Comprehension Q&A：4 題四選一，雙語呈現，答案解析以${toLangName}詳述推理過程、陷阱與語意細節，難度對應 C1。
        """.trimIndent()

            DifficultyLevel.VERY_HARD -> """
            - Vocabulary：採用 C2 層級的高階或專業詞彙，提供詞性、${bilingualDesc}釋義、語源/搭配詞與雙語例句，凸顯細膩語氣或文化脈絡。
            - Grammar：說明文中精緻的語法選擇（如名詞化、強烈省略、跨句銜接），雙語解析需揭示隱含意涵與語體效果。
            - Sentence patterns / Phrases / Idioms：聚焦高階句型、固定搭配或習語，雙語解說並以學術/專業語域寫例句，可帶批判或分析視角。
            - Comprehension Q&A：4 題四選一，題幹與選項需雙語，答案解析以${toLangName}深入討論推論與批判性細節，難度對應 C2。
        """.trimIndent()
        }
    }
}

