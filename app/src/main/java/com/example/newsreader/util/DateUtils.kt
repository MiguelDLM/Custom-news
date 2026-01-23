package com.example.newsreader.util

import android.content.Context
import com.example.newsreader.R
import java.text.SimpleDateFormat
import java.util.Locale

object DateUtils {
    // Common RSS date formats
    private val formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    )

    fun parseDate(dateString: String?): Long {
        if (dateString == null) return System.currentTimeMillis()
        for (format in formats) {
            try {
                return format.parse(dateString)?.time ?: continue
            } catch (e: Exception) {
                // Try next format
            }
        }
        return System.currentTimeMillis() // Fallback
    }

    fun getTimeAgo(timestamp: Long, context: Context): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now" // Fallback string, usually localized better
            minutes < 60 -> "$minutes min"
            hours < 24 -> "$hours h"
            days < 7 -> "$days d"
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                sdf.format(timestamp)
            }
        }
    }
}
