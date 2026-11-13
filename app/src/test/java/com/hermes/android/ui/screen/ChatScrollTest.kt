package com.hermes.android.ui.screen

import com.hermes.android.ui.viewmodel.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat's own scrolling rules (ChatScroll.kt). Each case is a bug the chat once had:
 * the list chasing every streamed token, snapping back to the question each time the
 * agent added a tool card, and the question pushed off screen by a wrong index.
 */
class ChatScrollTest {

    private var seq = 0L

    private fun user(id: String) = ChatMessage.User(id = id, timestamp = seq++, text = "q $id")

    private fun assistant(id: String, text: String = "", streaming: Boolean = false) =
        ChatMessage.Assistant(id = id, timestamp = seq++, text = text, isStreaming = streaming, reasoning = null)

    private fun tool(id: String) = ChatMessage.ToolCall(
        id = id, timestamp = seq++, toolName = "terminal", argsText = "{}", resultText = null,
        error = null, isRunning = true, durationS = null,
    )

    private fun status(id: String) = ChatMessage.Status(id = id, timestamp = seq++, text = "…", isError = false)

    @Test
    fun pinKeyIsTheNewestUserMessage() {
        val list = listOf(user("u1"), assistant("a1", "x"), user("u2"), assistant("a2"))
        assertEquals("u2", chatPinKey(list))
    }

    @Test
    fun pinKeyIsNullWithoutAUserMessage() {
        assertNull(chatPinKey(emptyList()))
        assertNull(chatPinKey(listOf(assistant("a1", "hello"))))
    }

    @Test
    fun streamingTextDoesNotChangeThePinKey() {
        val base = listOf(user("u1"), assistant("a1"), user("u2"))
        val key = chatPinKey(base + assistant("a2", "", streaming = true))
        var text = ""
        repeat(50) {
            text += "token "
            assertEquals(key, chatPinKey(base + assistant("a2", text, streaming = true)))
        }
    }

    @Test
    fun rowsTheAgentAddsDuringATurnDoNotChangeThePinKey() {
        val turn = mutableListOf<ChatMessage>(user("u1"), assistant("a1", "x"), user("u2"))
        val key = chatPinKey(turn)
        listOf(assistant("a2", streaming = true), tool("t1"), status("s1"), tool("t2"), assistant("a3", "done"))
            .forEach {
                turn += it
                assertEquals(key, chatPinKey(turn))
            }
    }

    @Test
    fun aNewUserTurnChangesThePinKey() {
        val before = listOf(user("u1"), assistant("a1", "x"))
        val after = before + user("u2")
        assertEquals("u1", chatPinKey(before))
        assertEquals("u2", chatPinKey(after))
    }

    @Test
    fun pinIndexCountsTheRenderedListNotTheFullOne() {
        // The full list still holds tool cards the chat folds away; the index must be
        // the rendered list's, or the question lands below the top of the screen.
        val full = listOf(user("u1"), tool("t1"), tool("t2"), assistant("a1", "x"), user("u2"), tool("t3"))
        val rendered = full.filter { it !is ChatMessage.ToolCall }
        assertEquals(4, chatPinIndex(full))
        assertEquals(2, chatPinIndex(rendered))
    }

    @Test
    fun pinIndexIsMinusOneWithoutAUserMessage() {
        assertEquals(-1, chatPinIndex(emptyList()))
        assertEquals(-1, chatPinIndex(listOf(assistant("a1", "x"), status("s1"))))
    }

    @Test
    fun jumpButtonRules() {
        assertFalse(chatAwayFromBottom(totalItems = 0, lastVisibleIndex = null))
        assertTrue(chatAwayFromBottom(totalItems = 5, lastVisibleIndex = null))
        assertTrue(chatAwayFromBottom(totalItems = 5, lastVisibleIndex = 3))
        assertFalse(chatAwayFromBottom(totalItems = 5, lastVisibleIndex = 4))
        assertFalse(chatAwayFromBottom(totalItems = 1, lastVisibleIndex = 0))
    }
}
