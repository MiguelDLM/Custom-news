package com.example.newsreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.newsreader.data.local.dao.FeedDao
import com.example.newsreader.data.local.dao.ScriptDao
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.data.local.entity.ScriptEntity

@Database(entities = [FeedEntity::class, ScriptEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun scriptDao(): ScriptDao
}
