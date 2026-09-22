package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway sends message.complete before it clears the session's `running`
 * flag, and a reconnect/gap recovery reads a snapshot over RPC that is not
 * ordered with the event stream. Whichever of the two lands first, the open
 * chat has to end up idle with the reply on screen.
 */
class RecoveryTurnEndOrderTest {

    private class FakeGatewayClient : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("live-1"))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        val methods = mutableListOf<String>()
        var handler: suspend (String) -> JsonElement = { JsonObject(emptyMap()) }

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement {
            methods += method
            return handler(method)
        }
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    private fun snapshot(running: Boolean, reply: String?) = buildJsonObject {
        put("session_id", "live-1")
        put("stored_session_id", "stored-1")
        put("running", running)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("id", "u1"); put("role", "user"); put("content", "hi") })
            if (reply != null) {
                add(buildJsonObject { put("id", "a1"); put("role", "assistant"); put("content", reply) })
            }
        })
    }

    private val gateway = FakeGatewayClient()

    private fun delegate(scope: kotlinx.coroutines.CoroutineScope) =
        ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), scope) {}

    /** The open chat as it looks mid-turn: a reply streaming, the stop button up. */
    private fun midTurn() = MutableStateFlow(
        ChatUiState(
            activeSessionId = "live-1",
            activeSessionKey = "stored-1",
            isSending = true,
            messages = listOf(
                ChatMessage.User(id = "u1", timestamp = 0L, text = "hi"),
                ChatMessage.Assistant(id = "s1", timestamp = 0L, text = "Hel", isStreaming = true, reasoning = null),
            ),
        ),
    )

    private fun ChatUiState.lastReply() = messages.filterIsInstance<ChatMessage.Assistant>().lastOrNull()

    @Test
    fun `completion landing while the snapshot is in flight leaves the chat idle with the reply`() = runTest {
        val sessions = delegate(this)
        val state = midTurn()
        var call = 0
        gateway.handler = {
            call++
            if (call == 1) {
                // message.complete is handled while this request is out; the
                // server read its snapshot before storing the reply.
                sessions.onTurnEnded()
                snapshot(running = true, reply = null)
            } else {
                // Still inside the gap where the reply is stored but `running`
                // has not been cleared yet.
                snapshot(running = true, reply = "Hello there")
            }
        }

        val running = sessions.recover(state, "live-1", "stored-1")

        assertEquals(false, running)
        assertFalse(state.value.isSending)
        assertEquals("Hello there", state.value.lastReply()?.text)
        assertFalse(state.value.messages.any { it is ChatMessage.Assistant && it.isStreaming })
        assertEquals(2, gateway.methods.count { it == GatewayMethods.SESSION_RESUME })
    }

    @Test
    fun `completion arriving after the snapshot also ends idle with the reply`() = runTest {
        val sessions = delegate(this)
        val state = midTurn()
        gateway.handler = { snapshot(running = true, reply = null) }

        // Snapshot first: the turn is genuinely still running.
        assertEquals(true, sessions.recover(state, "live-1", "stored-1"))
        assertTrue(state.value.isSending)

        // Then message.complete: the view model sees recoverOnTurnEnd and
        // refetches with turnEnded = true.
        sessions.onTurnEnded()
        gateway.handler = { snapshot(running = true, reply = "Hello there") }
        val running = sessions.recover(state, "live-1", "stored-1", turnEnded = true)

        assertEquals(false, running)
        assertFalse(state.value.isSending)
        assertEquals("Hello there", state.value.lastReply()?.text)
    }

    @Test
    fun `a turn still running with no completion seen stays running`() = runTest {
        val sessions = delegate(this)
        val state = midTurn()
        gateway.handler = { snapshot(running = true, reply = null) }

        assertEquals(true, sessions.recover(state, "live-1", "stored-1"))
        assertTrue(state.value.isSending)
        assertEquals(1, gateway.methods.size)
    }

    @Test
    fun `a completion from before the recovery started does not count as newer`() = runTest {
        val sessions = delegate(this)
        val state = midTurn()
        sessions.onTurnEnded()
        gateway.handler = { snapshot(running = true, reply = null) }

        assertEquals(true, sessions.recover(state, "live-1", "stored-1"))
        assertEquals(1, gateway.methods.size)
    }
}
