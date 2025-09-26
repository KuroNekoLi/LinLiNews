import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun main() {
    val objectMapper = ObjectMapper().registerKotlinModule()
    objectMapper.configure(
        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    val jsonString =
        """{"grammar": [{"rule_en": "Present Perfect Continuous Tense", "example_en": "The government has been looking at ways to support the suppliers.", "example_zh": "政府一直在尋找支持供應商的方法。", "explanation_en": "Used to describe an action that started in the past and is still continuing in the present.", "explanation_zh": "用於描述一個在過去開始並且仍在持續進行的動作。"}, {"rule_en": "Passive Voice", "example_en": "The factories were shut down due to the cyber-attack.", "example_zh": "由於網絡攻擊，工廠被關閉。", "explanation_en": "Used when the focus is on the action or when the subject is unknown or irrelevant.", "explanation_zh": "用於當重點在於動作或主語未知或不相關時。"}], "vocabulary": [{"pos": "noun", "word_en": "supply chain", "word_zh": "供應鏈", "example_en": "The disruption in the supply chain caused delays in production.", "example_zh": "供應鏈的中斷導致生產延遲。", "definition_en": "The sequence of processes involved in the production and distribution of a commodity.", "definition_zh": "涉及商品生產和分配的過程序列。"}], "phrases_idioms": [{"phrase_en": "go bust", "example_en": "Many small businesses went bust during the economic downturn.", "example_zh": "在經濟衰退期間，許多小企業破產了。", "explanation_en": "To go bankrupt or fail financially.", "explanation_zh": "破產或財務上失敗。"}], "comprehension_mcq": [{"answer": "A", "options": {"A": "Government buying component parts", "B": "Implementing a Covid-style furlough scheme", "C": "Providing free grants", "D": "Increasing taxes on suppliers"}, "question_en": "What is one of the financial support options being considered for JLR suppliers?", "question_zh": "為JLR供應商考慮的財務支持選項之一是什麼？", "explanation_zh": "文章提到，政府正在考慮購買供應商生產的零部件作為支持選項之一。"}], "sentence_patterns": [{"example_en": "The committee is due to meet on Thursday.", "example_zh": "委員會預定於週四開會。", "pattern_en": "Subject + is/are + due to + verb", "explanation_en": "Used to indicate a scheduled or expected event.", "explanation_zh": "用於表示預定或預期的事件。"}]}"""

    try {
        val jsonNode = objectMapper.readTree(jsonString)
        println("JSON fields present: ${jsonNode.fieldNames().asSequence().toList()}")

        // Test parsing McqOptions specifically
        if (jsonNode.has("comprehension_mcq")) {
            val mcqArray = jsonNode.get("comprehension_mcq")
            if (mcqArray.isArray && mcqArray.size() > 0) {
                val firstMcq = mcqArray.get(0)
                if (firstMcq.has("options")) {
                    val options = firstMcq.get("options")
                    println("MCQ options fields: ${options.fieldNames().asSequence().toList()}")
                    println("Options A: ${options.get("A")?.asText()}")
                }
            }
        }

        println("Testing McqOptions parsing...")
        // Try to parse the full object
        val result = objectMapper.readValue(jsonString, Explanation::class.java)
        println("✅ Success! Parsed explanation with:")
        println("- Vocabulary: ${result.vocabulary?.size}")
        println("- Grammar: ${result.grammar?.size}")
        println("- Sentence Patterns: ${result.sentencePatterns?.size}")
        println("- Phrases Idioms: ${result.phrasesIdioms?.size}")
        println("- Comprehension MCQ: ${result.comprehensionMcq?.size}")

        result.comprehensionMcq?.firstOrNull()?.options?.let { options ->
            println("- First MCQ Options: A=${options.A}, B=${options.B}")
        }

    } catch (e: Exception) {
        println("❌ Failed to parse JSON: ${e.message}")
        e.printStackTrace()
    }
}

// Copy the data classes here for testing
data class Explanation(
    val vocabulary: List<VocabularyItem>? = null,
    val grammar: List<GrammarItem>? = null,
    val sentence_patterns: List<SentencePatternItem>? = null,
    val phrases_idioms: List<PhraseIdiomItem>? = null,
    val comprehension_mcq: List<ComprehensionMcqItem>? = null
)

data class VocabularyItem(
    val pos: String? = null,
    val word_en: String? = null,
    val word_zh: String? = null,
    val definition_en: String? = null,
    val definition_zh: String? = null,
    val example_en: String? = null,
    val example_zh: String? = null
)

data class GrammarItem(
    val rule_en: String? = null,
    val explanation_en: String? = null,
    val explanation_zh: String? = null,
    val example_en: String? = null,
    val example_zh: String? = null
)

data class SentencePatternItem(
    val pattern_en: String? = null,
    val explanation_en: String? = null,
    val explanation_zh: String? = null,
    val example_en: String? = null,
    val example_zh: String? = null
)

data class PhraseIdiomItem(
    val phrase_en: String? = null,
    val explanation_en: String? = null,
    val explanation_zh: String? = null,
    val example_en: String? = null,
    val example_zh: String? = null
)

data class ComprehensionMcqItem(
    val question_en: String? = null,
    val question_zh: String? = null,
    val options: McqOptions? = null,
    val answer: String? = null,
    val explanation_zh: String? = null
)

data class McqOptions(
    val A: String? = null,
    val B: String? = null,
    val C: String? = null,
    val D: String? = null
)