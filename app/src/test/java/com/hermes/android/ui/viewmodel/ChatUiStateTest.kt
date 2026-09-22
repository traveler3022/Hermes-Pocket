package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun `User message has correct fields`() {
        val msg = ChatMessage.User(id = "u1", timestamp = 1000L, text = "hello")
        assertEquals("u1", msg.id)
        assertEquals(1000L, msg.timestamp)
        assertEquals("hello", msg.text)
        assertTrue(msg.attachments.isEmpty())
    }

    @Test
    fun `Assistant message streaming flag`() {
        val streaming = ChatMessage.Assistant(id = "a1", timestamp = 1000L, text = "partial", isStreaming = true, reasoning = null)
        val done = ChatMessage.Assistant(id = "a2", timestamp = 2000L, text = "complete", isStreaming = false, reasoning = "thought")
        assertTrue(streaming.isStreaming)
        assertFalse(done.isStreaming)
        assertEquals("thought", done.reasoning)
        assertNull(streaming.reasoning)
    }

    @Test
    fun `ToolCall message fields`() {
        val tc = ChatMessage.ToolCall(id = "t1", timestamp = 1000L, toolName = "search", argsText = "{q: test}", resultText = "found", error = null, isRunning = false, durationS = 1.5)
        assertEquals("search", tc.toolName)
        assertEquals(1.5, tc.durationS!!, 0.01)
        assertFalse(tc.isRunning)
        assertNull(tc.error)
    }

    @Test
    fun `ToolCall running state`() {
        val tc = ChatMessage.ToolCall(id = "t1", timestamp = 1000L, toolName = "search", argsText = null, resultText = null, error = null, isRunning = true, durationS = null)
        assertTrue(tc.isRunning)
        assertNull(tc.durationS)
    }

    @Test
    fun `Status message error flag`() {
        val err = ChatMessage.Status(id = "s1", timestamp = 1000L, text = "failed", isError = true)
        val ok = ChatMessage.Status(id = "s2", timestamp = 2000L, text = "done", isError = false)
        assertTrue(err.isError)
        assertFalse(ok.isError)
    }

    @Test
    fun `TodoItemUi pending status`() {
        val todo = TodoItemUi(id = "todo1", content = "task", status = TodoStatus.PENDING)
        assertEquals(TodoStatus.PENDING, todo.status)
    }

    @Test
    fun `TodoStatus has all expected values`() {
        val statuses = TodoStatus.values()
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(TodoStatus.PENDING))
        assertTrue(statuses.contains(TodoStatus.IN_PROGRESS))
        assertTrue(statuses.contains(TodoStatus.COMPLETED))
        assertTrue(statuses.contains(TodoStatus.CANCELLED))
    }

    @Test
    fun `ChatConnectionState has expected values`() {
        val states = ChatConnectionState.values()
        assertTrue(states.contains(ChatConnectionState.Disconnected))
        assertTrue(states.contains(ChatConnectionState.Connecting))
        assertTrue(states.contains(ChatConnectionState.Connected))
        assertTrue(states.contains(ChatConnectionState.Reconnecting))
        assertTrue(states.contains(ChatConnectionState.Failed))
    }

    @Test
    fun `ErrorEvent subtypes`() {
        val warning = ErrorEvent.Warning(message = "reconnecting", autoDismissMs = 4000)
        val error = ErrorEvent.Error(message = "send failed", autoDismissMs = 6000)
        val critical = ErrorEvent.Critical(message = "connection dead", autoDismissMs = 0)
        assertEquals("reconnecting", warning.message)
        assertEquals(4000, warning.autoDismissMs)
        assertEquals("send failed", error.message)
        assertEquals(0, critical.autoDismissMs)
    }

    @Test
    fun `SessionItem with optional fields`() {
        val session = SessionItem(id = "s1", title = "My Session", lastMessagePreview = "hi", updatedAt = 1000L)
        assertEquals("s1", session.id)
        assertNull(session.messageCount)
    }

    @Test
    fun `SessionItem with messageCount`() {
        val session = SessionItem(id = "s1", title = "My Session", lastMessagePreview = null, updatedAt = 1000L, messageCount = 42)
        assertEquals(42, session.messageCount)
    }

    @Test
    fun `SlashCommandSuggestion fields`() {
        val sugg = SlashCommandSuggestion(command = "/search", description = "Search web")
        assertEquals("/search", sugg.command)
        assertEquals("Search web", sugg.description)
    }

    @Test
    fun `PendingAttachment fields`() {
        val att = PendingAttachment(name = "test.txt", isImage = false)
        assertEquals("test.txt", att.name)
        assertFalse(att.isImage)
        assertNull(att.gatewayPath)
    }

    @Test
    fun `ChatUiState default values`() {
        val state = ChatUiState()
        assertTrue(state.messages.isEmpty())
        assertEquals(ChatConnectionState.Disconnected, state.connectionState)
        assertFalse(state.isSending)
        assertEquals("", state.inputText)
    }
}
