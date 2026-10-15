package com.hermes.android.gateway

import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * The reconnect auto-resume target ([OkHttpGatewayClient]'s `lastSessionId`)
 * belongs to the user's chat. Task Desk launches create their sessions with
 * `trackSession = false` precisely so a background task never becomes that
 * target ("The created session is deliberately NOT tracked as the reconnect
 * auto-resume target" — [com.hermes.android.data.SessionRepository.launchTask]).
 *
 * The response path honors that via `nonTrackingRequestIds`. These tests pin
 * the same contract on the EVENT path: a running task streams message/status
 * events carrying its own session id, and those events must not hijack the
 * resume target either — otherwise, after a reconnect with no chat open, the
 * task's session gets adopted as the user's open chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeTargetTrackingTest {

    private val client = OkHttpGatewayClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, StdioGatewayHub(), mockk(relaxed = true))

    // handleMessage is private: frames only reach it from a live socket.
    private val handleMessage = OkHttpGatewayClient::class.java
        .getDeclaredMethod("handleMessage", String::class.java, Function1::class.java)
        .apply { isAccessible = true }

    private fun receive(frame: JsonObject) {
        handleMessage.invoke(client, frame.toString(), { _: Any? -> })
    }

    private fun rpcResponse(id: Long, sessionId: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id.toString())
        putJsonObject("result") {
            put("session_id", sessionId)
            put("stored_session_id", "db-$sessionId")
        }
    }

    private fun event(type: String, sessionId: String, payload: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "event")
        putJsonObject("params") {
            put("type", type)
            put("session_id", sessionId)
            put("seq", 1)
            put("payload", payload)
        }
    }

    private fun field(name: String): Field = OkHttpGatewayClient::class.java
        .getDeclaredField(name).apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun pendingRequests(): ConcurrentHashMap<Long, CompletableDeferred<kotlinx.serialization.json.JsonElement>> =
        field("pendingRequests").get(client) as ConcurrentHashMap<Long, CompletableDeferred<kotlinx.serialization.json.JsonElement>>

    @Suppress("UNCHECKED_CAST")
    private fun nonTrackingRequestIds(): MutableSet<Long> =
        field("nonTrackingRequestIds").get(client) as MutableSet<Long>

    private fun lastSessionId(): String? = field("lastSessionId").get(client) as String?

    /** Registers request [id] as pending, [tracked] controlling session tracking. */
    private fun registerRequest(id: Long, tracked: Boolean) {
        pendingRequests()[id] = CompletableDeferred()
        if (!tracked) nonTrackingRequestIds() += id
    }

    private fun collect(scope: TestScope): MutableList<GatewayEvent> {
        val got = mutableListOf<GatewayEvent>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { client.events.toList(got) }
        return got
    }

    @Test
    fun `events from an untracked task session do not steal the reconnect resume target`() = runTest {
        val got = collect(this)
        // A Task Desk launch: session.create answered with the task's live id,
        // deliberately untracked (trackSession = false).
        registerRequest(id = 41, tracked = false)
        receive(rpcResponse(id = 41, sessionId = "task9"))
        assertEquals(null, lastSessionId())

        // The task runs and streams its own events, exactly like any turn.
        receive(event("status.update", "task9", buildJsonObject { put("status", "running") }))
        receive(event("message.delta", "task9", buildJsonObject { put("delta", "working") }))
        receive(event("message.complete", "task9", buildJsonObject { put("text", "done") }))

        // The resume target must still be nothing — not the task session.
        assertEquals(null, lastSessionId())
        // And the events themselves still flow to observers (task badges, notifications).
        assertTrue(got.any { it is GatewayEvent.MessageDelta })
        assertTrue(got.any { it is GatewayEvent.MessageComplete })
    }

    @Test
    fun `a tracked session's events still refresh the resume target`() = runTest {
        collect(this)
        // The user's chat: session.create tracked, so the response adopts it.
        registerRequest(id = 7, tracked = true)
        receive(rpcResponse(id = 7, sessionId = "chat5"))
        assertEquals("chat5", lastSessionId())

        // Its events keep the target fresh (e.g. a reply sent from a
        // notification continues in this session).
        receive(event("message.delta", "chat5", buildJsonObject { put("delta", "hi") }))
        assertEquals("chat5", lastSessionId())
    }

    @Test
    fun `opening an untracked session as the user's chat re-enables event tracking`() = runTest {
        collect(this)
        // Task Desk launch, untracked.
        registerRequest(id = 41, tracked = false)
        receive(rpcResponse(id = 41, sessionId = "task9"))
        receive(event("message.delta", "task9", buildJsonObject { put("delta", "working") }))
        assertEquals(null, lastSessionId())

        // The user opens the task's transcript in the chat (Tasks → open in
        // chat): a tracked session.activate / attach response for task9.
        registerRequest(id = 42, tracked = true)
        receive(rpcResponse(id = 42, sessionId = "task9"))
        assertEquals("task9", lastSessionId())

        // Now task9 IS the user's open chat; its events legitimately refresh it.
        receive(event("message.delta", "task9", buildJsonObject { put("delta", "stream") }))
        assertEquals("task9", lastSessionId())

        // Another chat's event moves the target (a tracked session, e.g. the
        // user replied from a notification elsewhere)...
        receive(event("message.delta", "chat5", buildJsonObject { put("delta", "elsewhere") }))
        assertEquals("chat5", lastSessionId())
        // ...but the OPEN chat's next event must win it back: task9 was
        // re-enabled by the tracked attach, so it must not stay poisoned.
        receive(event("message.delta", "task9", buildJsonObject { put("delta", "back") }))
        assertEquals("task9", lastSessionId())
    }

    @Test
    fun `an untracked response never adopts the resume target`() = runTest {
        collect(this)
        // Negative control for the response path itself (already guarded by
        // nonTrackingRequestIds): an untracked response's session_id must not
        // become the resume target.
        registerRequest(id = 43, tracked = false)
        receive(rpcResponse(id = 43, sessionId = "task9"))
        assertEquals(null, lastSessionId())
        assertFalse(nonTrackingRequestIds().contains(43))
    }
}
