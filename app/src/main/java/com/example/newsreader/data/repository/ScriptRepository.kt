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
        // Strip subdomains aggressively to find results (e.g. cultura.elpais.com -> elpais.com)
        val searchTerms = mutableSetOf<String>()
        var current = domain
        searchTerms.add(current)
        while (current.contains(".") && current.indexOf(".") != current.lastIndexOf(".")) {
            current = current.substring(current.indexOf(".") + 1)
            searchTerms.add(current)
        }
        
        // Also try specific standard variations if not covered
        if (domain.startsWith("www.")) {
            searchTerms.add(domain.removePrefix("www."))
        }

        val allResults = mutableListOf<GreasyForkScriptSummary>()
        
        try {
            // Add User-Agent to avoid being blocked
            val reqBuilder = Request.Builder().header("User-Agent", "NewsReaderScriptable/1.0")

            searchTerms.forEach { term ->
                val q = URLEncoder.encode(term, "UTF-8")
                // Try specific site endpoint first (usually cleaner)
                val bySiteUrl = "https://greasyfork.org/scripts/by-site/$q.json" // Removed /en/
                
                try {
                    val req = reqBuilder.url(bySiteUrl).build()
                    http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                allResults.addAll(parseGreasyForkJson(body))
                            }
                        }
                    }
                } catch (e: Exception) { /* ignore */ }
                
                // Also generic search for more obscure matches
                val searchUrl = "https://greasyfork.org/scripts.json?q=$q"
                try {
                    val req = reqBuilder.url(searchUrl).build()
                    http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                allResults.addAll(parseGreasyForkJson(body))
                            }
                        }
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            
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
                val urlToFetch = codeUrl ?: "https://greasyfork.org/scripts/$id/code/script.user.js"
                
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
