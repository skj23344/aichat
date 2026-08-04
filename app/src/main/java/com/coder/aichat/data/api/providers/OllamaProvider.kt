package com.coder.aichat.data.api.providers

/**
 * Ollama 本地模型 — 兼容 OpenAI API 格式。
 * 默认连接 localhost:11434，无需 API Key。
 */
class OllamaProvider : BaseOpenAiCompatProvider() {
    override val id = "ollama"
    override val displayName = "Ollama"
    override val brandColor = 0xFF6366F1.toInt()
    override val defaultModel = "llama3.1"
    override val requiresApiKey = false

    override val models = listOf(
        AiModel("llama3.1", "Llama 3.1", "Meta 本地模型", 4096, true),
        AiModel("qwen2.5", "Qwen 2.5", "阿里通义千问", 4096),
        AiModel("mistral", "Mistral", "Mistral 模型", 4096),
        AiModel("codellama", "Code Llama", "代码特化", 4096),
    )

    override fun defaultBaseUrl() = "http://localhost:11434"

    // Ollama 不需要鉴权，但保留 header 避免服务端校验失败
    override fun getApiKey(): String = "ollama"
}
