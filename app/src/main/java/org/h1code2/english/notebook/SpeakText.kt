package org.h1code2.english.notebook

import org.h1code2.english.notebook.data.EntryEntity
import org.h1code2.english.notebook.ui.SimpleMarkdownRenderer

/**
 * 生成 TTS 朗读文本：单词直接读标题；句子读标题；
 * 若正文以音标开头则跳过音标（TTS 读不了 IPA）。
 */
object SpeakText {
    fun forEntry(entry: EntryEntity): String = entry.title.trim()
}
