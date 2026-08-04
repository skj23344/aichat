package com.coder.aichat.data.api.providers

import com.coder.aichat.data.api.dto.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 统一 AI 供应商接口 — 所有厂商通过此接口接入。
 * 新增厂商只需实现此接口并注册到 [ProviderRegistry]。
 */
interface AiProvider {
    /** 厂商唯一标识 */
    val id: String

    /** 显示名称 */
    val displayName: String

    /** 品牌色 ARGB */
    val brandColor: Int

    /** 支持的模型列表 */
    val models: List<AiModel>

    /** 默认模型 */
    val defaultModel: String

    /** API Key */
    fun setApiKey(key: String)
    fun getApiKey(): String

    /** Base URL (支持自定义代理) */
    fun setBaseUrl(url: String)
    fun getBaseUrl(): String

    /** 默认 Base URL */
    fun defaultBaseUrl(): String

    /** 是否需要 API Key（Ollama 等本地服务不需要） */
    val requiresApiKey: Boolean get() = true

    /** 系统提示词是否受支持 */
    val supportsSystemPrompt: Boolean get() = true

    /**
     * 流式聊天 — 返回 Flow<String> 逐步产出文本增量。
     * @param temperature 温度，控制随机性（0~2），越高越发散
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String? = null,
        systemPrompt: String? = null,
        temperature: Double = 0.7
    ): Flow<String>

    /** 非流式聊天 */
    suspend fun chatSync(
        messages: List<ChatMessage>,
        model: String? = null,
        systemPrompt: String? = null,
        temperature: Double = 0.7
    ): String
}

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String = "",
    val maxTokens: Int = 4096,
    val isDefault: Boolean = false
)
