package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.memory.LearningJourney
import com.hermes.android.domain.memory.LearningNode
import com.hermes.android.domain.memory.LearningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Settings › Memory › What the agent learned: the learning graph, one node's text, edit and delete. */
@HiltViewModel
class LearningViewModel @Inject constructor(
    private val journey: LearningJourney,
) : ViewModel() {

    data class UiState(
        val nodes: List<LearningNode> = emptyList(),
        val links: Map<String, Set<String>> = emptyMap(),
        val loading: Boolean = true,
        val loadError: String? = null,
        /** Category shown; null = all. */
        val filter: String? = null,
        val open: Opened? = null,
    )

    /** The node whose sheet is open. */
    data class Opened(
        val node: LearningNode,
        val content: String? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val editing: Boolean = false,
        val draft: String = "",
        /** Saving or deleting. */
        val busy: Boolean = false,
    )

    sealed interface Event {
        data object Saved : Event
        /** [message] is Hermes' (for a skill it names the restore command). */
        data class Deleted(val message: String) : Event
        data class Failed(val message: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var detailJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val graph = journey.graph()
                _uiState.update { state ->
                    state.copy(
                        nodes = graph.nodes,
                        links = graph.links,
                        loading = false,
                        // A category whose last node went away would show an empty list.
                        filter = state.filter?.takeIf { f -> graph.nodes.any { it.category == f } },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Learning] graph failed")
                _uiState.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setFilter(category: String?) {
        _uiState.update { it.copy(filter = category) }
    }

    fun open(node: LearningNode) {
        _uiState.update { it.copy(open = Opened(node)) }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val content = journey.detail(node.id)
                updateOpened(node.id) { it.copy(content = content, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Learning] detail failed")
                updateOpened(node.id) { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun close() {
        detailJob?.cancel()
        _uiState.update { it.copy(open = null) }
    }

    fun startEdit() = updateOpenedNow { it.copy(editing = true, draft = it.content.orEmpty()) }

    fun cancelEdit() = updateOpenedNow { it.copy(editing = false) }

    fun updateDraft(text: String) = updateOpenedNow { it.copy(draft = text) }

    fun save() {
        val opened = _uiState.value.open ?: return
        if (opened.busy || opened.draft.isBlank()) return
        val id = opened.node.id
        val draft = opened.draft
        updateOpened(id) { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = guarded { journey.edit(id, draft) }) {
                is LearningResult.Done -> {
                    updateOpened(id) { it.copy(busy = false, editing = false, content = draft) }
                    _events.emit(Event.Saved)
                    // A memory's id carries a digest of its text, so the old id is stale now.
                    if (opened.node.kind == LearningNode.Kind.MEMORY) {
                        _uiState.update { it.copy(open = null) }
                        load()
                    }
                }
                is LearningResult.Failed -> {
                    updateOpened(id) { it.copy(busy = false) }
                    _events.emit(Event.Failed(result.message))
                }
            }
        }
    }

    fun delete() {
        val opened = _uiState.value.open ?: return
        if (opened.busy) return
        val id = opened.node.id
        updateOpened(id) { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = guarded { journey.delete(id) }) {
                is LearningResult.Done -> {
                    _uiState.update { it.copy(open = null) }
                    _events.emit(Event.Deleted(result.message))
                    load()
                }
                is LearningResult.Failed -> {
                    updateOpened(id) { it.copy(busy = false) }
                    _events.emit(Event.Failed(result.message))
                }
            }
        }
    }

    private suspend fun guarded(call: suspend () -> LearningResult): LearningResult = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[Learning] change failed")
        LearningResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    private fun updateOpenedNow(change: (Opened) -> Opened) {
        _uiState.update { state -> state.copy(open = state.open?.let(change)) }
    }

    /** Changes the open sheet only if it is still [id]'s (the user may have moved on). */
    private fun updateOpened(id: String, change: (Opened) -> Opened) {
        _uiState.update { state ->
            val open = state.open
            if (open == null || open.node.id != id) state else state.copy(open = change(open))
        }
    }
}

/** Nodes per month, oldest first, for the [months] months up to the newest dated node. */
internal fun monthlyActivity(
    nodes: List<LearningNode>,
    zone: ZoneId = ZoneId.systemDefault(),
    months: Int = 12,
): List<Pair<YearMonth, Int>> {
    val dated = nodes.mapNotNull { node -> node.timestamp?.let { YearMonth.from(Instant.ofEpochSecond(it).atZone(zone)) } }
    val last = dated.maxOrNull() ?: return emptyList()
    val first = maxOf(dated.min(), last.minusMonths((months - 1).toLong()))
    val counts = dated.groupingBy { it }.eachCount()
    return generateSequence(first) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .map { it to (counts[it] ?: 0) }
        .toList()
}
