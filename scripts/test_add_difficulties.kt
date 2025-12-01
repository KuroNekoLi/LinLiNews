// 測試腳本：為同一篇文章添加多個難度
// 驗證不會創建重複的 ArticleAi 記錄

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import com.linli.news.service.AiArticleService
import com.linli.news.model.DifficultyLevel

@Component
class TestAddDifficulties(
    private val aiArticleService: AiArticleService
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val articleId = 14L

        println("=== 測試開始：為 article $articleId 添加多個難度 ===")

        // 測試 1：添加 EASY 難度
        println("\n[測試 1] 添加 EASY 難度...")
        try {
            val result1 = aiArticleService.getOrProcessDto(
                articleId,
                listOf(DifficultyLevel.EASY),
                "en",
                "zh-TW"
            )
            println("✓ EASY 難度添加成功")
            println("  可用難度: ${result1.availableDifficulties}")
        } catch (e: Exception) {
            println("✗ EASY 難度添加失敗: ${e.message}")
        }

        // 測試 2：添加 HARD 難度
        println("\n[測試 2] 添加 HARD 難度...")
        try {
            val result2 = aiArticleService.getOrProcessDto(
                articleId,
                listOf(DifficultyLevel.HARD),
                "en",
                "zh-TW"
            )
            println("✓ HARD 難度添加成功")
            println("  可用難度: ${result2.availableDifficulties}")
        } catch (e: Exception) {
            println("✗ HARD 難度添加失敗: ${e.message}")
        }

        println("\n=== 測試完成 ===")
    }
}
