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

    /**
     * Search the skill registry ("search" — one of the official skills.manage
     * actions per server.py:12224, alongside list/install/browse/inspect).
     * Only "list" and "install" were ever wired here, so there was no way to
     * find a skill you didn't already know the exact name of. Empty query
     * falls back to the locally-known list.
     */
    fun searchSkills(query: String) {
        if (query.isBlank()) {
            loadSkills()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val params = buildJsonObject {
                    put("action", "search")
                    put("query", query)
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                val skills = parseSkills(result)
                _uiState.value = _uiState.value.copy(
                    skills = skills,
                    isLoading = false,
                )
                Timber.i("[Skills] Search '$query' found ${skills.size}")
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Search failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Search failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Fetch skill detail text ("inspect" action) for the detail dialog.
     */
    fun inspectSkill(skillName: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "inspect")
                    put("query", skillName)
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                val obj = result as? JsonObject
                val detail = (obj?.get("detail") ?: obj?.get("description") ?: obj?.get("content"))
                    as? JsonPrimitive
                _uiState.value = _uiState.value.copy(
                    inspectedSkillName = skillName,
                    inspectedSkillDetail = detail?.content ?: "(no details returned)",
                )
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Inspect failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Inspect failed: ${e.message}",
                )
            }
        }
    }

    fun dismissInspect() {
        _uiState.value = _uiState.value.copy(inspectedSkillName = null, inspectedSkillDetail = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class SkillsUiState(
    val skills: List<SkillItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val inspectedSkillName: String? = null,
    val inspectedSkillDetail: String? = null,
)

data class SkillItem(
    val name: String,
    val category: String,
)
