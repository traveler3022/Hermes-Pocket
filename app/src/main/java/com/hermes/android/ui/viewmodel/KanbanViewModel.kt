package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.KanbanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The server's Kanban board: one column at a time, a task's detail, and the moves a person makes. */
@HiltViewModel
class KanbanViewModel @Inject constructor(
    private val kanban: KanbanRepository,
) : ViewModel() {

    data class UiState(
        val available: Boolean = true,
        val columns: Map<String, List<KanbanRepository.Task>> = emptyMap(),
        val column: String = "ready",
        val isLoading: Boolean = true,
        val assignees: List<String> = emptyList(),
        val detail: KanbanRepository.Detail? = null,
        val isBusy: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState(available = kanban.available))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Re-reads the board while the screen is in front; workers move cards on their own. */
    suspend fun watch() {
        if (!kanban.available) return
        while (true) {
            refresh()
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val columns = kanban.board()
            _uiState.update { state ->
                // Open on the first column with work in it, until the user picks one.
                val column = if (state.isLoading) firstBusy(columns) ?: state.column else state.column
                state.copy(columns = columns, column = column, isLoading = false)
            }
            _uiState.value.detail?.let { open -> _uiState.update { it.copy(detail = kanban.task(open.task.id)) } }
        } catch (e: Exception) {
            Timber.w(e, "[Kanban] read failed")
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun selectColumn(column: String) = _uiState.update { it.copy(column = column) }

    fun loadAssignees() {
        viewModelScope.launch {
            runCatching { kanban.assignees() }
                .onSuccess { list -> _uiState.update { it.copy(assignees = list) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun open(task: KanbanRepository.Task) {
        viewModelScope.launch {
            runCatching { kanban.task(task.id) }
                .onSuccess { detail -> _uiState.update { it.copy(detail = detail) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun close() = _uiState.update { it.copy(detail = null) }

    fun create(title: String, body: String, assignee: String?, onDone: () -> Unit) = act(onDone) {
        kanban.create(title, body, assignee)
        _uiState.update { it.copy(column = if (assignee == null) "triage" else "ready") }
    }

    fun move(id: String, status: String, reason: String? = null) = act { kanban.move(id, status, reason) }

    fun comment(id: String, text: String, onDone: () -> Unit) = act(onDone) { kanban.comment(id, text) }

    fun delete(id: String) = act {
        kanban.delete(id)
        _uiState.update { it.copy(detail = null) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun act(onDone: () -> Unit = {}, action: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            try {
                action()
                onDone()
                refresh()
            } catch (e: Exception) {
                Timber.w(e, "[Kanban] action failed")
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun firstBusy(columns: Map<String, List<KanbanRepository.Task>>): String? =
        listOf("running", "review", "blocked", "ready", "triage", "todo").firstOrNull { !columns[it].isNullOrEmpty() }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
