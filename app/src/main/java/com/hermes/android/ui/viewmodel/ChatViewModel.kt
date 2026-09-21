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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    // Set when a reconnect landed mid-turn: the snapshot had no reply yet and
    // the stream lost its start, so the reply is fetched once the turn ends.
    private var recoverOnTurnEnd = false

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
    )

    init {
        loadDraft()
        loadAssistantName()
        watchForQueuedPromptFlush()
        connectAndCollect()
        loadCommandCatalog()
    }

    /**
     * Sends a prompt parked during boot as soon as a session exists. Every path that
     * lands on a live session goes through activeSessionId, so watching it covers
     * create, resume, activate and post-reconnect recovery without threading a flush
     * call through each of them.
     *
     * Started once from init, not from connectAndCollect: that function re-runs on
     * every retry and cancels only connectionWatchJob and eventCollectionJob, so a
     * watcher launched there would survive as a second, third, nth collector — and
     * two of them racing the same parked prompt would send it twice.
     */
    private fun watchForQueuedPromptFlush() {
        viewModelScope.launch {
            _uiState.map { it.activeSessionId }.distinctUntilChanged().collect { sessionId ->
                if (sessionId != null) flushQueuedPrompt(sessionId)
            }
        }
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

    /**
     * Rolling average of how long this device takes to get a live gateway. Hermes is a
     * large Python agent starting under proot, so the honest answer is "tens of seconds";
     * measuring it per device beats a hardcoded guess that is wrong on every phone.
     */
    private val bootEstimate: Long
        get() = prefs.getLong(KEY_BOOT_ESTIMATE_MS, 0L)

    private fun recordBootDuration() {
        val startedAt = _uiState.value.connectingSince
        if (startedAt == 0L) return
        val elapsed = System.currentTimeMillis() - startedAt
        // Reconnects to an already-live gateway finish instantly and would drag the
        // estimate down to nothing; only real starts belong in the average.
        if (elapsed < MIN_BOOT_SAMPLE_MS || elapsed > MAX_BOOT_SAMPLE_MS) return
        val previous = prefs.getLong(KEY_BOOT_ESTIMATE_MS, 0L)
        val blended = if (previous == 0L) elapsed else (previous * 2 + elapsed) / 3
        prefs.edit().putLong(KEY_BOOT_ESTIMATE_MS, blended).apply()
    }


    private fun connectAndCollect() {
        connectionWatchJob?.cancel()
        eventCollectionJob?.cancel()
        viewModelScope.launch {
            connectionWatchJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                var wasConnected = false
                gatewayClient.connectionState.collect { state ->
                    val cameUp = state is ConnectionState.Connected && !wasConnected
                    wasConnected = state is ConnectionState.Connected
                    val chatState = when (state) {
                        is ConnectionState.Disconnected -> ChatConnectionState.Disconnected
                        is ConnectionState.Connecting -> ChatConnectionState.Connecting
                        is ConnectionState.Connected -> ChatConnectionState.Connected
                        is ConnectionState.Reconnecting -> ChatConnectionState.Reconnecting
                        is ConnectionState.Failed -> ChatConnectionState.Failed
                    }
                    // Timing for the wait UI: an opaque spinner is what makes a slow start
                    // feel broken, so the screen gets both the elapsed time and this
                    // device's own measured estimate to show progress against.
                    val connecting = chatState == ChatConnectionState.Connecting ||
                        chatState == ChatConnectionState.Reconnecting
                    _uiState.update { current ->
                        val startedAt = when {
                            connecting && current.connectingSince == 0L -> System.currentTimeMillis()
                            connecting -> current.connectingSince
                            else -> 0L
                        }
                        current.copy(
                            connectionState = chatState,
                            connectingSince = startedAt,
                            bootEstimateMs = bootEstimate,
                        )
                    }
                    if (chatState == ChatConnectionState.Connected) recordBootDuration()

                    if (state is ConnectionState.Disconnected ||
                        state is ConnectionState.Failed
                    ) {
                        streamingDelegate.finalizeOrphanedMessage(
                            if (state is ConnectionState.Failed) "(connection failed)" else "(connection lost)",
                        )
                    }

                    if (state is ConnectionState.Connected) {
                        val liveId = state.sessionId
                        val activeId = _uiState.value.activeSessionId
                        when {
                            // Back from a drop: the stream missed whatever the gateway
                            // pushed meanwhile, so the open chat is rebuilt from the
                            // server instead of trusting what is on screen.
                            activeId != null -> if (cameUp) launch { recoverActiveSession(activeId) }
                            liveId != null -> {
                                _uiState.update { it.copy(activeSessionId = liveId) }
                                launch { sessionDelegate.loadHistory(_uiState, liveId) }
                            }
                            else -> launch { sessionDelegate.createOrResume(_uiState) }
                        }
                        if (cameUp) launch { reattachBusyBackground() }
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

    /** Rebuild the open chat from the server's snapshot of [liveId]. */
    private suspend fun recoverActiveSession(liveId: String, turnEnded: Boolean = false) {
        streamingDelegate.reset()
        val storedId = _uiState.value.activeSessionKey ?: storedIdByLiveId[liveId]
        recoverOnTurnEnd = sessionDelegate.recover(_uiState, liveId, storedId, turnEnded) ?: return
        val newId = _uiState.value.activeSessionId
        if (storedId != null && newId != null && newId != liveId) storedIdByLiveId[newId] = storedId
    }

    /** Bring back the busy chats the user is not looking at, and settle the ones that ended meanwhile. */
    private suspend fun reattachBusyBackground() {
        val active = _uiState.value.activeSessionId
        val busy = backgroundSessions.snapshot().filter { (sid, activity) -> activity.isRunning && sid != active }.keys
        if (busy.isEmpty()) return
        val ended = sessionDelegate.reattachBackground(busy.associateWith { storedIdByLiveId[it] })
        ended.forEach { backgroundSessions.onTurnEnd(it, "", isActive = false) }
        if (ended.isNotEmpty()) publishSessionActivity()
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
                activeSessionKey = null,
                activeTodos = emptyList(),
                pendingApproval = null,
                // Starting a new chat mid-turn dropped the session id while isSending
                // stayed true; if session.new then failed nothing ever cleared it and
                // the composer stayed locked.
                isSending = false,
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
        val sessionId = _uiState.value.activeSessionId

        clearDraft()

        val refs = attachments.mapNotNull { it.refText }
        val outgoing = when {
            refs.isEmpty() -> text.ifEmpty { attachments.joinToString("\n") { "[User attached image: ${it.name}]" } }
            else -> (text + "\n" + refs.joinToString("\n")).trim()
        }

        // No live session yet — Hermes is still booting inside Alpine, which takes
        // seconds. Park the prompt instead of rejecting it: the bubble goes up now and
        // [flushQueuedPrompt] sends it the moment a session exists, so the boot wait
        // costs the user nothing but time they were already spending.
        val queued = sessionId == null
        val userMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
            attachments = attachments,
            queued = queued,
        )
        _uiState.update { it.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isSending = !queued,
            pendingAttachments = emptyList(),
            activeTodos = emptyList(),
            queuedPrompt = if (queued) {
                QueuedPrompt(
                    bubbleId = userMsg.id,
                    outgoing = outgoing,
                    isSlashCommand = text.startsWith("/"),
                )
            } else {
                it.queuedPrompt
            },
        ) }
        if (queued) return

        if (text.startsWith("/")) {
            handleSlashCommand(text, sessionId!!)
        } else {
            sendPrompt(outgoing, sessionId!!)
        }
    }

    /**
     * Sends whatever the user typed while Hermes was booting. Called on every
     * transition into a live session; a no-op when nothing is parked.
     */
    private fun flushQueuedPrompt(sessionId: String) {
        val pending = _uiState.value.queuedPrompt ?: return
        _uiState.update { state ->
            state.copy(
                queuedPrompt = null,
                isSending = true,
                messages = state.messages.map {
                    if (it is ChatMessage.User && it.id == pending.bubbleId) it.copy(queued = false) else it
                },
            )
        }
        if (pending.isSlashCommand) {
            handleSlashCommand(pending.outgoing, sessionId)
        } else {
            sendPrompt(pending.outgoing, sessionId)
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
        // The state reset comes first and unconditionally. Stop is the only way out of
        // isSending, and returning early on a missing session id — which happens when a
        // new chat is started mid-turn and session.new then fails — left the button
        // pressed with nothing able to release it: Send disabled, Stop inert, forever.
        val sessionId = _uiState.value.activeSessionId
        streamingDelegate.finalizeOrphanedMessage("(stopped)")
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateAll({ msg ->
                msg is ChatMessage.ToolCall && msg.isRunning
            }) { msg ->
                (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Interrupted")
            },
            isSending = false,
        ) }
        if (sessionId == null) return
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
                fun params(liveId: String) = buildJsonObject {
                    put("text", text)
                    put("session_id", liveId)
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
                sessionRepository.onLiveSession(
                    liveId = sessionId,
                    storedId = _uiState.value.activeSessionKey,
                    onRebound = { sessionDelegate.adoptRebound(_uiState, sessionId, it) },
                ) { liveId ->
                    gatewayClient.request(
                        method = GatewayMethods.PROMPT_SUBMIT,
                        params = jsonToElementMap(params(liveId)),
                    )
                }
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

    // ── Display name ─────────────────────────────────────────────────────

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
                        put("request_id", pending.requestId)
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

    // Clarify / sudo / secret are server→client requests: the agent blocks
    // until a response frame with the request's id comes back. They have no
    // RPC of their own (the old clarify.respond / sudo.respond / secret.respond
    // don't exist on the server, so answers never arrived).

    /** [picked]: the typed text, the chosen option, or — multi-select — every chosen option. */
    fun respondToClarify(requestId: String, picked: List<String>) {
        val card = findInteractiveRequest(requestId) ?: return
        answerServerRequest(
            requestId,
            kotlinx.serialization.json.JsonObject(
                mapOf("answer" to JsonPrimitive(clarifyAnswer(picked, card.multiSelect))),
            ),
        )
    }

    /** Batch clarify: every question's answer, keyed by its qid. */
    fun respondToClarifyBatch(requestId: String, answers: Map<String, List<String>>) {
        val card = findInteractiveRequest(requestId) ?: return
        val encoded = card.questions.associate { q ->
            q.qid to JsonPrimitive(clarifyAnswer(answers[q.qid].orEmpty(), q.multiSelect))
        }
        answerServerRequest(
            requestId,
            kotlinx.serialization.json.JsonObject(mapOf("answers" to kotlinx.serialization.json.JsonObject(encoded))),
        )
    }

    fun respondToSudo(requestId: String, password: String) =
        answerServerRequest(requestId, kotlinx.serialization.json.JsonObject(mapOf("value" to JsonPrimitive(password))))

    fun respondToSecret(requestId: String, value: String) =
        answerServerRequest(requestId, kotlinx.serialization.json.JsonObject(mapOf("value" to JsonPrimitive(value))))

    private fun findInteractiveRequest(requestId: String): ChatMessage.InteractiveRequest? =
        _uiState.value.messages.lastOrNull {
            it is ChatMessage.InteractiveRequest && it.requestId == requestId
        } as? ChatMessage.InteractiveRequest

    /** The clarify tool reads a multi-select answer as a JSON list and anything else as the bare string. */
    private fun clarifyAnswer(picked: List<String>, multiSelect: Boolean): String {
        val values = picked.filter { it.isNotBlank() }
        return if (multiSelect) {
            kotlinx.serialization.json.JsonArray(values.map { JsonPrimitive(it) }).toString()
        } else {
            values.firstOrNull().orEmpty()
        }
    }

    private fun answerServerRequest(requestId: String, result: kotlinx.serialization.json.JsonObject) {
        if (gatewayClient.respondToServerRequest(requestId, result)) {
            markAnswered(requestId)
        } else {
            _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Not connected — answer not sent")) }
        }
    }

    /**
     * Show a question the agent is blocked on — only in its own chat (another
     * chat's comes back through `open_requests` when that chat is opened), and
     * only once: a reconnect replay re-delivers the same request id.
     */
    private fun addInteractiveRequest(sessionId: String?, request: ChatMessage.InteractiveRequest) {
        _uiState.update { state ->
            if (sessionId != null && sessionId != state.activeSessionId) return@update state
            if (state.messages.any { it.id == request.id }) return@update state
            state.copy(messages = state.messages + request)
        }
    }

    /** The server withdrew a request (timeout, interrupt, answered elsewhere): its card stops taking answers. */
    private fun onRequestCancel(serverRequestId: String) {
        val pending = _uiState.value.pendingApproval
        if (pending != null && pending.serverRequestId == serverRequestId) {
            approvalNotificationManager.cancelApproval(pending.requestId)
        }
        _uiState.update { state ->
            state.copy(
                pendingApproval = state.pendingApproval?.takeUnless { it.serverRequestId == serverRequestId },
                messages = state.messages.updateFirst({ msg ->
                    msg is ChatMessage.InteractiveRequest && msg.requestId == serverRequestId && !msg.answered
                }) { msg ->
                    (msg as ChatMessage.InteractiveRequest).copy(expired = true)
                },
            )
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
                if (!stored.isNullOrBlank() && sid == activeSid) {
                    _uiState.update {
                        if (it.activeSessionId == sid && it.activeSessionKey == null) it.copy(activeSessionKey = stored) else it
                    }
                }
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
            is GatewayEvent.Error -> {
                // The chat on screen shows its own error; only a chat the user
                // is not looking at needs the drawer's red dot.
                if (sid == activeSid) return
                backgroundSessions.onError(sid); true
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

    /**
     * Deltas stream into the bubble MessageStart opened. When that frame never
     * reached the screen (a recovery reset the turn, or the socket dropped it),
     * the bubble is missing and every token would be discarded — open one.
     */
    private fun ensureStreamingBubble() {
        if (streamingDelegate.currentAssistantMessageId != null) return
        openAssistantBubble(streamingDelegate.onMessageStart())
    }

    private fun handleEvent(event: GatewayEvent) {
        val eventSid = event.sessionId
        val activeSid = _uiState.value.activeSessionId
        // Turn boundaries in the journal, so a drop can be placed inside or between turns.
        when (event) {
            is GatewayEvent.MessageStart -> Timber.i("[Turn] start session=$eventSid active=${eventSid == activeSid}")
            is GatewayEvent.MessageComplete -> Timber.i("[Turn] complete session=$eventSid chars=${event.text.length}")
            is GatewayEvent.Error -> Timber.w("[Turn] error session=$eventSid: ${event.message?.take(300)}")
            else -> Unit
        }
        trackSessionActivity(event, eventSid, activeSid)
        if (eventSid != null && activeSid != null && eventSid != activeSid &&
            event !is GatewayEvent.ApprovalRequest &&
            event !is GatewayEvent.ClarifyRequest &&
            event !is GatewayEvent.SudoRequest &&
            event !is GatewayEvent.SecretRequest &&
            event !is GatewayEvent.RequestCancel &&
            event !is GatewayEvent.BackgroundComplete &&
            // A background chat renaming itself still repaints the drawer.
            event !is GatewayEvent.SessionTitle
        ) {
            return
        }

        when (event) {
            is GatewayEvent.MessageStart -> {
                streamingDelegate.finalizeOrphanedMessage("(interrupted)")
                streamingDelegate.reset()
                openAssistantBubble(streamingDelegate.onMessageStart())
            }

            is GatewayEvent.MessageDelta -> {
                ensureStreamingBubble()
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
                // Nothing on screen to settle AND nothing in the frame: the reply
                // exists only in the stored history (a turn this screen never saw
                // stream — muted, queued or auto-continued — or lost frames).
                val replyOnlyInHistory = finalText.isBlank() && _uiState.value.messages.none { msg ->
                    msg is ChatMessage.Assistant && msg.isStreaming &&
                        (streamingId == null || msg.id == streamingId)
                }
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.withReplyLanded(
                        streamingId, finalText, event.reasoning,
                    ).filterNot { msg ->
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
                    },
                    isSending = false,
                    activeTodos = emptyList(),
                ) }
                streamingDelegate.reset()
                if (recoverOnTurnEnd || replyOnlyInHistory) {
                    recoverOnTurnEnd = false
                    val sid = _uiState.value.activeSessionId
                    if (sid != null) viewModelScope.launch { recoverActiveSession(sid, turnEnded = true) }
                }
            }

            is GatewayEvent.EventGap -> {
                // Frames for the open chat were lost on a live socket: the same
                // hole a reconnect leaves, so the same recovery.
                viewModelScope.launch { recoverActiveSession(event.sessionId) }
            }

            is GatewayEvent.ThinkingDelta -> {
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
            }

            is GatewayEvent.ReasoningDelta -> {
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
            }

            is GatewayEvent.ToolStart -> {
                // Drain buffered reasoning first so the mark below counts
                // everything that arrived before this tool.
                streamingDelegate.flushBuffer()
                _uiState.update { state ->
                    val existing = state.messages.firstOrNull { it.id == event.toolId } as? ChatMessage.ToolCall
                    val exists = state.messages.any { it.id == event.toolId }
                    val ownerId = existing?.reasoningOwnerId ?: streamingDelegate.currentAssistantMessageId
                    val mark = existing?.reasoningMark
                        ?: (state.messages.firstOrNull { it.id == ownerId } as? ChatMessage.Assistant)
                            ?.reasoning?.length
                        ?: 0
                    val toolMsg = ChatMessage.ToolCall(
                        id = event.toolId,
                        timestamp = System.currentTimeMillis(),
                        toolName = event.name ?: "unknown",
                        argsText = event.argsText,
                        resultText = null,
                        error = null,
                        isRunning = true,
                        durationS = null,
                        reasoningOwnerId = ownerId,
                        reasoningMark = mark,
                    )
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
                // The server's queue id: approval.respond resolves exactly this
                // entry (a random id here left the server guessing which one).
                val requestId = event.requestId.ifBlank { event.serverRequestId }
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
                        serverRequestId = event.serverRequestId,
                    ),
                ) }
            }

            is GatewayEvent.ClarifyRequest -> addInteractiveRequest(
                event.sessionId,
                ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = event.question,
                    choices = event.choices,
                    kind = InteractiveKind.CLARIFY,
                    multiSelect = event.multiSelect,
                    questions = event.questions.map { ClarifyQuestionUi(it.qid, it.question, it.choices, it.multiSelect) },
                ),
            )

            is GatewayEvent.SudoRequest -> addInteractiveRequest(
                event.sessionId,
                ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = "",
                    choices = null,
                    kind = InteractiveKind.SUDO,
                ),
            )

            is GatewayEvent.SecretRequest -> addInteractiveRequest(
                event.sessionId,
                ChatMessage.InteractiveRequest(
                    id = event.requestId,
                    timestamp = System.currentTimeMillis(),
                    requestId = event.requestId,
                    question = event.prompt,
                    choices = null,
                    kind = InteractiveKind.SECRET,
                ),
            )

            is GatewayEvent.RequestCancel -> onRequestCancel(event.requestId)

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
                (event.info["model"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() && event.sessionId == _uiState.value.activeSessionId }
                    ?.let { model ->
                        val provider = (event.info["provider"] as? JsonPrimitive)?.content
                        _uiState.update {
                            it.copy(sessionModel = model, sessionProvider = provider, sessionInfoSeq = it.sessionInfoSeq + 1)
                        }
                    }
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
        private const val KEY_BOOT_ESTIMATE_MS = "boot_estimate_ms"
        /** Below this, the gateway was already up and we only re-dialled. */
        private const val MIN_BOOT_SAMPLE_MS = 1_500L
        /** Above this, something stalled; averaging it in would poison the estimate. */
        private const val MAX_BOOT_SAMPLE_MS = 180_000L
        private const val KEY_ASSISTANT_NAME = "assistant_display_name"
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
