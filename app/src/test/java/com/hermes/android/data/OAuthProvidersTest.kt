package com.hermes.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Accounts page is built from GET /api/providers/oauth (or the setup script printing the same
 * shape); rows here are Hermes' own output, with the token fields redacted.
 */
class OAuthProvidersTest {

    private fun parse(rows: String) = parseOAuthProviders(Json.parseToJsonElement("""{"ok": true, "providers": [$rows]}""").jsonObject)

    @Test
    fun `a device-code provider parses with its command and status`() {
        val nous = parse(
            """{"id": "nous", "name": "Nous Portal", "flow": "device_code", "cli_command": "hermes auth add nous",
               "docs_url": "https://portal.nousresearch.com", "disconnect_hint": null, "disconnectable": true,
               "status": {"logged_in": false, "source": "nous_portal", "expires_at": null, "free_tier": false}}""",
        ).single()
        assertEquals("nous", nous.id)
        assertEquals("Nous Portal", nous.name)
        assertEquals("device_code", nous.flow)
        assertEquals("hermes auth add nous", nous.cliCommand)
        assertFalse(nous.loggedIn)
        assertTrue(nous.disconnectable)
        assertNull(nous.disconnectHint)
    }

    @Test
    fun `a CLI-owned sign-in is signed in but not disconnectable, with the reason`() {
        val claude = parse(
            """{"id": "claude-code", "name": "Anthropic OAuth", "flow": "external", "cli_command": "claude setup-token",
               "docs_url": "", "disconnect_hint": "Managed outside Hermes — run the disconnect command to remove it.",
               "disconnectable": false, "status": {"logged_in": true, "expires_at": 1790820434061}}""",
        ).single()
        assertTrue(claude.loggedIn)
        assertFalse(claude.disconnectable)
        assertEquals("Managed outside Hermes — run the disconnect command to remove it.", claude.disconnectHint)
    }

    @Test
    fun `a free-tier token is not a signed-in account`() {
        val nous = parse(
            """{"id": "nous", "name": "Nous Portal", "flow": "device_code", "cli_command": "hermes auth add nous",
               "status": {"logged_in": true, "free_tier": true}}""",
        ).single()
        assertFalse(nous.loggedIn)
    }

    @Test
    fun `rows without an id are skipped and a missing name falls back to the id`() {
        val rows = parse("""{"name": "No id"}, {"id": "xai-oauth", "flow": "device_code"}""")
        assertEquals(listOf("xai-oauth"), rows.map { it.id })
        assertEquals("xai-oauth", rows.single().name)
        assertFalse(rows.single().loggedIn)
    }
}
