package com.example.newsreader.util

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
}
