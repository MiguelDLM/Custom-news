package com.example.newsreader.data.local.entity

enum class Category {
    POLITICS,
    TECHNOLOGY,
    SPORTS,
    FINANCE,
    WORLD,
    GENERAL,
    INVESTIGATIVE,
    SCIENCE,
    ENTERTAINMENT,
    HEALTH,
    BUSINESS,
    UNKNOWN;

    companion object {
        fun fromString(value: String): Category {
            val normalized = value.trim().uppercase()
            return when {
                normalized.contains("TECH") -> TECHNOLOGY
                normalized.contains("POLIT") -> POLITICS
                normalized.contains("SPORT") -> SPORTS
                normalized.contains("FINAN") || normalized.contains("ECON") -> FINANCE
                normalized.contains("WORLD") -> WORLD
                normalized.contains("CIENCIA") || normalized.contains("SCIENCE") -> SCIENCE
                normalized.contains("SALUD") || normalized.contains("HEALTH") -> HEALTH
                normalized == "GENERAL" -> GENERAL
                else -> try {
                    valueOf(normalized)
                } catch (e: Exception) {
                    UNKNOWN
                }
            }
        }
    }
}

enum class EditorialLine {
    MAINSTREAM,
    INDEPENDENT,
    STATE_OWNED,
    UNKNOWN
}
