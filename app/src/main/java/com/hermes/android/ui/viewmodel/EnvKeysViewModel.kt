package com.hermes.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.R
import com.hermes.android.domain.config.EnvKey
import com.hermes.android.domain.config.EnvKeys
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

data class EnvKeysUiState(
    val keys: List<EnvKey> = emptyList(),
    val isLoading: Boolean = false,
    /** Also list the keys Hermes knows about that have no value yet. */
    val showUnset: Boolean = false,
    val query: String = "",
    /** Values the user asked to see, by key; dropped when the page reloads. */
    val revealed: Map<String, String> = emptyMap(),
    /** The key being revealed, saved or removed. */
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val visible: List<EnvKey>
        get() = keys
            .filter { showUnset || it.isSet }
            .filter { query.isBlank() || it.key.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<EnvKey> { it.isSet }.thenBy { it.key })
}

/** Settings › Advanced › `.env` keys: see, edit, add and remove the server's keys. */
@HiltViewModel
class EnvKeysViewModel @Inject constructor(
    private val envKeys: EnvKeys,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvKeysUiState())
    val uiState: StateFlow<EnvKeysUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val keys = envKeys.list()
                _uiState.update { it.copy(keys = keys, isLoading = false, revealed = emptyMap()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[EnvKeys] list failed")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setShowUnset(show: Boolean) = _uiState.update { it.copy(showUnset = show) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun reveal(key: String) {
        if (_uiState.value.revealed.containsKey(key)) {
            _uiState.update { it.copy(revealed = it.revealed - key) }
            return
        }
        act(key, reload = false) {
            val value = envKeys.reveal(key)
            _uiState.update { it.copy(revealed = it.revealed + (key to value)) }
            null
        }
    }

    fun save(key: String, value: String) {
        val name = key.trim()
        if (!KEY_NAME.matches(name)) {
            _uiState.update { it.copy(error = context.getString(R.string.env_key_bad_name)) }
            return
        }
        if (value.isEmpty()) return
        act(name) {
            envKeys.save(name, value)
            context.getString(R.string.env_key_saved, name)
        }
    }

    fun remove(key: String) = act(key) {
        envKeys.remove(key)
        context.getString(R.string.env_key_removed, key)
    }

    fun clearMessages() = _uiState.update { it.copy(message = null, error = null) }

    /** Runs one change; after a write the list is read back so "set" marks match the server. */
    private fun act(key: String, reload: Boolean = true, block: suspend () -> String?) {
        if (_uiState.value.busy != null) return
        _uiState.update { it.copy(busy = key, message = null, error = null) }
        viewModelScope.launch {
            try {
                val message = block()
                val keys = if (reload) envKeys.list() else null
                _uiState.update {
                    it.copy(
                        busy = null,
                        message = message,
                        keys = keys ?: it.keys,
                        revealed = if (keys != null) it.revealed - key else it.revealed,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[EnvKeys] change on $key failed")
                _uiState.update { it.copy(busy = null, error = e.message) }
            }
        }
    }

    private companion object {
        // What a shell variable name may be; the server refuses others anyway.
        val KEY_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
