package com.hermes.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.GatewayException
import com.hermes.android.service.ApprovalNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * - Draft persistence (SharedPreferences)
 * - Search in messages
 * - Quick model switch
 * - Retry last message
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _notification = MutableStateFlow<NotificationUi?>(null)
    val notification: StateFlow<NotificationUi?> = _notification.asStateFlow()

    private var eventCollectionJob: Job? = null
    private var connectionWatchJob: Job? = null
    private var activeAssistantMessageId: String? = null
    private val streamingBuffer = StringBuilder()
    private var streamingFlushJob: Job? = null

    // Feature #23: SharedPreferences for draft persistence
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadDraft()
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

            // Connect to gateway
            try {
                gatewayClient.connect(url = hermesRuntime.getWebSocketUrl())
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to connect to gateway")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Cannot connect to Hermes gateway. Is it running?",
                    connectionState = ChatConnectionState.Failed,
                )
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
                    messageCount = session["message_count"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() },
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
            // session.history uses "id" param (not "session_id") — matches session.list response field
            val params = buildJsonObject { put("id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            val messages = parseSessionHistory(result)
            if (messages.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(messages = messages)
                Timber.i("[Chat] Loaded ${messages.size} history messages for session $sessionId")
            } else {
                Timber.w("[Chat] Session history returned empty for $sessionId")
            }
        } catch (e: Exception) {
            // History load failure is non-fatal — messages arrive via event stream after resume
            Timber.w(e, "[Chat] Could not load session history for $sessionId, continuing without it")
        }
    }

    private fun parseSessionHistory(result: kotlinx.serialization.json.JsonElement): List<ChatMessage> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["messages"] as? kotlinx.serialization.json.JsonArray
                ?: obj["history"] as? kotlinx.serialization.json.JsonArray
                ?: return emptyList()
            arr.mapNotNull { item ->
                val msg = item as? JsonObject ?: return@mapNotNull null
                val role = msg["role"]?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
                val content = msg["content"]?.let { (it as? JsonPrimitive)?.content }
                    ?: msg["text"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                val ts = msg["timestamp"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                    ?.let(::normalizeEpochMillis) ?: System.currentTimeMillis()
                val id = msg["id"]?.let { (it as? JsonPrimitive)?.content } ?: UUID.randomUUID().toString()
                when (role) {
                    "user" -> ChatMessage.User(id = id, timestamp = ts, text = content)
                    "assistant" -> ChatMessage.Assistant(
                        id = id, timestamp = ts, text = content,
                        isStreaming = false,
                        reasoning = msg["reasoning"]?.let { (it as? JsonPrimitive)?.content },
                    )
                    "tool" -> ChatMessage.ToolCall(
                        id = id, timestamp = ts,
                        toolName = msg["name"]?.let { (it as? JsonPrimitive)?.content } ?: "tool",
                        argsText = msg["args"]?.let { (it as? JsonPrimitive)?.content },
                        resultText = msg["result"]?.let { (it as? JsonPrimitive)?.content } ?: content,
                        error = msg["error"]?.let { (it as? JsonPrimitive)?.content },
                        isRunning = false, durationS = null,
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

        // Feature #23: Clear draft when message is sent
        clearDraft()

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

    // ── Feature #5: Retry / Regenerate ───────────────────────────────────

    fun retryLastMessage() {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isSending) return

        // Find the last user message
        val lastUserMsg = _uiState.value.messages.filterIsInstance<ChatMessage.User>().lastOrNull() ?: return
        val lastUserText = lastUserMsg.text

        // Remove the last assistant response (and any tool calls / status after it)
        val lastUserIndex = _uiState.value.messages.indexOfLast { it is ChatMessage.User }
        if (lastUserIndex >= 0) {
            val trimmedMessages = _uiState.value.messages.subList(0, lastUserIndex + 1)
            _uiState.value = _uiState.value.copy(
                messages = trimmedMessages,
                isSending = true,
            )
        }

        // Resend the prompt
        sendPrompt(lastUserText, sessionId)
    }

    // ── Feature #16: Search in current chat ──────────────────────────────

    fun toggleSearch() {
        val current = _uiState.value.showSearch
        _uiState.value = _uiState.value.copy(
            showSearch = !current,
            searchQuery = if (current) "" else _uiState.value.searchQuery,
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // ── Feature #23: Save / Load draft ───────────────────────────────────

    fun saveDraft() {
        val text = _uiState.value.inputText
        prefs.edit().putString(KEY_DRAFT, text).apply()
    }

    fun loadDraft() {
        val draft = prefs.getString(KEY_DRAFT, "") ?: ""
        if (draft.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(inputText = draft)
        }
    }

    private fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    // ── Feature #8: Quick model switch from chat ─────────────────────────

    fun toggleModelSwitcher() {
        _uiState.value = _uiState.value.copy(
            showModelSwitcher = !_uiState.value.showModelSwitcher,
        )
    }

    fun switchModelFromChat(provider: String, model: String) {
        viewModelScope.launch {
            try {
                // Set provider
                val providerParams = buildJsonObject {
                    put("key", "llm.provider")
                    put("value", provider)
                }
                gatewayClient.request(GatewayMethods.CONFIG_SET, providerParams.toMap())

                // Set model
                val modelParams = buildJsonObject {
                    put("key", "llm.model")
                    put("value", model)
                }
                gatewayClient.request(GatewayMethods.CONFIG_SET, modelParams.toMap())

                _uiState.value = _uiState.value.copy(
                    currentModelName = "$provider/$model",
                    showModelSwitcher = false,
                )
                Timber.i("[Chat] Model switched to $provider/$model")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to switch model")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to switch model: ${e.message}",
                    showModelSwitcher = false,
                )
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
                val targetId = activeAssistantMessageId ?: return
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.isStreaming && msg.id == targetId) {
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
                val isRateLimit = event.message?.contains("rate_limit", ignoreCase = true) == true ||
                        event.message?.contains("429") == true
                val displayMsg = if (isRateLimit) "Rate limited — please wait" else event.message
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.ToolCall && msg.isRunning) {
                            msg.copy(isRunning = false, error = displayMsg)
                        } else msg
                    },
                    errorMessage = displayMsg,
                    isSending = false,
                )
                if (isRateLimit) {
                    val statusMsg = ChatMessage.Status(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        text = "⏸ Rate limited — please wait a moment",
                        isError = false,
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + statusMsg,
                    )
                }
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

            is GatewayEvent.ClarifyRequest -> {
                val msg = ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = event.question,
                    choices = event.choices,
                    kind = InteractiveKind.CLARIFY,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
            }

            is GatewayEvent.SudoRequest -> {
                val msg = ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = "Sudo password required",
                    choices = null,
                    kind = InteractiveKind.SUDO,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
            }

            is GatewayEvent.SecretRequest -> {
                val msg = ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = event.prompt,
                    choices = null,
                    kind = InteractiveKind.SECRET,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
            }

            is GatewayEvent.SubagentEvent -> {
                when (event.subagentType) {
                    "spawn_requested", "start" -> {
                        val subagentId = event.payload["id"]?.jsonPrimitive?.content
                            ?: "subagent-${UUID.randomUUID()}"
                        val msg = ChatMessage.SubagentCard(
                            id = subagentId,
                            timestamp = System.currentTimeMillis(),
                            subagentType = event.subagentType,
                            text = event.payload["description"]?.jsonPrimitive?.content ?: "Sub-agent",
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + msg,
                        )
                    }
                    "complete" -> {
                        val subagentId = event.payload["id"]?.jsonPrimitive?.content
                        val text = event.payload["text"]?.jsonPrimitive?.content ?: ""
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages.map { msg ->
                                if (msg is ChatMessage.SubagentCard && !msg.isComplete &&
                                    (subagentId == null || msg.id == subagentId)) {
                                    msg.copy(isComplete = true, text = text.ifEmpty { msg.text })
                                } else msg
                            }
                        )
                    }
                    "thinking", "progress" -> {
                        val subagentId = event.payload["id"]?.jsonPrimitive?.content
                        val text = event.payload["text"]?.jsonPrimitive?.content ?: return
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages.map { msg ->
                                if (msg is ChatMessage.SubagentCard && !msg.isComplete &&
                                    (subagentId == null || msg.id == subagentId)) {
                                    msg.copy(text = text)
                                } else msg
                            }
                        )
                    }
                }
            }

            is GatewayEvent.ToolProgress -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.ToolCall && msg.isRunning) {
                            msg.copy(resultText = event.preview)
                        } else msg
                    }
                )
            }

            is GatewayEvent.ToolGenerating -> {
                Timber.d("[Chat] Tool generating: ${event.name}")
            }

            is GatewayEvent.ReasoningDelta -> {
                val targetId = activeAssistantMessageId ?: return
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.isStreaming && msg.id == targetId) {
                            msg.copy(reasoning = (msg.reasoning ?: "") + event.text)
                        } else msg
                    }
                )
            }

            is GatewayEvent.ReasoningAvailable -> {
                Timber.d("[Chat] Reasoning available")
            }

            is GatewayEvent.NotificationShow -> {
                val notifUi = NotificationUi(
                    key = event.key,
                    kind = event.kind,
                    level = event.level,
                    text = event.text,
                    ttlMs = event.ttlMs,
                )
                _notification.value = notifUi
                val ttl = event.ttlMs
                val key = event.key
                if (event.kind != "sticky" && ttl != null) {
                    viewModelScope.launch {
                        delay(ttl)
                        if (_notification.value?.key == key) _notification.value = null
                    }
                }
            }

            is GatewayEvent.NotificationClear -> {
                if (_notification.value?.key == event.key) _notification.value = null
            }

            is GatewayEvent.BackgroundComplete -> {
                val msg = ChatMessage.Status(
                    id = "bg-${event.taskId}",
                    timestamp = System.currentTimeMillis(),
                    text = "✅ Background task complete: ${event.text.take(200)}",
                    isError = false,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
            }

            is GatewayEvent.SessionInfo -> {
                Timber.d("[Chat] Session info: ${event.info}")
            }

            is GatewayEvent.GatewayStderr -> {
                Timber.w("[Chat] Gateway stderr: ${event.line}")
            }

            else -> {
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

    // ── Interactive responds ──────────────────────────────────────────────

    fun respondToClarify(requestId: String, answer: String) {
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    method = GatewayMethods.CLARIFY_RESPOND,
                    params = buildJsonObject {
                        put("request_id", requestId)
                        put("answer", answer)
                    },
                )
                markAnswered(requestId)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to respond to clarify")
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun respondToSudo(requestId: String, password: String) {
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    method = GatewayMethods.SUDO_RESPOND,
                    params = buildJsonObject {
                        put("request_id", requestId)
                        put("password", password)
                    },
                )
                markAnswered(requestId)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to respond to sudo")
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun respondToSecret(requestId: String, value: String) {
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    method = GatewayMethods.SECRET_RESPOND,
                    params = buildJsonObject {
                        put("request_id", requestId)
                        put("value", value)
                    },
                )
                markAnswered(requestId)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to respond to secret")
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    private fun markAnswered(requestId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg is ChatMessage.InteractiveRequest && msg.requestId == requestId) {
                    msg.copy(answered = true)
                } else msg
            }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun jsonToElementMap(obj: kotlinx.serialization.json.JsonObject):
        Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()

    private companion object {
        private const val STREAM_FLUSH_INTERVAL_MS = 80L
        private const val PREFS_NAME = "hermes_chat_prefs"
        private const val KEY_DRAFT = "draft_message"
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        resetStreamingBuffer()
        // Feature #23: Save draft when ViewModel is cleared
        saveDraft()
        viewModelScope.launch { gatewayClient.disconnect() }
    }
}
