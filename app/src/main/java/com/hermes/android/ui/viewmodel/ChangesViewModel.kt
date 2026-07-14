package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * "Changes" — git-checkpoint diff/restore for the active session (Milestone D
 * slice: rollback.list/diff/restore, session.undo). Session-scoped like chat,
 * so it's a sibling ViewModel opened as a sheet over ChatScreen rather than a
 * standalone nav route.
 */
@HiltViewModel
class ChangesViewModel @Inject constructor(
    private val repository: SessionRepository,
) : ViewModel() {

    data class ChangesUiState(
        val checkpoints: List<SessionRepository.Checkpoint> = emptyList(),
        val isLoading: Boolean = false,
        val selectedHash: String? = null,
        val selectedDiff: SessionRepository.CheckpointDiff? = null,
        val isLoadingDiff: Boolean = false,
        val isRestoring: Boolean = false,
        val error: String? = null,
        val lastActionMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(ChangesUiState())
    val uiState = _uiState.asStateFlow()

    private var sessionId: String? = null

    fun load(liveSessionId: String) {
        sessionId = liveSessionId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value = _uiState.value.copy(
                    checkpoints = repository.checkpoints(liveSessionId),
                    isLoading = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Changes] load failed")
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun viewDiff(hash: String) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDiff = true, selectedHash = hash)
            try {
                _uiState.value = _uiState.value.copy(
                    selectedDiff = repository.checkpointDiff(sid, hash),
                    isLoadingDiff = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Changes] diff failed")
                _uiState.value = _uiState.value.copy(isLoadingDiff = false, error = e.message)
            }
        }
    }

    fun closeDiff() {
        _uiState.value = _uiState.value.copy(selectedDiff = null, selectedHash = null)
    }

    fun restore(hash: String) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            try {
                val result = repository.restoreCheckpoint(sid, hash)
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    selectedDiff = null,
                    selectedHash = null,
                    lastActionMessage = if (result.success) {
                        "Restored (${result.historyRemoved} turns removed)"
                    } else {
                        "Restore did not apply"
                    },
                )
                load(sid)
            } catch (e: Exception) {
                Timber.e(e, "[Changes] restore failed")
                _uiState.value = _uiState.value.copy(isRestoring = false, error = e.message)
            }
        }
    }

    fun undoLastTurn() {
        val sid = sessionId ?: return
        viewModelScope.launch {
            try {
                val removed = repository.undoLastTurn(sid)
                _uiState.value = _uiState.value.copy(
                    lastActionMessage = if (removed > 0) "Undid last turn" else "Nothing to undo",
                )
            } catch (e: Exception) {
                Timber.e(e, "[Changes] undo failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(lastActionMessage = null)
    }
}
