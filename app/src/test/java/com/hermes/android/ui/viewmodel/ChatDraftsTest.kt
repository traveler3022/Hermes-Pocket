package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDraftsTest {

    @Test
    fun eachChatKeepsItsOwnText() {
        var stored: String? = null
        val drafts = ChatDrafts({ stored }, { stored = it })
        drafts.put("a", "hello a")
        drafts.put("b", "hello b")
        drafts.put(null, "new chat")
        assertEquals("hello a", drafts.get("a"))
        assertEquals("hello b", drafts.get("b"))
        assertEquals("new chat", drafts.get(null))
        assertEquals("", drafts.get("c"))
    }

    @Test
    fun survivesARestart() {
        var stored: String? = null
        ChatDrafts({ stored }, { stored = it }).put("a", "سلام \"quoted\"\nline")
        assertEquals("سلام \"quoted\"\nline", ChatDrafts({ stored }, { stored = it }).get("a"))
    }

    @Test
    fun blankTextRemovesTheDraft() {
        var stored: String? = null
        val drafts = ChatDrafts({ stored }, { stored = it })
        drafts.put("a", "x")
        drafts.put("a", "  ")
        assertEquals("", drafts.get("a"))
        assertEquals("{}", stored)
    }

    @Test
    fun keepsOnlyTheNewestFifty() {
        var stored: String? = null
        val drafts = ChatDrafts({ stored }, { stored = it })
        repeat(ChatDrafts.MAX + 5) { drafts.put("s$it", "t$it") }
        assertEquals("", drafts.get("s0"))
        assertEquals("t${ChatDrafts.MAX + 4}", drafts.get("s${ChatDrafts.MAX + 4}"))
        assertEquals(ChatDrafts.MAX, ChatDrafts.decode(stored).size)
    }

    @Test
    fun brokenStoreStartsEmpty() {
        assertEquals(emptyMap<String, String>(), ChatDrafts.decode("not json"))
        assertNull(ChatDrafts.decode(null)["a"])
    }
}
