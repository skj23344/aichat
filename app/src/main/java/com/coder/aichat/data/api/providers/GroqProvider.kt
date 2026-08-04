package com.coder.aichat.data.api.providers

class GroqProvider : BaseOpenAiCompatProvider() {
    override val id = "groq"
    override val displayName = "Groq"
    override val brandColor = 0xFFF97316.toInt()
    override val defaultModel = "llama-3.1-70b-versatile"

    override val models = listOf(
        AiModel("llama-3.1-70b-versatile", "Llama 3.1 70B", "Meta 旗舰", 4096, true),
        AiModel("llama-3.1-8b-instant", "Llama 3.1 8B", "高速推理", 4096),
        AiModel("mixtral-8x7b-32768", "Mixtral 8x7B", "MoE 模型", 4096),
        AiModel("gemma2-9b-it", "Gemma 2 9B", "Google 轻量", 4096),
    )

    override fun defaultBaseUrl() = "https://api.groq.com/openai"
}
