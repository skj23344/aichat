package com.coder.aichat

import android.app.Application
import com.coder.aichat.data.api.providers.CustomOpenAiProvider
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.data.local.AppDatabase
import com.coder.aichat.data.local.RolePlayStore
import com.coder.aichat.data.local.SettingsDataStore
import com.coder.aichat.data.repository.ChatRepository
import com.coder.aichat.data.search.SearchManager
import com.coder.aichat.data.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiChatApp : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: ChatRepository
    lateinit var settings: SettingsDataStore
    lateinit var rolePlayStore: RolePlayStore
    lateinit var searchManager: SearchManager
    lateinit var updateChecker: UpdateChecker

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 注册 AI 厂商
        ProviderRegistry.initDefault()

        // 初始化本地存储
        database = AppDatabase.getInstance(this)
        repository = ChatRepository(database.conversationDao())
        settings = SettingsDataStore(this)
        rolePlayStore = RolePlayStore(this)
        searchManager = SearchManager()
        updateChecker = UpdateChecker()

        // 从 DataStore 恢复各厂商的 API Key 与 Base URL
        restoreProviderConfigs()
    }

    private fun restoreProviderConfigs() {
        appScope.launch {
            ProviderRegistry.providerList.forEach { provider ->
                val apiKey = settings.getApiKey(provider.id)
                if (apiKey.isNotBlank()) provider.setApiKey(apiKey)

                val baseUrl = settings.getBaseUrl(provider.id)
                if (baseUrl.isNotBlank()) provider.setBaseUrl(baseUrl)

                // 中转站恢复自定义模型列表
                if (provider is CustomOpenAiProvider) {
                    val models = settings.getCustomModels()
                    if (models.isNotEmpty()) {
                        provider.setModels(models.map { com.coder.aichat.data.api.providers.AiModel(it, it) })
                    }
                }
            }
        }
    }

    companion object {
        lateinit var instance: AiChatApp
            private set
    }
}
