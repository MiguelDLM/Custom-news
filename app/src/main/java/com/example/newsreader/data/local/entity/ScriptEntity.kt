package com.example.newsreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val domainMatch: String, // e.g., "nytimes.com"
    val jsCode: String,      // e.g., "document.body.style.background='black';"
    val isEnabled: Boolean = true
)
