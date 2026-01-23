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
    val url: String
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
        try {
            val q = URLEncoder.encode(domain, "UTF-8")
            val url = "https://greasyfork.org/en/scripts/search?q=$q"
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                // Very small HTML parse: look for links like /en/scripts/12345-script-name
                val regex = Regex("/en/scripts/(\\d+)[^\"]*")
                val ids = regex.findAll(body).map { it.groupValues[1] }.distinct().toList()
                return ids.map { id ->
                    val scriptUrl = "https://greasyfork.org/en/scripts/$id"
                    GreasyForkScriptSummary(id = id, name = "script-$id", url = scriptUrl)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // Fetch raw JS from a GreasyFork script page. GreasyFork serves the code inside
    // a <script id="user-script"> ... </script> sometimes; we fallback to searching
    // for a <pre> or code block. This is fragile but works for common pages.
    fun fetchGreasyForkScriptCode(scriptSummary: GreasyForkScriptSummary): String? {
        try {
            val req = Request.Builder().url(scriptSummary.url).get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null
                // Attempt to find <script id="source"> ... </script> or similar
                val scriptRegex = Regex("<script[^>]*id=\\\"?user-script\\\"?[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
                val m = scriptRegex.find(body)
                if (m != null) return m.groupValues[1]

                // Fallback: look for <pre class="source"> ...</pre>
                val preRegex = Regex("<pre[^>]*>([\\s\\S]*?)</pre>", RegexOption.IGNORE_CASE)
                val m2 = preRegex.find(body)
                if (m2 != null) return m2.groupValues[1]

                return null
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
