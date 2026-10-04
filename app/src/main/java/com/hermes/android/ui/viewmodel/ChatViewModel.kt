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
import com.hermes.android.gateway.asText
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
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.contentOrNull

/**
 * ViewModel for the Chat screen — coordinator that delegates to focused
 * sub-handlers for session management, attachments, streaming, drawer UI, slash
 * commands, the agent's requests, reactions and edits of what was sent.
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
    private val profiles: com.hermes.android.data.ProfilesRepository? = null,
) : ViewModel() {

    // ── State ───────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _notification = MutableStateFlow<NotificationUi?>(null)
    val notification: StateFlow<NotificationUi?> = _notification.asStateFlow()

    val slashCommands: StateFlow<List<SlashCommandSuggestion>> get() = slashDelegate.suggestions

    private var eventCollectionJob: Job? = null
    private var connectionWatchJob: Job? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Connected to a remote server (features that live on its dashboard, like Kanban). */
    val isRemoteRuntime: Boolean
        get() = hermesRuntime.type == com.hermes.android.runtime.RuntimeType.REMOTE

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
        renameSession = { id, title -> sessionRepository.rename(id, title) },
        forgetSessionActivity = { sessionId ->
            liveIdsFor(sessionId).forEach {
                backgroundSessions.forget(it)
                storedIdByLiveId.remove(it)
            }
            publishSessionActivity()
        },
    )

    private val slashDelegate = ChatSlashDelegate(gatewayClient, viewModelScope, _uiState) { text, sessionId ->
        sendPrompt(text, sessionId)
    }

    private val requestsDelegate = ChatRequestsDelegate(
        gatewayClient, approvalNotificationManager, foregroundState, viewModelScope, _uiState,
    )

    private val reactionsDelegate = ChatReactionsDelegate(
        gatewayClient, sessionDelegate,
        context.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE),
        viewModelScope, _uiState,
    )

    private val rewindDelegate = ChatRewindDelegate(
        sessionRepository, sessionDelegate, viewModelScope, _uiState, clearDraft = ::clearDraft,
    ) { text, sessionId, rows, bubbleId, onRefused ->
        sendPrompt(text, sessionId, truncateBeforeRowIds = rows, bubbleId = bubbleId, onRefused = onRefused)
    }

    init {
        loadDraft()
        watchForQueuedPromptFlush()
        connectAndCollect()
        watchProfileSwitch()
        // Asked once at init the catalog raced Hermes' boot and usually failed, leaving
        // only the built-in fallback list: ask on every connect instead.
        viewModelScope.launch {
            gatewayClient.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected ->
                    if (connected) {
                        slashDelegate.loadCatalog()
                        reactionsDelegate.onConnected()
                    }
                }
        }
    }

    /**
     * Another profile was picked: its chats are not this one's, so open a new chat
     * in it and list its sessions. A chat still running in the old profile keeps
     * going in the background (its calls carry its own profile).
     */
    private fun watchProfileSwitch() {
        val switches = profiles?.switches ?: return
        viewModelScope.launch {
            switches.collect {
                newConversation()
                loadSessionList()
            }
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
                    // One malformed frame must not take the chat down: an exception
                    // here crashed the app (viewModelScope has no handler) and, short
                    // of that, would have ended this collector for good.
                    try {
                        handleEvent(event)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.e(e, "[Chat] could not handle ${event::class.simpleName}")
                    }
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
                activeSessionId = null,
                activeSessionKey = null,
                // The new chat's folder comes with its session.info, not the last chat's.
                sessionCwd = null,
                activeTodos = emptyList(),
                pendingApproval = null,
                // Starting a new chat mid-turn dropped the session id while isSending
                // stayed true; if session.new then failed nothing ever cleared it and
                // the composer stayed locked.
                isSending = false,
                thinkingStatus = "",
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
            if (text.isNotEmpty()) rewindDelegate.submitEdit(editing, text)
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
                    isSlashCommand = slashDelegate.isCommand(text),
                )
            } else {
                it.queuedPrompt
            },
        ) }
        if (sessionId == null) return

        if (slashDelegate.isCommand(text)) {
            slashDelegate.run(text, sessionId)
        } else {
            sendPrompt(outgoing, sessionId, bubbleId = userMsg.id)
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
            slashDelegate.run(pending.outgoing, sessionId)
        } else {
            sendPrompt(pending.outgoing, sessionId, bubbleId = pending.bubbleId)
        }
    }

    fun retryLastMessage() = rewindDelegate.retryLast()

    fun startEditing(messageId: String) = rewindDelegate.startEditing(messageId)

    fun cancelEditing() = rewindDelegate.cancelEditing()

    fun deleteFromMessage(messageId: String) = rewindDelegate.deleteFrom(messageId)

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
                val steered = sessionRepository.steer(sessionId, text)
                if (!steered.accepted) {
                    _uiState.update { it.copy(
                        messages = _uiState.value.messages + ChatMessage.Status(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            text = steered.note ?: "Steer rejected — the agent isn't at a steerable point right now.",
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
            thinkingStatus = "",
        ) }
        if (sessionId == null) return
        viewModelScope.launch { sessionRepository.stopTurn(sessionId) }
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

    /**
     * A prompt from an inline `::preview` page (`hermes.send`): a user turn with no bubble,
     * typed `display_kind: hidden` as the desktop sends it, so the stored transcript skips it
     * too. The page updating is the visible answer.
     */
    fun sendHiddenPrompt(text: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        _uiState.update { it.copy(isSending = true) }
        sendPrompt(text, sessionId, hidden = true)
    }

    suspend fun readPreviewFile(file: String): String? =
        attachmentDelegate.readPreviewFile(file, _uiState.value.sessionCwd)

    suspend fun previewShareUri(file: String): android.net.Uri? =
        attachmentDelegate.previewShareUri(file, _uiState.value.sessionCwd)

    /** The full-screen viewer's URL for a `::preview` file. */
    fun resolvePreviewUrl(file: String): String =
        resolveMediaUrl(attachmentDelegate.previewPath(file, _uiState.value.sessionCwd) ?: file)

    private fun sendPrompt(
        text: String,
        sessionId: String,
        truncateBeforeRowIds: List<Long> = emptyList(),
        hidden: Boolean = false,
        /** The user bubble this send is, to take the row the server stored it as. */
        bubbleId: String? = null,
        onRefused: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                fun params(liveId: String, truncateBeforeRowId: Long?) = buildJsonObject {
                    put("text", text)
                    put("session_id", liveId)
                    if (hidden) put("display_kind", "hidden")
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
                val reply = sessionRepository.onLiveSession(
                    liveId = sessionId,
                    storedId = _uiState.value.activeSessionKey,
                    onRebound = { sessionDelegate.adoptRebound(_uiState, sessionId, it) },
                ) { liveId -> submitToFirstAddressable(truncateBeforeRowIds) { params(liveId, it) } }
                // A message sent mid-turn is folded into the running turn, which
                // keeps streaming without a new message.start. Its bubble sat above
                // the message, so the rest of the turn landed there and its tools
                // below it with no bubble to fold into. A hidden prompt (a preview page's) has
                // no bubble, so the reply stays whole.
                val status = ((reply as? JsonObject)?.get("status") as? JsonPrimitive)?.contentOrNull
                if (!hidden && (status == "redirected" || status == "steered")) {
                    streamingDelegate.continueBelow()?.let { openAssistantBubble(it) }
                }
                // The row written for this very send, which Hermes names once it is stored
                // (never for a steered, queued or redirected message). It goes on this send's
                // own bubble only, never on whichever user message is newest (the desktop's
                // submit.ts): reactions, edit and delete address the message by it.
                val userRowId = (reply as? JsonObject)?.get("user_row_id").asText()?.toLongOrNull()
                if (bubbleId != null && userRowId != null) {
                    _uiState.update { state ->
                        state.copy(messages = state.messages.updateFirst({ it.id == bubbleId && it is ChatMessage.User }) {
                            (it as ChatMessage.User).copy(rowId = userRowId)
                        })
                    }
                }
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

    // ── Reactions (delegated) ────────────────────────────────────────────

    fun reactToMessage(messageId: String, emoji: String?) = reactionsDelegate.react(messageId, emoji)

    // ── Interactive responds (delegated) ─────────────────────────────────

    fun respondToApproval(choice: String) = requestsDelegate.respondToApproval(choice)
    fun respondToClarify(requestId: String, picked: List<String>) = requestsDelegate.respondToClarify(requestId, picked)
    fun respondToClarifyBatch(requestId: String, answers: Map<String, List<String>>) =
        requestsDelegate.respondToClarifyBatch(requestId, answers)
    fun respondToSudo(requestId: String, password: String) = requestsDelegate.respondToSudo(requestId, password)
    fun respondToSecret(requestId: String, value: String) = requestsDelegate.respondToSecret(requestId, value)

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
                val stored = (event.info["stored_session_id"] as? JsonPrimitive)?.contentOrNull
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
    /**
     * Gives the reply that just ended the turn the row Hermes stored it as. Only the
     * newest reply, only when it shows exactly the stored text, and only when it has no
     * row yet: a reply split over sealed commentary, or already reloaded, keeps what it has.
     */
    private fun bindFinalReplyRow(rowId: Long, storedText: String) {
        _uiState.update { state ->
            val index = state.messages.indexOfLast { it is ChatMessage.Assistant }
            val reply = state.messages.getOrNull(index) as? ChatMessage.Assistant
            if (reply == null || reply.rowId != null || reply.text.trim() != storedText.trim()) {
                state
            } else {
                state.copy(messages = state.messages.toMutableList().also { it[index] = reply.copy(rowId = rowId) })
            }
        }
    }

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
                if (_uiState.value.thinkingStatus.isNotEmpty()) _uiState.update { it.copy(thinkingStatus = "") }
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

            is GatewayEvent.AgentReaction -> reactionsDelegate.onAgentReaction(event)

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
                    thinkingStatus = "",
                ) }
                event.finalAssistantRowId?.let { rowId -> bindFinalReplyRow(rowId, event.text) }
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
            // thinking.delta is the agent's spinner / wait line ("(◕‿◕) pondering...",
            // a slow-provider notice, "" to clear), not reasoning. Appended to the
            // reasoning, it filled the trace with spinner phrases run together.
            is GatewayEvent.ThinkingDelta -> {
                val status = event.text.trim()
                if (status.isNotEmpty()) ensureStreamingBubble()
                if (status != _uiState.value.thinkingStatus) _uiState.update { it.copy(thinkingStatus = status) }
            }

            is GatewayEvent.ReasoningDelta -> {
                ensureStreamingBubble()
                streamingDelegate.enqueueDelta(event.text, isReasoning = true)
            }

            is GatewayEvent.ToolStart -> {
                // Drain buffered reasoning first so the tool's mark counts
                // everything that arrived before it.
                streamingDelegate.flushBuffer()
                _uiState.update { it.withToolStarted(event, streamingDelegate.currentAssistantMessageId) }
            }

            is GatewayEvent.TodoUpdated -> {
                _uiState.update { it.copy(activeTodos = event.todos.toUiTodos()) }
            }

            is GatewayEvent.ToolComplete -> _uiState.update { it.withToolCompleted(event) }

            is GatewayEvent.ToolProgress -> _uiState.update { it.copy(messages = it.messages.withToolProgress(event)) }

            is GatewayEvent.ToolGenerating -> {
                Timber.d("[Chat] Tool generating: ${event.name}")
            }

            is GatewayEvent.Error -> {
                val isRateLimit = event.message?.contains("rate_limit", ignoreCase = true) == true ||
                        event.message?.contains("429") == true
                val displayMsg = if (isRateLimit) "Rate limited — please wait" else event.message
                // A failed turn ends with this event alone, no message.complete
                // (tui_gateway's turn dispatcher), so the reply kept its typing dots
                // until the next turn. It ends here like any other turn.
                sessionDelegate.onTurnEnded()
                streamingDelegate.flushBuffer()
                _uiState.update { it.copy(
                    messages = it.messages.updateAll({ msg ->
                        msg is ChatMessage.ToolCall && msg.isRunning
                    }) { msg ->
                        (msg as ChatMessage.ToolCall).copy(isRunning = false, error = displayMsg)
                    }.closeStrayReplies(),
                    errorEvent = ErrorEvent.Warning(displayMsg ?: "Unknown error"),
                    isSending = false,
                    thinkingStatus = "",
                ) }
                streamingDelegate.reset()
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

            is GatewayEvent.ApprovalRequest -> requestsDelegate.onApprovalRequest(
                event,
                // The drawer marks the waiting chat by its stored id; the event only has the live one.
                sessionKey = event.sessionId?.let { sid ->
                    storedIdByLiveId[sid] ?: _uiState.value.activeSessionKey.takeIf { sid == activeSid }
                },
            )

            is GatewayEvent.ClarifyRequest -> requestsDelegate.onClarifyRequest(event)

            is GatewayEvent.SudoRequest -> requestsDelegate.onSudoRequest(event)

            is GatewayEvent.SecretRequest -> requestsDelegate.onSecretRequest(event)

            is GatewayEvent.RequestCancel -> requestsDelegate.onRequestCancel(event.requestId)

            is GatewayEvent.SubagentEvent -> _uiState.update { it.copy(messages = it.messages.withSubagentEvent(event)) }

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
                (event.info["reasoning_effort"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { effort -> _uiState.update { it.copy(reasoningLevel = effort) } }
                (event.info["cwd"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() && event.sessionId == _uiState.value.activeSessionId }
                    ?.let { cwd -> _uiState.update { it.copy(sessionCwd = cwd) } }
                (event.info["model"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() && event.sessionId == _uiState.value.activeSessionId }
                    ?.let { model ->
                        val provider = (event.info["provider"] as? JsonPrimitive)?.contentOrNull
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

    companion object {
        private const val PREFS_NAME = "hermes_chat_prefs"
        private const val KEY_DRAFT = "draft_message"
        private const val KEY_BOOT_ESTIMATE_MS = "boot_estimate_ms"
        /** Below this, the gateway was already up and we only re-dialled. */
        private const val MIN_BOOT_SAMPLE_MS = 1_500L
        /** Above this, something stalled; averaging it in would poison the estimate. */
        private const val MAX_BOOT_SAMPLE_MS = 180_000L
        private const val ACTIVITY_PUBLISH_INTERVAL_MS = 750L
        /** ThemeModeState's file, where the Appearance switches live. */
        private const val APPEARANCE_PREFS = "hermes_prefs"
    }

    override fun onCleared() {
        super.onCleared()
        reactionsDelegate.close()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        streamingDelegate.reset()
        saveDraft()
    }
}
