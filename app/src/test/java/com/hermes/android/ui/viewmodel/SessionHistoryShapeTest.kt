package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * session.history as `tui_gateway/session_history.py::_history_to_messages`
 * sends it: the words under `text`, tool rows with a name and args object.
 */
class SessionHistoryShapeTest {

    private class FakeGatewayClient(var handler: suspend (String) -> JsonElement) : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(null))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement = handler(method)
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    private fun serverHistory() = buildJsonObject {
        put("count", 4)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "user"); put("text", "list my files"); put("row_id", 11) })
            add(buildJsonObject { put("role", "assistant"); put("text", "Checking."); put("reasoning", "use ls") })
            add(buildJsonObject {
                put("role", "tool"); put("name", "terminal"); put("context", "ls")
                putJsonObject("args") { put("command", "ls") }
            })
            add(buildJsonObject { put("role", "assistant"); put("text", "Two files.") })
        })
    }

    private suspend fun kotlinx.coroutines.test.TestScope.load(history: JsonElement, onScreen: List<ChatMessage> = emptyList()): ChatUiState {
        val gateway = FakeGatewayClient { history }
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        val state = MutableStateFlow(ChatUiState(activeSessionId = "s1", messages = onScreen))
        sessions.loadHistory(state, "s1")
        return state.value
    }

    @Test
    fun `the server's text field is what each bubble shows, in order`() = runTest {
        val messages = load(serverHistory()).messages

        assertEquals(
            listOf("user", "assistant", "tool", "assistant"),
            messages.map {
                when (it) {
                    is ChatMessage.User -> "user"
                    is ChatMessage.Assistant -> "assistant"
                    is ChatMessage.ToolCall -> "tool"
                    else -> "other"
                }
            },
        )
        assertEquals("list my files", (messages[0] as ChatMessage.User).text)
        assertEquals(11L, (messages[0] as ChatMessage.User).rowId)
        assertEquals("Checking.", (messages[1] as ChatMessage.Assistant).text)
        assertEquals("Two files.", (messages[3] as ChatMessage.Assistant).text)
    }

    @Test
    fun `reasoning stays with the reply it belongs to`() = runTest {
        val messages = load(serverHistory()).messages.filterIsInstance<ChatMessage.Assistant>()

        assertEquals(listOf("use ls", null), messages.map { it.reasoning })
    }

    @Test
    fun `a tool row is a finished tool card under its own name`() = runTest {
        val tool = load(serverHistory()).messages.filterIsInstance<ChatMessage.ToolCall>().single()

        assertEquals("terminal", tool.toolName)
        assertFalse(tool.isRunning)
    }

    @Test
    fun `a tool row shows the arguments the server sent as an object`() = runTest {
        val tool = load(serverHistory()).messages.filterIsInstance<ChatMessage.ToolCall>().single()

        assertEquals("""{"command":"ls"}""", tool.argsText)
    }

    @Test
    fun `a timestamp in fractional seconds keeps its time`() = runTest {
        val history = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("text", "hi"); put("timestamp", 1727000000.25) })
            })
        }

        assertEquals(1_727_000_000_250L, load(history).messages.single().timestamp)
    }

    @Test
    fun `nothing from history is ever still streaming`() = runTest {
        val messages = load(serverHistory()).messages.filterIsInstance<ChatMessage.Assistant>()

        assertFalse(messages.any { it.isStreaming })
    }

    @Test
    fun `an empty history keeps what is on screen`() = runTest {
        val onScreen = listOf(ChatMessage.User(id = "u", timestamp = 0L, text = "hi"))

        val messages = load(buildJsonObject { put("count", 0); put("messages", buildJsonArray {}) }, onScreen).messages

        assertEquals(onScreen, messages)
    }

    @Test
    fun `an answer that is not a history keeps what is on screen`() = runTest {
        val onScreen = listOf(ChatMessage.User(id = "u", timestamp = 0L, text = "hi"))

        assertEquals(onScreen, load(JsonObject(emptyMap()), onScreen).messages)
    }
}
