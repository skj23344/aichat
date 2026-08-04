package com.coder.aichat.data.update

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 更新信息 */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val note: String
)

/**
 * 更新检查器 — 支持两种更新源：
 * 1. GitHub Releases API（https://api.github.com/repos/owner/repo/releases/latest）
 * 2. 自定义 JSON：{ "version": "1.7", "apkUrl": "https://.../app.apk", "note": "更新说明" }
 */
class UpdateChecker {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 检查更新。返回需要更新的 [UpdateInfo]，无更新或失败返回 null。
     */
    suspend fun check(updateUrl: String, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            if (updateUrl.isBlank()) return@withContext null
            try {
                val request = Request.Builder()
                    .url(updateUrl)
                    .addHeader("User-Agent", "AiChat-Android")
                    .addHeader("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val info = if (updateUrl.contains("api.github.com/repos/")) {
                    parseGitHubRelease(body)
                } else {
                    parseCustomJson(body)
                }
                info?.takeIf { compareVersions(it.version, currentVersion) > 0 }
            } catch (_: Exception) {
                null
            }
        }

    private fun parseGitHubRelease(json: String): UpdateInfo? {
        val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: return null
        val tag = obj.getAsJsonPrimitive("tag_name")?.asString ?: return null
        val version = tag.removePrefix("v")
        val note = obj.getAsJsonPrimitive("body")?.asString ?: ""

        var apkUrl = ""
        obj.getAsJsonArray("assets")?.forEach { el ->
            val a = el.asJsonObject
            val name = a.getAsJsonPrimitive("name")?.asString ?: ""
            if (name.endsWith(".apk") && apkUrl.isBlank()) {
                apkUrl = a.getAsJsonPrimitive("browser_download_url")?.asString ?: ""
            }
        }
        if (apkUrl.isBlank()) return null
        return UpdateInfo(version, apkUrl, note.take(500))
    }

    private fun parseCustomJson(json: String): UpdateInfo? {
        val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: return null
        val version = obj.getAsJsonPrimitive("version")?.asString ?: return null
        val apkUrl = obj.getAsJsonPrimitive("apkUrl")?.asString ?: return null
        val note = obj.getAsJsonPrimitive("note")?.asString ?: ""
        return UpdateInfo(version, apkUrl, note.take(500))
    }
}

/** 版本号比较：v1 > v2 返回正数，相等 0，小于负数 */
fun compareVersions(v1: String, v2: String): Int {
    val a = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val b = v2.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}
