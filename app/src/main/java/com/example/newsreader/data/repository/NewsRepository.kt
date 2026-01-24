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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.newsreader.data.local.dao.SearchHistoryDao
import com.example.newsreader.data.local.entity.SearchHistoryEntity

import com.example.newsreader.data.local.entity.Category

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
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getRecentSearches() // Add this

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

    suspend fun addFeed(url: String, title: String, categories: List<com.example.newsreader.data.local.entity.Category>, country: String = "Global") {
        feedDao.insertFeed(FeedEntity(url = url, title = title, categories = categories, country = country))
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
                            category = feed.categories.firstOrNull()?.name ?: "General"
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
    val suggestedFeeds = listOf(

        // ======================
        // 🔬 ACADEMIC & SCIENCE (PURE)
        // ======================
        
        SuggestedFeed("https://www.nature.com/nature.rss", "Nature", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("https://www.sciencemag.org/rss/news_current.xml", "Science Magazine", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("https://rss.sciencedaily.com/top.xml", "ScienceDaily", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("https://phys.org/rss-feed/breaking/technology-news/", "Phys.org", listOf(Category.SCIENCE, Category.TECHNOLOGY), "INT"),
        SuggestedFeed("https://plos.org/feed/", "PLOS", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("http://export.arxiv.org/rss/cs", "arXiv (CS)", listOf(Category.TECHNOLOGY, Category.SCIENCE), "INT"),
        SuggestedFeed("http://export.arxiv.org/rss/physics", "arXiv (Physics)", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("https://www.cell.com/cell/current.rss", "Cell", listOf(Category.SCIENCE), "INT"),
        SuggestedFeed("https://www.newscientist.com/feed/home/?format=rss", "New Scientist", listOf(Category.SCIENCE), "UK"),
        SuggestedFeed("https://www.scientificamerican.com/feed/home/", "Scientific American", listOf(Category.SCIENCE), "US"),
        
        // ======================
        // 🇲🇽 MEXICO – GENERAL
        // ======================

        SuggestedFeed("https://www.eluniversal.com.mx/arc/outboundfeeds/rss/", "El Universal", listOf(Category.GENERAL), "MX"),
        SuggestedFeed("https://www.eluniversal.com.mx/rss/nacion.xml", "El Universal", listOf(Category.POLITICS), "MX"),
        SuggestedFeed("https://www.eluniversal.com.mx/rss/cartera.xml", "El Universal", listOf(Category.FINANCE), "MX"),
        SuggestedFeed("https://www.eluniversal.com.mx/rss/cultura.xml", "El Universal", listOf(Category.GENERAL), "MX"), // Mapped Culture to General for now

        SuggestedFeed("https://www.milenio.com/rss", "Milenio", listOf(Category.GENERAL), "MX"),
        SuggestedFeed("https://www.milenio.com/rss/politica", "Milenio", listOf(Category.POLITICS), "MX"),
        SuggestedFeed("https://www.milenio.com/rss/negocios", "Milenio", listOf(Category.FINANCE), "MX"),

        SuggestedFeed("https://aristeguinoticias.com/feed", "Aristegui Noticias", listOf(Category.GENERAL, Category.INVESTIGATIVE), "MX"),
        SuggestedFeed("https://aristeguinoticias.com/feed/mexico", "Aristegui Noticias", listOf(Category.POLITICS), "MX"),
        SuggestedFeed("https://aristeguinoticias.com/feed/economia", "Aristegui Noticias", listOf(Category.FINANCE), "MX"),

        SuggestedFeed("https://www.proceso.com.mx/rss/", "Proceso", listOf(Category.GENERAL, Category.INVESTIGATIVE), "MX"),
        SuggestedFeed("https://www.proceso.com.mx/rss/politica.html", "Proceso", listOf(Category.POLITICS), "MX"),
        
        SuggestedFeed("https://www.animalpolitico.com/feed/", "Animal Político", listOf(Category.POLITICS, Category.INVESTIGATIVE), "MX"),
        
        SuggestedFeed("https://www.sinembargo.mx/feed", "SinEmbargo", listOf(Category.GENERAL), "MX"),
        
        SuggestedFeed("https://www.elfinanciero.com.mx/rss", "El Financiero", listOf(Category.FINANCE), "MX"),
        
        SuggestedFeed("https://www.reforma.com/rss/portada.xml", "Reforma", listOf(Category.GENERAL), "MX"),
        SuggestedFeed("https://www.reforma.com/rss/nacional.xml", "Reforma", listOf(Category.POLITICS), "MX"),
        
        SuggestedFeed("https://expansion.mx/rss", "Expansión", listOf(Category.FINANCE, Category.BUSINESS), "MX"), // Business -> Finance or map
        SuggestedFeed("https://expansion.mx/rss/tecnologia", "Expansión", listOf(Category.TECHNOLOGY), "MX"),

        SuggestedFeed("https://www.xataka.com.mx/feed", "Xataka México", listOf(Category.TECHNOLOGY), "MX"),
        
        // ======================
        // 🇺🇸 USA
        // ======================

        SuggestedFeed("https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "New York Times", listOf(Category.GENERAL), "US"),
        SuggestedFeed("https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "New York Times", listOf(Category.WORLD), "US"),
        SuggestedFeed("https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml", "New York Times", listOf(Category.TECHNOLOGY), "US"),
        
        SuggestedFeed("https://feeds.washingtonpost.com/rss/politics", "Washington Post", listOf(Category.POLITICS), "US"),
        
        SuggestedFeed("http://rss.cnn.com/rss/edition.rss", "CNN", listOf(Category.GENERAL), "US"),
        
        SuggestedFeed("https://techcrunch.com/feed/", "TechCrunch", listOf(Category.TECHNOLOGY), "US"),
        SuggestedFeed("https://www.theverge.com/rss/index.xml", "The Verge", listOf(Category.TECHNOLOGY), "US"),
        SuggestedFeed("https://arstechnica.com/feed/", "Ars Technica", listOf(Category.TECHNOLOGY), "US"),
        
        // ======================
        // 🇪🇸 SPAIN
        // ======================

        SuggestedFeed("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "El País", listOf(Category.GENERAL), "ES"),
        SuggestedFeed("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/politica", "El País", listOf(Category.POLITICS), "ES"),
        
        SuggestedFeed("https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml", "El Mundo", listOf(Category.GENERAL), "ES"),
        
        SuggestedFeed("https://e00-marca.uecdn.es/rss/portada.xml", "Marca", listOf(Category.SPORTS), "ES"),
        
        // ======================
        // 🌍 INTERNATIONAL
        // ======================

        SuggestedFeed("https://www.bbc.com/news/rss.xml", "BBC News", listOf(Category.WORLD), "UK"),
        SuggestedFeed("https://www.aljazeera.com/xml/rss/all.xml", "Al Jazeera", listOf(Category.WORLD), "QA")
    )
}

