package com.coder.aichat.data.api.providers

/**
 * 智谱清言 (Zhipu GLM) — OpenAI 兼容
 */
class ZhipuProvider : BaseOpenAiCompatProvider() {
    override val id = "zhipu"
    override val displayName = "智谱清言"
    override val brandColor = 0xFF3B82F6.toInt()
    override val defaultModel = "glm-4-flash"

    override val models = listOf(
        AiModel("glm-4-plus", "GLM-4-Plus", "旗舰模型", 128000, false),
        AiModel("glm-4-air", "GLM-4-Air", "高性价比", 128000, false),
        AiModel("glm-4-flash", "GLM-4-Flash", "免费快速", 128000, true),
        AiModel("glm-4-long", "GLM-4-Long", "超长文本", 1000000),
    )

    override fun defaultBaseUrl() = "https://open.bigmodel.cn/api/paas/v4"
}
