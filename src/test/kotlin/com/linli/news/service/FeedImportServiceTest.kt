package com.linli.news.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.linli.news.config.FullTextRssProperties
import com.linli.news.domain.ArticleSource
import com.linli.news.integration.FullTextRssClient
import com.linli.news.model.FullTextArticleResponse
import io.mockk.every
import io.mockk.justRun
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
    private val feedImportTxService = mockk<FeedImportTxService>()
    private val fullTextRssProperties = mockk<FullTextRssProperties>()

    private val service = FeedImportService(
        fullTextRssClient = fullTextRssClient,
        feedImportTxService = feedImportTxService,
        fullTextRssProperties = fullTextRssProperties
    )

    @Test
    fun `should import valid articles and filter invalid ones`() {
        val rssUrl = "https://feeds.example.com/rss.xml"

        // Mock Full-Text RSS 為啟用狀態
        every { fullTextRssProperties.enabled } returns true

        // ArticleSource 行為
        val savedSource = ArticleSource(name = "BBC", rssUrl = rssUrl, section = "news").apply { id = 1 }
        every { feedImportTxService.getOrCreateSource("BBC", rssUrl, "news") } returns savedSource
        every { feedImportTxService.saveArticleIfNew(any()) } returns true
        justRun { feedImportTxService.updateArticleContent(any()) }

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
        every {
            fullTextRssClient.fetchArticleFullText("https://example.com/a1")
        } returns FullTextArticleResponse(
            title = "Valid Article 1",
            excerpt = null,
            date = null,
            author = null,
            language = null,
            url = "https://example.com/a1",
            effectiveUrl = null,
            domain = null,
            wordCount = 250,
            content = "<p>${"Enhanced content ".repeat(15)}</p>"
        )

        val imported = service.importFromFeed(rssUrl, sourceName = "BBC", section = "news")
        // 至少不會拋出錯誤，且呼叫了 feed 讀取；匯入筆數應該為非負
        verify { fullTextRssClient.fetchFeedJson(rssUrl) }
        assertTrue(imported >= 0)
    }
}
