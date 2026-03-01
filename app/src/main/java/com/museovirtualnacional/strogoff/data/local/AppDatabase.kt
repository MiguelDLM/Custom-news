package com.museovirtualnacional.strogoff.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.museovirtualnacional.strogoff.data.local.converters.Converters
import com.museovirtualnacional.strogoff.data.local.dao.ArticleDao
import com.museovirtualnacional.strogoff.data.local.dao.FeedDao
import com.museovirtualnacional.strogoff.data.local.dao.ScriptDao
import com.museovirtualnacional.strogoff.data.local.dao.SearchHistoryDao
import com.museovirtualnacional.strogoff.data.local.entity.ArticleEntity
import com.museovirtualnacional.strogoff.data.local.entity.FeedEntity
import com.museovirtualnacional.strogoff.data.local.entity.ScriptEntity
import com.museovirtualnacional.strogoff.data.local.entity.SearchHistoryEntity

@Database(entities = [FeedEntity::class, ScriptEntity::class, ArticleEntity::class, SearchHistoryEntity::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun scriptDao(): ScriptDao
    abstract fun articleDao(): ArticleDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
