package com.coder.aichat.data.api.providers

import com.coder.aichat.data.api.dto.ChatCompletionRequest
import com.coder.aichat.data.api.dto.ChatCompletionResponse
import com.coder.aichat.data.api.dto.ChatMessage
import com.coder.aichat.data.api.dto.MessageDto
import com.coder.aichat.data.api.dto.MessageRole
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 API 的通用实现。
 * OpenAI、DeepSeek、Groq、Ollama、vLLM 等都遵循此格式。
 * 子类只需提供 id/displayName/brandColor/models/baseUrl。
 */
abstract class BaseOpenAiCompatProvider : AiProvider {
    protected val gson = Gson()
    protected val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var apiKey: String = ""
    private var baseUrl: String = defaultBaseUrl()
    private var extraHeaders: Map<String, String> = emptyMap()

    override fun setApiKey(key: String) { apiKey = key }
    override fun getApiKey(): String = apiKey

    override fun setBaseUrl(url: String) { baseUrl = url }
    override fun getBaseUrl(): String = baseUrl

    /** 设置自定义 HTTP 请求头（中转站等需要） */
    fun setExtraHeaders(headers: Map<String, String>) {
        extraHeaders = headers.filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    protected fun Request.Builder.withExtraHeaders(): Request.Builder {
        extraHeaders.forEach { (k, v) -> addHeader(k, v) }
        return this
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String?,
        systemPrompt: String?,
        temperature: Double
    ): Flow<String> = flow {
        val modelName = model ?: defaultModel
        val msgList = buildMessageList(messages, systemPrompt)

        val requestBody = ChatCompletionRequest(
            model = modelName,
            messages = msgList,
            stream = true,
            temperature = temperature
        )

        val jsonBody = gson.toJson(requestBody)
        val url = "${getBaseUrl().trimEnd('/')}/v1/chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${getApiKey()}")
            .addHeader("Content-Type", "application/json")
            .withExtraHeaders()
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                emit("⚠️ 请求失败 (${response.code})\n$errorBody")
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val data = line ?: continue
                if (!data.startsWith("data: ")) continue
                val json = data.removePrefix("data: ").trim()
                if (json == "[DONE]") break

                try {
                    val chunk = gson.fromJson(json, ChatCompletionResponse::class.java)
                    val content = chunk.choices?.firstOrNull()?.delta?.content ?: ""
                    if (content.isNotEmpty()) emit(content)
                } catch (_: Exception) { /* 跳过部分 chunk 解析错误 */ }
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
        val modelName = model ?: defaultModel
        val msgList = buildMessageList(messages, systemPrompt)

        val requestBody = ChatCompletionRequest(
            model = modelName,
            messages = msgList,
            stream = false,
            temperature = temperature
        )

        val jsonBody = gson.toJson(requestBody)
        val url = "${getBaseUrl().trimEnd('/')}/v1/chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${getApiKey()}")
            .addHeader("Content-Type", "application/json")
            .withExtraHeaders()
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return "⚠️ 空响应"
            if (!response.isSuccessful) return "⚠️ 请求失败 (${response.code})\n$body"

            val result = gson.fromJson(body, ChatCompletionResponse::class.java)
            result.choices?.firstOrNull()?.message?.content ?: ""
        } catch (e: Exception) {
            "⚠️ 网络错误: ${e.message}"
        }
    }

    protected fun buildMessageList(
        messages: List<ChatMessage>,
        systemPrompt: String?
    ): List<MessageDto> {
        val list = mutableListOf<MessageDto>()
        if (!systemPrompt.isNullOrBlank()) {
            list.add(MessageDto(role = "system", content = systemPrompt))
        }
        messages.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            list.add(MessageDto(role = role, content = msg.content))
        }
        return list
    }
}
