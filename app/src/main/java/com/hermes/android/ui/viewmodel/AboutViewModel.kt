package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.AppReleaseRepository
import com.hermes.android.data.UpdateCheck
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.RuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val installedVersion: String = "",
    val isChecking: Boolean = false,
    /** Null until a check has run — "not checked yet" is not "up to date". */
    val result: UpdateCheck? = null,
    /** Hermes Agent's own version (`hermes --version`), not this app's. */
    val hermesVersion: String? = null,
    val canUpdateHermes: Boolean = false,
    val hermesUpdate: HermesUpdate = HermesUpdate.Idle,
)

/** Updating Hermes Agent inside the built-in Linux. */
sealed class HermesUpdate {
    object Idle : HermesUpdate()
    data class Running(val message: String, val percent: Int?) : HermesUpdate()
    /** [changed] false: the newest version was already installed. */
    data class Done(val version: String, val changed: Boolean) : HermesUpdate()
    data class Failed(val reason: String) : HermesUpdate()
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val releases: AppReleaseRepository,
    private val runtime: HermesRuntime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(
            installedVersion = releases.installedVersion,
            hermesVersion = runtime.state.value.hermesVersion(),
            canUpdateHermes = runtime.canUpdateHermes,
        ),
    )
    val uiState = _uiState.asStateFlow()

    /**
     * Asks GitHub what the newest release is.
     *
     * Only ever on request. A check on every open would mean the app calling
     * out to a third party without being asked — cheap in bytes, but not the
     * kind of thing a client for a self-hosted agent should do behind its
     * user's back.
     */
    fun check() {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            val result = releases.check()
            _uiState.value = _uiState.value.copy(isChecking = false, result = result)
        }
    }

    /** Update Hermes Agent in place; the runtime stops and restarts it around the swap. */
    fun updateHermes() {
        if (_uiState.value.hermesUpdate is HermesUpdate.Running) return
        val before = _uiState.value.hermesVersion
        _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running("", 0)) }
        viewModelScope.launch {
            val result = runtime.updateHermes { progress ->
                _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running(progress.message, progress.percent)) }
            }
            _uiState.update { state ->
                when (result) {
                    is InstallResult.Success -> {
                        val version = result.info.hermesVersion ?: before.orEmpty()
                        state.copy(
                            hermesVersion = version,
                            hermesUpdate = HermesUpdate.Done(version, changed = version != before),
                        )
                    }
                    is InstallResult.Failure -> state.copy(hermesUpdate = HermesUpdate.Failed(result.reason))
                    else -> state.copy(hermesUpdate = HermesUpdate.Idle)
                }
            }
        }
    }
}

private fun RuntimeState.hermesVersion(): String? = when (this) {
    is RuntimeState.Running -> info.hermesVersion
    is RuntimeState.Installed -> info.hermesVersion
    is RuntimeState.Detected -> info.hermesVersion
    else -> null
}
