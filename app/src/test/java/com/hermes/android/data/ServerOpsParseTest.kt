package com.hermes.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOpsParseTest {

    @Test
    fun `latest run keeps only the lines after the last start header`() {
        val lines = listOf(
            "old output",
            "",
            "=== doctor started 2026-10-17 10:00:00 ===",
            "first run",
            "=== doctor completed 2026-10-17 10:00:05 ===",
            "",
            "=== doctor started 2026-10-17 13:52:27 ===",
            "Checking config",
            "All good",
        )
        assertEquals(listOf("Checking config", "All good"), latestRun(lines, "doctor"))
    }

    @Test
    fun `latest run without a header is the whole tail`() {
        assertEquals(listOf("a", "b"), latestRun(listOf("", "a", "b"), "dump"))
    }

    @Test
    fun `hooks and valid events`() {
        // Shape from GET /api/ops/hooks on Hermes 0.21.7.
        val hooks = parseHooks(
            Json.parseToJsonElement(
                """
                {"hooks": [
                  {"event": "pre_tool_call", "matcher": "terminal", "command": "~/hooks/check.sh",
                   "timeout": 30, "allowed": true, "approved_at": "2026-10-17", "executable": true},
                  {"event": "on_session_end", "matcher": null, "command": "notify-send done",
                   "timeout": null, "allowed": false, "approved_at": null, "executable": false}
                ],
                 "valid_events": ["on_session_end", "pre_tool_call"]}
                """.trimIndent(),
            ),
        )
        assertEquals(2, hooks.hooks.size)
        assertEquals("terminal", hooks.hooks[0].matcher)
        assertEquals(30, hooks.hooks[0].timeout)
        assertTrue(hooks.hooks[0].allowed)
        assertNull(hooks.hooks[1].matcher)
        assertNull(hooks.hooks[1].timeout)
        assertFalse(hooks.hooks[1].allowed)
        assertEquals(listOf("on_session_end", "pre_tool_call"), hooks.validEvents)
    }

    @Test
    fun `checkpoints with sizes`() {
        val cp = parseCheckpoints(
            Json.parseToJsonElement(
                """{"sessions": [{"session": "abc", "files": 3, "bytes": 2048}], "total_bytes": 2048}""",
            ),
        )
        assertEquals(1, cp.sessions.size)
        assertEquals(3, cp.sessions[0].files)
        assertEquals(2048L, cp.totalBytes)
        assertEquals(0, parseCheckpoints(Json.parseToJsonElement("""{"sessions": [], "total_bytes": 0}""")).sessions.size)
    }

    @Test
    fun `debug share links in order`() {
        val share = parseDebugShare(
            Json.parseToJsonElement(
                """
                {"ok": true, "urls": {"Report": "https://paste.rs/a", "agent.log": "https://paste.rs/b"},
                 "failures": ["gateway.log: empty"], "redacted": true, "auto_delete_seconds": 21600}
                """.trimIndent(),
            ) as JsonObject,
        )
        assertEquals(listOf("Report" to "https://paste.rs/a", "agent.log" to "https://paste.rs/b"), share.urls)
        assertEquals(listOf("gateway.log: empty"), share.failures)
        assertEquals(21600L, share.autoDeleteSeconds)
    }
}

class ModelSlotsParseTest {

    @Test
    fun `auxiliary tasks and main model`() {
        val aux = parseAuxiliary(
            Json.parseToJsonElement(
                """
                {"tasks": [
                  {"task": "vision", "provider": "auto", "model": "", "base_url": "", "reasoning_effort": null, "local_endpoint": false},
                  {"task": "title_generation", "provider": "openrouter", "model": "google/gemini-flash", "base_url": ""},
                  {"task": "my_plugin", "provider": "auto", "model": "", "label": "Plugin job", "hint": "Does a thing",
                   "inherit_from": "compression", "effective": {"provider": "openrouter", "model": "x/y", "base_url": ""}}
                ],
                 "main": {"provider": "custom:9router", "model": "oc/deepseek"}}
                """.trimIndent(),
            ),
        )
        assertEquals(3, aux.tasks.size)
        assertTrue(aux.tasks[0].isAuto)
        assertFalse(aux.tasks[1].isAuto)
        assertEquals("Plugin job", aux.tasks[2].label)
        assertEquals("x/y", aux.tasks[2].effectiveModel)
        assertEquals("custom:9router", aux.mainProvider)
    }

    @Test
    fun `moa round trip keeps the other preset fields`() {
        // Shape from GET /api/model/moa on Hermes 0.21.7.
        val moa = parseMoa(
            Json.parseToJsonElement(
                """
                {"default_preset": "default", "active_preset": "",
                 "presets": {"default": {"enabled": true,
                   "reference_models": [{"provider": "openai-codex", "model": "gpt-5.5", "enabled": true},
                                        {"provider": "openrouter", "model": "deepseek/deepseek-v4-pro", "enabled": false}],
                   "aggregator": {"provider": "openrouter", "model": "anthropic/claude-opus-4.8"},
                   "reference_temperature": 0.7, "aggregator_temperature": null, "reference_timeout": null,
                   "degraded_reference_policy": "loud", "fanout": "user_turn"}},
                 "reference_models": [], "privacy_filter": ""}
                """.trimIndent(),
            ),
        )
        val preset = moa.presets.getValue("default")
        assertEquals(2, preset.referenceModels.size)
        assertFalse(preset.referenceModels[1].enabled)
        assertEquals("anthropic/claude-opus-4.8", preset.aggregator.model)
        val body = moaBody(moa)
        val sent = (body["presets"] as JsonObject)["default"] as JsonObject
        assertEquals("0.7", sent["reference_temperature"].toString())
        assertEquals("\"loud\"", sent["degraded_reference_policy"].toString())
        assertEquals("\"user_turn\"", sent["fanout"].toString())
        assertEquals(parseMoa(body), moa)
    }
}
