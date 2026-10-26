package com.hermes.android.data

import com.hermes.android.domain.setup.SetupProvider
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.linux.ProotEnvironment
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** The built-in Linux probes a custom endpoint itself; it must find the base the dashboard would. */
class CustomEndpointProbeTest {

    private val custom = SetupProvider("custom", "Custom", "OPENAI_BASE_URL", "", needsBaseUrl = true)
    private val hits = mutableListOf<String>()
    private lateinit var server: HttpServer
    private lateinit var root: String

    /** Path → (status, body); anything else is a 404. */
    private var routes: Map<String, Pair<Int, String>> = emptyMap()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            synchronized(hits) { hits += path }
            val (code, body) = routes[path] ?: (404 to """{"error":"not found"}""")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        root = "http://127.0.0.1:${server.address.port}"
    }

    private var stopped = false

    @After
    fun tearDown() {
        if (!stopped) server.stop(0)
    }

    private fun repository(): ProviderSetupRepository {
        val runtime = mockk<HermesRuntime>(relaxed = true)
        every { runtime.getWebSocketUrl() } returns StdioGatewayHub.URL
        return ProviderSetupRepository(runtime, OkHttpClient(), mockk<GatewayClient>(relaxed = true), mockk<ProotEnvironment>(relaxed = true))
    }

    private val models = 200 to """{"data":[{"id":"fake-model"}]}"""

    @Test
    fun `a host root whose models live under v1 resolves to the v1 base`() = runBlocking {
        routes = mapOf("/v1/models" to models)

        val check = repository().checkKey(custom, "", root)

        assertTrue(check.ok)
        assertEquals("$root/v1", check.resolvedBaseUrl)
        assertEquals(listOf("fake-model"), check.models)
        assertEquals(listOf("/models", "/v1/models"), hits)
    }

    @Test
    fun `a base that answers keeps the typed address and is asked once`() = runBlocking {
        routes = mapOf("/v1/models" to models)

        val check = repository().checkKey(custom, "", "$root/v1/")

        assertEquals("$root/v1", check.resolvedBaseUrl)
        assertEquals(listOf("/v1/models"), hits)
    }

    @Test
    fun `a v1 base that 404s falls back to the stripped root`() = runBlocking {
        routes = mapOf("/models" to models)

        val check = repository().checkKey(custom, "", "$root/v1")

        assertEquals(root, check.resolvedBaseUrl)
    }

    @Test
    fun `a 401 from the alternate is the failure reported, not the root 404`() = runBlocking {
        routes = mapOf("/v1/models" to (401 to """{"error":"unauthorized"}"""))

        val check = repository().checkKey(custom, "", root)

        assertFalse(check.ok)
        assertTrue(check.reachable)
        assertEquals("$root/v1/models answered HTTP 401.", check.message)
    }

    @Test
    fun `nothing listening is unreachable`() = runBlocking {
        server.stop(0)
        stopped = true

        val check = repository().checkKey(custom, "", root)

        assertFalse(check.ok)
        assertFalse(check.reachable)
    }
}
