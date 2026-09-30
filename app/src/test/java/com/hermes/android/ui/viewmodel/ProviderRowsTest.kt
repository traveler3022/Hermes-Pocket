package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Providers pages read `model.options` with `include_unconfigured`: connected rows plus a
 * skeleton row per other provider. Rows here follow Hermes' own output (inventory.py picker hints).
 */
class ProviderRowsTest {

    private val payload = """
        {"model": "deepseek-chat", "provider": "deepseek", "providers": [
          {"slug": "deepseek", "name": "DeepSeek", "is_current": true, "is_user_defined": false,
           "models": ["deepseek-chat", "deepseek-reasoner"], "total_models": 2, "source": "built-in", "authenticated": true},
          {"slug": "custom:my-server", "name": "my-server", "is_current": false, "is_user_defined": true,
           "models": ["qwen3"], "total_models": 1, "source": "user-config", "authenticated": true},
          {"slug": "nous", "name": "Nous Portal", "is_current": false, "is_user_defined": false, "models": [],
           "total_models": 0, "source": "canonical", "authenticated": false, "auth_type": "oauth_device_code",
           "key_env": "", "warning": "run `hermes model` to configure (oauth_device_code)"},
          {"slug": "openrouter", "name": "OpenRouter", "is_current": false, "is_user_defined": false, "models": [],
           "total_models": 0, "source": "canonical", "authenticated": false, "auth_type": "api_key", "key_env": ""},
          {"slug": "fireworks", "name": "Fireworks AI", "is_current": false, "is_user_defined": false, "models": [],
           "total_models": 0, "source": "canonical", "authenticated": false, "auth_type": "api_key",
           "key_env": "FIREWORKS_API_KEY", "warning": "paste FIREWORKS_API_KEY to activate"},
          {"slug": "custom", "name": "Custom endpoint", "is_current": false, "is_user_defined": false, "models": [],
           "total_models": 0, "source": "canonical", "authenticated": false, "auth_type": "api_key", "key_env": ""}
        ]}
    """.trimIndent()

    private val rows = parseProviderRows(Json.parseToJsonElement(payload))

    @Test
    fun `rows keep their connection, auth type, key env and model count`() {
        val deepseek = rows.first { it.slug == "deepseek" }
        assertTrue(deepseek.connected)
        assertTrue(deepseek.isCurrent)
        assertEquals(2, deepseek.modelCount)
        assertNull(deepseek.authType)

        val fireworks = rows.first { it.slug == "fireworks" }
        assertFalse(fireworks.connected)
        assertEquals("api_key", fireworks.authType)
        assertEquals("FIREWORKS_API_KEY", fireworks.keyEnv)

        // OpenRouter has no registry entry, so Hermes sends a blank key env.
        assertNull(rows.first { it.slug == "openrouter" }.keyEnv)
    }

    @Test
    fun `connected lists Hermes providers only, not custom endpoints`() {
        val state = ProvidersUiState(rows = rows)
        assertEquals(listOf("deepseek"), state.connected.map { it.slug })
    }

    @Test
    fun `key providers are the unconnected api-key rows, without the custom endpoint skeleton`() {
        val state = ProvidersUiState(rows = rows)
        assertEquals(listOf("openrouter", "fireworks"), state.keyProviders.map { it.slug })
    }

    @Test
    fun `a server without picker hints lists configured providers, so they count as connected`() {
        val plain = parseProviderRows(
            Json.parseToJsonElement("""{"providers": [{"slug": "anthropic", "name": "Anthropic", "models": ["claude"]}]}"""),
        ).single()
        assertTrue(plain.connected)
        assertEquals(1, plain.modelCount)
    }

    @Test
    fun `malformed payloads give no rows`() {
        assertTrue(parseProviderRows(Json.parseToJsonElement("""{"providers": "nope"}""")).isEmpty())
        assertTrue(parseProviderRows(Json.parseToJsonElement("""[1, 2]""")).isEmpty())
    }
}
