package org.h1code2.english.notebook

import org.h1code2.english.notebook.data.EntryType
import org.h1code2.english.notebook.ui.SimpleMarkdownRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：只覆盖不含 android.text 类的解析逻辑。
 * render() 的 span 渲染依赖 android.text，在设备/模拟器上验证。
 */
class SimpleMarkdownRendererTest {

    @Test
    fun `parse paragraphs quotes and list items`() {
        val src = """
            意思是：**钱包**。

            > w-a-l-l-e-t = wallet

            - watch：手表
            - expensive：昂贵的
        """.trimIndent()

        val blocks = SimpleMarkdownRenderer.parse(src)

        assertEquals(4, blocks.size)
        assertEquals(SimpleMarkdownRenderer.Kind.PARAGRAPH, blocks[0].kind)
        assertEquals(SimpleMarkdownRenderer.Kind.QUOTE, blocks[1].kind)
        assertEquals("w-a-l-l-e-t = wallet", blocks[1].text)
        assertEquals(SimpleMarkdownRenderer.Kind.LIST_ITEM, blocks[2].kind)
        assertEquals("watch：手表", blocks[2].text)
        assertEquals(SimpleMarkdownRenderer.Kind.LIST_ITEM, blocks[3].kind)
    }

    @Test
    fun `consecutive quote lines merge into one block`() {
        val src = "> **This is my wallet.**\n> 这是我的钱包。"
        val blocks = SimpleMarkdownRenderer.parse(src)
        assertEquals(1, blocks.size)
        assertEquals("**This is my wallet.**\n这是我的钱包。", blocks[0].text)
    }

    @Test
    fun `star list marker also supported`() {
        val blocks = SimpleMarkdownRenderer.parse("* road：道路")
        assertEquals(1, blocks.size)
        assertEquals(SimpleMarkdownRenderer.Kind.LIST_ITEM, blocks[0].kind)
        assertEquals("road：道路", blocks[0].text)
    }

    @Test
    fun `adjacent plain lines join into one paragraph`() {
        val blocks = SimpleMarkdownRenderer.parse("读音近似：**沃利特**\n意思是：**钱包**。")
        assertEquals(1, blocks.size)
        assertEquals("读音近似：**沃利特** 意思是：**钱包**。", blocks[0].text)
    }

    @Test
    fun `plain text strips markers`() {
        val plain = SimpleMarkdownRenderer.toPlainText("**wallet** 的音标是 **/ˈwɑːlɪt/**")
        assertEquals("wallet 的音标是 /ˈwɑːlɪt/", plain)
    }

    @Test
    fun `inline code stripped in plain text`() {
        val plain = SimpleMarkdownRenderer.toPlainText("读作 `wallet` 单词")
        assertEquals("读作 wallet 单词", plain)
    }

    @Test
    fun `summary takes first line and truncates`() {
        assertEquals("第一行", SimpleMarkdownRenderer.summary("第一行\n\n第二行"))
        val summary = SimpleMarkdownRenderer.summary("**这是一段很长很长的内容**用于测试摘要", maxLength = 10)
        assertTrue(summary.length <= 10)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `empty input parses to nothing`() {
        assertEquals(0, SimpleMarkdownRenderer.parse("").size)
        assertEquals(0, SimpleMarkdownRenderer.parse("\n\n  \n").size)
    }

    @Test
    fun `entry type falls back to word`() {
        assertEquals(EntryType.WORD, EntryType.from("WORD"))
        assertEquals(EntryType.SENTENCE, EntryType.from("SENTENCE"))
        assertEquals(EntryType.WORD, EntryType.from("whatever"))
        assertEquals(EntryType.WORD, EntryType.from(null))
    }
}
