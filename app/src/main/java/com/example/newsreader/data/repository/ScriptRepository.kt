package com.example.newsreader.data.repository

import com.example.newsreader.data.local.dao.ScriptDao
import com.example.newsreader.data.local.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.text.Regex

// Minimal GreasyFork integration: search and fetch script code. This implementation
// tries the public search HTML and a few known code endpoints. It's best-effort and
// resilient to missing fields.
data class GreasyForkScriptSummary(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val authors: String = "",
    val version: String = "",
    val updatedAt: String = ""
)

class ScriptRepository(private val scriptDao: ScriptDao) {
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    suspend fun getScriptsForUrl(url: String): List<ScriptEntity> {
        return scriptDao.getScriptsForUrl(url)
    }

    suspend fun addScript(domain: String, code: String) {
        scriptDao.insertScript(ScriptEntity(domainMatch = domain, jsCode = code))
    }

    suspend fun addOrUpdateScript(domain: String, code: String) {
        val existing = scriptDao.getScriptByDomain(domain)
        if (existing != null) {
            scriptDao.insertScript(existing.copy(jsCode = code))
        } else {
            scriptDao.insertScript(ScriptEntity(domainMatch = domain, jsCode = code))
        }
    }

    suspend fun deleteScript(script: ScriptEntity) {
        scriptDao.deleteScript(script)
    }

    // Network client for GreasyFork interactions
    private val http = OkHttpClient()

    // Search GreasyFork for a script matching a domain. Returns possible scripts.
    fun searchGreasyForkForDomain(domain: String): List<GreasyForkScriptSummary> {
        // Prefer exact host and root domain lookups via the by-site endpoint.
        // Only fall back to generic search when site-specific results are missing,
        // and then apply stricter filtering to avoid unrelated matches for common words.
        val hostsToTry = mutableListOf<String>()
        var host = domain.trim().lowercase()
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        hostsToTry.add(host)

        // Also try root (remove first subdomain if present): e.g. cultura.elpais.com -> elpais.com
        val dotIndex = host.indexOf('.')
        if (dotIndex != -1) {
            val root = host.substring(dotIndex + 1)
            if (root.isNotBlank() && root != host) hostsToTry.add(root)
        }

        val allResults = mutableListOf<GreasyForkScriptSummary>()

        try {
            val reqBuilder = Request.Builder().header("User-Agent", "NewsReaderScriptable/1.0")

            // First try the site-specific endpoint for each candidate host.
            hostsToTry.forEach { h ->
                val q = URLEncoder.encode(h, "UTF-8")
                val bySiteUrl = "https://greasyfork.org/scripts/by-site/$q.json"
                try {
                    val req = reqBuilder.url(bySiteUrl).build()
                    http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val parsed = parseGreasyForkJson(body)
                                if (parsed.isNotEmpty()) {
                                    allResults.addAll(parsed)
                                }
                            }
                        }
                    }
                } catch (e: Exception) { /* ignore site lookup errors */ }
            }

            // If we found site-specific scripts, return those (they are the most relevant)
            if (allResults.isNotEmpty()) return allResults.distinctBy { it.id }

            // Otherwise, fall back to generic search but with stricter filtering.
            // Only query once per the most specific host (avoid flooding GreasyFork).
            val primary = URLEncoder.encode(hostsToTry.first(), "UTF-8")
            val searchUrl = "https://greasyfork.org/scripts.json?q=$primary"
            try {
                val req = reqBuilder.url(searchUrl).build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val genericResults = parseGreasyForkJson(body)
                            // Filter: script must mention the host/root in name, description or code url
                            val filtered = genericResults.filter { gs ->
                                val lowerName = gs.name.lowercase()
                                val lowerDesc = gs.description.lowercase()
                                val lowerUrl = gs.url.lowercase()
                                hostsToTry.any { h ->
                                    lowerName.contains(h) || lowerDesc.contains(h) || lowerUrl.contains(h)
                                }
                            }
                            allResults.addAll(filtered)
                        }
                    }
                }
            } catch (e: Exception) { /* ignore */ }

            return allResults.distinctBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseGreasyForkJson(json: String): List<GreasyForkScriptSummary> {
        // Simple manual JSON parser for [{"id":123,"name":"foo"}]
        // We use Gson since we added the dependency.
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type)
            
            return list.mapNotNull { item ->
                val id = (item["id"] as? Double)?.toLong()?.toString() ?: return@mapNotNull null
                val name = item["name"] as? String ?: "Unknown"
                val description = item["description"] as? String ?: ""
                val version = item["version"] as? String ?: ""
                val createdAt = item["created_at"] as? String ?: ""
                val codeUrl = item["code_url"] as? String 
                
                // Extract author
                val users = item["users"] as? List<Map<String, Any>>
                val authorName = users?.firstOrNull()?.get("name") as? String ?: "Unknown"

                // Construct script page URL or code URL
                // Note: The API returns 'code_url' which is the direct link to the .user.js
                // We prefer fetching code via this URL if available, otherwise construct page url
                val urlToFetch = codeUrl ?: (item["url"] as? String) ?: "https://greasyfork.org/scripts/$id/code/script.user.js"
                
                GreasyForkScriptSummary(
                    id = id, 
                    name = name, 
                    url = urlToFetch,
                    description = description,
                    authors = authorName,
                    version = version,
                    updatedAt = createdAt.take(10) // Simple date substring
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    // Fetch raw JS from a GreasyFork script page.
    fun fetchGreasyForkScriptCode(scriptSummary: GreasyForkScriptSummary): String? {
        try {
            val req = Request.Builder().url(scriptSummary.url).header("User-Agent", "NewsReaderScriptable/1.0").get().build()
            http.newCall(req).execute().use { resp ->
                return resp.body?.string()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // DAO helper
    suspend fun getScriptForDomain(domain: String): ScriptEntity? {
        return scriptDao.getScriptByDomain(domain)
    }
}
