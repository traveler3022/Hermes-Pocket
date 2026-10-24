package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.session.ForeignSession
import com.hermes.android.domain.session.ForeignSessionPreview
import com.hermes.android.domain.session.ForeignSessions
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
import javax.inject.Inject

/** Settings › Import chats: Claude Code / Codex sessions on the Hermes machine, into Hermes' history. */
@HiltViewModel
class ForeignSessionsViewModel @Inject constructor(
    private val foreign: ForeignSessions,
) : ViewModel() {

    data class UiState(
        /** `claude`, `codex`, or null for both. */
        val source: String? = null,
        val sessions: List<ForeignSession> = emptyList(),
        val nextOffset: Int? = null,
        val host: String = "",
        val unreadable: Int = 0,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val loadError: String? = null,
        /** The session whose preview sheet is open. */
        val selected: ForeignSession? = null,
        val preview: ForeignSessionPreview? = null,
        val previewError: String? = null,
        val importing: Boolean = false,
    )

    sealed interface Event {
        data class OpenChat(val sessionId: String) : Event
        data class Error(val message: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var listJob: Job? = null
    private var previewJob: Job? = null

    init {
        load()
    }

    fun setSource(source: String?) {
        if (source == _uiState.value.source) return
        _uiState.update { it.copy(source = source) }
        load()
    }

    /** The first page again (also after a source change). */
    fun load() {
        listJob?.cancel()
        _uiState.update { it.copy(loading = true, loadingMore = false, loadError = null) }
        listJob = viewModelScope.launch {
            try {
                val page = foreign.list(_uiState.value.source, 0)
                _uiState.update {
                    it.copy(
                        sessions = page.sessions,
                        nextOffset = page.nextOffset,
                        host = page.host,
                        unreadable = page.unreadable,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Foreign] list failed")
                _uiState.update { it.copy(sessions = emptyList(), nextOffset = null, loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val offset = state.nextOffset ?: return
        if (state.loading || state.loadingMore) return
        _uiState.update { it.copy(loadingMore = true) }
        listJob = viewModelScope.launch {
            try {
                val page = foreign.list(state.source, offset)
                _uiState.update { current ->
                    val known = current.sessions.mapTo(HashSet()) { it.id }
                    current.copy(
                        sessions = current.sessions + page.sessions.filter { it.id !in known },
                        nextOffset = page.nextOffset,
                        unreadable = current.unreadable + page.unreadable,
                        loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Foreign] next page failed")
                _uiState.update { it.copy(loadingMore = false) }
                _events.tryEmit(Event.Error(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun open(session: ForeignSession) {
        previewJob?.cancel()
        _uiState.update { it.copy(selected = session, preview = null, previewError = null, importing = false) }
        previewJob = viewModelScope.launch {
            try {
                val preview = foreign.preview(session.id)
                _uiState.update { if (it.selected?.id == session.id) it.copy(preview = preview) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Foreign] preview failed")
                _uiState.update { if (it.selected?.id == session.id) it.copy(previewError = e.message ?: e.javaClass.simpleName) else it }
            }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        _uiState.update { it.copy(selected = null, preview = null, previewError = null, importing = false) }
    }

    /** Imports the open session, or opens the earlier import. */
    fun importSelected() {
        val state = _uiState.value
        val session = state.selected ?: return
        if (state.importing) return
        state.preview?.importedSessionId?.let { existing ->
            closePreview()
            _events.tryEmit(Event.OpenChat(existing))
            return
        }
        _uiState.update { it.copy(importing = true) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            try {
                val sessionId = foreign.import(session.id)
                // Not closePreview(): it would cancel this very job.
                _uiState.update { it.copy(selected = null, preview = null, previewError = null, importing = false) }
                _events.tryEmit(Event.OpenChat(sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Foreign] import failed")
                _uiState.update { it.copy(importing = false) }
                _events.tryEmit(Event.Error(e.message ?: e.javaClass.simpleName))
            }
        }
    }
}
