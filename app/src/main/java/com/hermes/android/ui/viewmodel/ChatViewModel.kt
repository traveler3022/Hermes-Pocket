package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Chat screen — coordinator that delegates to focused
 * sub-handlers for session management, attachments, streaming, and drawer UI.
 *
 * Depends ONLY on [GatewayClient] interface — never on OkHttp or any
 * concrete implementation. This is the abstraction boundary.
 */

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val sessionRepository: com.hermes.android.data.SessionRepository,
    private val hermesRuntime: com.hermes.android.runtime.HermesRuntime,
    private val approvalNotificationManager: ApprovalNotificationManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── Delegates ───────────────────────────────────────────────────────

    private val sessionDelegate = ChatSessionDelegate(
        gatewayClient, sessionRepository, viewModelScope
    ) { loadReasoningLevel() }

    private val streamingDelegate = ChatStreamingDelegate(viewModelScope, _uiState)

    private val attachmentDelegate = ChatAttachmentDelegate(
        gatewayClient, hermesRuntime, context, viewModelScope,
    )

    private val drawerDelegate = ChatDrawerDelegate(
        gatewayClient, viewModelScope,
        loadSessionList = { sessionDelegate.loadList(it) },
        createNewSession = { sessionDelegate.create(it) },
    )

    // ── State ───────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _notification = MutableStateFlow<NotificationUi?>(null)
    val notification: StateFlow<NotificationUi?> = _notification.asStateFlow()

    private val _slashCommands = MutableStateFlow<List<SlashCommandSuggestion>>(emptyList())
    val slashCommands: StateFlow<List<SlashCommandSuggestion>> = _slashCommands.asStateFlow()

    private var eventCollectionJob: Job? = null
    private var connectionWatchJob: Job? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadDraft()
        loadAssistantName()
        loadAssistantAvatar()
        connectAndCollect()
        loadCommandCatalog()
    }

    // ── Reasoning ────────────────────────────────────────────────────────

    private fun loadReasoningLevel() {
        viewModelScope.launch {
            try {
                val level = sessionRepository.reasoningLevel(_uiState.value.activeSessionId)
                _uiState.update { it.copy(reasoningLevel = level) }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] Failed to load reasoning level")
            }
        }
    }

    fun setReasoningLevel(rawLevel: String) {
        viewModelScope.launch {
            try {
                val level = sessionRepository.setReasoningLevel(
                    rawLevel,
                    _uiState.value.activeSessionId,
                )
                _uiState.update { it.copy(reasoningLevel = level) }
                Timber.i("[Chat] reasoning set to $level (session=${_uiState.value.activeSessionId})")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to set reasoning level")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("Failed to set reasoning: ${e.message}")) }
            }
        }
    }

    // ── Command catalog ──────────────────────────────────────────────────

    private fun loadCommandCatalog() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.COMMANDS_CATALOG)
                val pairs = (result as? JsonObject)?.get("pairs") as? JsonArray
                val cmds = pairs?.mapNotNull { row ->
                    val arr = row as? JsonArray ?: return@mapNotNull null
                    val name = (arr.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val desc = (arr.getOrNull(1) as? JsonPrimitive)?.content ?: ""
                    SlashCommandSuggestion(command = name, description = desc)
                } ?: emptyList()
                if (cmds.isNotEmpty()) {
                    _slashCommands.value = cmds
                    Timber.i("[Chat] Loaded ${cmds.size} slash commands from catalog")
                }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] commands.catalog failed — slash command autocomplete will be empty until next retry")
            }
        }
    }

    // ── Connection ───────────────────────────────────────────────────────

    private fun connectAndCollect() {
        connectionWatchJob?.cancel()
        eventCollectionJob?.cancel()
        viewModelScope.launch {
            connectionWatchJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.connectionState.collect { state ->
                    val chatState = when (state) {
                        is ConnectionState.Disconnected -> ChatConnectionState.Disconnected
                        is ConnectionState.Connecting -> ChatConnectionState.Connecting
                        is ConnectionState.Connected -> ChatConnectionState.Connected
                        is ConnectionState.Reconnecting -> ChatConnectionState.Reconnecting
                        is ConnectionState.Failed -> ChatConnectionState.Failed
                    }
                    _uiState.update { it.copy(connectionState = chatState) }

                    if (state is ConnectionState.Disconnected ||
                        state is ConnectionState.Failed
                    ) {
                        streamingDelegate.finalizeOrphanedMessage(
                            if (state is ConnectionState.Failed) "(connection failed)" else "(connection lost)",
                        )
                    }

                    if (state is ConnectionState.Connected) {
                        val liveId = state.sessionId
                        if (liveId != null && liveId != _uiState.value.activeSessionId) {
                            _uiState.update { it.copy(activeSessionId = liveId) }
                            launch { sessionDelegate.loadHistory(_uiState, liveId) }
                        } else if (liveId == null && _uiState.value.activeSessionId == null) {
                            launch { sessionDelegate.createOrResume(_uiState) }
                        }
                        loadReasoningLevel()
                    }
                }
            }

            eventCollectionJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.events.collect { event ->
                    handleEvent(event)
                }
            }

            try {
                gatewayClient.connect(url = hermesRuntime.getWebSocketUrl())
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to connect to gateway")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Critical("Cannot connect to Hermes gateway. Is it running?"),
                    connectionState = ChatConnectionState.Failed,
                ) }
            }
        }
    }

    fun retryConnection() {
        connectAndCollect()
    }

    // ── Session management (coordinated via delegate) ────────────────────

    fun loadSessionList() {
        viewModelScope.launch { sessionDelegate.loadList(_uiState) }
    }

    fun resumeSession(sessionId: String) {
        viewModelScope.launch {
            streamingDelegate.reset()
            sessionDelegate.resume(_uiState, sessionId)
        }
    }

    fun branchSession() {
        viewModelScope.launch {
            sessionDelegate.branch(_uiState) { sessionDelegate.resolveLiveSessionId(_uiState) }
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            streamingDelegate.reset()
            _uiState.update { it.copy(
                messages = emptyList(),
                showSessionDrawer = false,
                activeSessionId = null,
                activeTodos = emptyList(),
                pendingApproval = null,
            ) }
            sessionDelegate.create(_uiState)
        }
    }

    // ── Sending messages ─────────────────────────────────────────────────

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachments = _uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return
        val sessionId = _uiState.value.activeSessionId ?: return

        clearDraft()

        val refs = attachments.mapNotNull { it.refText }
        val outgoing = when {
            refs.isEmpty() -> text.ifEmpty { attachments.joinToString("\n") { "[User attached image: ${it.name}]" } }
            else -> (text + "\n" + refs.joinToString("\n")).trim()
        }

        val userMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
            attachments = attachments,
        )
        _uiState.update { it.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isSending = true,
            pendingAttachments = emptyList(),
            activeTodos = emptyList(),
        ) }

        if (text.startsWith("/")) {
            handleSlashCommand(text, sessionId)
        } else {
            sendPrompt(outgoing, sessionId)
        }
    }

    fun retryLastMessage() {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isSending) return

        val lastUserMsg = _uiState.value.messages.filterIsInstance<ChatMessage.User>().lastOrNull() ?: return
        val refs = lastUserMsg.attachments.mapNotNull { it.refText }
        val lastUserText = if (refs.isEmpty()) {
            lastUserMsg.text
        } else {
            (lastUserMsg.text + "\n" + refs.joinToString("\n")).trim()
        }

        val userMessages = _uiState.value.messages.filterIsInstance<ChatMessage.User>()
        val lastUserOrdinal = userMessages.size - 1

        val lastUserIndex = _uiState.value.messages.indexOfLast { it is ChatMessage.User }
        if (lastUserIndex >= 0) {
            val trimmedMessages = _uiState.value.messages.subList(0, lastUserIndex + 1).toList()
            _uiState.update { it.copy(messages = trimmedMessages, isSending = true) }
        }

        sendPrompt(lastUserText, sessionId, truncateBeforeUserOrdinal = lastUserOrdinal)
    }

    fun steerAgent() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val steerMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = "\u21B3 $text",
        )
        _uiState.update { it.copy(messages = _uiState.value.messages + steerMsg, inputText = "") }
        clearDraft()
        viewModelScope.launch {
            val sessionId = sessionDelegate.resolveLiveSessionId(_uiState)
            if (sessionId == null) {
                _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("No active turn to steer")) }
                return@launch
            }
            try {
                val params = buildJsonObject {
                    put("session_id", sessionId)
                    put("text", text)
                }
                val result = gatewayClient.request(
                    method = GatewayMethods.SESSION_STEER,
                    params = jsonToElementMap(params),
                )
                val obj = result as? JsonObject
                val status = (obj?.get("status") as? JsonPrimitive)?.content
                if (status == "rejected") {
                    val note = (obj?.get("text") as? JsonPrimitive)?.content
                    _uiState.update { it.copy(
                        messages = _uiState.value.messages + ChatMessage.Status(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            text = note ?: "Steer rejected — the agent isn't at a steerable point right now.",
                            isError = true,
                        ),
                    ) }
                }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] session.steer failed")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Steer failed: ${e.message}")) }
            }
        }
    }

    fun stopGeneration() {
        val sessionId = _uiState.value.activeSessionId ?: return
        streamingDelegate.finalizeOrphanedMessage("(stopped)")
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateAll({ msg ->
                msg is ChatMessage.ToolCall && msg.isRunning
            }) { msg ->
                (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Interrupted")
            },
            isSending = false,
        ) }
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                gatewayClient.request(
                    method = GatewayMethods.SESSION_INTERRUPT,
                    params = jsonToElementMap(params),
                    timeoutMs = 5_000,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Chat] session.interrupt did not complete quickly")
            }
            try {
                gatewayClient.request(
                    method = GatewayMethods.PROCESS_STOP,
                    timeoutMs = 5_000,
                )
            } catch (e: Exception) {
                Timber.d(e, "[Chat] process.stop cleanup skipped/failed")
            }
        }
    }

    private fun sendPrompt(text: String, sessionId: String, truncateBeforeUserOrdinal: Int? = null) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("text", text)
                    put("session_id", sessionId)
                    if (truncateBeforeUserOrdinal != null) {
                        put("truncate_before_user_ordinal", truncateBeforeUserOrdinal)
                    }
                }
                gatewayClient.request(
                    method = GatewayMethods.PROMPT_SUBMIT,
                    params = jsonToElementMap(params),
                )
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to send prompt")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Failed to send: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    private fun handleSlashCommand(text: String, sessionId: String, depth: Int = 0) {
        if (depth > 5) {
            _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Command alias loop"), isSending = false) }
            return
        }
        viewModelScope.launch {
            try {
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
                val obj = result as? JsonObject
                when ((obj?.get("type") as? JsonPrimitive)?.content) {
                    "alias" -> {
                        val target = (obj["target"] as? JsonPrimitive)?.content
                        if (!target.isNullOrBlank()) {
                            val nextText = if (arg.isNotBlank()) "/$target $arg" else "/$target"
                            handleSlashCommand(nextText, sessionId, depth + 1)
                            return@launch
                        }
                    }
                    "send" -> {
                        val message = (obj["message"] as? JsonPrimitive)?.content
                        if (!message.isNullOrBlank()) {
                            sendPrompt(message, sessionId)
                            return@launch
                        }
                    }
                    "prefill" -> {
                        val message = (obj["message"] as? JsonPrimitive)?.content
                        _uiState.update { it.copy(inputText = message ?: _uiState.value.inputText, isSending = false) }
                        return@launch
                    }
                }
                val output = extractCommandOutput(result)
                val newMessages = if (!output.isNullOrBlank()) {
                    _uiState.value.messages + ChatMessage.Status(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        text = output.trim(),
                        isError = false,
                    )
                } else {
                    _uiState.value.messages
                }
                _uiState.update { it.copy(messages = newMessages, isSending = false) }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Slash command failed")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Command failed: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    private fun extractCommandOutput(result: kotlinx.serialization.json.JsonElement?): String? {
        if (result == null) return null
        (result as? JsonPrimitive)?.let { if (it.isString) return it.content }
        val obj = result as? JsonObject ?: return null
        for (key in listOf("output", "text", "message", "markdown", "result", "detail")) {
            val v = obj[key]
            if (v is JsonPrimitive && v.isString && v.content.isNotBlank()) return v.content
        }
        (obj["lines"] as? JsonArray)?.let { arr ->
            val joined = arr.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("\n")
            if (joined.isNotBlank()) return joined
        }
        return null
    }

    // ── Attachments (delegated) ──────────────────────────────────────────

    fun attachFromUri(uri: Uri) = attachmentDelegate.attachFromUri(_uiState, uri)

    fun downloadFile(url: String, filename: String) = attachmentDelegate.downloadFile(_uiState, url, filename)

    fun removeAttachment(attachment: PendingAttachment) = attachmentDelegate.removeAttachment(_uiState, attachment)

    fun resolveMediaUrl(raw: String): String = attachmentDelegate.resolveMediaUrl(raw)

    // ── Draft persistence ────────────────────────────────────────────────

    fun saveDraft() {
        val text = _uiState.value.inputText
        prefs.edit().putString(KEY_DRAFT, text).apply()
    }

    fun loadDraft() {
        val draft = prefs.getString(KEY_DRAFT, "") ?: ""
        if (draft.isNotEmpty()) {
            _uiState.update { it.copy(inputText = draft) }
        }
    }

    private fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    // ── Display name / avatar ────────────────────────────────────────────

    private fun loadAssistantName() {
        val saved = prefs.getString(KEY_ASSISTANT_NAME, null)
        if (!saved.isNullOrBlank()) {
            _uiState.update { it.copy(assistantName = saved) }
        }
    }

    fun setAssistantName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_ASSISTANT_NAME, trimmed).apply()
        _uiState.update { it.copy(assistantName = trimmed) }
    }

    fun loadAssistantAvatar() {
        val saved = prefs.getString(KEY_ASSISTANT_AVATAR, null)
        val path = if (!saved.isNullOrBlank() && java.io.File(saved).exists()) saved else null
        _uiState.update { it.copy(assistantAvatarPath = path) }
    }

    // ── Search ───────────────────────────────────────────────────────────

    fun toggleSearch() {
        val current = _uiState.value.showSearch
        _uiState.update { it.copy(
            showSearch = !current,
            searchQuery = if (current) "" else _uiState.value.searchQuery,
        ) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ── Drawer (delegated) ───────────────────────────────────────────────

    fun toggleSessionDrawer() {
        val opening = !_uiState.value.showSessionDrawer
        _uiState.update { it.copy(showSessionDrawer = opening) }
        if (opening) loadSessionList()
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = false) }
    }

    fun clearErrorEvent() {
        _uiState.update { it.copy(errorEvent = null) }
    }

    fun updateDrawerSearch(query: String) = drawerDelegate.updateSearch(_uiState, query)
    fun toggleDrawerSort() = drawerDelegate.toggleSort(_uiState)
    fun drawerTogglePin(sessionId: String) = drawerDelegate.togglePin(_uiState, sessionId)
    fun drawerShowRename(sessionId: String, currentTitle: String) = drawerDelegate.showRename(_uiState, sessionId, currentTitle)
    fun drawerUpdateRenameText(text: String) = drawerDelegate.updateRenameText(_uiState, text)
    fun drawerHideRename() = drawerDelegate.hideRename(_uiState)
    fun drawerConfirmRename() = drawerDelegate.confirmRename(_uiState)
    fun drawerShowDelete(sessionId: String) = drawerDelegate.showDelete(_uiState, sessionId)
    fun drawerHideDelete() = drawerDelegate.hideDelete(_uiState)
    fun drawerConfirmDelete() = drawerDelegate.confirmDelete(_uiState)

    // ── Interactive responds ─────────────────────────────────────────────

    fun respondToApproval(choice: String) {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        approvalNotificationManager.cancelApproval(pending.requestId)
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    method = GatewayMethods.APPROVAL_RESPOND,
                    params = buildJsonObject {
                        pending.sessionId?.let { sid -> put("session_id", sid) }
                        put("choice", choice)
                        put("all", false)
                    },
                )
                Timber.i("[Chat] Approval response sent: $choice")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to respond to approval")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error(e.message ?: "Unknown error")) }
            }
        }
    }

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
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error(e.message ?: "Unknown error")) }
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
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error(e.message ?: "Unknown error")) }
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
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    private fun markAnswered(requestId: String) {
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateFirst({ msg ->
                msg is ChatMessage.InteractiveRequest && msg.requestId == requestId
            }) { msg ->
                (msg as ChatMessage.InteractiveRequest).copy(answered = true)
            }
        ) }
    }

    // ── Event handling ───────────────────────────────────────────────────

    private fun handleEvent(event: GatewayEvent) {
        val eventSid = event.sessionId
        val activeSid = _uiState.value.activeSessionId
        if (eventSid != null && activeSid != null && eventSid != activeSid &&
            event !is GatewayEvent.ApprovalRequest &&
            event !is GatewayEvent.ClarifyRequest &&
            event !is GatewayEvent.SudoRequest &&
            event !is GatewayEvent.SecretRequest &&
            event !is GatewayEvent.BackgroundComplete
        ) {
            return
        }

        when (event) {
            is GatewayEvent.MessageStart -> {
                streamingDelegate.finalizeOrphanedMessage("(interrupted)")
                streamingDelegate.reset()
                val msgId = streamingDelegate.onMessageStart()
                val assistantMsg = ChatMessage.Assistant(
                    id = msgId,
                    timestamp = System.currentTimeMillis(),
                    text = "",
                    isStreaming = true,
                    reasoning = null,
                )
                _uiState.update { it.copy(messages = _uiState.value.messages + assistantMsg) }
            }

            is GatewayEvent.MessageDelta -> {
                streamingDelegate.enqueueDelta(event.text)
            }

            is GatewayEvent.MessageComplete -> {
                streamingDelegate.flushBuffer()
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.Assistant && msg.isStreaming &&
                            (streamingDelegate.currentAssistantMessageId == null || msg.id == streamingDelegate.currentAssistantMessageId)
                    }) { msg ->
                        (msg as ChatMessage.Assistant).copy(
                            text = event.text.ifEmpty { msg.text },
                            isStreaming = false,
                            reasoning = event.reasoning?.takeIf { it.isNotBlank() } ?: msg.reasoning,
                        )
                    }.let { msgs ->
                        msgs.updateAll({ msg ->
                            msg is ChatMessage.ToolCall && msg.isRunning
                        }) { msg ->
                            (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Completed")
                        }
                    },
                    isSending = false,
                    activeTodos = emptyList(),
                ) }
                streamingDelegate.reset()
            }

            is GatewayEvent.ThinkingDelta -> {
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
            }

            is GatewayEvent.ReasoningDelta -> {
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
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
                _uiState.update { state ->
                    val exists = state.messages.any { it.id == event.toolId }
                    state.copy(
                        messages = if (exists) {
                            state.messages.updateFirst({ it.id == event.toolId }) { toolMsg }
                        } else {
                            state.messages + toolMsg
                        },
                        activeTodos = event.todos?.toUiTodos() ?: state.activeTodos,
                    )
                }
            }

            is GatewayEvent.ToolComplete -> {
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.ToolCall && msg.id == event.toolId
                    }) { msg ->
                        (msg as ChatMessage.ToolCall).copy(
                            resultText = event.resultText ?: event.result,
                            error = event.error,
                            isRunning = false,
                            durationS = event.durationS,
                        )
                    },
                    activeTodos = event.todos?.toUiTodos() ?: _uiState.value.activeTodos,
                ) }
            }

            is GatewayEvent.ToolProgress -> {
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.ToolCall && msg.isRunning
                    }) { msg ->
                        (msg as ChatMessage.ToolCall).copy(resultText = event.preview)
                    }
                ) }
            }

            is GatewayEvent.ToolGenerating -> {
                Timber.d("[Chat] Tool generating: ${event.name}")
            }

            is GatewayEvent.Error -> {
                val isRateLimit = event.message?.contains("rate_limit", ignoreCase = true) == true ||
                        event.message?.contains("429") == true
                val displayMsg = if (isRateLimit) "Rate limited — please wait" else event.message
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateAll({ msg ->
                        msg is ChatMessage.ToolCall && msg.isRunning
                    }) { msg ->
                        (msg as ChatMessage.ToolCall).copy(isRunning = false, error = displayMsg)
                    },
                    errorEvent = ErrorEvent.Warning(displayMsg ?: "Unknown error"),
                    isSending = false,
                ) }
            }

            is GatewayEvent.StatusUpdate -> {
                val statusMsg = ChatMessage.Status(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    text = event.text ?: "",
                    isError = event.kind == "error",
                )
                _uiState.update { it.copy(messages = _uiState.value.messages + statusMsg) }
            }

            is GatewayEvent.ApprovalRequest -> {
                val requestId = UUID.randomUUID().toString()
                approvalNotificationManager.showApprovalRequest(
                    requestId = requestId,
                    sessionId = event.sessionId,
                    toolName = "terminal",
                    command = event.command,
                    description = event.description,
                    allowPermanent = event.allowPermanent,
                )
                val statusMsg = ChatMessage.Status(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    text = "Approval needed: ${event.description}\nCommand: ${event.command}",
                    isError = false,
                )
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + statusMsg,
                    pendingApproval = PendingApprovalUi(
                        requestId = requestId,
                        sessionId = event.sessionId,
                        command = event.command,
                        description = event.description,
                        patternKeys = event.patternKeys,
                        allowPermanent = event.allowPermanent,
                    ),
                ) }
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
                _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
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
                _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
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
                _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
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
                        _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
                    }
                    "complete" -> {
                        val subagentId = event.payload["id"]?.jsonPrimitive?.content
                        val text = event.payload["text"]?.jsonPrimitive?.content ?: ""
                        _uiState.update { it.copy(
                            messages = _uiState.value.messages.updateFirst({ msg ->
                                msg is ChatMessage.SubagentCard && !msg.isComplete &&
                                    (subagentId == null || msg.id == subagentId)
                            }) { msg ->
                                (msg as ChatMessage.SubagentCard).copy(isComplete = true, text = text.ifEmpty { msg.text })
                            }
                        ) }
                    }
                    "thinking", "progress" -> {
                        val subagentId = event.payload["id"]?.jsonPrimitive?.content
                        val text = event.payload["text"]?.jsonPrimitive?.content ?: return
                        _uiState.update { it.copy(
                            messages = _uiState.value.messages.updateFirst({ msg ->
                                msg is ChatMessage.SubagentCard && !msg.isComplete &&
                                    (subagentId == null || msg.id == subagentId)
                            }) { msg ->
                                (msg as ChatMessage.SubagentCard).copy(text = text)
                            }
                        ) }
                    }
                }
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
                        kotlinx.coroutines.delay(ttl)
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
                    text = "Background task complete: ${event.text.take(200)}",
                    isError = false,
                )
                _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
            }

            is GatewayEvent.SessionInfo -> {
                (event.info["reasoning_effort"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { effort -> _uiState.update { it.copy(reasoningLevel = effort) } }
                Timber.d("[Chat] Session info: ${event.info}")
            }

            is GatewayEvent.GatewayStderr -> {
                Timber.w("[Chat] Gateway stderr: ${event.line}")
            }

            is GatewayEvent.ReasoningAvailable -> {
                Timber.d("[Chat] Reasoning available")
            }

            else -> {
                Timber.d("[Chat] Unhandled event: ${event::class.simpleName}")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun jsonToElementMap(obj: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()

    private fun List<GatewayEvent.TodoItem>.toUiTodos(): List<TodoItemUi> =
        map { todo ->
            TodoItemUi(
                id = todo.id,
                content = todo.content,
                status = when (todo.status) {
                    "in_progress" -> TodoStatus.IN_PROGRESS
                    "completed" -> TodoStatus.COMPLETED
                    "cancelled" -> TodoStatus.CANCELLED
                    else -> TodoStatus.PENDING
                },
            )
        }

    companion object {
        private const val PREFS_NAME = "hermes_chat_prefs"
        private const val KEY_DRAFT = "draft_message"
        private const val KEY_ASSISTANT_NAME = "assistant_display_name"
        private const val KEY_ASSISTANT_AVATAR = "assistant_avatar_path"
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        streamingDelegate.reset()
        saveDraft()
    }
}
