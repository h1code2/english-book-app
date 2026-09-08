package org.english.book.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.TextPaint

/**
 * 轻量 Markdown 渲染器：支持 **加粗**、*斜体*、`行内代码`、~~删除线~~、
 * > 引用块、- / * 无序列表、段落。纯 android.text 实现，无第三方依赖，
 * 可在单元测试中直接运行（Robolectric 不需要：仅用到 text 包的类）。
 */
object SimpleMarkdownRenderer {

    data class Block(val kind: Kind, val text: String)

    enum class Kind { PARAGRAPH, QUOTE, LIST_ITEM }

    /** 解析为块列表，便于测试与渲染分离 */
    fun parse(src: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) blocks += Block(Kind.PARAGRAPH, text)
            paragraph.setLength(0)
        }

        src.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.isEmpty() -> flushParagraph()
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val content = trimmed.removePrefix(">").trim()
                    val last = blocks.lastOrNull()
                    if (last != null && last.kind == Kind.QUOTE) {
                        blocks[blocks.size - 1] = last.copy(text = last.text + "\n" + content)
                    } else {
                        blocks += Block(Kind.QUOTE, content)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    blocks += Block(Kind.LIST_ITEM, trimmed.substring(2).trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flushParagraph()
        return blocks
    }

    /** 行内样式：加粗 / 斜体 / 行内代码 / 删除线 */
    fun applyInlineSpans(text: String): SpannableStringBuilder =
        applyInlineSpans(text, Color.parseColor("#EEEEEE"), Color.parseColor("#C2185B"))

    /** 主题感知版本：行内代码的底色/前景色由调用方按当前主题传入 */
    fun applyInlineSpans(text: String, codeBg: Int, codeFg: Int): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        applyWrapped(sb, Regex("\\*\\*(.+?)\\*\\*")) { start, len ->
            sb.setSpan(StyleSpan(Typeface.BOLD), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyWrapped(sb, Regex("(?<!\\*)\\*([^*\n]+)\\*(?!\\*)")) { start, len ->
            sb.setSpan(StyleSpan(Typeface.ITALIC), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyWrapped(sb, Regex("`([^`]+)`")) { start, len ->
            sb.setSpan(CodeSpan(codeBg, codeFg), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyWrapped(sb, Regex("~~(.+?)~~")) { start, len ->
            sb.setSpan(StrikethroughSpan(), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /**
     * 通用的成对标记处理：只删除两侧定界符并对内部文本应用 span，
     * 从而保留内部文本上已有的 span（嵌套样式）。
     */
    private inline fun applyWrapped(
        sb: SpannableStringBuilder,
        regex: Regex,
        applySpan: (start: Int, innerLen: Int) -> Unit
    ) {
        var searchFrom = 0
        while (true) {
            val text = sb.toString()
            val match = regex.find(text, startIndex = searchFrom) ?: break
            val start = match.range.first
            val endEx = match.range.last + 1
            val inner = match.groupValues[1]
            val delimLen = (match.value.length - inner.length) / 2
            sb.delete(endEx - delimLen, endEx)   // 尾定界符
            sb.delete(start, start + delimLen)   // 首定界符
            applySpan(start, inner.length)
            searchFrom = start + inner.length
        }
    }

    /** 渲染整段 Markdown 为富文本 */
    fun render(src: String, quoteColor: Int = Color.parseColor("#5D4037")): CharSequence =
        render(src, quoteColor, Color.parseColor("#EEEEEE"), Color.parseColor("#C2185B"))

    /** 主题感知渲染：引用条、行内代码底色/前景色由调用方按当前主题传入 */
    fun render(src: String, quoteColor: Int, codeBg: Int, codeFg: Int): CharSequence {
        val out = SpannableStringBuilder()
        parse(src).forEachIndexed { index, block ->
            if (index > 0) out.append("\n\n")
            val start = out.length
            out.append(applyInlineSpans(block.text, codeBg, codeFg))
            val end = out.length
            when (block.kind) {
                Kind.QUOTE -> out.setSpan(QuoteSpan(quoteColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                Kind.LIST_ITEM -> out.setSpan(BulletSpan(8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                Kind.PARAGRAPH -> Unit
            }
        }
        return out
    }

    /** 行内代码样式：等宽字体 + 底色 */
    class CodeSpan(private val bg: Int, private val fg: Int) : CharacterStyle() {
        override fun updateDrawState(ds: TextPaint) {
            ds.bgColor = bg
            ds.color = fg
            ds.typeface = Typeface.MONOSPACE
        }
    }

    /** 提取纯文本（去标记，用于摘要、搜索、闪卡） */
    fun toPlainText(src: String): String =
        parse(src).joinToString("\n") { block ->
            block.text
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("~~(.+?)~~"), "$1")
                .replace(Regex("(?<!\\*)\\*([^*\n]+)\\*(?!\\*)"), "$1")
        }

    /** 生成正文首行摘要 */
    fun summary(src: String, maxLength: Int = 60): String {
        val text = toPlainText(src).lineSequence().firstOrNull()?.trim() ?: ""
        return if (text.length <= maxLength) text else text.take(maxLength - 1) + "…"
    }
}
