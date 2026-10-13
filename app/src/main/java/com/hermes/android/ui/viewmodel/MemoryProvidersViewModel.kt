package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.memory.MemoryProviders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Settings › Memory: the external memory provider and resetting the built-in memory. */
@HiltViewModel
class MemoryProvidersViewModel @Inject constructor(
    private val memory: MemoryProviders,
) : ViewModel() {

    data class UiState(
        val status: MemoryProviders.Status? = null,
        val isLoading: Boolean = true,
        val loadError: String? = null,
        /** The provider whose settings are open. */
        val editing: String? = null,
        val fields: List<MemoryProviders.ConfigField> = emptyList(),
        val values: Map<String, String> = emptyMap(),
        val isLoadingFields: Boolean = false,
        /** Saving, installing, turning off or resetting: one at a time. */
        val busy: Busy? = null,
        val install: MemoryProviders.InstallResult? = null,
        /** The last action's outcome, in the panel (null: nothing to say). */
        val notice: Notice? = null,
    )

    enum class Busy { SAVING, INSTALLING, TURNING_OFF, RESETTING }

    sealed interface Notice {
        data object Saved : Notice
        data object TurnedOff : Notice
        data object Reset : Notice
        data class Failed(val reason: String) : Notice
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            try {
                val status = memory.status()
                _uiState.update { it.copy(status = status, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Memory] status failed")
                _uiState.update { it.copy(isLoading = false, loadError = e.reason()) }
            }
        }
    }

    /** Opens [name]'s settings (null closes them). */
    fun edit(name: String?) {
        _uiState.update {
            it.copy(editing = name, fields = emptyList(), values = emptyMap(), install = null, notice = null)
        }
        if (name == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFields = true) }
            try {
                val fields = memory.config(name)
                if (_uiState.value.editing != name) return@launch
                _uiState.update { state ->
                    state.copy(
                        fields = fields,
                        values = fields.associate { it.key to it.value },
                        isLoadingFields = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Memory] config of $name failed")
                _uiState.update { it.copy(isLoadingFields = false, notice = Notice.Failed(e.reason())) }
            }
        }
    }

    fun setValue(key: String, value: String) {
        _uiState.update { it.copy(values = it.values + (key to value)) }
    }

    fun saveAndActivate() {
        val name = _uiState.value.editing ?: return
        perform(Busy.SAVING, Notice.Saved) {
            memory.saveAndActivate(name, visibleValues())
            _uiState.update { it.copy(editing = null) }
        }
    }

    fun install() {
        val name = _uiState.value.editing ?: return
        perform(Busy.INSTALLING, notice = null) {
            val result = memory.install(name, visibleValues())
            _uiState.update { it.copy(install = result) }
        }
    }

    fun turnOff() = perform(Busy.TURNING_OFF, Notice.TurnedOff) { memory.turnOff() }

    /** [target]: "all", "memory" or "user". */
    fun resetBuiltIn(target: String) = perform(Busy.RESETTING, Notice.Reset) { memory.resetBuiltIn(target) }

    /** Field visible under its `when` rule, as the dashboard sends only those. Blank secrets keep the saved one. */
    private fun visibleValues(): Map<String, String> {
        val state = _uiState.value
        return state.fields
            .filter { field -> field.showWhen.all { (k, v) -> state.values[k].orEmpty() == v } }
            .associate { it.key to state.values[it.key].orEmpty() }
            .filter { (key, value) ->
                value.isNotBlank() || state.fields.firstOrNull { it.key == key }?.kind != MemoryProviders.FieldKind.SECRET
            }
    }

    private fun perform(busy: Busy, notice: Notice?, action: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = busy, notice = null) }
            try {
                action()
                _uiState.update { it.copy(busy = null, notice = notice) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Memory] $busy failed")
                _uiState.update { it.copy(busy = null, notice = Notice.Failed(e.reason())) }
                load()
            }
        }
    }

    private fun Exception.reason(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
