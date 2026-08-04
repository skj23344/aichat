package com.coder.aichat.data.api.providers

/**
 * 通义千问 (阿里云 DashScope) — OpenAI 兼容模式
 */
class QwenProvider : BaseOpenAiCompatProvider() {
    override val id = "qwen"
    override val displayName = "通义千问"
    override val brandColor = 0xFF7C3AED.toInt()
    override val defaultModel = "qwen-plus"

    override val models = listOf(
        AiModel("qwen-max", "Qwen-Max", "旗舰推理", 32768, false),
        AiModel("qwen-plus", "Qwen-Plus", "均衡好用", 131072, true),
        AiModel("qwen-turbo", "Qwen-Turbo", "高速便宜", 1000000, false),
        AiModel("qwen2.5-72b-instruct", "Qwen2.5 72B", "开源最强", 32768),
        AiModel("qwen2.5-coder-32b", "Qwen2.5 Coder", "代码特化", 32768),
    )

    override fun defaultBaseUrl() = "https://dashscope.aliyuncs.com/compatible-mode/v1"
}
