package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.ProjectsRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/** Project browser with session creation support. */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectsRepository,
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    data class ProjectsUiState(
        val projects: List<ProjectsRepository.ProjectSummary> = emptyList(),
        val isLoading: Boolean = false,
        val selectedProject: ProjectsRepository.ProjectDetail? = null,
        val selectedProjectPath: String? = null,
        val isLoadingDetail: Boolean = false,
        val facts: String? = null,
        val error: String? = null,
        val createdSessionId: String? = null,
    )

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value = _uiState.value.copy(
                    projects = repository.projectTree(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Projects] load failed")
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun openProject(project: ProjectsRepository.ProjectSummary) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDetail = true, facts = null)
            try {
                val detail = repository.projectSessions(project.id)
                val facts = project.path.takeIf { it.isNotBlank() }?.let { repository.projectFacts(it) }
                _uiState.value = _uiState.value.copy(
                    selectedProject = detail,
                    selectedProjectPath = project.path.takeIf { it.isNotBlank() },
                    facts = facts,
                    isLoadingDetail = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Projects] openProject failed")
                _uiState.value = _uiState.value.copy(isLoadingDetail = false, error = e.message)
            }
        }
    }

    fun closeProject() {
        _uiState.value = _uiState.value.copy(selectedProject = null, selectedProjectPath = null, facts = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearCreatedSessionId() {
        _uiState.value = _uiState.value.copy(createdSessionId = null)
    }

    /** Create a new session linked to the given project path and expose its id. */
    fun createProjectSession(path: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("cwd", path) }
                val result = gatewayClient.request(
                    GatewayMethods.SESSION_CREATE,
                    params.toMap(),
                )
                val sessionId = (result as? JsonObject)
                    ?.get("session_id")
                    ?.let { (it as? JsonPrimitive)?.content }
                if (!sessionId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(createdSessionId = sessionId)
                    Timber.i("[Projects] Created session $sessionId for project path $path")
                }
            } catch (e: Exception) {
                Timber.w(e, "[Projects] createProjectSession failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
