package com.coder.aichat.data.api.providers

import com.coder.aichat.data.api.dto.ClaudeContentBlock
import com.coder.aichat.data.api.dto.ClaudeMessageDto
import com.coder.aichat.data.api.dto.ClaudeRequest
import com.coder.aichat.data.api.dto.ClaudeStreamEvent
import com.coder.aichat.data.api.dto.ChatMessage
import com.coder.aichat.data.api.dto.MessageRole
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ClaudeProvider : AiProvider {
    override val id = "claude"
    override val displayName = "Claude"
    override val brandColor = 0xFFD97757.toInt()
    override val defaultModel = "claude-sonnet-4-20250514"

    override val models = listOf(
        AiModel("claude-sonnet-4-20250514", "Claude Sonnet 4", "最强性价比", 4096, true),
        AiModel("claude-opus-4-20250514", "Claude Opus 4", "最强旗舰", 4096),
        AiModel("claude-3.5-haiku-20241022", "Claude Haiku 3.5", "极速轻量", 4096),
    )

    private var apiKey = ""
    private var baseUrl = "https://api.anthropic.com"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun setApiKey(key: String) { apiKey = key }
    override fun getApiKey(): String = apiKey
    override fun setBaseUrl(url: String) { baseUrl = url }
    override fun getBaseUrl(): String = baseUrl
    override fun defaultBaseUrl() = "https://api.anthropic.com"

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String?,
        systemPrompt: String?,
        temperature: Double
    ): Flow<String> = flow {
        val modelName = model ?: defaultModel
        val claudeMessages = messages.map { msg ->
            ClaudeMessageDto(
                role = when (msg.role) {
                    MessageRole.USER -> "user"
                    else -> "assistant"
                },
                content = listOf(ClaudeContentBlock("text", msg.content))
            )
        }

        val request = ClaudeRequest(
            model = modelName,
            maxTokens = 4096,
            messages = claudeMessages,
            system = systemPrompt,
            stream = true,
            temperature = temperature
        )

        val jsonBody = gson.toJson(request)
        val url = "${baseUrl.trimEnd('/')}/v1/messages"

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-api-key", getApiKey())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                emit("⚠️ 请求失败 (${response.code}) — ${response.body?.string()}")
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val data = line ?: continue
                if (!data.startsWith("data: ")) continue
                val json = data.removePrefix("data: ").trim()

                try {
                    val event = gson.fromJson(json, ClaudeStreamEvent::class.java)
                    if (event.type == "content_block_delta") {
                        event.delta?.text?.let { if (it.isNotEmpty()) emit(it) }
                    }
                } catch (_: Exception) { /* 跳过解析错误 */ }
            }
            reader.close()
        } catch (e: Exception) {
            emit("⚠️ 网络错误: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun chatSync(
        messages: List<ChatMessage>,
        model: String?,
        systemPrompt: String?,
        temperature: Double
    ): String {
        val sb = StringBuilder()
        chatStream(messages, model, systemPrompt, temperature).collect { sb.append(it) }
        return sb.toString()
    }
}
