package com.example.newsreader.data.repository

import com.example.newsreader.data.local.dao.ArticleDao
import com.example.newsreader.data.local.dao.FeedDao
import com.example.newsreader.data.local.entity.ArticleEntity
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.util.DateUtils
import com.example.newsreader.util.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NewsRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssParser: RssParser
) {
    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeeds()

    fun getAllArticles(): Flow<List<ArticleEntity>> = articleDao.getAllArticles()

    fun getArticlesByCategory(category: String): Flow<List<ArticleEntity>> {
        return if (category == "All") {
            articleDao.getAllArticles()
        } else {
            articleDao.getArticlesByCategory(category)
        }
    }

    suspend fun getFeedCount(): Int = feedDao.getFeedCount()

    suspend fun addFeed(url: String, title: String, category: String = "General") {
        feedDao.insertFeed(FeedEntity(url = url, title = title, category = category))
        syncFeeds() // Refresh immediately
    }

    suspend fun deleteFeed(feed: FeedEntity) {
        feedDao.deleteFeed(feed)
        // Articles will be deleted by Cascade
    }

    suspend fun syncFeeds() {
        withContext(Dispatchers.IO) {
            val feeds = feedDao.getAllFeeds().first()
            feeds.forEach { feed ->
                try {
                    val parsedArticles = rssParser.parse(feed.url)
                    val entities = parsedArticles.map { article ->
                        ArticleEntity(
                            feedId = feed.id,
                            title = article.title,
                            link = article.link,
                            description = article.description,
                            imageUrl = article.imageUrl,
                            pubDate = article.pubDate,
                            pubDateMillis = DateUtils.parseDate(article.pubDate),
                            category = feed.category
                        )
                    }
                    if (entities.isNotEmpty()) {
                        articleDao.insertArticles(entities)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun search(query: String): List<ArticleEntity> {
        return articleDao.searchArticles(query)
    }

    // Default feeds for suggestions
    val suggestedFeeds = listOf(
        Triple("https://www.xataka.com/feed.xml", "Xataka", "Technology"),
        Triple("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "El País", "News"),
        Triple("http://rss.cnn.com/rss/edition.rss", "CNN", "International"),
        Triple("https://www.theverge.com/rss/index.xml", "The Verge", "Technology"),
        Triple("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", "International"),
        Triple("https://www.espn.com/espn/rss/news", "ESPN", "Sports")
    )
}
