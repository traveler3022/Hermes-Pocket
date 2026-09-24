package com.hermes.android.gateway

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request params in the shapes of `tui_gateway/contracts/server_requests.py`.
 */
class ServerRequestParserTest {

    private fun parse(method: String, params: JsonObject) = ServerRequestParser.parse("srq-1", method, params)

    @Test
    fun `a single clarify question keeps its choices and answers to the frame id`() {
        val event = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("question", "Which one?")
            put("choices", buildJsonArray { add("a"); add("b") })
        }) as GatewayEvent.ClarifyRequest

        assertEquals("s1", event.sessionId)
        assertEquals("srq-1", event.requestId)
        assertEquals("Which one?", event.question)
        assertEquals(listOf("a", "b"), event.choices)
        assertFalse(event.multiSelect)
        assertTrue(event.questions.isEmpty())
    }

    @Test
    fun `a batch clarify becomes one question per entry`() {
        val event = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("questions", buildJsonArray {
                add(buildJsonObject { put("qid", "q1"); put("question", "Name?") })
                add(buildJsonObject {
                    put("qid", "q2"); put("question", "Colors?")
                    put("choices", buildJsonArray { add("red"); add("blue") })
                    put("multi_select", true)
                })
            })
        }) as GatewayEvent.ClarifyRequest

        assertEquals(listOf("q1", "q2"), event.questions.map { it.qid })
        assertNull(event.questions[0].choices)
        assertFalse(event.questions[0].multiSelect)
        assertEquals(listOf("red", "blue"), event.questions[1].choices)
        assertTrue(event.questions[1].multiSelect)
    }

    @Test
    fun `batch questions without a qid are dropped`() {
        val event = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("questions", buildJsonArray {
                add(buildJsonObject { put("question", "no id") })
                add(buildJsonObject { put("qid", "q2"); put("question", "kept") })
            })
        }) as GatewayEvent.ClarifyRequest

        assertEquals(listOf("q2"), event.questions.map { it.qid })
    }

    @Test
    fun `an empty questions list gives no batch`() {
        val event = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("question", "Plain?")
            put("questions", buildJsonArray {})
        }) as GatewayEvent.ClarifyRequest

        assertTrue(event.questions.isEmpty())
        assertEquals("Plain?", event.question)
    }

    @Test
    fun `blank choices are not offered and no choices at all reads as free text`() {
        val blank = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("question", "?")
            put("choices", buildJsonArray { add(""); add("  ") })
        }) as GatewayEvent.ClarifyRequest
        val mixed = parse("clarify", buildJsonObject {
            put("session_id", "s1")
            put("question", "?")
            put("choices", buildJsonArray { add("yes"); add(" ") })
        }) as GatewayEvent.ClarifyRequest

        assertNull(blank.choices)
        assertEquals(listOf("yes"), mixed.choices)
    }

    @Test
    fun `an approval carries its queue id, the frame id and the command`() {
        val event = parse("approval", buildJsonObject {
            put("session_id", "s1")
            put("request_id", "apr-9")
            put("command", "rm -rf build")
            put("description", "delete build output")
            put("pattern_keys", buildJsonArray { add("rm") })
        }) as GatewayEvent.ApprovalRequest

        assertEquals("apr-9", event.requestId)
        assertEquals("srq-1", event.serverRequestId)
        assertEquals("rm -rf build", event.command)
        assertEquals("delete build output", event.description)
        assertEquals(listOf("rm"), event.patternKeys)
    }

    @Test
    fun `always-allow is withheld only when the server says so`() {
        fun allow(value: Boolean?) = (parse("approval", buildJsonObject {
            put("session_id", "s1")
            put("request_id", "apr-1")
            if (value != null) put("allow_permanent", value)
        }) as GatewayEvent.ApprovalRequest).allowPermanent

        assertFalse(allow(false))
        assertTrue(allow(true))
        assertTrue(allow(null))
    }

    @Test
    fun `an approval offers the answers the server lists`() {
        fun choices(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
            (parse("approval", buildJsonObject { put("command", "rm x"); build() }) as GatewayEvent.ApprovalRequest)

        assertEquals(listOf("once", "deny"), choices { put("choices", buildJsonArray { add("once"); add("deny") }) }.choices)
        val smart = choices { put("smart_denied", true) }
        assertEquals(listOf("once", "deny"), smart.choices)
        assertFalse(smart.allowPermanent)
        assertEquals(listOf("once", "session", "deny"), choices { put("allow_permanent", false) }.choices)
        assertEquals(listOf("once", "session", "always", "deny"), choices { }.choices)
    }

    @Test
    fun `sudo and secret keep only what the prompt needs`() {
        val sudo = parse("sudo", buildJsonObject {
            put("session_id", "s1")
            put("command", "sudo apt update")
        }) as GatewayEvent.SudoRequest
        val secret = parse("secret", buildJsonObject {
            put("session_id", "s1")
            put("env_var", "OPENAI_API_KEY")
            put("prompt", "Paste your key")
        }) as GatewayEvent.SecretRequest

        assertEquals(GatewayEvent.SudoRequest("s1", "srq-1"), sudo)
        assertEquals(GatewayEvent.SecretRequest("s1", "srq-1", "OPENAI_API_KEY", "Paste your key"), secret)
    }

    @Test
    fun `a blank session id is no session`() {
        val event = parse("sudo", buildJsonObject { put("session_id", "") }) as GatewayEvent.SudoRequest

        assertNull(event.sessionId)
    }

    @Test
    fun `a request kind the app has no card for is not turned into an event`() {
        assertNull(parse("vault.code", buildJsonObject { put("session_id", "s1") }))
        assertNull(parse("preview.act", buildJsonObject { put("session_id", "s1"); put("action", "click") }))
    }
}
