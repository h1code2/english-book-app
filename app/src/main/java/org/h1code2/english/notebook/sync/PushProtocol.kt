package org.h1code2.english.notebook.sync

/**
 * 电脑推送协议的纯逻辑部分（可在 JVM 单元测试中直接验证）。
 *
 * 请求体 JSON：{"title":"wallet","content":"...","type":"word|sentence"}
 * title 缺失时由 content 首行推导；type 缺失时自动判断。
 */
object PushProtocol {

    private val SINGLE_WORD = Regex("[A-Za-z][A-Za-z'’-]*")

    data class AddRequest(val title: String, val content: String, val type: String)

    /** 自动判断单词 / 句子 */
    fun detectType(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "word"
        if (SINGLE_WORD.matches(t)) return "word"
        val spaces = t.count { it.isWhitespace() }
        val hasPunctuation = t.contains(Regex("[。！？.!?，,；;：:]"))
        val hasCjk = t.any { it.code in 0x4E00..0x9FFF }
        return if (spaces >= 2 || hasPunctuation || hasCjk) "sentence" else "word"
    }

    /** 拆分标题与正文：第一行（或全文）作为标题 */
    fun splitTitleContent(text: String): Pair<String, String> {
        val t = text.trim()
        if (t.isEmpty()) return "" to ""
        val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: t
        val title = if (firstLine.length <= 80) firstLine else firstLine.take(79) + "…"
        val content = if (t == title) "" else {
            t.removePrefix(title).trim('\n', ' ').ifEmpty { "" }
        }
        return title to content
    }

    /** 解析 /add 请求体，非法输入抛 IllegalArgumentException */
    fun parseAdd(body: String): AddRequest {
        if (body.isBlank()) throw IllegalArgumentException("empty body")
        val json = org.json.JSONObject(body)
        val content = json.optString("content", "").trim()
        var title = json.optString("title", "").trim()
        if (title.isEmpty() && content.isNotEmpty()) {
            title = splitTitleContent(content).first
        }
        if (title.isEmpty()) throw IllegalArgumentException("title/content 不能都为空")
        val typeRaw = json.optString("type", "").trim().uppercase()
        val type = when (typeRaw) {
            "WORD" -> "WORD"
            "SENTENCE" -> "SENTENCE"
            else -> detectType(if (title.isNotEmpty()) title else content).uppercase()
        }
        return AddRequest(title, content, type)
    }

    fun okResponse(added: Int, duplicates: Int): String =
        org.json.JSONObject().put("ok", true).put("added", added).put("duplicates", duplicates).toString()

    fun errorResponse(message: String): String =
        org.json.JSONObject().put("ok", false).put("error", message).toString()

    fun pingResponse(): String =
        org.json.JSONObject().put("ok", true).put("app", "english-notebook").toString()
}
