package com.hermes.android.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactCredentialsTest {

    @Test
    fun `the gateway token is redacted`() {
        assertEquals(
            "--> GET http://127.0.0.1:9119/api/ws?token=REDACTED",
            redactCredentials("--> GET http://127.0.0.1:9119/api/ws?token=abc123"),
        )
    }

    @Test
    fun `an API key sent as a query parameter is redacted`() {
        val line = redactCredentials(
            "--> GET https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSyExample (0-byte body)",
        )
        assertFalse(line.contains("AIzaSyExample"))
        assertEquals("--> GET https://generativelanguage.googleapis.com/v1beta/models?key=REDACTED (0-byte body)", line)
    }

    @Test
    fun `a connection ticket is redacted`() {
        assertEquals(
            "<-- 101 https://h/api/ws?ticket=REDACTED (12ms)",
            redactCredentials("<-- 101 https://h/api/ws?ticket=AbC-123_x (12ms)"),
        )
    }

    @Test
    fun `other query parameters are kept`() {
        assertEquals(
            "--> GET http://h/api/files/download?path=%2Froot%2Fa.png&token=REDACTED",
            redactCredentials("--> GET http://h/api/files/download?path=%2Froot%2Fa.png&token=t0k"),
        )
    }
}
