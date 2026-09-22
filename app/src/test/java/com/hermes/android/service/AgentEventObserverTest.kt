package com.hermes.android.service

import com.hermes.android.data.TaskCompletionTracker
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The completion watcher must only announce a turn the server has actually
 * finished. `session.active_list` reports a busy session as "working" (or
 * "waiting"), and message.complete goes out before the server clears
 * `running`, so a list read around the end of a turn still says busy.
 */
class AgentEventObserverTest {

    private class FakeGatewayClient : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("s1"))
        val eventFlow = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<GatewayEvent> = eventFlow

        var activeList: suspend () -> JsonElement = { rows() }
        var activeListCalls = 0

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement {
            check(method == GatewayMethods.SESSION_ACTIVE_LIST) { "unexpected $method" }
            activeListCalls++
            return activeList()
        }
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private companion object {
        fun rows(vararg statusById: Pair<String, String>) = buildJsonObject {
            put("sessions", buildJsonArray {
                for ((id, status) in statusById) {
                    add(buildJsonObject {
                        put("id", id)
                        put("status", status)
                        put("title", "Chat $id")
                        put("preview", "Hello there")
                    })
                }
            })
        }

        fun complete(sid: String) = GatewayEvent.MessageComplete(
            sessionId = sid, text = "Hello there", rendered = null, reasoning = null, usage = null,
        )
    }

    private val gateway = FakeGatewayClient()
    private val notifier = mockk<AgentActivityNotifier>(relaxed = true).also {
        every { it.taskFinishedText() } returns "Task finished"
    }

    /** App in the background: every completion the observer believes in is announced. */
    private fun TestScope.startObserver(): AgentEventObserver =
        AgentEventObserver(gateway, AppForegroundState(), notifier, mockk<TaskCompletionTracker>(relaxed = true))
            .also { it.start(backgroundScope); runCurrent() }

    @Test
    fun `a session the server reports as working is not announced as done`() = runTest {
        val observer = startObserver()
        gateway.activeList = { rows("s1" to "working") }

        gateway.eventFlow.emit(GatewayEvent.MessageStart("s1"))
        advanceTimeBy(20_000)
        runCurrent()

        verify(exactly = 0) { notifier.showTurnComplete(any(), any(), any()) }
        assertNotNull(observer.work.value)
    }

    @Test
    fun `a session blocked on a question is still working`() = runTest {
        val observer = startObserver()
        gateway.activeList = { rows("s1" to "waiting") }

        gateway.eventFlow.emit(GatewayEvent.MessageStart("s1"))
        advanceTimeBy(20_000)
        runCurrent()

        verify(exactly = 0) { notifier.showTurnComplete(any(), any(), any()) }
        assertNotNull(observer.work.value)
    }

    @Test
    fun `a turn that finished while its event was lost is announced once`() = runTest {
        val observer = startObserver()
        gateway.activeList = { rows("s1" to "idle") }

        gateway.eventFlow.emit(GatewayEvent.MessageStart("s1"))
        advanceTimeBy(20_000)
        runCurrent()

        verify(exactly = 1) { notifier.showTurnComplete("s1", "Hello there", any()) }
        assertNull(observer.work.value)
    }

    @Test
    fun `a completion seen during the sync is not watched again or announced twice`() = runTest {
        val observer = startObserver()
        gateway.eventFlow.emit(GatewayEvent.MessageStart("s1"))
        runCurrent()
        gateway.activeList = {
            // message.complete arrives while active_list is out, and the server
            // read the list before clearing `running`.
            gateway.eventFlow.emit(complete("s1"))
            yield()
            rows("s1" to "working")
        }

        advanceTimeBy(20_000)
        runCurrent()

        verify(exactly = 1) { notifier.showTurnComplete(any(), any(), any()) }
        assertNull(observer.work.value)
        assertEquals(1, gateway.activeListCalls)
    }
}
