package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleAi
import com.linli.news.domain.ArticleSource
import com.linli.news.repository.ArticleAiRepository
import com.linli.news.repository.ArticleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*

/**
 * AiArticleService 單元測試
 *
 * 測試範圍：
 * - 隨機文章取得（random）
 * - 帶處理的隨機文章（randomWithProcessing）
 * - 已處理文章列表（listProcessed）
 * - 取得或處理單篇文章（getOrProcess）
 */
@DisplayName("AiArticleService Tests")
class AiArticleServiceTest {

    private val articleRepository = mockk<ArticleRepository>()
    private val articleAiRepository = mockk<ArticleAiRepository>()
    private val aiProcessor = mockk<AiArticleProcessorService>()

    private val service = AiArticleService(
        articleRepository = articleRepository,
        articleAiRepository = articleAiRepository,
        aiProcessor = aiProcessor
    )

    private val objectMapper = ObjectMapper()

    // ============ random() 測試 ============

    /**
     * 測試 random 方法應該回傳隨機的 AI 文章清單
     */
    @Test
    fun `should return random AI articles`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article1 = Article(source = source, originalUrl = "https://test.com/1").apply { id = 1 }
        val article2 = Article(source = source, originalUrl = "https://test.com/2").apply { id = 2 }
        val aiArticle1 = ArticleAi(article = article1).apply { id = 1 }
        val aiArticle2 = ArticleAi(article = article2).apply { id = 2 }

        val page = PageImpl(listOf(aiArticle1, aiArticle2))
        every { articleAiRepository.findByArticle_Source_Name(any(), any()) } returns page

        val (result, total) = service.random(count = 1, source = "BBC", section = null)

