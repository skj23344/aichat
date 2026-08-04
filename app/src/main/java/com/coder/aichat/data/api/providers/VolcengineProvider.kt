package com.coder.aichat.data.api.providers

/**
 * 豆包 (字节跳动火山方舟 Volcengine Ark) — OpenAI 兼容
 * 注意：部分账号需使用推理接入点 ID（如 ep-xxxxxxxx），可在中转站/模型列表手动填。
 */
class VolcengineProvider : BaseOpenAiCompatProvider() {
    override val id = "doubao"
    override val displayName = "豆包"
    override val brandColor = 0xFF00A6FF.toInt()
    override val defaultModel = "doubao-seed-1-6-250615"

    override val models = listOf(
        AiModel("doubao-seed-1-6-250615", "Doubao Seed 1.6", "新一代旗舰", 65536, true),
        AiModel("doubao-pro-32k-250528", "Doubao Pro 32K", "通用旗舰", 32768),
        AiModel("doubao-lite-32k-250528", "Doubao Lite 32K", "轻量快速", 32768),
        AiModel("doubao-vision-pro-32k", "Doubao Vision Pro", "视觉理解", 32768),
    )

    override fun defaultBaseUrl() = "https://ark.cn-beijing.volces.com/api/v3"
}
