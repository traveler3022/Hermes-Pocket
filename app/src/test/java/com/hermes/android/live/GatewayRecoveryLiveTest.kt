package com.hermes.android.live

import android.content.Context
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.OkHttpGatewayClient
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatSessionDelegate
import com.hermes.android.ui.viewmodel.ChatUiState
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.concurrent.thread

/**
 * The app's own connection layer against a real Hermes gateway, with a TCP
 * relay in front of it that can cut, freeze or refuse the phone's link.
 *
 * Every case asserts what the user would see, so a failure here is a bug in
 * the client or a server behaviour the client assumes wrongly. Runs only when
 * HERMES_LIVE_URL is set (`wss://host:port/api/ws?token=…`); skipped in CI.
 * Each case runs a short real turn on the gateway's model.
 */
class GatewayRecoveryLiveTest {

    private val url: String? = System.getenv("HERMES_LIVE_URL")
    private lateinit var relay: Relay
    private lateinit var scope: CoroutineScope
    private val gateways = mutableListOf<OkHttpGatewayClient>()

    @Before
    fun setUp() {
        Assume.assumeTrue("HERMES_LIVE_URL not set — live gateway tests skipped", !url.isNullOrBlank())
        relay = Relay(URI(url!!))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runBlocking { gateways.forEach { runCatching { it.disconnect() } } }
        if (::relay.isInitialized) relay.close()
        if (::scope.isInitialized) scope.cancel()
    }

    // ── Cases ──────────────────────────────────────────────────────────────

    @Test
    fun `session create hands the chat its stored id`() {
        val phone = connectedPhone()

        assertNotNull("session.create gave no live id", phone.state.value.activeSessionId)
        assertNotNull(
            "session.create gave no stored id — a chat reaped later has nothing to resume through",
            phone.state.value.activeSessionKey,
        )
    }

    @Test
    fun `a short drop mid-turn keeps the chat and shows the reply once`() {
        val phone = connectedPhone()
        val mark = marker()
        val live = submit(phone, longPrompt(mark))
        awaitEvent(phone, 0, 90_000, "the first delta") { it is GatewayEvent.MessageDelta && it.sessionId == live }

        val cutAt = phone.events.size
        relay.cut()
        awaitReconnect(phone)

        recoverLikeTheApp(phone, live, cutAt)

        val state = phone.state.value
        assertEquals("a short drop must keep the same live session", live, state.activeSessionId)
        assertEquals("the prompt must appear exactly once", 1, state.users(mark))
        assertEquals("the reply must appear exactly once", 1, state.replies(mark))
        assertTrue("the turn is over but the chat still shows it running", !state.isSending)
    }

    @Test
    fun `a drop past the orphan grace re-attaches the next send through the stored id`() {
        val phone = connectedPhone()
        val first = marker()
        val live = submit(phone, shortPrompt(first))
        awaitEvent(phone, 0, 90_000, "the first reply") { it is GatewayEvent.MessageComplete && it.sessionId == live }

        relay.refusing = true
        relay.cut()
        // The gateway reaps a clientless, idle session after its 20 s grace.
        Thread.sleep(35_000)
        relay.refusing = false
        awaitConnected(phone, 90_000)

        val resumedAt = phone.events.size
        val second = marker()
        val used = submit(phone, shortPrompt(second))
        assertNotEquals("the reaped live id still accepted a prompt — the session was never reclaimed", live, used)
        assertEquals("the chat must adopt the re-issued live id", used, phone.state.value.activeSessionId)
        awaitEvent(phone, resumedAt, 90_000, "the second reply") { it is GatewayEvent.MessageComplete && it.sessionId == used }

        snapshot(phone, turnEnded = true)
        val state = phone.state.value
        assertEquals("first prompt count", 1, state.users(first))
        assertEquals("first reply count", 1, state.replies(first))
        assertEquals("second prompt count — the retry must not duplicate it", 1, state.users(second))
        assertEquals("second reply count", 1, state.replies(second))
    }

