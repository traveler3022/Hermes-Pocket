package com.hermes.android.runtime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteUrlsTest {

    @Test
    fun `the server base comes out the same however the address is typed`() {
        assertEquals("https://example.com:2083", remoteHttpBase("wss://example.com:2083"))
        assertEquals("https://example.com:2083", remoteHttpBase("https://example.com:2083/api/ws"))
        assertEquals("http://192.168.1.5:9119", remoteHttpBase("192.168.1.5:9119"))
        assertEquals("https://example.com/hermes", remoteHttpBase(" https://example.com/hermes/ "))
        assertEquals("https://example.com", remoteHttpBase("https://example.com:443/"))
        assertNull(remoteHttpBase("   "))
    }

    @Test
    fun `the gateway socket sits on the same origin`() {
        assertEquals("wss://example.com/hermes/api/ws", remoteWebSocketUrl("https://example.com/hermes"))
        assertEquals("ws://192.168.1.5:9119/api/ws", remoteWebSocketUrl("http://192.168.1.5:9119"))
    }
}
