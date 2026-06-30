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
 * ViewModel for the Plugins screen.
 *
 * Uses Hermes `plugins.manage`:
 *   - action="list"   → { plugins: [{name, version, description, source, status}] }
 *   - action="toggle" → { name, enable: Bool } flips a plugin on/off
 *
 * Reference: Phase 1.5 Rule 1, Rule 2 (gateway interface only).
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
                val params = buildJsonObject { put("action", "list") }
                val result = gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap())
                val plugins = parsePlugins(result)
                _uiState.value = _uiState.value.copy(plugins = plugins, isLoading = false)
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
            val arr = (result as? JsonObject)?.get("plugins") as? JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val p = item as? JsonObject ?: return@mapNotNull null
                val status = (p["status"] as? JsonPrimitive)?.content ?: ""
                PluginItem(
                    name = (p["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null,
                    version = (p["version"] as? JsonPrimitive)?.content ?: "",
                    description = (p["description"] as? JsonPrimitive)?.content ?: "",
                    source = (p["source"] as? JsonPrimitive)?.content ?: "",
                    status = status,
                    enabled = !status.lowercase().contains("disabled"),
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "[Plugins] Parse error")
            emptyList()
        }
    }

    fun togglePlugin(name: String, enable: Boolean) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "toggle")
                    put("name", name)
                    put("enable", enable)
                }
                gatewayClient.request(GatewayMethods.PLUGINS_MANAGE, params.toMap())
                Timber.i("[Plugins] Toggled $name → enable=$enable")
                loadPlugins()
            } catch (e: Exception) {
                Timber.e(e, "[Plugins] Toggle failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to toggle $name: ${e.message}")
            }
        }
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
    val version: String,
    val description: String,
    val source: String,
    val status: String,
    val enabled: Boolean,
)
