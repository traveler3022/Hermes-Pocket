package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Send when done": the per-chat queue, and when its delegate lets a message go. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSendQueueTest {

    private fun item(id: String) = QueuedSend(id, "text $id", "out $id")

    @Test
    fun `messages leave one at a time in the order they were queued`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("chat-a"), item("1"))
        q.enqueue(listOf("chat-a"), item("2"))
        q.enqueue(listOf("chat-a"), item("3"))
        assertEquals(listOf("1", "2", "3"), q.items(listOf("chat-a")).map { it.id })
        assertEquals("1", q.onTurnEnded(listOf("chat-a"), TurnOutcome.COMPLETE)?.id)
        assertEquals("2", q.onTurnEnded(listOf("chat-a"), TurnOutcome.COMPLETE)?.id)
        assertEquals("3", q.onTurnEnded(listOf("chat-a"), TurnOutcome.COMPLETE)?.id)
        assertNull(q.onTurnEnded(listOf("chat-a"), TurnOutcome.COMPLETE))
        assertTrue(q.isEmpty())
    }

    @Test
    fun `a turn of another chat sends nothing from this one`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("chat-a"), item("1"))
        assertNull(q.onTurnEnded(listOf("chat-b", "live-b"), TurnOutcome.COMPLETE))
        assertEquals(listOf("1"), q.items(listOf("chat-a")).map { it.id })
    }

    @Test
    fun `a stopped or failed turn keeps the queue`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("chat-a"), item("1"))
        assertNull(q.onTurnEnded(listOf("chat-a"), TurnOutcome.INTERRUPTED))
        assertNull(q.onTurnEnded(listOf("chat-a"), TurnOutcome.ERROR))
        assertEquals(listOf("1"), q.items(listOf("chat-a")).map { it.id })
        // The next turn that finishes normally lets it go.
        assertEquals("1", q.onTurnEnded(listOf("chat-a"), TurnOutcome.COMPLETE)?.id)
    }

    @Test
    fun `a chat queued under its live id is found once its stored key is known`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("live-a"), item("1"))
        q.enqueue(listOf("stored-a", "live-a"), item("2"))
        assertEquals(listOf("1", "2"), q.items(listOf("stored-a", "live-a")).map { it.id })
        assertEquals("1", q.takeNext(listOf("stored-a", "live-a"))?.id)
    }

    @Test
    fun `edit and cancel take exactly that message out`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("chat-a"), item("1"))
        q.enqueue(listOf("chat-a"), item("2"))
        q.enqueue(listOf("chat-a"), item("3"))
        assertEquals("2", q.remove(listOf("chat-a"), "2")?.id)
        assertNull(q.remove(listOf("chat-a"), "2"))
        assertNull(q.remove(listOf("chat-b"), "1"))
        assertEquals(listOf("1", "3"), q.items(listOf("chat-a")).map { it.id })
    }

    @Test
    fun `a failed send goes back to the front`() {
        val q = ChatSendQueue()
        q.enqueue(listOf("chat-a"), item("1"))
        q.enqueue(listOf("chat-a"), item("2"))
        val first = checkNotNull(q.takeNext(listOf("chat-a")))
        q.pushFront(listOf("chat-a"), first)
        assertEquals(listOf("1", "2"), q.items(listOf("chat-a")).map { it.id })
    }

    @Test
    fun `turn status maps to an outcome`() {
        assertEquals(TurnOutcome.COMPLETE, turnOutcomeOf(null))
        assertEquals(TurnOutcome.COMPLETE, turnOutcomeOf("complete"))
        assertEquals(TurnOutcome.INTERRUPTED, turnOutcomeOf("interrupted"))
        assertEquals(TurnOutcome.INTERRUPTED, turnOutcomeOf("cancelled"))
        assertEquals(TurnOutcome.ERROR, turnOutcomeOf("error"))
        assertEquals(TurnOutcome.ERROR, turnOutcomeOf("failed"))
    }

    // ── The delegate ────────────────────────────────────────────────────

    private class RecordingGateway : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected("live-a"))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()
        val requests = mutableListOf<Pair<String, Map<String, JsonElement>>>()
        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement {
            requests += method to params
            return JsonObject(emptyMap())
        }
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    @Test
    fun `the open chat sends its queue through the screen when its turn completes`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-a", activeSessionKey = "stored-a", isSending = true))
        val sentOpen = mutableListOf<Pair<String, String>>()
        val gateway = RecordingGateway()
        val d = ChatQueueDelegate(gateway, backgroundScope, state) { live, item -> sentOpen += live to item.outgoing }

        d.enqueue("one", "out one", emptyList())
        d.enqueue("two", "out two", emptyList())
        assertEquals(listOf("one", "two"), state.value.queuedSends.map { it.text })
        assertTrue(sentOpen.isEmpty())

        d.onTurnEnded("live-a", listOf("stored-a", "live-a"), "interrupted")
        d.onTurnEnded("live-a", listOf("stored-a", "live-a"), "error")
        assertTrue(sentOpen.isEmpty())

        d.onTurnEnded("live-b", listOf("stored-b", "live-b"), "complete")
        assertTrue(sentOpen.isEmpty())

        d.onTurnEnded("live-a", listOf("stored-a", "live-a"), "complete")
        assertEquals(listOf("live-a" to "out one"), sentOpen)
        assertEquals(listOf("two"), state.value.queuedSends.map { it.text })
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun `a chat in the background still sends, queued so it never interrupts`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-a", activeSessionKey = "stored-a", isSending = true))
        val gateway = RecordingGateway()
        val d = ChatQueueDelegate(gateway, backgroundScope, state) { _, _ -> error("not the open chat") }
        d.enqueue("later", "out later", emptyList())

        // The user opens another chat; the first one finishes in the background.
        state.value = ChatUiState(activeSessionId = "live-b", activeSessionKey = "stored-b")
        assertTrue(state.value.queuedSends.isEmpty())
        d.onTurnEnded("live-a", listOf("stored-a", "live-a"), "complete")

        val (method, params) = gateway.requests.single()
        assertEquals("prompt.submit", method)
        assertEquals(JsonPrimitive("out later"), params["text"])
        assertEquals(JsonPrimitive("live-a"), params["session_id"])
        assertEquals(JsonPrimitive(true), params["queued"])
    }

    @Test
    fun `queued after the turn already ended it goes at once`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-a", activeSessionKey = "stored-a", isSending = false))
        val sentOpen = mutableListOf<String>()
        val d = ChatQueueDelegate(RecordingGateway(), backgroundScope, state) { _, item -> sentOpen += item.text }
        d.enqueue("now", "out now", emptyList())
        assertEquals(listOf("now"), sentOpen)
        assertTrue(state.value.queuedSends.isEmpty())
    }

    @Test
    fun `edit hands the message back and cancel drops it`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow(ChatUiState(activeSessionId = "live-a", activeSessionKey = "stored-a", isSending = true))
        val sentOpen = mutableListOf<String>()
        val d = ChatQueueDelegate(RecordingGateway(), backgroundScope, state) { _, item -> sentOpen += item.text }
        d.enqueue("one", "out one", emptyList())
        d.enqueue("two", "out two", emptyList())
        val (one, two) = state.value.queuedSends
        assertEquals("one", d.takeForEdit(one.id)?.text)
        d.cancel(two.id)
        assertTrue(state.value.queuedSends.isEmpty())
        d.onTurnEnded("live-a", listOf("stored-a", "live-a"), "complete")
        assertTrue(sentOpen.isEmpty())
    }
}
