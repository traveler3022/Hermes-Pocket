package com.hermes.android.gateway

import kotlinx.coroutines.CoroutineScope
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
) : GatewayClient {

    private val scope = CoroutineScope(SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(
        replay = 5, // Fix S3F01: buffer last 5 events for reconnect replay
        extraBufferCapacity = 256,
    )
    override val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null

    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<JsonElement>>()

    private var lastSessionId: String? = null

    override suspend fun connect(
        url: String,
        connectTimeoutMs: Long,
    ): ConnectionState {
        // Idempotent — if already connected, return immediately
        if (_connectionState.value is ConnectionState.Connected) {
            return _connectionState.value
        }
        // Cancel any pending reconnect
        reconnectJob?.cancel()
        connectJob?.cancel()

        currentUrl = url
        _connectionState.value = ConnectionState.Connecting

        val connectDeferred = kotlinx.coroutines.CompletableDeferred<ConnectionState>()
        connectJob = scope.launch {
            doConnect(url, connectDeferred, connectTimeoutMs)
        }

        return connectDeferred.await()
    }

    private suspend fun doConnect(
        url: String,
        deferred: kotlinx.coroutines.CompletableDeferred<ConnectionState>,
        timeoutMs: Long,
    ) {
        try {
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
                        // Session resume on reconnect
                        if (lastSessionId != null) {
                            scope.launch { resumeSession(lastSessionId!!) }
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
                _connectionState.value = ConnectionState.Failed("Connect timeout after ${timeoutMs}ms")
                deferred.complete(_connectionState.value)
            }
        } catch (e: Exception) {
            Timber.e(e, "[Gateway] connect() failed")
            _connectionState.value = ConnectionState.Failed(e.message ?: "Connect failed")
            if (!deferred.isCompleted) {
                deferred.complete(_connectionState.value)
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
    }

    override suspend fun request(
        method: String,
        params: Map<String, JsonElement>,
        timeoutMs: Long,
    ): JsonElement {
        val state = _connectionState.value
        if (state !is ConnectionState.Connected) {
            throw GatewayException("Not connected (state: $state)")
        }

        val id = nextRequestId.getAndIncrement()
        val request = GatewayRequest(id = id, method = method, params = params)
        val requestJson = json.encodeToString(GatewayRequest.serializer(), request)

        val deferred = kotlinx.coroutines.CompletableDeferred<JsonElement>()
        pendingRequests[id] = deferred

        val ws = webSocket
        if (ws == null) {
            pendingRequests.remove(id)
            throw GatewayException("WebSocket is null")
        }

        if (!ws.send(requestJson)) {
            pendingRequests.remove(id)
            throw GatewayException("Failed to send WebSocket message")
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: run {
                pendingRequests.remove(id)
                throw GatewayException("Request $method timed out after ${timeoutMs}ms")
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
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

    // ── Reconnection ───────────────────────────────────────────────────────

    private fun handleDisconnect(reason: String) {
        Timber.w("[Gateway] disconnected: $reason")
        webSocket = null
        // Fail all pending requests
        pendingRequests.values.forEach { it.completeExceptionally(GatewayException("Disconnected: $reason")) }
        pendingRequests.clear()

        if (_connectionState.value is ConnectionState.Disconnected ||
            _connectionState.value is ConnectionState.Failed) {
            return // User-initiated disconnect, don't reconnect
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch { reconnect() }
    }

    private suspend fun reconnect() {
        var attempt = 0
        while (_connectionState.value !is ConnectionState.Connected &&
            _connectionState.value !is ConnectionState.Disconnected &&
            _connectionState.value !is ConnectionState.Failed) {

            attempt++
            val delayMs = min(MAX_RECONNECT_DELAY_MS, INITIAL_RECONNECT_DELAY_MS * (1L shl (attempt - 1)))
            _connectionState.value = ConnectionState.Reconnecting(
                attempt = attempt,
                nextAttemptInMs = delayMs,
                lastError = null,
            )

            Timber.i("[Gateway] reconnect attempt $attempt in ${delayMs}ms")
            delay(delayMs)

            val url = currentUrl ?: return
            val deferred = kotlinx.coroutines.CompletableDeferred<ConnectionState>()
            connectJob = scope.launch { doConnect(url, deferred, 15_000) }

            try {
                val result = deferred.await()
                if (result is ConnectionState.Connected) {
                    Timber.i("[Gateway] reconnected on attempt $attempt")
                    return
                }
            } catch (e: Exception) {
                Timber.w("[Gateway] reconnect attempt $attempt failed: ${e.message}")
            }
        }
    }

    // ── Session resume ─────────────────────────────────────────────────────

    private suspend fun resumeSession(sessionId: String) {
        try {
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = request(GatewayMethods.SESSION_RESUME, jsonToElementMap(params))
            Timber.i("[Gateway] session resumed: $sessionId")
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

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("[Gateway] WebSocket open")
            onState(WsState.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text, onState)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8(), onState)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("[Gateway] WebSocket closing: $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("[Gateway] WebSocket closed: $code $reason")
            onState(WsState.Closed(reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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
            Timber.w("[Gateway] no pending request for id=$id")
            return
        }

        if (response.error != null) {
            deferred.completeExceptionally(
                GatewayException("RPC error ${response.error.code}: ${response.error.message}")
            )
        } else if (response.result != null) {
            (response.result as? JsonObject)?.get("session_id")?.let { sidEl ->
                (sidEl as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { sid ->
                    lastSessionId = sid
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
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
    }
}
