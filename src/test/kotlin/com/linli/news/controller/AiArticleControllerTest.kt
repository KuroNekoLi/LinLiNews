package com.linli.news.controller

import com.linli.news.domain.Article
import com.linli.news.domain.ArticleAi
import com.linli.news.domain.ArticleSource
import com.linli.news.service.AiArticleService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.*

/**
 * AiArticleController 單元測試
 *
 * 測試範圍：
 * - GET /api/ai-articles - 列表查詢
 * - GET /api/ai-articles/{id} - 單篇查詢
 * - GET /api/ai-articles/random - 隨機文章
 * - GET /api/ai-articles/random-with-processing - 隨機並處理
 */
@WebMvcTest(AiArticleController::class)
@ActiveProfiles("test")
class AiArticleControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var aiArticleService: AiArticleService

    private fun sampleArticleAi(id: Long = 1L): ArticleAi {
        val source = ArticleSource(name = "BBC", rssUrl = "https://feed", section = "news").apply { this.id = 100 }
        val article = Article(source = source, originalUrl = "https://example.com/a$id").apply {
            this.id = id
            this.title = "Title $id"
            this.contentHtml = "<p>content</p>"
        }
        return ArticleAi(article = article).apply { this.id = id }
    }

    /**
     * 測試 GET /api/ai-articles 應回傳 200
     */
    @Test
    fun `GET ai-articles should return 200`() {
        val page = PageImpl(listOf(sampleArticleAi(1)))
        every { aiArticleService.listProcessed(0, 20) } returns page

        mockMvc.get("/api/ai-articles")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/ai-articles/{id} 應回傳 200
     */
    @Test
    fun `GET ai-articles by id should return 200`() {
        every { aiArticleService.getOrProcess(10) } returns sampleArticleAi(10)

        mockMvc.get("/api/ai-articles/10")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/ai-articles/random 應回傳 200
     */
    @Test
    fun `GET ai-articles random should return 200`() {
        val list = listOf(sampleArticleAi(1), sampleArticleAi(2))
        every { aiArticleService.random(2, null, null) } returns Pair(list, 2)

        mockMvc.get("/api/ai-articles/random?count=2")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/ai-articles/random-with-processing 應回傳 200 並包含處理統計
     */
    @Test
    fun `GET ai-articles random-with-processing should return 200 with processing stats`() {
        val list = listOf(sampleArticleAi(1))
        every { aiArticleService.randomWithProcessing(1, null, null) } returns Triple(list, 10, 1)

        mockMvc.get("/api/ai-articles/random-with-processing?count=1")
            .andExpect {
                status { isOk() }
                jsonPath("$.articles") { isArray() }
                jsonPath("$.totalAvailable") { value(10) }
                jsonPath("$.processedCount") { value(1) }
            }
    }

    /**
     * 測試 GET /api/ai-articles/random 應支援來源過濾
     */
    @Test
    fun `GET ai-articles random should support source filter`() {
        val list = listOf(sampleArticleAi(1))
        every { aiArticleService.random(5, "BBC", null) } returns Pair(list, 1)

        mockMvc.get("/api/ai-articles/random?count=5&source=BBC")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/ai-articles/random 應支援區塊過濾
     */
    @Test
    fun `GET ai-articles random should support section filter`() {
        val list = listOf(sampleArticleAi(1))
        every { aiArticleService.random(5, null, "technology") } returns Pair(list, 1)

        mockMvc.get("/api/ai-articles/random?count=5&section=technology")
            .andExpect { status { isOk() } }
    }
}