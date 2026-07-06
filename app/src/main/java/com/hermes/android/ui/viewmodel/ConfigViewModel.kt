package com.hermes.android.ui.viewmodel

import android.util.Base64
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
        loadMemory()
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

    // ── Model Behavior Config ──────────────────────────────────────────────

    fun setYolo(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("yolo", enabled.toString())
                _uiState.value = _uiState.value.copy(yolo = enabled)
                Timber.i("[Config] Yolo set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set yolo")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set yolo: ${e.message}",
                )
            }
        }
    }

    fun setReasoning(level: String) {
        viewModelScope.launch {
            try {
                saveConfigSilent("reasoning", level)
                _uiState.value = _uiState.value.copy(reasoning = level)
                Timber.i("[Config] Reasoning set to $level")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set reasoning")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set reasoning: ${e.message}",
                )
            }
        }
    }

    fun setThinkingMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("thinking_mode", enabled.toString())
                _uiState.value = _uiState.value.copy(thinkingMode = enabled)
                Timber.i("[Config] Thinking mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set thinking mode")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set thinking mode: ${e.message}",
                )
            }
        }
    }

    fun setFast(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("fast", enabled.toString())
                _uiState.value = _uiState.value.copy(fast = enabled)
                Timber.i("[Config] Fast mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set fast mode")
            }
        }
    }

    fun setBusy(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("busy", enabled.toString())
                _uiState.value = _uiState.value.copy(busy = enabled)
                Timber.i("[Config] Busy mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set busy mode")
            }
        }
    }

    fun setVerbose(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("verbose", enabled.toString())
                _uiState.value = _uiState.value.copy(verbose = enabled)
                Timber.i("[Config] Verbose mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set verbose mode")
            }
        }
    }

    fun setDetailsMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("details_mode", enabled.toString())
                _uiState.value = _uiState.value.copy(detailsMode = enabled)
                Timber.i("[Config] Details mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set details mode")
            }
        }
    }

    fun setCompact(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("compact", enabled.toString())
                _uiState.value = _uiState.value.copy(compact = enabled)
                Timber.i("[Config] Compact mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set compact mode")
            }
        }
    }

    fun setStatusbar(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("statusbar", enabled.toString())
                _uiState.value = _uiState.value.copy(statusbar = enabled)
                Timber.i("[Config] Statusbar set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set statusbar")
            }
        }
    }

    fun setMouse(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("mouse", enabled.toString())
                _uiState.value = _uiState.value.copy(mouse = enabled)
                Timber.i("[Config] Mouse mode set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set mouse mode")
            }
        }
    }

    fun setIndicator(enabled: Boolean) {
        viewModelScope.launch {
            try {
                saveConfigSilent("indicator", enabled.toString())
                _uiState.value = _uiState.value.copy(indicator = enabled)
                Timber.i("[Config] Indicator set to $enabled")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set indicator")
            }
        }
    }

    fun setPersonality(value: String) {
        viewModelScope.launch {
            try {
                saveConfigSilent("personality", value)
                _uiState.value = _uiState.value.copy(personality = value)
                Timber.i("[Config] Personality set to $value")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set personality")
            }
        }
    }

    fun setSkin(value: String) {
        viewModelScope.launch {
            try {
                saveConfigSilent("skin", value)
                _uiState.value = _uiState.value.copy(skin = value)
                Timber.i("[Config] Skin set to $value")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set skin")
            }
        }
    }

    fun setPrompt(value: String) {
        viewModelScope.launch {
            try {
                saveConfigSilent("prompt", value)
                _uiState.value = _uiState.value.copy(prompt = value)
                Timber.i("[Config] Prompt set")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set prompt")
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

    /**
     * Reset the current model connection (`model.disconnect`). This RPC has
     * been defined in GatewayMethods since the original protocol wiring but
     * had zero call sites anywhere in the app — real users had no way to
     * force-clear a stuck/authenticated model session (e.g. after rotating an
     * API key or when a provider connection wedges) other than restarting the
     * whole gateway. Re-loads models/providers afterward so the UI reflects
     * the cleared state.
     */
    fun disconnectModel() {
        viewModelScope.launch {
            try {
                gatewayClient.request(GatewayMethods.MODEL_DISCONNECT)
                Timber.i("[Config] Model disconnected")
                _uiState.value = _uiState.value.copy(errorMessage = "Model disconnected")
                loadModels()
                loadProviders()
            } catch (e: Exception) {
                Timber.e(e, "[Config] model.disconnect failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to disconnect model: ${e.message}",
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

    // ── Memory (USER.md / MEMORY.md) ─────────────────────────────────────

    fun loadMemory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMemory = true)
            try {
                val userResult = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to JsonPrimitive("cat ~/.hermes/memories/USER.md 2>/dev/null || echo '(not found)'")),
                )
                val userMd = (userResult as? JsonObject)
                    ?.get("stdout")?.let { (it as? JsonPrimitive)?.content } ?: "(not found)"

                val memResult = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to JsonPrimitive("cat ~/.hermes/memories/MEMORY.md 2>/dev/null || echo '(not found)'")),
                )
                val memoryMd = (memResult as? JsonObject)
                    ?.get("stdout")?.let { (it as? JsonPrimitive)?.content } ?: "(not found)"

                _uiState.value = _uiState.value.copy(
                    memoryUserMd = userMd,
                    memoryMd = memoryMd,
                    isLoadingMemory = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load memory")
                _uiState.value = _uiState.value.copy(isLoadingMemory = false)
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

    // ── Provider Management (matches Hermes Agent config.yaml) ──────────

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProviders = true)
            try {
                // Read config.yaml directly as JSON via shell.exec — parsing
                // config.show's human-formatted text was too fragile.
                val out = execPython(
                    """
                    import json, yaml, pathlib
                    home = pathlib.Path.home() / '.hermes'
                    cp = home / 'config.yaml'
                    cfg = (yaml.safe_load(cp.read_text()) if cp.exists() else {}) or {}
                    provs = cfg.get('providers') or {}
                    model = cfg.get('model')
                    if not isinstance(model, dict): model = {'model': model or ''}
                    print(json.dumps({
                        'providers': {str(k): {
                            'base_url': str((v or {}).get('base_url', '') if isinstance(v, dict) else ''),
                            'default_model': str((v or {}).get('default_model', '') if isinstance(v, dict) else ''),
                        } for k, v in provs.items()},
                        'strategies': cfg.get('credential_pool_strategies') or {},
                        'fallback': [str(x) for x in (cfg.get('fallback_providers') or []) if isinstance(x, (str, int))],
                        'active_provider': str(model.get('provider') or ''),
                    }))
                    """.trimIndent()
                )
                val root = kotlinx.serialization.json.Json.parseToJsonElement(out) as JsonObject
                val fallbackList = (root["fallback"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                val strategies = root["strategies"] as? JsonObject
                val activeProv = (root["active_provider"] as? JsonPrimitive)?.content ?: ""
                val providers = (root["providers"] as? JsonObject)?.map { (slug, v) ->
                    val obj = v as? JsonObject
                    HermesProviderConfig(
                        slug = slug,
                        baseUrl = (obj?.get("base_url") as? JsonPrimitive)?.content ?: "",
                        defaultModel = (obj?.get("default_model") as? JsonPrimitive)?.content ?: "",
                        strategy = (strategies?.get(slug) as? JsonPrimitive)?.content ?: "rotate",
                        isPrimary = slug == activeProv,
                        isFallback = slug in fallbackList,
                        fallbackOrder = fallbackList.indexOf(slug),
                    )
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    providers = providers,
                    fallbackProviders = fallbackList,
                    isLoadingProviders = false,
                )

                // Load credential pool for each provider
                providers.forEach { loadCredentialPool(it.slug) }

                Timber.i("[Config] Providers loaded: ${providers.size}")
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load providers")
                _uiState.value = _uiState.value.copy(isLoadingProviders = false)
            }
        }
    }

    private fun parseConfigSectionsMap(result: JsonElement): Map<String, List<String>> {
        val sections = mutableMapOf<String, List<String>>()
        try {
            val obj = result as? JsonObject ?: return sections
            // config.show returns {sections: {name: {rows: [...]}}}
            val sectionsObj = obj["sections"] as? JsonObject
            sectionsObj?.forEach { (name, section) ->
                val sectionObj = section as? JsonObject
                val rows = (sectionObj?.get("rows") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                if (rows != null) sections[name] = rows
            }
        } catch (e: Exception) {
            Timber.w(e, "[Config] Failed to parse config sections")
        }
        return sections
    }

    private fun parseFallbackProviders(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        // Parse "[provider1, provider2]" or "fallback_providers: [provider1, provider2]"
        val value = raw.substringAfter(":").trim().removeSurrounding("[", "]").trim()
        if (value.isBlank()) return emptyList()
        return value.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
    }

    fun addProvider(slug: String, baseUrl: String, defaultModel: String, apiKey: String) {
        viewModelScope.launch {
            try {
                // Sanitize the slug: it is interpolated into python/yaml.
                val s = slug.trim().lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                if (s.isEmpty()) throw IllegalArgumentException("Invalid provider name")
                // The gateway's config.set RPC only accepts a whitelist of
                // special keys and rejects everything else with "unknown
                // config key" — writing providers.* through it can never
                // work. Write config.yaml directly via shell.exec instead.
                // Values travel base64-encoded so quoting can't break.
                execPython(
                    """
                    import base64, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    provs = d.setdefault('providers', {})
                    entry = provs.setdefault('$s', {})
                    entry['base_url'] = base64.b64decode('${b64(baseUrl.trim())}').decode()
                    dm = base64.b64decode('${b64(defaultModel.trim())}').decode()
                    if dm: entry['default_model'] = dm
                    # Let Hermes auto-list the provider's models on the next
                    # model.options call, so the model dropdown fills itself.
                    entry['discover_models'] = True
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                // Save API key to the credential pool
                if (apiKey.isNotBlank()) addCredentialDirect(s, apiKey, "primary")
                loadProviders()
                loadModels()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Provider \"$s\" added"
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to add provider")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add provider: ${e.message}"
                )
            }
        }
    }

    fun removeProvider(rawSlug: String) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            try {
                // Use shell.exec to remove provider section from config.yaml
                val script = """
                    import yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) or {}
                    d.get('providers', {}).pop('$slug', None)
                    d.get('credential_pool_strategies', {}).pop('$slug', None)
                    fb = d.get('fallback_providers') or []
                    if '$slug' in fb: fb.remove('$slug')
                    d['fallback_providers'] = fb
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                """.trimIndent()
                execPython(script)
                loadProviders()
                loadModels()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Provider \"$slug\" removed"
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to remove provider")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to remove provider: ${e.message}"
                )
            }
        }
    }

    fun setFallbackProviders(providers: List<String>) {
        viewModelScope.launch {
            try {
                // config.set rejects non-whitelisted keys — write the yaml list
                // directly. The list travels as base64 JSON to avoid quoting.
                val json = providers.joinToString(",", "[", "]") { "\"${it.trim()}\"" }
                execPython(
                    """
                    import base64, json, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    d['fallback_providers'] = json.loads(base64.b64decode('${b64(json)}').decode())
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(fallbackProviders = providers)
                loadProviders()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Fallback providers updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set fallback: ${e.message}"
                )
            }
        }
    }

    fun setProviderStrategy(slug: String, strategy: String) {
        viewModelScope.launch {
            try {
                val s = slug.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                val strat = strategy.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                execPython(
                    """
                    import yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    d.setdefault('credential_pool_strategies', {})['$s'] = '$strat'
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                loadProviders()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set strategy: ${e.message}"
                )
            }
        }
    }

    /**
     * Make this provider the active/primary one. Uses the same Hermes-native
     * model switch as [selectModel]: sets config `model` to the provider's
     * default model (or a discovered one), so both the live agent and future
     * sessions use it.
     */
    fun setPrimaryProvider(provider: HermesProviderConfig) {
        viewModelScope.launch {
            try {
                val model = provider.defaultModel.ifBlank {
                    _uiState.value.availableModels.firstOrNull { it.provider == provider.slug }?.modelId
                }
                if (model.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Set a default model for \"${provider.slug}\" first"
                    )
                    return@launch
                }
                val error = applyHermesModelSwitch(provider.slug, model)
                if (error != null) {
                    _uiState.value = _uiState.value.copy(errorMessage = error)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    activeProvider = provider.slug,
                    activeModel = model,
                    errorMessage = "\"${provider.slug}\" is now primary ($model)",
                )
                loadProviders()
                loadModels()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed: ${e.message}")
            }
        }
    }

    /** Add or remove a provider from the capacity-aware fallback chain. */
    fun toggleFallback(slug: String) {
        val current = _uiState.value.fallbackProviders
        val next = if (slug in current) current - slug else current + slug
        setFallbackProviders(next)
    }

    /** Move a provider up/down in the fallback chain (order = try order). */
    fun moveFallback(slug: String, up: Boolean) {
        val list = _uiState.value.fallbackProviders.toMutableList()
        val i = list.indexOf(slug)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= list.size) return
        list[i] = list[j].also { list[j] = list[i] }
        setFallbackProviders(list)
    }

    /**
     * Reorder a key within a provider's pool. Order IS priority for the
     * fill_first strategy, and the pool list order is what Hermes iterates.
     */
    fun moveCredential(rawSlug: String, index: Int, up: Boolean) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            try {
                val delta = if (up) -1 else 1
                execPython(
                    """
                    import json, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'auth.json'
                    d = json.loads(p.read_text()) if p.exists() else {}
                    pool = d.get('credential_pool', {}).get('$slug', [])
                    i = ${index} - 1
                    j = i + (${delta})
                    if 0 <= i < len(pool) and 0 <= j < len(pool):
                        pool[i], pool[j] = pool[j], pool[i]
                        # keep an explicit priority field in sync with order
                        for n, e in enumerate(pool):
                            e['priority'] = n
                    p.write_text(json.dumps(d, indent=2))
                    print('OK')
                    """.trimIndent()
                )
                loadCredentialPool(slug)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to reorder key: ${e.message}")
            }
        }
    }

    /** Load the Nous credits/balance view (credits.view RPC). */
    fun loadCredits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCredits = true, creditsText = null)
            try {
                val r = gatewayClient.request(GatewayMethods.CREDITS_VIEW)
                val obj = r as? JsonObject
                val loggedIn = (obj?.get("logged_in") as? JsonPrimitive)?.content == "true"
                val text = if (!loggedIn) {
                    "No Nous account logged in.\nSign in from Termux: hermes login"
                } else {
                    val lines = (obj?.get("balance_lines") as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                    lines.joinToString("\n").ifBlank { "No balance information." }
                }
                _uiState.value = _uiState.value.copy(creditsText = text, isLoadingCredits = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    creditsText = "Could not load credits: ${e.message}",
                    isLoadingCredits = false,
                )
            }
        }
    }

    fun dismissCredits() {
        _uiState.value = _uiState.value.copy(creditsText = null)
    }

    fun toggleProviderExpanded(slug: String) {
        val current = _uiState.value.expandedProviderSlug
        _uiState.value = _uiState.value.copy(
            expandedProviderSlug = if (current == slug) null else slug
        )
    }

    // ── Credential Pool ─────────────────────────────────────────────────

    fun loadCredentialPool(rawSlug: String) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCredentials = true)
            try {
                val script = """
                    import json, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'auth.json'
                    d = json.loads(p.read_text()) if p.exists() else {}
                    pool = d.get('credential_pool', {}).get('$slug', [])
                    out = []
                    for i, e in enumerate(pool, 1):
                        tok = e.get('access_token', '')
                        out.append({
                            'index': i,
                            'id': e.get('id'),
                            'label': e.get('label', ''),
                            'auth_type': e.get('auth_type', 'api_key'),
                            'token_preview': tok[:8] + '...' + tok[-4:] if len(tok) > 12 else '***',
                            'priority': e.get('priority', 0),
                            'last_status': e.get('last_status'),
                            'request_count': e.get('request_count', 0),
                        })
                    print(json.dumps(out))
                """.trimIndent()
                val output = execPython(script)
                val entries = parseCredentialEntries(output.ifBlank { "[]" })
                val currentPool = _uiState.value.credentialPool.toMutableMap()
                currentPool[slug] = entries
                _uiState.value = _uiState.value.copy(
                    credentialPool = currentPool,
                    isLoadingCredentials = false,
                )
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load credential pool for $slug")
                _uiState.value = _uiState.value.copy(isLoadingCredentials = false)
            }
        }
    }

    private fun parseCredentialEntries(json: String): List<CredentialEntry> {
        return try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonArray
            arr?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                CredentialEntry(
                    index = (obj["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    id = (obj["id"] as? JsonPrimitive)?.content,
                    label = (obj["label"] as? JsonPrimitive)?.content,
                    authType = (obj["auth_type"] as? JsonPrimitive)?.content,
                    tokenPreview = (obj["token_preview"] as? JsonPrimitive)?.content ?: "***",
                    priority = (obj["priority"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    lastStatus = (obj["last_status"] as? JsonPrimitive)?.content,
                    requestCount = (obj["request_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "[Config] Failed to parse credentials")
            emptyList()
        }
    }

    fun addCredential(slug: String, apiKey: String, label: String? = null) {
        viewModelScope.launch {
            try {
                addCredentialDirect(slug, apiKey, label)
                loadCredentialPool(slug)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Key added to $slug"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add key: ${e.message}"
                )
            }
        }
    }

    private suspend fun addCredentialDirect(rawSlug: String, apiKey: String, label: String? = null) {
        val slug = safeSlug(rawSlug)
        val lbl = label?.takeIf { it.isNotBlank() } ?: "key"
        // Key and label travel base64-encoded — an API key containing a quote
        // or backslash must never be able to break the embedded python.
        execPython(
            """
            import base64, json, uuid, pathlib
            p = pathlib.Path.home() / '.hermes' / 'auth.json'
            d = json.loads(p.read_text()) if p.exists() else {}
            pool = d.setdefault('credential_pool', {}).setdefault('$slug', [])
            pool.append({
                'id': uuid.uuid4().hex[:6],
                'label': base64.b64decode('${b64(lbl)}').decode(),
                'auth_type': 'api_key',
                'priority': 0,
                'source': 'manual',
                'access_token': base64.b64decode('${b64(apiKey)}').decode(),
            })
            p.write_text(json.dumps(d, indent=2))
            print('OK')
            """.trimIndent()
        )
    }

    fun removeCredential(rawSlug: String, index: Int) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            try {
                val script = """
                    import json, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'auth.json'
                    d = json.loads(p.read_text()) if p.exists() else {}
                    pool = d.get('credential_pool', {}).get('$slug', [])
                    if 0 < ${index} <= len(pool):
                        pool.pop(${index} - 1)
                    p.write_text(json.dumps(d, indent=2))
                    print('OK')
                """.trimIndent()
                execPython(script)
                loadCredentialPool(slug)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Key removed from $slug"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to remove key: ${e.message}"
                )
            }
        }
    }

    private suspend fun saveConfigSilent(key: String, value: String) {
        val params = buildJsonObject {
            put("key", key)
            put("value", value)
        }
        gatewayClient.request(GatewayMethods.CONFIG_SET, params.toMap())
    }

    private fun shellQuote(s: String): String {
        // Safe single-quote wrapping for shell
        return "'" + s.replace("'", "'\\''") + "'"
    }

    /** Base64 (no-wrap) for smuggling arbitrary values into embedded python. */
    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Slugs are interpolated straight into the python source, so they must
     * never carry a quote, newline or shell metacharacter. Provider slugs are
     * always `[a-z0-9._-]` in practice; strip anything else as defense-in-depth
     * (the value may originate from a hand-edited config.yaml).
     */
    private fun safeSlug(s: String): String =
        s.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

    /**
     * Run a python snippet in Termux via the gateway's shell.exec RPC and
     * return its stdout. Throws with stderr when the script fails — callers
     * surface that as the error message instead of silently "succeeding".
     *
     * The script is fed through a quoted heredoc on stdin, NOT `python3 -c`:
     * the gateway's safety filter hard-blocks any `-c`/`-e` script execution
     * ("script execution via -e/-c flag"), which is exactly why every
     * provider operation used to fail. Heredoc passes the filter and works
     * even though shell.exec runs the outer shell with stdin=DEVNULL (bash
     * wires the heredoc to python's stdin itself).
     */
    private suspend fun execPython(script: String): String {
        val result = gatewayClient.request(GatewayMethods.SHELL_EXEC, buildJsonObject {
            put("command", "python3 - <<'H2PYEOF'\n$script\nH2PYEOF")
        }.toMap())
        val obj = result as? JsonObject
        val code = (obj?.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: -1
        val stdout = (obj?.get("stdout") as? JsonPrimitive)?.content ?: ""
        if (code != 0) {
            val stderr = (obj?.get("stderr") as? JsonPrimitive)?.content ?: "unknown error"
            throw IllegalStateException(stderr.lines().lastOrNull { it.isNotBlank() } ?: stderr)
        }
        return stdout.trim()
    }

    // ── UI actions ────────────────────────────────────────────────────────

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

}

// ── UI State models ──────────────────────────────────────────────────────

data class ConfigUiState(
    val configYaml: String = "",
    val isLoadingConfig: Boolean = false,
    val activeProvider: String? = null,
    val activeModel: String? = null,
    val availableModels: List<ModelOption> = emptyList(),
    val isLoadingModels: Boolean = false,
    val availableTools: List<ToolOption> = emptyList(),
    val isLoadingTools: Boolean = false,
    val memoryUserMd: String = "",
    val memoryMd: String = "",
    val isLoadingMemory: Boolean = false,
    val errorMessage: String? = null,
    // Provider management
    val providers: List<HermesProviderConfig> = emptyList(),
    val isLoadingProviders: Boolean = false,
    val fallbackProviders: List<String> = emptyList(),
    val credentialPool: Map<String, List<CredentialEntry>> = emptyMap(),
    val isLoadingCredentials: Boolean = false,
    val expandedProviderSlug: String? = null,
    // Nous credits/balance panel (opened from the Models tab)
    val creditsText: String? = null,
    val isLoadingCredits: Boolean = false,
    // Model behavior config
    val yolo: Boolean = false,
    val reasoning: String = "standard",
    val thinkingMode: Boolean = true,
    // Additional behavior config
    val fast: Boolean = false,
    val busy: Boolean = false,
    val verbose: Boolean = false,
    val detailsMode: Boolean = false,
    val compact: Boolean = false,
    val statusbar: Boolean = true,
    val mouse: Boolean = false,
    val indicator: Boolean = true,
    // Text config values
    val personality: String = "",
    val skin: String = "",
    val prompt: String = "",
)

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

// ── Provider / Credential Pool models ──────────────────────────────────
// Maps 1:1 to Hermes Agent config.yaml structure:
//   providers.<slug>.base_url
//   providers.<slug>.default_model
//   credential_pool_strategies.<slug>
//   fallback_providers: []
// Credentials live in auth.json → credential_pool.<slug>[]

data class HermesProviderConfig(
    val slug: String,
    val baseUrl: String,
    val defaultModel: String = "",
    val strategy: String = "rotate",          // rotate | failover
    val credentials: List<CredentialEntry> = emptyList(),
    val isPrimary: Boolean = false,
    val isFallback: Boolean = false,
    val fallbackOrder: Int = -1,              // position in fallback_providers list
)

data class CredentialEntry(
    val index: Int,                           // 1-based (matches REST API)
    val id: String?,
    val label: String?,
    val authType: String?,                    // api_key | oauth
    val tokenPreview: String,                 // redacted
    val priority: Int,
    val lastStatus: String?,                  // ok | fail | null
    val requestCount: Int,
)
