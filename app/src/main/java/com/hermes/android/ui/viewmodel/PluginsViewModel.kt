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
     * its real state. Verified against tui_gateway/server.py's docstring for
     * plugins.manage. Derive enabled from status instead (only an explicit
     * "disabled" status counts as off — bundled/enabled/etc. are on).
     */
    private fun parsePlugins(result: kotlinx.serialization.json.JsonElement): List<PluginItem> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val pluginsArr = obj["plugins"] as? JsonArray ?: return emptyList()
            pluginsArr.mapNotNull { pluginEl ->
                val plugin = pluginEl as? JsonObject ?: return@mapNotNull null
                val name = (plugin["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val status = (plugin["status"] as? JsonPrimitive)?.content ?: ""
                PluginItem(
                    name = name,
                    description = (plugin["description"] as? JsonPrimitive)?.content ?: "",
                    source = (plugin["source"] as? JsonPrimitive)?.content ?: "",
                    status = status,
                    enabled = status.lowercase() != "disabled",
                )
            }
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
    fun togglePlugin(name: String, enable: Boolean) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "toggle")
                    put("name", name)
                    put("enable", enable)
                }
                gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap())
                Timber.i("[Plugins] $name -> enabled=$enable")
                loadPlugins()
            } catch (e: GatewayException) {
                Timber.e(e, "[Plugins] Failed to toggle $name")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to toggle $name: ${e.message}",
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

data class PluginsUiState(
    val plugins: List<PluginItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class PluginItem(
    val name: String,
    val description: String = "",
    val source: String = "",
    val status: String = "",
    val enabled: Boolean,
)
