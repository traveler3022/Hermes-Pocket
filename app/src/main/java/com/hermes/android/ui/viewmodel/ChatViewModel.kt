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

    // ── Delegates ───────────────────────────────────────────────────────

    private val sessionDelegate = ChatSessionDelegate(
        gatewayClient, sessionRepository, viewModelScope
    ) { loadReasoningLevel() }

    private val streamingDelegate = ChatStreamingDelegate(viewModelScope, _uiState)
    private val backgroundSessions = BackgroundSessionTracker()
    private var lastActivityPublishedAt = 0L
    // Events carry a live session id; the drawer rows come from session.list,
    // which returns stored db ids. session.info reports both, so the badges
    // can be published under the id the rows are actually keyed by.
    private val storedIdByLiveId = mutableMapOf<String, String>()

    private val attachmentDelegate = ChatAttachmentDelegate(
        gatewayClient, hermesRuntime, context, viewModelScope,
    )

    private val drawerDelegate = ChatDrawerDelegate(
        gatewayClient, viewModelScope,
        loadSessionList = { sessionDelegate.loadList(it) },
        createNewSession = { sessionDelegate.create(it) },
        forgetSessionActivity = { sessionId ->
            liveIdsFor(sessionId).forEach {
                backgroundSessions.forget(it)
                storedIdByLiveId.remove(it)
            }
            publishSessionActivity()
        },
        isActiveSession = { drawerId ->
            val active = _uiState.value.activeSessionId
            active != null && active in liveIdsFor(drawerId)
        },
    )

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
                        val marker =
                            if (state is ConnectionState.Failed) "(connection failed)" else "(connection lost)"
                        streamingDelegate.finalizeOrphanedMessage(marker)
                        // finalizeOrphanedMessage only speaks for a bubble that
                        // was open. The socket is gone either way, so nothing
                        // will ever complete the tool cards still marked
                        // running or clear the composer's stop button: the
                        // chat sat on "working" forever, inviting the user to
                        // wait for a reply that can no longer arrive.
                        _uiState.update { s -> s.copy(
                            messages = s.messages.updateAll({ msg ->
                                msg is ChatMessage.ToolCall && msg.isRunning
                            }) { msg ->
                                (msg as ChatMessage.ToolCall)
                                    .copy(isRunning = false, resultText = msg.resultText ?: marker)
                            },
                            isSending = false,
                        ) }
                    }

                    if (state is ConnectionState.Connected) {
                        val liveId = state.sessionId
                        if (liveId != null && liveId != _uiState.value.activeSessionId) {
                            val hadTranscript = _uiState.value.messages.isNotEmpty()
                            _uiState.update { it.copy(activeSessionId = liveId) }
                            // A reconnect re-mints the live id of the SAME
                            // conversation, so the transcript already on screen
                            // IS this session. Refetching it over the top threw
                            // away the turn in flight: the bubble the deltas
                            // were landing in vanished, every later delta found
                            // nothing to append to, and the answer stopped
                            // halfway with no way to tell it had. Only fetch
                            // when there is nothing on screen to lose.
                            if (hadTranscript) {
                                Timber.i("[Chat] adopted live session $liveId, keeping the transcript on screen")
                            } else {
                                launch { sessionDelegate.loadHistory(_uiState, liveId) }
                            }
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
            // resume() resolves the clicked (stored) id to the live one the
            // events carry; clear the badge under either spelling.
            _uiState.value.activeSessionId?.let { backgroundSessions.markRead(it) }
            liveIdsFor(sessionId).forEach { backgroundSessions.markRead(it) }
            publishSessionActivity()
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

    fun sendSuggestion(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = text) }
        sendMessage()
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

        // Steer lines look like user messages on screen but were never user
        // turns on the server, so [retryTarget] skips them: resending one
        // would resubmit the arrow-prefixed nudge, and counting one would
        // rewind the server to the wrong turn.
        val target = _uiState.value.messages.retryTarget() ?: return
        val refs = target.message.attachments.mapNotNull { it.refText }
        val lastUserText = if (refs.isEmpty()) {
            target.message.text
        } else {
            (target.message.text + "\n" + refs.joinToString("\n")).trim()
        }

        val trimmedMessages = _uiState.value.messages.subList(0, target.index + 1).toList()
        _uiState.update { it.copy(messages = trimmedMessages, isSending = true) }

        sendPrompt(lastUserText, sessionId, truncateBeforeUserOrdinal = target.ordinal)
    }

    fun steerAgent() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val steerMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = "\u21B3 $text",
            isSteer = true,
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
            // process.stop is process_registry.kill_all() — it reaps every
            // session's background work, so stopping one chat killed the
            // others. process.list/process.kill are session-scoped.
            try {
                val listed = gatewayClient.request(
                    method = GatewayMethods.PROCESS_LIST,
                    params = jsonToElementMap(buildJsonObject { put("session_id", sessionId) }),
                    timeoutMs = 5_000,
                )
                val processes = (listed as? JsonObject)?.get("processes") as? JsonArray ?: JsonArray(emptyList())
                for (entry in processes) {
                    val row = entry as? JsonObject ?: continue
                    // The registry names a process id "session_id" (a "proc_…"
                    // handle), which is not the chat session id.
                    val procId = (row["session_id"] as? JsonPrimitive)?.content
                    if (procId.isNullOrBlank()) continue
                    if ((row["status"] as? JsonPrimitive)?.content == "exited") continue
                    gatewayClient.request(
                        method = GatewayMethods.PROCESS_KILL,
                        params = jsonToElementMap(buildJsonObject {
                            put("session_id", sessionId)
                            put("process_id", procId)
                        }),
                        timeoutMs = 5_000,
                    )
                }
            } catch (e: Exception) {
                Timber.d(e, "[Chat] session-scoped process cleanup skipped/failed")
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
                        // The server refuses truncating submits with 4029 unless the
                        // rewind is explicitly confirmed, so a stale ordinal on an
                        // ordinary submit can never silently drop history. Ordinal 0
                        // (regenerating the first turn) empties the transcript and
                        // needs the second opt-in, or the server answers 4028.
                        put("confirm_truncate", true)
                        if (truncateBeforeUserOrdinal == 0) put("confirm_empty_truncate", true)
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
                // Only the clarify answer is echoed — see markAnswered.
                markAnswered(requestId, answer)
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

    /**
     * [answer] is shown on the card so the reader can see what was chosen.
     * The sudo and secret paths pass nothing: a password or an API key must
     * never be written back into the transcript.
     */
    private fun markAnswered(requestId: String, answer: String? = null) {
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateFirst({ msg ->
                msg is ChatMessage.InteractiveRequest && msg.requestId == requestId
            }) { msg ->
                (msg as ChatMessage.InteractiveRequest).copy(
                    answered = true,
                    answer = answer?.takeIf { it.isNotBlank() },
                )
            }
        ) }
    }

    // ── Event handling ───────────────────────────────────────────────────

    /**
     * Keep every live session's turn state, including the ones the filter
     * below drops. The gateway runs them concurrently — one session key per
     * chat, exactly like a Telegram forum topic — so a chat the user is not
     * looking at still needs to show as busy and to flag its reply.
     */
    private fun trackSessionActivity(event: GatewayEvent, eventSid: String?, activeSid: String?) {
        val sid = eventSid ?: return
        // A delta arrives per token burst; publishing the map on each one would
        // recompose the whole chat screen at token rate, so only the turn
        // boundaries push immediately and the live preview is rate-limited.
        val publishNow = when (event) {
            is GatewayEvent.SessionInfo -> {
                val stored = (event.info["stored_session_id"] as? JsonPrimitive)?.content
                if (stored.isNullOrBlank() || storedIdByLiveId[sid] == stored) return
                storedIdByLiveId[sid] = stored
                true
            }
            is GatewayEvent.MessageStart -> {
                backgroundSessions.onTurnStart(sid); true
            }
            is GatewayEvent.MessageComplete -> {
                backgroundSessions.onTurnEnd(sid, event.text, isActive = sid == activeSid); true
            }
            is GatewayEvent.MessageDelta -> {
                backgroundSessions.onDelta(sid, event.text)
                val now = System.currentTimeMillis()
                if (now - lastActivityPublishedAt >= ACTIVITY_PUBLISH_INTERVAL_MS) {
                    lastActivityPublishedAt = now
                    true
                } else {
                    false
                }
            }
            else -> return
        }
        if (publishNow) publishSessionActivity()
    }

    /** Republish the badges under the ids the drawer rows use. */
    private fun publishSessionActivity() {
        val byDrawerId = backgroundSessions.snapshot()
            .mapKeys { (liveId, _) -> storedIdByLiveId[liveId] ?: liveId }
        _uiState.update { it.copy(sessionActivity = byDrawerId) }
    }

    /** Every live id this drawer row could be streaming under. */
    private fun liveIdsFor(drawerId: String): List<String> =
        listOf(drawerId) + storedIdByLiveId.filterValues { it == drawerId }.keys

    /** Put an empty streaming bubble on screen for [msgId] to stream into. */
    private fun openAssistantBubble(msgId: String) {
        val assistantMsg = ChatMessage.Assistant(
            id = msgId,
            timestamp = System.currentTimeMillis(),
            text = "",
            isStreaming = true,
            reasoning = null,
        )
        _uiState.update { it.copy(messages = it.messages + assistantMsg) }
    }

    private fun handleEvent(event: GatewayEvent) {
        val eventSid = event.sessionId
        val activeSid = _uiState.value.activeSessionId
        trackSessionActivity(event, eventSid, activeSid)
        if (eventSid != null && activeSid != null && eventSid != activeSid &&
            event !is GatewayEvent.ApprovalRequest &&
            event !is GatewayEvent.ClarifyRequest &&
            event !is GatewayEvent.SudoRequest &&
            event !is GatewayEvent.SecretRequest &&
            event !is GatewayEvent.BackgroundComplete &&
            // A background chat renaming itself still repaints the drawer.
            event !is GatewayEvent.SessionTitle
        ) {
            return
        }

        when (event) {
            is GatewayEvent.MessageStart -> {
                // A turn is not one message: the gateway opens a new one for
                // every stretch of narration between tool calls. Treating that
                // as an interruption stamped "(interrupted)" onto a fragment
                // the agent had just finished saying and cleared isSending
                // mid-turn, so the composer offered Send while the reply was
                // still being written. The previous fragment is simply
                // finished — seal it and open the next.
                //
                // reset() is deliberately not called here: it would also drop
                // the sealed-interim texts this turn needs at message.complete
                // to avoid repeating its own preview. The turn's own end
                // (message.complete, an error, a dead socket) resets.
                streamingDelegate.sealOpenBubble()
                openAssistantBubble(streamingDelegate.onMessageStart())
            }

            is GatewayEvent.MessageDelta -> {
                streamingDelegate.enqueueDelta(event.text)
            }

            is GatewayEvent.MessageInterim -> {
                streamingDelegate.sealInterim(event.text)?.let { openAssistantBubble(it) }
            }

            is GatewayEvent.SessionTitle -> {
                if (event.title.isNotBlank() && event.storedSessionId.isNotBlank()) {
                    _uiState.update { state ->
                        state.copy(
                            sessions = state.sessions.updateFirst({ it.id == event.storedSessionId }) {
                                it.copy(title = event.title)
                            },
                        )
                    }
                }
            }

            is GatewayEvent.SessionsChanged -> {
                // Fires on every message append of every session, floored to
                // one per 2s server-side. Only worth a refetch while the list
                // is on screen; opening the drawer reloads it anyway.
                if (_uiState.value.showSessionDrawer) loadSessionList()
            }

            is GatewayEvent.MessageComplete -> {
                streamingDelegate.flushBuffer()
                // A previewed answer repeats text already sealed on screen; any
                // other final text is new and follows the sealed commentary.
                val finalText = if (event.responsePreviewed) {
                    streamingDelegate.withoutSealedInterims(event.text)
                } else {
                    event.text
                }
                val streamingId = streamingDelegate.currentAssistantMessageId
                // Whether there is still a bubble to put the answer in. A
                // turn can end with none: the socket dropped and finalized it,
                // a reconnect replaced the transcript, message.start never
                // arrived. Every one of those silently threw the finished
                // answer away — the reply simply never appeared — so when
                // nothing matches, the answer is appended as its own message
                // instead of being dropped.
                val hasOpenBubble = _uiState.value.messages.any { msg ->
                    msg is ChatMessage.Assistant && msg.isStreaming &&
                        (streamingId == null || msg.id == streamingId)
                }
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.Assistant && msg.isStreaming &&
                            (streamingId == null || msg.id == streamingId)
                    }) { msg ->
                        (msg as ChatMessage.Assistant).copy(
                            text = finalText.ifEmpty { msg.text },
                            isStreaming = false,
                            reasoning = event.reasoning?.takeIf { it.isNotBlank() } ?: msg.reasoning,
                        )
                    }.filterNot { msg ->
                        // The bubble sealInterim opened, on a turn that ended
                        // with nothing left to put in it.
                        msg is ChatMessage.Assistant && msg.id == streamingId &&
                            msg.text.isBlank() && msg.reasoning.isNullOrBlank()
                    }.let { msgs ->
                        msgs.updateAll({ msg ->
                            msg is ChatMessage.ToolCall && msg.isRunning
                        }) { msg ->
                            (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Completed")
                        }
                    }.let { msgs ->
                        if (hasOpenBubble || finalText.isBlank()) {
                            msgs
                        } else {
                            msgs + ChatMessage.Assistant(
                                // A fresh id: streamingId may still be on a
                                // bubble sealed earlier, and a repeated id is
                                // fatal to the keyed list that renders this.
                                id = UUID.randomUUID().toString(),
                                timestamp = System.currentTimeMillis(),
                                text = finalText,
                                isStreaming = false,
                                reasoning = event.reasoning?.takeIf { r -> r.isNotBlank() },
                            )
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

            is GatewayEvent.TodoUpdated -> {
                _uiState.update { it.copy(activeTodos = event.todos.toUiTodos()) }
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
                // The event names the tool it is reporting on. Several tools
                // can be running at once, so "the first running card" is the
                // wrong card as soon as that happens — match the name when
                // there is one and only fall back to the first runner when
                // the gateway sent none.
                val progressName = event.name?.takeIf { it.isNotBlank() }
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.ToolCall && msg.isRunning &&
                            (progressName == null || msg.toolName == progressName)
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
                // The turn is over. Without this the half-written bubble kept
                // its typing animation forever, and the buffers and sealed
                // interims of the dead turn bled into the next one.
                streamingDelegate.sealOpenBubble()
                streamingDelegate.reset()
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
        private const val ACTIVITY_PUBLISH_INTERVAL_MS = 750L
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        streamingDelegate.reset()
        saveDraft()
    }
}