        assertEquals(1, result.size)
        assertEquals(2, total)
        verify { articleAiRepository.findByArticle_Source_Name("BBC", any()) }
    }

    /**
     * 測試 random 方法當沒有文章時應該回傳空清單
     */
    @Test
    fun `should return empty list when no articles exist`() {
        val emptyPage = PageImpl<ArticleAi>(emptyList())
        every { articleAiRepository.findAll(any<PageRequest>()) } returns emptyPage

        val (result, total) = service.random(count = 5, source = null, section = null)

        assertTrue(result.isEmpty())
        assertEquals(0, total)
    }

    /**
     * 測試 random 方法應該支援來源過濾
     */
    @Test
    fun `should filter by source`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "world")
        val article = Article(source = source, originalUrl = "https://test.com/1").apply { id = 1 }
        val aiArticle = ArticleAi(article = article).apply { id = 1 }

        val page = PageImpl(listOf(aiArticle))
        every { articleAiRepository.findByArticle_Source_Name("BBC", any()) } returns page

        val (result, _) = service.random(count = 10, source = "BBC", section = null)

        assertTrue(result.isNotEmpty())
        verify { articleAiRepository.findByArticle_Source_Name("BBC", any()) }
    }

    /**
     * 測試 random 方法應該支援區塊過濾
     */
    @Test
    fun `should filter by section`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "technology")
        val article = Article(source = source, originalUrl = "https://test.com/1").apply { id = 1 }
        val aiArticle = ArticleAi(article = article).apply { id = 1 }

        val page = PageImpl(listOf(aiArticle))
        every { articleAiRepository.findByArticle_Source_Section("technology", any()) } returns page

        val (result, _) = service.random(count = 10, source = null, section = "technology")

        assertTrue(result.isNotEmpty())
        verify { articleAiRepository.findByArticle_Source_Section("technology", any()) }
    }

    /**
     * 測試 random 方法應該支援來源與區塊同時過濾
     */
    @Test
    fun `should filter by both source and section`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "world")
        val article = Article(source = source, originalUrl = "https://test.com/1").apply { id = 1 }
        val aiArticle = ArticleAi(article = article).apply { id = 1 }

        val page = PageImpl(listOf(aiArticle))
        every {
            articleAiRepository.findByArticle_Source_NameAndArticle_Source_Section(
                "BBC",
                "world",
                any()
            )
        } returns page

        val (result, _) = service.random(count = 10, source = "BBC", section = "world")

        assertTrue(result.isNotEmpty())
        verify { articleAiRepository.findByArticle_Source_NameAndArticle_Source_Section("BBC", "world", any()) }
    }

    // ============ randomWithProcessing() 測試 ============

    /**
     * 測試 randomWithProcessing 方法當文章已處理過時不應重複處理
     */
    @Test
    fun `should not reprocess already processed articles`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article = Article(
            source = source,
            originalUrl = "https://test.com/1",
            contentHtml = "<p>Test content</p>"
        ).apply { id = 1; title = "Title" }

        val existingAi = ArticleAi(article = article).apply { id = 99; titleZh = "已處理" }

        every { articleRepository.findAll() } returns listOf(article)
        every { articleAiRepository.findByArticle_Id(1) } returns Optional.of(existingAi)

        val (result, total, processedCount) = service.randomWithProcessing(1, null, null, null)

        assertEquals(1, result.size)
        assertEquals(1, total)
        assertEquals(0, processedCount)
        verify(exactly = 0) { aiProcessor.process(any<String>(), any<String>()) }
    }

    /**
     * 測試 randomWithProcessing 方法應該過濾掉沒有內容的文章
     */
    @Test
    fun `should filter out articles without content`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val articleWithoutContent = Article(
            source = source,
            originalUrl = "https://test.com/1",
            contentHtml = null
        ).apply { id = 1 }

        every { articleRepository.findAll() } returns listOf(articleWithoutContent)

        val (result, total, processedCount) = service.randomWithProcessing(1, null, null, null)

        assertTrue(result.isEmpty())
        assertEquals(0, total)
        assertEquals(0, processedCount)
    }

    /**
     * 測試 randomWithProcessing 方法應該自動處理未處理的文章
     */
    @Test
    fun `should automatically process unprocessed articles`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article = Article(
            source = source,
            originalUrl = "https://test.com/1",
            contentHtml = "<p>Test content for AI processing</p>"
        ).apply { id = 1; title = "Test Title" }

        // Mock AI 處理回應
        val aiResponse = objectMapper.createObjectNode().apply {
            put("title", "Test Title")
            put("title_zh", "測試標題")
            put("cleaned_text", "Test content for AI processing")
            put("cleaned_text_zh", "測試內容供AI處理")
            put("summary_en", "English summary")
            put("summary_zh", "中文摘要")
            putObject("explanation")
        }

        every { articleRepository.findAll() } returns listOf(article)
        every { articleAiRepository.findByArticle_Id(1) } returns Optional.empty()
        every { articleRepository.findById(1) } returns Optional.of(article)
        every { aiProcessor.process("Test Title", any<String>()) } returns aiResponse
        every {
            aiProcessor.optimizeHtml(
                "Test Title",
                any()
            )
        } returns HtmlOptimizationResult(
            optimizedHtml = "<article><p>Optimized</p></article>",
            optimizedZhHtml = "<article><p>優化內容</p></article>"
        )
        every { articleAiRepository.save(any<ArticleAi>()) } answers { firstArg() }

        val (result, _, processedCount) = service.randomWithProcessing(1, null, null, null)

        assertEquals(1, processedCount)
        assertTrue(result.isNotEmpty())
        verify(exactly = 1) { aiProcessor.process(any<String>(), any<String>()) }
        verify(exactly = 1) { aiProcessor.optimizeHtml(any(), any()) }
    }

    /**
     * 測試 randomWithProcessing 方法處理失敗時應繼續處理其他文章
     */
    @Test
    fun `should continue processing other articles when one fails`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article1 = Article(source = source, originalUrl = "https://test.com/1", contentHtml = "<p>Content 1</p>")
            .apply { id = 1; title = "Title 1" }
        val article2 = Article(source = source, originalUrl = "https://test.com/2", contentHtml = "<p>Content 2</p>")
            .apply { id = 2; title = "Title 2" }

        val aiResponse = objectMapper.createObjectNode().apply {
            put("title", "Title 2")
            put("title_zh", "標題2")
            put("cleaned_text", "Content 2")
            put("cleaned_text_zh", "內容2")
            put("summary_en", "Summary 2")
            put("summary_zh", "摘要2")
            putObject("explanation")
        }

        every { articleRepository.findAll() } returns listOf(article1, article2)
        every { articleAiRepository.findByArticle_Id(1) } returns Optional.empty()
        every { articleAiRepository.findByArticle_Id(2) } returns Optional.empty()
        every { articleRepository.findById(1) } throws RuntimeException("AI 處理失敗")
        every { articleRepository.findById(2) } returns Optional.of(article2)
        every { aiProcessor.process("Title 2", any<String>()) } returns aiResponse
        every {
            aiProcessor.optimizeHtml("Title 2", any())
        } returns HtmlOptimizationResult(
            optimizedHtml = "<article><p>Optimized 2</p></article>",
            optimizedZhHtml = "<article><p>優化內容2</p></article>"
        )
        every { articleAiRepository.save(any<ArticleAi>()) } answers {
            firstArg<ArticleAi>().apply { id = 100 }
        }

        val (_, total, processedCount) = service.randomWithProcessing(2, null, null, null)

        assertEquals(1, processedCount) // 只有一篇成功處理
        assertEquals(2, total) // 總共有2篇文章可選
    }

    // ============ listProcessed() 測試 ============

    /**
     * 測試 listProcessed 方法應該正確返回分頁資料
     */
    @Test
    fun `should return paginated processed articles`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article = Article(source = source, originalUrl = "https://test.com/1").apply { id = 1 }
        val aiArticle = ArticleAi(article = article).apply { id = 1 }

        val page = PageImpl(listOf(aiArticle), PageRequest.of(0, 20), 1)
        every { articleAiRepository.findAll(PageRequest.of(0, 20)) } returns page

        val result = service.listProcessed(0, 20)

        assertEquals(1, result.content.size)
        assertEquals(1, result.totalElements)
        verify { articleAiRepository.findAll(PageRequest.of(0, 20)) }
    }

    // ============ getOrProcess() 測試 ============

    /**
     * 測試 getOrProcess 方法文章已處理時應直接返回
     */
    @Test
    fun `should return existing AI article without reprocessing`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article = Article(
            source = source,
            originalUrl = "https://test.com/1",
            contentHtml = "<p>Content</p>"
        ).apply { id = 1; title = "Title" }
        val existingAi = ArticleAi(article = article).apply { id = 99; titleZh = "已處理標題" }

        every { articleAiRepository.findByArticle_Id(1) } returns Optional.of(existingAi)

        val result = service.getOrProcess(1)

        assertEquals(99, result.id)
        assertEquals("已處理標題", result.titleZh)
        verify(exactly = 0) { aiProcessor.process(any<String>(), any<String>()) }
    }

    /**
     * 測試 getOrProcess 方法文章未處理時應進行 AI 處理
     */
    @Test
    fun `should process article with AI when not yet processed`() {
        val source = ArticleSource(name = "BBC", rssUrl = "https://test.com/rss", section = "news")
        val article = Article(
            source = source,
            originalUrl = "https://test.com/1",
            contentHtml = "<p>Test content</p>"
        ).apply { id = 1; title = "Test Title" }

        val aiResponse = objectMapper.createObjectNode().apply {
            put("title", "Test Title")
            put("title_zh", "測試標題")
            put("cleaned_text", "Test content")
            put("cleaned_text_zh", "測試內容")
            put("summary_en", "Summary")
            put("summary_zh", "摘要")
            putObject("explanation")
        }

        every { articleAiRepository.findByArticle_Id(1) } returns Optional.empty()
        every { articleRepository.findById(1) } returns Optional.of(article)
        every { aiProcessor.process("Test Title", any<String>()) } returns aiResponse
        every {
            aiProcessor.optimizeHtml(
                "Test Title",
                any()
            )
        } returns HtmlOptimizationResult(
            optimizedHtml = "<article><p>Optimized</p></article>",
            optimizedZhHtml = "<article><p>優化內容</p></article>"
        )
        every { articleAiRepository.save(any<ArticleAi>()) } answers {
            firstArg<ArticleAi>().apply { id = 100 }
        }

        val result = service.getOrProcess(1)

        assertNotNull(result.id)
        assertEquals("測試標題", result.titleZh)
        verify(exactly = 1) { aiProcessor.process(any<String>(), any<String>()) }
        verify(exactly = 1) { aiProcessor.optimizeHtml(any(), any()) }
    }
}
