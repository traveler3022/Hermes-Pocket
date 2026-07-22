package com.hermes.android.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * OkHttp implementation of [GatewayClient].
 *
 * ## Responsibilities
 * - Manages a single WebSocket connection to the tui_gateway
 * - Serializes/deserializes JSON-RPC 2.0 messages
 * - Routes responses to pending requests by `id`
 * - Parses events into [GatewayEvent] sealed class instances
 * - Implements exponential backoff reconnection (mobile-network friendly)
 *
 * ## Phase 1.5 compliance
 * - This is the ONLY file in `gateway/` that imports OkHttp
 * - Only `di/GatewayModule.kt` references this class
 * - ViewModel/UI never import this class — they use the [GatewayClient] interface
 *
 * Reference: `tui_gateway/ws.py` (wire protocol), `ui-tui/src/gatewayClient.ts` (TS reference)
 */
@Singleton
class OkHttpGatewayClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : GatewayClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Telegram-style: the moment ANY network comes back, dial immediately
        // instead of sleeping out a backoff window. Registered once for the
        // process lifetime (this is a @Singleton).
        registerNetworkCallback()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Fix: replay=5 could re-deliver stale MessageDelta/MessageComplete/ToolStart
    // events to a freshly (re)subscribed collector (e.g. retryConnection()),
    // potentially duplicating streamed text. finalizeOrphanedStreamingMessage()
    // already resets isStreaming/activeAssistantMessageId before any retry can
    // re-collect, so nothing actually needs the old events replayed — 0 removes
    // the risk entirely instead of relying on that ordering.
    private val _events = MutableSharedFlow<GatewayEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    override val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var currentUrl: String? = null
    @Volatile
    private var reconnectJob: Job? = null

    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<JsonElement>>()

    @Volatile
    private var lastSessionId: String? = null

    /** HTTP status code from the last connection failure (for permanent error detection). */
    @Volatile
    private var lastHttpError: Int? = null

    /** Timestamp of the first failure in the current reconnect window. */
    @Volatile
    private var firstFailureAt: Long? = null

    /** Whether the last error was permanent (401/403/404). */
    @Volatile
    private var lastErrorPermanent: Boolean = false

    /** Request ids whose responses must NOT update [lastSessionId] (see
     *  GatewayClient.request's trackSession param). */
    private val nonTrackingRequestIds =
        java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    override suspend fun connect(
        url: String,
        connectTimeoutMs: Long,
    ): ConnectionState {
        // Idempotent — if already connected, return immediately. But verify
        // the socket actually exists: a stale Connected (socket died, state
        // never downgraded) must fall through and dial, not no-op.
        if (_connectionState.value is ConnectionState.Connected) {
            if (webSocket != null) return _connectionState.value
            Timber.w("[Gateway] state=Connected but socket is null — re-dialing")
        }

        currentUrl = url
        // Reset the failure window on manual retry
        firstFailureAt = null
        lastErrorPermanent = false
        if (_connectionState.value !is ConnectionState.Reconnecting) {
            _connectionState.value = ConnectionState.Connecting
        }

        val result = startDial(url, connectTimeoutMs).await()
        // Self-heal: a failed dial must never be terminal. As long as the
        // user hasn't explicitly disconnect()ed, keep a background retry
        // loop alive.
        if (result !is ConnectionState.Connected &&
            _connectionState.value !is ConnectionState.Disconnected
        ) {
            scheduleReconnect()
        }
        return result
    }

    /**
     * SINGLE-FLIGHT dialing — the fix for the "218 connection attempts, none
     * ever completes" storm. Multiple dial sources exist (the reconnect loop,
     * the network callback, foreground onStart, and every request()'s
     * dial-on-demand); when each opened its own socket, every new attempt
     * CLOSED the previous attempt's still-handshaking socket (doConnect closes
     * webSocket first), so on any link where the handshake takes longer than
     * the gap between dial triggers, no attempt ever survived to
     * gateway.ready. Now there is at most ONE dial in flight, it runs on the
     * client's own scope (cancelling a caller never kills the dial), and
     * every other path joins its result.
     */
    @Volatile
    private var inFlightDial: kotlinx.coroutines.CompletableDeferred<ConnectionState>? = null

    private fun startDial(
        url: String,
        timeoutMs: Long = 15_000,
    ): kotlinx.coroutines.CompletableDeferred<ConnectionState> = synchronized(this) {
        inFlightDial?.let { existing ->
            if (!existing.isCompleted) return existing
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<ConnectionState>()
        inFlightDial = deferred
        scope.launch {
            try {
                doConnect(url, deferred, timeoutMs, quietFailure = true)
            } finally {
                if (inFlightDial === deferred) inFlightDial = null
                if (!deferred.isCompleted) {
                    deferred.complete(ConnectionState.Failed("dial cancelled"))
                }
            }
        }
        deferred
    }

    private suspend fun doConnect(
        url: String,
        deferred: kotlinx.coroutines.CompletableDeferred<ConnectionState>,
        timeoutMs: Long,
        // From the reconnect loop: report the failure via the deferred only,
        // without stamping the terminal-looking Failed state — the loop shows
        // Reconnecting and keeps going.
        quietFailure: Boolean = false,
    ) {
        try {
            // Always close any existing socket before opening a new one. Both
            // the initial connect() and the reconnect() loop funnel through
            // here, so this is the single place that guarantees we never leave
            // an orphaned WebSocket alive on the gateway.
            val oldSocket = synchronized(this) {
                val socket = webSocket
                webSocket = null
                socket
            }
            oldSocket?.close(1000, "reconnecting")

            val request = Request.Builder().url(url).build()
            val listener = GatewayWebSocketListener { state ->
                when (state) {
                    is WsState.Opened -> {
                        // Wait for gateway.ready event (handled in onMessage)
                    }
                    is WsState.Ready -> {
                        if (!deferred.isCompleted) {
                            _connectionState.value = ConnectionState.Connected(state.sessionId)
                            deferred.complete(_connectionState.value)
                            // Session resume on reconnect. Capture into a local so
                            // a concurrent write to lastSessionId can't null it out
                            // between the check and the resume call.
                            lastSessionId?.let { sid ->
                                scope.launch { resumeSession(sid) }
                            }
                        }
                    }
                    is WsState.Closed -> {
                        handleDisconnect(state.reason)
                        if (!deferred.isCompleted) {
                            deferred.complete(_connectionState.value)
                        }
                    }
                    is WsState.Failure -> {
                        handleDisconnect(state.error.message ?: "WebSocket failure")
                        if (!deferred.isCompleted) {
                            deferred.complete(_connectionState.value)
                        }
                    }
                }
            }

            val newSocket = httpClient.newWebSocket(request, listener)
            synchronized(this) {
                webSocket = newSocket
            }

            // Wait for ready or timeout
            withTimeoutOrNull(timeoutMs) {
                // The deferred completes when gateway.ready arrives
                deferred.await()
            }
            if (!deferred.isCompleted) {
                val failed = ConnectionState.Failed("Connect timeout after ${timeoutMs}ms")
                if (!quietFailure) _connectionState.value = failed
                deferred.complete(failed)
                // Close the socket to prevent a late gateway.ready from flipping state
                webSocket?.close(1000, "connect timeout")
                webSocket = null
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // NEVER swallow cancellation into a Failed state — that turned a
            // routine job cancel into a phantom connection failure.
            if (!deferred.isCompleted) deferred.complete(ConnectionState.Failed("dial cancelled"))
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "[Gateway] connect() failed")
            val failed = ConnectionState.Failed(e.message ?: "Connect failed")
            if (!quietFailure) _connectionState.value = failed
            if (!deferred.isCompleted) {
                deferred.complete(failed)
            }
        }
    }

    override suspend fun disconnect() {
        val ws = synchronized(this) {
            reconnectJob?.cancel()
            // Setting Disconnected FIRST makes any in-flight dial's success moot;
            // startDial's finally also resolves its deferred for joiners.
            val socket = webSocket
            webSocket = null
            _connectionState.value = ConnectionState.Disconnected
            // Fail all pending requests
            pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Disconnected")) }
            pendingRequests.clear()
            nonTrackingRequestIds.clear()
            socket
        }
        // Close outside the lock (network call)
        ws?.close(1000, "client disconnect")
    }

    override suspend fun request(
        method: String,
        params: Map<String, JsonElement>,
        timeoutMs: Long,
        trackSession: Boolean,
    ): JsonElement {
        var state = _connectionState.value
        if (state is ConnectionState.Connected && webSocket == null) {
            // Stale Connected: the socket died but no callback downgraded the
            // state yet. Kick the recovery machine and fall through to the
            // dial-on-demand path below instead of dying "WebSocket is null".
            handleDisconnect("stale Connected state (socket is null)")
            state = _connectionState.value
        }
        if (state !is ConnectionState.Connected) {
            // Dial-on-demand (v2ray model): a user action is the strongest
            // possible "we need a connection NOW" signal — dial instead of
            // failing or waiting out a backoff window. connect() joins any
            // in-flight attempt, so concurrent requests share one dial.
            val url = currentUrl ?: throw GatewayException("Not connected (state: $state)")
            state = connect(url)
            if (state !is ConnectionState.Connected) {
                throw GatewayException("Not connected (state: $state)")
            }
        }

        val id = nextRequestId.getAndIncrement()
        if (!trackSession) nonTrackingRequestIds.add(id)
        val request = GatewayRequest(id = id, method = method, params = params)
        val requestJson = json.encodeToString(GatewayRequest.serializer(), request)

        val deferred = kotlinx.coroutines.CompletableDeferred<JsonElement>()
        val ws = synchronized(this) {
            pendingRequests[id] = deferred
            webSocket
        }
        if (ws == null) {
            pendingRequests.remove(id)
            nonTrackingRequestIds.remove(id)
            handleDisconnect("socket vanished mid-request")
            throw GatewayException("WebSocket is null")
        }

        if (!ws.send(requestJson)) {
            pendingRequests.remove(id)
            nonTrackingRequestIds.remove(id)
            // A refused send means the socket is dead even if no callback has
            // fired yet — arm recovery now rather than waiting for the ping
            // cycle to notice.
            handleDisconnect("send failed (socket dead)")
            throw GatewayException("Failed to send WebSocket message")
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: run {
                pendingRequests.remove(id)
                nonTrackingRequestIds.remove(id)
                throw GatewayException("Request $method timed out after ${timeoutMs}ms")
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            nonTrackingRequestIds.remove(id)
            if (e is GatewayException) throw e
            throw GatewayException("Request $method failed: ${e.message}", e)
        }
    }

    override suspend fun notify(method: String, params: Map<String, JsonElement>) {
        val state = _connectionState.value
        if (state !is ConnectionState.Connected) {
            Timber.w("[Gateway] notify() called while not connected (state: $state)")
            return
        }
        val id = nextRequestId.getAndIncrement()
        val request = GatewayRequest(id = id, method = method, params = params)
        val requestJson = json.encodeToString(GatewayRequest.serializer(), request)
        webSocket?.send(requestJson)
    }

    override suspend fun downloadFile(url: String): ByteArray = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        // Create a client with a read timeout for downloads (the shared httpClient
        // has readTimeout=0 for WebSocket, which is wrong for one-shot downloads).
        val downloadClient = httpClient.newBuilder()
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GatewayException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw GatewayException("Download failed: empty response")
            // Stream to a temp file to avoid OOM on large files
            val tempFile = java.io.File.createTempFile("download", ".tmp")
            try {
                body.source().use { source ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                        }
                        Timber.d("[Gateway] Downloaded $bytesRead bytes to ${tempFile.absolutePath}")
                    }
                }
                // Read the file back into memory (caller expects ByteArray)
                tempFile.readBytes()
            } finally {
                tempFile.delete()
            }
        }
    }

    // ── Reconnection ───────────────────────────────────────────────────────

    private fun handleDisconnect(reason: String) {
        Timber.w("[Gateway] disconnected: $reason")
        synchronized(this) {
            webSocket = null
            // Fail all pending requests
            pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Disconnected: $reason")) }
            pendingRequests.clear()
            nonTrackingRequestIds.clear()

            // Only a user-initiated disconnect() stops the machine. Failed is NOT
            // terminal — treating it as terminal is what used to strand the app
            // offline until a force-stop.
            if (_connectionState.value is ConnectionState.Disconnected) return

            // CRITICAL: downgrade the state. Nothing else does — and a live-drop
            // used to leave state=Connected with webSocket=null, so the reconnect
            // loop saw "Connected" and returned instantly, connect() early-returned
            // "already connected", dial-on-demand never fired, and every request
            // died with "WebSocket is null" until a force-stop. This one line is
            // what actually arms the whole recovery machine.
            if (_connectionState.value is ConnectionState.Connected ||
                _connectionState.value is ConnectionState.Connecting
            ) {
                _connectionState.value = ConnectionState.Reconnecting(
                    attempt = 0,
                    nextAttemptInMs = 0,
                    lastError = reason,
                )
            }
        }
        // Schedule reconnect outside the lock (it acquires its own lock)
        scheduleReconnect()
    }

    /** Idempotent: keeps exactly one retry loop alive. */
    private fun scheduleReconnect() {
        synchronized(this) {
            if (reconnectJob?.isActive == true) return
            reconnectJob = scope.launch { reconnect() }
        }
    }

    /**
     * Retry until Connected or user disconnect(). NEVER gives up on failure —
     * the connection is disposable, the server state is the source of truth,
     * so the only job here is to get a fresh pipe as soon as one is possible
     * (Telegram model). Backoff is capped low; the network callback and
     * dial-on-demand cut the wait entirely when there's a better signal.
     */
    private suspend fun reconnect() {
        var attempt = 0
        var lastReason: String? = null
        
        // Initialize the failure window on first entry
        if (firstFailureAt == null) {
            firstFailureAt = System.currentTimeMillis()
        }
        
        while (true) {
            when (_connectionState.value) {
                is ConnectionState.Connected -> {
                    // Success — reset the failure window
                    firstFailureAt = null
                    lastErrorPermanent = false
                    return
                }
                is ConnectionState.Disconnected -> return // user asked to stop
                else -> Unit
            }
            
            // Check for permanent error (401/403/404)
            if (lastErrorPermanent) {
                val reason = "Permanent error: HTTP ${lastHttpError ?: "unknown"}"
                Timber.e("[Gateway] $reason — stopping reconnect")
                _connectionState.value = ConnectionState.Failed(reason)
                return
            }
            
            // Check if we've exceeded the reconnect window
            val elapsed = System.currentTimeMillis() - (firstFailureAt ?: System.currentTimeMillis())
            if (elapsed > MAX_RECONNECT_WINDOW_MS) {
                val reason = "Reconnect timeout after ${elapsed / 1000}s (last: $lastReason)"
                Timber.e("[Gateway] $reason — stopping reconnect")
                _connectionState.value = ConnectionState.Failed(reason)
                return
            }
            
            attempt++
            // Exponent clamped BEFORE shifting: the old `1L shl (attempt-1)`
            // wrapped negative past attempt 63.
            val baseDelayMs = min(
                MAX_RECONNECT_DELAY_MS,
                INITIAL_RECONNECT_DELAY_MS shl min(attempt - 1, RECONNECT_BACKOFF_MAX_EXP),
            )
            // Add ±20% jitter to avoid thundering herd (though for a single-client
            // personal app this is mostly theoretical).
            val jitter = (baseDelayMs * 0.2 * (Math.random() * 2 - 1)).toLong()
            val delayMs = baseDelayMs + jitter
            // Carry the previous attempt's failure REASON into the state so
            // the UI/notification can show WHY it keeps reconnecting — the
            // difference between debuggable and "it just spins forever".
            _connectionState.value = ConnectionState.Reconnecting(
                attempt = attempt,
                nextAttemptInMs = delayMs,
                lastError = lastReason,
            )
            Timber.i("[Gateway] reconnect attempt $attempt in ${delayMs}ms (last: $lastReason, elapsed: ${elapsed / 1000}s)")
            delay(delayMs)

            val url = currentUrl ?: return
            try {
                when (val result = startDial(url).await()) {
                    is ConnectionState.Connected -> {
                        Timber.i("[Gateway] reconnected on attempt $attempt")
                        firstFailureAt = null
                        lastErrorPermanent = false
                        return
                    }
                    is ConnectionState.Failed -> lastReason = result.reason
                    else -> Unit
                }
            } catch (e: Exception) {
                lastReason = e.message
                Timber.w("[Gateway] reconnect attempt $attempt failed: ${e.message}")
            }
        }
    }

    /**
     * Network came back (or changed) — dial NOW instead of waiting out a
     * backoff window. Single-flight makes this safe: if a dial is already in
     * flight we join it; the sleeping loop discovers the result on its own
     * schedule. Nothing gets cancelled mid-handshake anymore.
     */
    private fun registerNetworkCallback() {
        try {
            val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val url = currentUrl ?: return
                    val state = _connectionState.value
                    if (state is ConnectionState.Connected || state is ConnectionState.Disconnected) return
                    Timber.i("[Gateway] network available — dialing immediately")
                    // Reset the failure window when network comes back
                    firstFailureAt = null
                    lastErrorPermanent = false
                    startDial(url)
                    scheduleReconnect() // safety net if this dial fails
                }
            })
        } catch (e: Exception) {
            // Missing permission / restricted context — degrade to backoff-only.
            Timber.w(e, "[Gateway] network callback unavailable")
        }
    }

    // ── Session resume ─────────────────────────────────────────────────────

    /**
     * Fix: this used to fire-and-forget — call session.resume and throw the
     * response away without even reading the live session_id it returns
     * (session.resume mints a NEW live id bound to the old transcript; the
     * original id it was called with stops being valid for prompt.submit).
     * ChatViewModel had no way to learn that id, so on reconnect it fell back
     * to its own independent session.most_recent + resume call — a second,
     * uncoordinated session.resume RPC racing this one on every reconnect.
     * Now we parse the returned session_id, adopt it as lastSessionId, and
     * re-publish it via connectionState so ChatViewModel can adopt the SAME
     * resumed session instead of resuming it a second time itself.
     */
    private suspend fun resumeSession(sessionId: String) {
        try {
            val params = buildJsonObject { put("session_id", sessionId) }
            // lastSessionId is a LIVE id (that's what responses/events carry),
            // but session.resume resolves STORED db ids and 4007s on live ones
            // — so this auto-resume was silently failing every time. Attach to
            // the still-live session via session.activate first; fall back to
            // resume for the (stored-id / reaped-session) cases.
            val result = try {
                request(GatewayMethods.SESSION_ACTIVATE, jsonToElementMap(params))
            } catch (activateError: Exception) {
                Timber.w("[Gateway] activate failed (${activateError.message}); trying session.resume")
                request(GatewayMethods.SESSION_RESUME, jsonToElementMap(params))
            }
            val liveId = (result as? JsonObject)?.get("session_id")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() } ?: sessionId
            lastSessionId = liveId
            Timber.i("[Gateway] session resumed: $sessionId -> live $liveId")
            _connectionState.value = ConnectionState.Connected(liveId)
        } catch (e: Exception) {
            Timber.w("[Gateway] session resume failed, creating new: ${e.message}")
            lastSessionId = null
            // Will create a new session in Step 4
        }
    }

    private fun jsonToElementMap(obj: JsonObject): Map<String, JsonElement> =
        obj.toMap()

    // ── WebSocket listener ─────────────────────────────────────────────────

    private enum class WsStateKind { OPENED, READY, CLOSED, FAILURE }
    private sealed class WsState {
        object Opened : WsState()
        data class Ready(val sessionId: String?) : WsState()
        data class Closed(val reason: String) : WsState()
        data class Failure(val error: Throwable) : WsState()
    }

    private inner class GatewayWebSocketListener(
        private val onState: (WsState) -> Unit,
    ) : WebSocketListener() {

        // doConnect() closes the previous socket before opening a new one
        // (webSocket?.close(...) then webSocket = null then webSocket =
        // newWebSocket(...)). OkHttp still delivers that old socket's
        // onClosed/onFailure asynchronously, sometimes AFTER the new one is
        // already assigned — a stale callback from a listener bound to a
        // socket we've already abandoned. Without this guard it fires
        // handleDisconnect() on the new, healthy connection: webSocket = null
        // clobbers the live reference and a second reconnectJob spins up
        // fighting the working one. On a real device this reproduced as "tap
        // retry, nothing happens" — only a full app force-stop cleared the
        // stuck coroutines. Each callback's own webSocket param always
        // matches the exact socket THIS listener is attached to (OkHttp's
        // 1:1 listener/socket contract), so comparing it against the
        // currently-tracked field tells a stale callback from a live one.
        private fun isCurrent(socket: WebSocket) = socket === webSocket

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent(webSocket)) return
            Timber.d("[Gateway] WebSocket open")
            onState(WsState.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket)) return
            handleMessage(text, onState)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!isCurrent(webSocket)) return
            handleMessage(bytes.utf8(), onState)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("[Gateway] WebSocket closing: $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket)) {
                Timber.d("[Gateway] Ignoring onClosed from a stale/replaced socket: $reason")
                return
            }
            Timber.w("[Gateway] WebSocket closed: $code $reason")
            onState(WsState.Closed(reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket)) {
                Timber.d("[Gateway] Ignoring onFailure from a stale/replaced socket: ${t.message}")
                return
            }
            // Capture HTTP status code for permanent error detection
            response?.code?.let { code ->
                lastHttpError = code
                lastErrorPermanent = code in listOf(401, 403, 404)
                if (lastErrorPermanent) {
                    Timber.e("[Gateway] Permanent HTTP error: $code")
                }
            }
            Timber.e(t, "[Gateway] WebSocket failure")
            onState(WsState.Failure(t))
        }
    }

    private fun handleMessage(raw: String, onState: (WsState) -> Unit = {}) {
        try {
            val element = json.parseToJsonElement(raw)
            if (element !is JsonObject) {
                Timber.w("[Gateway] non-object message: $raw")
                return
            }
            val obj = element.jsonObject

            // Check if it's a response (has "id") or an event (has "method" == "event")
            if ("id" in obj) {
                handleResponse(obj)
            } else if (obj["method"]?.jsonPrimitive?.content == "event") {
                handleEvent(obj, onState)
            } else {
                Timber.w("[Gateway] unknown message shape: ${raw.take(200)}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[Gateway] failed to parse message: ${raw.take(200)}")
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
        val response = json.decodeFromJsonElement(GatewayResponse.serializer(), obj)

        val deferred = pendingRequests.remove(id) ?: run {
            nonTrackingRequestIds.remove(id)
            Timber.w("[Gateway] no pending request for id=$id")
            return
        }
        val skipSessionTracking = nonTrackingRequestIds.remove(id)

        if (response.error != null) {
            deferred.completeExceptionally(
                GatewayException("RPC error ${response.error.code}: ${response.error.message}")
            )
        } else if (response.result != null) {
            if (!skipSessionTracking) {
                (response.result as? JsonObject)?.get("session_id")?.let { sidEl ->
                    (sidEl as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { sid ->
                        lastSessionId = sid
                    }
                }
            }
            deferred.complete(response.result)
        } else {
            deferred.completeExceptionally(GatewayException("Response has neither result nor error"))
        }
    }

    private fun handleEvent(obj: JsonObject, onState: (WsState) -> Unit = {}) {
        val params = obj["params"]?.jsonObject ?: return
        val eventType = (params["event"] ?: params["type"])?.jsonPrimitive?.content ?: return
        val sid = (params["sid"] ?: params["session_id"])?.jsonPrimitive?.content
        val payload = params["payload"]?.jsonObject ?: JsonObject(emptyMap())

        val event = parseEvent(eventType, sid, payload)
        if (event is GatewayEvent.GatewayReady) {
            // gateway.ready is the transport-level handshake that connect()
            // waits for. Without this, the socket can be open while the app
            // remains stuck in Connecting until timeout.
            onState(WsState.Ready(event.sessionId))
            // Track last session for resume
            // (gateway.ready usually doesn't carry a session id, but preserve
            // it if a future server version includes one.)
            if (event.sessionId != null) {
                lastSessionId = event.sessionId
            }
        } else if (event.sessionId != null) {
            lastSessionId = event.sessionId
        }
        if (!_events.tryEmit(event)) {
            Timber.w("[Gateway] Event buffer full, dropped: $eventType")
        }
    }

    private fun parseEvent(
        eventType: String,
        sid: String?,
        payload: JsonObject,
    ): GatewayEvent {
        val p = payload
        return when (eventType) {
            "gateway.ready" -> GatewayEvent.GatewayReady(
                sessionId = sid,
                skin = p["skin"]?.let { GatewayEventHelpers.parseSkinMap(it) },
            )
            "gateway.stderr" -> GatewayEvent.GatewayStderr(sid, p["line"]?.jsonPrimitive?.content ?: "")
            "gateway.start_timeout" -> GatewayEvent.GatewayStartTimeout(
                sid,
                p["cwd"]?.jsonPrimitive?.content,
                p["python"]?.jsonPrimitive?.content,
                p["stderr_tail"]?.jsonPrimitive?.content,
            )
            "gateway.protocol_error" -> GatewayEvent.GatewayProtocolError(
                sid, p["preview"]?.jsonPrimitive?.content,
            )
            "session.info" -> GatewayEvent.SessionInfo(sid, p.toMap())
            "message.start" -> GatewayEvent.MessageStart(sid)
            "message.delta" -> GatewayEvent.MessageDelta(
                sid,
                p["text"]?.jsonPrimitive?.content ?: "",
                p["rendered"]?.jsonPrimitive?.content,
            )
            "message.complete" -> GatewayEvent.MessageComplete(
                sid,
                p["text"]?.jsonPrimitive?.content ?: "",
                p["rendered"]?.jsonPrimitive?.content,
                p["reasoning"]?.jsonPrimitive?.content,
                p["usage"]?.jsonObject?.toMap()
                    ?.mapNotNull { (k, v) -> v.jsonPrimitive.content.toLongOrNull()?.let { k to it } }?.toMap(),
            )
            "thinking.delta" -> GatewayEvent.ThinkingDelta(sid, p["text"]?.jsonPrimitive?.content ?: "")
            "reasoning.delta" -> GatewayEvent.ReasoningDelta(sid, p["text"]?.jsonPrimitive?.content ?: "")
            "reasoning.available" -> GatewayEvent.ReasoningAvailable(sid, p["text"]?.jsonPrimitive?.content)
            "status.update" -> GatewayEvent.StatusUpdate(
                sid,
                p["kind"]?.jsonPrimitive?.content,
                p["text"]?.jsonPrimitive?.content,
            )
            "tool.start" -> GatewayEvent.ToolStart(
                sid,
                p["tool_id"]?.jsonPrimitive?.content ?: "",
                p["name"]?.jsonPrimitive?.content,
                p["args_text"]?.jsonPrimitive?.content,
                p["context"]?.jsonPrimitive?.content,
                todos = p["todos"]?.let { GatewayEventHelpers.parseTodos(it) },
            )
            "tool.complete" -> GatewayEvent.ToolComplete(
                sid,
                p["tool_id"]?.jsonPrimitive?.content ?: "",
                p["name"]?.jsonPrimitive?.content,
                p["result"]?.jsonPrimitive?.content,
                p["result_text"]?.jsonPrimitive?.content,
                p["summary"]?.jsonPrimitive?.content,
                p["duration_s"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                p["inline_diff"]?.jsonPrimitive?.content,
                error = p["error"]?.jsonPrimitive?.content,
                todos = p["todos"]?.let { GatewayEventHelpers.parseTodos(it) },
            )
            "tool.generating" -> GatewayEvent.ToolGenerating(sid, p["name"]?.jsonPrimitive?.content)
            "tool.progress" -> GatewayEvent.ToolProgress(
                sid,
                p["name"]?.jsonPrimitive?.content,
                p["preview"]?.jsonPrimitive?.content,
            )
            "approval.request" -> GatewayEvent.ApprovalRequest(
                sid,
                p["command"]?.jsonPrimitive?.content ?: "",
                p["description"]?.jsonPrimitive?.content ?: "",
                p["pattern_keys"]?.let { GatewayEventHelpers.parseStringList(it) } ?: emptyList(),
                // Absent means unrestricted — only an explicit false hides "always allow"
                allowPermanent = p["allow_permanent"]?.jsonPrimitive?.content != "false",
            )
            "clarify.request" -> GatewayEvent.ClarifyRequest(
                sid,
                p["request_id"]?.jsonPrimitive?.content ?: "",
                p["question"]?.jsonPrimitive?.content ?: "",
                p["choices"]?.let { GatewayEventHelpers.parseStringList(it) },
            )
            "sudo.request" -> GatewayEvent.SudoRequest(
                sid,
                p["request_id"]?.jsonPrimitive?.content ?: "",
            )
            "secret.request" -> GatewayEvent.SecretRequest(
                sid,
                p["request_id"]?.jsonPrimitive?.content ?: "",
                p["env_var"]?.jsonPrimitive?.content ?: "",
                p["prompt"]?.jsonPrimitive?.content ?: "",
            )
            "notification.show" -> GatewayEvent.NotificationShow(
                sid,
                p["key"]?.jsonPrimitive?.content,
                p["kind"]?.jsonPrimitive?.content,
                p["level"]?.jsonPrimitive?.content,
                p["text"]?.jsonPrimitive?.content,
                p["ttl_ms"]?.jsonPrimitive?.content?.toLongOrNull(),
            )
            "notification.clear" -> GatewayEvent.NotificationClear(
                sid,
                p["key"]?.jsonPrimitive?.content,
            )
            "billing.step_up.verification" -> GatewayEvent.BillingStepUpVerification(
                sid,
                p["verification_url"]?.jsonPrimitive?.content ?: "",
                p["user_code"]?.jsonPrimitive?.content,
            )
            "voice.status" -> GatewayEvent.VoiceStatus(sid, p["state"]?.jsonPrimitive?.content)
            "voice.transcript" -> GatewayEvent.VoiceTranscript(
                sid,
                p["text"]?.jsonPrimitive?.content,
                p["no_speech_limit"]?.jsonPrimitive?.content == "true",
            )
            "subagent.spawn_requested", "subagent.start", "subagent.thinking",
            "subagent.tool", "subagent.progress", "subagent.complete" -> GatewayEvent.SubagentEvent(
                sid, eventType, p.toMap(),
            )
            "background.complete" -> GatewayEvent.BackgroundComplete(
                sid,
                p["task_id"]?.jsonPrimitive?.content ?: "",
                p["text"]?.jsonPrimitive?.content ?: "",
            )
            "review.summary" -> GatewayEvent.ReviewSummary(sid, p["text"]?.jsonPrimitive?.content)
            "browser.progress" -> GatewayEvent.BrowserProgress(
                sid,
                p["level"]?.jsonPrimitive?.content,
                p["message"]?.jsonPrimitive?.content,
            )
            "skin.changed" -> GatewayEvent.SkinChanged(sid, p["skin"]?.let { GatewayEventHelpers.parseSkinMap(it) })
            "dashboard.new_session_requested" -> GatewayEvent.DashboardNewSessionRequested(
                sid, p["reason"]?.jsonPrimitive?.content,
            )
            "error" -> GatewayEvent.Error(sid, p["message"]?.jsonPrimitive?.content)
            else -> GatewayEvent.Unknown(sid, eventType, p.toMap())
        }
    }

    private fun parseSkinMap(element: JsonElement): Map<String, String> {
        return try {
            element.jsonObject.toMap().mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Mirrors `parseTodos` in `ui-tui/src/app/turnController.ts`: drop items
     * without a known status or with empty id/content instead of failing the
     * whole event.
     */
    private fun parseTodos(element: JsonElement): List<GatewayEvent.TodoItem>? {
        val array = element as? kotlinx.serialization.json.JsonArray ?: return null
        val validStatuses = setOf("pending", "in_progress", "completed", "cancelled")
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val status = obj["status"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (status !in validStatuses) return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val content = obj["content"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (id.isEmpty() || content.isEmpty()) return@mapNotNull null
            GatewayEvent.TodoItem(id = id, content = content, status = status)
        }
    }

    private fun parseStringList(element: JsonElement): List<String>? {
        return try {
            val array = when (element) {
                is kotlinx.serialization.json.JsonArray -> element
                is JsonObject -> element["choices"] as? kotlinx.serialization.json.JsonArray
                    ?: element["pattern_keys"] as? kotlinx.serialization.json.JsonArray
                else -> null
            }
            array?.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Release resources. For a @Singleton with process lifetime this is
     * technically unnecessary (the process exit cleans up), but it makes
     * the class testable and future-proof if the lifetime changes.
     */
    fun close() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "client shutdown")
        webSocket = null
        pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Client closed")) }
        pendingRequests.clear()
        nonTrackingRequestIds.clear()
        scope.cancel()
    }

    companion object {
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L

        // Capped LOW (Telegram-grade): with the network callback and
        // dial-on-demand carrying the fast paths, the loop is only a safety
        // net — but a 30s ceiling made "it eventually comes back" feel broken.
        private const val MAX_RECONNECT_DELAY_MS = 15_000L

        /** Clamp for the backoff shift: 1s,2s,4s,8s then the 15s ceiling. */
        private const val RECONNECT_BACKOFF_MAX_EXP = 4
        private const val MAX_RECONNECT_WINDOW_MS = 120_000L // 2 minutes
    }
}
