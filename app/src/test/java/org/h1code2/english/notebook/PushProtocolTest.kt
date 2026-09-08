package org.h1code2.english.notebook.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushProtocolTest {

    @Test
    fun `single english word detects as word`() {
        assertEquals("word", PushProtocol.detectType("wallet"))
        assertEquals("word", PushProtocol.detectType("isn't"))
    }

    @Test
    fun `english sentence detects as sentence`() {
        assertEquals("sentence", PushProtocol.detectType("The watch is expensive."))
        assertEquals("sentence", PushProtocol.detectType("hello world foo"))
    }

    @Test
    fun `chinese text detects as sentence`() {
        assertEquals("sentence", PushProtocol.detectType("钱包"))
        assertEquals("sentence", PushProtocol.detectType("这块手表很贵"))
    }

    @Test
    fun `two short english words still word`() {
        // 少于 2 个空格且无标点 → 视为短语（词组也算单词类）
        assertEquals("word", PushProtocol.detectType("ice cream"))
    }

    @Test
    fun `split keeps whole text as title when single line`() {
        val (title, content) = PushProtocol.splitTitleContent("The road is black and long.")
        assertEquals("The road is black and long.", title)
        assertEquals("", content)
    }

    @Test
    fun `split takes first line as title and rest as content`() {
        val (title, content) = PushProtocol.splitTitleContent("wallet\n\n**wallet** 的音标是：/ˈwɑːlɪt/")
        assertEquals("wallet", title)
        assertTrue(content.contains("音标"))
    }

    @Test
    fun `parseAdd with full json`() {
        val req = PushProtocol.parseAdd("""{"title":"wallet","content":"音标…","type":"word"}""")
        assertEquals("wallet", req.title)
        assertEquals("音标…", req.content)
        assertEquals("WORD", req.type)
    }

    @Test
    fun `parseAdd derives title and type from content`() {
        val req = PushProtocol.parseAdd("""{"content":"The road is black and long."}""")
        assertEquals("The road is black and long.", req.title)
        assertEquals("SENTENCE", req.type)
    }

    @Test
    fun `parseAdd rejects empty input`() {
        var thrown = false
        try {
            PushProtocol.parseAdd("")
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `responses contain expected fields`() {
        assertTrue(PushProtocol.okResponse(1, 2).contains("\"duplicates\":2"))
        assertTrue(PushProtocol.errorResponse("boom").contains("boom"))
        assertTrue(PushProtocol.pingResponse().contains("english-notebook"))
    }
}
