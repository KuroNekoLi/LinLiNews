package com.linli.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.linli.news.domain.Article
import com.linli.news.domain.ArticleSource
import com.linli.news.integration.FullTextRssClient
import com.linli.news.repository.ArticleRepository
import com.linli.news.repository.ArticleSourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class FeedImportService(
    private val fullTextRssClient: FullTextRssClient,
    private val articleRepository: ArticleRepository,
    private val articleSourceRepository: ArticleSourceRepository
) {

    /**
     * 從 RSS 匯入全文文章並儲存。
     *
     * @param rssUrl RSS 來源
     * @param sourceName 來源名稱（例如 CNN）
     * @param section 區塊（如 world/technology）
     * @return 匯入筆數
     */
    @Transactional
    fun importFromFeed(rssUrl: String, sourceName: String = "CNN", section: String? = null): Int {
        // 查找或創建 ArticleSource，確保使用正確的來源名稱和區塊
        val source = articleSourceRepository.findByRssUrl(rssUrl)?.apply {
            // 更新現有來源的名稱和區塊（如果有變化）
            if (this.name != sourceName || this.section != section) {
                println("DEBUG: Updating source name from '${this.name}' to '$sourceName' and section from '${this.section}' to '$section'")
                this.name = sourceName
                this.section = section
                articleSourceRepository.save(this)
            }
        } ?: articleSourceRepository.save(ArticleSource(name = sourceName, rssUrl = rssUrl, section = section))

        val json = fullTextRssClient.fetchFeedJson(rssUrl)
        val items = extractItems(json)
        println("DEBUG: Found ${items.size} items in feed from source: $sourceName, section: $section")

        var imported = 0
        for ((index, item) in items.withIndex()) {
            val link = getText(item, "link")
            println("DEBUG: Processing item $index, link = $link")

            if (link == null) {
                println("DEBUG: Skipping item $index - no link found")
                continue
            }

            // 去重
            val existing = articleRepository.findByOriginalUrl(link)
            if (existing != null) {
                println("DEBUG: Skipping item $index - duplicate URL: $link")
                continue
            }

            // 檢查所有可用欄位
            val title = getText(item, "title")
            val description = getText(item, "description")
            val pubDate = getText(item, "pubDate")
            val content = getText(item, "content") ?: getText(item, "content:encoded") ?: getText(item, "fullcontent")

            // 過濾非新聞文章內容
            if (!isValidNewsArticle(title, description, link)) {
                println("DEBUG: Skipping item $index - not a valid news article: $title")
                continue
            }

            println("DEBUG: Item $index fields:")
            println("  title: $title")
            println("  description: ${description?.take(100)}...")
            println("  pubDate: $pubDate")
            println("  content: ${if (content != null) "有全文內容 (${content.length} 字)" else "無全文內容"}")
            println("  available fields: ${item.fieldNames().asSequence().toList()}")

            val article = Article(
                source = source, // 使用更新後的來源
                title = title,
                excerpt = description,
                author = getText(item, "author"),
                language = getText(item, "dc_language") ?: getText(item, "language"),
                domain = getText(item, "domain"),
                originalUrl = link,
                effectiveUrl = getText(item, "effective_url"),
                contentHtml = content ?: description, // 如果沒有全文內容，使用摘要
                wordCount = getText(item, "word_count")?.toIntOrNull(),
                publishedAt = parseDate(pubDate)
            )

            try {
                articleRepository.save(article)
                imported++
                println("DEBUG: Successfully imported article from $sourceName/${section}: $title")

                // 檢查是否有完整內容
                if (content != null && content.length > 200) {
                    println("DEBUG: Article has full content (${content.length} chars)")
                } else {
                    println("DEBUG:  Article has limited content, attempting to fetch full text...")

                    // 嘗試使用 Full-Text RSS extract API 取得完整內容
                    try {
                        val fullTextResponse = fullTextRssClient.fetchArticleFullText(link)
                        if (!fullTextResponse.content.isNullOrBlank() && fullTextResponse.content!!.length > 200) {
                            article.contentHtml = fullTextResponse.content
                            article.author = fullTextResponse.author ?: article.author
                            article.language = fullTextResponse.language ?: article.language
                            article.wordCount = fullTextResponse.wordCount ?: article.wordCount
                            article.effectiveUrl = fullTextResponse.url ?: article.effectiveUrl

                            articleRepository.save(article)
                            println("DEBUG: Successfully fetched and saved full content (${fullTextResponse.content!!.length} chars)")
                        } else {
                            println("DEBUG:  Full-text extraction returned limited content")
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Failed to fetch full content: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: Failed to save article: ${e.message}")
            }
        }

        println("DEBUG: Import completed for $sourceName/$section. Total imported: $imported out of ${items.size}")
        return imported
    }

    private fun extractItems(root: JsonNode): List<JsonNode> {
        val channel = root.path("rss").path("channel")
        val itemNode = channel.path("item")
        return when {
            itemNode.isArray -> itemNode.toList()
            itemNode.isMissingNode -> emptyList()
            else -> listOf(itemNode)
        }
    }

    private fun getText(node: JsonNode, field: String): String? {
        return node.path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
    }

    private fun parseDate(text: String?): ZonedDateTime? {
        if (text.isNullOrBlank()) return null
        return try {
            // 嘗試 RFC1123（常見於 RSS）
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
        } catch (_: Exception) {
            try {
                ZonedDateTime.parse(text)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isValidNewsArticle(title: String?, description: String?, link: String?): Boolean {
        if (title.isNullOrBlank() || link.isNullOrBlank()) {
            return false
        }

        // 排除明顯的非新聞內容
        val invalidPatterns = listOf(
            // 報紙頭版合集
            "the papers",
            "paper review",
            "front page",
            "headlines",

            // 媒體內容
            "sounds play",
            "iplayer",
            "video",
            "watch:",
            "listen:",
            "play now",

            // 非內容頁面
            "live updates",
            "as it happened",
            "minute by minute",

            // 導航/目錄頁面
            "more stories",
            "latest news",
            "breaking news updates"
        )

        // 檢查標題是否包含無效模式
        val titleLower = title.lowercase()
        if (invalidPatterns.any { pattern -> titleLower.contains(pattern) }) {
            return false
        }

        // 檢查 URL 是否指向非文章頁面
        val urlLower = link.lowercase()
        val invalidUrlPatterns = listOf(
            "sounds/play",
            "iplayer/episode",
            "/video/",
            "/videos/",
            "/live/",
            "/gallery/",
            "/in-pictures/"
        )

        if (invalidUrlPatterns.any { pattern -> urlLower.contains(pattern) }) {
            return false
        }

        // 檢查內容是否足夠豐富（如果有description）
        if (!description.isNullOrBlank()) {
            val textContent = extractTextFromHtml(description)
            if (textContent != null && textContent.length < 50) {
                // 如果純文字內容少於50字符，可能不是完整文章
                return false
            }
        }

        return true
    }

    private fun extractTextFromHtml(html: String?): String? {
        if (html == null) return null

        // 簡單的HTML標籤移除
        val pattern = Pattern.compile("<[^>]*>")
        val matcher = pattern.matcher(html)
        return matcher.replaceAll(" ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
