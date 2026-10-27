package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.vault.CredentialVault
import com.hermes.android.domain.vault.VaultItem
import com.hermes.android.domain.vault.VaultKind
import com.hermes.android.domain.vault.VaultSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Settings › Credential vault, as the desktop's panel: the saved items (add, delete local
 * ones) and the password managers (on/off, unlock, lock). Secrets typed here are handed to
 * the call and not kept in the state.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vault: CredentialVault,
) : ViewModel() {

    data class UiState(
        val items: List<VaultItem> = emptyList(),
        /** Password managers only; the local vault is always on. */
        val managers: List<VaultSource> = emptyList(),
        val loading: Boolean = true,
        val loadError: String? = null,
        /** A call is running (add, unlock, switch…): its buttons wait. */
        val busy: Boolean = false,
        /** Shown in the open dialog (add / unlock) when the server refused. */
        val dialogError: String? = null,
        /** Snackbar for a failure outside a dialog. */
        val message: String? = null,
        /** Closes the open dialog after a successful add / unlock. */
        val dialogDone: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                // coroutineScope, so a failed call is thrown here and caught below; a bare
                // async would fail the launch itself and crash the app (gateway disconnected).
                val (sources, items) = coroutineScope {
                    val sources = async { vault.sources() }
                    val items = async { vault.items() }
                    sources.await() to items.await()
                }
                _uiState.update {
                    it.copy(
                        items = items,
                        managers = sources.filter { s -> s.needsUnlock },
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Vault] load failed")
                _uiState.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun add(kind: VaultKind, label: String, origin: String?, secret: Map<String, String>) =
        run(inDialog = true) { vault.add(kind, label, origin, secret) }

    fun remove(item: VaultItem) = run(inDialog = false) { vault.remove(item.id) }

    fun setManagerEnabled(source: VaultSource, enabled: Boolean) =
        run(inDialog = false) { vault.setSourceEnabled(source.name, enabled) }

    fun unlock(source: VaultSource, masterPassword: String) =
        run(inDialog = true) { vault.unlock(source.name, masterPassword) }

    fun lock(source: VaultSource) = run(inDialog = false) { vault.lock(source.name) }

    fun clearDialogError() = _uiState.update { it.copy(dialogError = null) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun run(inDialog: Boolean, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, dialogError = null) }
        viewModelScope.launch {
            try {
                block()
                _uiState.update { it.copy(busy = false, dialogDone = if (inDialog) it.dialogDone + 1 else it.dialogDone) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Vault] call failed")
                val text = e.message ?: e.javaClass.simpleName
                _uiState.update {
                    if (inDialog) it.copy(busy = false, dialogError = text) else it.copy(busy = false, message = text)
                }
            }
        }
    }
}
