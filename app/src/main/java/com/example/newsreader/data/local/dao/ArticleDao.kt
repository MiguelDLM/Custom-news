package com.example.newsreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newsreader.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY pubDateMillis DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE category = :category ORDER BY pubDateMillis DESC")
    fun getArticlesByCategory(category: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY pubDateMillis DESC")
    suspend fun searchArticles(query: String): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore duplicates based on link unique index
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteArticlesByFeed(feedId: Int)
    
    @Query("DELETE FROM articles WHERE isSaved = 0")
    suspend fun clearAllUnsaved()
}
