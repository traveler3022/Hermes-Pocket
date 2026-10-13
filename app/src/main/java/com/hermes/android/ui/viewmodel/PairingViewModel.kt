package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.messaging.MessagingPairing
import com.hermes.android.domain.messaging.MessagingPairing.Request
import com.hermes.android.domain.messaging.MessagingPairing.User
import com.hermes.android.domain.messaging.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

data class PairingUiState(
    val loading: Boolean = false,
    val pending: List<Request> = emptyList(),
    val approved: List<User> = emptyList(),
    /** Set while an approve/revoke/clear runs; its rows are disabled. */
    val busy: Boolean = false,
    val loadError: String? = null,
)

sealed interface PairingEvent {
    data class Approved(val who: String) : PairingEvent
    data class Revoked(val who: String) : PairingEvent
    data object Cleared : PairingEvent
    data class Failed(val message: String) : PairingEvent
}

/** Who asked to talk to the user's bots, and who may: approve, revoke, clear requests. */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairing: MessagingPairing,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    val available: Boolean get() = pairing.available

    init {
        if (available) refresh()
    }

    fun refresh() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val list = pairing.list()
                _uiState.update { it.copy(loading = false, pending = list.pending, approved = list.approved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Pairing] list failed")
                _uiState.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun approve(request: Request) {
        val id = request.requestId ?: return
        act({ pairing.approve(request.platform, id) }) { PairingEvent.Approved(request.userName.ifBlank { request.userId }) }
    }

    fun revoke(user: User) {
        act({ pairing.revoke(user.platform, user.userId) }) { PairingEvent.Revoked(user.userName.ifBlank { user.userId }) }
    }

    fun clearPending() {
        act({ pairing.clearPending() }) { PairingEvent.Cleared }
    }

    private fun act(call: suspend () -> PairingResult, onDone: () -> PairingEvent) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val event = try {
                when (val result = call()) {
                    PairingResult.Done -> onDone()
                    is PairingResult.Failed -> PairingEvent.Failed(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Pairing] action failed")
                PairingEvent.Failed(e.message ?: e.javaClass.simpleName)
            }
            _uiState.update { it.copy(busy = false) }
            _events.emit(event)
            refresh()
        }
    }
}
