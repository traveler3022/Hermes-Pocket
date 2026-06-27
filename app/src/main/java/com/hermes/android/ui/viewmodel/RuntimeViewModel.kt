package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.HermesRuntimeManager
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Runtime Setup screen.
 *
 * Depends ONLY on [HermesRuntimeManager] — never on any concrete runtime
 * implementation. This is the abstraction boundary: if we later swap
 * implementations, this file does not change.
 *
 * The UI observes [uiState] (a [RuntimeUiState]) — NOT the raw
 * [com.hermes.android.runtime.RuntimeState]. This keeps the UI decoupled
 * from the runtime layer's internal representation (Phase 1.5 Rule 1).
 *
 * Reference: ADR-002 (Native Compose), ADR-009 (production embedded Python),
 *            Phase 1.5 Rule 1 (Strict Layer Dependency)
 */
@HiltViewModel
class RuntimeViewModel @Inject constructor(
    private val runtimeManager: HermesRuntimeManager,
) : ViewModel() {

    /**
     * UI-facing state — converted from the runtime's RuntimeState.
     * The UI observes THIS, not the raw runtime state.
     */
    private val _uiState = MutableStateFlow<RuntimeUiState>(RuntimeUiState.NotDetected)
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgressUi?>(null)
    val installProgress: StateFlow<InstallProgressUi?> = _installProgress.asStateFlow()

    private val _installInstructions = MutableStateFlow<InstallInstructionsUi?>(null)
    val installInstructions: StateFlow<InstallInstructionsUi?> = _installInstructions.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Bridge runtime state → UI state
        viewModelScope.launch {
            runtimeManager.state.collect { runtimeState ->
                _uiState.value = mapToUiState(runtimeState)
            }
        }
        viewModelScope.launch {
            runtimeManager.installProgress.collect { progress ->
                _installProgress.value = progress?.let {
                    InstallProgressUi(
                        stage = it.stage,
                        message = it.message,
                        percent = it.percent,
                    )
                }
            }
        }
    }

    private fun mapToUiState(runtimeState: RuntimeState): RuntimeUiState {
        return when (runtimeState) {
            is RuntimeState.NotDetected -> RuntimeUiState.NotDetected
            is RuntimeState.Detecting -> RuntimeUiState.Detecting
            is RuntimeState.Detected -> RuntimeUiState.Detected(
                version = runtimeState.info.version,
                diskFreeBytes = runtimeState.info.diskFreeBytes,
            )
            is RuntimeState.Installing -> RuntimeUiState.Installing
            is RuntimeState.Installed -> RuntimeUiState.Installed(
                hermesVersion = runtimeState.info.hermesVersion,
            )
            is RuntimeState.Running -> RuntimeUiState.Running(
                webSocketUrl = runtimeState.gateway.webSocketUrl,
            )
            is RuntimeState.Error -> RuntimeUiState.Error(runtimeState.message)
        }
    }

    fun detect() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val result = runtimeManager.runtime.detect()
                if (result is DetectionResult.Missing) {
                    Timber.i("[Runtime] Runtime missing: ${result.title}")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Detection failed")
                _errorMessage.value = e.message ?: "Detection failed"
            }
        }
    }

    fun startInstall() {
        viewModelScope.launch {
            _errorMessage.value = null
            _installing.value = true

            val emitter = ProgressEmitter { progress ->
                Timber.d("[Runtime] Progress: ${progress.stage} — ${progress.message}")
            }

            try {
                val result = runtimeManager.runtime.install(emitter)
                when (result) {
                    is InstallResult.Success -> Timber.i("[Runtime] Install succeeded")
                    is InstallResult.Failure -> {
                        Timber.e("[Runtime] Install failed: ${result.reason}")
                        _errorMessage.value = result.reason
                    }
                    InstallResult.Cancelled -> Timber.w("[Runtime] Install cancelled")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Install threw exception")
                _errorMessage.value = e.message ?: "Install failed"
            } finally {
                _installing.value = false
            }
        }
    }

    fun startGateway() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val handle = runtimeManager.runtime.startGateway()
                Timber.i("[Runtime] Gateway started: ${handle.webSocketUrl}")
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Failed to start gateway")
                _errorMessage.value = e.message ?: "Failed to start gateway in Termux"
            }
        }
    }

    fun prepareInstallInstructions() {
        val instructions = runtimeManager.runtime.getInstallInstructions()
        if (instructions != null) {
            _installInstructions.value = InstallInstructionsUi(
                title = instructions.title,
                steps = instructions.steps,
                command = instructions.command,
            )
        } else {
            _errorMessage.value = "This runtime does not require user-side installation."
        }
    }

    fun launchHostApp() {
        val launched = runtimeManager.runtime.launchHostApp()
        if (!launched) {
            _errorMessage.value = "Could not launch the runtime host application."
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
