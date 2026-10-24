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
        /** The user's archived projects; `projects.tree` leaves them out. */
        val archived: List<Projects.StoredProject> = emptyList(),
        /** The open project's folders, when it is one the user made (auto projects have none to edit). */
        val selectedStored: Projects.StoredProject? = null,
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
                val projects = repository.projectTree()
                // An older server without projects.list still shows the tree.
                val archived = runCatching { repository.storedProjects().filter { it.archived } }
                    .onFailure { Timber.w(it, "[Projects] projects.list failed") }
                    .getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    archived = archived,
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
                val stored = if (!project.isStored) null else runCatching { repository.storedProject(project.id) }
                    .onFailure { Timber.w(it, "[Projects] projects.get failed") }
                    .getOrNull()
                _uiState.value = _uiState.value.copy(
                    selectedProject = detail,
                    selectedStored = stored,
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
        _uiState.value = _uiState.value.copy(
            selectedProject = null, selectedProjectPath = null, facts = null, selectedStored = null,
        )
    }

    /** A new project named [name] that works in [folder] on the server. */
    fun createProject(name: String, folder: String) = mutate("createProject") {
        repository.createProject(name.trim(), folder.trim())
        load()
    }

    fun renameProject(id: String, name: String) = mutate("renameProject") {
        repository.renameProject(id, name.trim())
        load()
    }

    /** Archives a project (it leaves the list and its chats fall back to automatic grouping), or restores it. */
    fun archiveProject(id: String, restore: Boolean) = mutate("archiveProject") {
        repository.archiveProject(id, restore)
        load()
    }

    fun addFolder(path: String) {
        val id = _uiState.value.selectedStored?.id ?: return
        mutate("addFolder") { applyStored(repository.addFolder(id, path.trim())) }
    }

    fun removeFolder(path: String) {
        val id = _uiState.value.selectedStored?.id ?: return
        mutate("removeFolder") { applyStored(repository.removeFolder(id, path)) }
    }

    /** Shows the server's answer for the open project; its primary folder is where new chats start. */
    private fun applyStored(stored: Projects.StoredProject?) {
        if (stored == null || stored.id != _uiState.value.selectedStored?.id) return
        _uiState.value = _uiState.value.copy(selectedStored = stored, selectedProjectPath = stored.primaryPath)
    }

    private fun mutate(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.w(e, "[Projects] $what failed")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
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
