package com.hermes.android.runtime.remote

import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.GatewayHandle
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallAction
import com.hermes.android.runtime.InstallInstructions
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeInfo
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.StopResult
import com.hermes.android.runtime.VerifyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production runtime — Hermes runs on a remote server (VPS), the app is a
 * thin WebSocket client.
 *
 * ## Architecture
 * ```
 * app ──wss://host:port/api/ws?token=…──► reverse proxy (TLS) ──► hermes dashboard (127.0.0.1:9119)
 * ```
 * The server side is a systemd-managed `hermes dashboard` bound to loopback,
 * fronted by a TLS reverse proxy (e.g. Caddy). The app authenticates with the
 * same session token the dashboard was started with
 * (`HERMES_DASHBOARD_SESSION_TOKEN`).
 *
 * ## Lifecycle mapping
 * Nothing is installed or spawned on the device, so the [HermesRuntime]
 * lifecycle collapses:
 * - [detect] — "available" == the user has entered server URL + token.
 * - [install] / [verify] — no-ops; the server is provisioned out-of-band.
 * - [startGateway] — establishes the WebSocket connection (the gateway
 *   itself is already running on the server).
 * - [stopGateway] — disconnects the client; never stops the remote service.
 *
 * Replaces the Termux migration adapter (ADR-001) — see ADR-009: production
 * must not require Termux. Unlike the embedded-Python plan, the agent isn't
 * on the device at all; the same [GatewayClient] wire protocol is spoken
 * against a remote host instead of loopback.
 */
