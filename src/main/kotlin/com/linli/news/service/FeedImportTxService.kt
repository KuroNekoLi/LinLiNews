package com.linli.news.service

import com.linli.news.domain.Article
import com.linli.news.domain.ArticleSource
import com.linli.news.repository.ArticleRepository
import com.linli.news.repository.ArticleSourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feed u532fu5165u4ea4u6613u670du52d9
 *
 * u5c08u8cacu8655u7406u77edu4ea4u6613u7684u6578u64dau5eabu64cdu4f5cuff0cu6bcfu500bu65b9u6cd5u90fdu662fu4e00u500bu55aeu7368u7684 @Transactional u64cdu4f5c
 * u907fu514du9577u6642u9593u4ea4u6613u9020u6210u7684u6578u64dau5eabu9396u5b9au554fu984c
 */
@Service
class FeedImportTxService(
    private val articleRepository: ArticleRepository,
    private val articleSourceRepository: ArticleSourceRepository
) {
    private val logger = LoggerFactory.getLogger(FeedImportTxService::class.java)

    /**
     * u67e5u8a62u6216u5275u5efau4f86u6e90uff08u77edu4ea4u6613uff09
     *
     * @param sourceName u4f86u6e90u540du7a31
     * @param rssUrl RSS u7db2u5740
     * @param section u5340u584auff08u53efu9078uff09
     * @return u66f4u65b0u5f8cu7684 ArticleSource u7269u4ef6
     */
    @Transactional
    fun getOrCreateSource(sourceName: String, rssUrl: String, section: String? = null): ArticleSource {
        val source = articleSourceRepository.findByRssUrl(rssUrl)?.apply {
            // u66f4u65b0u73feu6709u4f86u6e90u7684u540du7a31u548cu5340u584auff08u5982u679cu6709u8b8au5316uff09
            if (this.name != sourceName || this.section != section) {
                logger.debug("Updating source name from '${this.name}' to '$sourceName' and section from '${this.section}' to '$section'")
                this.name = sourceName
                this.section = section
                articleSourceRepository.save(this)
            }
        } ?: articleSourceRepository.save(ArticleSource(name = sourceName, rssUrl = rssUrl, section = section))

        return source
    }

    /**
     * u4fddu5b58u6587u7ae0u81f3u6578u64dau5eabuff08u5982u679cu662fu65b0u6587u7ae0uff09
     *
     * @param article u8981u4fddu5b58u7684u6587u7ae0
     * @return u662fu5426u5df2u5132u5b58u65b0u6587u7ae0
     */
    @Transactional
    fun saveArticleIfNew(article: Article): Boolean {
        // u53bbu91cd - u6aa2u67e5u66ffu4ee3u8b58u5225u689du4ef6uff1au6a19u984c + u767cu5e03u6642u9593uff0cu6216 URL
        val existing = article.originalUrl?.let { articleRepository.findByOriginalUrl(it) }
        if (existing != null) {
            logger.debug("Skipping article - duplicate URL: ${article.originalUrl}")
            return false
        }

        // u6aa2u67e5u6a19u984cu548cu767cu5e03u6642u9593u91cdu8907
        if (article.title != null && article.publishedAt != null) {
            val duplicate = articleRepository.findByTitleAndPublishedAt(
                article.title!!,
                article.publishedAt!!
            )
            if (duplicate != null) {
                logger.debug("Skipping article - duplicate title and date: ${article.title}")
                return false
            }
        }

        try {
            articleRepository.save(article)
            logger.info("Successfully saved article: ${article.title}")
            return true
        } catch (e: Exception) {
            logger.error("Failed to save article: ${e.message}", e)
            throw e
        }
    }

    /**
     * u66f4u65b0u5df2u5b58u5728u6587u7ae0u7684u5168u6587u5167u5bb9
     *
     * @param article u8981u66f4u65b0u7684u6587u7ae0
     */
    @Transactional
    fun updateArticleContent(article: Article) {
        try {
            articleRepository.save(article)
            logger.info("Successfully updated article content: ${article.title}")
        } catch (e: Exception) {
            logger.error("Failed to update article content: ${e.message}", e)
            throw e
        }
    }
}