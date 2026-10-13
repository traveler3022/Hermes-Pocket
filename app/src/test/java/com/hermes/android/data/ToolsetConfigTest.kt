package com.hermes.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsetConfigTest {

    // Trimmed from GET /api/tools/toolsets/web/config on Hermes v0.21.7.
    private val web = """
        {"name": "web", "has_category": true, "active_provider": "Brave Search (Free)", "providers": [
          {"name": "Nous Subscription", "badge": "subscription", "tag": "Managed web search", "env_vars": [],
           "requires_nous_auth": true, "is_active": false, "status": "needs_auth"},
          {"name": "Brave Search (Free)", "badge": "free", "tag": "Free-tier API key",
           "env_vars": [{"key": "BRAVE_SEARCH_API_KEY", "prompt": "Brave Search API key (free tier)",
                         "url": "https://brave.com/search/api/", "default": null, "is_set": true}],
           "requires_nous_auth": false, "is_active": true, "status": "ready"}
        ]}
    """.trimIndent()

    @Test
    fun `providers, keys and the active one are read`() {
        val config = parseToolsetConfig("web", Json.parseToJsonElement(web))
        assertEquals("Brave Search (Free)", config.activeProvider)
        assertEquals(2, config.providers.size)
        val nous = config.providers[0]
        assertTrue(nous.requiresNousAuth)
        assertFalse(nous.isActive)
        assertTrue(nous.envVars.isEmpty())
        val brave = config.providers[1]
        assertEquals("ready", brave.status)
        assertEquals("BRAVE_SEARCH_API_KEY", brave.envVars.single().key)
        assertTrue(brave.envVars.single().isSet)
    }

    @Test
    fun `a toolset without backends gives an empty list`() {
        val config = parseToolsetConfig("todo", Json.parseToJsonElement("""{"name": "todo", "has_category": false, "providers": []}"""))
        assertEquals("todo", config.name)
        assertTrue(config.providers.isEmpty())
        assertNull(config.activeProvider)
    }
}
