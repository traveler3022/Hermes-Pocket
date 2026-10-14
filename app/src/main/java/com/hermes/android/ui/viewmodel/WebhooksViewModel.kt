package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.messaging.Webhooks
import com.hermes.android.domain.messaging.Webhooks.Created
import com.hermes.android.domain.messaging.Webhooks.Draft
import com.hermes.android.domain.messaging.Webhooks.Route
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

data class WebhooksUiState(
    val loading: Boolean = false,
    /** Null until the first list arrives. */
    val platformEnabled: Boolean? = null,
    val baseUrl: String = "",
    val routes: List<Route> = emptyList(),
    val loadError: String? = null,
    /** Set while enable/create/delete/switch runs; actions are disabled meanwhile. */
    val busy: Boolean = false,
    /** The route just made, with the secret the server shows only once. */
    val created: Created? = null,
)

sealed interface WebhooksEvent {
    data class PlatformEnabled(val needsRestart: Boolean) : WebhooksEvent
    data class Deleted(val name: String) : WebhooksEvent
    data class Failed(val message: String) : WebhooksEvent
}

/** Webhook routes of the messaging gateway: list, switch, create, delete, and turning the platform on. */
@HiltViewModel
class WebhooksViewModel @Inject constructor(
    private val webhooks: Webhooks,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebhooksUiState())
    val uiState: StateFlow<WebhooksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WebhooksEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WebhooksEvent> = _events.asSharedFlow()

    val available: Boolean get() = webhooks.available

    init {
        if (available) refresh()
    }

    fun refresh() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val list = webhooks.list()
                _uiState.update {
                    it.copy(
                        loading = false,
                        platformEnabled = list.platformEnabled,
                        baseUrl = list.baseUrl,
                        routes = list.routes,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Webhooks] list failed")
                _uiState.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun enablePlatform() {
        act {
            val result = webhooks.enablePlatform()
            WebhooksEvent.PlatformEnabled(result.needsRestart)
        }
    }

    fun create(draft: Draft) {
        act {
            val created = webhooks.create(draft)
            _uiState.update { it.copy(created = created) }
            null
        }
    }

    fun dismissCreated() {
        _uiState.update { it.copy(created = null) }
    }

    fun delete(route: Route) {
        act {
            webhooks.delete(route.name)
            WebhooksEvent.Deleted(route.name)
        }
    }

    fun setEnabled(route: Route, enabled: Boolean) {
        if (_uiState.value.busy) return
        // Flip the switch at once; the refresh after the call shows what the server kept.
        _uiState.update { state ->
            state.copy(routes = state.routes.map { if (it.name == route.name) it.copy(enabled = enabled) else it })
        }
        act {
            webhooks.setEnabled(route.name, enabled)
            null
        }
    }

    /** Runs [call] alone, reports a failure, then reloads the list either way. */
    private fun act(call: suspend () -> WebhooksEvent?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val event = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Webhooks] action failed")
                WebhooksEvent.Failed(e.message ?: e.javaClass.simpleName)
            }
            _uiState.update { it.copy(busy = false) }
            event?.let { _events.emit(it) }
            refresh()
        }
    }
}
