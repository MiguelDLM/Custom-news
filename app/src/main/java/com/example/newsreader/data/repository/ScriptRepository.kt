package com.example.newsreader.data.repository

import com.example.newsreader.data.local.dao.ScriptDao
import com.example.newsreader.data.local.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val scriptDao: ScriptDao) {
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    suspend fun getScriptsForUrl(url: String): List<ScriptEntity> {
        return scriptDao.getScriptsForUrl(url)
    }

    suspend fun addScript(domain: String, code: String) {
        scriptDao.insertScript(ScriptEntity(domainMatch = domain, jsCode = code))
    }

    suspend fun deleteScript(script: ScriptEntity) {
        scriptDao.deleteScript(script)
    }
}
