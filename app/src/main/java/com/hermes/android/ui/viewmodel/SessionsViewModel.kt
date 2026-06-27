package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Sessions & Memory screen.
 *
 * Loads session list, session history, and memory files (USER.md, MEMORY.md)
 * via gateway RPCs.
 *
 * Reference: Phase 1.5 Rule 1, Rule 2
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val _memoryState = MutableStateFlow(MemoryUiState())
    val memoryState: StateFlow<MemoryUiState> = _memoryState.asStateFlow()

    init {
        loadSessions()
        loadMemory()
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
                        ?: 0L,
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
            _uiState.value = _uiState.value.copy(isLoadingHistory = true)
            try {
                val params = buildJsonObject {
                    put("session_id", sessionId)
                }
                val result = gatewayClient.request(
                    GatewayMethods.SESSION_HISTORY,
                    params.toMap(),
                )
                val messages = parseHistory(result)
                _uiState.value = _uiState.value.copy(
                    selectedSessionHistory = messages,
                    selectedSessionId = sessionId,
                    isLoadingHistory = false,
                )
                Timber.i("[Sessions] History loaded: ${messages.size} messages")
            } catch (e: Exception) {
                Timber.e(e, "[Sessions] Failed to load history")
                _uiState.value = _uiState.value.copy(isLoadingHistory = false)
            }
        }
    }

    private fun parseHistory(result: kotlinx.serialization.json.JsonElement): List<HistoryMessage> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            // Fix S9F02: response is {count, messages: [{role, text}]} — field is "text" not "content"
            val arr = obj["messages"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val m = item as? JsonObject ?: return@mapNotNull null
                HistoryMessage(
                    role = m["role"]?.let { (it as? JsonPrimitive)?.content } ?: "unknown",
                    content = m["text"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    timestamp = 0L, // Hermes doesn't include timestamp in history
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

// ── UI State models ──────────────────────────────────────────────────────

data class SessionsUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    val selectedSessionHistory: List<HistoryMessage> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val errorMessage: String? = null,
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
