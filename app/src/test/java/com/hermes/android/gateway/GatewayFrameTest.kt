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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raw socket frames, in the shapes of `tui_gateway/contracts/events.py` and
 * `server_requests.py`, through the client's own frame handling to the events
 * the chat sees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayFrameTest {

    private val client = OkHttpGatewayClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, StdioGatewayHub(), mockk(relaxed = true))

    // handleMessage is private: frames only reach it from a live socket.
    private val handleMessage = OkHttpGatewayClient::class.java
        .getDeclaredMethod("handleMessage", String::class.java, Function1::class.java)
        .apply { isAccessible = true }

    private fun receive(frame: JsonObject) {
        handleMessage.invoke(client, frame.toString(), { _: Any? -> })
    }

    private fun receiveRaw(raw: String) {
        handleMessage.invoke(client, raw, { _: Any? -> })
    }

    private fun frame(type: String, sessionId: String = "s1", seq: Long? = null, payload: JsonObject) =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "event")
            putJsonObject("params") {
                put("type", type)
                put("session_id", sessionId)
                if (seq != null) put("seq", seq)
                put("payload", payload)
            }
        }

    private fun collect(scope: TestScope): MutableList<GatewayEvent> {
        val got = mutableListOf<GatewayEvent>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { client.events.toList(got) }
        return got
    }

    @Test
    fun `tool start keeps its id and shows raw args when the session is not verbose`() = runTest {
        val got = collect(this)
        receive(frame("tool.start", payload = buildJsonObject {
            put("tool_id", "t1")
            put("name", "terminal")
            put("context", "ls -la")
            putJsonObject("args") { put("command", "ls -la") }
        }))

        val start = got.single() as GatewayEvent.ToolStart
        assertEquals("t1", start.toolId)
        assertEquals("terminal", start.name)
        assertEquals("ls -la", start.context)
        assertEquals("""{"command":"ls -la"}""", start.argsText)
    }

    @Test
    fun `tool complete with a structured result still arrives`() = runTest {
        val got = collect(this)
        receive(frame("tool.complete", payload = buildJsonObject {
            put("tool_id", "t1")
            put("name", "web_search")
            putJsonObject("result") { put("hits", buildJsonArray { add("a"); add("b") }) }
            put("summary", "2 results")
            put("duration_s", 1.5)
        }))

        val done = got.single() as GatewayEvent.ToolComplete
        assertEquals("t1", done.toolId)
        assertEquals("""{"hits":["a","b"]}""", done.result)
        assertEquals("2 results", done.summary)
        assertEquals(1.5, done.durationS!!, 0.0)
    }

    @Test
    fun `reasoning and thinking chunks keep their text`() = runTest {
        val got = collect(this)
        receive(frame("reasoning.delta", payload = buildJsonObject { put("text", "step one") }))
        receive(frame("thinking.delta", payload = buildJsonObject { put("text", "hmm") }))
        receive(frame("reasoning.available", payload = buildJsonObject { put("text", "whole block") }))

        assertEquals(
            listOf(
                GatewayEvent.ReasoningDelta("s1", "step one"),
                GatewayEvent.ThinkingDelta("s1", "hmm"),
                GatewayEvent.ReasoningAvailable("s1", "whole block"),
            ),
            got,
        )
    }

    @Test
    fun `message complete keeps the reply, its reasoning and the numeric usage`() = runTest {
        val got = collect(this)
        receive(frame("message.complete", payload = buildJsonObject {
            put("text", "done")
            put("reasoning", "because")
            put("status", "complete")
            putJsonObject("usage") {
                put("model", "some-model")
                put("input", 120)
                put("output", 30)
                put("cost_usd", 0.002)
                put("context_estimated", true)
            }
        }))

        val complete = got.single() as GatewayEvent.MessageComplete
        assertEquals("done", complete.text)
        assertEquals("because", complete.reasoning)
        assertEquals(mapOf("input" to 120L, "output" to 30L), complete.usage)
    }

    @Test
    fun `request cancel names the request it withdraws`() = runTest {
        val got = collect(this)
        receive(frame("request.cancel", payload = buildJsonObject {
            put("id", "srq-7")
            put("method", "clarify")
            put("reason", "timeout")
        }))

        assertEquals(listOf(GatewayEvent.RequestCancel("s1", "srq-7", "clarify")), got)
    }

    @Test
    fun `a broadcast with an empty session id belongs to no session`() = runTest {
        val got = collect(this)
        receive(frame("sessions.changed", sessionId = "", payload = JsonObject(emptyMap())))

        assertEquals(listOf(GatewayEvent.SessionsChanged(null)), got)
    }

    @Test
    fun `a missing frame is announced before the event after it`() = runTest {
        val got = collect(this)
        receive(frame("message.delta", seq = 1, payload = buildJsonObject { put("text", "a") }))
        receive(frame("message.delta", seq = 3, payload = buildJsonObject { put("text", "c") }))

        assertEquals(
            listOf(
                GatewayEvent.MessageDelta("s1", "a", null),
                GatewayEvent.EventGap("s1"),
                GatewayEvent.MessageDelta("s1", "c", null),
            ),
            got,
        )
    }

    @Test
    fun `a broken frame is skipped and the next one still arrives`() = runTest {
        val got = collect(this)
        receiveRaw("{not json")
        receiveRaw("[1,2,3]")
        receive(frame("message.start", payload = JsonObject(emptyMap())))

        assertEquals(listOf(GatewayEvent.MessageStart("s1")), got)
    }

    private fun serverRequest(id: String, method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }

    @Test
    fun `a server question becomes a card event answered by its frame id`() = runTest {
        val got = collect(this)
        receive(serverRequest("srq-1", "clarify", buildJsonObject {
            put("session_id", "s1")
            put("question", "Proceed?")
        }))

        val clarify = got.single() as GatewayEvent.ClarifyRequest
        assertEquals("srq-1", clarify.requestId)
        assertEquals("Proceed?", clarify.question)
    }

    @Test
    fun `a server request the app cannot show produces no card`() = runTest {
        val got = collect(this)
        receive(serverRequest("srq-2", "vault.code", buildJsonObject { put("session_id", "s1") }))

        assertTrue(got.isEmpty())
    }

    @Test
    fun `open requests handed back on reconnect show again`() = runTest {
        val got = collect(this)
        client.redeliverServerRequests(buildJsonArray {
            add(serverRequest("srq-3", "sudo", buildJsonObject { put("session_id", "s1") }))
            add(serverRequest("srq-4", "secret", buildJsonObject {
                put("session_id", "s1"); put("env_var", "KEY"); put("prompt", "Key?")
            }))
        })

        assertEquals(
            listOf(
                GatewayEvent.SudoRequest("s1", "srq-3"),
                GatewayEvent.SecretRequest("s1", "srq-4", "KEY", "Key?"),
            ),
            got,
        )
    }
}
