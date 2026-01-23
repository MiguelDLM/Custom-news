package com.example.newsreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newsreader.data.local.entity.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    // Finding scripts that might match the URL. 
    // We check if the domainMatch string is contained within the current URL.
    // NOTE: In SQLite, INSTR returns > 0 if found.
    // Simple matching logic: url contains domainMatch. 
    // Ideally user stores "example.com" and we match if url has "example.com".
    @Query("SELECT * FROM scripts WHERE :url LIKE '%' || domainMatch || '%' AND isEnabled = 1")
    suspend fun getScriptsForUrl(url: String): List<ScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity)

    @Delete
    suspend fun deleteScript(script: ScriptEntity)
}
