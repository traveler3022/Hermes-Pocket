package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.GroupsRepository
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

/** The list of Group Chats and making a new one. */
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groups: GroupsRepository,
    private val profiles: ProfilesRepository,
) : ViewModel() {

    data class UiState(
        val rooms: List<GroupsRepository.Room> = emptyList(),
        val isLoading: Boolean = true,
        /** False when this gateway does not drive rooms; null until asked. */
        val available: Boolean? = null,
        /** Profiles a new group can seat, loaded when the create sheet opens. */
        val profiles: List<ProfilesRepository.Profile> = emptyList(),
        val isCreating: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val available = groups.available()
                val rooms = if (available) groups.list() else emptyList()
                _uiState.update { it.copy(rooms = rooms, available = available, isLoading = false) }
            } catch (e: Exception) {
                Timber.w(e, "[Groups] load failed")
                _uiState.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(profiles = profiles.list()) }
            } catch (e: Exception) {
                Timber.w(e, "[Groups] profiles failed")
                _uiState.update { it.copy(error = e.userMessage()) }
            }
        }
    }

    /** Creates the group and hands its id to [onCreated] (the screen opens it). */
    fun create(name: String, members: List<ProfilesRepository.Profile>, onCreated: (String) -> Unit) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val room = groups.create(name, members)
                _uiState.update { it.copy(isCreating = false, rooms = listOf(room) + it.rooms) }
                onCreated(room.roomId)
            } catch (e: Exception) {
                Timber.w(e, "[Groups] create failed")
                _uiState.update { it.copy(isCreating = false, error = e.userMessage()) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun Exception.userMessage(): String =
        (this as? GatewayException)?.rpcMessage ?: message ?: toString()
}
