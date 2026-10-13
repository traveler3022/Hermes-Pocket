package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.DownloadStorage
import com.hermes.android.data.KanbanRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.LinuxFileAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** The server's Kanban board: one column at a time, a task's detail, and the moves a person makes. */
@HiltViewModel
class KanbanViewModel @Inject constructor(
    private val kanban: KanbanRepository,
    private val gatewayClient: GatewayClient,
    hermesRuntime: HermesRuntime,
    linuxFiles: LinuxFileAccess,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val downloads by lazy { DownloadStorage(context) }

    /** Turns a server path into what the file viewer reads, per runtime, as for chat files. */
    private val media = ChatAttachmentDelegate(gatewayClient, hermesRuntime, linuxFiles, context, viewModelScope)

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
        /** Words the shown tasks must contain (title, details, hand-off note). */
        val query: String = "",
        /** Show one profile's tasks: a name, [UNASSIGNED], or null for everyone's. */
        val assigneeFilter: String? = null,
        /** Tasks picked for a change on all of them at once (long press). */
        val selected: Set<String> = emptySet(),
        val orchestration: KanbanRepository.Orchestration? = null,
        /** The open task is being written out by the server's model (specify). */
        val isSpecifying: Boolean = false,
        /** Something done that the user should hear about (a file saved, a board imported). */
        val notice: String? = null,
        /** The `archived` column is read and shown too. */
        val showArchived: Boolean = false,
        /** Tasks on the board with trouble, worst first. */
        val diagnostics: List<KanbanRepository.TaskDiagnostics> = emptyList(),
        /** The open task's estimate, once asked for. */
        val estimate: KanbanRepository.Estimate? = null,
        val isEstimating: Boolean = false,
        val modelOptions: List<KanbanRepository.ModelProvider> = emptyList(),
        /** The open task's home channels; null until read. */
        val homeChannels: List<KanbanRepository.HomeChannel>? = null,
    ) {
        val filtering: Boolean get() = query.isNotBlank() || assigneeFilter != null

        /** [columns] narrowed to the tasks that pass the filter. */
        val shown: Map<String, List<KanbanRepository.Task>>
            get() = if (!filtering) columns else columns.mapValues { (_, tasks) -> tasks.filter(::passes) }

        private fun passes(task: KanbanRepository.Task): Boolean {
            val who = when (assigneeFilter) {
                null -> true
                UNASSIGNED -> task.assignee == null
                else -> task.assignee == assigneeFilter
            }
            val words = query.trim()
            return who && (
                words.isEmpty() ||
                    listOf(task.title, task.body, task.summary, task.id).any { it.contains(words, ignoreCase = true) }
                )
        }
    }

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
        // First, so the board kept from last time is the one read and followed.
        loadBoards()
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

    private var boardRestored = false

    private suspend fun loadBoards() {
        runCatching { kanban.boards() }
            .onSuccess { boards ->
                if (!boardRestored) {
                    boardRestored = true
                    kanban.restoreBoard(boards)
                }
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
            it.copy(
                board = slug, columns = emptyMap(), isLoading = true, detail = null, log = null,
                inspections = emptyMap(), selected = emptySet(), assigneeFilter = null, diagnostics = emptyList(),
            )
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
            val columns = kanban.board(includeArchived = _uiState.value.showArchived)
            // A read that began on the board the user just left must not overwrite the new one.
            if (kanban.selectedBoard.value != board) return
            _uiState.update { state ->
                // Open on the first column with work in it, until the user picks one.
                val column = if (state.isLoading) firstBusy(columns) ?: state.column else state.column
                state.copy(columns = columns, column = column, isLoading = false)
            }
            // An older server has no diagnostics; that must not fail the board.
            runCatching { kanban.diagnostics() }.onSuccess { found ->
                if (kanban.selectedBoard.value == board) _uiState.update { it.copy(diagnostics = found) }
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

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setAssigneeFilter(assignee: String?) = _uiState.update { it.copy(assigneeFilter = assignee) }

    fun clearFilter() = _uiState.update { it.copy(query = "", assigneeFilter = null) }

    fun toggleSelected(id: String) = _uiState.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    /** Picks every shown task of the open column. */
    fun selectColumnTasks() = _uiState.update { state ->
        state.copy(selected = state.selected + state.shown[state.column].orEmpty().map { it.id })
    }

    fun bulkMove(status: String) = bulk { ids -> kanban.bulk(ids, status = status) }

    fun bulkArchive() = bulk { ids -> kanban.bulk(ids, archive = true) }

    /** [profile] null: nobody. */
    fun bulkAssign(profile: String?) = bulk { ids -> kanban.bulk(ids, assignee = profile.orEmpty()) }

    /** Runs a change on the picked tasks; refused ones stay picked, with the first reason shown. */
    private fun bulk(change: suspend (Set<String>) -> Map<String, String>) {
        val ids = _uiState.value.selected.ifEmpty { return }
        act {
            val refused = change(ids)
            _uiState.update { it.copy(selected = refused.keys.intersect(ids)) }
            // Not thrown: the others did change, and the board is re-read after a change that returns.
            if (refused.isNotEmpty()) {
                _uiState.update { it.copy(error = "${refused.size}/${ids.size}: ${refused.values.first()}") }
            }
        }
    }

    /** What the file viewer opens for [attachment]: the server's download, or the file in the built-in Linux. */
    fun attachmentUrl(attachment: KanbanRepository.Attachment): String = media.resolveMediaUrl(attachment.storedPath)

    /** Makes the open task wait on [parentId]. */
    fun addParent(parentId: String) {
        val id = _uiState.value.detail?.task?.id ?: return
        act { kanban.link(parentId = parentId, childId = id) }
    }

    fun unlink(parentId: String, childId: String) = act { kanban.unlink(parentId, childId) }

    /** Has the server's model write the open triage task out; the sheet shows the new text when it returns. */
    fun specify(id: String) {
        if (_uiState.value.isSpecifying) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSpecifying = true, error = null) }
            try {
                val refused = kanban.specify(id)
                if (refused != null) _uiState.update { it.copy(error = refused) }
                refresh()
            } catch (e: Exception) {
                Timber.w(e, "[Kanban] specify failed")
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isSpecifying = false) }
            }
        }
    }

    fun loadOrchestration() {
        viewModelScope.launch {
            runCatching { kanban.orchestration() }
                .onSuccess { found -> _uiState.update { it.copy(orchestration = found) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun saveOrchestration(settings: KanbanRepository.Orchestration, onDone: () -> Unit) = act(onDone) {
        val saved = kanban.saveOrchestration(settings)
        _uiState.update { it.copy(orchestration = saved) }
    }

    fun loadAssignees() {
        viewModelScope.launch {
            runCatching { kanban.assignees() }
                .onSuccess { list -> _uiState.update { it.copy(assignees = list) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun open(task: KanbanRepository.Task) = open(task.id)

    fun open(id: String) {
        viewModelScope.launch {
            runCatching { kanban.task(id) }
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            detail = detail, log = null, isLogLoading = false, inspections = emptyMap(),
                            estimate = null, isEstimating = false, homeChannels = null,
                        )
                    }
                    loadHomeChannels(id)
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun close() = _uiState.update {
        it.copy(detail = null, log = null, isLogLoading = false, inspections = emptyMap(), estimate = null, homeChannels = null)
    }

    fun clearNotice() = _uiState.update { it.copy(notice = null) }

    fun setShowArchived(show: Boolean) {
        _uiState.update { it.copy(showArchived = show, column = if (show) "archived" else if (it.column == "archived") "done" else it.column) }
        viewModelScope.launch { refresh() }
    }

    /** Adds the file at [uri] (picked on the phone) to the open task. */
    fun upload(uri: Uri) {
        val id = _uiState.value.detail?.task?.id ?: return
        act {
            val (file, name, mime) = copyToCache(uri)
            try {
                kanban.upload(id, file, name, mime)
            } finally {
                file.delete()
            }
            _uiState.update { it.copy(notice = "Added $name") }
        }
    }

    fun deleteAttachment(id: Long) = act { kanban.deleteAttachment(id) }

    fun estimate(id: String) {
        if (_uiState.value.isEstimating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEstimating = true, error = null) }
            runCatching { kanban.estimate(id) }
                .onSuccess { found -> _uiState.update { if (it.detail?.task?.id == id) it.copy(estimate = found) else it } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isEstimating = false) }
        }
    }

    fun loadModelOptions() {
        if (_uiState.value.modelOptions.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { kanban.modelOptions() }
                .onSuccess { found -> _uiState.update { it.copy(modelOptions = found) } }
                .onFailure { e -> Timber.w(e, "[Kanban] model options failed") }
        }
    }

    fun setRunSettings(id: String, model: String?, provider: String?, effort: String?) =
        act { kanban.setRunSettings(id, model, provider, effort) }

    private suspend fun loadHomeChannels(id: String) {
        runCatching { kanban.homeChannels(id) }
            .onSuccess { found -> _uiState.update { if (it.detail?.task?.id == id) it.copy(homeChannels = found) else it } }
            .onFailure { e -> Timber.w(e, "[Kanban] home channels failed") }
    }

    fun setHomeChannel(platform: String, on: Boolean) {
        val id = _uiState.value.detail?.task?.id ?: return
        act {
            kanban.setHomeChannel(id, platform, on)
            loadHomeChannels(id)
        }
    }

    /** Writes the board on screen to an archive on the server and saves it on the phone. */
    fun exportBoard() {
        val slug = _uiState.value.board ?: return
        act {
            val serverPath = kanban.exportBoard(slug)
            val name = serverPath.substringAfterLast('/')
            val savedAs = downloads.save(media.resolveMediaUrl(serverPath), name) { gatewayClient.fetchFile(it) }
            _uiState.update { it.copy(notice = "Saved to $savedAs") }
        }
    }

    /** Imports the board archive at [uri] as a new board and shows it. */
    fun importBoard(uri: Uri) {
        act {
            val (file, _, _) = copyToCache(uri)
            val slug = try {
                kanban.importBoard(file)
            } finally {
                file.delete()
            }
            loadBoards()
            selectBoard(slug)
            _uiState.update { it.copy(notice = "Imported board $slug") }
        }
    }

    /** A picked file as a file in the app's cache, with its name and type. */
    private suspend fun copyToCache(uri: Uri): Triple<File, String, String?> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val dir = File(context.cacheDir, "kanban-uploads").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString())
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open $name")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        Triple(file, name, resolver.getType(uri))
    }

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

    companion object {
        /** [UiState.assigneeFilter] for tasks nobody is assigned to. */
        const val UNASSIGNED = "\u0000unassigned"

        private const val POLL_MS = 10_000L
        private const val LIVE_POLL_MS = 60_000L
        private const val STREAM_RETRY_MS = 2_000L
        private const val MAX_STREAM_RETRY_MS = 30_000L
    }
}
