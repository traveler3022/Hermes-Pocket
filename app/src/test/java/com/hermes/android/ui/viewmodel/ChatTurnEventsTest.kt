package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.service.AppForegroundState
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The open chat driven by the turn events tui_gateway sends: a failed turn ends
 * with `error` alone, and `thinking.delta` is the agent's status line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTurnEventsTest {

    private class FakeGateway : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("live-1"))
        val eventFlow = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<GatewayEvent> = eventFlow

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement = JsonObject(emptyMap())
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    private val main = UnconfinedTestDispatcher()
    private val gateway = FakeGateway()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChatViewModel(
        gateway, SessionRepository(gateway, NoTasks), mockk(relaxed = true),
        mockk(relaxed = true), AppForegroundState(), mockk(relaxed = true),
    )

    private fun ChatViewModel.replies() = uiState.value.messages.filterIsInstance<ChatMessage.Assistant>()

    @Test
    fun `a turn that fails keeps what it wrote and stops streaming`() = runTest(main) {
        val vm = viewModel()
        assertEquals("live-1", vm.uiState.value.activeSessionId)

        gateway.eventFlow.emit(GatewayEvent.MessageStart("live-1"))
        gateway.eventFlow.emit(GatewayEvent.MessageDelta("live-1", "Hel", null))
        advanceTimeBy(200)
        gateway.eventFlow.emit(GatewayEvent.Error("live-1", "boom"))

        assertEquals(listOf("Hel"), vm.replies().map { it.text })
        assertFalse(vm.replies().any { it.isStreaming })
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun `a turn that fails before any word leaves no empty bubble`() = runTest(main) {
        val vm = viewModel()

        gateway.eventFlow.emit(GatewayEvent.MessageStart("live-1"))
        gateway.eventFlow.emit(GatewayEvent.Error("live-1", "agent init failed"))

        assertTrue(vm.replies().isEmpty())
    }

    @Test
    fun `the thinking line is a status, never part of the reasoning`() = runTest(main) {
        val vm = viewModel()

        gateway.eventFlow.emit(GatewayEvent.MessageStart("live-1"))
        gateway.eventFlow.emit(GatewayEvent.ThinkingDelta("live-1", "(◕‿◕) pondering..."))
        gateway.eventFlow.emit(GatewayEvent.ReasoningDelta("live-1", "step one"))
        advanceTimeBy(200)

        assertEquals("step one", vm.replies().single().reasoning)
        assertEquals("(◕‿◕) pondering...", vm.uiState.value.thinkingStatus)

        // The server clears it once tokens flow.
        gateway.eventFlow.emit(GatewayEvent.ThinkingDelta("live-1", ""))
        assertEquals("", vm.uiState.value.thinkingStatus)

        gateway.eventFlow.emit(GatewayEvent.ThinkingDelta("live-1", "waiting for the provider"))
        gateway.eventFlow.emit(GatewayEvent.MessageComplete("live-1", "Done.", null, null, null))
        assertEquals("", vm.uiState.value.thinkingStatus)
        assertEquals("step one", vm.replies().single().reasoning)
    }

    @Test
    fun `a sub-agent is one card, from spawn to its result`() = runTest(main) {
        val vm = viewModel()
        fun frame(stage: String, vararg fields: Pair<String, String>) = GatewayEvent.SubagentEvent(
            "live-1", stage, mapOf("subagent_id" to JsonPrimitive("sa-1")) + fields.associate { (k, v) -> k to JsonPrimitive(v) },
        )

        gateway.eventFlow.emit(frame("spawn_requested", "goal" to "read the logs"))
        gateway.eventFlow.emit(frame("start", "goal" to "read the logs"))
        gateway.eventFlow.emit(frame("thinking", "text" to "opening app.log"))
        gateway.eventFlow.emit(frame("complete", "summary" to "3 errors found"))

        val card = vm.uiState.value.messages.filterIsInstance<ChatMessage.SubagentCard>().single()
        assertTrue(card.isComplete)
        assertEquals("3 errors found", card.text)
    }

    @Test
    fun `another chat's status does not reach the open one`() = runTest(main) {
        val vm = viewModel()

        gateway.eventFlow.emit(GatewayEvent.ThinkingDelta("other", "(◕‿◕) pondering..."))

        assertEquals("", vm.uiState.value.thinkingStatus)
    }
}
