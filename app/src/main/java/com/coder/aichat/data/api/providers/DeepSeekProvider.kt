package com.coder.aichat.data.api.providers

class DeepSeekProvider : BaseOpenAiCompatProvider() {
    override val id = "deepseek"
    override val displayName = "DeepSeek"
    override val brandColor = 0xFF4F46E5.toInt()
    override val defaultModel = "deepseek-chat"

    override val models = listOf(
        AiModel("deepseek-chat", "DeepSeek V3", "通用对话", 4096, true),
        AiModel("deepseek-reasoner", "DeepSeek R1", "推理模型", 4096),
    )

    override fun defaultBaseUrl() = "https://api.deepseek.com"
}
