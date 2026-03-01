package com.museovirtualnacional.strogoff

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.museovirtualnacional.strogoff.data.local.AppDatabase
import com.museovirtualnacional.strogoff.data.repository.NewsRepository
import com.museovirtualnacional.strogoff.data.repository.ScriptRepository
import com.museovirtualnacional.strogoff.data.repository.SettingsRepository
import com.museovirtualnacional.strogoff.util.RssParser

class NewsApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var newsRepository: NewsRepository
    lateinit var scriptRepository: ScriptRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration: List<Category> to List<String> both compile to TEXT via TypeConverters
            }
        }

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "news-database"
        )
        .addMigrations(MIGRATION_5_6)
        .fallbackToDestructiveMigration()
        .build()

        val rssParser = RssParser()
        settingsRepository = SettingsRepository(applicationContext)
        newsRepository = NewsRepository(
            database.feedDao(), 
            database.articleDao(), 
            rssParser,
            settingsRepository,
            applicationContext,
            database.searchHistoryDao()
        )
        scriptRepository = ScriptRepository(database.scriptDao())
    }
}
