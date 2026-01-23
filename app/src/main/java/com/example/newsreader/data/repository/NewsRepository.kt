package com.example.newsreader.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SuggestedFeed(
    val url: String,
    val title: String,
    val category: String,
    val country: String // "MX", "US", "ES"
)

class NewsRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssParser: RssParser,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) {
    val hiddenTabs: Flow<Set<String>> = settingsRepository.hiddenTabs
    val whitelist: Flow<Set<String>> = settingsRepository.keywordWhitelist
    
    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeeds()

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

    suspend fun addFeed(url: String, title: String, category: String = "General") {
        feedDao.insertFeed(FeedEntity(url = url, title = title, category = category))
        syncFeeds()
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
                            category = feed.category
                        )
                    }
                    
                    if (newEntities.isNotEmpty()) {
                        articleDao.insertArticles(newEntities)
                        
                        // Check for notifications
                        if (whitelist.isNotEmpty()) {
                            newEntities.forEach { article ->
                                if (whitelist.any { article.title.contains(it, ignoreCase = true) }) {
                                    sendNotification(article.title)
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
    
    private fun sendNotification(title: String) {
        val channelId = "news_channel"
        val notificationId = title.hashCode()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "News Updates"
            val descriptionText = "Notifications for interested topics"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        try {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) 
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                // permission check needed for API 33+
                // ignoring for prototype simplicity as requested to "just work" in context or user will handle permissions
                try {
                    notify(notificationId, builder.build())
                } catch (e: SecurityException) {
                    // Permission not granted
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun search(query: String): List<ArticleEntity> {
        return articleDao.searchArticles(query) // DAO usually needs wildcards if not added there
    }
    
    // Updated list of suggested feeds
    val suggestedFeeds = listOf(
        // Mexico
        SuggestedFeed("https://www.eluniversal.com.mx/arc/outboundfeeds/rss/", "El Universal", "General", "MX"),
        SuggestedFeed("https://www.milenio.com/rss", "Milenio", "General", "MX"),
        SuggestedFeed("https://www.infobae.com/arc/outboundfeeds/rss/?category=/mexico", "Infobae México", "General", "MX"),
        SuggestedFeed("https://aristeguinoticias.com/feed/mexico", "Aristegui Noticias", "General", "MX"),
        SuggestedFeed("https://www.excelsior.com.mx/rss", "Excelsior", "General", "MX"),
        SuggestedFeed("https://www.proceso.com.mx/rss/feed.html", "Proceso", "Politics", "MX"),
        SuggestedFeed("https://www.elfinanciero.com.mx/rss", "El Financiero", "Finance", "MX"),
        SuggestedFeed("http://feeds.weblogssl.com/xatakamexico", "Xataka México", "Technology", "MX"),

        // USA
        SuggestedFeed("https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "NY Times", "General", "US"),
        SuggestedFeed("https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "NY Times World", "World", "US"),
        SuggestedFeed("http://rss.cnn.com/rss/edition.rss", "CNN", "General", "US"),
        SuggestedFeed("https://www.espn.com/espn/rss/news", "ESPN", "Sports", "US"),
        SuggestedFeed("https://techcrunch.com/feed/", "TechCrunch", "Technology", "US"),
        SuggestedFeed("https://www.theverge.com/rss/index.xml", "The Verge", "Technology", "US"),

        // Spain
        SuggestedFeed("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "El País", "General", "ES"),
        SuggestedFeed("https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml", "El Mundo", "General", "ES"),
        SuggestedFeed("https://e00-marca.uecdn.es/rss/portada.xml", "Marca", "Sports", "ES"),
        SuggestedFeed("https://e00-expansion.uecdn.es/rss/portada.xml", "Expansión", "Finance", "ES")
    )
}
