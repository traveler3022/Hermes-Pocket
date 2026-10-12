package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.KanbanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
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
        val boards: List<KanbanRepository.Board> = emptyList(),
        /** The board on screen; null until the server says which one is current. */
        val board: String? = null,
        val columns: Map<String, List<KanbanRepository.Task>> = emptyMap(),
        val column: String = "ready",
        val isLoading: Boolean = true,
        val assignees: List<String> = emptyList(),
        val detail: KanbanRepository.Detail? = null,
        /** The open task's worker log, once asked for; re-read with the board while it runs. */
        val log: KanbanRepository.Log? = null,
        val isLogLoading: Boolean = false,
        /** Run id → the live numbers of its worker, for the open task. */
        val inspections: Map<Long, KanbanRepository.Inspection> = emptyMap(),
        val isBusy: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState(available = kanban.available))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Whether the server's event stream is open; the board is then re-read on each change. */
    @Volatile
    private var live = false

    /**
     * Re-reads the board while the screen is in front; workers move cards on their own. A remote
     * server says when something changed (its event stream), and the timer only backs that up;
     * the phone's own gateway has no stream, so it is read on the timer alone.
     */
    suspend fun watch() = coroutineScope {
        if (!kanban.available) return@coroutineScope
        launch { loadBoards() }
        if (kanban.hasLiveChanges) launch { followChanges() }
        while (true) {
            refresh()
            delay(if (live) LIVE_POLL_MS else POLL_MS)
        }
    }

    /** The stream is pinned to one board at the handshake, so a board change opens a new one. */
    private suspend fun followChanges() {
        kanban.selectedBoard.collectLatest { followChanges(it) }
    }

    private suspend fun followChanges(board: String?) {
        var retry = 0
        try {
            while (true) {
                try {
                    // A burst of events while a read is on collapses into one more read.
                    kanban.changes().conflate().collect {
                        live = true
                        retry = 0
                        if (kanban.selectedBoard.value == board) refresh()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("[Kanban] event stream: ${e.message}")
                }
                live = false
                delay((STREAM_RETRY_MS shl retry.coerceAtMost(4)).coerceAtMost(MAX_STREAM_RETRY_MS))
                retry++
            }
        } finally {
            live = false
        }
    }

    private suspend fun loadBoards() {
        runCatching { kanban.boards() }
            .onSuccess { boards ->
                _uiState.update { state ->
                    state.copy(boards = boards, board = kanban.selectedBoard.value ?: boards.firstOrNull { it.isCurrent }?.slug)
                }
            }
            .onFailure { e -> Timber.w(e, "[Kanban] boards read failed") }
    }

    /** Re-reads the boards and their counts, for the board menu as it opens. */
    fun refreshBoards() {
        viewModelScope.launch { loadBoards() }
    }

    /** Shows [slug]'s board: its columns are read fresh, the open task (another board's) closes. */
    fun selectBoard(slug: String) {
        if (slug == _uiState.value.board) return
        kanban.selectBoard(slug)
        _uiState.update {
            it.copy(board = slug, columns = emptyMap(), isLoading = true, detail = null, log = null, inspections = emptyMap())
        }
        viewModelScope.launch { refresh() }
    }

    fun createBoard(name: String, onDone: () -> Unit) = act(onDone) {
        val slug = kanban.createBoard(name)
        loadBoards()
        selectBoard(slug)
    }

    private suspend fun refresh() {
        try {
            val board = kanban.selectedBoard.value
            val columns = kanban.board()
            // A read that began on the board the user just left must not overwrite the new one.
            if (kanban.selectedBoard.value != board) return
            _uiState.update { state ->
                // Open on the first column with work in it, until the user picks one.
                val column = if (state.isLoading) firstBusy(columns) ?: state.column else state.column
                state.copy(columns = columns, column = column, isLoading = false)
            }
            _uiState.value.detail?.let { open ->
                val detail = kanban.task(open.task.id)
                // Read it before the update: a sheet closed meanwhile must stay closed.
                // A failed log read keeps the last one; it must not fail the board.
                val log = if (_uiState.value.log != null && detail.task.status == "running") {
                    runCatching { kanban.log(detail.task.id) }.getOrNull()
                } else {
                    null
                }
                _uiState.update { state ->
                    if (state.detail?.task?.id != detail.task.id) state
                    else state.copy(detail = detail, log = log ?: state.log)
                }
            }
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
                .onSuccess { detail ->
                    _uiState.update { it.copy(detail = detail, log = null, isLogLoading = false, inspections = emptyMap()) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun close() = _uiState.update { it.copy(detail = null, log = null, isLogLoading = false, inspections = emptyMap()) }

    fun loadLog(id: String) {
        if (_uiState.value.isLogLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLogLoading = true) }
            runCatching { kanban.log(id) }
                .onSuccess { log -> _uiState.update { if (it.detail?.task?.id == id) it.copy(log = log) else it } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isLogLoading = false) }
        }
    }

    fun inspect(runId: Long) {
        val id = _uiState.value.detail?.task?.id ?: return
        viewModelScope.launch {
            runCatching { kanban.inspect(runId) }
                .onSuccess { found ->
                    _uiState.update { if (it.detail?.task?.id == id) it.copy(inspections = it.inspections + (runId to found)) else it }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun reclaim(id: String) = act { kanban.reclaim(id, "Stopped from the Hermes app") }

    fun reassign(id: String, profile: String?, running: Boolean) = act { kanban.reassign(id, profile, stopWorker = running) }

    fun create(title: String, body: String, assignee: String?, onDone: () -> Unit) = act(onDone) {
        kanban.create(title, body, assignee)
        _uiState.update { it.copy(column = if (assignee == null) "triage" else "ready") }
    }

    fun move(id: String, status: String, reason: String? = null) = act { kanban.move(id, status, reason) }

    fun comment(id: String, text: String, onDone: () -> Unit) = act(onDone) { kanban.comment(id, text) }

    fun delete(id: String) = act {
        kanban.delete(id)
        close()
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
        const val LIVE_POLL_MS = 60_000L
        const val STREAM_RETRY_MS = 2_000L
        const val MAX_STREAM_RETRY_MS = 30_000L
    }
}
