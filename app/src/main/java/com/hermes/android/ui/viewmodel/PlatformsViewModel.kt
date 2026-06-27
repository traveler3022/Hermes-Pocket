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
 * ViewModel for the Messaging Platforms screen.
 *
 * Manages configuration of Telegram, Discord, Slack bots.
 * Uses `config.get` / `config.set` RPCs to read and write platform
 * credentials.
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency),
 *            Phase 1.5 Rule 2 (orchestrator only)
 */
@HiltViewModel
class PlatformsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlatformsUiState())
    val uiState: StateFlow<PlatformsUiState> = _uiState.asStateFlow()

    init {
        loadPlatforms()
    }

    fun loadPlatforms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load each platform's config
                val platforms = mutableListOf<PlatformConfig>()
                for (platform in PlatformType.entries) {
                    val config = loadPlatformConfig(platform)
                    platforms.add(config)
                }
                _uiState.value = _uiState.value.copy(
                    platforms = platforms,
                    isLoading = false,
                )
                Timber.i("[Platforms] Loaded ${platforms.size} platforms")
            } catch (e: Exception) {
                Timber.e(e, "[Platforms] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load platforms: ${e.message}",
                )
            }
        }
    }

    private suspend fun loadPlatformConfig(platform: PlatformType): PlatformConfig {
        // Fix S8F01: config.get does NOT support platform token keys.
        // Hermes config.get only supports: provider, profile, project.
        // Platform tokens must be configured via `hermes gateway setup` in Termux.
        // The app shows instructions instead of trying to read/write tokens.
        return PlatformConfig(
            type = platform,
            botToken = "",
            isConnected = false,
        )
    }

    fun saveToken(platform: PlatformType, token: String) {
        // Fix S8F01: Cannot save platform tokens via config.get/config.set.
        // Hermes config.set fallback writes to display.{key}, not root-level platforms.{key}.
        // User must configure via `hermes gateway setup` in Termux.
        _uiState.value = _uiState.value.copy(
            errorMessage = "Platform tokens must be configured via 'hermes gateway setup' in Termux. The app cannot set them directly.",
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

// ── UI State models ──────────────────────────────────────────────────────

data class PlatformsUiState(
    val platforms: List<PlatformConfig> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class PlatformConfig(
    val type: PlatformType,
    val botToken: String,
    val isConnected: Boolean,
)

enum class PlatformType(
    val displayName: String,
    val configKey: String,
    val description: String,
) {
    TELEGRAM("Telegram", "telegram", "Telegram bot for chat integration"),
    DISCORD("Discord", "discord", "Discord bot for server integration"),
    SLACK("Slack", "slack", "Slack bot for workspace integration"),
}
