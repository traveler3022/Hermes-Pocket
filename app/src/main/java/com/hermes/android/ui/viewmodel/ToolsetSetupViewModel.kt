package com.hermes.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.tools.ToolsetConfig
import com.hermes.android.domain.tools.ToolsetSetup
import com.hermes.android.i18n.tForContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ToolsetSetupUiState(
    val toolset: String = "",
    val config: ToolsetConfig? = null,
    val isLoading: Boolean = false,
    // The provider being switched to, or the env key being saved / cleared.
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null,
)

/** One toolset's backend and keys (the sheet behind a toolset row). */
@HiltViewModel
class ToolsetSetupViewModel @Inject constructor(
    private val setup: ToolsetSetup,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsetSetupUiState())
    val uiState: StateFlow<ToolsetSetupUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun open(toolset: String) {
        loadJob?.cancel()
        _uiState.value = ToolsetSetupUiState(toolset = toolset, isLoading = true)
        loadJob = viewModelScope.launch { reload(toolset) }
    }

    private suspend fun reload(toolset: String) {
        try {
            val config = setup.config(toolset)
            _uiState.update { if (it.toolset == toolset) it.copy(config = config, isLoading = false) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Toolsets] config of $toolset failed")
            _uiState.update { if (it.toolset == toolset) it.copy(isLoading = false, error = e.message) else it }
        }
    }

    fun selectProvider(provider: String) = act(provider) { toolset ->
        val needsSignIn = setup.selectProvider(toolset, provider)
        if (needsSignIn) {
            tForContext(context, "$provider chosen — it needs a Nous Portal sign-in to work", "$provider انتخاب شد — برای کار کردن ورود به Nous Portal لازم است")
        } else {
            tForContext(context, "$provider chosen", "$provider انتخاب شد")
        }
    }

    fun saveKey(key: String, value: String) {
        if (value.isBlank()) return
        act(key) { toolset ->
            setup.saveKey(toolset, key, value.trim())
            tForContext(context, "$key saved", "$key ذخیره شد")
        }
    }

    fun clearKey(key: String) = act(key) {
        setup.clearKey(key)
        tForContext(context, "$key removed", "$key پاک شد")
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    /** Runs one change, then reads the toolset back so badges and "set" marks match the server. */
    private fun act(busy: String, block: suspend (toolset: String) -> String) {
        val toolset = _uiState.value.toolset
        if (toolset.isEmpty() || _uiState.value.busy != null) return
        _uiState.update { it.copy(busy = busy, message = null, error = null) }
        viewModelScope.launch {
            try {
                val message = block(toolset)
                reload(toolset)
                _uiState.update { it.copy(busy = null, message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Toolsets] change on $toolset failed")
                _uiState.update { it.copy(busy = null, error = e.message) }
            }
        }
    }
}
