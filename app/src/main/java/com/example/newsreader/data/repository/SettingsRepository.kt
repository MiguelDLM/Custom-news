package com.example.newsreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val THEME_KEY = stringPreferencesKey("theme") // light, dark, system
        val LANGUAGE_KEY = stringPreferencesKey("language") // en, es, etc.
        val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval") 
        val KEYWORD_WHITELIST_KEY = stringSetPreferencesKey("keyword_whitelist")
        val KEYWORD_BLACKLIST_KEY = stringSetPreferencesKey("keyword_blacklist")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync")
        val HIDDEN_TABS_KEY = stringSetPreferencesKey("hidden_tabs")
    }

    val lastSync: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_KEY] ?: 0L
    }
    
    val hiddenTabs: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[HIDDEN_TABS_KEY] ?: emptySet()
    }

    val theme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "system"
    }

    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    val refreshInterval: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_INTERVAL_KEY] ?: "30_min" // Default 30 min
    }

    val keywordWhitelist: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEYWORD_WHITELIST_KEY] ?: emptySet()
    }

    val keywordBlacklist: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEYWORD_BLACKLIST_KEY] ?: emptySet()
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun setRefreshInterval(interval: String) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_KEY] = interval
        }
    }

    suspend fun setKeywordWhitelist(whitelist: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEYWORD_WHITELIST_KEY] = whitelist
        }
    }

    suspend fun setKeywordBlacklist(blacklist: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEYWORD_BLACKLIST_KEY] = blacklist
        }
    }

    suspend fun setLastSync(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_KEY] = time
        }
    }
    
    suspend fun setHiddenTabs(tabs: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[HIDDEN_TABS_KEY] = tabs
        }
    }
}
