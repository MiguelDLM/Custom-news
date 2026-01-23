package com.example.newsreader

import android.app.Application
import androidx.room.Room
import com.example.newsreader.data.local.AppDatabase
import com.example.newsreader.data.repository.NewsRepository
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.data.repository.SettingsRepository
import com.example.newsreader.util.RssParser

class NewsApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var newsRepository: NewsRepository
    lateinit var scriptRepository: ScriptRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "news-database"
        )
        .fallbackToDestructiveMigration()
        .build()

        val rssParser = RssParser()
        settingsRepository = SettingsRepository(applicationContext)
        newsRepository = NewsRepository(
            database.feedDao(), 
            database.articleDao(), 
            rssParser,
            settingsRepository,
            applicationContext
        )
        scriptRepository = ScriptRepository(database.scriptDao())
    }
}
