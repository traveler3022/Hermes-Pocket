package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.projects.Projects
import com.hermes.android.domain.session.ChatSessions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Project browser with session creation support. */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: Projects,
    private val sessionRepository: ChatSessions,
) : ViewModel() {

    data class ProjectsUiState(
        val projects: List<Projects.ProjectSummary> = emptyList(),
        val isLoading: Boolean = false,
        val selectedProject: Projects.ProjectDetail? = null,
        val selectedProjectPath: String? = null,
        val isLoadingDetail: Boolean = false,
        val facts: String? = null,
        val error: String? = null,
        val createdSessionId: String? = null,
    )

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState = _uiState.asStateFlow()

    /** Outcome of a move-to-project: the project's label, or null when it failed. */
    sealed interface MoveResult {
        data class Moved(val projectLabel: String) : MoveResult
        data class Failed(val message: String?) : MoveResult
    }

    private val _moveResults = MutableSharedFlow<MoveResult>(extraBufferCapacity = 1)
    val moveResults: SharedFlow<MoveResult> = _moveResults.asSharedFlow()

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

    fun openProject(project: Projects.ProjectSummary) {
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
                val sessionId = sessionRepository.createChat(cwd = path)?.liveId
                if (sessionId != null) {
                    _uiState.value = _uiState.value.copy(createdSessionId = sessionId)
                    Timber.i("[Projects] Created session $sessionId for project path $path")
                }
            } catch (e: Exception) {
                Timber.w(e, "[Projects] createProjectSession failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Moves a stored chat into [project]'s folder (`session.workspace.move`), as the desktop's "Move to project". */
    fun moveSession(sessionId: String, project: Projects.ProjectSummary) {
        if (project.path.isBlank()) return
        viewModelScope.launch {
            val result = try {
                repository.moveSession(sessionId, project.path)
                load()
                MoveResult.Moved(project.label)
            } catch (e: Exception) {
                Timber.w(e, "[Projects] moveSession failed")
                MoveResult.Failed(e.message)
            }
            _moveResults.emit(result)
        }
    }
}
