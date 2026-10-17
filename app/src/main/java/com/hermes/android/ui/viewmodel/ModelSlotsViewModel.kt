package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.config.AuxiliaryModels
import com.hermes.android.domain.config.ModelSlots
import com.hermes.android.domain.config.MoaConfig
import com.hermes.android.domain.config.MoaPreset
import com.hermes.android.domain.config.MoaSlot
import com.hermes.android.domain.config.SlotSave
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

/**
 * Settings › Models › Task models and Mixture of Agents: which model each auxiliary task
 * uses, and the MoA presets' reference models and aggregator. Edits to a preset stay
 * local until Save; a task's model is saved as soon as it is picked.
 */
@HiltViewModel
class ModelSlotsViewModel @Inject constructor(
    private val slots: ModelSlots,
) : ViewModel() {

    /** A model pick waiting for the user to accept Hermes' cost warning. */
    data class ExpensiveConfirm(val task: String, val model: ModelOption, val message: String)

    data class UiState(
        val auxiliary: AuxiliaryModels? = null,
        val auxLoading: Boolean = false,
        val auxError: String? = null,
        /** The task being saved. */
        val savingTask: String? = null,
        val confirm: ExpensiveConfirm? = null,
        val moa: MoaConfig? = null,
        /** The preset as edited; differs from [moa] until saved. */
        val moaDraft: MoaConfig? = null,
        val moaLoading: Boolean = false,
        val moaError: String? = null,
        val savingMoa: Boolean = false,
    ) {
        val moaDirty: Boolean get() = moaDraft != null && moaDraft != moa
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun loadAuxiliary() {
        _uiState.update { it.copy(auxLoading = true, auxError = null) }
        viewModelScope.launch {
            try {
                val aux = slots.auxiliary()
                _uiState.update { it.copy(auxiliary = aux, auxLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ModelSlots] auxiliary failed")
                _uiState.update { it.copy(auxLoading = false, auxError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** [model] null = back to Hermes' automatic choice. */
    fun setTaskModel(task: String, model: ModelOption?, confirmed: Boolean = false) {
        if (_uiState.value.savingTask != null) return
        _uiState.update { it.copy(savingTask = task, confirm = null) }
        viewModelScope.launch {
            try {
                val result = slots.setAuxiliary(task, model?.provider ?: "auto", model?.modelId.orEmpty(), confirmed)
                when (result) {
                    is SlotSave.ConfirmExpensive -> if (model != null) {
                        _uiState.update { it.copy(confirm = ExpensiveConfirm(task, model, result.message)) }
                    }
                    SlotSave.Saved -> {
                        val aux = slots.auxiliary()
                        _uiState.update { it.copy(auxiliary = aux) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ModelSlots] set %s failed", task)
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(savingTask = null) }
            }
        }
    }

    fun confirmExpensive() {
        val confirm = _uiState.value.confirm ?: return
        setTaskModel(confirm.task, confirm.model, confirmed = true)
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirm = null) }
    }

    fun resetAuxiliary() {
        if (_uiState.value.savingTask != null) return
        _uiState.update { it.copy(savingTask = RESET) }
        viewModelScope.launch {
            try {
                slots.resetAuxiliary()
                val aux = slots.auxiliary()
                _uiState.update { it.copy(auxiliary = aux) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ModelSlots] reset failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(savingTask = null) }
            }
        }
    }

    fun loadMoa() {
        _uiState.update { it.copy(moaLoading = true, moaError = null) }
        viewModelScope.launch {
            try {
                val moa = slots.moa()
                _uiState.update { it.copy(moa = moa, moaDraft = moa, moaLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ModelSlots] moa failed")
                _uiState.update { it.copy(moaLoading = false, moaError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setDefaultPreset(name: String) = editMoa { it.copy(defaultPreset = name) }

    fun setPresetEnabled(preset: String, enabled: Boolean) = editPreset(preset) { it.copy(enabled = enabled) }

    fun setFanout(preset: String, fanout: String) = editPreset(preset) { it.copy(fanout = fanout) }

    fun addReference(preset: String, model: ModelOption) = editPreset(preset) { p ->
        if (p.referenceModels.any { it.provider == model.provider && it.model == model.modelId }) p
        else p.copy(referenceModels = p.referenceModels + MoaSlot(model.provider, model.modelId))
    }

    fun replaceReference(preset: String, index: Int, model: ModelOption) = editPreset(preset) { p ->
        p.copy(
            referenceModels = p.referenceModels.mapIndexed { i, slot ->
                if (i == index) slot.copy(provider = model.provider, model = model.modelId, reasoningEffort = null) else slot
            },
        )
    }

    fun setReferenceEnabled(preset: String, index: Int, enabled: Boolean) = editPreset(preset) { p ->
        p.copy(referenceModels = p.referenceModels.mapIndexed { i, slot -> if (i == index) slot.copy(enabled = enabled) else slot })
    }

    fun removeReference(preset: String, index: Int) = editPreset(preset) { p ->
        p.copy(referenceModels = p.referenceModels.filterIndexed { i, _ -> i != index })
    }

    fun setAggregator(preset: String, model: ModelOption) = editPreset(preset) { p ->
        p.copy(aggregator = MoaSlot(model.provider, model.modelId))
    }

    fun discardMoa() {
        _uiState.update { it.copy(moaDraft = it.moa) }
    }

    fun saveMoa() {
        val draft = _uiState.value.moaDraft ?: return
        if (_uiState.value.savingMoa) return
        _uiState.update { it.copy(savingMoa = true) }
        viewModelScope.launch {
            try {
                val saved = slots.saveMoa(draft)
                _uiState.update { it.copy(moa = saved, moaDraft = saved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ModelSlots] moa save failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(savingMoa = false) }
            }
        }
    }

    private fun editMoa(change: (MoaConfig) -> MoaConfig) {
        _uiState.update { state -> state.copy(moaDraft = state.moaDraft?.let(change)) }
    }

    private fun editPreset(name: String, change: (MoaPreset) -> MoaPreset) = editMoa { config ->
        val preset = config.presets[name] ?: return@editMoa config
        config.copy(presets = config.presets + (name to change(preset)))
    }

    companion object {
        /** [UiState.savingTask] while every task is being reset. */
        const val RESET = "__reset__"
    }
}
