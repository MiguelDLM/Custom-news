package com.example.newsreader.data.repository

import com.example.newsreader.data.local.dao.FeedDao
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.domain.model.Article
import com.example.newsreader.util.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NewsRepository(
    private val feedDao: FeedDao,
    private val rssParser: RssParser
) {
    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeeds()

    suspend fun addFeed(url: String, title: String) {
        feedDao.insertFeed(FeedEntity(url = url, title = title))
    }

    suspend fun deleteFeed(feed: FeedEntity) {
        feedDao.deleteFeed(feed)
    }

    suspend fun getArticles(url: String): List<Article> {
        return withContext(Dispatchers.IO) {
            rssParser.parse(url)
        }
    }
}
