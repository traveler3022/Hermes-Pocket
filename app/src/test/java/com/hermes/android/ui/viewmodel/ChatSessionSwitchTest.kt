package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Opening a chat from a notification races the screen's own pick on a cold
 * start. The answer for a chat that is no longer the one being opened must not
 * land, and giving up on it must not read as an error.
 */
class ChatSessionSwitchTest {

    private class FakeGatewayClient : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(null))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        var handler: suspend (String) -> JsonElement = { JsonObject(emptyMap()) }

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

    private val gateway = FakeGatewayClient()

    private fun history(text: String) = buildJsonObject {
        put("messages", buildJsonArray {
            add(buildJsonObject { put("id", text); put("role", "assistant"); put("content", text) })
        })
    }

    @Test
    fun `history of a chat that is no longer open does not replace the open one`() = runTest {
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        val answer = CompletableDeferred<JsonElement>()
        gateway.handler = { answer.await() }
        val state = MutableStateFlow(ChatUiState(activeSessionId = "most-recent"))

        val load = launch { sessions.loadHistory(state, "most-recent") }
        runCurrent()
        // The notification's chat opens while that history is still out.
        val notified = listOf(ChatMessage.User(id = "n1", timestamp = 0L, text = "from the notification"))
        state.value = state.value.copy(activeSessionId = "notified", messages = notified)
        answer.complete(history("other chat"))
        load.join()

        assertEquals("notified", state.value.activeSessionId)
        assertEquals(notified, state.value.messages)
    }

    @Test
    fun `a superseded resume neither lands nor reports an error`() = runTest {
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        val never = CompletableDeferred<JsonElement>()
        gateway.handler = { never.await() }
        val state = MutableStateFlow(ChatUiState(activeSessionId = "notified"))

        val resume = launch { sessions.resume(state, "most-recent") }
        runCurrent()
        resume.cancel()
        resume.join()

        assertEquals("notified", state.value.activeSessionId)
        assertNull(state.value.errorEvent)
    }

    private fun resumed(running: Boolean) = buildJsonObject {
        put("session_id", "live-2")
        put("running", running)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("id", "u1"); put("role", "user"); put("content", "hi") })
        })
    }

    @Test
    fun `opening a chat mid-turn shows it busy and reports the turn`() = runTest {
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        gateway.handler = { resumed(running = true) }
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-1", isSending = false))

        val running = sessions.resume(state, "stored-2")

        assertEquals(true, running)
        assertEquals("live-2", state.value.activeSessionId)
        assertEquals(true, state.value.isSending)
    }

    @Test
    fun `opening an idle chat from a busy one leaves the busy state behind`() = runTest {
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        gateway.handler = { resumed(running = false) }
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-1", isSending = true))

        val running = sessions.resume(state, "stored-2")

        assertEquals(false, running)
        assertEquals(false, state.value.isSending)
    }

    @Test
    fun `a chat that cannot be opened reports no turn state`() = runTest {
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        gateway.handler = { error("gateway unreachable") }
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-1", isSending = true))

        assertNull(sessions.resume(state, "stored-2"))
        assertEquals("live-1", state.value.activeSessionId)
    }
}
