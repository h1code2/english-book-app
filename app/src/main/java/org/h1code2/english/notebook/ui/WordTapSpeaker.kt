package org.h1code2.english.notebook.ui

import android.graphics.Color
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.MotionEvent
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 点击 TextView 中的英文单词即回调该单词（用于 TTS 朗读），并给出短暂高亮反馈。
 */
object WordTapSpeaker {

    private val WORD_REGEX = Regex("[A-Za-z][A-Za-z'’-]*")
    private const val HIGHLIGHT_MS = 800L
    private val scope = MainScope()

    /**
     * 安装触摸监听。onWord 回调在触摸命中文本中的英文单词时触发（返回完整单词文本）。
     * 返回 true 表示事件已消费（命中单词），false 时按普通点击处理。
     */
    fun attach(textView: TextView, onWord: (String) -> Unit) {
        textView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 必须在 DOWN 消费事件，后续 UP 才会派发给本 View
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val text = textView.text as? Spanned ?: return@setOnTouchListener false
                    val x = event.x - textView.totalPaddingLeft + textView.scrollX
                    val y = event.y - textView.totalPaddingTop + textView.scrollY
                    val layout = textView.layout ?: return@setOnTouchListener false
                    val line = layout.getLineForVertical(y.toInt())
                    val offset = layout.getOffsetForHorizontal(line, x)
                    if (offset >= text.length) return@setOnTouchListener true
                    val word = wordAt(text.toString(), offset)
                    if (word != null) {
                        onWord(word)
                        highlight(textView, word)
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 提取 offset 所在的英文单词；不在单词内返回 null */
    fun wordAt(text: String, offset: Int): String? {
        if (offset < 0 || offset > text.length) return null
        for (match in WORD_REGEX.findAll(text)) {
            if (offset >= match.range.first && offset <= match.range.last + 1) {
                return match.value.trim('\'', '’', '-')
            }
        }
        return null
    }

    private fun highlight(textView: TextView, word: String) {
        val spannable = textView.text as? Spannable ?: return
        val sb = spannable as? SpannableStringBuilder ?: SpannableStringBuilder(spannable)
        var start = -1
        var end = -1
        WORD_REGEX.findAll(sb).forEach { m ->
            if (m.value.trim('\'', '’', '-') == word && start == -1) {
                start = m.range.first
                end = m.range.last + 1
            }
        }
        if (start == -1) return
        val span = BackgroundColorSpan(Color.parseColor("#66FFB300"))
        sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = sb
        scope.launch {
            delay(HIGHLIGHT_MS)
            sb.removeSpan(span)
            textView.text = sb
        }
    }
}
