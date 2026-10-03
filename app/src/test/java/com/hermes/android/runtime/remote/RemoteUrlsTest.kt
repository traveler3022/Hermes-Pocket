package com.hermes.android.runtime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class RemoteUrlsTest {

    @Test
    fun `the server base comes out the same however the address is typed`() {
        assertEquals("https://example.com:2083", remoteHttpBase("wss://example.com:2083"))
        assertEquals("https://example.com:2083", remoteHttpBase("https://example.com:2083/api/ws"))
        assertEquals("https://my-server.tail1234.ts.net", remoteHttpBase("my-server.tail1234.ts.net"))
        assertEquals("https://example.com/hermes", remoteHttpBase(" https://example.com/hermes/ "))
        assertEquals("https://example.com", remoteHttpBase("https://example.com:443/"))
        assertNull(remoteHttpBase("   "))
    }

    @Test
    fun `only encrypted addresses are accepted`() {
        assertEquals("https://my-server.tail1234.ts.net", encryptedBase("my-server.tail1234.ts.net"))
        assertThrows(IOException::class.java) { encryptedBase("http://192.168.1.5:9119") }
        assertThrows(IOException::class.java) { encryptedBase("ws://example.com/api/ws") }
    }

    @Test
    fun `the gateway socket sits on the same origin`() {
        assertEquals("wss://my-server.tail1234.ts.net/api/ws", remoteWebSocketUrl("https://my-server.tail1234.ts.net"))
        assertEquals("wss://example.com/hermes/api/ws", remoteWebSocketUrl("https://example.com/hermes"))
    }
}
