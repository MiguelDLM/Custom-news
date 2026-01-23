package com.example.newsreader

import android.app.Application
import androidx.room.Room
import com.example.newsreader.data.local.AppDatabase
import com.example.newsreader.data.repository.NewsRepository
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.util.RssParser

class NewsApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var newsRepository: NewsRepository
    lateinit var scriptRepository: ScriptRepository

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "news-database"
        ).build()

        val rssParser = RssParser()
        newsRepository = NewsRepository(database.feedDao(), rssParser)
        scriptRepository = ScriptRepository(database.scriptDao())
    }
}
