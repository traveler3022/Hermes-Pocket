package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.DefaultModelResult
import com.hermes.android.data.ProviderSetupRepository
import com.hermes.android.data.SetupProvider
import com.hermes.android.data.SetupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class SetupStep { Welcome, Runtime, Provider, ApiKey, Model }

data class SetupUiState(
    val step: SetupStep = SetupStep.Welcome,
    val providers: List<SetupProvider> = emptyList(),
    val provider: SetupProvider? = null,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val modelQuery: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val confirmModel: String? = null,
    val confirmMessage: String? = null,
    val alreadyConfigured: Boolean = false,
    val finished: Boolean = false,
)

/** First-run setup like Aether's: welcome → runtime → provider → API key → model. */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: ProviderSetupRepository,
    private val setupState: SetupState,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState(providers = repository.providers))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun goTo(step: SetupStep) = _state.update { it.copy(step = step, error = null) }

    fun back() {
        val previous = when (_state.value.step) {
            SetupStep.Welcome, SetupStep.Runtime -> SetupStep.Welcome
            SetupStep.Provider -> SetupStep.Runtime
            SetupStep.ApiKey -> SetupStep.Provider
            SetupStep.Model -> SetupStep.ApiKey
        }
        goTo(previous)
    }

    /** Called once the gateway is running; learns whether a model is already configured. */
    fun onRuntimeReady() {
        goTo(SetupStep.Provider)
        launchBusy {
            val configured = repository.hasConfiguredModel()
            _state.update { it.copy(alreadyConfigured = configured) }
        }
    }

    fun pickProvider(provider: SetupProvider) =
        _state.update { it.copy(provider = provider, apiKey = "", baseUrl = "", step = SetupStep.ApiKey, error = null) }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun setModelQuery(value: String) = _state.update { it.copy(modelQuery = value) }

    fun submitKey() {
        val current = _state.value
        val provider = current.provider ?: return
        val key = current.apiKey.trim()
        val baseUrl = current.baseUrl.trim()
        if (provider.needsBaseUrl && baseUrl.isEmpty()) {
            _state.update { it.copy(error = "Enter the endpoint base URL.") }
            return
        }
        if (!provider.needsBaseUrl && key.isEmpty()) {
            _state.update { it.copy(error = "Paste your API key first.") }
            return
        }
        launchBusy {
            val check = repository.checkKey(provider, key, baseUrl)
            // reachable=false means the probe couldn't run (offline / no probe for this
            // provider); like Hermes' own dashboard, save anyway rather than hard-block.
            if (!check.ok && check.reachable) {
                _state.update { it.copy(error = check.message.ifBlank { "That key was rejected." }) }
                return@launchBusy
            }
            repository.saveKey(provider, key)
            val models = if (provider.needsBaseUrl) check.models else repository.models(provider)
            _state.update { it.copy(models = models, modelQuery = "", step = SetupStep.Model) }
        }
    }

    fun pickModel(model: String, confirmed: Boolean = false) {
        val current = _state.value
        val provider = current.provider ?: return
        if (model.isBlank()) return
        launchBusy {
            when (val result = repository.setDefaultModel(provider, model.trim(), current.apiKey.trim(), current.baseUrl.trim(), confirmed)) {
                DefaultModelResult.Applied -> finish()
                is DefaultModelResult.NeedsConfirm ->
                    _state.update { it.copy(confirmModel = model, confirmMessage = result.message) }
            }
        }
    }

    fun confirmExpensiveModel() {
        val model = _state.value.confirmModel ?: return
        _state.update { it.copy(confirmModel = null, confirmMessage = null) }
        pickModel(model, confirmed = true)
    }

    fun dismissConfirm() = _state.update { it.copy(confirmModel = null, confirmMessage = null) }

    fun finish() {
        setupState.markComplete()
        _state.update { it.copy(finished = true) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Setup] step failed")
                _state.update { it.copy(error = e.message ?: "Something went wrong — is the Hermes gateway running?") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
