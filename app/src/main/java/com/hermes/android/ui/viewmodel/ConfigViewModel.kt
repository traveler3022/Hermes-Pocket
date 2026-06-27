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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Configuration screen.
 *
 * Depends ONLY on [GatewayClient] interface — never on any concrete
 * runtime or gateway implementation.
 *
 * Responsibilities:
 * - Load current config via `config.show` RPC
 * - Load available models via `model.options` RPC
 * - Load available tools via `tools.list` RPC
 * - Save config changes via `config.set` RPC
 * - Save API keys via `model.save_key` RPC
 *
 * Reference: Phase 1.5 Rule 1, Rule 2 (orchestrator only)
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        loadConfig()
        loadModels()
        loadTools()
    }

    // ── Config ────────────────────────────────────────────────────────────

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingConfig = true)
            try {
                val result = gatewayClient.request(GatewayMethods.CONFIG_SHOW)
                // Fix S5F01: config.show returns {sections: [{title, rows: [[label, value]]}]}
                val configText = parseConfigSections(result)
                _uiState.value = _uiState.value.copy(
                    configYaml = configText,
                    isLoadingConfig = false,
                )
                Timber.i("[Config] Config loaded")
            } catch (e: GatewayException) {
                Timber.e(e, "[Config] Failed to load config")
                _uiState.value = _uiState.value.copy(
                    isLoadingConfig = false,
                    errorMessage = "Failed to load config: ${e.message}",
                )
            }
        }
    }

    private fun parseConfigSections(result: JsonElement): String {
        return try {
            val obj = result as? JsonObject ?: return "(empty)"
            val sections = obj["sections"] as? kotlinx.serialization.json.JsonArray ?: return "(empty)"
            buildString {
                for (sectionEl in sections) {
                    val section = sectionEl as? JsonObject ?: continue
                    val title = section["title"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    appendLine("## $title")
                    val rows = section["rows"] as? kotlinx.serialization.json.JsonArray ?: continue
                    for (rowEl in rows) {
                        val row = rowEl as? kotlinx.serialization.json.JsonArray ?: continue
                        val label = row.getOrNull(0)?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val value = row.getOrNull(1)?.let { (it as? JsonPrimitive)?.content } ?: ""
                        appendLine("  $label: $value")
                    }
                    appendLine()
                }
            }
        } catch (e: Exception) {
            "(parse error: ${e.message})"
        }
    }

    fun saveConfig(key: String, value: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("key", key)
                    put("value", value)
                }
                gatewayClient.request(GatewayMethods.CONFIG_SET, params.toMap())
                Timber.i("[Config] Saved: $key=$value")
                loadConfig()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save config")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save: ${e.message}",
                )
            }
        }
    }

    // ── Models ────────────────────────────────────────────────────────────

    fun loadModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true)
            try {
                val result = gatewayClient.request(GatewayMethods.MODEL_OPTIONS)
                // Parse model list from result
                val models = parseModelOptions(result)
                _uiState.value = _uiState.value.copy(
                    availableModels = models,
                    isLoadingModels = false,
                )
                Timber.i("[Config] Models loaded: ${models.size}")
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load models")
                _uiState.value = _uiState.value.copy(isLoadingModels = false)
            }
        }
    }

    private fun parseModelOptions(result: JsonElement): List<ModelOption> {
        return try {
            // Fix F01: build_models_payload (inventory.py:222-226) returns:
            //   {providers: [rows], model: str, provider: str}
            // Each row (model_switch.py:1401-1407) has:
            //   slug, name, is_current, is_user_defined, models: List[str], total_models, source
            // models is a List[str] of model IDs — NOT a list of objects.
            // picker_hints adds: authenticated, auth_type, key_env, warning
            val obj = result as? JsonObject ?: return emptyList()
            val providersArr = obj["providers"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            providersArr.flatMap { providerEl ->
                val providerObj = providerEl as? JsonObject ?: return@flatMap emptyList()
                val slug = providerObj["slug"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                val models = providerObj["models"] as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
                models.mapNotNull { modelEl ->
                    val modelId = (modelEl as? JsonPrimitive)?.content ?: return@mapNotNull null
                    ModelOption(
                        provider = slug,
                        modelId = modelId,
                        name = modelId,
                        requiresApiKey = providerObj["authenticated"]?.let { (it as? JsonPrimitive)?.content } == "false",
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Config] Failed to parse model options")
            emptyList()
        }
    }

    fun saveApiKey(provider: String, apiKey: String) {
        viewModelScope.launch {
            try {
                // Fix S5F04: model.save_key uses "slug" param, not "provider"
                val params = buildJsonObject {
                    put("slug", provider)
                    put("api_key", apiKey)
                }
                gatewayClient.request(GatewayMethods.MODEL_SAVE_KEY, params.toMap())
                Timber.i("[Config] API key saved for $provider")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "API key saved for $provider",
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save API key")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save API key: ${e.message}",
                )
            }
        }
    }

    // ── Tools ─────────────────────────────────────────────────────────────

    fun loadTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTools = true)
            try {
                val result = gatewayClient.request(GatewayMethods.TOOLS_LIST)
                val tools = parseToolList(result)
                _uiState.value = _uiState.value.copy(
                    availableTools = tools,
                    isLoadingTools = false,
                )
                Timber.i("[Config] Tools loaded: ${tools.size}")
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load tools")
                _uiState.value = _uiState.value.copy(isLoadingTools = false)
            }
        }
    }

    private fun parseToolList(result: JsonElement): List<ToolOption> {
        return try {
            // Fix S5F02: tools.list returns {toolsets: [{name, description, tool_count, enabled, tools}]}
            val obj = result as? JsonObject ?: return emptyList()
            val toolsets = obj["toolsets"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            toolsets.mapNotNull { tsEl ->
                val ts = tsEl as? JsonObject ?: return@mapNotNull null
                ToolOption(
                    name = ts["name"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    description = ts["description"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    enabled = ts["enabled"]?.let { (it as? JsonPrimitive)?.content } != "false",
                    toolset = null,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggleTool(toolName: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                // Fix S5F03: tools.configure params: {action: "enable"/"disable", names: [...]}
                val params = buildJsonObject {
                    put("action", if (enabled) "enable" else "disable")
                    put("names", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(toolName))))
                }
                gatewayClient.request(GatewayMethods.TOOLS_CONFIGURE, params.toMap())
                Timber.i("[Config] Tool $toolName -> $enabled")
                // Update local state immediately
                _uiState.value = _uiState.value.copy(
                    availableTools = _uiState.value.availableTools.map {
                        if (it.name == toolName) it.copy(enabled = enabled) else it
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to toggle tool")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to toggle tool: ${e.message}",
                )
            }
        }
    }

    // ── UI actions ────────────────────────────────────────────────────────

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun selectTab(tab: ConfigTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}

// ── UI State models ──────────────────────────────────────────────────────

data class ConfigUiState(
    val selectedTab: ConfigTab = ConfigTab.GENERAL,
    val configYaml: String = "",
    val isLoadingConfig: Boolean = false,
    val availableModels: List<ModelOption> = emptyList(),
    val isLoadingModels: Boolean = false,
    val availableTools: List<ToolOption> = emptyList(),
    val isLoadingTools: Boolean = false,
    val errorMessage: String? = null,
)

enum class ConfigTab(val label: String) {
    GENERAL("General"),
    MODELS("Models"),
    TOOLS("Tools"),
}

data class ModelOption(
    val provider: String,
    val modelId: String,
    val name: String,
    val requiresApiKey: Boolean,
)

data class ToolOption(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val toolset: String?,
)
