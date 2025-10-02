package com.linli.news.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator
import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility

@Schema(name = "AiArticleDetail", description = "統一的文章資料物件（含 AI 欄位）")
data class AiArticleDetail(
    @field:Schema(description = "文章ID")
    val id: Long,
    @field:Schema(description = "來源名稱，例如 BBC、CNN")
    val sourceName: String?,
    @field:Schema(description = "來源區塊，例如 news、world、technology")
    val section: String?,
    @field:Schema(description = "文章標題（英文原文）")
    val title: String?,
    @field:Schema(description = "發布時間（ISO-8601 字串）")
    val publishedAt: String?,
    @field:Schema(description = "原始文章連結")
    val originalUrl: String,
    @field:Schema(description = "完整 HTML 內容（原始）")
    val contentHtml: String?,
    @field:JsonProperty("optimized_zh_html")
    @field:Schema(description = "AI 產生的繁體中文 HTML 內容")
    val optimizedZhHtml: String?,
    @field:JsonProperty("optimized_html")
    @field:Schema(description = "AI 產生的英文 HTML 內容")
    val optimizedHtml: String?,

    // AI 產出欄位
    @field:JsonProperty("title_zh")
    @Schema(description = "AI 產生的中文標題")
    val titleZh: String?,

    @field:JsonProperty("summary_en")
    @Schema(description = "AI 產生的英文摘要")
    val summaryEn: String?,

    @field:JsonProperty("summary_zh")
    @Schema(description = "AI 產生的中文摘要")
    val summaryZh: String?,

    @field:JsonProperty("cleaned_text")
    @Schema(description = "AI 清理後的英文純文字")
    val cleanedText: String?,

    @field:JsonProperty("cleaned_text_zh")
    @Schema(description = "AI 翻譯後的中文純文字，良好分段（以空行分段）")
    val cleanedTextZh: String?,

    @field:Schema(description = "AI 教學解說物件")
    val explanation: Explanation?
)

@Schema(name = "Explanation", description = "AI 教學解說：單字、文法、句型與閱讀理解題")
data class Explanation(
    @field:Schema(description = "單字講解清單") val vocabulary: List<VocabularyItem>? = null,
    @field:Schema(description = "文法講解清單") val grammar: List<GrammarItem>? = null,
    @field:JsonProperty("sentence_patterns")
    @field:Schema(description = "句型講解清單") val sentencePatterns: List<SentencePatternItem>? = null,
    @field:JsonProperty("phrases_idioms")
    @field:Schema(description = "片語/成語講解清單") val phrasesIdioms: List<PhraseIdiomItem>? = null,
    @field:JsonProperty("comprehension_mcq")
    @field:Schema(description = "閱讀理解多選題清單") val comprehensionMcq: List<ComprehensionMcqItem>? = null
)

@Schema(name = "VocabularyItem")
data class VocabularyItem(
    @field:Schema(description = "詞性") val pos: String? = null,
    @field:JsonProperty("word_en") @field:Schema(description = "單字（英文）") val wordEn: String? = null,
    @field:JsonProperty("word_zh") @field:Schema(description = "單字（中文）") val wordZh: String? = null,
    @field:JsonProperty("definition_en") @field:Schema(description = "釋義（英文）") val definitionEn: String? = null,
    @field:JsonProperty("definition_zh") @field:Schema(description = "釋義（中文）") val definitionZh: String? = null,
    @field:JsonProperty("example_en") @field:Schema(description = "例句（英文）") val exampleEn: String? = null,
    @field:JsonProperty("example_zh") @field:Schema(description = "例句（中文）") val exampleZh: String? = null
)

@Schema(name = "GrammarItem")
data class GrammarItem(
    @field:JsonProperty("rule_en") @field:Schema(description = "規則（英文簡述）") val ruleEn: String? = null,
    @field:JsonProperty("explanation_en") @field:Schema(description = "解說（英文）") val explanationEn: String? = null,
    @field:JsonProperty("explanation_zh") @field:Schema(description = "解說（中文）") val explanationZh: String? = null,
    @field:JsonProperty("example_en") @field:Schema(description = "例句（英文）") val exampleEn: String? = null,
    @field:JsonProperty("example_zh") @field:Schema(description = "例句（中文）") val exampleZh: String? = null
)

@Schema(name = "SentencePatternItem")
data class SentencePatternItem(
    @field:JsonProperty("pattern_en") @field:Schema(description = "句型（英文）") val patternEn: String? = null,
    @field:JsonProperty("explanation_en") @field:Schema(description = "解說（英文）") val explanationEn: String? = null,
    @field:JsonProperty("explanation_zh") @field:Schema(description = "解說（中文）") val explanationZh: String? = null,
    @field:JsonProperty("example_en") @field:Schema(description = "例句（英文）") val exampleEn: String? = null,
    @field:JsonProperty("example_zh") @field:Schema(description = "例句（中文）") val exampleZh: String? = null
)

@Schema(name = "PhraseIdiomItem")
data class PhraseIdiomItem(
    @field:JsonProperty("phrase_en") @field:Schema(description = "片語/成語（英文）") val phraseEn: String? = null,
    @field:JsonProperty("explanation_en") @field:Schema(description = "解說（英文）") val explanationEn: String? = null,
    @field:JsonProperty("explanation_zh") @field:Schema(description = "解說（中文）") val explanationZh: String? = null,
    @field:JsonProperty("example_en") @field:Schema(description = "例句（英文）") val exampleEn: String? = null,
    @field:JsonProperty("example_zh") @field:Schema(description = "例句（中文）") val exampleZh: String? = null
)

@Schema(name = "ComprehensionMcqItem")
data class ComprehensionMcqItem(
    @field:JsonProperty("question_en") @field:Schema(description = "題目（英文）") val questionEn: String? = null,
    @field:JsonProperty("question_zh") @field:Schema(description = "題目（中文）") val questionZh: String? = null,
    @field:Schema(description = "選項（英文）") val options: McqOptions? = null,
    @field:Schema(description = "正確選項（A/B/C/D）") val answer: String? = null,
    @field:JsonProperty("explanation_zh") @field:Schema(description = "詳解（中文）") val explanationZh: String? = null
)

@Schema(name = "McqOptions")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = McqOptionsDeserializer::class)
@JsonSerialize(using = McqOptionsSerializer::class)
@JsonAutoDetect(
    fieldVisibility = Visibility.ANY,
    getterVisibility = Visibility.NONE,
    isGetterVisibility = Visibility.NONE,
    setterVisibility = Visibility.NONE
)
data class McqOptions @JsonCreator constructor(
    @field:JsonProperty("A") @get:JsonProperty("A") @param:JsonProperty("A") @field:Schema(description = "選項 A（英文）") val A: String? = null,
    @field:JsonProperty("B") @get:JsonProperty("B") @param:JsonProperty("B") @field:Schema(description = "選項 B（英文）") val B: String? = null,
    @field:JsonProperty("C") @get:JsonProperty("C") @param:JsonProperty("C") @field:Schema(description = "選項 C（英文）") val C: String? = null,
    @field:JsonProperty("D") @get:JsonProperty("D") @param:JsonProperty("D") @field:Schema(description = "選項 D（英文）") val D: String? = null
)
