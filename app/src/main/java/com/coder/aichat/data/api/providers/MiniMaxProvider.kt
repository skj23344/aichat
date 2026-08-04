package com.coder.aichat.data.api.providers

/**
 * MiniMax — OpenAI 兼容
 */
class MiniMaxProvider : BaseOpenAiCompatProvider() {
    override val id = "minimax"
    override val displayName = "MiniMax"
    override val brandColor = 0xFF4F46E5.toInt()
    override val defaultModel = "MiniMax-Text-01"

    override val models = listOf(
        AiModel("MiniMax-Text-01", "MiniMax Text 01", "旗舰文本", 1000000, true),
        AiModel("abab6.5s-chat", "abab6.5s", "旧版均衡", 245760),
        AiModel("abab5.5s-chat", "abab5.5s", "轻量快速", 16384),
    )

    override fun defaultBaseUrl() = "https://api.minimaxi.com/v1"
}
