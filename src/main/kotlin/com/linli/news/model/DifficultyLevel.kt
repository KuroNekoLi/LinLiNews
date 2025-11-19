package com.linli.news.model

/**
 * 文章輸出難度等級，對應 CEFR 分級。
 *
 * @property label 顯示用名稱
 * @property cefrCode CEFR 等級代碼
 * @property minWords 建議最少英文單字數
 * @property maxWords 建議最多英文單字數，若無上限則為 null
 */
enum class DifficultyLevel(
    val label: String,
    val cefrCode: String,
    val minWords: Int,
    val maxWords: Int?
) {
    EASY("Easy", "B1", 300, 600),
    NORMAL("Normal", "B2", 600, 900),
    HARD("Hard", "C1", 900, 1200),
    VERY_HARD("VeryHard", "C2", 1200, null);
}
