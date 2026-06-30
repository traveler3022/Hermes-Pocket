package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject

/**
 * ViewModel for the Sessions & Memory screen.
 *
 * Loads session list, session history, and memory files (USER.md, MEMORY.md)
 * via gateway RPCs.
 *
 * Reference: Phase 1.5 Rule 1, Rule 2
 */
/** One-shot side-effects emitted by [SessionsViewModel] for the UI to handle. */
sealed interface SessionsEffect {
    data class ShareText(val text: String, val title: String) : SessionsEffect
}

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val _memoryState = MutableStateFlow(MemoryUiState())
    val memoryState: StateFlow<MemoryUiState> = _memoryState.asStateFlow()

    private val _effects = MutableSharedFlow<SessionsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SessionsEffect> = _effects.asSharedFlow()

    init {
        loadSessions()
        loadMemory()
        loadAgents()
        loadInsights()
    }

    // ── Sessions ──────────────────────────────────────────────────────────

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSessions = true)
            try {
                val result = gatewayClient.request(GatewayMethods.SESSION_LIST)
                val sessions = parseSessionList(result)
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoadingSessions = false,
                )
                Timber.i("[Sessions] Loaded ${sessions.size} sessions")
            } catch (e: GatewayException) {
                Timber.e(e, "[Sessions] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoadingSessions = false,
                    errorMessage = "Failed to load sessions: ${e.message}",
                )
            }
        }
    }

    private fun parseSessionList(result: kotlinx.serialization.json.JsonElement): List<SessionSummary> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["sessions"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val s = item as? JsonObject ?: return@mapNotNull null
                SessionSummary(
                    id = s["id"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    title = s["title"]?.let { (it as? JsonPrimitive)?.content }
                        ?: "Untitled",
                    // Fix S9F01: field is "preview" not "last_message"
                    lastMessagePreview = s["preview"]?.let { (it as? JsonPrimitive)?.content },
                    // Fix S9F01: field is "started_at" not "updated_at"
                    updatedAt = s["started_at"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                        ?.let(::normalizeEpochMillis) ?: 0L,
                    messageCount = s["message_count"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                        ?: 0,
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "[Sessions] Parse error")
            emptyList()
        }
    }

    fun loadSessionHistory(sessionId: String) {
        viewModelScope.launch {
            // Open detail view immediately so the user sees the loading state
            _uiState.value = _uiState.value.copy(
                isLoadingHistory = true,
                selectedSessionId = sessionId,
                selectedSessionHistory = emptyList(),
                selectedSessionUsage = null,
            )
            // Fetch token usage for the opened session in parallel.
            loadUsage(sessionId)
            val messages = try {
                // Hermes session.history resolves via _sess_nowait → params["session_id"].
                val params = buildJsonObject { put("session_id", sessionId) }
                val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, params.toMap())
                parseHistory(result).also { msgs ->
                    Timber.i("[Sessions] session.history returned ${msgs.size} messages for $sessionId")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Sessions] session.history RPC failed — trying filesystem fallback")
                loadHistoryFromFilesystem(sessionId)
            }

            _uiState.value = _uiState.value.copy(
                selectedSessionHistory = messages,
                isLoadingHistory = false,
                errorMessage = if (messages.isEmpty()) "پیامی برای این گفتگو پیدا نشد" else null,
            )
        }
    }

    private suspend fun loadHistoryFromFilesystem(sessionId: String): List<HistoryMessage> {
        return try {
            val payload = Base64.getEncoder().encodeToString(sessionId.toByteArray(Charsets.UTF_8))
            val command = """
                python3 - <<'PY'
                import base64, json
                from pathlib import Path

                session_id = base64.b64decode('$payload').decode('utf-8')
                base = Path.home() / '.hermes'
                messages = []

                for dir_name in ['sessions', 'conversations', 'chats']:
                    sdir = base / dir_name
                    if not sdir.exists():
                        continue
                    for entry in sorted(sdir.iterdir()):
                        if session_id not in entry.name:
                            continue
                        paths_to_try = []
                        if entry.is_dir():
                            for fname in ['messages.jsonl', 'conversation.jsonl', 'messages.json', 'history.json']:
                                paths_to_try.append(entry / fname)
                        else:
                            paths_to_try.append(entry)
                        for path in paths_to_try:
                            if not path.exists():
                                continue
                            try:
                                for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
                                    line = line.strip()
                                    if not line:
                                        continue
                                    try:
                                        obj = json.loads(line)
                                        role = obj.get('role', '')
                                        if role in ('user', 'assistant', 'human', 'ai'):
                                            role = 'user' if role in ('user', 'human') else 'assistant'
                                            content = obj.get('content', obj.get('text', ''))
                                            if isinstance(content, list):
                                                content = ' '.join(b.get('text', '') for b in content if isinstance(b, dict))
                                            messages.append({'role': role, 'text': str(content)})
                                    except Exception:
                                        pass
                            except Exception:
                                pass
                            if messages:
                                break
                        if messages:
                            break
                    if messages:
                        break

                print(json.dumps({'messages': messages}))
                PY
            """.trimIndent()
            val result = gatewayClient.request(
                GatewayMethods.SHELL_EXEC,
                mapOf("command" to JsonPrimitive(command)),
                timeoutMs = 10_000,
            )
            parseHistory(result).also { msgs ->
                Timber.i("[Sessions] Filesystem fallback found ${msgs.size} messages for $sessionId")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Sessions] Filesystem fallback failed")
            emptyList()
        }
    }

    private fun parseHistory(result: kotlinx.serialization.json.JsonElement): List<HistoryMessage> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            // Support both "messages" and "history" as the array key (Hermes may use either)
            val arr = obj["messages"] as? JsonArray
                ?: obj["history"] as? JsonArray
                ?: return emptyList()
            arr.mapNotNull { item ->
                val m = item as? JsonObject ?: return@mapNotNull null
                // Support both "text" (Hermes WS) and "content" (OpenAI-style)
                val content = m["text"]?.let { (it as? JsonPrimitive)?.content }
                    ?: m["content"]?.let { (it as? JsonPrimitive)?.content }
                    ?: ""
                HistoryMessage(
                    role = m["role"]?.let { (it as? JsonPrimitive)?.content } ?: "unknown",
                    content = content,
                    timestamp = 0L,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun closeHistory() {
        _uiState.value = _uiState.value.copy(
            selectedSessionId = null,
            selectedSessionHistory = emptyList(),
        )
    }

    // ── Delete with confirmation (#9) ─────────────────────────────────────

    /** Show confirmation dialog before deleting. */
    fun confirmDelete(sessionId: String) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = sessionId)
    }

    /** Cancel the pending delete. */
    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    /** Execute the confirmed delete. */
    fun executeDelete() {
        val sessionId = _uiState.value.showDeleteConfirm ?: return
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
        deleteSession(sessionId)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                gatewayClient.request(GatewayMethods.SESSION_DELETE, params.toMap())
                Timber.i("[Sessions] Deleted: $sessionId")
                loadSessions()
            } catch (e: Exception) {
                Timber.e(e, "[Sessions] Delete failed")
            }
        }
    }

    // ── Rename session (#6) ───────────────────────────────────────────────

    fun showRenameDialog(sessionId: String, currentTitle: String) {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = SessionRenameDialog(sessionId, currentTitle),
        )
    }

    fun hideRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = null)
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("session_id", sessionId)
                    put("title", newTitle)
                }
                gatewayClient.request(GatewayMethods.SESSION_TITLE, params.toMap())
                Timber.i("[Sessions] Renamed $sessionId to: $newTitle")
                hideRenameDialog()
                loadSessions()
            } catch (e: Exception) {
                Timber.e(e, "[Sessions] Rename failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to rename session: ${e.message}",
                )
            }
        }
    }

    // ── Search sessions (#17) ─────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // ── Pin session (#18) ─────────────────────────────────────────────────

    fun togglePin(sessionId: String) {
        val current = _uiState.value.pinnedSessionIds
        _uiState.value = _uiState.value.copy(
            pinnedSessionIds = if (sessionId in current) current - sessionId else current + sessionId,
        )
    }

    // ── Sort sessions (#19) ───────────────────────────────────────────────

    fun setSortOrder(order: SessionSortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
    }

    // ── Export / Share session (#20, #21) ──────────────────────────────────

    fun exportSession(sessionId: String) {
        shareOrExportSession(sessionId)
    }

    fun shareSession(sessionId: String) {
        shareOrExportSession(sessionId)
    }

    private fun shareOrExportSession(sessionId: String) {
        viewModelScope.launch {
            try {
                // Load history if not already loaded for this session
                val messages = if (_uiState.value.selectedSessionId == sessionId) {
                    _uiState.value.selectedSessionHistory
                } else {
                    val params = buildJsonObject { put("session_id", sessionId) }
                    val result = gatewayClient.request(
                        GatewayMethods.SESSION_HISTORY,
                        params.toMap(),
                    )
                    parseHistory(result)
                }

                // Find session title
                val session = _uiState.value.sessions.find { it.id == sessionId }
                val title = session?.title ?: "Untitled"

                // Format as Markdown
                val markdownText = buildString {
                    appendLine("# $title")
                    appendLine()
                    messages.forEach { msg ->
                        appendLine("**${msg.role}:**")
                        appendLine(msg.content)
                        appendLine()
                    }
                }

                _effects.emit(SessionsEffect.ShareText(markdownText, title))
                Timber.i("[Sessions] Exported/shared session: $sessionId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Sessions] Export/share failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to export session: ${e.message}",
                )
            }
        }
    }

    // ── Memory ────────────────────────────────────────────────────────────

    fun loadMemory() {
        // Fix S9F03: Use shell.exec RPC to read memory file content.
        // Hermes has NO dedicated RPC for reading USER.md/MEMORY.md content.
        // shell.exec is an official RPC (server.py:12366) that executes
        // commands and returns {stdout, stderr, code}.
        // Memory files are at: ~/.hermes/memories/USER.md and MEMORY.md
        // (tools/memory_tool.py:55-57: get_memory_dir() = get_hermes_home() / "memories")
        viewModelScope.launch {
            _memoryState.value = _memoryState.value.copy(isLoading = true)
            try {
                val userResult = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to kotlinx.serialization.json.JsonPrimitive("cat ~/.hermes/memories/USER.md 2>/dev/null || echo '(not found)'")),
                )
                val userMd = (userResult as? JsonObject)
                    ?.get("stdout")
                    ?.let { it as? JsonPrimitive }
                    ?.content ?: "(not found)"

                val memResult = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to kotlinx.serialization.json.JsonPrimitive("cat ~/.hermes/memories/MEMORY.md 2>/dev/null || echo '(not found)'")),
                )
                val memoryMd = (memResult as? JsonObject)
                    ?.get("stdout")
                    ?.let { it as? JsonPrimitive }
                    ?.content ?: "(not found)"

                _memoryState.value = _memoryState.value.copy(
                    userMd = userMd,
                    memoryMd = memoryMd,
                    isLoading = false,
                )
                Timber.i("[Memory] Loaded USER.md (${userMd.length} chars), MEMORY.md (${memoryMd.length} chars)")
            } catch (e: Exception) {
                Timber.w(e, "[Memory] Failed to load")
                _memoryState.value = _memoryState.value.copy(
                    userMd = "(failed to load)",
                    memoryMd = "(failed to load)",
                    isLoading = false,
                )
            }
        }
    }

    // ── Token usage / cost (session.usage) ────────────────────────────────

    /**
     * Fetch token usage for a session. Hermes `session.usage` (param session_id)
     * returns { calls, input, output, total, credits_lines? }. Surfaces the
     * user's main concern — how many tokens a session burned.
     */
    fun loadUsage(sessionId: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                val result = gatewayClient.request(GatewayMethods.SESSION_USAGE, params.toMap())
                val obj = result as? JsonObject
                fun longOf(k: String) = (obj?.get(k) as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                val credits = (obj?.get("credits_lines") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    selectedSessionUsage = SessionUsage(
                        calls = longOf("calls"),
                        input = longOf("input"),
                        output = longOf("output"),
                        total = longOf("total"),
                        creditsLines = credits,
                    ),
                )
                Timber.i("[Sessions] Usage for $sessionId: total=${longOf("total")} tokens")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Sessions] Failed to load usage")
                _uiState.value = _uiState.value.copy(selectedSessionUsage = null)
            }
        }
    }

    // ── Active agents (agents.list) ───────────────────────────────────────

    /**
     * List running agent/sub-agent processes. Hermes `agents.list` (no params)
     * returns { processes: [{session_id, command, status, uptime}] }.
     */
    fun loadAgents() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.AGENTS_LIST)
                val arr = (result as? JsonObject)?.get("processes") as? JsonArray ?: JsonArray(emptyList())
                val procs = arr.mapNotNull { item ->
                    val p = item as? JsonObject ?: return@mapNotNull null
                    AgentProcess(
                        sessionId = (p["session_id"] as? JsonPrimitive)?.content ?: "",
                        command = (p["command"] as? JsonPrimitive)?.content ?: "",
                        status = (p["status"] as? JsonPrimitive)?.content ?: "",
                        uptimeSeconds = (p["uptime"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                    )
                }
                _uiState.value = _uiState.value.copy(activeAgents = procs)
                Timber.i("[Sessions] ${procs.size} active agents")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Sessions] Failed to load agents")
                _uiState.value = _uiState.value.copy(activeAgents = emptyList())
            }
        }
    }

    // ── Usage insights (insights.get) ─────────────────────────────────────

    /**
     * Aggregate usage insights over a window. Hermes `insights.get` (param days)
     * returns { days, sessions, messages }.
     */
    fun loadInsights(days: Int = 30) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingInsights = true)
            try {
                val params = buildJsonObject { put("days", days) }
                val result = gatewayClient.request(GatewayMethods.INSIGHTS_GET, params.toMap())
                val obj = result as? JsonObject
                fun intOf(k: String) = (obj?.get(k) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                _uiState.value = _uiState.value.copy(
                    insights = InsightsData(
                        days = intOf("days").takeIf { it > 0 } ?: days,
                        sessions = intOf("sessions"),
                        messages = intOf("messages"),
                    ),
                    isLoadingInsights = false,
                )
                Timber.i("[Sessions] Insights ${days}d: ${intOf("sessions")} sessions, ${intOf("messages")} messages")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Sessions] Failed to load insights")
                _uiState.value = _uiState.value.copy(isLoadingInsights = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

}

// ── UI State models ──────────────────────────────────────────────────────

enum class SessionSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    NAME_AZ,
}

