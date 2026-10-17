package com.hermes.android.data

import com.hermes.android.domain.config.ConfigField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvAndConfigEditorTest {

    // Trimmed from GET /api/env on Hermes v0.21.7.
    private val env = """
        {"OPENROUTER_API_KEY": {"is_set": false, "redacted_value": null, "description": "OpenRouter API key",
           "url": "https://openrouter.ai/keys", "category": "provider", "is_password": true, "channel_managed": false, "custom": false},
         "TELEGRAM_BOT_TOKEN": {"is_set": true, "redacted_value": "«redacted:1234...abcd»", "category": "messaging",
           "is_password": true, "channel_managed": true, "custom": false},
         "MY_FAKE_KEY": {"is_set": true, "redacted_value": "«redacted:fake...-123»", "description": "", "url": null,
           "category": "custom", "is_password": true, "channel_managed": false, "custom": true}}
    """.trimIndent()

    @Test
    fun `env keys are read and Platforms keys left out`() {
        val keys = parseEnvKeys(Json.parseToJsonElement(env)).associateBy { it.key }
        assertEquals(setOf("OPENROUTER_API_KEY", "MY_FAKE_KEY"), keys.keys)
        assertFalse(keys.getValue("OPENROUTER_API_KEY").isSet)
        assertEquals("https://openrouter.ai/keys", keys.getValue("OPENROUTER_API_KEY").url)
        val custom = keys.getValue("MY_FAKE_KEY")
        assertTrue(custom.isSet && custom.custom)
        assertEquals("fake...-123", custom.preview)
    }

    private val schema = """
        {"fields": {
          "model": {"type": "string", "description": "Default model", "category": "general"},
          "agent.max_turns": {"type": "number", "description": "Max turns", "category": "agent"},
          "agent.verbose": {"type": "boolean", "description": "", "category": "agent"},
          "terminal.backend": {"type": "select", "category": "terminal", "options": ["local", "docker"]},
          "toolsets": {"type": "list", "category": "general"},
          "agent.missing": {"type": "string", "category": "agent"}},
         "category_order": ["general", "agent", "terminal"]}
    """.trimIndent()
    private val values = """
        {"model": "gpt-x", "agent": {"max_turns": 90, "verbose": false}, "terminal": {"backend": "local"}, "toolsets": ["hermes-cli"]}
    """.trimIndent()

    @Test
    fun `form fields carry type and current value`() {
        val form = parseConfigForm(Json.parseToJsonElement(schema), Json.parseToJsonElement(values))
        val fields = form.fields.associateBy { it.key }
        assertEquals("gpt-x", fields.getValue("model").value)
        assertEquals(ConfigField.Type.NUMBER, fields.getValue("agent.max_turns").type)
        assertEquals("90", fields.getValue("agent.max_turns").value)
        assertEquals("false", fields.getValue("agent.verbose").value)
        assertEquals(listOf("local", "docker"), fields.getValue("terminal.backend").options)
        assertEquals("[\"hermes-cli\"]", fields.getValue("toolsets").value)
        assertEquals("", fields.getValue("agent.missing").value)
        assertEquals(listOf("general", "agent", "terminal"), form.categoryOrder)
    }

    @Test
    fun `changed fields are sent nested with their schema types`() {
        val json = Json
        val nested = nestedConfig(
            mapOf(
                "agent.max_turns" to configValue("agent.max_turns", ConfigField.Type.NUMBER, "77", json),
                "agent.verbose" to configValue("agent.verbose", ConfigField.Type.FLAG, "true", json),
                "toolsets" to configValue("toolsets", ConfigField.Type.LIST, "hermes-cli, web", json),
            ),
        )
        assertEquals(
            Json.parseToJsonElement("""{"agent": {"max_turns": 77, "verbose": true}, "toolsets": ["hermes-cli", "web"]}"""),
            nested,
        )
        assertEquals(JsonPrimitive(0.5), configValue("x", ConfigField.Type.NUMBER, "0.5", json))
        assertEquals(JsonNull, configValue("x", ConfigField.Type.NUMBER, " ", json))
        assertEquals(JsonArray(listOf(JsonPrimitive("a"))), configValue("x", ConfigField.Type.LIST, "[\"a\"]", json))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a number field refuses text`() {
        configValue("agent.max_turns", ConfigField.Type.NUMBER, "many", Json)
    }
}
