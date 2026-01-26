package com.example.newsreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val THEME_KEY = stringPreferencesKey("theme") // light, dark, system
        val LANGUAGE_KEY = stringPreferencesKey("language") // en, es, etc.
        val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval") 
        val KEYWORD_WHITELIST_KEY = stringSetPreferencesKey("keyword_whitelist")
        val KEYWORD_BLACKLIST_KEY = stringSetPreferencesKey("keyword_blacklist")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync")
        // Hidden tabs feature removed - users can hide topics via kiosk/subscriptions now
        val TAB_ORDER_KEY = stringPreferencesKey("tab_order")
        
        // AdBlocker
        val ADBLOCK_ENABLED_LISTS_KEY = stringSetPreferencesKey("adblock_enabled_lists")
        val ADBLOCK_CUSTOM_LISTS_KEY = stringSetPreferencesKey("adblock_custom_lists")
        val BROKEN_FEEDS_KEY = stringSetPreferencesKey("broken_feeds")
    }

    val brokenFeeds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BROKEN_FEEDS_KEY] ?: emptySet()
    }

    val lastSync: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_KEY] ?: 0L
    }
    
    // previous hiddenTabs removed

    val tabOrder: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[TAB_ORDER_KEY]
        if (json != null) {
            val list = mutableListOf<String>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } else {
            emptyList()
        }
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
    
    // AdBlocker
    val adBlockEnabledLists: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        // Default: StevenBlack and OISD
        preferences[ADBLOCK_ENABLED_LISTS_KEY] ?: setOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "https://oisd.nl/domainswild"
        )
    }

    val adBlockCustomLists: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[ADBLOCK_CUSTOM_LISTS_KEY] ?: emptySet()
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
    
    // setHiddenTabs removed
    
    suspend fun setTabOrder(order: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[TAB_ORDER_KEY] = JSONArray(order).toString()
        }
    }
    
    suspend fun setAdBlockEnabledLists(lists: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[ADBLOCK_ENABLED_LISTS_KEY] = lists
        }
    }

    suspend fun setAdBlockCustomLists(lists: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[ADBLOCK_CUSTOM_LISTS_KEY] = lists
        }
    }

    suspend fun addBrokenFeed(url: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BROKEN_FEEDS_KEY] ?: emptySet()
            preferences[BROKEN_FEEDS_KEY] = current + url
        }
    }
}
