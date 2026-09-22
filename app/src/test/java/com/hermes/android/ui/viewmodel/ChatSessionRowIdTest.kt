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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A retry/regenerate is an ordinal-only truncate on the wire unless the target user
 * message's durable row id rides along with it — the server rejects an ordinal with
 * no row id as RPC 4004. session.history is where that row id is learned.
 */
class ChatSessionRowIdTest {

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

    private fun historyWithRowIds() = buildJsonObject {
        put("messages", buildJsonArray {
            add(buildJsonObject { put("id", "u1"); put("role", "user"); put("content", "first"); put("row_id", 101) })
            add(buildJsonObject { put("id", "a1"); put("role", "assistant"); put("content", "reply one") })
            add(buildJsonObject { put("id", "u2"); put("role", "user"); put("content", "second"); put("row_id", 104) })
            add(buildJsonObject { put("id", "a2"); put("role", "assistant"); put("content", "reply two") })
        })
    }

    @Test
    fun `loadHistory stamps each user message with its durable row id`() = runTest {
        val gateway = FakeGatewayClient { historyWithRowIds() }
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}
        val state = MutableStateFlow(ChatUiState(activeSessionId = "s1"))

        sessions.loadHistory(state, "s1")

        val users = state.value.messages.filterIsInstance<ChatMessage.User>()
        assertEquals(listOf(101L, 104L), users.map { it.rowId })
    }

    @Test
    fun `resolveUserRowId returns the nth user turn's row id`() = runTest {
        val gateway = FakeGatewayClient { historyWithRowIds() }
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}

        assertEquals(101L, sessions.resolveUserRowId("s1", 0))
        assertEquals(104L, sessions.resolveUserRowId("s1", 1))
    }

    @Test
    fun `resolveUserRowId gives up quietly when the gateway fails`() = runTest {
        val gateway = FakeGatewayClient { error("gateway unreachable") }
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}

        assertNull(sessions.resolveUserRowId("s1", 0))
    }
}
