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

    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null

    // The single in-flight connect attempt. Concurrent/looping callers join
    // this instead of each opening their own WebSocket — otherwise every extra
    // open orphans the previous socket, which the gateway logs as a dead
    // connection (messages=0, reason=client_disconnect code=1006).
    @Volatile
    private var pendingConnect: kotlinx.coroutines.CompletableDeferred<ConnectionState>? = null

    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<JsonElement>>()

    private var lastSessionId: String? = null

    /** Request ids whose responses must NOT update [lastSessionId] (see
     *  GatewayClient.request's trackSession param). */
    private val nonTrackingRequestIds =
        java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    override suspend fun connect(
        url: String,
        connectTimeoutMs: Long,
    ): ConnectionState {
        // Idempotent — if already connected, return immediately
        if (_connectionState.value is ConnectionState.Connected) {
            return _connectionState.value
        }
        // A connect attempt is already in flight — join it instead of opening a
        // second WebSocket. Without this, callers that race (ChatViewModel, the
        // foreground service) or poll (TermuxBridge.waitForGatewayReady, which
        // calls connect() every 500ms) each open a fresh socket and orphan the
        // previous one, producing the messages=0 / code=1006 ghost connections
        // seen in the gateway log.
        pendingConnect?.let { existing ->
            if (!existing.isCompleted) return existing.await()
        }

        // Cancel any pending reconnect
        reconnectJob?.cancel()
        connectJob?.cancel()

        currentUrl = url
        _connectionState.value = ConnectionState.Connecting

        val connectDeferred = kotlinx.coroutines.CompletableDeferred<ConnectionState>()
        pendingConnect = connectDeferred
        connectJob = scope.launch {
            doConnect(url, connectDeferred, connectTimeoutMs)
        }

        return try {
            val result = connectDeferred.await()
            // Self-heal: a failed dial must never be terminal. As long as the
            // user hasn't explicitly disconnect()ed, keep a background retry
            // loop alive — this is the fix for the "first failed reconnect
            // set Failed and everything stopped forever until a force-stop"
            // trap (the reconnect loop refused to run while state was Failed).
            if (result !is ConnectionState.Connected &&
                _connectionState.value !is ConnectionState.Disconnected
            ) {
                scheduleReconnect()
            }
            result
        } finally {
            if (pendingConnect === connectDeferred) pendingConnect = null
        }
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
            webSocket?.close(1000, "reconnecting")
            webSocket = null

            val request = Request.Builder().url(url).build()
            val listener = GatewayWebSocketListener { state ->
                when (state) {
                    is WsState.Opened -> {
                        // Wait for gateway.ready event (handled in onMessage)
                    }
                    is WsState.Ready -> {
                        _connectionState.value = ConnectionState.Connected(state.sessionId)
                        if (!deferred.isCompleted) {
                            deferred.complete(_connectionState.value)
                        }
                        // Session resume on reconnect. Capture into a local so
                        // a concurrent write to lastSessionId can't null it out
                        // between the check and the resume call.
                        lastSessionId?.let { sid ->
                            scope.launch { resumeSession(sid) }
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

            webSocket = httpClient.newWebSocket(request, listener)

            // Wait for ready or timeout
            withTimeoutOrNull(timeoutMs) {
                // The deferred completes when gateway.ready arrives
                deferred.await()
            }
            if (!deferred.isCompleted) {
                val failed = ConnectionState.Failed("Connect timeout after ${timeoutMs}ms")
                if (!quietFailure) _connectionState.value = failed
                deferred.complete(failed)
            }
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
        reconnectJob?.cancel()
        connectJob?.cancel()
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        // Fail all pending requests
        pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Disconnected")) }
        pendingRequests.clear()
        nonTrackingRequestIds.clear()
    }

    override suspend fun request(
        method: String,
        params: Map<String, JsonElement>,
        timeoutMs: Long,
        trackSession: Boolean,
    ): JsonElement {
        var state = _connectionState.value
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
        pendingRequests[id] = deferred

        val ws = webSocket
        if (ws == null) {
            pendingRequests.remove(id)
            nonTrackingRequestIds.remove(id)
            throw GatewayException("WebSocket is null")
        }

        if (!ws.send(requestJson)) {
            pendingRequests.remove(id)
            nonTrackingRequestIds.remove(id)
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
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GatewayException("Download failed: HTTP ${response.code}")
            }
            response.body?.bytes() ?: throw GatewayException("Download failed: empty response")
        }
    }

    // ── Reconnection ───────────────────────────────────────────────────────

    private fun handleDisconnect(reason: String) {
        Timber.w("[Gateway] disconnected: $reason")
        webSocket = null
        // Fail all pending requests
        pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Disconnected: $reason")) }
        pendingRequests.clear()
        nonTrackingRequestIds.clear()

        // Only a user-initiated disconnect() stops the machine. Failed is NOT
        // terminal — treating it as terminal is what used to strand the app
        // offline until a force-stop.
        if (_connectionState.value is ConnectionState.Disconnected) return

        // Do NOT cancel-and-restart here: the reconnect loop's own failed
        // sockets fire onFailure → handleDisconnect too, and restarting the
        // loop from inside its own failure resets the backoff to zero — a
        // 1-second retry storm. Just make sure a loop exists.
        scheduleReconnect()
    }

    /** Idempotent: keeps exactly one retry loop alive. */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch { reconnect() }
    }

    /**
     * Retry until Connected or user disconnect(). NEVER gives up on failure —
     * the connection is disposable, the server state is the source of truth,
     * so the only job here is to get a fresh pipe as soon as one is possible
     * (Telegram model). Backoff is capped low; the network callback and
     * dial-on-demand cut the wait entirely when there's a better signal.
     */
    private suspend fun reconnect(startImmediately: Boolean = false) {
        var attempt = 0
        while (true) {
            when (_connectionState.value) {
                is ConnectionState.Connected -> return
                is ConnectionState.Disconnected -> return // user asked to stop
                else -> Unit
            }
            attempt++
            // Exponent clamped BEFORE shifting: the old `1L shl (attempt-1)`
            // wrapped negative past attempt 63.
            val delayMs = if (attempt == 1 && startImmediately) 0L else min(
                MAX_RECONNECT_DELAY_MS,
                INITIAL_RECONNECT_DELAY_MS shl min(attempt - 1, RECONNECT_BACKOFF_MAX_EXP),
            )
            _connectionState.value = ConnectionState.Reconnecting(
                attempt = attempt,
                nextAttemptInMs = delayMs,
                lastError = null,
            )
            Timber.i("[Gateway] reconnect attempt $attempt in ${delayMs}ms")
            if (delayMs > 0) delay(delayMs)

            val url = currentUrl ?: return
            val deferred = kotlinx.coroutines.CompletableDeferred<ConnectionState>()
            try {
                doConnect(url, deferred, 15_000, quietFailure = true)
                if (deferred.await() is ConnectionState.Connected) {
                    Timber.i("[Gateway] reconnected on attempt $attempt")
                    return
                }
            } catch (e: Exception) {
                Timber.w("[Gateway] reconnect attempt $attempt failed: ${e.message}")
            }
        }
    }

    /**
     * Network came back (or changed) — dial NOW instead of waiting out a
     * backoff window. Cancelling the sleeping loop and starting a fresh one
     * with an immediate first attempt is what makes recovery feel instant
     * the moment the screen turns on / Wi-Fi reattaches.
     */
    private fun registerNetworkCallback() {
        try {
            val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (currentUrl == null) return
                    val state = _connectionState.value
                    if (state is ConnectionState.Connected || state is ConnectionState.Disconnected) return
                    Timber.i("[Gateway] network available — dialing immediately")
                    reconnectJob?.cancel()
                    reconnectJob = scope.launch { reconnect(startImmediately = true) }
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
            val result = request(GatewayMethods.SESSION_RESUME, jsonToElementMap(params))
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
        scope.launch { _events.emit(event) }
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
                skin = p["skin"]?.let { parseSkinMap(it) },
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
                todos = p["todos"]?.let { parseTodos(it) },
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
                todos = p["todos"]?.let { parseTodos(it) },
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
                p["pattern_keys"]?.let { parseStringList(it) } ?: emptyList(),
                // Absent means unrestricted — only an explicit false hides "always allow"
                allowPermanent = p["allow_permanent"]?.jsonPrimitive?.content != "false",
            )
            "clarify.request" -> GatewayEvent.ClarifyRequest(
                sid,
                p["request_id"]?.jsonPrimitive?.content ?: "",
                p["question"]?.jsonPrimitive?.content ?: "",
                p["choices"]?.let { parseStringList(it) },
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
            "skin.changed" -> GatewayEvent.SkinChanged(sid, p["skin"]?.let { parseSkinMap(it) })
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

    companion object {
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L

        // Capped LOW (Telegram-grade): with the network callback and
        // dial-on-demand carrying the fast paths, the loop is only a safety
        // net — but a 30s ceiling made "it eventually comes back" feel broken.
        private const val MAX_RECONNECT_DELAY_MS = 15_000L

        /** Clamp for the backoff shift: 1s,2s,4s,8s then the 15s ceiling. */
        private const val RECONNECT_BACKOFF_MAX_EXP = 4
    }
}
