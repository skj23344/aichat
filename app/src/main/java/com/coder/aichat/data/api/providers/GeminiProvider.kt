package com.coder.aichat.data.api.providers

import com.coder.aichat.data.api.dto.*
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

class GeminiProvider : AiProvider {
    override val id = "gemini"
    override val displayName = "Gemini"
    override val brandColor = 0xFF4285F4.toInt()
    override val defaultModel = "gemini-2.0-flash"

    override val models = listOf(
        AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Google 最新速度旗舰", 4096, true),
        AiModel("gemini-1.5-pro", "Gemini 1.5 Pro", "长上下文推理", 4096),
        AiModel("gemini-1.5-flash", "Gemini 1.5 Flash", "轻量快速", 4096),
    )

    private var apiKey = ""
    private var baseUrl = "https://generativelanguage.googleapis.com"
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
    override fun defaultBaseUrl() = "https://generativelanguage.googleapis.com"

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String?,
        systemPrompt: String?,
        temperature: Double
    ): Flow<String> = flow {
        val modelName = model ?: defaultModel

        val contents = messages.map { msg ->
            GeminiContent(
                role = when (msg.role) {
                    MessageRole.USER -> "user"
                    else -> "model"
                },
                parts = listOf(GeminiPart(msg.content))
            )
        }.toMutableList()

        // Gemini 不支持 system role，把系统提示词前置进第一条 user 消息
        if (!systemPrompt.isNullOrBlank()) {
            val systemInstruction = "【系统指令】$systemPrompt\n\n"
            if (contents.isEmpty()) {
                contents.add(GeminiContent("user", listOf(GeminiPart(systemInstruction + "你好"))))
            } else {
                val first = contents.first()
                if (first.role == "user") {
                    contents[0] = first.copy(
                        parts = listOf(GeminiPart(systemInstruction + first.parts.first().text))
                    )
                } else {
                    contents.add(0, GeminiContent("user", listOf(GeminiPart(systemInstruction))))
                }
            }
        }

        val request = GeminiRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(temperature = temperature)
        )

        val jsonBody = gson.toJson(request)
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/$modelName:streamGenerateContent" +
                "?alt=sse&key=${getApiKey()}"

        val httpRequest = Request.Builder()
            .url(url)
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
                    val chunk = gson.fromJson(json, GeminiStreamResponse::class.java)
                    val text = chunk.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text ?: ""
                    if (text.isNotEmpty()) emit(text)
                } catch (_: Exception) { /* 跳过 */ }
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
