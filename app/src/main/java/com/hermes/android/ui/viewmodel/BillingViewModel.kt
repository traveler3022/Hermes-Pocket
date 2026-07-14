package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Read-only billing state + auto-reload toggle. Charging is desktop/CLI-only. */
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
) : ViewModel() {

    data class BillingUiState(
        val state: BillingRepository.BillingState? = null,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value = _uiState.value.copy(state = repository.state(), isLoading = false)
            } catch (e: Exception) {
                Timber.w(e, "[Billing] load failed")
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setAutoReloadEnabled(enabled: Boolean, threshold: String, reloadTo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.setAutoReload(enabled, threshold, reloadTo)
                _uiState.value = _uiState.value.copy(state = repository.state(), isSaving = false)
            } catch (e: Exception) {
                Timber.e(e, "[Billing] auto-reload update failed")
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