@Singleton
class RemoteRuntime @Inject constructor(
    private val settings: RemoteServerSettings,
    private val gatewayClient: GatewayClient,
) : HermesRuntime {

    override val type: RuntimeType = RuntimeType.REMOTE

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    // Remote runtime never installs anything, so progress is always null.
    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    override val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    override suspend fun detect(): DetectionResult {
        _state.value = RuntimeState.Detecting

        val config = settings.config.value
        if (!config.isComplete) {
            Timber.i("[Remote] No server configured yet")
            _state.value = RuntimeState.NotDetected
            return DetectionResult.Missing(
                title = "Server connection not configured",
                instructions = """
                    Hermes runs on your own server. Enter the server address and
                    session token below, then tap "Save & Connect".

                    Server address example: wss://example.com:2083
                    Token: the HERMES_DASHBOARD_SESSION_TOKEN your server was started with.
                """.trimIndent(),
                action = InstallAction.None,
            )
        }

        val info = remoteInfo(config)
        Timber.i("[Remote] Server configured: ${config.serverUrl}")
        // A configured server is by definition "installed" — provisioning
        // happens server-side. Connectivity is proven by startGateway()/isHealthy().
        _state.value = RuntimeState.Installed(info)
        return DetectionResult.Available(info)
    }

    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult {
        // Nothing to install on-device. Provisioning is done on the server.
        val config = settings.config.value
        if (!config.isComplete) {
            return InstallResult.Failure("Configure the server address and token first.")
        }
        val info = remoteInfo(config)
        _state.value = RuntimeState.Installed(info)
        return InstallResult.Success(info)
    }

    override suspend fun verify(): VerifyResult {
        val url = settings.webSocketUrl()
            ?: return VerifyResult.Failure("Server connection not configured.")
        return try {
            val state = gatewayClient.connect(url = url, connectTimeoutMs = CONNECT_TIMEOUT_MS)
            if (state is ConnectionState.Connected) {
                VerifyResult.Success(hermesVersion = "remote", doctorOk = true)
            } else {
                VerifyResult.Failure("Could not reach the server: $state")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VerifyResult.Failure("Could not reach the server: ${e.message}")
        }
    }

    override suspend fun startGateway(): GatewayHandle {
        val config = settings.config.value
        val url = settings.webSocketUrl()
            ?: throw IllegalStateException("Server connection not configured. Enter the server address and token first.")

        // The gateway is already running server-side (systemd). "Starting"
        // it from the app's perspective means proving we can connect.
        if (_state.value is RuntimeState.Running) {
            Timber.i("[Remote] Already connected — returning existing handle")
            return (_state.value as RuntimeState.Running).gateway
        }

        Timber.i("[Remote] Connecting to ${config.serverUrl}")
        val connectionState = try {
            gatewayClient.connect(url = url, connectTimeoutMs = CONNECT_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Cannot reach the Hermes server at ${config.serverUrl}: ${e.message}"
            _state.value = RuntimeState.Error(msg, e)
            throw IllegalStateException(msg, e)
        }

        if (connectionState !is ConnectionState.Connected) {
            val msg = "Hermes server at ${config.serverUrl} did not accept the connection " +
                "($connectionState). Check the address, the token, and that the server is up."
            _state.value = RuntimeState.Error(msg)
            throw IllegalStateException(msg)
        }

        val handle = GatewayHandle(
            pid = null, // remote process — PID not visible to the app
            startedAt = System.currentTimeMillis(),
            webSocketUrl = url,
        )
        _state.value = RuntimeState.Running(remoteInfo(config), handle)
        return handle
    }

    override suspend fun stopGateway(): StopResult {
        // Never stop the remote service — it serves other clients (Telegram,
        // CLI, web). Stopping means disconnecting this app.
        val currentState = _state.value
        try {
            gatewayClient.disconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Remote] Error disconnecting")
            return StopResult.Failure("Error while disconnecting: ${e.message}")
        }
        if (currentState is RuntimeState.Running) {
            _state.value = RuntimeState.Installed(currentState.info)
        }
        return StopResult.Success
    }

    override suspend fun fetchLogs() {
        // Logs live on the server (journalctl -u hermes-dashboard). Nothing
        // to fetch on-device.
        Timber.i("[Remote] Logs are on the server: journalctl -u hermes-dashboard")
    }

    override suspend fun runDoctor(): String {
        val config = settings.config.value
        if (!config.isComplete) {
            return "Server connection not configured. Enter the server address and token in Runtime Setup."
        }
        val url = settings.webSocketUrl()
            ?: return "Server connection not configured. Enter the server address and token in Runtime Setup."
        return try {
            val state = gatewayClient.connect(url = url, connectTimeoutMs = CONNECT_TIMEOUT_MS)
            if (state is ConnectionState.Connected) {
                """
                Server: ${config.serverUrl} — reachable ✓
                WebSocket: connected ✓
                Token: accepted ✓

                Full diagnostics run on the server: `hermes doctor`
                Logs: `journalctl -u hermes-dashboard`
                """.trimIndent()
            } else {
                "Server: ${config.serverUrl} — connection failed ($state).\n" +
                    "Check that the server is up and the token matches."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Server: ${config.serverUrl} — unreachable (${e.message}).\n" +
                "Check the address, your network, and that the reverse proxy is running."
        }
    }

    override suspend fun isHealthy(): Boolean {
        val url = settings.webSocketUrl() ?: return false
        return try {
            val state = gatewayClient.connect(url = url, connectTimeoutMs = HEALTH_CHECK_TIMEOUT_MS)
            state is ConnectionState.Connected
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Remote] Health check failed")
            false
        }
    }

    override fun getWebSocketUrl(): String = settings.webSocketUrl() ?: ""

    override fun launchHostApp(): Boolean = false // no host app — server is remote

    override fun getInstallInstructions(): InstallInstructions? = null

    private fun remoteInfo(config: RemoteServerConfig) = RuntimeInfo(
        type = RuntimeType.REMOTE,
        version = config.serverUrl,
        path = null,
        pythonVersion = null,
        diskFreeBytes = null,
        hermesVersion = "remote",
    )

    companion object {
        // Generous: covers TLS handshake + gateway.ready over mobile networks.
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val HEALTH_CHECK_TIMEOUT_MS = 5_000L
    }
}
