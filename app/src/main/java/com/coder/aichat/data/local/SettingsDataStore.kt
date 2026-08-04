package com.coder.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_THEME = stringPreferencesKey("theme") // "system" | "light" | "dark"
        private val KEY_CUSTOM_MODELS = stringPreferencesKey("custom_models")
        private val KEY_SEARCH_ENABLED = booleanPreferencesKey("search_enabled")
        private val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_SEARCH_API_KEY = stringPreferencesKey("search_api_key")
        private val KEY_UPDATE_URL = stringPreferencesKey("update_url")
        private val KEY_UPDATE_AUTO = booleanPreferencesKey("update_auto_check")

        fun providerApiKeyKey(providerId: String) = stringPreferencesKey("api_key_$providerId")
        fun providerBaseUrlKey(providerId: String) = stringPreferencesKey("base_url_$providerId")
    }

    val selectedProvider: Flow<String> =
        context.dataStore.data.map { it[KEY_SELECTED_PROVIDER] ?: "openai" }

    val selectedModel: Flow<String> =
        context.dataStore.data.map { it[KEY_SELECTED_MODEL] ?: "" }

    val systemPrompt: Flow<String> =
        context.dataStore.data.map { it[KEY_SYSTEM_PROMPT] ?: "" }

    val theme: Flow<String> =
        context.dataStore.data.map { it[KEY_THEME] ?: "system" }

    suspend fun getSelectedProvider(): String = selectedProvider.first()
    suspend fun getSelectedModel(): String = selectedModel.first()
    suspend fun getSystemPrompt(): String = systemPrompt.first()

    suspend fun setSelectedProvider(id: String) {
        context.dataStore.edit { it[KEY_SELECTED_PROVIDER] = id }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setApiKey(providerId: String, key: String) {
        context.dataStore.edit { it[providerApiKeyKey(providerId)] = key }
    }

    suspend fun getApiKey(providerId: String): String =
        context.dataStore.data.map { it[providerApiKeyKey(providerId)] ?: "" }.first()

    suspend fun setBaseUrl(providerId: String, url: String) {
        context.dataStore.edit { it[providerBaseUrlKey(providerId)] = url }
    }

    suspend fun getBaseUrl(providerId: String): String =
        context.dataStore.data.map { it[providerBaseUrlKey(providerId)] ?: "" }.first()

    /** 中转站自定义模型列表（逗号分隔） */
    suspend fun setCustomModels(models: List<String>) {
        context.dataStore.edit { it[KEY_CUSTOM_MODELS] = models.joinToString(",") }
    }

    suspend fun getCustomModels(): List<String> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_CUSTOM_MODELS]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        }.first()

    // ── 联网搜索配置 ──
    val searchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SEARCH_ENABLED] ?: false }

    suspend fun getSearchEnabled(): Boolean = searchEnabled.first()

    suspend fun setSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SEARCH_ENABLED] = enabled }
    }

    suspend fun getSearchEngine(): String =
        context.dataStore.data.map { it[KEY_SEARCH_ENGINE] ?: "tavily" }.first()

    suspend fun setSearchEngine(engine: String) {
        context.dataStore.edit { it[KEY_SEARCH_ENGINE] = engine }
    }

    suspend fun getSearchApiKey(): String =
        context.dataStore.data.map { it[KEY_SEARCH_API_KEY] ?: "" }.first()

    suspend fun setSearchApiKey(key: String) {
        context.dataStore.edit { it[KEY_SEARCH_API_KEY] = key }
    }

    // ── 软件更新配置 ──
    suspend fun getUpdateUrl(): String =
        context.dataStore.data.map { it[KEY_UPDATE_URL] ?: "" }.first()

    suspend fun setUpdateUrl(url: String) {
        context.dataStore.edit { it[KEY_UPDATE_URL] = url }
    }

    suspend fun getUpdateAutoCheck(): Boolean =
        context.dataStore.data.map { it[KEY_UPDATE_AUTO] ?: true }.first()

    suspend fun setUpdateAutoCheck(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UPDATE_AUTO] = enabled }
    }
}
