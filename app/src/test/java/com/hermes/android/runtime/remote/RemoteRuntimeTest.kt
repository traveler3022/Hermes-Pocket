package com.hermes.android.runtime.remote

import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RemoteRuntime].
 *
 * Key behaviors:
 * - detect() maps "configured" → Available/Installed and "not configured" → Missing.
 * - startGateway() connects via GatewayClient and transitions to Running.
 * - stopGateway() disconnects but never touches the remote service.
 * - isHealthy() reflects the real WS connection state.
 */
class RemoteRuntimeTest {

    private lateinit var settings: RemoteServerSettings
    private lateinit var gatewayClient: GatewayClient
    private lateinit var runtime: RemoteRuntime

    private val configFlow = MutableStateFlow(RemoteServerConfig("", ""))

    private val testUrl = "wss://example.com:2083/api/ws?token=secret"

    @Before
    fun setup() {
        settings = mockk()
        gatewayClient = mockk(relaxed = true)
        every { settings.config } returns configFlow
        every { settings.webSocketUrl() } answers {
            val c = configFlow.value
            if (c.isComplete) "${c.serverUrl}/api/ws?token=${c.token}" else null
        }
        runtime = RemoteRuntime(settings, gatewayClient)
    }

    private fun configure() {
        configFlow.value = RemoteServerConfig("wss://example.com:2083", "secret")
    }

    // ---- detect ----

    @Test
    fun `detect returns Missing when not configured`() = runTest {
        val result = runtime.detect()
        assertTrue(result is DetectionResult.Missing)
        assertTrue(runtime.state.value is RuntimeState.NotDetected)
    }

    @Test
    fun `detect returns Available and Installed state when configured`() = runTest {
        configure()
        val result = runtime.detect()
        assertTrue(result is DetectionResult.Available)
        assertEquals(RuntimeType.REMOTE, (result as DetectionResult.Available).info.type)
        assertTrue(runtime.state.value is RuntimeState.Installed)
    }

    // ---- startGateway ----

    @Test(expected = IllegalStateException::class)
    fun `startGateway throws when not configured`() = runTest {
        runtime.startGateway()
    }

    @Test
    fun `startGateway transitions to Running when connection succeeds`() = runTest {
        configure()
        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Connected(testUrl)
        val handle = runtime.startGateway()
        assertEquals(testUrl, handle.webSocketUrl)
        assertNull(handle.pid) // remote process — no local PID
        assertTrue(runtime.state.value is RuntimeState.Running)
    }

    @Test
    fun `startGateway sets Error state when connection fails`() = runTest {
        configure()
        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Failed("refused")
        try {
            runtime.startGateway()
            throw AssertionError("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(runtime.state.value is RuntimeState.Error)
        }
    }

    @Test
    fun `startGateway is idempotent while Running`() = runTest {
        configure()
        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Connected(testUrl)
        val first = runtime.startGateway()
        val second = runtime.startGateway()
        assertEquals(first, second)
        coVerify(exactly = 1) { gatewayClient.connect(any(), any()) }
    }

    // ---- stopGateway ----

    @Test
    fun `stopGateway disconnects and reverts to Installed`() = runTest {
        configure()
        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Connected(testUrl)
        runtime.startGateway()

        runtime.stopGateway()

        coVerify { gatewayClient.disconnect() }
        assertTrue(runtime.state.value is RuntimeState.Installed)
    }

    // ---- isHealthy ----

    @Test
    fun `isHealthy returns false when not configured`() = runTest {
        assertFalse(runtime.isHealthy())
    }

    @Test
    fun `isHealthy reflects gateway connection state`() = runTest {
        configure()
        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Connected(testUrl)
        assertTrue(runtime.isHealthy())

        coEvery { gatewayClient.connect(any(), any()) } returns
            ConnectionState.Failed("down")
        assertFalse(runtime.isHealthy())
    }

    // ---- getWebSocketUrl ----

    @Test
    fun `getWebSocketUrl returns empty string when not configured`() {
        assertEquals("", runtime.getWebSocketUrl())
    }

    @Test
    fun `getWebSocketUrl builds full url from settings`() {
        configure()
        assertEquals(testUrl, runtime.getWebSocketUrl())
    }
}

/** Tests for [RemoteServerSettings.Companion.normalizeUrl] — pure function, no Android deps. */
class NormalizeUrlTest {

    @Test
    fun `bare host gets wss scheme`() {
        assertEquals(
            "wss://example.com:2083",
            RemoteServerSettings.normalizeUrl("example.com:2083"),
        )
    }

    @Test
    fun `https is mapped to wss`() {
        assertEquals(
            "wss://example.com:2083",
            RemoteServerSettings.normalizeUrl("https://example.com:2083"),
        )
    }

    @Test
    fun `full ws url pasted by user is stripped to base`() {
        assertEquals(
            "wss://example.com:2083",
            RemoteServerSettings.normalizeUrl("wss://example.com:2083/api/ws?token=abc"),
        )
    }

    @Test
    fun `trailing slash and whitespace are trimmed`() {
        assertEquals(
            "wss://example.com:2083",
            RemoteServerSettings.normalizeUrl("  wss://example.com:2083/  "),
        )
    }

    @Test
    fun `explicit ws scheme is preserved`() {
        assertEquals(
            "ws://192.168.1.10:9119",
            RemoteServerSettings.normalizeUrl("ws://192.168.1.10:9119"),
        )
    }
}
