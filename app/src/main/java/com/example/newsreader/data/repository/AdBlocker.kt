package com.example.newsreader.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader

object AdBlocker {
    private val blockedDomains = HashSet<String>()
    private var isInitialized = false
    private val client = OkHttpClient()

    // Predefined sources map (URL -> Name/Key)
    val PREDEFINED_LISTS = mapOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts" to "StevenBlack",
        "https://oisd.nl/domainswild" to "OISD",
        "https://adaway.org/hosts.txt" to "AdAway",
        "https://easylist.to/easylist/easylist.txt" to "EasyList", // Note: EasyList is not Hosts format, usually. We need separate parser if we support EasyList format proper.
        "https://easylist.to/easylist/easyprivacy.txt" to "EasyPrivacy"
    )
    
    // For this implementation, we will stick to HOSTS format mostly as it's simpler for domain blocking.
    // EasyList standard format is complex (CSS selectors etc). 
    // We will attempt to parse domains from whatever is fed if it looks like a host list.

    suspend fun reload(enabledLists: Set<String>, customLists: Set<String>) {
        withContext(Dispatchers.IO) {
            blockedDomains.clear()
            blockedDomains.addAll(COMMON_ADS)
            
            val allLists = enabledLists + customLists
            allLists.forEach { url ->
                fetchAndParseHosts(url)
            }
            isInitialized = true
        }
    }

    suspend fun init(enabledLists: Set<String>, customLists: Set<String>) {
        if (isInitialized) return
        reload(enabledLists, customLists)
    }

    private fun fetchAndParseHosts(url: String) {
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: return
                BufferedReader(StringReader(body)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        // Basic HOSTS parsing
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                            // Split by whitespace
                            val parts = trimmed.split("\\s+".toRegex())
                            
                            // Check for 0.0.0.0 domain.com (Hosts format)
                            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                                blockedDomains.add(parts[1])
                            } 
                            // Check for AdBlock Plus / EasyList domain-only format e.g. ||example.com^
                            else if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
                                val domain = trimmed.substring(2, trimmed.length - 1)
                                blockedDomains.add(domain)
                            }
                            // Check for simple domain list (raw text file of domains)
                            else if (parts.size == 1 && !trimmed.contains("/") && !trimmed.contains(":")) {
                                blockedDomains.add(trimmed)
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore fetch errors
        }
    }

    fun isBlocked(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host ?: return false
            if (blockedDomains.contains(host)) return true
            
            // Subdomain check (naive)
            var current = host
            while (current.contains(".")) {
                if (blockedDomains.contains(current)) return true
                val nextDot = current.indexOf(".")
                if (nextDot == -1 || nextDot == current.lastIndexOf(".")) break
                current = current.substring(nextDot + 1)
            }
            
            return false
        } catch (e: Exception) {
            false
        }
    }

    private val COMMON_ADS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "facebook.com", "analytics.twitter.com",
        "scorecardresearch.com", "zedo.com", "ads.twitter.com",
        "teads.tv", "outbrain.com", "taboola.com", "adservice.google.com"
    )
}
