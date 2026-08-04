package com.coder.aichat.data.search

/** 单条搜索结果 */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/** 支持的联网搜索引擎 */
enum class SearchEngine(val id: String, val displayName: String, val keyHint: String) {
    BING("bing", "Bing 免费", "无需 Key"),
    TAVILY("tavily", "Tavily", "tavily.com 免费申请"),
    EXA("exa", "Exa", "exa.ai 免费申请");

    companion object {
        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id == id } ?: BING
    }
}

/** 把搜索结果格式化为发给 AI 的上下文 */
fun List<SearchResult>.toContext(query: String): String {
    if (isEmpty()) return ""
    val sb = StringBuilder()
    sb.append("以下是关于「$query」的联网搜索结果（供回答参考，请标注来源）：\n\n")
    forEachIndexed { i, r ->
        sb.append("${i + 1}. **${r.title}**\n")
        sb.append("来源：${r.url}\n")
        sb.append("${r.snippet}\n\n")
    }
    sb.append("请结合以上信息回答用户问题；若信息不足请明确说明。")
    return sb.toString()
}
