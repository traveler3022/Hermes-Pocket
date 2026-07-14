package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
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
    private val sessionRepository: com.hermes.android.data.SessionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    // Same prefs file as ChatViewModel's client-side appearance settings
    // (assistant name/avatar) — both screens read/write it independently.
    private val prefs = context.getSharedPreferences("hermes_chat_prefs", Context.MODE_PRIVATE)

    init {
        loadAll()
        loadAvatarUri()
        loadHubStats()
        collectGatewayLog()
    }

    fun loadAll() {
        loadConfig()
        loadBehaviorConfig()
        loadModels()
        loadTools()
        loadMemory()
        loadSoul()
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
    //
    // Fix: every one of these used to send a bare top-level config.set key
    // (yolo, reasoning, thinking_mode, fast, busy, verbose, details_mode,
    // statusbar, mouse, indicator, personality, skin, prompt) — verified
    // against Hermes' own docs, none of those keys exist at that path (some,
    // like fast/busy/statusbar, don't exist anywhere in Hermes at all). The
    // writes were silently accepted and silently ignored. Real keys are
    // nested under display./agent./approvals., and there was never any
    // read-back, so toggles always reset to the Kotlin default on reload
    // regardless of what was actually saved. loadBehaviorConfig() now reads
    // the real values back; fast/busy/verbose/details_mode/statusbar/mouse/
    // indicator are removed outright — they were never wired to any UI
    // control anyway (dead code), and half of them have no real Hermes
    // equivalent.

    /** Read back the real current values so the controls reflect saved state. */
    fun loadBehaviorConfig() {
        viewModelScope.launch {
            try {
                val out = execPython(
                    """
                    import json, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    approvals = d.get('approvals') or {}
                    agent = d.get('agent') or {}
                    display = d.get('display') or {}
                    print(json.dumps({
                        'approval_mode': str(approvals.get('mode', 'manual')),
                        'reasoning': str(agent.get('reasoning_effort', '') or 'medium'),
                        'personality': str(display.get('personality', '')),
                    }))
                    """.trimIndent()
                )
                val obj = kotlinx.serialization.json.Json.parseToJsonElement(out) as? JsonObject
                _uiState.value = _uiState.value.copy(
                    approvalMode = (obj?.get("approval_mode") as? JsonPrimitive)?.content ?: "manual",
                    reasoning = (obj?.get("reasoning") as? JsonPrimitive)?.content ?: "medium",
                    personality = (obj?.get("personality") as? JsonPrimitive)?.content ?: "",
                )
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load behavior config")
            }
        }
    }

    /** approvals.mode: manual | smart | off ("off" = equivalent of --yolo). */
    fun setApprovalMode(rawMode: String) {
        viewModelScope.launch {
            try {
                // Value comes from a fixed dropdown (manual/smart/off), but
                // sanitize anyway before interpolating into python source.
                val mode = rawMode.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                execPython(
                    """
                    import yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    d.setdefault('approvals', {})['mode'] = '$mode'
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(approvalMode = mode)
                Timber.i("[Config] approvals.mode set to $mode")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set approval mode")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set approval mode: ${e.message}",
                )
            }
        }
    }

    /** agent.reasoning_effort: none | minimal | low | medium | high | xhigh. */
    /**
     * Settings is the global-default control (applies to new chats); the
     * live-chat switch is the chat input bar's control. Scope semantics live
     * in SessionRepository.setReasoningLevel (Milestone A).
     */
    fun setReasoning(rawLevel: String) {
        viewModelScope.launch {
            try {
                val level = sessionRepository.setReasoningLevel(rawLevel, liveSessionId = null)
                _uiState.value = _uiState.value.copy(reasoning = level)
                Timber.i("[Config] reasoning set to $level (global default)")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set reasoning")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set reasoning: ${e.message}",
                )
            }
        }
    }

    /** display.personality — a name referencing agent.personalities (or a built-in). */
    fun setPersonality(value: String) {
        viewModelScope.launch {
            try {
                execPython(
                    """
                    import base64, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    d.setdefault('display', {})['personality'] = base64.b64decode('${b64(value)}').decode()
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(personality = value)
                Timber.i("[Config] display.personality set to $value")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to set personality")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to set personality: ${e.message}",
                )
            }
        }
    }

    /**
     * Client-side avatar image shown next to agent replies in chat. The
     * picked image is copied into app-private storage (not just a
     * content:// reference, which isn't guaranteed to survive a reboot
     * without extra permission plumbing) and referenced by a stable file
     * path saved to prefs — same file/key ChatViewModel reads, no gateway
     * RPC involved.
     */
    fun loadAvatarUri() {
        val saved = prefs.getString(KEY_ASSISTANT_AVATAR, null)
        val path = if (!saved.isNullOrBlank() && java.io.File(saved).exists()) saved else null
        _uiState.value = _uiState.value.copy(avatarUri = path)
    }

    fun setAvatarUri(source: Uri) {
        viewModelScope.launch {
            try {
                val dest = java.io.File(context.filesDir, "assistant_avatar.jpg")
                context.contentResolver.openInputStream(source)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw java.io.IOException("Could not open picked image")
                prefs.edit().putString(KEY_ASSISTANT_AVATAR, dest.absolutePath).apply()
                _uiState.value = _uiState.value.copy(avatarUri = dest.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save avatar image")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save avatar image: ${e.message}",
                )
            }
        }
    }

    fun clearAvatarUri() {
        val saved = prefs.getString(KEY_ASSISTANT_AVATAR, null)
        if (!saved.isNullOrBlank()) java.io.File(saved).delete()
        prefs.edit().remove(KEY_ASSISTANT_AVATAR).apply()
        _uiState.value = _uiState.value.copy(avatarUri = null)
    }

    /**
     * SOUL.md — the agent's persistent identity/voice, first slot in the
     * system prompt (~/.hermes/SOUL.md, plain markdown, auto-created by
     * Hermes if missing). Replaces the old free-text "System Prompt" field,
     * which wrote a config.set "prompt" key that doesn't correspond to
     * anything Hermes reads.
     */
    fun loadSoul() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSoul = true)
            try {
                val result = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to JsonPrimitive("cat ~/.hermes/SOUL.md 2>/dev/null || echo ''")),
                )
                val soul = (result as? JsonObject)?.get("stdout")?.let { (it as? JsonPrimitive)?.content } ?: ""
                _uiState.value = _uiState.value.copy(soulMd = soul, isLoadingSoul = false)
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load SOUL.md")
                _uiState.value = _uiState.value.copy(isLoadingSoul = false)
            }
        }
    }

    fun saveSoul(content: String) {
        viewModelScope.launch {
            try {
                execPython(
                    """
                    import base64, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'SOUL.md'
                    p.write_text(base64.b64decode('${b64(content)}').decode())
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(soulMd = content, errorMessage = "SOUL.md saved")
                Timber.i("[Config] SOUL.md saved")
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save SOUL.md")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save SOUL.md: ${e.message}",
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
                // Verified against tui_gateway/server.py's tools.configure:
                // 1. Only names in the server's CONFIGURABLE_TOOLSETS whitelist
                //    are applied — anything else returns OK with the name in
                //    `unknown`. We used to ignore the response and flip the
                //    switch locally, so non-configurable toolsets LOOKED
                //    toggled while the server did nothing.
                // 2. Without session_id the change only lands in config.yaml —
                //    the LIVE agent keeps its current toolsets until the
                //    session is reset. Passing session_id makes the server
                //    reset the agent so the change applies to the current chat.
                val sid = try {
                    val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
                    (mr as? JsonObject)?.get("session_id")?.let { (it as? JsonPrimitive)?.content }
                } catch (e: Exception) {
                    null
                }
                val params = buildJsonObject {
                    put("action", if (enabled) "enable" else "disable")
                    put("names", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(toolName))))
                    if (!sid.isNullOrBlank()) put("session_id", sid)
                }
                val result = gatewayClient.request(GatewayMethods.TOOLS_CONFIGURE, params.toMap())
                val obj = result as? JsonObject
                val unknown = (obj?.get("unknown") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                if (toolName in unknown) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "\"$toolName\" cannot be toggled on this server",
                    )
                } else {
                    val reset = (obj?.get("reset") as? JsonPrimitive)?.content == "true"
                    Timber.i("[Config] Tool $toolName -> $enabled (live session reset=$reset)")
                }
                // Re-read from the server so switches show the REAL state
                // instead of an optimistic local flip.
                loadTools()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to toggle tool")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to toggle tool: ${e.message}",
                )
                loadTools()
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

    /** ~/.hermes/.env — raw edit, same pattern as SOUL.md. Reload buttons
     *  only re-read the file into the running process; without this there
     *  was no way to actually change what's in it from the app. */
    fun loadEnvFile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingEnv = true)
            try {
                val result = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to JsonPrimitive("cat ~/.hermes/.env 2>/dev/null || echo ''")),
                )
                val env = (result as? JsonObject)?.get("stdout")?.let { (it as? JsonPrimitive)?.content } ?: ""
                _uiState.value = _uiState.value.copy(envText = env, isLoadingEnv = false)
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load .env")
                _uiState.value = _uiState.value.copy(isLoadingEnv = false)
            }
        }
    }

    fun saveEnvFile(content: String) {
        viewModelScope.launch {
            try {
                execPython(
                    """
                    import base64, pathlib
                    p = pathlib.Path.home() / '.hermes' / '.env'
                    p.write_text(base64.b64decode('${b64(content)}').decode())
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(envText = content)
                Timber.i("[Config] .env saved")
                reloadEnv()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save .env")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save .env: ${e.message}",
                )
            }
        }
    }

    /** mcp_servers section of config.yaml, edited as its own JSON blob so
     *  the rest of the config isn't at risk from a hand-typed mistake. */
    fun loadMcpServers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMcp = true)
            try {
                val out = execPython(
                    """
                    import json, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    print(json.dumps(d.get('mcp_servers') or {}, indent=2))
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(mcpServersText = out, isLoadingMcp = false)
            } catch (e: Exception) {
                Timber.w(e, "[Config] Failed to load mcp_servers")
                _uiState.value = _uiState.value.copy(isLoadingMcp = false)
            }
        }
    }

    fun saveMcpServers(content: String) {
        viewModelScope.launch {
            try {
                execPython(
                    """
                    import base64, json, yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    parsed = json.loads(base64.b64decode('${b64(content)}').decode())
                    d['mcp_servers'] = parsed
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print('OK')
                    """.trimIndent()
                )
                _uiState.value = _uiState.value.copy(mcpServersText = content)
                Timber.i("[Config] mcp_servers saved")
                reloadMcp()
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to save mcp_servers")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save MCP servers: ${e.message} — check it's valid JSON",
                )
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
                //
                // Fix: this used to read/write a `providers:` dict keyed by
                // slug — a key Hermes' config loader does not recognize at
                // all. The real schema (per Hermes' own docs) is a
                // `custom_providers:` LIST of {name, base_url, ...}, and a
                // custom provider is only "active" once `model.provider` is
                // set to `custom:<name>`. This was silently a no-op against
                // the real agent before — this is why "add provider" never
                // actually connected anything.
                val out = execPython(
                    """
                    import json, yaml, pathlib
                    home = pathlib.Path.home() / '.hermes'
                    cp = home / 'config.yaml'
                    cfg = (yaml.safe_load(cp.read_text()) if cp.exists() else {}) or {}
                    custom = cfg.get('custom_providers') or []
                    if not isinstance(custom, list): custom = []
                    model = cfg.get('model')
                    if not isinstance(model, dict): model = {}
                    active_raw = str(model.get('provider') or '')
                    active = active_raw[len('custom:'):] if active_raw.startswith('custom:') else ''
                    print(json.dumps({
                        'providers': [{
                            'name': str((c or {}).get('name', '')),
                            'base_url': str((c or {}).get('base_url', '')),
                            'default_model': str((c or {}).get('default_model', '')),
                        } for c in custom if isinstance(c, dict) and c.get('name')],
                        'active_provider': active,
                    }))
                    """.trimIndent()
                )
                val root = kotlinx.serialization.json.Json.parseToJsonElement(out) as JsonObject
                val activeProv = (root["active_provider"] as? JsonPrimitive)?.content ?: ""
                val providers = (root["providers"] as? JsonArray)?.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val slug = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    HermesProviderConfig(
                        slug = slug,
                        baseUrl = (obj["base_url"] as? JsonPrimitive)?.content ?: "",
                        defaultModel = (obj["default_model"] as? JsonPrimitive)?.content ?: "",
                        isPrimary = slug == activeProv,
                    )
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    providers = providers,
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

    fun addProvider(slug: String, baseUrl: String, defaultModel: String, apiKey: String) {
        viewModelScope.launch {
            try {
                // Sanitize the slug: it is interpolated into python/yaml.
                val s = slug.trim().lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                if (s.isEmpty()) throw IllegalArgumentException("Invalid provider name")
                _uiState.value = _uiState.value.copy(isLoadingModels = true)
                // The gateway's config.set RPC only accepts a whitelist of
                // special keys and rejects everything else with "unknown
                // config key" — writing providers.* through it can never
                // work. Write config.yaml directly via shell.exec instead.
                // Values travel base64-encoded so quoting can't break.
                //
                // Beyond just writing the entry, ask the provider's own
                // OpenAI-compatible /models endpoint for its model list,
                // running server-side where the provider is reachable. This
                // auto-detects models from just base_url + key and picks a
                // default_model — without it, a custom provider sat selected
                // but never actually connected. Try both {base}/models and
                // {base}/v1/models so it works whether the base URL already
                // includes /v1 or not, and surface the real failure reason
                // (HTTP error / "no models") instead of failing silently.
                val out = execPython(
                    """
                    import base64, json, yaml, pathlib, urllib.request
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) if p.exists() else {}
                    d = d or {}
                    base = base64.b64decode('${b64(baseUrl.trim())}').decode().strip()
                    key = base64.b64decode('${b64(apiKey.trim())}').decode().strip()
                    hint = base64.b64decode('${b64(defaultModel.trim())}').decode().strip()
                    # custom_providers is a LIST of {name, base_url, ...} — the
                    # `providers:` dict this used to write is not a key Hermes'
                    # config loader recognizes at all.
                    custom = d.setdefault('custom_providers', [])
                    if not isinstance(custom, list): custom = d['custom_providers'] = []
                    entry = next((c for c in custom if isinstance(c, dict) and c.get('name') == '$s'), None)
                    if entry is None:
                        entry = {'name': '$s'}
                        custom.append(entry)
                    if base: entry['base_url'] = base
                    # The API key itself goes through the existing, already-
                    # correct credential_pool mechanism (~/.hermes/auth.json,
                    # see addCredentialDirect below) — not duplicated here.
                    b = base.rstrip('/')
                    cands = [b + '/models']
                    if not b.endswith('/v1'):
                        cands.insert(0, b + '/v1/models')
                    ids = []
                    err = ''
                    for u in cands:
                        try:
                            hdr = {'Authorization': 'Bearer ' + key} if key else {}
                            req = urllib.request.Request(u, headers=hdr)
                            with urllib.request.urlopen(req, timeout=20) as r:
                                body = r.read().decode('utf-8', 'replace')
                            j = json.loads(body)
                            rows = j.get('data') if isinstance(j, dict) else j
                            if rows is None and isinstance(j, dict):
                                rows = j.get('models') or []
                            got = [ (m.get('id') or m.get('name')) for m in (rows or [])
                                    if isinstance(m, dict) and (m.get('id') or m.get('name')) ]
                            if got:
                                ids = got; err = ''; break
                            err = 'endpoint returned no models'
                        except Exception as e:
                            err = str(e)[:200]
                    chosen = hint or (ids[0] if ids else '')
                    if chosen: entry['default_model'] = chosen
                    p.write_text(yaml.dump(d, default_flow_style=False, allow_unicode=True))
                    print(json.dumps({'models': ids, 'chosen': chosen, 'error': err, 'tried': cands}))
                    """.trimIndent()
                )
                // Save the provider's API key
                if (apiKey.isNotBlank()) setCredentialDirect(s, apiKey)

                // Merge the detected models into the dropdown state directly, so
                // it works even if Hermes' model.options doesn't surface them.
                val detected = runCatching {
                    val obj = kotlinx.serialization.json.Json.parseToJsonElement(out) as? JsonObject
                    val arr = obj?.get("models") as? JsonArray
                    val chosen = (obj?.get("chosen") as? JsonPrimitive)?.content ?: ""
                    val err = (obj?.get("error") as? JsonPrimitive)?.content ?: ""
                    val ids = arr?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                    Triple(ids, chosen, err)
                }.getOrDefault(Triple(emptyList<String>(), "", ""))
                val (modelIds, chosen, detectError) = detected
                if (modelIds.isNotEmpty()) {
                    val newModels = modelIds.map {
                        ModelOption(provider = s, modelId = it, name = it, requiresApiKey = true)
                    }
                    val merged = _uiState.value.availableModels.filter { it.provider != s } + newModels
                    _uiState.value = _uiState.value.copy(availableModels = merged)
                }

                loadProviders()
                // Actually connect: point the live agent at the detected model.
                // Hermes only resolves this provider via the "custom:<name>"
                // address (matching custom_providers[].name above) — passing
                // the bare slug would silently fail to activate it.
                if (chosen.isNotBlank()) {
                    val err = applyHermesModelSwitch("custom:$s", chosen)
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        activeProvider = if (err == null) s else _uiState.value.activeProvider,
                        activeModel = if (err == null) chosen else _uiState.value.activeModel,
                        errorMessage = err
                            ?: "Provider \"$s\" added — ${modelIds.size} models, using $chosen",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        errorMessage = "Provider \"$s\" added, but couldn't auto-detect models" +
                            (if (detectError.isNotBlank()) " ($detectError)" else "") +
                            ". Check the base URL/key, or type a model name.",
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[Config] Failed to add provider")
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    errorMessage = "Failed to add provider: ${e.message}"
                )
            }
        }
    }

    fun removeProvider(rawSlug: String) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            try {
                // Remove from config.yaml's custom_providers LIST (not a
                // `providers:` dict — see loadProviders/addProvider), and
                // drop any fallback_providers pair whose provider is this
                // one (matched against both the bare name and "custom:name",
                // since older-written entries may still use either form).
                val script = """
                    import yaml, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'config.yaml'
                    d = yaml.safe_load(p.read_text()) or {}
                    custom = d.get('custom_providers') or []
                    d['custom_providers'] = [c for c in custom if not (isinstance(c, dict) and c.get('name') == '$slug')]
                    strategies = d.get('credential_pool_strategies') or {}
                    strategies.pop('$slug', None)
                    d['credential_pool_strategies'] = strategies
                    def fb_provider(x):
                        return str((x or {}).get('provider', '')) if isinstance(x, dict) else str(x or '')
                    fb = d.get('fallback_providers') or []
                    d['fallback_providers'] = [x for x in fb if fb_provider(x) not in ('$slug', 'custom:$slug')]
                    model = d.get('model')
                    if isinstance(model, dict) and str(model.get('provider') or '') == 'custom:$slug':
                        model['provider'] = ''
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
                // provider.slug is one of our custom_providers entries — Hermes
                // only resolves it via the "custom:<name>" address (see
                // addProvider's comment for why the bare slug silently fails).
                val error = applyHermesModelSwitch("custom:${provider.slug}", model)
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

    fun toggleProviderExpanded(slug: String) {
        val current = _uiState.value.expandedProviderSlug
        _uiState.value = _uiState.value.copy(
            expandedProviderSlug = if (current == slug) null else slug
        )
    }

    // ── Credentials (one key per provider) ──────────────────────────────

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

    fun setCredential(slug: String, apiKey: String) {
        viewModelScope.launch {
            try {
                setCredentialDirect(slug, apiKey)
                loadCredentialPool(slug)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Key saved for $slug"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save key: ${e.message}"
                )
            }
        }
    }

    private suspend fun setCredentialDirect(rawSlug: String, apiKey: String) {
        val slug = safeSlug(rawSlug)
        // The key travels base64-encoded — an API key containing a quote or
        // backslash must never be able to break the embedded python. One key
        // per provider: writing replaces whatever was there before.
        execPython(
            """
            import base64, json, uuid, pathlib
            p = pathlib.Path.home() / '.hermes' / 'auth.json'
            d = json.loads(p.read_text()) if p.exists() else {}
            d.setdefault('credential_pool', {})['$slug'] = [{
                'id': uuid.uuid4().hex[:6],
                'label': 'key',
                'auth_type': 'api_key',
                'priority': 0,
                'source': 'manual',
                'access_token': base64.b64decode('${b64(apiKey)}').decode(),
            }]
            p.write_text(json.dumps(d, indent=2))
            print('OK')
            """.trimIndent()
        )
    }

    fun removeCredential(rawSlug: String) {
        val slug = safeSlug(rawSlug)
        viewModelScope.launch {
            try {
                val script = """
                    import json, pathlib
                    p = pathlib.Path.home() / '.hermes' / 'auth.json'
                    d = json.loads(p.read_text()) if p.exists() else {}
                    d.get('credential_pool', {}).pop('$slug', None)
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

    // ── Control Center stats (design E) ───────────────────────────────────

    /**
     * Live numbers for the Settings hub's stat tiles: credit balance
     * (`credits.view` — a backend capability that was never surfaced in any
     * UI before) and 30-day usage (`insights.get`). Both are best-effort:
     * a failure leaves the tile empty instead of raising an error banner.
     */
    fun loadHubStats() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.CREDITS_VIEW)
                val obj = result as? JsonObject
                val loggedIn = (obj?.get("logged_in") as? JsonPrimitive)?.content == "true"
                val balance = (obj?.get("balance_lines") as? JsonArray)
                    ?.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                _uiState.value = _uiState.value.copy(
                    creditsSummary = when {
                        balance != null -> balance
                        loggedIn -> null
                        else -> null
                    },
                )
            } catch (e: Exception) {
                Timber.w(e, "[Config] credits.view failed (tile stays empty)")
            }
        }
        viewModelScope.launch {
            try {
                val params = buildJsonObject { put("days", 30) }
                val result = gatewayClient.request(GatewayMethods.INSIGHTS_GET, params.toMap())
                val obj = result as? JsonObject
                fun intOf(k: String) = (obj?.get(k) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                _uiState.value = _uiState.value.copy(
                    insights = InsightsData(
                        days = intOf("days").takeIf { it > 0 } ?: 30,
                        sessions = intOf("sessions"),
                        messages = intOf("messages"),
                    ),
                )
            } catch (e: Exception) {
                Timber.w(e, "[Config] insights.get failed (tile stays empty)")
            }
        }
    }

    // ── Command console (design I) ────────────────────────────────────────

    /**
     * Run one shell command on the server (`shell.exec`) and append the
     * result to the console history. The same RPC already powers all the
     * file editors here — this just exposes it directly for diagnostics
     * without SSH.
     */
    fun runConsoleCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || _uiState.value.isConsoleRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConsoleRunning = true)
            try {
                val result = gatewayClient.request(
                    GatewayMethods.SHELL_EXEC,
                    mapOf("command" to JsonPrimitive(trimmed)),
                )
                val obj = result as? JsonObject
                val stdout = (obj?.get("stdout") as? JsonPrimitive)?.content.orEmpty()
                val stderr = (obj?.get("stderr") as? JsonPrimitive)?.content.orEmpty()
                val code = (obj?.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val output = buildString {
                    if (stdout.isNotBlank()) append(stdout.trimEnd())
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(stderr.trimEnd())
                    }
                    if (isEmpty()) append("(exit $code)")
                }
                _uiState.value = _uiState.value.copy(
                    isConsoleRunning = false,
                    consoleEntries = (_uiState.value.consoleEntries + ConsoleEntry(
                        command = trimmed,
                        output = output,
                        isError = code != 0,
                    )).takeLast(30),
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] Console command failed")
                _uiState.value = _uiState.value.copy(
                    isConsoleRunning = false,
                    consoleEntries = (_uiState.value.consoleEntries + ConsoleEntry(
                        command = trimmed,
                        output = e.message ?: "request failed",
                        isError = true,
                    )).takeLast(30),
                )
            }
        }
    }

    /** Emergency stop for runaway processes started from the console. */
    fun stopProcesses() {
        viewModelScope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.PROCESS_STOP)
                val killed = ((result as? JsonObject)?.get("killed") as? JsonPrimitive)
                    ?.content?.toIntOrNull() ?: 0
                _uiState.value = _uiState.value.copy(
                    consoleEntries = (_uiState.value.consoleEntries + ConsoleEntry(
                        command = "process.stop",
                        output = "killed: $killed",
                        isError = false,
                    )).takeLast(30),
                )
            } catch (e: Exception) {
                Timber.e(e, "[Config] process.stop failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to stop: ${e.message}")
            }
        }
    }

    // ── Gateway log (design I) ────────────────────────────────────────────

    /**
     * Keep the last lines of the server's stderr stream (gateway.stderr
     * events) so connection problems can be diagnosed from the phone,
     * without SSH. Bounded to 200 lines.
     */
    private fun collectGatewayLog() {
        viewModelScope.launch {
            gatewayClient.events
                .filterIsInstance<GatewayEvent.GatewayStderr>()
                .collect { event ->
                    _uiState.value = _uiState.value.copy(
                        gatewayLog = (_uiState.value.gatewayLog + event.line).takeLast(200),
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        // Same key ChatViewModel reads from the shared "hermes_chat_prefs"
        // file — keep these in sync if either changes.
        const val KEY_ASSISTANT_AVATAR = "assistant_avatar_path"
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
    val credentialPool: Map<String, List<CredentialEntry>> = emptyMap(),
    val isLoadingCredentials: Boolean = false,
    val expandedProviderSlug: String? = null,
    // Agent behavior config — real Hermes config.yaml keys, verified against
    // the official docs (see setApprovalMode/setReasoning/etc. comments).
    // approvals.mode replaces the old fictional "yolo" boolean — "off" is
    // the documented equivalent of --yolo. TUI-only cosmetics the old screen
    // exposed (skin/compact/mouse/indicator/statusbar/details_mode) are gone:
    // they only restyle the terminal UI on the server, which an Android
    // client never sees.
    val approvalMode: String = "manual", // approvals.mode: manual | smart | off
    val reasoning: String = "medium", // agent.reasoning_effort: none|minimal|low|medium|high|xhigh|max
    val personality: String = "", // display.personality — system-prompt overlay name
    // SOUL.md — the agent's persistent identity/voice (first slot in the
    // system prompt). Replaces the old free-text "prompt" field, which sent
    // a config.set key ("prompt") that doesn't exist anywhere in Hermes.
    val soulMd: String = "",
    val isLoadingSoul: Boolean = false,
    // ~/.hermes/.env raw contents, editable.
    val envText: String = "",
    val isLoadingEnv: Boolean = false,
    // mcp_servers section of config.yaml, as pretty-printed JSON, editable.
    val mcpServersText: String = "",
    val isLoadingMcp: Boolean = false,
    // Client-side avatar image (local file path, null = default icon).
    val avatarUri: String? = null,
    // ── Control Center stats (design E) ──
    // First balance line from credits.view, or null (not logged in / failed).
    val creditsSummary: String? = null,
    // 30-day aggregate from insights.get (reuses SessionsViewModel's type).
    val insights: InsightsData? = null,
    // ── Advanced: command console + gateway log (design I) ──
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val isConsoleRunning: Boolean = false,
    val gatewayLog: List<String> = emptyList(),
)

/** One command + its result in the Advanced screen's console. */
data class ConsoleEntry(
    val command: String,
    val output: String,
    val isError: Boolean,
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

// ── Provider / Credential models ────────────────────────────────────────
// Maps 1:1 to Hermes Agent config.yaml structure:
//   providers.<slug>.base_url
//   providers.<slug>.default_model
// The provider's key lives in auth.json → credential_pool.<slug>[]
// (one entry — the app registers a single key per provider)

data class HermesProviderConfig(
    val slug: String,
    val baseUrl: String,
    val defaultModel: String = "",
    val credentials: List<CredentialEntry> = emptyList(),
    val isPrimary: Boolean = false,
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
