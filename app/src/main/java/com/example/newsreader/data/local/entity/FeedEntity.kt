package com.example.newsreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val description: String? = null,
    val categories: List<Category> = listOf(Category.GENERAL),
    val country: String = "Global",
    val editorialLine: EditorialLine = EditorialLine.UNKNOWN
)
