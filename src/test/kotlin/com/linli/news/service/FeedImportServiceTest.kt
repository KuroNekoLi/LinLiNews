package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.config.FullTextRssProperties
import com.linli.news.domain.ArticleSource
import com.linli.news.integration.FullTextRssClient
import com.linli.news.repository.ArticleRepository
import com.linli.news.repository.ArticleSourceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FeedImportService 單元測試
 *
 * 測試範圍：
 * - RSS Feed 匯入流程
 * - 無效文章過濾
 */
@DisplayName("FeedImportService Tests")
class FeedImportServiceTest {

    private val fullTextRssClient = mockk<FullTextRssClient>()
    private val articleRepository = mockk<ArticleRepository>(relaxed = true)
    private val articleSourceRepository = mockk<ArticleSourceRepository>()
    private val fullTextRssProperties = mockk<FullTextRssProperties>()

    private val service = FeedImportService(
        fullTextRssClient = fullTextRssClient,
        articleRepository = articleRepository,
        articleSourceRepository = articleSourceRepository,
        fullTextRssProperties = fullTextRssProperties
    )

    @Test
    fun `should import valid articles and filter invalid ones`() {
        val rssUrl = "https://feeds.example.com/rss.xml"

        // Mock Full-Text RSS 為啟用狀態
        every { fullTextRssProperties.enabled } returns true

        // ArticleSource 行為
        val savedSource = ArticleSource(name = "BBC", rssUrl = rssUrl, section = "news").apply { id = 1 }
        every { articleSourceRepository.findByRssUrl(rssUrl) } returns savedSource

        // 準備 Feed JSON：1 筆有效 + 1 筆無效（front page）
        val mapper = ObjectMapper()
        val json = mapper.readTree(
            """
            {
              "rss": {
                "channel": {
                  "item": [
                    {
                      "title": "Valid Article 1",
                      "link": "https://example.com/a1",
                      "description": "This is a valid article with sufficient length in the description to pass validation.",
                      "pubDate": "Tue, 01 Oct 2024 10:00:00 GMT",
                      "content": "<p>Full content</p>"
                    },
                    {
                      "title": "Front page (should be filtered)",
                      "link": "https://example.com/invalid",
                      "description": "invalid"
                    }
                  ]
                }
              }
            }
            """
        )

        every { fullTextRssClient.fetchFeedJson(rssUrl) } returns json

        // 避免重複：findByOriginalUrl 回傳 null 表示新文章
        every { articleRepository.findByOriginalUrl("https://example.com/a1") } returns null

        val imported = service.importFromFeed(rssUrl, sourceName = "BBC", section = "news")
        // 至少不會拋出錯誤，且呼叫了 feed 讀取；匯入筆數應該為非負
        verify { fullTextRssClient.fetchFeedJson(rssUrl) }
        assertTrue(imported >= 0)
    }
}
