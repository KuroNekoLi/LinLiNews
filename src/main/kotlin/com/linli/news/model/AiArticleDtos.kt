package com.linli.news.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "AiArticleDetail", description = "統一的文章資料物件（含 AI 視圖欄位）")
data class AiArticleDetail(
    @field:Schema(description = "文章ID")
    val id: Long,
    @field:Schema(description = "來源名稱，例如 BBC、CNN")
    val sourceName: String?,
    @field:Schema(description = "來源區塊，例如 news、world、technology")
    val section: String?,
    @field:Schema(description = "原始文章標題")
    val title: String?,
    @field:Schema(description = "發布時間（ISO-8601 字串）")
    val publishedAt: String?,
    @field:Schema(description = "原始文章連結")
    val originalUrl: String,
    @field:Schema(description = "原始 HTML 內容（未經 AI 處理）")
    val contentHtml: String?,

    @field:Schema(description = "AI 產出的原文標題")
    val titleFrom: String?,
    @field:Schema(description = "AI 產出的目標語言標題")
    val titleTo: String?,
    @field:JsonProperty("title_zh")
    @field:Schema(description = "向下相容的中文標題欄位（等同 titleTo）")
    val titleZh: String?,

    @field:Schema(description = "AI 產出的原文摘要（條列）")
    val summaryFrom: String?,
    @field:Schema(description = "AI 產出的目標語言摘要（條列）")
    val summaryTo: String?,
    @field:JsonProperty("summary_en")
    @field:Schema(description = "向下相容的英文摘要欄位（等同 summaryFrom）")
    val summaryEn: String?,
    @field:JsonProperty("summary_zh")
    @field:Schema(description = "向下相容的中文摘要欄位（等同 summaryTo）")
    val summaryZh: String?,

    @field:JsonProperty("requested_difficulties")
    @Schema(description = "使用者指定的難度等級（可複選，預設包含 Normal）")
    val requestedDifficulties: List<DifficultyLevel>,

    @field:JsonProperty("from_language")
    @Schema(description = "原文語言碼（BCP-47）")
    val fromLanguage: String,
    @field:JsonProperty("to_language")
    @Schema(description = "目標語言碼（BCP-47）")
    val toLanguage: String,

    @Schema(description = "依語言與難度回傳的內容")
    val content: DifficultyContent?,

    @field:JsonProperty("available_difficulties")
    @Schema(description = "此語言組合目前可用的難度列表")
    val availableDifficulties: List<DifficultyLevel> = emptyList(),

    @field:JsonProperty("available_views")
    @Schema(description = "所有視圖（語言+難度）的摘要清單")
    val availableViews: List<AiViewAvailability> = emptyList()
)

@Schema(name = "AiViewAvailability", description = "可用視圖（語言對＋難度）")
data class AiViewAvailability(
    @field:JsonProperty("from_language")
    val fromLanguage: String,
    @field:JsonProperty("to_language")
    val toLanguage: String,
    val difficulty: DifficultyLevel
)

@Schema(name = "DifficultyContent", description = "依語言與難度分級的文章內容與解析")
data class DifficultyContent(
    val difficulty: DifficultyLevel,
    @field:JsonProperty("from_language")
    val fromLanguage: String,
    @field:JsonProperty("to_language")
    val toLanguage: String,
    @field:JsonProperty("optimized_html_from")
    @Schema(description = "重寫後的原文 HTML 內容")
    val optimizedHtmlFrom: String?,
    @field:JsonProperty("optimized_html_to")
    @Schema(description = "重寫後的目標語言 HTML 內容")
    val optimizedHtmlTo: String?,
    @field:JsonProperty("cleaned_text_from")
    @Schema(description = "重寫後的原文純文字")
    val cleanedTextFrom: String?,
    @field:JsonProperty("cleaned_text_to")
    @Schema(description = "重寫後的目標語言純文字")
    val cleanedTextTo: String?,
    @field:Schema(description = "教學解說物件")
    val explanation: Explanation?
)

@Schema(name = "Explanation", description = "AI 教學解說：單字、文法、句型與閱讀理解題")
data class Explanation(
    val vocabulary: List<VocabularyItem>? = null,
    val grammar: List<GrammarItem>? = null,
    @field:JsonProperty("sentence_patterns")
    val sentencePatterns: List<SentencePatternItem>? = null,
    @field:JsonProperty("phrases_idioms")
    val phrasesIdioms: List<PhraseIdiomItem>? = null,
    @field:JsonProperty("comprehension_mcq")
    val comprehensionMcq: List<ComprehensionMcqItem>? = null
)

