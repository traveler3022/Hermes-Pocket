package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayEventHelpers
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
    private val foregroundState: com.hermes.android.service.AppForegroundState,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── State ───────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _notification = MutableStateFlow<NotificationUi?>(null)
    val notification: StateFlow<NotificationUi?> = _notification.asStateFlow()

    private val _slashCommands = MutableStateFlow<List<SlashCommandSuggestion>>(emptyList())
    val slashCommands: StateFlow<List<SlashCommandSuggestion>> = _slashCommands.asStateFlow()

    /** Every command and alias Hermes knows, lowercase with the slash; empty until the catalog lands. */
    @Volatile
    private var knownCommands: Set<String> = emptySet()

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

    // A chat the user asked for (notification, drawer, task list) that has not
    // opened yet. While it is pending the screen does not pick a chat on its
    // own: on a cold start the connect path used to open the most recent one
    // in parallel, and whichever answer landed last won.
    private var requestedSessionId: String? = null
    private var resumeJob: Job? = null
    // The connect path opening a chat by itself; a requested one cancels it.
    private var autoPickJob: Job? = null

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
        // Asked once at init the catalog raced Hermes' boot and usually failed, leaving
        // only the built-in fallback list: ask on every connect instead.
        viewModelScope.launch {
            gatewayClient.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) loadCommandCatalog() }
        }
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
                val result = gatewayClient.request(GatewayMethods.COMMANDS_CATALOG) as? JsonObject
                val pairs = (result?.get("pairs") as? JsonArray)?.mapNotNull { row ->
                    val arr = row as? JsonArray ?: return@mapNotNull null
                    val name = (arr.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val desc = (arr.getOrNull(1) as? JsonPrimitive)?.content ?: ""
                    SlashCommandSuggestion(command = name, description = desc)
                } ?: emptyList()
                if (pairs.isEmpty()) return@launch
                // `canon` holds every name and alias; skills are only in `pairs`.
                val canon = (result?.get("canon") as? JsonObject)?.keys.orEmpty()
                knownCommands = (canon + pairs.map { it.command }).map { it.lowercase() }.toSet()
                _slashCommands.value = pairs
                    .filterNot { it.command.lowercase() in HIDDEN_SLASH_COMMANDS }
                    // Stable: everything after the useful ones keeps the catalog's order.
                    .sortedBy { FIRST_SLASH_COMMANDS.indexOf(it.command.lowercase()).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                Timber.i("[Chat] Loaded ${pairs.size} slash commands from catalog")
            } catch (e: Exception) {
                Timber.w(e, "[Chat] commands.catalog failed — slash command autocomplete will be empty until next retry")
            }
        }
    }

    /**
     * Only a name Hermes knows makes a slash command: `/sdcard/x.txt رو بخون` is a message.
     * Before the catalog arrives every `/…` is still a command, as it always was.
     */
    private fun isSlashCommand(text: String): Boolean =
        isSlashCommandText(text, knownCommands)

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
                        val requested = requestedSessionId
                        when {
                            // The asked-for chat opens instead; retried here when it
                            // could not reach the gateway before the socket was up.
                            requested != null -> if (resumeJob?.isActive != true) resumeSession(requested)
                            // Back from a drop: the stream missed whatever the gateway
                            // pushed meanwhile, so the open chat is rebuilt from the
                            // server instead of trusting what is on screen.
                            activeId != null -> if (cameUp) launch { recoverActiveSession(activeId) }
                            liveId != null -> {
                                _uiState.update { it.copy(activeSessionId = liveId) }
                                autoPickJob = launch { sessionDelegate.loadHistory(_uiState, liveId) }
                            }
                            else -> autoPickJob = launch { sessionDelegate.createOrResume(_uiState) }
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
        if (recoverOnTurnEnd) ensureStreamingBubble()
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

    /** The server's transcript changed under the open chat (an undo); show what it holds now. */
    fun reloadTranscript() {
        val sid = _uiState.value.activeSessionId ?: return
        viewModelScope.launch { sessionDelegate.loadHistory(_uiState, sid, allowEmpty = true) }
    }

    /** The chat id this screen was opened with (notification, Tasks, Sessions), already acted on. */
    private var openedRouteSessionId: String? = null

    /**
     * Open the chat the screen was navigated to, once. The id stays in the route,
     * so coming back to this screen (from Settings, or after the activity is
     * recreated) asked for it again: the chat the user had switched to since was
     * replaced by an old one, or 4001 when that one was gone.
     */
    fun openRouteSession(sessionId: String) {
        if (sessionId == openedRouteSessionId) return
        openedRouteSessionId = sessionId
        resumeSession(sessionId)
    }

    fun resumeSession(sessionId: String) {
        requestedSessionId = sessionId
        // An edit belongs to the chat it started in.
        if (_uiState.value.editingMessageId != null) cancelEditing()
        autoPickJob?.cancel()
        // Only the latest pick may land; an older one finishing late would
        // replace the chat the user just chose.
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            streamingDelegate.reset()
            // Reopening the chat on screen (back from the background, a reconnect)
            // or one known by its live id: not a stored id from the drawer.
            val isLiveId = sessionId == _uiState.value.activeSessionId || sessionId in storedIdByLiveId
            val running = sessionDelegate.resume(_uiState, sessionId, isLiveId)
            val opened = running != null
            // Joined mid-turn: its start went by before this chat was open, so
            // the reply is fetched once the turn ends. An idle chat drops the
            // flag the previous chat may have left behind.
            if (opened) recoverOnTurnEnd = running == true
            // The transcript holds no reply yet for a turn still running, and the screen
            // draws "thinking" only inside a reply bubble: without one, a chat opened
            // mid-turn showed nothing until the turn was over.
            if (running == true) ensureStreamingBubble()
            // Keep asking on the next connect only when the gateway was not
            // reachable; a refusal from the server will not change on retry.
            if ((opened || gatewayClient.connectionState.value is ConnectionState.Connected) &&
                requestedSessionId == sessionId
            ) {
                requestedSessionId = null
            }
            // resume() resolves the clicked (stored) id to the live one the
            // events carry; clear the badge under either spelling.
            _uiState.value.activeSessionId?.let { backgroundSessions.markRead(it) }
            liveIdsFor(sessionId).forEach { backgroundSessions.markRead(it) }
            publishSessionActivity()
        }
    }

    fun branchSession() {
        viewModelScope.launch {
            sessionDelegate.branch(_uiState) { _uiState.value.activeSessionId }
        }
    }

    fun newConversation() {
        if (_uiState.value.editingMessageId != null) cancelEditing()
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

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachments = _uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return
        val sessionId = _uiState.value.activeSessionId
        _uiState.value.editingMessageId?.let { editing ->
            if (text.isNotEmpty()) submitEdit(editing, text)
            return
        }

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
                    isSlashCommand = isSlashCommand(text),
                )
            } else {
                it.queuedPrompt
            },
        ) }
        if (queued) return

        if (isSlashCommand(text)) {
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
            sentText(lastUserMsg)
        } else {
            (sentText(lastUserMsg) + "\n" + refs.joinToString("\n")).trim()
        }

        val before = _uiState.value.messages
        val lastUserIndex = before.indexOfLast { it is ChatMessage.User }
        _uiState.update { it.copy(messages = before.subList(0, lastUserIndex + 1).toList(), isSending = true) }

        viewModelScope.launch {
            val rows = locateOnServer(sessionId, before, lastUserMsg)
            if (rows == null) {
                _uiState.update { it.copy(messages = before, isSending = false) }
                return@launch
            }
            sendPrompt(lastUserText, sessionId, truncateBeforeRowIds = rows) {
                _uiState.update { it.copy(messages = before) }
            }
        }
    }

    /** Puts a sent message back in the composer; sending replaces it and what followed. */
    fun startEditing(messageId: String) {
        if (_uiState.value.isSending) return
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } as? ChatMessage.User ?: return
        _uiState.update { it.copy(editingMessageId = messageId, inputText = sentText(message)) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessageId = null, inputText = "") }
    }

    private fun submitEdit(messageId: String, text: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isSending) return
        val before = _uiState.value.messages
        val index = before.indexOfFirst { it.id == messageId }
        val target = before.getOrNull(index) as? ChatMessage.User ?: run { cancelEditing(); return }
        // File references ride along as they did the first time; the edit is to the words.
        val refs = target.attachments.mapNotNull { it.refText }
        val outgoing = if (refs.isEmpty()) text else (text + "\n" + refs.joinToString("\n")).trim()
        val edited = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
            attachments = target.attachments,
        )
        clearDraft()
        _uiState.update { it.copy(
            messages = before.subList(0, index) + edited,
            editingMessageId = null,
            inputText = "",
            isSending = true,
        ) }
        viewModelScope.launch {
            val rows = locateOnServer(sessionId, before, target)
            if (rows == null) {
                _uiState.update { it.copy(messages = before, isSending = false, editingMessageId = messageId, inputText = text) }
                return@launch
            }
            // Refused (busy, stale): the server kept everything, so the screen must too.
            sendPrompt(outgoing, sessionId, truncateBeforeRowIds = rows) {
                _uiState.update { it.copy(messages = before, editingMessageId = messageId, inputText = text) }
            }
        }
    }

    /**
     * Removes a sent message and everything after it. The server has no cut without a
     * new prompt, only session.undo (the last turn), so the turns from the message to
     * the end are undone one by one, and the chat then reloads what the server kept.
     */
    fun deleteFromMessage(messageId: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isSending) return
        val messages = _uiState.value.messages
        val target = messages.firstOrNull { it.id == messageId } as? ChatMessage.User ?: return
        viewModelScope.launch {
            try {
                val server = sessionDelegate.serverUserTurns(sessionId)
                val rowId = server?.let { findUserRow(messages, target, it) }
                if (server == null || rowId == null) {
                    _uiState.update { it.copy(errorEvent = ErrorEvent.Error(MESSAGE_NOT_LOCATED)) }
                    return@launch
                }
                for (turn in 1..turnsFrom(server, rowId)) {
                    if (sessionRepository.undoLastTurn(sessionId) == 0) break
                }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] delete from message failed")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error(rewindFailure(e))) }
            }
            sessionDelegate.loadHistory(_uiState, sessionId, allowEmpty = true)
        }
    }

    /** [target]'s row on the server, or null after telling the user it could not be found. */
    private suspend fun locateOnServer(
        sessionId: String,
        local: List<ChatMessage>,
        target: ChatMessage.User,
    ): List<Long>? {
        val server = sessionDelegate.serverUserTurns(sessionId)
        val rowId = server?.let { findUserRow(local, target, it) }
        if (rowId == null) {
            _uiState.update { it.copy(errorEvent = ErrorEvent.Error(MESSAGE_NOT_LOCATED)) }
            return null
        }
        // The target first; the hidden rows are only tried if the server refuses it.
        return listOf(rowId) + hiddenRowsBefore(server, rowId)
    }

    private fun rewindFailure(e: Exception): String =
        if (e.message.orEmpty().startsWith("RPC error 4009:")) {
            "Hermes is still finishing the last reply. Try again in a moment."
        } else {
            "Could not change the chat: ${e.message}"
        }

    fun steerAgent() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val steerMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = "$STEER_PREFIX$text",
        )
        _uiState.update { it.copy(messages = _uiState.value.messages + steerMsg, inputText = "") }
        clearDraft()
        viewModelScope.launch {
            // The open chat's live id. session.most_recent answered with the newest
            // *stored* id, which session.steer (a live-only method) rejects with
            // 4001 "session not found" — every mid-turn message failed that way.
            val sessionId = _uiState.value.activeSessionId
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

    /**
     * prompt.submit aimed at the first of [rows] the server accepts (none: a plain send).
     * A refused target (4018) is rejected before anything is written, so the next is safe.
     */
    private suspend fun submitToFirstAddressable(
        rows: List<Long>,
        params: (Long?) -> JsonObject,
    ): kotlinx.serialization.json.JsonElement {
        val targets: List<Long?> = rows.ifEmpty { listOf(null) }
        for ((i, target) in targets.withIndex()) {
            try {
                return gatewayClient.request(GatewayMethods.PROMPT_SUBMIT, jsonToElementMap(params(target)))
            } catch (e: Exception) {
                if (target == null || i == targets.lastIndex || !e.message.orEmpty().startsWith("RPC error 4018:")) throw e
                Timber.w("[Chat] Row $target is not addressable; trying row ${targets[i + 1]}")
            }
        }
        error("no prompt.submit target")
    }

    private fun sendPrompt(
        text: String,
        sessionId: String,
        truncateBeforeRowIds: List<Long> = emptyList(),
        onRefused: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                fun params(liveId: String, truncateBeforeRowId: Long?) = buildJsonObject {
                    put("text", text)
                    put("session_id", liveId)
                    if (truncateBeforeRowId != null) {
                        // A rewind (edit, retry) is aimed at the target's durable row id
                        // alone: ordinals drift — a steered message is stored wherever the
                        // agent picked it up — and a mismatched ordinal is refused (4030).
                        // The cut is explicit (4029 without confirm_truncate), and cutting
                        // at the first turn empties the transcript, which needs its own
                        // opt-in (4028); both are the user's own action here.
                        put("truncate_before_row_id", truncateBeforeRowId)
                        put("confirm_truncate", true)
                        put("confirm_empty_truncate", true)
                    }
                }
                sessionRepository.onLiveSession(
                    liveId = sessionId,
                    storedId = _uiState.value.activeSessionKey,
                    onRebound = { sessionDelegate.adoptRebound(_uiState, sessionId, it) },
                ) { liveId -> submitToFirstAddressable(truncateBeforeRowIds) { params(liveId, it) } }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to send prompt")
                onRefused?.invoke()
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error(
                        if (truncateBeforeRowIds.isNotEmpty()) rewindFailure(e) else "Failed to send: ${e.message}",
                    ),
                    isSending = false,
                ) }
            }
        }
    }

    /**
     * Runs a slash command the way Hermes' own TUI does (`ui-tui/src/app/createSlashHandler.ts`):
     * `slash.exec` first, because `command.dispatch` alone only knows quick/plugin/skill commands
     * and a handful of built-ins, so `/help`, `/model`, `/status`… came back as error 4018.
     * `command.dispatch` is only the fallback for the refusals where `slash.exec` says the
     * command is not its to run.
     */
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
                // Hermes' own clients run these two themselves (ui-tui slash/commands/session.ts):
                // the slash worker has no side agent to hand them to.
                SIDE_AGENT_COMMANDS[name.lowercase()]?.let { method ->
                    startSideAgent(method, name, arg, sessionId)
                    return@launch
                }
                val result = try {
                    gatewayClient.request(
                        method = GatewayMethods.SLASH_EXEC,
                        params = jsonToElementMap(buildJsonObject {
                            put("command", withoutSlash)
                            put("session_id", sessionId)
                        }),
                    )
                } catch (e: GatewayException) {
                    if (!slashExecDisowns(e)) throw e
                    gatewayClient.request(
                        method = GatewayMethods.COMMAND_DISPATCH,
                        params = jsonToElementMap(buildJsonObject {
                            put("name", name)
                            put("arg", arg)
                            put("session_id", sessionId)
                        }),
                    )
                }
                applySlashResult(result, name, arg, sessionId, depth)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Slash command failed")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Command failed: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    private suspend fun startSideAgent(method: String, name: String, arg: String, sessionId: String) {
        if (arg.isBlank()) {
            postSlashStatus(if (method == GatewayMethods.PROMPT_BTW) "/btw <question>" else "/$name <prompt>")
            return
        }
        val result = gatewayClient.request(
            method = method,
            params = jsonToElementMap(buildJsonObject {
                put("session_id", sessionId)
                put("text", arg)
            }),
        ) as? JsonObject
        val taskId = (result?.get("task_id") as? JsonPrimitive)?.content
        postSlashStatus(
            when {
                taskId == null -> "/$name: no task started"
                method == GatewayMethods.PROMPT_BTW -> "btw $taskId — answering from a snapshot of this chat"
                else -> "bg $taskId started"
            },
        )
    }

    private fun applySlashResult(
        result: kotlinx.serialization.json.JsonElement?,
        name: String,
        arg: String,
        sessionId: String,
        depth: Int,
    ) {
        val obj = result as? JsonObject
        fun str(key: String) = (obj?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val notice = str("notice")?.takeIf { it.isNotBlank() }
        val message = str("message")
        when (str("type")) {
            "alias" -> {
                val target = str("target")
                if (!target.isNullOrBlank()) {
                    handleSlashCommand(if (arg.isNotBlank()) "/$target $arg" else "/$target", sessionId, depth + 1)
                    return
                }
            }
            "exec", "plugin" -> {
                postSlashStatus(str("output")?.takeIf { it.isNotBlank() } ?: "(no output)")
                return
            }
            "skill", "send" -> {
                notice?.let { postSlashStatus(it) }
                if (!message.isNullOrBlank()) {
                    sendPrompt(message, sessionId)
                } else {
                    postSlashStatus("/$name: empty message", isError = true)
                }
                return
            }
            "prefill" -> {
                notice?.let { postSlashStatus(it) }
                _uiState.update { it.copy(inputText = message ?: it.inputText, isSending = false) }
                return
            }
        }
        val output = extractCommandOutput(result)?.trim().orEmpty().ifBlank { "/$name: no output" }
        val warning = str("warning")?.takeIf { it.isNotBlank() }
        postSlashStatus(if (warning != null) "warning: $warning\n$output" else output)
    }

    private fun postSlashStatus(text: String, isError: Boolean = false) {
        _uiState.update { it.copy(
            messages = it.messages + ChatMessage.Status(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                text = text.trim(),
                isError = isError,
            ),
            isSending = false,
        ) }
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
        _uiState.update { it.copy(showSessionDrawer = !it.showSessionDrawer) }
    }

    /** However the drawer opened (hamburger or swipe), it shows a current chat list. */
    fun onSessionDrawerOpened() {
        _uiState.update { if (it.showSessionDrawer) it else it.copy(showSessionDrawer = true) }
        loadSessionList()
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = false) }
    }

    fun clearErrorEvent() {
        _uiState.update { it.copy(errorEvent = null) }
    }

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
                val settled = GatewayEventHelpers.isSettledSessionInfo(event.info) &&
                    backgroundSessions.onSettled(sid, isActive = sid == activeSid)
                val stored = (event.info["stored_session_id"] as? JsonPrimitive)?.content
                if (!stored.isNullOrBlank() && sid == activeSid) {
                    _uiState.update {
                        if (it.activeSessionId == sid && it.activeSessionKey == null) it.copy(activeSessionKey = stored) else it
                    }
                }
                if (!stored.isNullOrBlank() && storedIdByLiveId[sid] != stored) {
                    storedIdByLiveId[sid] = stored
                    true
                } else {
                    settled
                }
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
        // One reply streams per chat: a bubble the delegate lost track of (its id
        // reset by a recovery or a switch) was never settled and kept its dots.
        _uiState.update { it.copy(messages = it.messages.closeStrayReplies() + assistantMsg) }
    }

    /** Stop every reply still marked streaming; one with nothing in it is dropped. */
    private fun List<ChatMessage>.closeStrayReplies(): List<ChatMessage> = mapNotNull { msg ->
        when {
            msg !is ChatMessage.Assistant || !msg.isStreaming -> msg
            msg.text.isBlank() && msg.reasoning.isNullOrBlank() -> null
            else -> msg.copy(isStreaming = false)
        }
    }

    /**
     * Deltas stream into the bubble MessageStart opened. When that frame never
     * reached the screen (a recovery reset the turn, or the socket dropped it),
     * the bubble is missing and every token would be discarded — open one.
     */
    private fun ensureStreamingBubble() {
        // An id alone is not a bubble: switching chats while the one being left
        // still streams opens a bubble for it that the new transcript then drops,
        // and the id outlives it — the opened chat's reasoning went nowhere.
        val id = streamingDelegate.currentAssistantMessageId
        if (id != null && _uiState.value.messages.any { it.id == id }) return
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
        // No open chat (a new one waiting for its id) is not a licence to draw
        // anyone's frames: the chat left behind still streaming poured its text
        // and typing dots into the new, empty chat.
        if (eventSid != null && eventSid != activeSid &&
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
                // A recovery still waiting on its snapshot must not let that
                // older snapshot mark this turn running again.
                sessionDelegate.onTurnEnded()
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
                        }.closeStrayReplies()
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

            // Reasoning needs a bubble to land in just like text does; with none open
            // (a chat joined mid-turn) every chunk was flushed into nothing.
            is GatewayEvent.ThinkingDelta -> {
                ensureStreamingBubble()
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
            }

            is GatewayEvent.ReasoningDelta -> {
                ensureStreamingBubble()
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
                if (GatewayEventHelpers.isAuxiliaryNoise(event.text.orEmpty())) {
                    Timber.w("[Chat] auxiliary status (not shown): ${event.text}")
                    return
                }
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
                // In the app the approval sheet asks; the notification is for when the user is away.
                if (!foregroundState.isForeground) {
                    approvalNotificationManager.showApprovalRequest(
                        requestId = requestId,
                        sessionId = event.sessionId,
                        toolName = "terminal",
                        command = event.command,
                        description = event.description,
                        allowPermanent = event.allowPermanent,
                    )
                }
                // The approval sheet and the notification show the request. It is not added
                // to the chat: as plain text it stayed there for good, raw command and all.
                _uiState.update { it.copy(
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
                    text = "[bg ${event.taskId}] ${event.text}",
                    isError = false,
                )
                _uiState.update { it.copy(messages = _uiState.value.messages + msg) }
            }

            is GatewayEvent.BtwComplete -> {
                val question = event.question?.takeIf { it.isNotBlank() }?.let { " \"$it\"" }.orEmpty()
                val msg = ChatMessage.Status(
                    id = "btw-${event.taskId}",
                    timestamp = System.currentTimeMillis(),
                    text = "[btw$question] ${event.text}",
                    isError = false,
                )
                _uiState.update { it.copy(messages = it.messages + msg) }
            }

            is GatewayEvent.SessionInfo -> {
                // Busy only on a snapshot's word, and the turn's message.complete
                // never reached this chat (it went out before the snapshot was
                // answered, or while the socket was down): this frame, sent after
                // the server cleared `running`, is the turn end. Settle it the
                // way message.complete would, from the stored transcript.
                if (recoverOnTurnEnd && eventSid != null && eventSid == activeSid &&
                    GatewayEventHelpers.isSettledSessionInfo(event.info)
                ) {
                    Timber.i("[Turn] settled session=$eventSid without a message.complete; refetching")
                    recoverOnTurnEnd = false
                    sessionDelegate.onTurnEnded()
                    _uiState.update { it.copy(isSending = false) }
                    viewModelScope.launch { recoverActiveSession(eventSid, turnEnded = true) }
                }
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
        /**
         * `slash.exec` answers 4018 with exactly these texts when the command is not its own.
         * Every other 4018 comes from a `command.dispatch` handler it already forwarded to
         * (/retry, /undo, /compress…): dispatching again would run a mutating command twice.
         */
        private val SLASH_EXEC_NOT_MINE = Regex(
            "^skill command: use command\\.dispatch for /|use command\\.dispatch for /snapshot restore",
        )

        internal fun slashExecDisowns(e: GatewayException): Boolean =
            e.code == 4018 && SLASH_EXEC_NOT_MINE.containsMatchIn(e.rpcMessage.orEmpty())

        private val SIDE_AGENT_COMMANDS = mapOf(
            "btw" to GatewayMethods.PROMPT_BTW,
            "bg" to GatewayMethods.PROMPT_BACKGROUND,
            "background" to GatewayMethods.PROMPT_BACKGROUND,
        )

        internal fun isSlashCommandText(text: String, known: Set<String>): Boolean {
            if (!text.startsWith("/")) return false
            if (known.isEmpty()) return true
            val name = "/" + text.removePrefix("/").trimStart().substringBefore(' ').substringBefore('\n').lowercase()
            return name in known
        }

        /** Shown first in the `/` list: what the app has no button for and people reach for. */
        internal val FIRST_SLASH_COMMANDS = listOf(
            "/plan", "/goal", "/subgoal", "/btw", "/bg", "/loop", "/review", "/learn", "/status", "/help",
        )

        /**
         * Left out of the `/` list (typing them in full still runs them): the app has a
         * button or screen for them, or they only mean something in a terminal.
         */
        internal val HIDDEN_SLASH_COMMANDS = setOf(
            "/new", "/clear", "/retry", "/undo", "/branch", "/model", "/approve", "/deny",
            "/sessions", "/resume", "/stop", "/image",
            "/context", "/queue", "/compress", "/title", "/save", "/history",
            // Settings, Tools, Task Desk, Changes and the composer already do these.
            "/steer", "/reasoning", "/personality", "/approvals", "/yolo", "/config",
            "/reload", "/reload-mcp", "/tools", "/toolsets", "/skills", "/reload-skills",
            "/plugins", "/cron", "/platforms", "/agents", "/insights", "/usage",
            "/rollback", "/skin",
            "/redraw", "/prompt", "/palette", "/statusbar", "/battery", "/indicator",
            "/copy", "/paste", "/quit", "/wake", "/voice",
        )

        private const val PREFS_NAME = "hermes_chat_prefs"
        private const val KEY_DRAFT = "draft_message"
        private const val KEY_BOOT_ESTIMATE_MS = "boot_estimate_ms"
        /** Below this, the gateway was already up and we only re-dialled. */
        private const val MIN_BOOT_SAMPLE_MS = 1_500L
        /** Above this, something stalled; averaging it in would poison the estimate. */
        private const val MAX_BOOT_SAMPLE_MS = 180_000L
        private const val KEY_ASSISTANT_NAME = "assistant_display_name"
        private const val ACTIVITY_PUBLISH_INTERVAL_MS = 750L
        private const val MESSAGE_NOT_LOCATED =
            "Could not find this message in the stored chat, so nothing was changed. Reopen the chat and try again."
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        streamingDelegate.reset()
        saveDraft()
    }
}
