package com.hermes.android.gateway

import com.hermes.android.runtime.remote.TailnetRoute
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionPool
import okhttp3.Request
import okhttp3.WebSocket
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * A link is dead when nothing at all comes from the server, not when an answer is slow: an
 * upload the server can't answer before it has read all of it keeps the link while its bytes
 * are acknowledged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkWatchTest {

    /** The tunnel's count of bytes received from the server. */
    private class Tunnel : TailnetRoute by TailnetRoute.None {
        var bytes = 0L
        override suspend fun receivedFrom(host: String): Long = bytes
    }

    private fun socket(queued: Long = 0, onSend: () -> Unit = {}): WebSocket = mockk(relaxed = true) {
        every { request() } returns Request.Builder().url("https://server.example.ts.net/api/ws").build()
        every { queueSize() } returns queued
        every { send(any<String>()) } answers { onSend(); true }
    }

    @Test
    fun `an idle link that answers its probes stays up`() = runTest {
        val frames = AtomicLong(0)
        var dead: String? = null
        val ws = socket(onSend = { frames.incrementAndGet() })
        LinkWatch(Tunnel()).watch(backgroundScope, ws, frames::get, probe = { it.send("ping") }) { dead = it }

        advanceTimeBy(10 * LinkWatch.INTERVAL_MS)
        assertNull(dead)
    }

    @Test
    fun `a link that goes silent is dropped, idle connections with it`() = runTest {
        val pool = mockk<ConnectionPool>(relaxed = true)
        var dead: String? = null
        LinkWatch(Tunnel(), pool).watch(backgroundScope, socket(), { 0L }, probe = { it.send("ping") }) { dead = it }

        advanceTimeBy(LinkWatch.INTERVAL_MS + 1)
        assertNull("a probe gets its round to be answered", dead)
        advanceTimeBy(LinkWatch.INTERVAL_MS)
        assertNotNull(dead)
        verify { pool.evictAll() }
    }

    @Test
    fun `an upload keeps the link while its bytes are acknowledged`() = runTest {
        val tunnel = Tunnel()
        var dead: String? = null
        val ws = socket(queued = 5_000_000)
        LinkWatch(tunnel).watch(backgroundScope, ws, { 0L }, probe = { it.send("ping") }) { dead = it }

        repeat(10) {
            tunnel.bytes += 1_000
            advanceTimeBy(LinkWatch.INTERVAL_MS)
        }
        assertNull(dead)
        verify(exactly = 0) { ws.send(any<String>()) }
    }

    @Test
    fun `an upload on a dead link is dropped`() = runTest {
        var dead: String? = null
        LinkWatch(Tunnel()).watch(backgroundScope, socket(queued = 5_000_000), { 0L }, probe = { it.send("ping") }) { dead = it }

        advanceTimeBy(2 * LinkWatch.INTERVAL_MS + 1)
        assertNotNull(dead)
    }
}
