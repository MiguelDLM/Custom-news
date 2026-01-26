package com.example.newsreader.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.newsreader.R
import com.example.newsreader.data.local.dao.ArticleDao
import com.example.newsreader.data.local.dao.FeedDao
import com.example.newsreader.data.local.entity.ArticleEntity
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.util.DateUtils
import com.example.newsreader.util.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.newsreader.data.local.dao.SearchHistoryDao
import com.example.newsreader.data.local.entity.SearchHistoryEntity

import com.example.newsreader.data.local.entity.Category
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class SuggestedFeed(
    val url: String,
    val title: String,
    val categories: List<Category>,
    val country: String // "MX", "US", "ES"
)

class NewsRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssParser: RssParser,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    private val searchHistoryDao: SearchHistoryDao // Add this
) {
    // Hidden tabs removed from settings repository; maintain empty set for compatibility
    val hiddenTabs: Flow<Set<String>> = settingsRepository.tabOrder.map { emptySet<String>() }
    val whitelist: Flow<Set<String>> = settingsRepository.keywordWhitelist
    val tabOrder: Flow<List<String>> = settingsRepository.tabOrder
    
    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeeds()
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getRecentSearches()
    val brokenFeeds: Flow<Set<String>> = settingsRepository.brokenFeeds

    suspend fun saveSearch(query: String) {
        searchHistoryDao.insertSearch(SearchHistoryEntity(query = query))
    }

    suspend fun clearHistory() {
        searchHistoryDao.clearHistory()
    }
    
    suspend fun deleteHistoryItem(item: SearchHistoryEntity) {
        searchHistoryDao.deleteSearch(item)
    }

    fun getAllArticles(): Flow<List<ArticleEntity>> {
        return combine(
            articleDao.getAllArticles(),
            settingsRepository.keywordBlacklist
        ) { articles, blacklist ->
            filterArticles(articles, blacklist)
        }
    }
    
    fun getForYouArticles(): Flow<List<ArticleEntity>> {
         return combine(
            articleDao.getAllArticles(),
            settingsRepository.keywordWhitelist,
            settingsRepository.keywordBlacklist
        ) { articles, whitelist, blacklist ->
            val blacklisted = filterArticles(articles, blacklist)
            if (whitelist.isEmpty()) {
                emptyList() // Return empty if no interests defined, UI handles "Add Interests" prompt
            } else {
                blacklisted.filter { article ->
                    whitelist.any { keyword -> article.title.contains(keyword, ignoreCase = true) }
                }
            }
        }
    }

    fun getArticlesByCategory(category: String): Flow<List<ArticleEntity>> {
        val flow = if (category == "All") {
            articleDao.getAllArticles()
        } else {
            // Fix case sensitivity by fetching all and filtering in memory if needed, 
            // or rely on DAO. DAO 'WHERE category = :category' is usually case-sensitive in SQLite unless COLLATE NOCASE.
            // Let's try to match loosely or trust the data input.
            // Since user reported issues, let's fetch all and filter in Kotlin to be safe for now, 
            // OR ensure input category matches DB.
            articleDao.getArticlesByCategory(category) 
        }
        
        return combine(flow, settingsRepository.keywordBlacklist) { articles, blacklist ->
            filterArticles(articles, blacklist)
        }
    }
    
    private fun filterArticles(articles: List<ArticleEntity>, blacklist: Set<String>): List<ArticleEntity> {
        if (blacklist.isEmpty()) return articles
        return articles.filter { article ->
            blacklist.none { keyword -> 
                article.title.contains(keyword, ignoreCase = true) 
            }
        }
    }

    suspend fun getFeedCount(): Int = feedDao.getFeedCount()

    // Returns null on success, or an error message describing the failure
    suspend fun addFeed(url: String, title: String, categories: List<com.example.newsreader.data.local.entity.Category>, country: String = "Global"): String? {
        // Validate feed: ensure it's reachable and a valid RSS/Atom feed and has at least one item
        return try {
            val parsed = withContext(Dispatchers.IO) {
                rssParser.parse(url)
            }
            if (parsed.isEmpty()) {
                // Consider empty feeds invalid for adding
                "Feed parsed but contains no items"
            } else {
                // If parse succeeds and has items, insert
                feedDao.insertFeed(FeedEntity(url = url, title = title, categories = categories, country = country))
                syncFeeds()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Mark as broken for future filtering
            try {
                markFeedAsBroken(url)
            } catch (_: Exception) {}
            // Return a concise error message
            e.message ?: "Unknown error"
        }
    }

    suspend fun markFeedAsBroken(url: String) {
        settingsRepository.addBrokenFeed(url)
    }

    suspend fun deleteFeed(feed: FeedEntity) {
        feedDao.deleteFeed(feed)
    }

    suspend fun checkSync() {
        val lastSync = settingsRepository.lastSync.first()
        val intervalStr = settingsRepository.refreshInterval.first()
        val intervalMillis = when(intervalStr) {
             "15_min" -> 15 * 60 * 1000L
             "30_min" -> 30 * 60 * 1000L
             "1_hour" -> 60 * 60 * 1000L
             "daily" -> 24 * 60 * 60 * 1000L
             else -> 30 * 60 * 1000L
        }
        
        if (System.currentTimeMillis() - lastSync > intervalMillis) {
             syncFeeds()
             settingsRepository.setLastSync(System.currentTimeMillis())
        }
    }

    suspend fun syncFeeds() {
        withContext(Dispatchers.IO) {
            val feeds = feedDao.getAllFeeds().first()
            val whitelist = settingsRepository.keywordWhitelist.first()
            val existingArticles = articleDao.getAllArticles().first().map { it.link }.toSet()

            feeds.forEach { feed ->
                try {
                    val parsedArticles = rssParser.parse(feed.url)
                    val newEntities = parsedArticles.filter { !existingArticles.contains(it.link) }.map { article ->
                        ArticleEntity(
                            feedId = feed.id,
                            title = article.title,
                            link = article.link,
                            description = article.description,
                            imageUrl = article.imageUrl,
                            pubDate = article.pubDate,
                            pubDateMillis = DateUtils.parseDate(article.pubDate),
                            category = feed.categories.firstOrNull()?.name ?: "General"
                        )
                    }
                    
                    if (newEntities.isNotEmpty()) {
                        articleDao.insertArticles(newEntities)

                        // Check for notifications
                        if (whitelist.isNotEmpty()) {
                            newEntities.forEach { article ->
                                if (whitelist.any { article.title.contains(it, ignoreCase = true) }) {
                                    notificationHelper.sendNotification(article.title)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private val notificationHelper = NotificationHelper(context)

    suspend fun hideArticle(articleId: Long) {
        articleDao.hideArticle(articleId)
    }

    suspend fun search(query: String): List<ArticleEntity> {
        return articleDao.searchArticles(query) // DAO usually needs wildcards if not added there
    }

    // Allow updating tab order from UI
    suspend fun setTabOrder(order: List<String>) {
        settingsRepository.setTabOrder(order)
    }
    
    // Updated list of suggested feeds
    // Loaded from assets/suggested_feeds.json to avoid method size limits
    val suggestedFeeds: List<SuggestedFeed> by lazy {
        try {
            val inputStream = context.assets.open("suggested_feeds.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<SuggestedFeed>>() {}.type
            Gson().fromJson(reader, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
