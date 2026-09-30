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
)

data class PluginItem(
    val name: String,
    val key: String = name,
    val description: String = "",
    val source: String = "",
    val status: String = "",
    val enabled: Boolean,
)
