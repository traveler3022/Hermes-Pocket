package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * Delegation v1 — the "task desk" (میز کار).
 *
 * A task is nothing exotic: it's an ordinary live gateway session, created
 * with a title and fired with one prompt, that keeps running server-side
 * whether or not this phone is watching. That deliberately reuses the whole
 * existing session machinery (persistence in state.db, resume into ChatScreen,
 * interrupt, approval flow) instead of the ephemeral `prompt.background`
 * thread whose result exists only as a single WS event — on mobile, where the
 * socket comes and goes, results must live in the session store.
 *
 * The dashboard is `session.active_list` (live in-memory sessions only; a
 * finished task stays listed as "idle" with its transcript until closed or
 * reaped, and remains in Sessions history after that).
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    data class TaskItem(
        val id: String,
        val title: String,
        /** Server live status: e.g. "streaming" (running) or "idle". */
        val status: String,
        val preview: String,
        val model: String,
        val messageCount: Int,
        val lastActive: Double,
        val current: Boolean,
    ) {
        val isRunning: Boolean get() = status == "streaming" || status == "running"
    }

    data class TasksUiState(
        val tasks: List<TaskItem> = emptyList(),
        val isLoading: Boolean = false,
        val isLaunching: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Refresh the board the moment any turn completes so a task flips
        // from "running" to "done" without waiting for the next poll tick.
        viewModelScope.launch {
            gatewayClient.events.collect { event ->
                if (event is GatewayEvent.MessageComplete ||
                    event is GatewayEvent.BackgroundComplete
                ) {
                    refresh(showSpinner = false)
                }
            }
        }
    }

    /** Poll while the screen is visible; live statuses go stale otherwise. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            refresh(showSpinner = true)
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh(showSpinner = false)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh(showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner) _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = gatewayClient.request(GatewayMethods.SESSION_ACTIVE_LIST)
                val rows = (result as? JsonObject)?.get("sessions") as? JsonArray
                val tasks = rows?.mapNotNull { parseTask(it) } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    tasks = tasks.sortedByDescending { it.lastActive },
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Tasks] active_list failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * Fire-and-forget: create a titled session and submit the prompt. The
     * server runs the turn regardless of what this client does next; the
     * response streams into the session store and the proactive notifier
     * (AgentEventObserver) surfaces completion if the app is backgrounded.
     */
    fun launchTask(title: String, prompt: String, onLaunched: (String) -> Unit = {}) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLaunching = true)
            try {
                val createParams = buildJsonObject {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isNotEmpty()) put("title", cleanTitle)
                    put("source", "pocket_task")
                }
                val created = gatewayClient.request(
                    GatewayMethods.SESSION_CREATE,
                    createParams.toElementMap(),
                    // A task session must never become the chat's auto-resume
                    // target — see GatewayClient.request's trackSession doc.
                    trackSession = false,
                )
                val sessionId = (created as? JsonObject)?.get("session_id")
                    ?.let { (it as? JsonPrimitive)?.content }
                    ?: throw IllegalStateException("session.create returned no session_id")

                gatewayClient.request(
                    GatewayMethods.PROMPT_SUBMIT,
                    buildJsonObject {
                        put("text", cleanPrompt)
                        put("session_id", sessionId)
                    }.toElementMap(),
                )
                Timber.i("[Tasks] launched task '$title' as session $sessionId")
                _uiState.value = _uiState.value.copy(isLaunching = false)
                refresh(showSpinner = false)
                onLaunched(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "[Tasks] launch failed")
                _uiState.value = _uiState.value.copy(
                    isLaunching = false,
                    error = e.message,
                )
            }
        }
    }

    fun interrupt(sessionId: String) {
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    GatewayMethods.SESSION_INTERRUPT,
                    buildJsonObject { put("session_id", sessionId) }.toElementMap(),
                )
                refresh(showSpinner = false)
            } catch (e: Exception) {
                Timber.e(e, "[Tasks] interrupt failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Tears the live worker down; the transcript stays in Sessions history. */
    fun close(sessionId: String) {
        viewModelScope.launch {
            try {
                gatewayClient.request(
                    GatewayMethods.SESSION_CLOSE,
                    buildJsonObject { put("session_id", sessionId) }.toElementMap(),
                )
                refresh(showSpinner = false)
            } catch (e: Exception) {
                Timber.e(e, "[Tasks] close failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun parseTask(element: JsonElement): TaskItem? {
        val obj = element as? JsonObject ?: return null
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.content ?: ""
        val id = str("id").ifEmpty { return null }
        return TaskItem(
            id = id,
            title = str("title").ifEmpty { str("session_key").ifEmpty { id } },
            status = str("status"),
            preview = str("preview"),
            model = str("model"),
            messageCount = str("message_count").toIntOrNull() ?: 0,
            lastActive = str("last_active").toDoubleOrNull() ?: 0.0,
            current = str("current").toBooleanStrictOrNull() ?: false,
        )
    }

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L
    }
}
