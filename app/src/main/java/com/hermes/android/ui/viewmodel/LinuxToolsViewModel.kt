package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.runtime.LinuxFileAccess
import com.hermes.android.runtime.LinuxTools
import com.hermes.android.runtime.PackageProfile
import com.hermes.android.runtime.TerminalLaunchSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinuxToolsUiState(
    val rootfsInstalled: Boolean = false,
    /** Profile id → installed; absent until the first check finishes. */
    val installed: Map<String, Boolean> = emptyMap(),
    val checking: Boolean = false,
    val installing: String? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LinuxToolsViewModel @Inject constructor(
    private val tools: LinuxTools,
    val linuxFiles: LinuxFileAccess,
) : ViewModel() {

    private val _state = MutableStateFlow(LinuxToolsUiState(rootfsInstalled = tools.isRootfsInstalled))
    val state: StateFlow<LinuxToolsUiState> = _state.asStateFlow()

    val profiles: List<PackageProfile> = tools.profiles

    fun refresh() {
        val installed = tools.isRootfsInstalled
        _state.update { it.copy(rootfsInstalled = installed) }
        if (!installed || _state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val result = runCatching { tools.installedProfiles() }
            _state.update {
                it.copy(
                    checking = false,
                    installed = result.getOrDefault(emptyMap()),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun install(profile: PackageProfile) {
        if (_state.value.installing != null) return
        viewModelScope.launch {
            _state.update { it.copy(installing = profile.id, log = emptyList(), error = null) }
            try {
                val outcome = tools.install(profile) { line -> appendLog(line) }
                _state.update {
                    it.copy(
                        installed = it.installed + (profile.id to outcome.ok),
                        error = outcome.error,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Install failed") }
            } finally {
                _state.update { it.copy(installing = null) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun showMessage(message: String) = _state.update { it.copy(error = message) }

    suspend fun terminalLaunchSpec(): Result<TerminalLaunchSpec> = tools.terminalLaunchSpec()

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        _state.update { it.copy(log = (it.log + line).takeLast(LOG_LINES)) }
    }

    private companion object {
        const val LOG_LINES = 12
    }
}
