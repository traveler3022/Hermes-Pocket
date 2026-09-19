package com.hermes.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModelPickerUiState(
    val models: List<ModelOption> = emptyList(),
    val activeModel: String? = null,
    val activeProvider: String? = null,
    val isLoading: Boolean = false,
    // Starred models pinned atop the sheet (local prefs, max ModelFavorites.MAX).
    val favorites: List<ModelFavorite> = emptyList(),
    val confirm: ModelSwitchConfirm? = null,
    // One-shot switch feedback, shown as a snackbar.
    val notice: String? = null,
)

/**
 * The chat composer's model chip and sheet: the catalogue, the open chat's
 * running model, starred models, and switching the chat's model.
 */
@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    private val modelSwitcher: ModelSwitcher,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelPickerUiState())
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    // Same prefs file as the other client-side chat settings.
    private val prefs = context.getSharedPreferences("hermes_chat_prefs", Context.MODE_PRIVATE)

    /** Live id + pick of a switch the gateway queued for the next turn (`deferred`). */
    private var pendingSwitch: Pair<String, ModelOption>? = null

    init {
        _uiState.update { it.copy(favorites = ModelFavorites.decode(prefs.getString(KEY_FAVORITES, null))) }
        load(null)
    }

    /** [liveSessionId]: the open chat — its running model is reported instead of config.yaml's default. */
    fun load(liveSessionId: String?) {
        val sid = liveSessionId?.takeIf { it.isNotBlank() }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val catalog = modelSwitcher.loadCatalog(sid)
                _uiState.update { state ->
                    // A queued mid-turn pick isn't on the agent yet, and config.yaml's
                    // default must not replace a chat model already known.
                    val adopt = if (sid != null) pendingSwitch?.first != sid else state.activeModel == null
                    state.copy(
                        models = catalog.models,
                        activeModel = catalog.model.takeIf { adopt } ?: state.activeModel,
                        activeProvider = catalog.provider.takeIf { adopt } ?: state.activeProvider,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "[ModelPicker] Failed to load models")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** [confirmed]: re-send of a pick the user confirmed ([confirmSwitch]). */
    fun select(model: ModelOption, liveSessionId: String?, storedSessionId: String?, confirmed: Boolean = false) {
        val live = liveSessionId?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val outcome = try {
                modelSwitcher.switch(model.provider, model.modelId, live, storedSessionId, confirmed)
            } catch (e: Exception) {
                Timber.e(e, "[ModelPicker] Failed to select model")
                ModelSwitchOutcome.Failed("Failed to select model: ${e.message}")
            }
            when (outcome) {
                is ModelSwitchOutcome.Failed -> _uiState.update { it.copy(notice = outcome.message) }
                is ModelSwitchOutcome.NeedsConfirm -> _uiState.update {
                    it.copy(confirm = ModelSwitchConfirm(model, live, storedSessionId, outcome.message))
                }
                is ModelSwitchOutcome.Applied -> {
                    pendingSwitch = if (outcome.deferred && live != null) live to model else null
                    _uiState.update {
                        it.copy(
                            activeModel = model.modelId,
                            activeProvider = model.provider,
                            notice = if (outcome.deferred) {
                                "Model changes to ${model.modelId} on your next message"
                            } else {
                                "Model set to ${model.modelId}"
                            },
                        )
                    }
                    // Queued mid-turn: refetching now would repaint the model still
                    // running. session.info re-syncs once it lands.
                    if (!outcome.deferred) load(live)
                }
            }
        }
    }

    fun confirmSwitch() {
        val confirm = _uiState.value.confirm ?: return
        _uiState.update { it.copy(confirm = null) }
        select(confirm.model, confirm.liveSessionId, confirm.storedSessionId, confirmed = true)
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirm = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** Star/unstar a model (no-op when starring past the cap). */
    fun toggleFavorite(model: ModelOption) {
        val updated = ModelFavorites.toggle(_uiState.value.favorites, ModelFavorite(model.provider, model.modelId))
        prefs.edit().putString(KEY_FAVORITES, ModelFavorites.encode(updated)).apply()
        _uiState.update { it.copy(favorites = updated) }
    }

    /**
     * Adopt the open chat's model from its session.info — the gateway's source
     * of truth, which also reports a queued pick while it is pending.
     */
    fun onSessionModel(liveSessionId: String?, model: String, provider: String?) {
        if (liveSessionId.isNullOrBlank() || model.isBlank()) return
        if (pendingSwitch?.first == liveSessionId) pendingSwitch = null
        _uiState.update {
            it.copy(activeModel = model, activeProvider = provider?.takeIf { p -> p.isNotBlank() } ?: it.activeProvider)
        }
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_models"
    }
}