data class SessionRenameDialog(
    val sessionId: String,
    val currentTitle: String,
)

data class SessionsUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    val selectedSessionHistory: List<HistoryMessage> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val sortOrder: SessionSortOrder = SessionSortOrder.NEWEST_FIRST,
    val pinnedSessionIds: Set<String> = emptySet(),
    val showRenameDialog: SessionRenameDialog? = null,
    val showDeleteConfirm: String? = null,
    // Token usage for the currently-open session (session.usage)
    val selectedSessionUsage: SessionUsage? = null,
    // Running agent processes (agents.list)
    val activeAgents: List<AgentProcess> = emptyList(),
    // Aggregate usage insights (insights.get)
    val insights: InsightsData? = null,
    val isLoadingInsights: Boolean = false,
)

data class SessionUsage(
    val calls: Long,
    val input: Long,
    val output: Long,
    val total: Long,
    val creditsLines: List<String> = emptyList(),
)

data class AgentProcess(
    val sessionId: String,
    val command: String,
    val status: String,
    val uptimeSeconds: Long,
)

data class InsightsData(
    val days: Int,
    val sessions: Int,
    val messages: Int,
)

data class MemoryUiState(
    val userMd: String = "",
    val memoryMd: String = "",
    val isLoading: Boolean = false,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val lastMessagePreview: String?,
    val updatedAt: Long,
    val messageCount: Int,
)

data class HistoryMessage(
    val role: String,
    val content: String,
    val timestamp: Long,
)
