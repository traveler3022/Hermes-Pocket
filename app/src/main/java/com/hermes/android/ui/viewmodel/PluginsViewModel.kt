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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import kotlinx.serialization.json.contentOrNull

/**
 * ViewModel for the Plugins Manager screen.
 *
 * Lists available plugins, shows plugin details, manages plugin lifecycle.
 *
 * Reference: Phase 1.5 Rule 1, Rule 2
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val catalog: com.hermes.android.domain.tools.PluginCatalog,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val params = buildJsonObject {
                    put("action", "list")
                }
                val result = gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap())
                val plugins = parsePlugins(result)
                _uiState.value = _uiState.value.copy(
                    plugins = plugins,
                    isLoading = false,
                )
                Timber.i("[Plugins] Loaded ${plugins.size} plugins")
            } catch (e: GatewayException) {
                Timber.e(e, "[Plugins] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load plugins: ${e.message}",
                )
            }
        }
    }

    /**
     * Fix: plugins.manage's `list` action returns rows shaped
     * {name, version, description, source, status} — there is no `enabled`
     * field at all. Reading plugin["enabled"] always fell through to the
     * `?: false` default, so every plugin showed "Disabled" regardless of
     * its real state. Derive enabled from status instead: it is "enabled",
     * "disabled" or "not enabled" (hermes_cli/plugins_cmd.py _plugin_status),
     * and only "enabled" is on, as on Hermes desktop's switch.
     *
     * Only the rows Hermes desktop's Plugins page lists are kept
     * (store/agent-plugins.ts isDesktopRelevantPlugin): the user's own
     * plugins, plus the two bundled ones with a plain on/off switch. Every
     * other bundled plugin (messaging platforms, web/browser/image/video
     * backends, dashboard sign-in) is on by default and set up elsewhere.
     */
    private fun parsePlugins(result: kotlinx.serialization.json.JsonElement): List<PluginItem> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val pluginsArr = obj["plugins"] as? JsonArray ?: return emptyList()
            pluginsArr.mapNotNull { pluginEl ->
                val plugin = pluginEl as? JsonObject ?: return@mapNotNull null
                val name = (plugin["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val status = (plugin["status"] as? JsonPrimitive)?.contentOrNull ?: ""
                PluginItem(
                    name = name,
                    // The canonical registry key: bare names collide across plugin categories,
                    // and the server resolves a toggle by key first.
                    key = (plugin["key"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: name,
                    description = (plugin["description"] as? JsonPrimitive)?.contentOrNull ?: "",
                    source = (plugin["source"] as? JsonPrimitive)?.contentOrNull ?: "",
                    status = status,
                    enabled = status.lowercase() == "enabled",
                    catalogName = (plugin["catalog_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                    updateAvailable = (plugin["update_available"] as? JsonPrimitive)?.booleanOrNull == true,
                    settings = (plugin["settings_schema"] as? JsonArray).orEmpty().mapNotNull(::parseSetting),
                )
            }.filter { isListedPlugin(it) }
        } catch (e: Exception) {
            Timber.e(e, "[Plugins] Parse error")
            emptyList()
        }
    }

    /**
     * Fix: the screen had no way to actually enable/disable a plugin at all
     * — plugins.manage's `toggle` action (name + enable) was never called
     * from anywhere. "Plugins Manager" only ever listed plugins.
     */
    fun togglePlugin(plugin: PluginItem, enable: Boolean) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "toggle")
                    put("key", plugin.key)
                    put("enable", enable)
                }
                gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap())
                Timber.i("[Plugins] ${plugin.key} -> enabled=$enable")
                loadPlugins()
            } catch (e: GatewayException) {
                Timber.e(e, "[Plugins] Failed to toggle ${plugin.key}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to toggle ${plugin.name}: ${e.message}",
                )
            }
        }
    }

    private fun parseSetting(el: JsonElement): PluginSetting? {
        val o = el as? JsonObject ?: return null
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        val key = str("key")?.takeIf { it.isNotBlank() } ?: return null
        return PluginSetting(
            key = key,
            type = str("type") ?: "string",
            label = str("label")?.takeIf { it.isNotBlank() } ?: key,
            description = str("description").orEmpty(),
            required = (o["required"] as? JsonPrimitive)?.booleanOrNull == true,
            value = o["value"]?.takeUnless { it is JsonNull },
            default = o["default"]?.takeUnless { it is JsonNull },
            choices = (o["choices"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            env = str("env"),
            hasValue = (o["has_value"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    // ── Catalog, install, update, remove, settings (plugins.manage, as Hermes desktop's Plugins hub) ──

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(catalogLoading = true, catalogError = null) }
            try {
                val entries = catalog.entries().sortedWith(compareBy({ it.tier != "official" }, { it.title.lowercase() }))
                _uiState.update { it.copy(catalog = entries, catalogLoading = false) }
            } catch (e: Exception) {
                Timber.w(e, "[Plugins] Catalog unavailable")
                _uiState.update { it.copy(catalogLoading = false, catalogError = e.message ?: "unavailable") }
            }
        }
    }

    /** A curated entry, at the catalog's pinned commit. */
    fun installFromCatalog(name: String) = manage("Installed $name", "install") { put("catalog_name", name) }

    /** A git URL or owner/repo; [force] replaces an existing install of the same name. */
    fun installFromUrl(identifier: String, force: Boolean) = manage("Installed", "install") {
        put("identifier", identifier.trim())
        if (force) put("force", true)
    }

    /** Catalog installs only; a wider plugin asks first ([PluginsUiState.pendingConsent]). */
    fun update(plugin: PluginItem, acceptCapabilities: Boolean = false) {
        _uiState.update { it.copy(pendingConsent = null) }
        manage("Updated ${plugin.name}", "update", consentFor = plugin) {
            put("name", plugin.installName())
            if (acceptCapabilities) put("accept_capabilities", true)
        }
    }

    fun dismissConsent() = _uiState.update { it.copy(pendingConsent = null) }

    fun remove(plugin: PluginItem) = manage("Removed ${plugin.name}", "remove") { put("name", plugin.installName()) }

    fun saveSettings(plugin: PluginItem, values: Map<String, JsonElement>) =
        manage("Saved ${plugin.name}", "settings") {
            put("key", plugin.key)
            put("values", JsonObject(values))
        }

    private fun manage(
        done: String,
        action: String,
        consentFor: PluginItem? = null,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val params = buildJsonObject {
                    put("action", action)
                    extra()
                }
                val r = gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap()) as? JsonObject
                if (consentFor != null && (r?.get("consent_required") as? JsonPrimitive)?.booleanOrNull == true) {
                    val lines = (r["delta_lines"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    _uiState.update { it.copy(busy = false, pendingConsent = consentFor to lines) }
                    return@launch
                }
                _uiState.update { it.copy(busy = false, noticeMessage = resultNotice(done, r)) }
                loadPlugins()
                if (_uiState.value.catalog.isNotEmpty()) loadCatalog()
            } catch (e: Exception) {
                Timber.e(e, "[Plugins] $action failed")
                _uiState.update { it.copy(busy = false, errorMessage = "Failed: ${e.message}") }
            }
        }
    }

    /** What the server says still needs doing: warnings, missing keys, a restart. */
    private fun resultNotice(done: String, r: JsonObject?): String {
        fun list(k: String) = (r?.get(k) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val parts = mutableListOf(done)
        list("missing_env").takeIf { it.isNotEmpty() }?.let { parts += "needs: " + it.joinToString(", ") }
        if ((r?.get("restart_required") as? JsonPrimitive)?.booleanOrNull == true) parts += "restart Hermes to finish"
        parts += list("warnings")
        return parts.joinToString(" · ")
    }

    fun clearNotice() = _uiState.update { it.copy(noticeMessage = null) }

    fun reloadPlugins() {
        loadPlugins()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

private val HIDDEN_PLUGIN_KEY_PREFIXES = listOf("dashboard_auth/", "model-providers/", "platforms/")
private val MANAGEABLE_BUNDLED_PLUGINS = setOf("disk-cleanup", "security-guidance")

internal fun isListedPlugin(plugin: PluginItem): Boolean =
    if (plugin.source == "bundled") {
        plugin.key in MANAGEABLE_BUNDLED_PLUGINS
    } else {
        HIDDEN_PLUGIN_KEY_PREFIXES.none { plugin.key.startsWith(it) }
    }

data class PluginsUiState(
    val plugins: List<PluginItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val busy: Boolean = false,
    val catalog: List<com.hermes.android.domain.tools.CatalogPlugin> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    /** An update that widens the plugin, with what it adds, waiting for the user's yes. */
    val pendingConsent: Pair<PluginItem, List<String>>? = null,
)

/** One `config_schema` key of a plugin (PluginSettingField); a secret carries no value, only [env]. */
data class PluginSetting(
    val key: String,
    /** string, number, boolean, enum, secret or json. */
    val type: String,
    val label: String,
    val description: String,
    val required: Boolean,
    val value: JsonElement?,
    val default: JsonElement?,
    val choices: List<String>,
    val env: String?,
    val hasValue: Boolean,
)

data class PluginItem(
    val name: String,
    val key: String = name,
    val description: String = "",
    val source: String = "",
    val status: String = "",
    val enabled: Boolean,
    /** Set for a catalog install: only those update in place. */
    val catalogName: String? = null,
    val updateAvailable: Boolean = false,
    val settings: List<PluginSetting> = emptyList(),
) {
    /** A user install, the only kind remove takes. */
    val removable: Boolean get() = source == "user"

    /** The install directory's name, which update and remove take: the key's last part. */
    fun installName(): String = key.substringAfterLast('/')
}
