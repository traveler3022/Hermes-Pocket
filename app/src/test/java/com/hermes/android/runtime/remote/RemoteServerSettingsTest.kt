package com.hermes.android.runtime.remote

import com.hermes.android.runtime.remote.RemoteServerSettings.Companion.normalizeToken
import com.hermes.android.runtime.remote.RemoteServerSettings.Companion.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A token is pasted out of a chat message, so it arrives wearing its label.
 * The server answers a labelled token with a WebSocket close carrying no
 * readable reason, which is a long way to travel to learn about a prefix.
 */
class RemoteServerSettingsTest {

    /** The 64-char token this server actually mints. */
    private val token = "e03730c0e30b558fca69ec9596c3303de9ab04869b19dddf8d51caad927e476c"

    @Test
    fun `a bare token is left alone`() {
        assertEquals(token, normalizeToken(token))
        assertEquals(token, normalizeToken("  $token  "))
    }

    /** Verbatim from the nginx log: three 403s before the label was noticed. */
    @Test
    fun `a Persian label in front of the token is dropped`() {
        assertEquals(token, normalizeToken("- توکن: $token"))
    }

    @Test
    fun `an English label in front of the token is dropped`() {
        assertEquals(token, normalizeToken("Token: $token"))
        assertEquals(token, normalizeToken("session token = $token"))
        assertEquals(token, normalizeToken("token=$token"))
    }

    @Test
    fun `a label after the token is dropped too`() {
        assertEquals(token, normalizeToken("$token <- paste this"))
    }

    @Test
    fun `a pasted WebSocket URL reduces to its token`() {
        assertEquals(token, normalizeToken("wss://star.skyteach.bond:2083/api/ws?token=$token"))
    }

    @Test
    fun `a quoted token loses its quotes`() {
        assertEquals(token, normalizeToken("\"$token\""))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", normalizeToken(""))
        assertEquals("", normalizeToken("   "))
    }

    /**
     * A wrong token must still reach the server. Silently rewriting it into
     * something else would trade one unexplained failure for a stranger one.
     */
    @Test
    fun `input with no token-shaped run is passed through trimmed`() {
        assertEquals("توکن", normalizeToken("  توکن  "))
    }

    @Test
    fun `url normalization keeps its existing behaviour`() {
        assertEquals("wss://star.skyteach.bond:2083", normalizeUrl("star.skyteach.bond:2083"))
        assertEquals("wss://star.skyteach.bond:2083", normalizeUrl("https://star.skyteach.bond:2083/"))
        assertEquals(
            "wss://star.skyteach.bond:2083",
            normalizeUrl("wss://star.skyteach.bond:2083/api/ws?token=abc"),
        )
    }
}
