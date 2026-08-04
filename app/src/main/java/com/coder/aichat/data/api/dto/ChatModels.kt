package com.coder.aichat.data.api.dto

import com.google.gson.annotations.SerializedName

// ── 通用消息模型 ──

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = java.util.UUID.randomUUID().toString()
)

enum class MessageRole {
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("system") SYSTEM;

    override fun toString(): String = name.lowercase()
}

// ── OpenAI 兼容格式 ──

data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    @SerializedName("top_p") val topP: Double = 1.0
)

data class MessageDto(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<ChoiceDto>?,
    val usage: UsageDto? = null
)

data class ChoiceDto(
    val index: Int,
    val delta: DeltaDto? = null,
    val message: MessageDto? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class DeltaDto(
    val role: String? = null,
    val content: String? = null
)

data class UsageDto(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

// ── Claude Messages API ──

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessageDto>,
    val system: String? = null,
    val stream: Boolean = true,
    val temperature: Double = 0.7
)

data class ClaudeMessageDto(
    val role: String,
    val content: List<ClaudeContentBlock>
)

data class ClaudeContentBlock(
    val type: String,
    val text: String? = null
)

data class ClaudeStreamEvent(
    val type: String,
    val delta: ClaudeDelta? = null,
    @SerializedName("content_block") val contentBlock: ClaudeContentBlock? = null
)

data class ClaudeDelta(
    val type: String? = null,
    val text: String? = null
)

// ── Gemini API ──

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 4096,
    @SerializedName("topP") val topP: Double = 1.0
)

data class GeminiStreamResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)

// ── 模型列表（中转站 /v1/models） ──

data class ModelListResponse(
    val data: List<ModelInfoDto>? = null
)

data class ModelInfoDto(
    val id: String,
    @SerializedName("owned_by") val ownedBy: String? = null
)
