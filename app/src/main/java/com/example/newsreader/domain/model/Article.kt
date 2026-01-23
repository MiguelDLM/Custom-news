package com.example.newsreader.domain.model

data class Article(
    val title: String,
    val link: String,
    val description: String?,
    val pubDate: String?,
    val imageUrl: String? = null
)
