package com.example.newsreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["feedId"]), Index(value = ["link"], unique = true)]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Int,
    val title: String,
    val link: String,
    val description: String?,
    val imageUrl: String?,
    val pubDate: String?,
    val pubDateMillis: Long = 0, // For sorting
    val isSaved: Boolean = false,
    val isHidden: Boolean = false, // New field for hidden articles
    val category: String = "General" // Denormalized for easier querying
)
