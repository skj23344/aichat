package com.coder.aichat.data.api.providers

/**
 * Kimi (月之暗面 Moonshot) — OpenAI 兼容
 */
class KimiProvider : BaseOpenAiCompatProvider() {
    override val id = "kimi"
    override val displayName = "Kimi"
    override val brandColor = 0xFF1A1A1A.toInt()
    override val defaultModel = "kimi-latest"

    override val models = listOf(
        AiModel("kimi-latest", "Kimi 最新", "官方最新版本", 128000, true),
        AiModel("kimi-k2-0905-preview", "Kimi K2", "新一代推理模型", 128000),
        AiModel("moonshot-v1-128k", "Moonshot 128K", "超长上下文", 128000),
        AiModel("moonshot-v1-32k", "Moonshot 32K", "长上下文", 32768),
        AiModel("moonshot-v1-8k", "Moonshot 8K", "轻量快速", 8192),
    )

    override fun defaultBaseUrl() = "https://api.moonshot.cn/v1"
}
