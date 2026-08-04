package com.coder.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 一条记忆 */
data class MemoryItem(
    val id: String,
    val text: String
)

private val Context.memoryDataStore: DataStore<androidx.datastore.preferences.core.Preferences>
    by preferencesDataStore(name = "memory")

/**
 * 智能记忆 — 持久化用户偏好/事实，发送消息时作为上下文给 AI。
 */
class MemoryStore(private val context: Context) {

    companion object {
        private val KEY_MEMORY = stringPreferencesKey("memory_json")
        private val gson = Gson()
    }

    val memories: Flow<List<MemoryItem>> =
        context.memoryDataStore.data.map { prefs ->
            val json = prefs[KEY_MEMORY]
            if (json.isNullOrBlank()) emptyList()
            else runCatching {
                gson.fromJson(json, Array<MemoryItem>::class.java).toList()
            }.getOrDefault(emptyList())
        }

    suspend fun getMemories(): List<MemoryItem> = memories.first()

    suspend fun getMemoryTexts(): List<String> = getMemories().map { it.text }

    suspend fun addMemory(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val list = getMemories().toMutableList()
        list.add(MemoryItem(java.util.UUID.randomUUID().toString(), trimmed))
        save(list)
    }

    suspend fun removeMemory(id: String) {
        save(getMemories().filterNot { it.id == id })
    }

    private suspend fun save(list: List<MemoryItem>) {
        context.memoryDataStore.edit { it[KEY_MEMORY] = gson.toJson(list) }
    }
}