@Schema(name = "VocabularyItem")
data class VocabularyItem(
    @field:Schema(description = "詞性")
    val pos: String? = null,
    @field:JsonProperty("word_from")
    val wordFrom: String? = null,
    @field:JsonProperty("word_to")
    val wordTo: String? = null,
    @field:JsonProperty("definition_from")
    val definitionFrom: String? = null,
    @field:JsonProperty("definition_to")
    val definitionTo: String? = null,
    @field:JsonProperty("example_from")
    val exampleFrom: String? = null,
    @field:JsonProperty("example_to")
    val exampleTo: String? = null
)

@Schema(name = "GrammarItem")
data class GrammarItem(
    @field:JsonProperty("rule_from")
    val ruleFrom: String? = null,
    @field:JsonProperty("rule_to")
    val ruleTo: String? = null,
    @field:JsonProperty("explanation_from")
    val explanationFrom: String? = null,
    @field:JsonProperty("explanation_to")
    val explanationTo: String? = null,
    @field:JsonProperty("example_from")
    val exampleFrom: String? = null,
    @field:JsonProperty("example_to")
    val exampleTo: String? = null
)

@Schema(name = "SentencePatternItem")
data class SentencePatternItem(
    @field:JsonProperty("pattern_from")
    val patternFrom: String? = null,
    @field:JsonProperty("pattern_to")
    val patternTo: String? = null,
    @field:JsonProperty("explanation_from")
    val explanationFrom: String? = null,
    @field:JsonProperty("explanation_to")
    val explanationTo: String? = null,
    @field:JsonProperty("example_from")
    val exampleFrom: String? = null,
    @field:JsonProperty("example_to")
    val exampleTo: String? = null
)

@Schema(name = "PhraseIdiomItem")
data class PhraseIdiomItem(
    @field:JsonProperty("phrase_from")
    val phraseFrom: String? = null,
    @field:JsonProperty("phrase_to")
    val phraseTo: String? = null,
    @field:JsonProperty("explanation_from")
    val explanationFrom: String? = null,
    @field:JsonProperty("explanation_to")
    val explanationTo: String? = null,
    @field:JsonProperty("example_from")
    val exampleFrom: String? = null,
    @field:JsonProperty("example_to")
    val exampleTo: String? = null
)

@Schema(name = "ComprehensionMcqItem")
data class ComprehensionMcqItem(
    @field:JsonProperty("question_from")
    val questionFrom: String? = null,
    @field:JsonProperty("question_to")
    val questionTo: String? = null,
    val options: List<McqOption>? = null,
    val answer: String? = null,
    @field:JsonProperty("explanation_from")
    val explanationFrom: String? = null,
    @field:JsonProperty("explanation_to")
    val explanationTo: String? = null
)

@Schema(name = "McqOption")
data class McqOption(
    val label: String,
    @field:JsonProperty("text_from")
    val textFrom: String? = null,
    @field:JsonProperty("text_to")
    val textTo: String? = null
)

@Schema(name = "RandomArticlesFilters", description = "隨機文章查詢的過濾條件")
data class RandomArticlesFilters(
    @field:Schema(description = "來源過濾，例如 BBC、CNN")
    val source: String?,
    @field:Schema(description = "區塊過濾，例如 news、world、technology")
    val section: String?,
    @field:Schema(description = "指定 AI 輸出的難度等級（可複選）")
    val difficulties: List<DifficultyLevel>,
    @field:JsonProperty("from_language")
    @field:Schema(description = "原文語言碼")
    val fromLanguage: String?,
    @field:JsonProperty("to_language")
    @field:Schema(description = "目標語言碼")
    val toLanguage: String?
)

@Schema(name = "RandomArticlesResponse", description = "隨機取得 AI 文章的回應")
data class RandomArticlesResponse(
    @field:Schema(description = "訊息，預設為 success")
    val message: String = "success",
    @field:Schema(description = "請求的文章數")
    val requestedCount: Int,
    @field:Schema(description = "實際回傳的文章數")
    val actualCount: Int,
    @field:Schema(description = "資料庫內可用的總筆數")
    val totalAvailable: Int,
    @field:Schema(description = "使用的過濾條件")
    val filters: RandomArticlesFilters,
    @field:Schema(description = "文章清單")
    val articles: List<AiArticleDetail>
)

@Schema(name = "RandomWithProcessingResponse", description = "隨機抽取並自動 AI 處理的回應")
data class RandomWithProcessingResponse(
    @field:Schema(description = "訊息，預設為 success")
    val message: String = "success",
    @field:Schema(description = "請求的文章數")
    val requestedCount: Int,
    @field:Schema(description = "實際回傳的文章數")
    val actualCount: Int,
    @field:Schema(description = "資料庫內可用的總筆數")
    val totalAvailable: Int,
    @field:Schema(description = "此次實際執行 AI 處理的文章數")
    val processedCount: Int,
    @field:Schema(description = "使用的過濾條件")
    val filters: RandomArticlesFilters,
    @field:Schema(description = "文章清單")
    val articles: List<AiArticleDetail>
)
