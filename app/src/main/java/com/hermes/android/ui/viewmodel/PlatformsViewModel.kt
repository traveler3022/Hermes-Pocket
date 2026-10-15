package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.messaging.GatewayActionResult
import com.hermes.android.domain.messaging.GatewayStatus
import com.hermes.android.domain.messaging.MessagingGateway
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val messagingGateway: MessagingGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlatformsUiState())
    val uiState: StateFlow<PlatformsUiState> = _uiState.asStateFlow()

    private val _gateway = MutableStateFlow(GatewayCardState())
    val gateway: StateFlow<GatewayCardState> = _gateway.asStateFlow()

    private val _gatewayEvents = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 4)
    val gatewayEvents: SharedFlow<GatewayEvent> = _gatewayEvents.asSharedFlow()

    /** False on the built-in Linux, which runs no messaging gateway. */
    val gatewayAvailable: Boolean get() = messagingGateway.available

    private var gatewayLoad: Job? = null

    init {
        loadPlatforms()
        if (gatewayAvailable) refreshGateway()
    }

    fun refreshGateway() {
        if (gatewayLoad?.isActive == true) return
        _gateway.update { it.copy(loading = true, loadError = null) }
        gatewayLoad = viewModelScope.launch { loadGatewayStatus() }
    }

    private suspend fun loadGatewayStatus() {
        try {
            val status = messagingGateway.status()
            _gateway.update { it.copy(status = status, loading = false, loadError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Gateway] status failed")
            _gateway.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
        }
    }

    fun startGateway() = gatewayAction(GatewayAction.START) { messagingGateway.start() }

    fun stopGateway() = gatewayAction(GatewayAction.STOP) { messagingGateway.stop() }

    fun restartGateway() = gatewayAction(GatewayAction.RESTART) { messagingGateway.restart() }

    /**
     * Hermes answers as soon as the `hermes gateway <verb>` child is spawned, so the state is read
     * again over the next ~15 s until it shows what was asked.
     */
    private fun gatewayAction(action: GatewayAction, call: suspend () -> GatewayActionResult) {
        if (_gateway.value.busy != null) return
        _gateway.update { it.copy(busy = action) }
        viewModelScope.launch {
            try {
                when (val result = call()) {
                    is GatewayActionResult.Failed -> _gatewayEvents.emit(GatewayEvent.Failed(result.message))
                    GatewayActionResult.Sent -> {
                        _gatewayEvents.emit(GatewayEvent.Sent(action))
                        for ((index, wait) in FOLLOW_UP_MS.withIndex()) {
                            delay(wait)
                            loadGatewayStatus()
                            val running = _gateway.value.status?.running ?: continue
                            // A restart can still read "running" before the old process is gone.
                            val reached = when (action) {
                                GatewayAction.START -> running
                                GatewayAction.STOP -> !running
                                GatewayAction.RESTART -> running && index > 0
                            }
                            if (reached) break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Gateway] $action failed")
                _gatewayEvents.emit(GatewayEvent.Failed(e.message ?: e.javaClass.simpleName))
            } finally {
                _gateway.update { it.copy(busy = null) }
            }
        }
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
        // Hermes config.set fallback writes to display.{key}, not root-level
        // platforms.{key} — this is a real gateway limitation, not something
        // the app can work around. The old message told users to run
        // `hermes gateway setup` in Termux, which no longer applies at all —
        // the app is a thin client to a remote server now, no on-device
        // Termux runtime exists to run that command in.
        _uiState.value = _uiState.value.copy(
            errorMessage = "Platform tokens can't be set from the app yet — edit " +
                "~/.hermes/config.yaml on your server (SSH) or run 'hermes gateway setup' there.",
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

// ── UI State models ──────────────────────────────────────────────────────

private val FOLLOW_UP_MS = listOf(2_000L, 3_000L, 5_000L, 5_000L)

data class GatewayCardState(
    val status: GatewayStatus? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    /** The action running now; the buttons are disabled meanwhile. */
    val busy: GatewayAction? = null,
)

enum class GatewayAction { START, STOP, RESTART }

sealed interface GatewayEvent {
    data class Sent(val action: GatewayAction) : GatewayEvent
    data class Failed(val message: String) : GatewayEvent
}

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
