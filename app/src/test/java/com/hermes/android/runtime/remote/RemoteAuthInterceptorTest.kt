package com.hermes.android.runtime.remote

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RemoteAuthInterceptorTest {

    private val minted = AtomicInteger()
    private val server = TinyServer { _, path, headers ->
        when (path) {
            "/api/auth/ws-ticket" -> 200 to """{"ticket":"t${minted.incrementAndGet()}","ttl_seconds":30}"""
            "/auth/native/refresh" ->
                200 to """{"access_token":"acc2","refresh_token":"ref2","expires_at":0,"provider":"basic","user_id":"me"}"""
            "/api/expiring" -> if (headers["authorization"] == "Bearer acc2") 200 to "{}" else 401 to "{}"
            else -> 200 to "{}"
        }
    }
    private val base = "http://127.0.0.1:${server.port}"
    private val store = storeWith(RemoteTokens(base, "acc", "ref", 0L, "basic", "me"))
    private val client = clientFor(store)

    @After
    fun tearDown() = server.close()

    @Test
    fun `every connection to the gateway gets its own new ticket`() {
        get(client, "$base/api/ws?token=old")
        get(client, "$base/api/ws?token=old")

        assertEquals(listOf("ticket=t1", "ticket=t2"), server.requests.filter { it.path == "/api/ws" }.map { it.query })
        val mints = server.requests.filter { it.path == "/api/auth/ws-ticket" }
        assertEquals(2, mints.size)
        assertTrue(mints.all { it.method == "POST" && it.headers["authorization"] == "Bearer acc" })
    }

    @Test
    fun `plain calls carry the sign-in as a header, never in the URL`() {
        get(client, "$base/api/status?token=old")

        val call = server.requests.single()
        assertEquals("Bearer acc", call.headers["authorization"])
        assertNull(call.query)
    }

    @Test
    fun `another server never sees the sign-in`() {
        get(clientFor(storeWith(RemoteTokens("http://127.0.0.1:1", "acc", "ref", 0L, "basic", "me"))), "$base/api/ws?token=x")

        val call = server.requests.single()
        assertEquals("token=x", call.query)
        assertFalse(call.headers.containsKey("authorization"))
    }

    @Test
    fun `an expired sign-in is refreshed once and the call retried`() {
        get(client, "$base/api/expiring")

        assertEquals(listOf("Bearer acc", "Bearer acc2"), server.requests.filter { it.path == "/api/expiring" }.map { it.headers["authorization"] })
        val refresh = server.requests.single { it.path == "/auth/native/refresh" }
        assertTrue(refresh.body.contains("\"refresh_token\":\"ref\""))
        verify { store.save(match { it.accessToken == "acc2" && it.refreshToken == "ref2" }) }
    }

    private fun storeWith(tokens: RemoteTokens) = mockk<RemoteTokenStore> {
        every { load() } returns tokens
        every { save(any()) } just Runs
        every { clear() } just Runs
    }

    private fun clientFor(store: RemoteTokenStore) =
        OkHttpClient.Builder().addInterceptor(RemoteAuthInterceptor(RemoteAuth(store))).build()

    private fun get(client: OkHttpClient, url: String) =
        client.newCall(Request.Builder().url(url).build()).execute().close()
}

private data class Recorded(
    val method: String,
    val path: String,
    val query: String?,
    val headers: Map<String, String>,
    val body: String,
)

/** A one-connection-per-request HTTP server on 127.0.0.1 that records every request. */
private class TinyServer(
    private val route: (method: String, path: String, headers: Map<String, String>) -> Pair<Int, String>,
) : AutoCloseable {
    val requests = CopyOnWriteArrayList<Recorded>()
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int = socket.localPort

    init {
        Thread {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                client.use { handle(it) }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        val input = client.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = CharArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val parts = requestLine.split(' ')
        val target = parts.getOrElse(1) { "/" }
        val path = target.substringBefore('?')
        requests += Recorded(parts[0], path, target.substringAfter('?', "").ifEmpty { null }, headers, String(body, 0, read))
        val (status, text) = route(parts[0], path, headers)
        val bytes = text.toByteArray()
        val reason = if (status == 200) "OK" else "Unauthorized"
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(bytes)
        out.flush()
    }

    override fun close() {
        socket.close()
    }
}
