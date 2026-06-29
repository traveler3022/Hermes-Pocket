package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Skills Browser screen.
 *
 * Lists available skills, shows skill details, enables/disables skills.
 *
 * Reference: Phase 1.5 Rule 1, Rule 2
 */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fix S10F01: skills.manage requires {action: "list"} param
                val params = buildJsonObject {
                    put("action", "list")
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                val skills = parseSkills(result)
                _uiState.value = _uiState.value.copy(
                    skills = skills,
                    isLoading = false,
                )
                Timber.i("[Skills] Loaded ${skills.size} skills")
            } catch (e: GatewayException) {
                Timber.e(e, "[Skills] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load skills: ${e.message}",
                )
            }
        }
    }

    private fun parseSkills(result: kotlinx.serialization.json.JsonElement): List<SkillItem> {
        return try {
            // Fix S10F02: Hermes get_available_skills() returns Dict[str, List[str]]
            // = {category: [skill_name, ...]}
            // server.py:12206: return _ok(rid, {"skills": get_available_skills()})
            // banner.py:93: returns skills_by_category: Dict[str, List[str]]
            val obj = result as? JsonObject ?: return emptyList()
            val skillsObj = obj["skills"] as? JsonObject ?: return emptyList()
            // Flatten {category: [names]} into list of SkillItem
            skillsObj.entries.flatMap { (category, namesArr) ->
                val names = namesArr as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
                names.mapNotNull { nameEl ->
                    val name = (nameEl as? JsonPrimitive)?.content ?: return@mapNotNull null
                    SkillItem(
                        name = name,
                        category = category,
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun installSkill(skillName: String) {
        // Fix S10F02: Hermes skills.manage does NOT support enable/disable.
        // Official actions are: list, search, install, browse, inspect.
        // Use "install" for adding a skill (server.py:12224)
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "install")
                    put("query", skillName)
                }
                gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                Timber.i("[Skills] Installed: $skillName")
                loadSkills() // Refresh list
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Install failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to install: ${e.message}",
                )
            }
        }
    }

    fun reloadSkills() {
        viewModelScope.launch {
            try {
                gatewayClient.request(GatewayMethods.SKILLS_RELOAD)
                Timber.i("[Skills] Reloaded")
                loadSkills()
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Reload failed")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class SkillsUiState(
    val skills: List<SkillItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SkillItem(
    val name: String,
    val category: String,
)