    @Test
    fun `a frozen link is detected and the reply still arrives`() {
        val phone = connectedPhone()
        val mark = marker()
        val live = submit(phone, longPrompt(mark))
        awaitEvent(phone, 0, 90_000, "the first delta") { it is GatewayEvent.MessageDelta && it.sessionId == live }

        val cutAt = phone.events.size
        val frozenAt = System.currentTimeMillis()
        relay.freeze()
        runBlocking {
            withTimeout(60_000) { phone.gateway.connectionState.first { it !is ConnectionState.Connected } }
        }
        println("[live] frozen link detected after ${System.currentTimeMillis() - frozenAt} ms")
        awaitConnected(phone, 90_000)

        recoverLikeTheApp(phone, live, cutAt)

        val state = phone.state.value
        assertEquals("the prompt must appear exactly once", 1, state.users(mark))
        assertEquals("the reply must appear exactly once", 1, state.replies(mark))
    }

    @Test
    fun `a healthy turn reports no seq holes`() {
        val phone = connectedPhone()
        val live = submit(phone, longPrompt(marker()))
        awaitEvent(phone, 0, 120_000, "the reply") { it is GatewayEvent.MessageComplete && it.sessionId == live }

        val gaps = phone.events.filterIsInstance<GatewayEvent.EventGap>()
        assertTrue("EventGap on a socket that never dropped: $gaps", gaps.isEmpty())
    }

    @Test
    fun `the gateway announces a reclaimed chat to other clients`() {
        val watcher = Phone(throughRelay = false)
        runBlocking { watcher.gateway.connect(url!!) }
        val phone = connectedPhone()
        val live = submit(phone, shortPrompt(marker()))
        awaitEvent(phone, 0, 90_000, "the reply") { it is GatewayEvent.MessageComplete && it.sessionId == live }
        val key = phone.state.value.activeSessionKey

        relay.refusing = true
        relay.cut()

        val reclaimed = awaitEvent(watcher, 0, 90_000, "session.reclaimed for $live") {
            it is GatewayEvent.Unknown && it.eventType == "session.reclaimed" &&
                (it.rawPayload["session_id"] as? JsonPrimitive)?.content == live
        } as GatewayEvent.Unknown
        assertEquals(
            "session.reclaimed must name the stored id to resume",
            key,
            (reclaimed.rawPayload["stored_session_id"] as? JsonPrimitive)?.content,
        )
    }

    @Test
    fun `every busy background chat delivers its reply after a reconnect`() {
        val phone = connectedPhone()
        val openLive = phone.state.value.activeSessionId!!
        // Two of them: the gateway client re-attaches at most one session on its
        // own (whichever spoke last), so a single chat could pass by luck.
        val chats = List(2) {
            val chat = MutableStateFlow(ChatUiState())
            runBlocking { phone.chat.create(chat) }
            chat
        }
        val marks = chats.map { chat ->
            val mark = marker()
            runBlocking {
                phone.gateway.request(
                    GatewayMethods.PROMPT_SUBMIT,
                    mapOf("session_id" to JsonPrimitive(chat.value.activeSessionId!!), "text" to JsonPrimitive(longPrompt(mark))),
                )
            }
            mark
        }
        chats.forEach { chat ->
            val live = chat.value.activeSessionId
            awaitEvent(phone, 0, 90_000, "the first delta of $live") { it is GatewayEvent.MessageDelta && it.sessionId == live }
        }

        val cutAt = phone.events.size
        relay.cut()
        awaitReconnect(phone)
        // What ChatViewModel does after a reconnect: recover the open chat only.
        runBlocking { phone.chat.recover(phone.state, openLive, phone.state.value.activeSessionKey) }

        val deadline = System.currentTimeMillis() + 150_000
        val pending = chats.map { it.value.activeSessionId!! }.toMutableSet()
        while (pending.isNotEmpty() && System.currentTimeMillis() < deadline) {
            phone.eventsSince(cutAt).forEach { if (it is GatewayEvent.MessageComplete) pending.remove(it.sessionId) }
            Thread.sleep(250)
        }
        if (pending.isEmpty()) return
        val onServer = chats.zip(marks).filter { (chat, _) -> chat.value.activeSessionId in pending }.map { (chat, mark) ->
            val raw = runBlocking { phone.repo.attach(chat.value.activeSessionKey ?: chat.value.activeSessionId!!) }.raw
            "${chat.value.activeSessionId}: reply on server=${raw.toString().contains(mark)}"
        }
        fail("background chats whose reply never reached the client after the reconnect: $onServer")
    }

