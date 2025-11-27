package com.linli.news.model

/**
 * 文章輸出難度等級，對應 CEFR 分級。
 * AI 會根據難度等級自動調整文章的篇幅、語言複雜度與教學深度。
 *
 * @property label 顯示用名稱
 * @property cefrCode CEFR 等級代碼
 */
@io.swagger.v3.oas.annotations.media.Schema(
    description = """
        AI 產出的文章難度等級，對應 CEFR 分級：
        - VERY_EASY (A2): 初學者/初中生，簡單句型，日常詞彙
        - EASY (B1): 中階學習者，基礎複句，生活情境
        - NORMAL (B2): 中高階學習者，適度複雜句型，標準新聞語氣
        - HARD (C1): 高階學習者，複雜句型，專業詞彙
        - VERY_HARD (C2): 接近母語水平，學術/專業語域
        
        AI 會根據難度自動調整段落數、句子複雜度與教學內容深度，無需指定字數限制。
    """,
    example = "NORMAL"
)
enum class DifficultyLevel(
    val label: String,
    val cefrCode: String
) {
    VERY_EASY("VeryEasy", "A2"),
    EASY("Easy", "B1"),
    NORMAL("Normal", "B2"),
    HARD("Hard", "C1"),
    VERY_HARD("VeryHard", "C2");
}
