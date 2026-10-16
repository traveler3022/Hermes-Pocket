package com.hermes.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.R
import com.hermes.android.domain.config.ConfigEditor
import com.hermes.android.domain.config.ConfigForm
import com.hermes.android.domain.config.RawConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ConfigEditorUiState(
    val raw: Boolean = false,
    val form: ConfigForm? = null,
    /** Form edits not saved yet: dotted key → text. */
    val edits: Map<String, String> = emptyMap(),
    val query: String = "",
    val rawConfig: RawConfig? = null,
    val rawDraft: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val rawChanged: Boolean get() = rawConfig != null && rawDraft != rawConfig.yaml
}

/** Settings › Advanced › config.yaml: the schema form or the raw file, as the dashboard's Config page. */
@HiltViewModel
class ConfigEditorViewModel @Inject constructor(
    private val editor: ConfigEditor,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigEditorUiState())
    val uiState: StateFlow<ConfigEditorUiState> = _uiState.asStateFlow()

    fun load() = loadMode(_uiState.value.raw)

    /** Switching mode reads the file again, so the other mode's saved changes show. */
    fun setRaw(raw: Boolean) {
        if (raw == _uiState.value.raw) return
        _uiState.update { it.copy(raw = raw) }
        loadMode(raw)
    }

    private fun loadMode(raw: Boolean) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                if (raw) {
                    val file = editor.raw()
                    _uiState.update { if (it.raw) it.copy(rawConfig = file, rawDraft = file.yaml, isLoading = false) else it }
                } else {
                    val form = editor.form()
                    _uiState.update { if (!it.raw) it.copy(form = form, edits = emptyMap(), isLoading = false) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ConfigEditor] load failed")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun edit(key: String, text: String) = _uiState.update { state ->
        val original = state.form?.fields?.firstOrNull { it.key == key }?.value
        state.copy(edits = if (text == original) state.edits - key else state.edits + (key to text))
    }

    fun discardEdits() = _uiState.update { it.copy(edits = emptyMap()) }

    fun setRawDraft(text: String) = _uiState.update { it.copy(rawDraft = text) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                if (state.raw) {
                    editor.saveRaw(state.rawDraft)
                    val file = editor.raw()
                    _uiState.update { it.copy(rawConfig = file, rawDraft = file.yaml) }
                } else {
                    val form = state.form ?: return@launch
                    editor.saveFields(form.fields, state.edits)
                    val fresh = editor.form()
                    _uiState.update { it.copy(form = fresh, edits = emptyMap()) }
                }
                _uiState.update { it.copy(isSaving = false, message = context.getString(R.string.config_saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[ConfigEditor] save failed")
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } finally {
                _uiState.update { if (it.isSaving) it.copy(isSaving = false) else it }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(message = null, error = null) }
}
