package com.hermes.android.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun `Disconnected is object singleton`() {
        val s1: ConnectionState = ConnectionState.Disconnected
        val s2: ConnectionState = ConnectionState.Disconnected
        assertEquals(s1, s2)
    }

    @Test
    fun `Connecting is object singleton`() {
        val s1: ConnectionState = ConnectionState.Connecting
        val s2: ConnectionState = ConnectionState.Connecting
        assertEquals(s1, s2)
    }

    @Test
    fun `Connected holds session id`() {
        val state = ConnectionState.Connected(sessionId = "sess-123")
        assertEquals("sess-123", state.sessionId)
    }

    @Test
    fun `Connected with null session id`() {
        val state = ConnectionState.Connected(sessionId = null)
        assertNull(state.sessionId)
    }

    @Test
    fun `Reconnecting holds attempt and error`() {
        val state = ConnectionState.Reconnecting(attempt = 3, nextAttemptInMs = 5000, lastError = "timeout")
        assertEquals(3, state.attempt)
        assertEquals(5000L, state.nextAttemptInMs)
        assertEquals("timeout", state.lastError)
    }

    @Test
    fun `Reconnecting with null error`() {
        val state = ConnectionState.Reconnecting(attempt = 1, nextAttemptInMs = 1000, lastError = null)
        assertNull(state.lastError)
    }

    @Test
    fun `Failed holds reason`() {
        val state = ConnectionState.Failed(reason = "max retries exceeded")
        assertEquals("max retries exceeded", state.reason)
    }

    @Test
    fun `all states are distinct types`() {
        val states: List<ConnectionState> = listOf(
            ConnectionState.Disconnected,
            ConnectionState.Connecting,
            ConnectionState.Connected("s1"),
            ConnectionState.Reconnecting(1, 1000, null),
            ConnectionState.Failed("err"),
        )
        assertEquals(5, states.map { it::class.simpleName }.toSet().size)
    }
}
