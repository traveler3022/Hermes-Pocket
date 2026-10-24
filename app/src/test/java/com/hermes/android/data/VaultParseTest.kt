package com.hermes.android.data

import com.hermes.android.domain.vault.VaultKind
import com.hermes.android.ui.screen.isWebOrigin
import com.hermes.android.ui.screen.vaultSecret
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultParseTest {

    @Test
    fun `vault list rows keep metadata and their source`() {
        val items = parseVaultItems(Json.parseToJsonElement("""
            {"items": [
              {"id": "vault_1", "kind": "login", "label": "github.com", "origin": "https://github.com",
               "created_at": "2026-10-24T10:00:00+00:00", "identifier": "me@x.io", "identifier_type": "email",
               "has_otp": true, "backend": "local"},
              {"id": "bw-2", "kind": "login", "label": "Bank", "origin": null, "backend": "bitwarden"},
              {"kind": "login"}
            ]}
        """))
        assertEquals(2, items.size)
        assertEquals("me@x.io", items[0].identifier)
        assertTrue(items[0].hasOtp)
        assertNull(items[1].origin)
        assertEquals("bitwarden", items[1].backend)
    }

    @Test
    fun `vault sources read every flag`() {
        val sources = parseVaultSources(Json.parseToJsonElement("""
            {"sources": [{"name": "bitwarden", "display_name": "Bitwarden", "enabled": true,
              "needs_unlock": true, "unlocked": false, "installed": true}]}
        """))
        val bw = sources.single()
        assertEquals("Bitwarden", bw.displayName)
        assertTrue(bw.enabled && bw.needsUnlock && bw.installed)
        assertFalse(bw.unlocked)
    }

    @Test
    fun `the add payload uses the server field names and drops empty optionals`() {
        val login = vaultSecret(VaultKind.LOGIN, "email", mapOf("identifier" to " me@x.io ", "password" to "p w"))
        assertEquals(mapOf("identifier_type" to "email", "identifier" to "me@x.io", "password" to "p w"), login)
        val card = vaultSecret(VaultKind.PAYMENT, "email", mapOf("card_number" to "4242 4242 4242 4242"))
        assertEquals("4242424242424242", card["card_number"])
        val address = vaultSecret(VaultKind.ADDRESS, "email", mapOf("address_line1" to "1 St", "address_line2" to " "))
        assertFalse("address_line2" in address)
    }

    @Test
    fun `only http and https addresses with a host are origins`() {
        assertTrue(isWebOrigin("https://github.com"))
        assertFalse(isWebOrigin("github.com"))
        assertFalse(isWebOrigin("ftp://x.io"))
        assertFalse(isWebOrigin(""))
    }
}
