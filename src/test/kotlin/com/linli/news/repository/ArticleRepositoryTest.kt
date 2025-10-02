package com.linli.news.repository

import com.linli.news.domain.Article
import com.linli.news.domain.ArticleSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/**
 * ArticleRepository 單元測試
 *
 * 測試範圍：
 * - 基本 CRUD 操作
 * - 自訂查詢方法（findByOriginalUrl, findBySource_Name, findBySource_Section）
 * - 分頁查詢
 */
@DataJpaTest
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var articleRepository: ArticleRepository

    /**
     * 測試 save 與 findByOriginalUrl 應運作正常
     */
    @Test
    fun `should save and find article by original url`() {
        val source = entityManager.persist(ArticleSource(name = "BBC", rssUrl = "https://feed", section = "news"))

        val article = Article(
            source = source,
            originalUrl = "https://example.com/a1"
        )
        val saved = articleRepository.save(article)
        assertNotNull(saved.id)

        val found = articleRepository.findByOriginalUrl("https://example.com/a1")
        assertNotNull(found)
        assertEquals("https://example.com/a1", found!!.originalUrl)
    }

    /**
     * 測試 findByOriginalUrl 找不到時應回傳 null
     */
    @Test
    fun `should return null when article not found by url`() {
        val found = articleRepository.findByOriginalUrl("https://nonexistent.com")
        assertNull(found)
    }

    /**
     * 測試 findBySource_Name 應正確過濾來源
     */
    @Test
    fun `should filter articles by source name`() {
        val bbcSource = entityManager.persist(ArticleSource(name = "BBC", rssUrl = "https://bbc/rss", section = "news"))
        val cnnSource = entityManager.persist(ArticleSource(name = "CNN", rssUrl = "https://cnn/rss", section = "news"))

        val bbcArticle = Article(source = bbcSource, originalUrl = "https://bbc.com/a1")
        val cnnArticle = Article(source = cnnSource, originalUrl = "https://cnn.com/a1")

        articleRepository.save(bbcArticle)
        articleRepository.save(cnnArticle)
        entityManager.flush()

        val bbcResults = articleRepository.findBySource_Name("BBC", PageRequest.of(0, 10))
        assertEquals(1, bbcResults.content.size)
        assertEquals("BBC", bbcResults.content[0].source.name)
    }

    /**
     * 測試 findBySource_Section 應正確過濾區塊
     */
    @Test
    fun `should filter articles by section`() {
        val techSource = entityManager.persist(
            ArticleSource(name = "BBC", rssUrl = "https://bbc/tech", section = "technology")
        )
        val newsSource = entityManager.persist(
            ArticleSource(name = "BBC", rssUrl = "https://bbc/news", section = "news")
        )

        val techArticle = Article(source = techSource, originalUrl = "https://bbc.com/tech1")
        val newsArticle = Article(source = newsSource, originalUrl = "https://bbc.com/news1")

        articleRepository.save(techArticle)
        articleRepository.save(newsArticle)
        entityManager.flush()

        val techResults = articleRepository.findBySource_Section("technology", PageRequest.of(0, 10))
        assertEquals(1, techResults.content.size)
        assertEquals("technology", techResults.content[0].source.section)
    }

    /**
     * 測試 findBySource_NameAndSource_Section 應正確過濾來源與區塊
     */
    @Test
    fun `should filter articles by both source name and section`() {
        val bbcTechSource = entityManager.persist(
            ArticleSource(name = "BBC", rssUrl = "https://bbc/tech", section = "technology")
        )
        val bbcNewsSource = entityManager.persist(
            ArticleSource(name = "BBC", rssUrl = "https://bbc/news", section = "news")
        )
        val cnnTechSource = entityManager.persist(
            ArticleSource(name = "CNN", rssUrl = "https://cnn/tech", section = "technology")
        )

        val bbcTechArticle = Article(source = bbcTechSource, originalUrl = "https://bbc.com/tech1")
        val bbcNewsArticle = Article(source = bbcNewsSource, originalUrl = "https://bbc.com/news1")
        val cnnTechArticle = Article(source = cnnTechSource, originalUrl = "https://cnn.com/tech1")

        articleRepository.save(bbcTechArticle)
        articleRepository.save(bbcNewsArticle)
        articleRepository.save(cnnTechArticle)
        entityManager.flush()

        val results = articleRepository.findBySource_NameAndSource_Section("BBC", "technology", PageRequest.of(0, 10))
        assertEquals(1, results.content.size)
        assertEquals("BBC", results.content[0].source.name)
        assertEquals("technology", results.content[0].source.section)
    }

    /**
     * 測試分頁查詢應正確運作
     */
    @Test
    fun `should paginate articles correctly`() {
        val source = entityManager.persist(ArticleSource(name = "BBC", rssUrl = "https://feed", section = "news"))

        for (i in 1..15) {
            articleRepository.save(Article(source = source, originalUrl = "https://example.com/a$i"))
        }
        entityManager.flush()

        val page1 = articleRepository.findAll(PageRequest.of(0, 10))
        val page2 = articleRepository.findAll(PageRequest.of(1, 10))

        assertEquals(10, page1.content.size)
        assertEquals(5, page2.content.size)
        assertEquals(15, page1.totalElements)
    }
}
