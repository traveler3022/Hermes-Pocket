package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.SessionRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Delegation — the "task desk" (میز کار).
 *
 * A task is an ordinary live gateway session, created with a title and fired
 * with one prompt, that keeps running server-side whether or not this phone
 * is watching. All session-protocol semantics (id kinds, task identity,
 * launch mechanics) live in [SessionRepository] — this ViewModel only holds
 * screen state (Milestone A, پیمان ۵).
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: SessionRepository,
    gatewayClient: GatewayClient,
) : ViewModel() {

    data class TasksUiState(
        val tasks: List<SessionRepository.TaskRow> = emptyList(),
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
                _uiState.value = _uiState.value.copy(
                    tasks = repository.activeTasks(),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Tasks] refresh failed")
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun launchTask(title: String, prompt: String, onLaunched: (String) -> Unit = {}) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLaunching = true)
            try {
                val liveId = repository.launchTask(title, prompt)
                _uiState.value = _uiState.value.copy(isLaunching = false)
                refresh(showSpinner = false)
                onLaunched(liveId)
            } catch (e: Exception) {
                Timber.e(e, "[Tasks] launch failed")
                _uiState.value = _uiState.value.copy(isLaunching = false, error = e.message)
            }
        }
    }

    fun interrupt(sessionId: String) {
        viewModelScope.launch {
            try {
                repository.interrupt(sessionId)
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
                repository.closeSession(sessionId)
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

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L
    }
}
