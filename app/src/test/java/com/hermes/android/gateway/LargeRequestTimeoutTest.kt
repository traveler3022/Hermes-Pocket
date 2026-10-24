package com.hermes.android.gateway

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A photo's base64 over mobile data can take minutes to upload, and the server answers only
 * once it has read all of it: the request gets time for its size, not the plain limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LargeRequestTimeoutTest {

    private fun connectedClient(): OkHttpGatewayClient {
        val client = OkHttpGatewayClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, StdioGatewayHub(), mockk(relaxed = true))
        val socket = mockk<WebSocket>(relaxed = true) { every { send(any<String>()) } returns true }
        OkHttpGatewayClient::class.java.getDeclaredField("webSocket").apply { isAccessible = true }.set(client, socket)
        @Suppress("UNCHECKED_CAST")
        val state = OkHttpGatewayClient::class.java.getDeclaredField("_connectionState")
            .apply { isAccessible = true }.get(client) as MutableStateFlow<ConnectionState>
        state.value = ConnectionState.Connected(null)
        return client
    }

    @Test
    fun `an upload gets time for its size, then still times out`() = runTest {
        val client = connectedClient()
        // 1 MiB of base64: 30 s + 1 MiB at 32 KB/s = about 62 s.
        val params = mapOf("content_base64" to JsonPrimitive("A".repeat(1024 * 1024)))
        var thrown: Throwable? = null
        val job = launch {
            try {
                client.request("image.attach_bytes", params, timeoutMs = 1_000)
            } catch (t: Throwable) {
                thrown = t
            }
        }
        advanceTimeBy(45_000)
        runCurrent()
        assertNull("gave up before the upload had time: $thrown", thrown)
        assertTrue(job.isActive)

        advanceTimeBy(30_000)
        runCurrent()
        assertTrue("got $thrown", thrown is GatewayException)
    }

    @Test
    fun `a small request keeps its own limit`() = runTest {
        val client = connectedClient()
        var thrown: Throwable? = null
        launch {
            try {
                client.request("session.activate", timeoutMs = 1_000)
            } catch (t: Throwable) {
                thrown = t
            }
        }
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue("got $thrown", thrown is GatewayException)
    }
}