    // ── The phone ──────────────────────────────────────────────────────────

    private inner class Phone(throughRelay: Boolean = true) {
        val events = CopyOnWriteArrayList<GatewayEvent>()
        val state = MutableStateFlow(ChatUiState())
        val gateway: OkHttpGatewayClient
        val repo: SessionRepository
        val chat: ChatSessionDelegate

        init {
            // Transport settings copied from GatewayModule.provideOkHttpClient.
            val http = OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .apply { if (throughRelay) socketFactory(relay.socketFactory) }
                .build()
            // Serializer settings copied from GatewayModule.provideJson.
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = false
            }
            gateway = OkHttpGatewayClient(http, json, mockk<Context>(relaxed = true))
            gateways += gateway
            scope.launch(start = CoroutineStart.UNDISPATCHED) { gateway.events.collect { events += it } }
            repo = SessionRepository(gateway, NoTasks)
            chat = ChatSessionDelegate(gateway, repo, scope) {}
        }

        fun eventsSince(index: Int): List<GatewayEvent> = events.toList().drop(index)
    }

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    private fun connectedPhone(): Phone {
        val phone = Phone()
        val state = runBlocking { phone.gateway.connect(url!!) }
        assertTrue("could not connect to the live gateway: $state", state is ConnectionState.Connected)
        runBlocking { phone.chat.create(phone.state) }
        assertNotNull("session.create failed", phone.state.value.activeSessionId)
        return phone
    }

    /** prompt.submit the way ChatViewModel.sendPrompt sends it; returns the live id that took it. */
    private fun submit(phone: Phone, text: String): String = runBlocking {
        val current = phone.state.value
        val live = checkNotNull(current.activeSessionId)
        phone.repo.onLiveSession(
            liveId = live,
            storedId = current.activeSessionKey,
            onRebound = { phone.chat.adoptRebound(phone.state, live, it) },
        ) { liveId ->
            phone.gateway.request(
                GatewayMethods.PROMPT_SUBMIT,
                mapOf("session_id" to JsonPrimitive(liveId), "text" to JsonPrimitive(text)),
            )
            liveId
        }
    }

    /** ChatViewModel after a reconnect: snapshot now, and once more when a running turn ends. */
    private fun recoverLikeTheApp(phone: Phone, live: String, cutAt: Int) {
        val running = snapshot(phone, turnEnded = false)
        assertNotNull("recovery after the drop applied nothing", running)
        if (running == true) {
            val current = phone.state.value.activeSessionId
            awaitEvent(phone, cutAt, 120_000, "the end of the recovered turn") {
                it is GatewayEvent.MessageComplete && (it.sessionId == live || it.sessionId == current)
            }
            snapshot(phone, turnEnded = true)
        }
    }

    private fun snapshot(phone: Phone, turnEnded: Boolean): Boolean? = runBlocking {
        val current = phone.state.value
        phone.chat.recover(phone.state, checkNotNull(current.activeSessionId), current.activeSessionKey, turnEnded)
    }

    private fun awaitReconnect(phone: Phone) = runBlocking {
        withTimeout(30_000) { phone.gateway.connectionState.first { it !is ConnectionState.Connected } }
        withTimeout(60_000) { phone.gateway.connectionState.first { it is ConnectionState.Connected } }
    }

    private fun awaitConnected(phone: Phone, timeoutMs: Long) = runBlocking {
        withTimeout(timeoutMs) { phone.gateway.connectionState.first { it is ConnectionState.Connected } }
    }

    private fun awaitEvent(
        phone: Phone,
        from: Int,
        timeoutMs: Long,
        what: String,
        predicate: (GatewayEvent) -> Boolean,
    ): GatewayEvent {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            phone.eventsSince(from).firstOrNull(predicate)?.let { return it }
            Thread.sleep(100)
        }
        val seen = phone.eventsSince(from).groupingBy { it::class.simpleName }.eachCount()
        throw AssertionError("timed out after $timeoutMs ms waiting for $what; saw $seen")
    }

    private fun marker() = "PKT" + System.nanoTime().toString().takeLast(9)

    private fun shortPrompt(mark: String) = "Reply with just the word $mark. No tools."

    private fun longPrompt(mark: String) =
        "Reply with the word $mark and then the numbers from 1 to 120 separated by spaces. No tools, no other text."

    private fun ChatUiState.users(mark: String) =
        messages.count { it is ChatMessage.User && it.text.contains(mark) }

    private fun ChatUiState.replies(mark: String) =
        messages.count { it is ChatMessage.Assistant && it.text.contains(mark) }

    // ── The link ───────────────────────────────────────────────────────────

    /** TCP relay in front of the gateway: the test drops, freezes or refuses the phone's link. */
    private class Relay(target: URI) : AutoCloseable {
        private val host = target.host
        private val port = if (target.port > 0) target.port else 443
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val links = CopyOnWriteArrayList<Link>()

        /** New connections are closed on accept — the phone has no network. */
        @Volatile var refusing = false

        val socketFactory: SocketFactory = object : SocketFactory() {
            override fun createSocket(): Socket = object : Socket() {
                override fun connect(endpoint: SocketAddress?, timeout: Int) =
                    super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), timeout)
            }

            override fun createSocket(host: String?, port: Int): Socket = unsupported()
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()
            override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                unsupported()

            private fun unsupported(): Socket = throw UnsupportedOperationException("OkHttp connects unconnected sockets")
        }

        init {
            thread(isDaemon = true, name = "relay-accept") { acceptLoop() }
        }

        private fun acceptLoop() {
            while (!server.isClosed) {
                val phone = try {
                    server.accept()
                } catch (e: Exception) {
                    return
                }
                if (refusing) {
                    phone.close()
                    continue
                }
                val upstream = try {
                    Socket(host, port)
                } catch (e: Exception) {
                    phone.close()
                    continue
                }
                links += Link(phone, upstream).also { it.start() }
            }
        }

        /** Both legs close: an app kill or a network switch the server hears about. */
        fun cut() {
            links.forEach { it.close(force = true) }
            links.clear()
        }

        /** Bytes stop in both directions and nobody is told: a phone that lost signal. */
        fun freeze() {
            links.forEach { it.frozen = true }
        }

        override fun close() {
            refusing = true
            cut()
            server.close()
        }

        private class Link(private val phone: Socket, private val upstream: Socket) {
            @Volatile var frozen = false

            fun start() {
                pump(phone.getInputStream(), upstream.getOutputStream())
                pump(upstream.getInputStream(), phone.getOutputStream())
            }

            private fun pump(from: InputStream, to: OutputStream) = thread(isDaemon = true) {
                val buffer = ByteArray(16 * 1024)
                try {
                    while (true) {
                        val n = from.read(buffer)
                        if (n < 0) break
                        while (frozen && !phone.isClosed) Thread.sleep(200)
                        if (frozen) break
                        to.write(buffer, 0, n)
                        to.flush()
                    }
                } catch (e: Exception) {
                    // The other side went away.
                }
                close(force = false)
            }

            /** A frozen link never tells the server: only the phone's leg closes. */
            fun close(force: Boolean) {
                runCatching { phone.close() }
                if (force || !frozen) runCatching { upstream.close() }
            }
        }
    }
}
