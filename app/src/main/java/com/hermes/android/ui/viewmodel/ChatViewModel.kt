package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Slash-command catalog from the gateway (replaces the hardcoded list). */
    private val _slashCommands = MutableStateFlow<List<SlashCommandSuggestion>>(emptyList())
    val slashCommands: StateFlow<List<SlashCommandSuggestion>> = _slashCommands.asStateFlow()

    private var eventCollectionJob: Job? = null
    private var connectionWatchJob: Job? = null
    private var activeAssistantMessageId: String? = null
    private val streamingBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var streamingFlushJob: Job? = null

    // Feature #23: SharedPreferences for draft persistence
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadDraft()
        loadAssistantName()
        loadAssistantAvatar()
        connectAndCollect()
        loadCommandCatalog()
        loadReasoningLevel()
    }

    /**
     * Reasoning effort (agent.reasoning_effort), quick-switchable from the
     * input bar — mirrors ConfigViewModel's setting under Settings > General
     * so both stay in sync (both read/write the same config.yaml key).
     */
    private fun loadReasoningLevel() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf(
                        "command" to JsonPrimitive(
                            "python3 - <<'H2PYEOF'\n" +
                                "import yaml, pathlib\n" +
                                "p = pathlib.Path.home() / '.hermes' / 'config.yaml'\n" +
                                "d = yaml.safe_load(p.read_text()) if p.exists() else {}\n" +
                                "d = d or {}\n" +
                                "print(str((d.get('agent') or {}).get('reasoning_effort', '') or 'medium'))\n" +
                                "H2PYEOF"
                        ),
                    ),
                )
                val level = (result as? JsonObject)?.get("stdout")?.let { (it as? JsonPrimitive)?.content }
                    ?.trim()?.takeIf { it.isNotBlank() } ?: "medium"
                _uiState.update { it.copy(reasoningLevel = level) }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] Failed to load reasoning level")
            }
        }
    }

    /**
     * Fix: this used to hand-edit config.yaml directly, which only affects
     * future sessions. Verified against tui_gateway/server.py: config.set's
     * key="reasoning" case, when given a session_id, sets
     * session["create_reasoning_override"] and updates the live agent's
     * reasoning_config in place — an immediate effect on the CURRENT chat.
     * Passing our own activeSessionId is exactly that live-session path.
     */
    fun setReasoningLevel(rawLevel: String) {
        val level = rawLevel.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("key", "reasoning")
                    put("value", level)
                    _uiState.value.activeSessionId?.let { put("session_id", it) }
                }
                gatewayClient.request(GatewayMethods.CONFIG_SET, jsonToElementMap(params))
                _uiState.update { it.copy(reasoningLevel = level) }
                Timber.i("[Chat] reasoning set to $level (session=${_uiState.value.activeSessionId})")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to set reasoning level")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("Failed to set reasoning: ${e.message}")) }
            }
        }
    }

    /**
     * Load the real slash-command catalog from Hermes (`commands.catalog`,
     * no params) instead of a hardcoded list. Response shape:
     *   { pairs: [[name, description], ...], ... }
     */
    private fun loadCommandCatalog() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.COMMANDS_CATALOG)
                val pairs = (result as? JsonObject)?.get("pairs") as? kotlinx.serialization.json.JsonArray
                val cmds = pairs?.mapNotNull { row ->
                    val arr = row as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
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
                    _uiState.update { it.copy(connectionState = chatState) }

                    // Fix F-A5: When connection is lost mid-stream, finalize any
                    // assistant message that was left in isStreaming=true state.
                    // Without this, the spinner spins forever and the user can't
                    // tell the message is incomplete. We mark it as not-streaming
                    // and append a small "(connection lost)" marker so the user
                    // knows the turn was interrupted.
                    if (state is ConnectionState.Disconnected ||
                        state is ConnectionState.Failed
                    ) {
                        finalizeOrphanedStreamingMessage(
                            marker = if (state is ConnectionState.Failed) {
                                "(connection failed)"
                            } else {
                                "(connection lost)"
                            }
                        )
                    }

                    // When connected, create or resume a session.
                    //
                    // OkHttpGatewayClient runs its OWN low-level auto-resume on
                    // reconnect (using its internally-tracked lastSessionId) and,
                    // once fixed, re-publishes the resulting live session id via
                    // this same connectionState — so if that already resolved a
                    // live id, adopt it directly (just fetch its transcript) rather
                    // than issuing a second, redundant session.resume RPC.
                    if (state is ConnectionState.Connected) {
                        val liveId = state.sessionId
                        if (liveId != null && liveId != _uiState.value.activeSessionId) {
                            _uiState.update { it.copy(activeSessionId = liveId) }
                            launch { loadSessionHistory(liveId) }
                        } else if (liveId == null && _uiState.value.activeSessionId == null) {
                            // No id yet from the low-level client (either a brand
                            // new process with nothing to resume, or its resume is
                            // still in flight and hasn't re-published yet). Do our
                            // own most_recent-based resume as the safety net for
                            // the case nothing else will — e.g. the Activity/
                            // ChatViewModel was recreated while the WebSocket
                            // itself never actually dropped, so no low-level
                            // reconnect-resume ever fires. session.resume's fast
                            // path reuses the same live id when the worker is
                            // still alive, so overlapping with an in-flight
                            // low-level resume here is a redundant round-trip at
                            // worst, not destructive.
                            createOrResumeSession()
                        }
                    }
                }
            }

            // Collect events. Must be attached BEFORE connect() is called, not
            // after: events.replay is 0 (see OkHttpGatewayClient), so any
            // message.start/delta/complete the gateway pushes right after the
            // handshake — e.g. as part of a session resume — would otherwise
            // race the collector attaching and be dropped forever, silently
            // wiping the messages that should have shown up as chat history.
            // UNDISPATCHED guarantees the collector is live before this
            // coroutine yields to call connect() below.
            eventCollectionJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.events.collect { event ->
                    handleEvent(event)
                }
            }

            // Connect to gateway
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

    // ── Session management ────────────────────────────────────────────────

    /**
     * On a fresh ChatViewModel (cold app start, or process death + relaunch —
     * the common case, not just first-ever launch), unconditionally calling
     * createSession() meant every reconnect threw away whatever conversation
     * was in flight and showed a blank chat. The gateway keeps a disconnected
     * session alive for a grace window (see ws.py's `_close_sessions_for_transport`
     * orphan reaper) specifically so a reconnecting client can pick the same
     * conversation back up — but nothing here was actually trying that. Now,
     * before creating a new session, check `session.most_recent` and resume
     * it if one exists; only fall back to a genuinely blank session when there
     * is nothing to resume (first-ever use). An explicit resumeSessionId from
     * Sessions/share-intent (ChatScreen's LaunchedEffect) still runs after this
     * and wins, since it always overwrites activeSessionId unconditionally.
     */
    private suspend fun createOrResumeSession() {
        val mostRecentId = try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            (mr as? JsonObject)?.get("session_id")?.let { (it as? JsonPrimitive)?.content }
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] session.most_recent failed, falling back to a new session")
            null
        }
        if (mostRecentId != null) {
            resumeSession(mostRecentId)
        } else {
            createSession()
        }
    }

    private suspend fun createSession() {
        try {
            val result = gatewayClient.request(GatewayMethods.SESSION_CREATE)
            val sessionId = (result as? kotlinx.serialization.json.JsonObject)
                ?.get("session_id")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
            if (sessionId != null) {
                _uiState.update { it.copy(activeSessionId = sessionId) }
                Timber.i("[Chat] Session created: $sessionId")
            }
        } catch (e: GatewayException) {
            Timber.e(e, "[Chat] Failed to create session")
            _uiState.update { it.copy(
                errorEvent = ErrorEvent.Error("Failed to create session: ${e.message}")
            ) }
        }
    }

    fun loadSessionList() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.SESSION_LIST)
                val sessions = parseSessionList(result)
                _uiState.update { it.copy(sessions = sessions) }
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
                    updatedAt = (session["started_at"] ?: session["updated_at"])
                        ?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() }
                        ?.let(::normalizeEpochMillis) ?: System.currentTimeMillis(),
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
                // Hermes `session.resume` (param: session_id) MINTS A NEW live
                // session id bound to the old transcript and returns it as
                // `session_id` (the original db id comes back as `resumed`).
                // The response also inlines the full transcript:
                //   { session_id, resumed, message_count, messages: [...] }
                // We MUST adopt the returned `session_id` as the active session —
                // sending prompt.submit with the old id fails "session not found".
                val params = buildJsonObject { put("session_id", sessionId) }
                val result = gatewayClient.request(GatewayMethods.SESSION_RESUME, jsonToElementMap(params))
                activeAssistantMessageId = null
                resetStreamingBuffer()
                val liveSessionId = (result as? JsonObject)
                    ?.get("session_id")?.let { (it as? JsonPrimitive)?.content }
                    ?.takeIf { it.isNotBlank() } ?: sessionId
                val history = parseSessionHistory(result)
                _uiState.update { it.copy(
                    activeSessionId = liveSessionId,
                    messages = history,
                    showSessionDrawer = false,
                    errorEvent = null,
                    sessionLoadedAt = System.currentTimeMillis(),
                    activeTodos = emptyList(),
                    pendingApproval = null,
                ) }
                if (history.isNotEmpty()) {
                    Timber.i("[Chat] Resumed $sessionId as live session $liveSessionId with ${history.size} messages")
                } else {
                    // Fallback: lazy/live resume paths may not inline the
                    // transcript — fetch it explicitly via session.history using
                    // the live id.
                    Timber.w("[Chat] Resume returned no inline messages, falling back to session.history for $liveSessionId")
                    loadSessionHistory(liveSessionId)
                }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to resume session")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Failed to resume: ${e.message}")) }
            }
        }
    }

    private suspend fun loadSessionHistory(sessionId: String) {
        try {
            // Hermes `session.history` resolves the session via _sess_nowait,
            // which reads params["session_id"] — NOT "id".
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            val messages = parseSessionHistory(result)
            if (messages.isNotEmpty()) {
                _uiState.update { it.copy(
                    messages = messages,
                    sessionLoadedAt = System.currentTimeMillis(),
                ) }
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
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachments = _uiState.value.pendingAttachments
        if (text.isEmpty() && attachments.isEmpty()) return
        val sessionId = _uiState.value.activeSessionId ?: return

        // Feature #23: Clear draft when message is sent
        clearDraft()

        // Wire format vs UI: the gateway's file.attach protocol requires the
        // @file: ref inside the submitted prompt text (that's how the agent
        // learns about the file). That is a transport detail — the chat bubble
        // shows ONLY what the user typed; attachments render as separate
        // elements from ChatMessage.User.attachments. Images need no ref at
        // all: they were queued gateway-side and ride along automatically.
        val refs = attachments.mapNotNull { it.refText }
        val outgoing = when {
            refs.isEmpty() -> text.ifEmpty { attachments.joinToString("\n") { "[User attached image: ${it.name}]" } }
            else -> (text + "\n" + refs.joinToString("\n")).trim()
        }

        // Add user message to UI immediately
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
            // New turn — drop the previous turn's task list (matches
            // upstream turnController, whose turn state resets per turn)
            activeTodos = emptyList(),
        ) }

        // Check for slash commands
        if (text.startsWith("/")) {
            handleSlashCommand(text, sessionId)
        } else {
            sendPrompt(outgoing, sessionId)
        }
    }

    // ── Attachments — files/images travel over the loopback gateway only ──
    // (Termux keeps its sandbox: no shared-storage permission is involved.)

    /** Max upload size; matches the gateway's image.attach_bytes cap (25 MB). */
    private val maxAttachBytes = 25 * 1024 * 1024

    /** Chunk size for streaming Base64 encoding (1 MB raw = ~1.33 MB b64). */
    private val attachChunkSize = 1024 * 1024

    /**
     * Upload a user-picked file to the gateway session.
     *
     * Images → `image.attach_bytes` (queued for native vision on the next
     * prompt). Everything else → `file.attach` (staged in the workspace,
     * referenced from the prompt via the returned `@file:` token).
     *
     * Uses chunked Base64 encoding to avoid loading the entire file +
     * its b64 representation in RAM simultaneously (a 25 MB file would
     * otherwise spike ~91 MB). Peak memory is now ~2.7 MB.
     */
    fun attachFromUri(uri: Uri) {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isAttaching) return
        _uiState.update { it.copy(isAttaching = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = resolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                } ?: uri.lastPathSegment ?: "attachment"
                val mime = resolver.getType(uri) ?: "application/octet-stream"

                // Stream the file in chunks and build Base64 incrementally.
                // Peak RAM: one chunk (1 MB raw) + its b64 (1.33 MB) + the
                // accumulated b64 StringBuilder.  For 25 MB files the final
                // b64 string is ~33 MB but we never hold the raw bytes AND
                // the b64 string at the same time.
                val b64 = StringBuilder()
                var totalSize = 0
                resolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(attachChunkSize)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        totalSize += read
                        if (totalSize > maxAttachBytes) {
                            throw IllegalStateException("File too large (max 25 MB)")
                        }
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        b64.append(Base64.encodeToString(chunk, Base64.NO_WRAP))
                    }
                } ?: throw IllegalStateException("Cannot read file")

                if (totalSize == 0) {
                    throw IllegalStateException("File is empty")
                }

                val attachment = if (mime.startsWith("image/")) {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("content_base64", b64.toString())
                        put("filename", name)
                    }
                    val result = gatewayClient.request("image.attach_bytes", jsonToElementMap(params))
                    val path = ((result as? JsonObject)?.get("path") as? JsonPrimitive)?.content
                    PendingAttachment(name = name, isImage = true, gatewayPath = path, localUri = uri.toString())
                } else {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("data_url", "data:$mime;base64,${b64}")
                        put("name", name)
                    }
                    val result = gatewayClient.request("file.attach", jsonToElementMap(params))
                    val ref = ((result as? JsonObject)?.get("ref_text") as? JsonPrimitive)?.content
                        ?: throw IllegalStateException("Gateway returned no file reference")
                    PendingAttachment(name = name, isImage = false, refText = ref, localUri = uri.toString())
                }
                _uiState.update { it.copy(
                    pendingAttachments = _uiState.value.pendingAttachments + attachment,
                    isAttaching = false,
                ) }
                Timber.i("[Chat] Attached ${attachment.name} (image=${attachment.isImage}, size=${totalSize})")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Attach failed")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Attach failed: ${e.message}"),
                    isAttaching = false,
                ) }
            }
        }
    }

    /**
     * Download a gateway file/artifact (image, video, file-ref) and save it
     * into the public Downloads/Hermes folder.
     *
     * Deliberately does NOT use the system DownloadManager. DownloadManager
     * runs as a separate OS process with its own network stack — it doesn't
     * necessarily honor this app's cleartext-traffic manifest policy (a
     * self-hosted gateway is commonly plain http:// on a LAN/VPN), and any
     * failure (cert, cleartext, or otherwise) happens silently with no
     * exception the app can surface, which is exactly what "I tap download
     * and nothing happens" looks like. Fetching through GatewayClient reuses
     * the same HTTP client that's already proven to reach this host (the
     * chat connection itself), and any failure here becomes a real,
     * visible error message.
     */
    fun downloadFile(url: String, filename: String) {
        viewModelScope.launch {
            val derivedName = filename.ifBlank {
                url.substringAfterLast('/').substringBefore('?')
            }
            val safeName = derivedName.ifBlank { "hermes_file" }
                .let { name -> name.filter { it != '/' && it != '\\' } }
                .ifBlank { "hermes_file" }
                .let { if (!it.contains('.')) "$it.jpg" else it }
            try {
                val bytes = gatewayClient.downloadFile(url)
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Hermes")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create download entry")
                resolver.openOutputStream(itemUri)?.use { out -> out.write(bytes) }
                    ?: error("Could not open output stream")
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                Timber.i("[Chat] Downloaded $safeName (${bytes.size} bytes) -> Downloads/Hermes")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("Saved to Downloads/Hermes: $safeName")) }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Download failed: $safeName")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Download failed: ${e.message}")) }
            }
        }
    }

    /** Remove a staged attachment (detaches queued images gateway-side too). */
    fun removeAttachment(attachment: PendingAttachment) {
        _uiState.update { it.copy(
            pendingAttachments = _uiState.value.pendingAttachments - attachment,
        ) }
        val sessionId = _uiState.value.activeSessionId ?: return
        if (attachment.isImage && attachment.gatewayPath != null) {
            viewModelScope.launch {
                try {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("path", attachment.gatewayPath)
                    }
                    gatewayClient.request("image.detach", jsonToElementMap(params))
                } catch (e: Exception) {
                    Timber.w(e, "[Chat] image.detach failed (ignored)")
                }
            }
        }
    }

    /**
     * Map a gateway-local image/file path (from `![..](..)` markdown) to an
     * HTTP URL the app can actually load. Agent-written files live inside
     * Termux's sandbox, which this app cannot read directly — but the
     * dashboard's `/api/files/download` endpoint streams them over loopback
     * and accepts the session token as a query param, so the resulting URL
     * works as-is for both Coil and DownloadManager.
     */
    fun resolveMediaUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("content://") || raw.startsWith("data:")
        ) return raw
        val path = if (raw.startsWith("file://")) raw.removePrefix("file://") else raw
        if (!path.startsWith("/") && !path.startsWith("~")) return raw
        val ws = hermesRuntime.getWebSocketUrl()
        val base = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")
            .substringBefore("/api/ws")
        val token = ws.substringAfter("token=", "").substringBefore('&')
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        // Note: gateway's /api/files/download only accepts token as a query
        // param (no header support), so we must include it in the URL.
        // OkHttp logging interceptor strips sensitive query params — see
        // GatewayModule.provideOkHttpClient() which redacts "token=".
        return buildString {
            append(base).append("/api/files/download?path=").append(encoded)
            if (token.isNotEmpty()) append("&token=").append(token)
        }
    }

    private fun sendPrompt(
        text: String,
        sessionId: String,
        truncateBeforeUserOrdinal: Int? = null,
    ) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("text", text)
                    put("session_id", sessionId)
                    // Fix F-A3: when retrying, pass truncate_before_user_ordinal
                    // so the server truncates history at the target user message
                    // before appending the new one. Without this, retry would
                    // duplicate the user message in server history.
                    // Verified in tui_gateway/server.py:7448,7467-7484.
                    if (truncateBeforeUserOrdinal != null) {
                        put("truncate_before_user_ordinal", truncateBeforeUserOrdinal)
                    }
                }
                gatewayClient.request(
                    method = GatewayMethods.PROMPT_SUBMIT,
                    params = jsonToElementMap(params),
                )
                // Server returns {"status": "streaming"} — actual content comes
                // via message.start / message.delta / message.complete events.
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Failed to send prompt")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Failed to send: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    /**
     * Fix: command.dispatch's response is a discriminated union on `type`
     * (gatewayTypes.ts `CommandDispatchResponse`) — exec/plugin/alias/skill/
     * send/prefill — and this only ever handled the exec/plugin shape
     * (generic key-matching over output/text/message/...). For commands that
     * come back as `alias` (re-route to another command), `send` (the
     * expansion must actually be SUBMITTED as a new prompt), or `prefill`
     * (the expansion belongs in the composer for the user to edit/send, not
     * displayed as inert text), the old code just printed a static status
     * line — which does nothing from the agent's point of view. That's almost
     * certainly why commands "didn't work": the app showed *something* but
     * never actually dispatched the resulting action.
     */
    private fun handleSlashCommand(text: String, sessionId: String, depth: Int = 0) {
        if (depth > 5) {
            // Guard against a misbehaving/looping alias chain.
            _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Command alias loop"), isSending = false) }
            return
        }
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
                val obj = result as? JsonObject
                when ((obj?.get("type") as? JsonPrimitive)?.content) {
                    "alias" -> {
                        // Re-route to the target command, preserving the arg.
                        val target = (obj["target"] as? JsonPrimitive)?.content
                        if (!target.isNullOrBlank()) {
                            val nextText = if (arg.isNotBlank()) "/$target $arg" else "/$target"
                            handleSlashCommand(nextText, sessionId, depth + 1)
                            return@launch
                        }
                    }
                    "send" -> {
                        // The command expanded into prompt text that must
                        // actually be submitted to the agent, not just shown.
                        val message = (obj["message"] as? JsonPrimitive)?.content
                        if (!message.isNullOrBlank()) {
                            sendPrompt(message, sessionId)
                            return@launch
                        }
                    }
                    "prefill" -> {
                        // The expansion goes in the composer for the user to
                        // review/edit before sending — not auto-sent.
                        val message = (obj["message"] as? JsonPrimitive)?.content
                        _uiState.update { it.copy(
                            inputText = message ?: _uiState.value.inputText,
                            isSending = false,
                        ) }
                        return@launch
                    }
                }
                // exec/plugin/skill (or an unrecognized/forward-compat shape):
                // surface whatever textual output the response carries.
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
                _uiState.update { it.copy(
                    messages = newMessages,
                    isSending = false,
                ) }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Slash command failed")
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Command failed: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    /**
     * Pull human-readable output from a `command.dispatch` response. Hermes
     * commands return varying shapes — a bare string, or an object with one of
     * `output`/`text`/`message`/`markdown`/`result`/`detail`, or a `lines`
     * array. We check the common keys and return null when the response is just
     * a status ack ({"status":"ok"}) with nothing worth showing.
     */
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

    /**
     * The gateway session that actually holds the live agent + history is not
     * always [ChatUiState.activeSessionId] (that comes from session.create and
     * can diverge from the running session). Session-scoped RPCs must target
     * the session `session.most_recent` reports, falling back to the local id.
     */
    private suspend fun resolveLiveSessionId(): String? {
        return try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            ((mr as? JsonObject)?.get("session_id") as? JsonPrimitive)?.content
        } catch (e: Exception) {
            null
        } ?: _uiState.value.activeSessionId
    }

    /**
     * Fork the current conversation into a new session and switch to it.
     * Triggered by long-pressing a message → "Branch conversation".
     * Backed by Hermes' `session.branch` (copies the current history into a
     * fresh session; returns the new session_id).
     */
    fun branchSession() {
        viewModelScope.launch {
            try {
                val sid = resolveLiveSessionId()
                if (sid == null) {
                    _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("No active conversation to branch")) }
                    return@launch
                }
                val result = gatewayClient.request(
                    GatewayMethods.SESSION_BRANCH,
                    jsonToElementMap(buildJsonObject { put("session_id", sid) }),
                )
                val newId = ((result as? JsonObject)?.get("session_id") as? JsonPrimitive)?.content
                loadSessionList()
                if (newId != null) {
                    resumeSession(newId)
                    _uiState.update { it.copy(errorEvent = ErrorEvent.Warning("Branched into a new conversation")) }
                }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] session.branch failed")
                val m = e.message.orEmpty()
                _uiState.update { it.copy(
                    errorEvent = if (m.contains("4008") || m.contains("nothing to branch"))
                        ErrorEvent.Warning("Send at least one message before branching")
                    else ErrorEvent.Error("Branch failed: $m"),
                ) }
            }
        }
    }

    fun stopGeneration() {
        val sessionId = _uiState.value.activeSessionId ?: return
        // Make the UI stop spinning immediately. The backend interrupt is
        // cooperative and can take a moment if a tool/model call is in-flight.
        //
        // The streaming assistant message must be finalized here too — the
        // server doesn't reliably send message.complete for an interrupted
        // turn, so without this the bubble stays isStreaming=true forever:
        // typing dots never stop, and the streaming spacer at the end of the
        // list keeps a permanent blank gap. finalizeOrphanedStreamingMessage
        // needs activeAssistantMessageId, so it runs before the null-out.
        finalizeOrphanedStreamingMessage(marker = "(stopped)")
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateAll({ msg ->
                msg is ChatMessage.ToolCall && msg.isRunning
            }) { msg ->
                (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Interrupted")
            },
            isSending = false,
        ) }
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

    /**
     * Redirect the agent mid-turn WITHOUT interrupting it (`session.steer`).
     *
     * This is the desktop/TUI's primary "course-correct" control and the main
     * thing the phone app was missing: while a turn is streaming, the only
     * option here used to be Stop (a full interrupt). Steer instead injects a
     * new instruction that the agent folds in at its next step, so you can nudge
     * it ("actually use TypeScript", "skip the tests") without losing the turn.
     *
     * Response shape (gatewayTypes.ts `SessionSteerResponse`):
     * `{status: "queued" | "rejected", text?: string}`.
     */
    fun steerAgent() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        // Echo the steer inline (arrow-prefixed) and clear the composer now.
        val steerMsg = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = "↳ $text",
        )
        _uiState.update { it.copy(
            messages = _uiState.value.messages + steerMsg,
            inputText = "",
        ) }
        clearDraft()
        viewModelScope.launch {
            // Steer must target the live running session, not the local id.
            val sessionId = resolveLiveSessionId()
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
                _uiState.update { it.copy(
                    errorEvent = ErrorEvent.Error("Steer failed: ${e.message}"),
                ) }
            }
        }
    }

    // ── Feature #5: Retry / Regenerate ───────────────────────────────────

    fun retryLastMessage() {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (_uiState.value.isSending) return

        // Find the last user message. Rebuild the same wire text sendMessage()
        // originally submitted: typed text + the @file: refs of any attachments
        // that rode along. Retrying with .text alone silently dropped the
        // files from the retried turn.
        val lastUserMsg = _uiState.value.messages.filterIsInstance<ChatMessage.User>().lastOrNull() ?: return
        val refs = lastUserMsg.attachments.mapNotNull { it.refText }
        val lastUserText = if (refs.isEmpty()) {
            lastUserMsg.text
        } else {
            (lastUserMsg.text + "\n" + refs.joinToString("\n")).trim()
        }

        // Fix F-A3: Compute the user-message ordinal (0-based index among
        // user messages only) of the last user message. The server's
        // prompt.submit handler accepts `truncate_before_user_ordinal` and
        // truncates history at history[:user_indices[ordinal]] BEFORE appending
        // the new text — so the user message is replaced, not duplicated.
        // Verified in tui_gateway/server.py:7448,7467-7484.
        val userMessages = _uiState.value.messages.filterIsInstance<ChatMessage.User>()
        val lastUserOrdinal = userMessages.size - 1  // 0-based index of last user msg

        // Remove the last assistant response (and any tool calls / status after it)
        val lastUserIndex = _uiState.value.messages.indexOfLast { it is ChatMessage.User }
        if (lastUserIndex >= 0) {
            // .toList() copies — subList returns a live view backed by the
            // original list, and stashing that view in immutable state is a
            // latent aliasing bug.
            val trimmedMessages = _uiState.value.messages.subList(0, lastUserIndex + 1).toList()
            _uiState.update { it.copy(
                messages = trimmedMessages,
                isSending = true,
            ) }
        }

        // Resend the prompt with truncation — server will drop history from
        // the target user message onward, then append the new text as a fresh
        // user message. Net effect on server: [user(msg1), assistant(old),
        // user(msg1)] becomes [user(msg1), assistant(old_truncated_away),
        // user(msg1_fresh)]. The retried turn runs against a clean history.
        sendPrompt(
            text = lastUserText,
            sessionId = sessionId,
            truncateBeforeUserOrdinal = lastUserOrdinal,
        )
    }

    // ── Feature #16: Search in current chat ──────────────────────────────

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

    // ── Feature #23: Save / Load draft ───────────────────────────────────

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

    // ── Client-side display name (top bar / drawer header) ───────────────

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

    // Avatar is customized from Settings (ConfigViewModel writes the same
    // prefs key) — re-read on every return to this screen so the change
    // shows up without needing a shared reactive store between ViewModels.
    fun loadAssistantAvatar() {
        val saved = prefs.getString(KEY_ASSISTANT_AVATAR, null)
        val path = if (!saved.isNullOrBlank() && java.io.File(saved).exists()) saved else null
        _uiState.update { it.copy(assistantAvatarPath = path) }
    }

    // Model switching lives in the Settings screen (ConfigViewModel), not in
    // chat — it must go through `config.set` with key="model" against the
    // active session, which Settings owns.

    // ── Event handling ────────────────────────────────────────────────────

    private fun handleEvent(event: GatewayEvent) {
        when (event) {
            is GatewayEvent.MessageStart -> {
                // Start a new assistant message (streaming). Use a unique
                // message id; sessionId is stable for the whole conversation
                // and would collide across multiple assistant turns.
                //
                // Fix F-A5 (regression): if a previous assistant message was
                // left in isStreaming=true (e.g. because reconnect succeeded
                // after a mid-stream drop and the server started a new turn),
                // finalize it now before assigning a new activeAssistantMessageId.
                // Otherwise the old message would orphan with spinner forever.
                finalizeOrphanedStreamingMessage(marker = "(interrupted)")
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + assistantMsg,
                ) }
            }

            is GatewayEvent.MessageDelta -> {
                enqueueStreamingDelta(event.text)
            }

            is GatewayEvent.MessageComplete -> {
                flushStreamingBuffer()
                // Finalize the assistant message
                _uiState.update { it.copy(
                    messages = _uiState.value.messages.updateFirst({ msg ->
                        msg is ChatMessage.Assistant && msg.isStreaming &&
                            (activeAssistantMessageId == null || msg.id == activeAssistantMessageId)
                    }) { msg ->
                        (msg as ChatMessage.Assistant).copy(
                            text = event.text.ifEmpty { msg.text },
                            isStreaming = false,
                            // Keep the reasoning accumulated from deltas when
                            // the complete event doesn't carry its own copy —
                            // overwriting with a null here erased the Thoughts
                            // block the moment the reply finished.
                            reasoning = event.reasoning?.takeIf { it.isNotBlank() } ?: msg.reasoning,
                        )
                    }.let { msgs ->
                        // Also finalize any running tool calls — ALL of them,
                        // not just the first match: the agent can run several
                        // tools in parallel, and finalizing only one left the
                        // rest spinning forever after the turn ended.
                        msgs.updateAll({ msg ->
                            msg is ChatMessage.ToolCall && msg.isRunning
                        }) { msg ->
                            (msg as ChatMessage.ToolCall).copy(isRunning = false, resultText = msg.resultText ?: "Completed")
                        }
                    },
                    isSending = false,
                    // Turn is over — drop the live plan so a stale list can't
                    // reappear at the start of the next turn (the strip is
                    // only visible while a turn is active anyway).
                    activeTodos = emptyList(),
                ) }
                activeAssistantMessageId = null
                resetStreamingBuffer()
            }

            is GatewayEvent.ThinkingDelta -> {
                // Reasoning tokens arrive just as fast as text tokens, so they
                // go through the same 80ms flush buffer — updating state per
                // token here re-rendered the whole message list on every
                // reasoning token, the exact per-token recomposition storm the
                // text path was already protected against.
                if (activeAssistantMessageId != null) {
                    enqueueStreamingDelta(event.text, isReasoning = true)
                }
            }

            is GatewayEvent.ToolStart -> {
                // Defense in depth: id is server-assigned (event.toolId), not a
                // locally generated UUID, so a duplicate delivery of the same
                // event (e.g. a stray replay after a reconnect) would append a
                // second message with an id already in the list. Compose's
                // LazyColumn (keyed by message id) treats a repeated key as
                // fatal and crashes the whole screen. Update in place instead
                // of appending when the id already exists.
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
                if (isRateLimit) {
                    val statusMsg = ChatMessage.Status(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        text = "⏸ Rate limited — please wait a moment",
                        isError = false,
                    )
                    _uiState.update { it.copy(
                        messages = _uiState.value.messages + statusMsg,
                    ) }
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + statusMsg,
                ) }
            }

            is GatewayEvent.ApprovalRequest -> {
                // Step 7: notification (for when the app is backgrounded) +
                // modal approval sheet in the chat (pendingApproval). The
                // transcript keeps a status line as the permanent record of
                // the request.
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
                    text = "🔐 Approval needed: ${event.description}\nCommand: ${event.command}",
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + msg,
                ) }
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + msg,
                ) }
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + msg,
                ) }
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
                        _uiState.update { it.copy(
                            messages = _uiState.value.messages + msg,
                        ) }
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

            is GatewayEvent.ReasoningDelta -> {
                // Same buffering as ThinkingDelta — see the comment there.
                if (activeAssistantMessageId != null) {
                    enqueueStreamingDelta(event.text, isReasoning = true)
                }
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
                _uiState.update { it.copy(
                    messages = _uiState.value.messages + msg,
                ) }
            }

            is GatewayEvent.SessionInfo -> {
                // Pushed after things like a session-scoped reasoning change
                // (config.set key="reasoning") — reflects the LIVE agent's
                // actual current effort (session override included), so this
                // is the authoritative source for what the chat's control
                // should show, not our own optimistic local copy.
                // Server distinguishes "" (unset/provider default) from the
                // explicit "none" (reasoning disabled) — collapsing them
                // would make the control lie about state right after a
                // fresh session, before any override has been set. Only
                // update on a concrete value; leave the existing display
                // (config.yaml default) alone otherwise.
                (event.info["reasoning_effort"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { effort -> _uiState.update { it.copy(reasoningLevel = effort) } }
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

    private fun enqueueStreamingDelta(text: String, isReasoning: Boolean = false) {
        if (text.isEmpty()) return
        (if (isReasoning) reasoningBuffer else streamingBuffer).append(text)
        if (streamingFlushJob?.isActive == true) return
        streamingFlushJob = viewModelScope.launch {
            delay(STREAM_FLUSH_INTERVAL_MS)
            flushStreamingBuffer()
            streamingFlushJob = null
        }
    }

    private fun flushStreamingBuffer() {
        if (streamingBuffer.isEmpty() && reasoningBuffer.isEmpty()) return
        val chunk = streamingBuffer.toString()
        streamingBuffer.setLength(0)
        val reasoningChunk = reasoningBuffer.toString()
        reasoningBuffer.setLength(0)
        val targetId = activeAssistantMessageId
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateFirst({ msg ->
                msg is ChatMessage.Assistant && msg.isStreaming &&
                    (targetId == null || msg.id == targetId)
            }) { msg ->
                (msg as ChatMessage.Assistant).copy(
                    text = msg.text + chunk,
                    reasoning = if (reasoningChunk.isEmpty()) msg.reasoning
                        else (msg.reasoning ?: "") + reasoningChunk,
                )
            }
        ) }
    }

    private fun resetStreamingBuffer() {
        streamingFlushJob?.cancel()
        streamingFlushJob = null
        streamingBuffer.setLength(0)
        reasoningBuffer.setLength(0)
    }

    /**
     * Finalize any assistant message left in `isStreaming=true` state.
     *
     * Called when the WebSocket disconnects mid-stream. Without this, the
     * spinner would spin forever and the user couldn't tell the message was
     * incomplete. We:
     *   1. Flush any buffered deltas to the message text (don't lose partial output)
     *   2. Mark the message as `isStreaming = false`
     *   3. Append a small marker so the user sees the turn was interrupted
     *   4. Clear `activeAssistantMessageId` so a fresh MessageStart doesn't
     *      accidentally target this orphaned message
     *   5. Reset `isSending` so the input bar is interactive again
     *
     * Fix for F-A5: orphaned streaming message on disconnect.
     */
    private fun finalizeOrphanedStreamingMessage(marker: String) {
        // Flush any buffered text first so the user sees what was streamed
        // before the disconnect.
        flushStreamingBuffer()

        val orphanedId = activeAssistantMessageId ?: return
        var found = false
        _uiState.update { it.copy(
            messages = _uiState.value.messages.updateFirst({ msg ->
                msg is ChatMessage.Assistant && msg.isStreaming && msg.id == orphanedId
            }) { msg ->
                found = true
                (msg as ChatMessage.Assistant).copy(
                    isStreaming = false,
                    text = if (msg.text.isBlank()) marker else "${msg.text}\n\n$marker",
                )
            },
            isSending = false,
        ) }
        if (found) {
            Timber.w("[Chat] Finalized orphaned streaming message $orphanedId with marker: $marker")
        }
        activeAssistantMessageId = null
        resetStreamingBuffer()
    }

    // ── Drawer: search / sort / pin / rename / delete ─────────────────────

    fun updateDrawerSearch(query: String) {
        _uiState.update { it.copy(drawerSearchQuery = query) }
    }

    fun toggleDrawerSort() {
        _uiState.update { it.copy(drawerSortNewest = !_uiState.value.drawerSortNewest) }
    }

    fun drawerTogglePin(sessionId: String) {
        val pins = _uiState.value.drawerPinnedIds
        _uiState.update { it.copy(
            drawerPinnedIds = if (sessionId in pins) pins - sessionId else pins + sessionId,
        ) }
    }

    fun drawerShowRename(sessionId: String, currentTitle: String) {
        _uiState.update { it.copy(
            drawerRenameTarget = DrawerRenameState(sessionId, currentTitle),
        ) }
    }

    fun drawerUpdateRenameText(text: String) {
        _uiState.update { it.copy(
            drawerRenameTarget = _uiState.value.drawerRenameTarget?.copy(inputText = text),
        ) }
    }

    fun drawerHideRename() {
        _uiState.update { it.copy(drawerRenameTarget = null) }
    }

    fun drawerConfirmRename() {
        val target = _uiState.value.drawerRenameTarget ?: return
        val newTitle = target.inputText.trim().ifEmpty { return }
        _uiState.update { it.copy(drawerRenameTarget = null) }
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("session_id", target.sessionId)
                    put("title", newTitle)
                }
                gatewayClient.request(GatewayMethods.SESSION_TITLE, jsonToElementMap(params))
                Timber.i("[Chat] Renamed ${target.sessionId} → $newTitle")
                loadSessionList()
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Rename failed")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Rename failed: ${e.message}")) }
            }
        }
    }

    fun drawerShowDelete(sessionId: String) {
        _uiState.update { it.copy(drawerDeleteTarget = sessionId) }
    }

    fun drawerHideDelete() {
        _uiState.update { it.copy(drawerDeleteTarget = null) }
    }

    fun drawerConfirmDelete() {
        val sessionId = _uiState.value.drawerDeleteTarget ?: return
        _uiState.update { it.copy(drawerDeleteTarget = null) }
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                gatewayClient.request(GatewayMethods.SESSION_DELETE, jsonToElementMap(params))
                Timber.i("[Chat] Deleted $sessionId")
                if (_uiState.value.activeSessionId == sessionId) {
                    _uiState.update { it.copy(
                        activeSessionId = null,
                        messages = emptyList(),
                    ) }
                    createSession()
                }
                loadSessionList()
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Delete failed")
                _uiState.update { it.copy(errorEvent = ErrorEvent.Error("Delete failed: ${e.message}")) }
            }
        }
    }

    // ── UI actions ────────────────────────────────────────────────────────

    fun toggleSessionDrawer() {
        val opening = !_uiState.value.showSessionDrawer
        _uiState.update { it.copy(showSessionDrawer = opening) }
        if (opening) loadSessionList()
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = false) }
    }

    fun newConversation() {
        viewModelScope.launch {
            activeAssistantMessageId = null
            resetStreamingBuffer()
            _uiState.update { it.copy(
                messages = emptyList(),
                showSessionDrawer = false,
                activeSessionId = null,
                activeTodos = emptyList(),
                pendingApproval = null,
            ) }
            createSession()
        }
    }

    fun clearErrorEvent() {
        _uiState.update { it.copy(errorEvent = null) }
    }

    // ── Interactive responds ──────────────────────────────────────────────

    /**
     * Answer the pending tool-approval request (the modal sheet). [choice]
     * uses the canonical upstream values: "once" | "always" | "deny"
     * (see `tools/approval.py` — same contract as the notification buttons
     * in ApprovalActionReceiver). approval.respond is per-session: the
     * gateway holds a single pending approval, so no request_id is sent.
     */
    fun respondToApproval(choice: String) {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        // The notification mirrors the same request — dismiss it so the user
        // can't answer twice.
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun jsonToElementMap(obj: kotlinx.serialization.json.JsonObject):
        Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()

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

    private companion object {
        private const val STREAM_FLUSH_INTERVAL_MS = 80L
        private const val PREFS_NAME = "hermes_chat_prefs"
        private const val KEY_DRAFT = "draft_message"
        private const val KEY_ASSISTANT_NAME = "assistant_display_name"
        private const val KEY_ASSISTANT_AVATAR = "assistant_avatar_path"
    }

    /**
     * Replace a single item in a list by index — O(1) replacement,
     * O(n) copy (unavoidable with immutable lists), but avoids the
     * O(n) predicate scan of [List.map] when the target index is known.
     */
    private inline fun <T> List<T>.updateAt(index: Int, transform: (T) -> T): List<T> {
        val mutable = toMutableList()
        mutable[index] = transform(mutable[index])
        return mutable.toList()
    }

    /**
     * Find the first item matching [predicate] and replace it — O(n) scan
     * once + O(1) copy at the found index. Returns the same list if no
     * match is found (avoids a new allocation).
     */
    private inline fun <T> List<T>.updateFirst(predicate: (T) -> Boolean, transform: (T) -> T): List<T> {
        val idx = indexOfFirst(predicate)
        if (idx == -1) return this
        return updateAt(idx, transform)
    }

    /**
     * Replace EVERY item matching [predicate] — for cleanup passes where any
     * number of items can be in the target state (e.g. several tool calls
     * running in parallel when the turn ends). Returns the same list when
     * nothing matches.
     */
    private inline fun <T> List<T>.updateAll(predicate: (T) -> Boolean, transform: (T) -> T): List<T> {
        if (none(predicate)) return this
        return map { if (predicate(it)) transform(it) else it }
    }

    override fun onCleared() {
        super.onCleared()
        eventCollectionJob?.cancel()
        connectionWatchJob?.cancel()
        resetStreamingBuffer()
        // Feature #23: Save draft when ViewModel is cleared
        saveDraft()
        // Note: we intentionally do NOT call gatewayClient.disconnect() here.
        // GatewayClient is a process-scoped @Singleton (GatewayModule.kt:31)
        // shared with HermesGatewayService (foreground service) and other
        // ViewModels. The ViewModel should not tear down a connection it
        // doesn't own. The previous `viewModelScope.launch { disconnect() }`
        // call here was also dead code: viewModelScope is already cancelled
        // by the time onCleared() runs, so the coroutine never executed.
    }
}
