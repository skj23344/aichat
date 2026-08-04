package com.coder.aichat.data.api.providers

import com.coder.aichat.data.api.dto.ModelListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自定义中转站 — 任意 OpenAI 兼容 API。
 * 模型列表可从 /v1/models 自动拉取，也可在设置页手动填写。
 */
class CustomOpenAiProvider : BaseOpenAiCompatProvider() {
    override val id = "custom"
    override val displayName = "自定义中转站"
    override val brandColor = 0xFF6366F1.toInt()
    override val defaultModel: String get() = _models.firstOrNull()?.id ?: "model"

    private val _models = mutableListOf<AiModel>()
    override val models: List<AiModel> get() = _models

    /** 是否可显式配置 API Key（中转站一般需要，但允许留空走白名单） */
    override val requiresApiKey = false

    override fun defaultBaseUrl() = "https://api.example.com"

    fun setModels(list: List<AiModel>) {
        _models.clear()
        _models.addAll(list)
    }

    /** 从 /v1/models 拉取可用模型 */
    suspend fun fetchModels(): List<AiModel> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl().trimEnd('/')}/v1/models"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                return@withContext listOf(
                    AiModel("__error__", "拉取失败(${response.code})", body.take(100), 4096)
                )
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            val result = gson.fromJson(body, ModelListResponse::class.java)
            result.data?.map { AiModel(it.id, it.id, it.ownedBy ?: "", 4096) } ?: emptyList()
        } catch (e: Exception) {
            listOf(AiModel("__error__", "网络错误", e.message ?: "", 4096))
        }
    }
}
