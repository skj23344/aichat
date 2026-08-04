package com.coder.aichat.data.search

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 联网搜索引擎 — 支持 Tavily / Exa。
 * 在发送消息前搜索，把结果作为上下文给 AI。
 */
class SearchManager {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(engine: SearchEngine, apiKey: String, query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                when (engine) {
                    SearchEngine.BING -> searchBing(query)              // 免费，无需 Key
                    SearchEngine.TAVILY ->
                        if (apiKey.isBlank()) emptyList() else searchTavily(apiKey, query)
                    SearchEngine.EXA ->
                        if (apiKey.isBlank()) emptyList() else searchExa(apiKey, query)
                }
            } catch (e: Exception) {
                listOf(SearchResult("搜索失败", "", e.message ?: "网络错误"))
            }
        }

    // ── Bing 免费搜索（抓取结果页解析，无需 Key） ──
    private fun searchBing(query: String): List<SearchResult> {
        val url = "https://www.bing.com/search?q=" +
                java.net.URLEncoder.encode(query, "UTF-8") +
                "&count=8&setlang=zh-CN&cc=CN"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", DESKTOP_UA)
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Accept", "text/html,application/xhtml+xml")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return listOf(SearchResult("Bing 请求失败", "", "HTTP ${response.code}"))
        }
        val html = response.body?.string() ?: return emptyList()
        val parsed = parseBingHtml(html)
        return if (parsed.isEmpty()) {
            listOf(SearchResult("未获取到结果", "", "可能被临时限流，可稍后重试或改用 Tavily/Exa"))
        } else parsed
    }

    private fun parseBingHtml(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val algoRegex = Regex("<li[^>]*class=\"b_algo[^\"]*\"[\\s\\S]*?</li>")
        algoRegex.findAll(html).forEach { match ->
            val block = match.value
            val linkMatch = Regex("<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>\\s*</h2>")
                .find(block)
            if (linkMatch != null) {
                val url = linkMatch.groupValues[1]
                val title = cleanHtml(linkMatch.groupValues[2])
                if (title.isBlank() || url.isBlank()) return@forEach

                val snippet = Regex("<p[^>]*>([\\s\\S]*?)</p>").find(block)
                    ?.groupValues?.get(1)?.let { cleanHtml(it) }.orEmpty()
                results.add(SearchResult(title, url, snippet))
            }
        }
        return results
    }

    private fun cleanHtml(raw: String): String {
        return raw
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }

    // ── Tavily ──
    private fun searchTavily(apiKey: String, query: String): List<SearchResult> {
        val body = gson.toJson(
            mapOf(
                "api_key" to apiKey,
                "query" to query,
                "max_results" to 5,
                "include_answer" to true,
                "search_depth" to "basic"
            )
        )
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return listOf(SearchResult("搜索请求失败", "", "HTTP ${response.code}: ${response.body?.string()?.take(120)}"))
        }
        val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

        val results = mutableListOf<SearchResult>()
        // 优先放 Tavily 的智能摘要
        json.getAsJsonPrimitive("answer")?.takeUnless { it.asString.isBlank() }?.let { answer ->
            results.add(SearchResult("综合摘要", "", answer.asString))
        }
        json.getAsJsonArray("results")?.forEach { el ->
            val o = el.asJsonObject
            results.add(
                SearchResult(
                    title = o.getAsJsonPrimitive("title")?.asString ?: "",
                    url = o.getAsJsonPrimitive("url")?.asString ?: "",
                    snippet = o.getAsJsonPrimitive("content")?.asString ?: ""
                )
            )
        }
        return results
    }

    // ── Exa ──
    private fun searchExa(apiKey: String, query: String): List<SearchResult> {
        val body = gson.toJson(
            mapOf(
                "query" to query,
                "numResults" to 5,
                "text" to mapOf("maxCharacters" to 800)
            )
        )
        val request = Request.Builder()
            .url("https://api.exa.ai/search")
            .addHeader("x-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return listOf(SearchResult("搜索请求失败", "", "HTTP ${response.code}: ${response.body?.string()?.take(120)}"))
        }
        val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

        val results = mutableListOf<SearchResult>()
        json.getAsJsonArray("results")?.forEach { el ->
            val o = el.asJsonObject
            results.add(
                SearchResult(
                    title = o.getAsJsonPrimitive("title")?.asString ?: "",
                    url = o.getAsJsonPrimitive("url")?.asString ?: "",
                    snippet = o.getAsJsonPrimitive("text")?.asString ?: ""
                )
            )
        }
        return results
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
