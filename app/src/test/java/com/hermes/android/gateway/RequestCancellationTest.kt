package com.hermes.android.gateway

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A request whose caller is cancelled (a newer chat picked while the older
 * resume was in flight) must end as a cancellation. Wrapped as a
 * GatewayException it read as a failed request and the screen said
 * "Failed to resume".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestCancellationTest {

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
    fun `a cancelled caller sees a cancellation, not a gateway failure`() = runTest {
        val client = connectedClient()
        var thrown: Throwable? = null
        val job = launch {
            try {
                client.request("session.activate")
            } catch (t: Throwable) {
                thrown = t
                throw t
            }
        }
        runCurrent()
        job.cancel()
        job.join()

        assertTrue("got $thrown", thrown is CancellationException)
    }

    @Test
    fun `a request that runs out of time is still a gateway failure`() = runTest {
        val client = connectedClient()
        var thrown: Throwable? = null
        launch {
            try {
                client.request("session.activate", timeoutMs = 1_000)
            } catch (t: Throwable) {
                thrown = t
            }
        }.join()

        assertTrue("got $thrown", thrown is GatewayException)
    }
}
