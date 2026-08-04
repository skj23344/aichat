package com.coder.aichat.data.api.providers

class OpenAiProvider : BaseOpenAiCompatProvider() {
    override val id = "openai"
    override val displayName = "OpenAI"
    override val brandColor = 0xFF10A37F.toInt()
    override val defaultModel = "gpt-4o"

    override val models = listOf(
        AiModel("gpt-4o", "GPT-4o", "最新多模态旗舰", 4096, true),
        AiModel("gpt-4o-mini", "GPT-4o Mini", "轻量快速", 4096),
        AiModel("gpt-4-turbo", "GPT-4 Turbo", "前代旗舰", 4096),
        AiModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "经典经济型", 4096),
        AiModel("o1-mini", "o1-mini", "推理特化", 4096),
    )

    override fun defaultBaseUrl() = "https://api.openai.com"
}
