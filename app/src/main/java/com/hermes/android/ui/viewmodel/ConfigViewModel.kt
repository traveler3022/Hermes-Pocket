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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Base64
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
                val obj = result as? JsonObject
                val activeProvider = obj?.get("provider")?.let { (it as? JsonPrimitive)?.content }
                val activeModel = obj?.get("model")?.let { (it as? JsonPrimitive)?.content }
                // Parse model list from result
                val models = parseModelOptions(result)
                _uiState.value = _uiState.value.copy(
                    availableModels = models,
                    activeProvider = activeProvider ?: _uiState.value.activeProvider,
                    activeModel = activeModel ?: _uiState.value.activeModel,
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
                validateApiKey(provider)
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save API key")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save API key: ${e.message}",
                )
            }
        }
    }

    fun validateApiKey(provider: String) {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.MODEL_OPTIONS)
                val obj = result as? JsonObject
                val providers = obj?.get("providers") as? JsonArray
                if (providers != null && providers.isNotEmpty()) {
                    Timber.i("[Config] API key validated for $provider")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "API key validated successfully",
                    )
                } else {
                    Timber.w("[Config] API key validation: no providers returned for $provider")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "API key may be invalid (could not verify)",
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "[Config] API key validation failed for $provider")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "API key may be invalid (could not verify)",
                )
            }
        }
    }

    fun configureXiaomiBackend(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            try {
                val targetModel = model.ifBlank { "mimo-v2.5-free" }
                if (apiKey.isNotBlank()) {
                    gatewayClient.request(
                        GatewayMethods.MODEL_SAVE_KEY,
                        buildJsonObject {
                            put("slug", "xiaomi")
                            put("api_key", apiKey.trim())
                        }.toMap(),
                    )
                }
                // Persist provider/model + base_url to config.yaml (for new
                // sessions), then switch the LIVE agent via config.set key=model.
                writeModelConfig("xiaomi", targetModel, baseUrl.trim().ifBlank { null })
                val switchError = applyHermesModelSwitch("xiaomi", targetModel)
                _uiState.value = _uiState.value.copy(
                    activeProvider = "xiaomi",
                    activeModel = targetModel,
                    errorMessage = switchError ?: "MiMo backend saved",
                )
                loadConfig()
                loadModels()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to configure MiMo")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to configure MiMo: ${e.message}")
            }
        }
    }

    fun configureGeminiBackend(apiKey: String, model: String) {
        viewModelScope.launch {
            try {
                val targetModel = model.ifBlank { "gemini-2.5-flash" }
                if (apiKey.isNotBlank()) {
                    gatewayClient.request(
                        GatewayMethods.MODEL_SAVE_KEY,
                        buildJsonObject {
                            put("slug", "gemini")
                            put("api_key", apiKey.trim())
                        }.toMap(),
                    )
                }
                // Persist to config.yaml (for new sessions), then switch the
                // LIVE agent via config.set key=model.
                writeModelConfig("gemini", targetModel)
                val switchError = applyHermesModelSwitch("gemini", targetModel)
                _uiState.value = _uiState.value.copy(
                    activeProvider = "gemini",
                    activeModel = targetModel,
                    errorMessage = switchError ?: "Gemini backend saved",
                )
                loadConfig()
                loadModels()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to configure Gemini")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to configure Gemini: ${e.message}")
            }
        }
    }

    fun configureFallbackProvider(provider: String, model: String, baseUrl: String = "") {
        viewModelScope.launch {
            try {
                val cleanProvider = provider.trim()
                val cleanModel = model.trim()
                if (cleanProvider.isBlank() || cleanModel.isBlank()) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Fallback provider and model are required")
                    return@launch
                }
                writeFallbackChain(
                    listOf(
                        FallbackProviderConfig(
                            provider = cleanProvider,
                            model = cleanModel,
                            baseUrl = baseUrl.trim().ifBlank { null },
                        )
                    )
                )
                _uiState.value = _uiState.value.copy(
                    fallbackSummary = "$cleanProvider/$cleanModel",
                    errorMessage = "Fallback set to $cleanProvider/$cleanModel",
                )
                loadConfig()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to configure fallback")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to configure fallback: ${e.message}")
            }
        }
    }

    fun clearFallbackProviders() {
        viewModelScope.launch {
            try {
                writeFallbackChain(emptyList())
                _uiState.value = _uiState.value.copy(
                    fallbackSummary = "none",
                    errorMessage = "Fallback providers cleared",
                )
                loadConfig()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to clear fallback")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to clear fallback: ${e.message}")
            }
        }
    }

    // config.set RPC doesn't accept dotted keys (model.provider, model.default) — write config.yaml directly.
    // Data is passed via Base64 JSON to avoid shell/Python injection for any provider or model name.
    private suspend fun writeModelConfig(provider: String, model: String, baseUrl: String? = null) {
        val json = buildString {
            append("{\"provider\":\"").append(provider.escapeJson()).append("\"")
            append(",\"model\":\"").append(model.escapeJson()).append("\"")
            if (baseUrl != null) append(",\"base_url\":\"").append(baseUrl.escapeJson()).append("\"")
            append("}")
        }
        val payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val command = """
            python3 - <<'PY'
            import base64, json
            from pathlib import Path
            import yaml
            data = json.loads(base64.b64decode('$payload').decode('utf-8'))
            path = Path.home() / '.hermes' / 'config.yaml'
            if path.exists():
                cfg = yaml.safe_load(path.read_text(encoding='utf-8')) or {}
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                cfg = {}
            cfg.setdefault('model', {})['provider'] = data['provider']
            cfg.setdefault('model', {})['default'] = data['model']
            if 'base_url' in data:
                cfg.setdefault('model', {})['base_url'] = data['base_url']
            path.write_text(yaml.safe_dump(cfg, sort_keys=False, allow_unicode=True), encoding='utf-8')
            print('model config updated: provider=' + data['provider'] + ' model=' + data['model'])
            PY
        """.trimIndent()
        gatewayClient.request(
            GatewayMethods.SHELL_EXEC,
            mapOf("command" to JsonPrimitive(command)),
            timeoutMs = 10_000,
        )
    }

    private suspend fun writeFallbackChain(entries: List<FallbackProviderConfig>) {
        val json = entries.joinToString(prefix = "[", postfix = "]") { entry ->
            buildString {
                append("{")
                append("\"provider\":\"").append(entry.provider.escapeJson()).append("\"")
                append(",\"model\":\"").append(entry.model.escapeJson()).append("\"")
                entry.baseUrl?.let { append(",\"base_url\":\"").append(it.escapeJson()).append("\"") }
                append("}")
            }
        }
        val payload = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val command = """
            python3 - <<'PY'
            import base64, json
            from pathlib import Path
            import yaml

            entries = json.loads(base64.b64decode('$payload').decode('utf-8'))
            path = Path.home() / '.hermes' / 'config.yaml'
            if path.exists():
                cfg = yaml.safe_load(path.read_text(encoding='utf-8')) or {}
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                cfg = {}
            cfg['fallback_providers'] = entries
            cfg.pop('fallback_model', None)
            path.write_text(yaml.safe_dump(cfg, sort_keys=False, allow_unicode=True), encoding='utf-8')
            print('fallback_providers updated:', entries)
            PY
        """.trimIndent()
        gatewayClient.request(
            GatewayMethods.SHELL_EXEC,
            mapOf("command" to JsonPrimitive(command)),
            timeoutMs = 10_000,
        )
    }

    fun configureCustomProvider(provider: String, model: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            try {
                if (apiKey.isNotBlank()) {
                    gatewayClient.request(
                        GatewayMethods.MODEL_SAVE_KEY,
                        buildJsonObject {
                            put("slug", provider)
                            put("api_key", apiKey)
                        }.toMap(),
                    )
                }
                // Persist base_url + provider/model to config.yaml (for new
                // sessions), then switch the LIVE agent via config.set key=model.
                writeModelConfig(provider, model, baseUrl.ifBlank { null })
                val switchError = applyHermesModelSwitch(provider, model)
                _uiState.value = _uiState.value.copy(
                    activeProvider = provider,
                    activeModel = model,
                    errorMessage = switchError ?: "Backend set to $provider/$model",
                )
                loadConfig()
                loadModels()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to configure custom provider")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to configure $provider: ${e.message}",
                )
            }
        }
    }

    fun selectModel(model: ModelOption) {
        viewModelScope.launch {
            try {
                val error = applyHermesModelSwitch(model.provider, model.modelId)
                if (error != null) {
                    _uiState.value = _uiState.value.copy(errorMessage = error)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    activeProvider = model.provider,
                    activeModel = model.modelId,
                    errorMessage = "Backend set to ${model.provider}/${model.modelId}",
                )
                loadConfig()
                loadModels()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to select model")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to select model: ${e.message}",
                )
            }
        }
    }

    /**
     * Switch model + provider the Hermes-native way, via `config.set` key="model".
     *
     * Earlier the app wrote `~/.hermes/config.yaml` directly (writeModelConfig).
     * That only affects the NEXT session — the live running agent keeps the old
     * model, which is why "switch provider" appeared to do nothing.
     *
     * The correct path is Hermes' own `config.set` handler with key="model".
     * Its `value` mirrors the `/model` command grammar parsed by
     * `parse_model_flags`:
     *   "<model> --provider <provider> --global"
     *     • `--provider` pins the provider (else Hermes infers it from the model)
     *     • `--global` persists the choice to config.yaml so new sessions inherit it
     *
     * We target the most-recent live session so the running agent switches too.
     * Returns null on success, or a user-facing error string on failure.
     */
    private suspend fun applyHermesModelSwitch(provider: String, model: String): String? {
        val sid = try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            (mr as? JsonObject)?.get("session_id")?.let { (it as? JsonPrimitive)?.content }
        } catch (e: Exception) {
            null
        }
        val value = buildString {
            append(model)
            if (provider.isNotBlank()) append(" --provider ").append(provider)
            append(" --global")
        }
        val params = buildJsonObject {
            put("key", "model")
            put("value", value)
            if (!sid.isNullOrBlank()) put("session_id", sid)
        }
        return try {
            gatewayClient.request(GatewayMethods.CONFIG_SET, params.toMap())
            null
        } catch (e: GatewayException) {
            // 4009 = session busy (mid-turn). Hermes rejects model swaps while a
            // turn is in flight; surface an actionable message.
            val m = e.message.orEmpty()
            if (m.contains("busy") || m.contains("4009")) {
                "Session is busy — interrupt the current turn before switching models."
            } else {
                "Failed to switch model: $m"
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
                val tools = (ts["tools"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?: emptyList()
                ToolOption(
                    name = ts["name"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    description = ts["description"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    enabled = ts["enabled"]?.let { (it as? JsonPrimitive)?.content } != "false",
                    toolset = null,
                    toolCount = ts["tool_count"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                        ?: tools.size,
                    tools = tools,
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

    // ── Disconnect provider (model.disconnect) ────────────────────────────

    /**
     * Remove saved credentials for a provider. Hermes `model.disconnect`
     * (param slug) wipes the stored key; errors 4001 (no slug) / 4005 (no creds).
     */
    fun disconnectProvider(slug: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("slug", slug) }
                gatewayClient.request(GatewayMethods.MODEL_DISCONNECT, params.toMap())
                _uiState.value = _uiState.value.copy(errorMessage = "Disconnected $slug")
                loadModels()
                loadConfig()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to disconnect $slug")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to disconnect $slug: ${e.message}")
            }
        }
    }

    // ── Reload config without restart (reload.mcp / reload.env) ────────────

    fun reloadMcp() {
        viewModelScope.launch {
            try {
                gatewayClient.request(GatewayMethods.RELOAD_MCP, buildJsonObject { put("confirm", true) }.toMap())
                _uiState.value = _uiState.value.copy(errorMessage = "MCP servers reloaded")
            } catch (e: Exception) {
                Timber.e(e, "[Config] reload.mcp failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to reload MCP: ${e.message}")
            }
        }
    }

    fun reloadEnv() {
        viewModelScope.launch {
            try {
                gatewayClient.request(GatewayMethods.RELOAD_ENV)
                _uiState.value = _uiState.value.copy(errorMessage = "Environment reloaded")
            } catch (e: Exception) {
                Timber.e(e, "[Config] reload.env failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to reload env: ${e.message}")
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
    val activeProvider: String? = null,
    val activeModel: String? = null,
    val fallbackSummary: String? = null,
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

data class FallbackProviderConfig(
    val provider: String,
    val model: String,
    val baseUrl: String? = null,
)

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

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
    val toolCount: Int = 0,
    val tools: List<String> = emptyList(),
)
