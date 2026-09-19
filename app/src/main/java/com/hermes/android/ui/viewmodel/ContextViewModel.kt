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
 * "Context" — what is filling the active session's context window, and manual
 * compression when it fills up. Session-scoped like Changes, so it opens as a
 * sheet over ChatScreen rather than a nav route.
 */
@HiltViewModel
class ContextViewModel @Inject constructor(
    private val repository: SessionRepository,
) : ViewModel() {

    data class ContextUiState(
        val breakdown: SessionRepository.ContextBreakdown? = null,
        val isLoading: Boolean = false,
        val isCompressing: Boolean = false,
        val focusTopic: String = "",
        val error: String? = null,
        val lastActionMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(ContextUiState())
    val uiState = _uiState.asStateFlow()

    private var sessionId: String? = null

    fun load(liveSessionId: String) {
        sessionId = liveSessionId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value = _uiState.value.copy(
                    breakdown = repository.contextBreakdown(liveSessionId),
                    isLoading = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Context] breakdown failed")
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateFocusTopic(topic: String) {
        _uiState.value = _uiState.value.copy(focusTopic = topic)
    }

    fun compress() {
        val sid = sessionId ?: return
        if (_uiState.value.isCompressing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompressing = true)
            try {
                val result = repository.compressSession(sid, _uiState.value.focusTopic)
                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    focusTopic = "",
                    lastActionMessage = describe(result),
                )
                // Re-read rather than deriving the new usage from the result:
                // the compute-host path answers before compression finishes.
                load(sid)
            } catch (e: Exception) {
                Timber.w(e, "[Context] compress failed")
                _uiState.value = _uiState.value.copy(isCompressing = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(lastActionMessage = null)
    }

    private fun describe(result: SessionRepository.CompressionResult): String = when (result.status) {
        "compressed" -> {
            val saved = result.beforeTokens - result.afterTokens
            if (saved > 0) {
                "Compressed ${result.removedMessages} messages, freed ~$saved tokens"
            } else {
                "Compressed ${result.removedMessages} messages"
            }
        }
        "aborted" -> result.message.ifBlank { "Compression was aborted" }
        "pending" -> result.message.ifBlank { "Compressing in the background…" }
        "busy" -> result.message.ifBlank { "Another compression is already running" }
        else -> result.message.ifBlank { "Compression finished" }
    }
}
