package com.hermes.android.gateway

import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stdio gateway writes the frames of Group Chat member sessions to the app's one
 * channel (they have no client of their own). The sequence below is what a 0.21.4
 * gateway wrote for a member turn: session.info (titled `Group: <room>`) first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSessionFrameTest {

    private val client = OkHttpGatewayClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, StdioGatewayHub(), mockk(relaxed = true))

    private val handleMessage = OkHttpGatewayClient::class.java
        .getDeclaredMethod("handleMessage", String::class.java, Function1::class.java)
        .apply { isAccessible = true }

    private fun receive(frame: JsonObject) {
        handleMessage.invoke(client, frame.toString(), { _: Any? -> })
    }

    private fun event(type: String, sessionId: String, payload: JsonObject = JsonObject(emptyMap())) =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "event")
            putJsonObject("params") {
                put("type", type)
                put("session_id", sessionId)
                put("payload", payload)
            }
        }

    private fun collect(scope: TestScope): MutableList<GatewayEvent> {
        val got = mutableListOf<GatewayEvent>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { client.events.toList(got) }
        return got
    }

    @Test
    fun `a member turn of a room never reaches the app`() = runTest {
        val got = collect(this)
        receive(event("session.info", "room-live", buildJsonObject {
            put("title", "Group: room-test-1")
            put("stored_session_id", "20261003_231207_c50d5c")
            put("running", true)
        }))
        receive(event("message.start", "room-live"))
        receive(event("message.delta", "room-live", buildJsonObject { put("text", "Hermes here") }))
        receive(event("message.complete", "room-live", buildJsonObject { put("text", "Hermes here") }))

        assertTrue(got.toString(), got.isEmpty())
    }

    @Test
    fun `a room member's approval is left for the room to answer`() = runTest {
        val got = collect(this)
        receive(event("session.info", "room-live", buildJsonObject { put("title", "Group: room-test-1") }))
        receive(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "srq-1")
            put("method", "approval")
            putJsonObject("params") {
                put("session_id", "room-live")
                put("command", "rm -rf build")
            }
        })

        assertTrue(got.toString(), got.isEmpty())
    }

    @Test
    fun `the user's own chats still come through`() = runTest {
        val got = collect(this)
        receive(event("session.info", "room-live", buildJsonObject { put("title", "Group: room-test-1") }))
        receive(event("session.info", "chat-1", buildJsonObject { put("title", "Groceries for the week") }))
        receive(event("message.start", "chat-1"))

        assertEquals(listOf("chat-1", "chat-1"), got.map { it.sessionId })
    }
}
