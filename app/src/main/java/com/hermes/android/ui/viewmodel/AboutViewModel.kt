package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.AppReleaseRepository
import com.hermes.android.data.UpdateCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val installedVersion: String = "",
    val isChecking: Boolean = false,
    /** Null until a check has run — "not checked yet" is not "up to date". */
    val result: UpdateCheck? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val releases: AppReleaseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(installedVersion = releases.installedVersion),
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
}
