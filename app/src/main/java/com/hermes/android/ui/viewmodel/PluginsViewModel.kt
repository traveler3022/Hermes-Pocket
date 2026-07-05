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

    private fun parsePlugins(result: kotlinx.serialization.json.JsonElement): List<PluginItem> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val pluginsArr = obj["plugins"] as? JsonArray ?: return emptyList()
            pluginsArr.mapNotNull { pluginEl ->
                val plugin = pluginEl as? JsonObject ?: return@mapNotNull null
                val name = (plugin["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val enabled = (plugin["enabled"] as? JsonPrimitive)?.content?.toBoolean() ?: false
                PluginItem(
                    name = name,
                    enabled = enabled,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[Plugins] Parse error")
            emptyList()
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
    val enabled: Boolean,
)
