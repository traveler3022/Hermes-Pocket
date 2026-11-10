package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.portal.Connector
import com.hermes.android.domain.portal.ConnectorAccount
import com.hermes.android.domain.portal.NousPortal
import com.hermes.android.domain.portal.PortalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Settings › Nous Portal: the account's status and the apps it has connected. */
@HiltViewModel
class NousPortalViewModel @Inject constructor(
    private val portal: NousPortal,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val status: PortalStatus? = null,
        val statusError: String? = null,
        /** False when this Hermes has connectors switched off. */
        val connectorsAvailable: Boolean = true,
        val connectors: List<Connector> = emptyList(),
        val connectorsError: String? = null,
        val accounts: List<ConnectorAccount> = emptyList(),
        /** The app whose connection is waiting on the browser. */
        val connecting: String? = null,
        /** The account being disconnected. */
        val removing: String? = null,
    )

    sealed interface Event {
        data class OpenUrl(val url: String) : Event
        data class Error(val message: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var connectJob: Job? = null

    /** The operation waiting on the browser; woken when the user comes back to the app. */
    private var openOperation: String? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            // Each part on its own: a server without the dashboard API still lists connectors.
            val status = attempt { portal.status() }
            val list = attempt { portal.connectors() }
            val accounts = attempt { portal.accounts() }
            _uiState.update {
                it.copy(
                    loading = false,
                    status = status.getOrNull(),
                    statusError = status.exceptionOrNull()?.message,
                    connectorsAvailable = list.getOrNull()?.available ?: true,
                    connectors = list.getOrNull()?.connectors.orEmpty(),
                    connectorsError = list.exceptionOrNull()?.message,
                    accounts = accounts.getOrNull().orEmpty(),
                )
            }
        }
    }

    /** Starts connecting [connector]: the browser opens the page, then the result is waited for. */
    fun connect(connector: Connector) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            _uiState.update { it.copy(connecting = connector.slug) }
            try {
                var operation = portal.connect(connector.slug, reconnect = connector.connected)
                openOperation = operation.opId
                operation.connectUrl?.let { _events.tryEmit(Event.OpenUrl(it)) }
                while (!operation.settled && operation.failure == null &&
                    System.currentTimeMillis() / 1000.0 < operation.deadlineAt
                ) {
                    delay(POLL_MS)
                    operation = portal.operation(operation.opId)
                }
                operation.failure?.let { _events.tryEmit(Event.Error(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[NousPortal] Connecting ${connector.slug} failed")
                _events.tryEmit(Event.Error(e.message ?: "Could not connect"))
            } finally {
                openOperation = null
                _uiState.update { it.copy(connecting = null) }
            }
            load()
        }
    }

    /** Stops waiting for the browser; a connection the user still finishes shows on the next load. */
    fun cancelConnect() {
        connectJob?.cancel()
    }

    /** The app is in front again: Hermes reads the browser's result now instead of at its next tick. */
    fun onResumed() {
        val opId = openOperation ?: return
        viewModelScope.launch {
            try {
                portal.wake(opId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The operation may have settled already; the poll finds out.
                Timber.d("[NousPortal] wake: ${e.message}")
            }
        }
    }

    fun removeAccount(account: ConnectorAccount) {
        if (_uiState.value.removing != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(removing = account.connectionId) }
            try {
                portal.removeAccount(account.connectionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[NousPortal] Removing an account failed")
                _events.tryEmit(Event.Error(e.message ?: "Could not disconnect"))
            } finally {
                _uiState.update { it.copy(removing = null) }
            }
            load()
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[NousPortal] load failed")
            Result.failure(e)
        }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
