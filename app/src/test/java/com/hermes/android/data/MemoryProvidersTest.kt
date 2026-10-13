package com.hermes.android.data

import com.hermes.android.domain.memory.MemoryProviders.FieldKind
import com.hermes.android.domain.memory.MemoryProviders.ProviderState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProvidersTest {

    @Test
    fun `status from GET api memory`() {
        // Trimmed from Hermes 0.21.7.
        val status = parseMemoryStatus(
            Json.parseToJsonElement(
                """
                {"active": "", "builtin_files": {"memory": 2043, "user": 0},
                 "providers": [
                   {"name": "byterover", "description": "ByteRover", "available": false, "configured": true,
                    "status": "unavailable", "setup": {"external_dependencies": [
                      {"name": "brv", "install": "curl -fsSL https://byterover.dev/install.sh | sh", "check": "brv --version"}],
                      "required_env": [], "dependencies_installed": false}},
                   {"name": "retaindb", "status": "needs_config",
                    "setup": {"required_env": ["RETAINDB_API_KEY"], "dependencies_installed": true}},
                   {"name": "honcho", "status": "ready"},
                   {"description": "no name, dropped"}
                 ]}
                """,
            ),
        )
        assertEquals("", status.active)
        assertEquals(2043L, status.memoryFileBytes)
        assertEquals(0L, status.userFileBytes)
        assertEquals(listOf("byterover", "retaindb", "honcho"), status.providers.map { it.name })
        val brv = status.providers[0]
        assertEquals(ProviderState.UNAVAILABLE, brv.state)
        assertFalse(brv.dependenciesInstalled)
        assertEquals(listOf("curl -fsSL https://byterover.dev/install.sh | sh"), brv.externalTools)
        assertEquals(ProviderState.NEEDS_CONFIG, status.providers[1].state)
        assertEquals(listOf("RETAINDB_API_KEY"), status.providers[1].requiredEnv)
        // No setup block: nothing known to install.
        assertTrue(status.providers[2].dependenciesInstalled)
        assertEquals(ProviderState.READY, status.providers[2].state)
    }

    @Test
    fun `config fields with kinds, options and when`() {
        val fields = parseMemoryConfig(
            Json.parseToJsonElement(
                """
                {"name": "byterover", "fields": [
                  {"key": "api_key", "label": "Api Key", "kind": "secret", "required": true, "value": "", "is_set": true,
                   "url": "https://app.byterover.dev", "when": null},
                  {"key": "auto_extract", "label": "Auto Extract", "kind": "select", "value": "true",
                   "options": [{"value": "true", "label": "true"}, {"value": "false", "label": "false"}]},
                  {"key": "depth", "kind": "integer", "value": 3, "when": {"auto_extract": "true"}},
                  {"key": "flag", "kind": "boolean", "value": false},
                  {"key": "weird", "kind": "slider"},
                  {"label": "no key, dropped"}
                ]}
                """,
            ),
        )
        assertEquals(listOf("api_key", "auto_extract", "depth", "flag", "weird"), fields.map { it.key })
        assertEquals(
            listOf(FieldKind.SECRET, FieldKind.SELECT, FieldKind.INTEGER, FieldKind.BOOLEAN, FieldKind.TEXT),
            fields.map { it.kind },
        )
        assertTrue(fields[0].isSet && fields[0].required)
        assertTrue(fields[0].showWhen.isEmpty())
        assertEquals(listOf("true", "false"), fields[1].options.map { it.value })
        assertEquals("3", fields[2].value)
        assertEquals(mapOf("auto_extract" to "true"), fields[2].showWhen)
        assertEquals("false", fields[3].value)
        assertEquals("weird", fields[4].label)
    }

    @Test
    fun `install result keeps failed steps readable`() {
        val result = parseInstallResult(
            Json.parseToJsonElement(
                """
                {"ok": false, "provider": "byterover", "results": [
                  {"kind": "external_check", "name": "brv", "status": "missing", "command": "brv --version", "stderr": "not found"},
                  {"kind": "external_install", "name": "brv", "status": "failed", "command": "curl … | sh", "stdout": "boom\n"}
                ]}
                """,
            ),
        )
        assertFalse(result.ok)
        assertEquals(listOf("brv --version", "curl … | sh"), result.steps.map { it.name })
        assertEquals("not found", result.steps[0].detail)
        assertEquals("boom", result.steps[1].detail)
    }
}
