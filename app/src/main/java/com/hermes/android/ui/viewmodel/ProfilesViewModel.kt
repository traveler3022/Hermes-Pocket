package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.ProfilesRepository
import com.hermes.android.gateway.GatewayException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The profile picker: which agent the app talks to, and making a new one. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfilesRepository,
) : ViewModel() {

    data class UiState(
        val profiles: List<ProfilesRepository.Profile> = emptyList(),
        val isLoading: Boolean = false,
        val isCreating: Boolean = false,
        val error: String? = null,
        /** The editor's snapshot; null while it loads or when no editor is open. */
        val editing: ProfilesRepository.Details? = null,
        val isLoadingEditor: Boolean = false,
        /** Saving, renaming or deleting: the editor's buttons wait. */
        val isBusy: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Selected profile; null is the gateway's own (shown as the default row). */
    val active: StateFlow<String?> = repository.active

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val profiles = repository.list()
                _uiState.update { it.copy(profiles = profiles, isLoading = false) }
            } catch (e: Exception) {
                Timber.w(e, "[Profiles] list failed")
                _uiState.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        }
    }

    fun select(profile: ProfilesRepository.Profile) {
        // The default row stands for the gateway's own profile: no param at all.
        repository.select(if (profile.isDefault) null else profile.name)
    }

    /** Creates [name] (a copy of the open profile when [copyCurrent]) and switches to it. */
    fun create(name: String, description: String, copyCurrent: Boolean, onDone: () -> Unit) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val current = active.value ?: _uiState.value.profiles.firstOrNull { it.isDefault }?.name
                repository.create(name, description, copyFrom = current.takeIf { copyCurrent })
                repository.select(name)
                _uiState.update { it.copy(isCreating = false) }
                onDone()
                load()
            } catch (e: Exception) {
                Timber.w(e, "[Profiles] create failed")
                _uiState.update { it.copy(isCreating = false, error = e.userMessage()) }
            }
        }
    }

    fun openEditor(profile: ProfilesRepository.Profile) {
        viewModelScope.launch {
            _uiState.update { it.copy(editing = null, isLoadingEditor = true, error = null) }
            try {
                val details = repository.describe(profile.name)
                _uiState.update { it.copy(editing = details, isLoadingEditor = false) }
            } catch (e: Exception) {
                Timber.w(e, "[Profiles] describe failed")
                _uiState.update { it.copy(isLoadingEditor = false, error = e.userMessage()) }
            }
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editing = null, isLoadingEditor = false, error = null) }

    /**
     * Saves the editor. [newName] differs from the profile's name for a rename; for the
     * default profile it is the shown name (its id stays `default`).
     */
    fun save(name: String, newName: String, description: String, soul: String, onDone: () -> Unit) {
        busy(onDone) {
            repository.save(name, description, soul)
            if (newName.isNotBlank() && newName != name && newName != currentTitle(name)) {
                repository.rename(name, newName)
            }
        }
    }

    fun delete(name: String, onDone: () -> Unit) {
        busy(onDone) { repository.delete(name) }
    }

    private fun currentTitle(name: String): String? =
        _uiState.value.profiles.firstOrNull { it.name == name }?.title

    private fun busy(onDone: () -> Unit, action: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            try {
                action()
                _uiState.update { it.copy(isBusy = false, editing = null) }
                onDone()
                load()
            } catch (e: Exception) {
                Timber.w(e, "[Profiles] edit failed")
                _uiState.update { it.copy(isBusy = false, error = e.userMessage()) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun Exception.userMessage(): String =
        (this as? GatewayException)?.rpcMessage ?: message ?: toString()
}
