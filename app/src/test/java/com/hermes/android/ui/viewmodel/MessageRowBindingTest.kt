package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A message sent and a reply streamed in the open chat learn the rows Hermes stored them
 * as: prompt.submit names the user's (`user_row_id`), message.complete the reply's
 * (`persisted_turn.final_assistant_row_id`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRowBindingTest {

    private class FakeGateway : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("live-1"))
        val eventFlow = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<GatewayEvent> = eventFlow
        var submitResult: JsonObject = JsonObject(emptyMap())

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement = if (method == GatewayMethods.PROMPT_SUBMIT) submitResult else JsonObject(emptyMap())
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

    private fun ChatViewModel.sent() = uiState.value.messages.filterIsInstance<ChatMessage.User>().single()
    private fun ChatViewModel.reply() = uiState.value.messages.filterIsInstance<ChatMessage.Assistant>().single()

    @Test
    fun `a sent message takes the row prompt submit names for it`() = runTest(main) {
        gateway.submitResult = buildJsonObject { put("status", "streaming"); put("user_row_id", 41) }
        val vm = viewModel()

        vm.updateInputText("hello")
        vm.sendMessage()

        assertEquals(41L, vm.sent().rowId)
    }

    @Test
    fun `a message Hermes has not vouched for keeps no row`() = runTest(main) {
        gateway.submitResult = buildJsonObject { put("status", "queued") }
        val vm = viewModel()

        vm.updateInputText("hello")
        vm.sendMessage()

        assertNull(vm.sent().rowId)
    }

    @Test
    fun `the reply that ends the turn takes its stored row`() = runTest(main) {
        val vm = viewModel()

        gateway.eventFlow.emit(GatewayEvent.MessageStart("live-1"))
        gateway.eventFlow.emit(GatewayEvent.MessageDelta("live-1", "Done.", null))
        advanceTimeBy(200)
        gateway.eventFlow.emit(
            GatewayEvent.MessageComplete("live-1", "Done.", null, null, null, finalAssistantRowId = 42),
        )

        assertEquals(42L, vm.reply().rowId)
    }

    @Test
    fun `a turn end without a stored row leaves the reply without one`() = runTest(main) {
        val vm = viewModel()

        gateway.eventFlow.emit(GatewayEvent.MessageStart("live-1"))
        gateway.eventFlow.emit(GatewayEvent.MessageDelta("live-1", "Done.", null))
        advanceTimeBy(200)
        gateway.eventFlow.emit(GatewayEvent.MessageComplete("live-1", "Done.", null, null, null))

        assertNull(vm.reply().rowId)
    }
}
