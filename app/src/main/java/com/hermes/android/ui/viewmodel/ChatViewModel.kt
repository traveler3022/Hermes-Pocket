package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.GatewayException
import com.hermes.android.service.ApprovalNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Chat screen.
 *
 * Depends ONLY on [GatewayClient] interface — never on OkHttp or any
 * concrete implementation. This is the abstraction boundary.
 *
 * Responsibilities:
 * - Connect to the gateway on init
 * - Subscribe to gateway events and convert them to [ChatUiState]
 * - Send user messages via `prompt.submit` RPC
 * - Send interrupt via `session.interrupt` RPC
 * - Manage session list (create, list, resume)
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency),
 *            Phase 1.5 Rule 2 (Agent Is Orchestrator — this ViewModel
 *            coordinates gateway + UI state, does NOT implement service logic)
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val hermesRuntime: com.hermes.android.runtime.HermesRuntime,
    private val approvalNotificationManager: ApprovalNotificationManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var eventCollectionJob: Job? = null
    private var connectionWatchJob: Job? = null
    private var activeAssistantMessageId: String? = null
    private val streamingBuffer = StringBuilder()
    private var streamingFlushJob: Job? = null

    init {
        connectAndCollect()
    }

    // ── Connection ────────────────────────────────────────────────────────

    private fun connectAndCollect() {
        connectionWatchJob?.cancel()
        eventCollectionJob?.cancel()
        viewModelScope.launch {
            // Watch connection state
            connectionWatchJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.connectionState.collect { state ->
                    val chatState = when (state) {
                        is ConnectionState.Disconnected -> ChatConnectionState.Disconnected
                        is ConnectionState.Connecting -> ChatConnectionState.Connecting
                        is ConnectionState.Connected -> ChatConnectionState.Connected
                        is ConnectionState.Reconnecting -> ChatConnectionState.Reconnecting
                        is ConnectionState.Failed -> ChatConnectionState.Failed
                    }
                    _uiState.value = _uiState.value.copy(connectionState = chatState)

                    // When connected, create or resume a session
                    if (state is ConnectionState.Connected && _uiState.value.activeSessionId == null) {
                        createSession()
                    }
                }
            }

            // Connect to gateway. Before each attempt, ensure the Termux
            // dashboard is up with a token that matches ours. After an app
            // reinstall the WS token is regenerated, so this re-syncs it from
            // Termux (and restarts the dashboard if needed) — otherwise the
            // handshake is rejected and the client would dead-end in Failed.
            var connected = false
            for (attempt in 1..CONNECT_ATTEMPTS) {
                try {
                    hermesRuntime.ensureGatewayReady()
                    val state = gatewayClient.connect(url = hermesRuntime.getWebSocketUrl())
                    if (state is ConnectionState.Connected) {
                        connected = true
                        break
                    }
                } catch (e: Exception) {
                    Timber.w(e, "[Chat] connect attempt $attempt/$CONNECT_ATTEMPTS failed")
                }
                if (attempt < CONNECT_ATTEMPTS) delay(CONNECT_RETRY_DELAY_MS)
            }
            if (!connected) {
                Timber.e("[Chat] Failed to connect to gateway after $CONNECT_ATTEMPTS attempts")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Cannot connect to Hermes gateway. Is it running?",
                    connectionState = ChatConnectionState.Failed,
                )
            } else {
                // Ensure the foreground service is running so the WebSocket
                // connection survives screen-off and app-backgrounding.
                com.hermes.android.service.HermesGatewayService.start(context)
            }

            // Collect events
            eventCollectionJob = launch {
                gatewayClient.events.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    fun retryConnection() {
        connectAndCollect()
    }

    // ── Session management ────────────────────────────────────────────────

    private suspend fun createSession() {
        try {
            val result = gatewayClient.request(GatewayMethods.SESSION_CREATE)
            val sessionId = (result as? kotlinx.serialization.json.JsonObject)
                ?.get("session_id")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
            if (sessionId != null) {
                _uiState.value = _uiState.value.copy(activeSessionId = sessionId)
                Timber.i("[Chat] Session created: $sessionId")
            }
        } catch (e: GatewayException) {
            Timber.e(e, "[Chat] Failed to create session")
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to create session: ${e.message}"
            )
        }
    }

    fun loadSessionList() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.SESSION_LIST)
                val sessions = parseSessionList(result)
                _uiState.value = _uiState.value.copy(sessions = sessions)
                Timber.d("[Chat] Session list loaded: ${sessions.size}")
            } catch (e: Exception) {
                Timber.w(e, "[Chat] Failed to load session list")
            }
        }
    }

    private fun parseSessionList(result: kotlinx.serialization.json.JsonElement): List<SessionItem> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["sessions"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val session = item as? JsonObject ?: return@mapNotNull null
                SessionItem(
                    id = session["id"]?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null,
                    title = session["title"]?.let { (it as? JsonPrimitive)?.content }?.ifBlank { null }
                        ?: "Untitled",
                    lastMessagePreview = session["preview"]?.let { (it as? JsonPrimitive)?.content },
                    updatedAt = session["started_at"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?.let(::normalizeEpochMillis) ?: 0L,
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Failed to parse sessions")
            emptyList()
        }
    }

    fun resumeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                gatewayClient.request(GatewayMethods.SESSION_RESUME, jsonToElementMap(params))
                activeAssistantMessageId = null
                resetStreamingBuffer()
                _uiState.value = _uiState.value.copy(
                    activeSessionId = sessionId,
                    messages = emptyList(),
                    showSessionDrawer = false,
                    errorMessage = null,
                )
                Timber.i("[Chat] Resumed session: $sessionId")
                loadSessionHistory(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to resume session")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to resume: ${e.message}")
            }
        }
    }

    private suspend fun loadSessionHistory(sessionId: String) {
        try {
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            val history = parseSessionHistory(result)
            if (history.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(messages = history)
            } else {
                appendStatus("Session resumed — start typing to continue")
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Could not load session history")
            appendStatus("Session resumed")
        }
    }

    private fun appendStatus(text: String) {
        val msg = ChatMessage.Status(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
            isError = false,
        )
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + msg)
    }

    private fun parseSessionHistory(result: kotlinx.serialization.json.JsonElement): List<ChatMessage> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = (obj["messages"] ?: obj["history"])
                as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val msg = item as? JsonObject ?: return@mapNotNull null
                val role = msg["role"]?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
                val text = msg["text"]?.let { (it as? JsonPrimitive)?.content }
                    ?: msg["content"]?.let { (it as? JsonPrimitive)?.content }
                    ?: return@mapNotNull null
                val ts = msg["timestamp"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                    ?.let(::normalizeEpochMillis) ?: System.currentTimeMillis()
                when (role) {
                    "user" -> ChatMessage.User(
                        id = UUID.randomUUID().toString(),
                        timestamp = ts,
                        text = text,
                    )
                    "assistant" -> ChatMessage.Assistant(
                        id = UUID.randomUUID().toString(),
                        timestamp = ts,
                        text = text,
                        isStreaming = false,
                        reasoning = msg["reasoning"]?.let { (it as? JsonPrimitive)?.content },
                    )
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Failed to parse session history")
            emptyList()
        }
    }

    // ── Sending messages ──────────────────────────────────────────────────

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val sessionId = _uiState.value.activeSessionId ?: return

        // Add user message to UI immediately
        val userMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isSending = true,
        )

        // Check for slash commands
        if (text.startsWith("/")) {
            handleSlashCommand(text, sessionId)
        } else {
            sendPrompt(text, sessionId)
        }
    }

    private fun sendPrompt(text: String, sessionId: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("text", text)
                    put("session_id", sessionId)
                }
                gatewayClient.request(
                    method = GatewayMethods.PROMPT_SUBMIT,
                    params = jsonToElementMap(params),
                )
                // Response is just {"ok": true} — actual content comes via events
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to send prompt")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to send: ${e.message}",
                    isSending = false,
                )
            }
        }
    }

    private fun handleSlashCommand(text: String, sessionId: String) {
        viewModelScope.launch {
            try {
                // Fix S4F04: command.dispatch expects {name, arg, session_id}
                // Parse "/model claude" → name="model", arg="claude"
                val withoutSlash = text.removePrefix("/").trim()
                val parts = withoutSlash.split(" ", limit = 2)
                val name = parts[0]
                val arg = if (parts.size > 1) parts[1] else ""
                val params = buildJsonObject {
                    put("name", name)
                    put("arg", arg)
                    put("session_id", sessionId)
                }
                val result = gatewayClient.request(
                    method = GatewayMethods.COMMAND_DISPATCH,
                    params = jsonToElementMap(params),
                )
                // Slash command result may contain output to display
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Slash command failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Command failed: ${e.message}",
                    isSending = false,
                )
            }
        }
    }

    fun stopGeneration() {
        val sessionId = _uiState.value.activeSessionId ?: return
        // Make the UI stop spinning immediately. The backend interrupt is
        // cooperative and can take a moment if a tool/model call is in-flight.
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg is ChatMessage.ToolCall && msg.isRunning) {
                    msg.copy(isRunning = false, resultText = msg.resultText ?: "Interrupted")
                } else msg
            },
            isSending = false,
        )
        activeAssistantMessageId = null
        resetStreamingBuffer()
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("session_id", sessionId)
                }
                gatewayClient.request(
                    method = GatewayMethods.SESSION_INTERRUPT,
                    params = jsonToElementMap(params),
                    timeoutMs = 5_000,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Chat] session.interrupt did not complete quickly")
            }
            try {
                // Best-effort cleanup for background/shell processes that keep
                // a tool card spinning after the turn was interrupted. This is
                // still routed through GatewayClient, so UI does not know about
                // backend process details.
                gatewayClient.request(
                    method = GatewayMethods.PROCESS_STOP,
                    timeoutMs = 5_000,
                )
            } catch (e: Exception) {
                Timber.d(e, "[Chat] process.stop cleanup skipped/failed")
            }
        }
    }

    // ── Event handling ────────────────────────────────────────────────────

    private fun handleEvent(event: GatewayEvent) {
        when (event) {
            is GatewayEvent.MessageStart -> {
                // Start a new assistant message (streaming). Use a unique
                // message id; sessionId is stable for the whole conversation
                // and would collide across multiple assistant turns.
                resetStreamingBuffer()
                val msgId = UUID.randomUUID().toString()
                activeAssistantMessageId = msgId
                val assistantMsg = ChatMessage.Assistant(
                    id = msgId,
                    timestamp = System.currentTimeMillis(),
                    text = "",
                    isStreaming = true,
                    reasoning = null,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMsg,
                )
            }

            is GatewayEvent.MessageDelta -> {
                enqueueStreamingDelta(event.text)
            }

            is GatewayEvent.MessageComplete -> {
                flushStreamingBuffer()
                // Finalize the assistant message
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.isStreaming &&
                            (activeAssistantMessageId == null || msg.id == activeAssistantMessageId)
                        ) {
                            msg.copy(
                                text = event.text.ifEmpty { msg.text },
                                isStreaming = false,
                                reasoning = event.reasoning,
                            )
                        } else if (msg is ChatMessage.ToolCall && msg.isRunning) {
                            msg.copy(isRunning = false, resultText = msg.resultText ?: "Completed")
                        } else msg
                    },
                    isSending = false,
                )
                activeAssistantMessageId = null
                resetStreamingBuffer()
            }

            is GatewayEvent.ThinkingDelta -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.isStreaming &&
                            (activeAssistantMessageId == null || msg.id == activeAssistantMessageId)
                        ) {
                            msg.copy(reasoning = (msg.reasoning ?: "") + event.text)
                        } else msg
                    }
                )
            }

            is GatewayEvent.ToolStart -> {
                val toolMsg = ChatMessage.ToolCall(
                    id = event.toolId,
                    timestamp = System.currentTimeMillis(),
                    toolName = event.name ?: "unknown",
                    argsText = event.argsText,
                    resultText = null,
                    error = null,
                    isRunning = true,
                    durationS = null,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + toolMsg,
                )
            }

            is GatewayEvent.ToolComplete -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.ToolCall && msg.id == event.toolId) {
                            msg.copy(
                                resultText = event.resultText ?: event.result,
                                error = null,
                                isRunning = false,
                                durationS = event.durationS,
                            )
                        } else msg
                    }
                )
            }

            is GatewayEvent.Error -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.ToolCall && msg.isRunning) {
                            msg.copy(isRunning = false, error = event.message)
                        } else msg
                    },
                    errorMessage = event.message,
                    isSending = false,
                )
                activeAssistantMessageId = null
            }

            is GatewayEvent.StatusUpdate -> {
                // Show as a status message
                val statusMsg = ChatMessage.Status(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    text = event.text ?: "",
                    isError = event.kind == "error",
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + statusMsg,
                )
            }

            is GatewayEvent.ApprovalRequest -> {
                // Step 7: show approval notification + in-chat card
                val requestId = UUID.randomUUID().toString()
                approvalNotificationManager.showApprovalRequest(
                    requestId = requestId,
                    sessionId = event.sessionId,
                    toolName = "terminal",
                    command = event.command,
                    description = event.description,
                )
                val statusMsg = ChatMessage.Status(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    text = "🔐 Approval needed: ${event.description}\nCommand: ${event.command}",
                    isError = false,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + statusMsg,
                )
            }

            else -> {
                // Other events (billing, voice, subagent, etc.) — ignore for now
                Timber.d("[Chat] Unhandled event: ${event::class.simpleName}")
            }
        }
    }

    // ── Streaming buffer ─────────────────────────────────────────────────

    private fun enqueueStreamingDelta(text: String) {
        if (text.isEmpty()) return
        streamingBuffer.append(text)
        if (streamingFlushJob?.isActive == true) return
        streamingFlushJob = viewModelScope.launch {
            delay(STREAM_FLUSH_INTERVAL_MS)
            flushStreamingBuffer()
            streamingFlushJob = null
        }
    }

    private fun flushStreamingBuffer() {
        if (streamingBuffer.isEmpty()) return
        val chunk = streamingBuffer.toString()
        streamingBuffer.setLength(0)
        val targetId = activeAssistantMessageId
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg is ChatMessage.Assistant && msg.isStreaming &&
                    (targetId == null || msg.id == targetId)
                ) {
                    msg.copy(text = msg.text + chunk)
                } else msg
            }
        )
    }

    private fun resetStreamingBuffer() {
        streamingFlushJob?.cancel()
        streamingFlushJob = null
        streamingBuffer.setLength(0)
    }

    // ── UI actions ────────────────────────────────────────────────────────

    fun toggleSessionDrawer() {
        val opening = !_uiState.value.showSessionDrawer
        _uiState.value = _uiState.value.copy(showSessionDrawer = opening)
        if (opening) loadSessionList()
    }

    fun closeSessionDrawer() {
        _uiState.value = _uiState.value.copy(showSessionDrawer = false)
    }

    fun newConversation() {
        viewModelScope.launch {
            activeAssistantMessageId = null
            resetStreamingBuffer()
            _uiState.value = _uiState.value.copy(
                messages = emptyList(),
                showSessionDrawer = false,
                activeSessionId = null,
            )
            createSession()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun jsonToElementMap(obj: kotlinx.serialization.json.JsonObject):
        Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()

    private fun normalizeEpochMillis(value: Long): Long =
        if (value in 1..999_999_999_999L) value * 1000L else value

    private companion object {
        private const val STREAM_FLUSH_INTERVAL_MS = 80L
        // Initial-connect retry. ensureGatewayReady() may need to (re)start the
        // Termux dashboard (cold start is 20-40s on a phone); a few spaced
        // attempts ride that out instead of dead-ending in Failed.
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_DELAY_MS = 2_000L
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        resetStreamingBuffer()
        viewModelScope.launch { gatewayClient.disconnect() }
    }
}
