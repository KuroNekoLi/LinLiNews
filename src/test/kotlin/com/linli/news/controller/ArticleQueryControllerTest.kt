package com.linli.news.controller

import com.linli.news.domain.Article
import com.linli.news.domain.ArticleSource
import com.linli.news.integration.FullTextRssClient
import com.linli.news.jobs.BbcImportJobService
import com.linli.news.repository.ArticleRepository
import com.linli.news.service.AiArticleService
import com.linli.news.service.FeedImportService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.*

/**
 * ArticleQueryController 單元測試
 *
 * 測試範圍：
 * - GET /api/articles - 列表查詢（支援來源、區塊、分頁過濾）
 * - GET /api/articles/{id} - 單篇查詢
 */
@WebMvcTest(ArticleQueryController::class)
@ActiveProfiles("test")
@DisplayName("ArticleQueryController Tests")
class ArticleQueryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var articleRepository: ArticleRepository

    @MockkBean
    lateinit var fullTextRssClient: FullTextRssClient

    @MockkBean
    lateinit var feedImportService: FeedImportService

    @MockkBean
    lateinit var aiArticleService: AiArticleService

    @MockkBean
    lateinit var bbcImportJobService: BbcImportJobService

    private fun sampleArticle(id: Long = 1L, sourceName: String = "BBC", section: String = "news"): Article {
        val source = ArticleSource(name = sourceName, rssUrl = "https://feed", section = section)
            .apply { this.id = 100 }
        return Article(source = source, originalUrl = "https://example.com/a$id").apply {
            this.id = id
            this.title = "Title $id"
            this.contentHtml = "<p>content $id</p>"
        }
    }

    /**
     * 測試 GET /api/articles 應回傳 200
     */
    @Test
    fun `GET articles should return 200`() {
        every { articleRepository.findAll(any<PageRequest>()) } returns PageImpl(emptyList())

        mockMvc.get("/api/articles")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles 應支援分頁參數
     */
    @Test
    fun `GET articles should support pagination parameters`() {
        val page = PageImpl(listOf(sampleArticle(1)), PageRequest.of(1, 10), 1)
        every { articleRepository.findAll(any<PageRequest>()) } returns page

        mockMvc.get("/api/articles?page=1&size=10")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles 應支援來源過濾
     */
    @Test
    fun `GET articles should support source filter`() {
        val page = PageImpl(listOf(sampleArticle(1, sourceName = "BBC")))
        every { articleRepository.findBySource_Name("BBC", any<PageRequest>()) } returns page

        mockMvc.get("/api/articles?source=BBC")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles 應支援區塊過濾
     */
    @Test
    fun `GET articles should support section filter`() {
        val page = PageImpl(listOf(sampleArticle(1, section = "technology")))
        every { articleRepository.findBySource_Section("technology", any<PageRequest>()) } returns page

        mockMvc.get("/api/articles?section=technology")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles 應支援來源與區塊同時過濾
     */
    @Test
    fun `GET articles should support both source and section filters`() {
        val page = PageImpl(listOf(sampleArticle(1, sourceName = "BBC", section = "world")))
        every {
            articleRepository.findBySource_NameAndSource_Section("BBC", "world", any<PageRequest>())
        } returns page

        mockMvc.get("/api/articles?source=BBC&section=world")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles/{id} 找到文章時應回傳 200
     */
    @Test
    fun `GET article by id should return 200 when found`() {
        every { articleRepository.findById(1) } returns Optional.of(sampleArticle(1))

        mockMvc.get("/api/articles/1")
            .andExpect { status { isOk() } }
    }

    /**
     * 測試 GET /api/articles/{id} 找不到時應回傳 404
     */
    @Test
    fun `GET article by id should return 404 when not found`() {
        every { articleRepository.findById(999) } returns Optional.empty()

        mockMvc.get("/api/articles/999")
            .andExpect { status { isNotFound() } }
    }
}
