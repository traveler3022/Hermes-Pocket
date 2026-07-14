package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.ProjectsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Read-only project browser (projects.tree / projects.project_sessions / project.facts). */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectsRepository,
) : ViewModel() {

    data class ProjectsUiState(
        val projects: List<ProjectsRepository.ProjectSummary> = emptyList(),
        val isLoading: Boolean = false,
        val selectedProject: ProjectsRepository.ProjectDetail? = null,
        val isLoadingDetail: Boolean = false,
        val facts: String? = null,
        val error: String? = null,
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
        _uiState.value = _uiState.value.copy(selectedProject = null, facts = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
