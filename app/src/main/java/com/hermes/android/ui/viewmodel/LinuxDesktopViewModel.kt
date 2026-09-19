package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.runtime.linux.LinuxDesktop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinuxDesktopUiState(
    /** Null until the first check finishes. */
    val installed: Boolean? = null,
    val busy: Boolean = false,
    /** A setting changed that only applies after the desktop restarts. */
    val restartPending: Boolean = false,
    val message: String? = null,
)

/** Browser & desktop (VNC) settings and viewer — Aether's Alpine Chrome page. */
@HiltViewModel
class LinuxDesktopViewModel @Inject constructor(
    private val desktop: LinuxDesktop,
) : ViewModel() {

    private val _ui = MutableStateFlow(LinuxDesktopUiState())
    val ui: StateFlow<LinuxDesktopUiState> = _ui.asStateFlow()

    val state: StateFlow<LinuxDesktop.State> = desktop.state
    val settings: StateFlow<LinuxDesktop.Settings> = desktop.settings

    val viewerUrl: String get() = desktop.viewerUrl

    fun refresh() {
        viewModelScope.launch {
            val installed = desktop.isInstalled()
            _ui.update { it.copy(installed = installed) }
            desktop.refresh()
        }
    }

    suspend fun start(): Result<Unit> = desktop.start().also { result ->
        if (result.isSuccess) _ui.update { it.copy(restartPending = false) }
    }

    fun startInBackground() {
        viewModelScope.launch { start() }
    }

    fun stop() = busy { desktop.stop() }

    fun restart() = busy {
        desktop.restart()
        _ui.update { it.copy(restartPending = false) }
    }

    fun update(transform: (LinuxDesktop.Settings) -> LinuxDesktop.Settings) = busy {
        if (desktop.update(transform(desktop.settings.value))) {
            _ui.update { it.copy(restartPending = true) }
        }
    }

    fun clearBrowserData(doneMessage: String) = busy {
        desktop.clearBrowserData()
            .onSuccess { _ui.update { it.copy(message = doneMessage) } }
            .onFailure { e -> _ui.update { it.copy(message = e.message) } }
    }

    fun lanAddress(): String? = desktop.lanAddress()

    suspend fun shouldShowKeyboard(y: Int): Boolean = desktop.focusedElementIsEditable(y)

    fun clearMessage() = _ui.update { it.copy(message = null) }

    private fun busy(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }
}
