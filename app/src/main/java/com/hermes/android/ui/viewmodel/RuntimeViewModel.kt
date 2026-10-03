package com.hermes.android.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.HermesRuntimeManager
import com.hermes.android.runtime.InstallAction
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.PrerequisiteResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.remote.RemoteAuth
import com.hermes.android.runtime.remote.RemoteServerConfig
import com.hermes.android.runtime.remote.RemoteServerSettings
import com.hermes.android.runtime.remote.tailscale.TailscaleNode
import com.hermes.android.runtime.remote.tailscale.TailscaleStatus
import com.hermes.android.service.HermesGatewayService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/** Where Hermes runs: on the phone (built-in Linux or Termux) or on the user's own server. */
enum class RuntimeChoiceUi { BuiltInLinux, Termux, RemoteServer }

enum class TailscaleUiState { Off, Starting, NeedsLogin, NeedsApproval, Connected, Unavailable }

/** A device in the user's tailnet, as the server screen lists it. */
data class TailnetDeviceUi(
    val name: String,
    /** `https://<machine>.<tailnet>.ts.net`: what the app dials when this is the server. */
    val address: String,
    val os: String,
    val online: Boolean,
    val direct: Boolean,
    val relay: String,
)

/** One-off steps of Tailscale's own login that the server screen carries out. */
sealed interface TailscaleLoginEvent {
    /** Open Tailscale's login page in a browser tab on top of the app. */
    data class Open(val url: String) : TailscaleLoginEvent

    /** The login went through: bring the app back over the browser tab. */
    data object Done : TailscaleLoginEvent
}

/** The in-app Tailscale connection, as the server screen shows it. */
data class TailscaleUi(
    val state: TailscaleUiState,
    /** The Tailscale account this phone is in. */
    val account: String = "",
    /** This phone's name in the tailnet. */
    val device: String = "",
    val devices: List<TailnetDeviceUi> = emptyList(),
    val error: String? = null,
)


/**
 * ViewModel for the Runtime Setup screen.
 *
 * Depends ONLY on [HermesRuntimeManager] — never on any concrete runtime
 * implementation. This is the abstraction boundary: if we later swap
 * implementations, this file does not change.
 *
 * The UI observes [uiState] (a [RuntimeUiState]) — NOT the raw
 * [com.hermes.android.runtime.RuntimeState]. This keeps the UI decoupled
 * from the runtime layer's internal representation (Phase 1.5 Rule 1).
 *
 * Reference: ADR-002 (Native Compose), ADR-009 (production embedded Python),
 *            Phase 1.5 Rule 1 (Strict Layer Dependency)
 */
@HiltViewModel
class RuntimeViewModel @Inject constructor(
    private val runtimeManager: HermesRuntimeManager,
    private val remoteServerSettings: RemoteServerSettings,
    private val remoteAuth: RemoteAuth,
    private val tailscale: TailscaleNode,
    private val gatewayClient: GatewayClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** True when the bound runtime is the remote-server runtime. */
    val isRemoteRuntime: Boolean get() = runtimeManager.runtimeType == RuntimeType.REMOTE

    /**
     * Live gateway connection state for the setup screen's status chip,
     * mapped to the UI-facing type ([GatewayConnectionUi]) so the screen
     * never imports from the gateway package (Phase 1.5 Rule 1). Carries
     * the human-readable detail (failure reason / reconnect attempt) so
     * errors surface with their real cause instead of a generic message.
     */
    val connectionState: StateFlow<GatewayConnectionUi> = gatewayClient.connectionState
        .map { state ->
            when (state) {
                is ConnectionState.Connected -> GatewayConnectionUi(ChatConnectionState.Connected)
                is ConnectionState.Connecting -> GatewayConnectionUi(ChatConnectionState.Connecting)
                is ConnectionState.Reconnecting -> GatewayConnectionUi(
                    ChatConnectionState.Reconnecting,
                    detail = state.lastError,
                    reconnectAttempt = state.attempt,
                )
                is ConnectionState.Failed -> GatewayConnectionUi(
                    ChatConnectionState.Failed,
                    detail = state.reason,
                )
                is ConnectionState.Disconnected -> GatewayConnectionUi(ChatConnectionState.Disconnected)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GatewayConnectionUi(ChatConnectionState.Disconnected))

    val runtimeChoice: StateFlow<RuntimeChoiceUi> = runtimeManager.selectedRuntime
        .map { it.toChoice() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, runtimeManager.selectedRuntime.value.toChoice())

    /** False until the user has picked a runtime; setup waits for that. */
    val runtimeChosen: StateFlow<Boolean> = runtimeManager.runtimeChosen

    private fun RuntimeType.toChoice() = when (this) {
        RuntimeType.TERMUX -> RuntimeChoiceUi.Termux
        RuntimeType.REMOTE -> RuntimeChoiceUi.RemoteServer
        else -> RuntimeChoiceUi.BuiltInLinux
    }

    fun selectRuntime(choice: RuntimeChoiceUi) {
        if (_installing.value) {
            _errorMessage.value = "Wait for the current install to finish before switching runtimes."
            return
        }
        runtimeManager.selectRuntime(
            when (choice) {
                RuntimeChoiceUi.Termux -> RuntimeType.TERMUX
                RuntimeChoiceUi.BuiltInLinux -> RuntimeType.PROOT_LINUX
                RuntimeChoiceUi.RemoteServer -> RuntimeType.REMOTE
            }
        )
        // Only the server needs the tailnet; on the phone it would just keep a connection open.
        if (choice != RuntimeChoiceUi.RemoteServer) viewModelScope.launch { tailscale.stop() }
        detect()
    }

    /** Current remote-server connection settings (URL + token). */
    val serverConfig: StateFlow<RemoteServerConfig> = remoteServerSettings.config

    /** Who the app is signed in as on the configured server; null when not signed in there. */
    val remoteSignedInAs: StateFlow<String?> = combine(remoteAuth.session, remoteServerSettings.config) { session, config ->
        session?.takeIf { remoteAuth.isSignedInTo(config.serverUrl) }?.let { it.userId.ifBlank { it.provider } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _signingIn = MutableStateFlow(false)

    /** True while the username and password are being checked. */
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private var signInJob: Job? = null

    /** The in-app Tailscale connection and the devices in its tailnet. */
    val tailscaleUi: StateFlow<TailscaleUi> = tailscale.status
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tailscale.status.value.toUi())

    private fun TailscaleStatus.toUi() = TailscaleUi(
        state = when (state) {
            TailscaleStatus.State.Off, TailscaleStatus.State.Stopped -> TailscaleUiState.Off
            TailscaleStatus.State.Starting -> TailscaleUiState.Starting
            TailscaleStatus.State.NeedsLogin -> TailscaleUiState.NeedsLogin
            TailscaleStatus.State.NeedsMachineAuth -> TailscaleUiState.NeedsApproval
            TailscaleStatus.State.Running -> TailscaleUiState.Connected
            TailscaleStatus.State.Unavailable, TailscaleStatus.State.Error -> TailscaleUiState.Unavailable
        },
        account = user,
        device = self.substringBefore('.'),
        devices = peers.filter { it.dnsName.isNotEmpty() }.map {
            TailnetDeviceUi(
                name = it.name.ifEmpty { it.dnsName.substringBefore('.') },
                address = "https://${it.dnsName}",
                os = it.os,
                online = it.online,
                direct = it.direct,
                relay = it.relay,
            )
        },
        error = error,
    )

    /** Starts Tailscale inside the app, so the server screen can show it and sign in. */
    fun startTailscale() {
        viewModelScope.launch {
            try {
                tailscale.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    private val _tailscaleLogin = MutableSharedFlow<TailscaleLoginEvent>(extraBufferCapacity = 2)

    /** Steps of Tailscale's login for the screen: open the page, then come back when it went through. */
    val tailscaleLogin: SharedFlow<TailscaleLoginEvent> = _tailscaleLogin.asSharedFlow()

    private var tailscaleLoginJob: Job? = null

    /**
     * Tailscale's own login (not Hermes'): its page opens in a browser tab on top of the app. The
     * page has no way back to the app, so the app waits for this phone to join the tailnet and then
     * returns by itself.
     */
    fun signInToTailscale() {
        if (tailscaleLoginJob?.isActive == true) return
        tailscaleLoginJob = viewModelScope.launch {
            _errorMessage.value = null
            try {
                val url = tailscale.loginUrl()
                if (url == null) {
                    if (tailscale.status.value.state != TailscaleStatus.State.Running) {
                        _errorMessage.value = "Tailscale sent no login link yet. Check the internet connection and try again."
                    }
                    return@launch
                }
                _tailscaleLogin.emit(TailscaleLoginEvent.Open(url))
                val joined = withTimeoutOrNull(TAILSCALE_LOGIN_WAIT_MS) {
                    tailscale.status.first { it.state == TailscaleStatus.State.Running }
                }
                if (joined != null) _tailscaleLogin.emit(TailscaleLoginEvent.Done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Tailscale login failed")
                _errorMessage.value = e.message ?: "Tailscale login failed"
            }
        }
    }

    /** Takes this phone out of the tailnet. */
    fun signOutOfTailscale() {
        viewModelScope.launch { tailscale.logout() }
    }

    /** Uses [device] as the Hermes server. */
    fun chooseServer(device: TailnetDeviceUi) {
        viewModelScope.launch {
            _errorMessage.value = null
            remoteServerSettings.save(device.address, "")
        }
    }

    /**
     * Signs in to the server at [serverUrl] with its Hermes [username] and [password], then
     * connects. The login is kept encrypted so the app can sign in again by itself.
     */
    fun signInToRemoteServer(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank()) {
            _errorMessage.value = "Enter the server address first."
            return
        }
        if (username.isBlank() || password.isEmpty()) {
            _errorMessage.value = "Enter your Hermes username and password."
            return
        }
        if (signInJob?.isActive == true) return
        signInJob = viewModelScope.launch {
            _errorMessage.value = null
            _signingIn.value = true
            try {
                remoteServerSettings.save(serverUrl, "")
                if (!remoteAuth.probe(serverUrl).nativeSignIn) {
                    _errorMessage.value = "This server's Hermes is too old for app sign-in. Update it."
                    return@launch
                }
                remoteAuth.signInWithPassword(serverUrl, username.trim(), password)
                connectRemote()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Sign-in failed")
                _errorMessage.value = e.message ?: "Sign-in failed"
            } finally {
                _signingIn.value = false
            }
        }
    }

    /** Forgets the sign-in on this phone and drops the connection; the server address stays. */
    fun signOut() {
        viewModelScope.launch {
            _errorMessage.value = null
            remoteAuth.signOut()
            HermesGatewayService.stop(context)
            gatewayClient.disconnect()
            gatewayClient.forgetEndpoint()
            detect()
        }
    }

    /** Detects the server and connects, first dropping a socket that may still use the old setup. */
    private suspend fun connectRemote() {
        // An already-connected client ignores a new URL, so the old socket has to go first.
        gatewayClient.disconnect()
        gatewayClient.forgetEndpoint()
        when (val result = runtimeManager.runtime.detect()) {
            is DetectionResult.Available -> {
                runtimeManager.runtime.startGateway()
                // Should Android refuse the service (the app went to the background meanwhile),
                // the sign-in is still kept and "Try again" connects.
                runCatching { HermesGatewayService.start(context) }
                    .onFailure { Timber.w(it, "[Runtime] Could not start the gateway service yet") }
            }
            is DetectionResult.Missing -> _errorMessage.value = result.instructions
            is DetectionResult.Incompatible -> _errorMessage.value = result.reason
        }
    }

    /**
     * UI-facing state — converted from the runtime's RuntimeState.
     * The UI observes THIS, not the raw runtime state.
     */
    private val _uiState = MutableStateFlow<RuntimeUiState>(RuntimeUiState.NotDetected)
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    /** Set when the last detect() found the host app missing; the runtime state alone says only NotDetected. */
    private val _missing = MutableStateFlow<RuntimeUiState.Missing?>(null)

    private val _installProgress = MutableStateFlow<InstallProgressUi?>(null)
    val installProgress: StateFlow<InstallProgressUi?> = _installProgress.asStateFlow()

    private val _installInstructions = MutableStateFlow<InstallInstructionsUi?>(null)
    val installInstructions: StateFlow<InstallInstructionsUi?> = _installInstructions.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _logs = MutableStateFlow<String?>(null)
    val logs: StateFlow<String?> = _logs.asStateFlow()

    private val logReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.hermes.android.LOG_UPDATE") {
                val logContent = intent.getStringExtra("logs")
                _logs.value = logContent
                Timber.i("[RuntimeViewModel] Logs updated via broadcast")
            }
        }
    }

    init {
        // Register log receiver
        val filter = android.content.IntentFilter("com.hermes.android.LOG_UPDATE")
        ContextCompat.registerReceiver(context, logReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Bridge runtime state → UI state
        viewModelScope.launch {
            combine(runtimeManager.state, _missing) { runtimeState, missing ->
                // NotDetected is also the state before any probe: only a Missing result
                // turns it into "not installed" instead of an endless spinner.
                if (runtimeState is RuntimeState.NotDetected && missing != null) missing else mapToUiState(runtimeState)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            runtimeManager.installProgress.collect { progress ->
                _installProgress.value = progress?.let {
                    InstallProgressUi(
                        stage = it.stage,
                        message = it.message,
                        percent = it.percent,
                    )
                }
            }
        }
    }

    private fun mapToUiState(runtimeState: RuntimeState): RuntimeUiState {
        return when (runtimeState) {
            is RuntimeState.NotDetected -> RuntimeUiState.NotDetected
            is RuntimeState.Detecting -> RuntimeUiState.Detecting
            is RuntimeState.Detected -> RuntimeUiState.Detected(
                version = runtimeState.info.version,
                diskFreeBytes = runtimeState.info.diskFreeBytes,
            )
            is RuntimeState.Installing -> RuntimeUiState.Installing
            is RuntimeState.Installed -> RuntimeUiState.Installed(
                hermesVersion = runtimeState.info.hermesVersion,
            )
            is RuntimeState.Running -> RuntimeUiState.Running(
                webSocketUrl = runtimeState.gateway.webSocketUrl,
            )
            is RuntimeState.Error -> RuntimeUiState.Error(runtimeState.message)
        }
    }

    fun detect() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                _missing.value = null
                val result = runtimeManager.runtime.detect()
                if (result is DetectionResult.Missing) {
                    Timber.i("[Runtime] Runtime missing: ${result.title}")
                    _missing.value = RuntimeUiState.Missing(
                        storeUrl = when (val action = result.action) {
                            is InstallAction.OpenStore -> action.fDroidUrl
                            is InstallAction.OpenUrl -> action.url
                            InstallAction.None -> null
                        },
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Detection failed")
                _errorMessage.value = e.message ?: "Detection failed"
            }
        }
    }

    fun startInstall() {
        // The Install button stays up until the runtime reports Installing, and the
        // Termux preflight probe takes a while: a second tap started a second install.
        if (_installing.value) return
        _installing.value = true
        viewModelScope.launch {
            _errorMessage.value = null

            val emitter = ProgressEmitter { progress ->
                Timber.d("[Runtime] Progress: ${progress.stage} — ${progress.message}")
            }

            try {
                // Preflight gate: verify every prerequisite (Termux installed,
                // allow-external-apps enabled, enough storage) BEFORE starting, so
                // the install can't die halfway on a missing precondition. Show the
                // fix instead of a corrupted partial install.
                when (val prereq = runtimeManager.runtime.checkInstallPrerequisites()) {
                    is PrerequisiteResult.Blocked -> {
                        _errorMessage.value = "${prereq.title}\n\n${prereq.instructions}"
                        return@launch
                    }
                    PrerequisiteResult.Ready -> Unit
                }

                val result = runtimeManager.runtime.install(emitter)
                when (result) {
                    is InstallResult.Success -> {
                        Timber.i("[Runtime] Install succeeded")
                        // The built-in runtime has no host app to hop through, so go straight to running.
                        if (runtimeChoice.value == RuntimeChoiceUi.BuiltInLinux) startGateway()
                    }
                    is InstallResult.Failure -> {
                        Timber.e("[Runtime] Install failed: ${result.reason}")
                        _errorMessage.value = result.reason
                    }
                    InstallResult.Cancelled -> Timber.w("[Runtime] Install cancelled")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Install threw exception")
                _errorMessage.value = e.message ?: "Install failed"
            } finally {
                _installing.value = false
            }
        }
    }

    fun startGateway() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val handle = runtimeManager.runtime.startGateway()
                Timber.i("[Runtime] Gateway started: ${handle.webSocketUrl.substringBefore("?token=")}")
                HermesGatewayService.start(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Failed to start gateway")
                _errorMessage.value = e.message ?: "Failed to start gateway"
            }
        }
    }

    fun prepareInstallInstructions() {
        val instructions = runtimeManager.runtime.getInstallInstructions()
        if (instructions != null) {
            _installInstructions.value = InstallInstructionsUi(
                title = instructions.title,
                steps = instructions.steps,
                command = instructions.command,
            )
        } else {
            _errorMessage.value = "This runtime does not require user-side installation."
        }
    }

    fun launchHostApp() {
        val launched = runtimeManager.runtime.launchHostApp()
        if (!launched) {
            _errorMessage.value = "Could not launch the runtime host application."
        }
    }

    fun fetchLogs() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                _logs.value = if (runtimeChoice.value == RuntimeChoiceUi.Termux) {
                    "Fetching logs from Termux (saving to /sdcard/Download/hermes_logs.txt)..."
                } else {
                    "Reading logs from the built-in Linux runtime…"
                }
                runtimeManager.runtime.fetchLogs()
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Failed to fetch logs")
                _errorMessage.value = e.message ?: "Failed to fetch logs"
            }
        }
    }

    fun runDoctor() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                _logs.value = "Running hermes doctor…"
                _logs.value = runtimeManager.runtime.runDoctor()
            } catch (e: Exception) {
                Timber.e(e, "[Runtime] Failed to run doctor")
                _errorMessage.value = e.message ?: "Failed to run doctor"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try { context.unregisterReceiver(logReceiver) } catch (e: Exception) { Timber.w(e, "[Runtime] Failed to unregister log receiver") }
    }

    private companion object {
        /** How long the app waits for Tailscale's login in the browser before it stops watching. */
        const val TAILSCALE_LOGIN_WAIT_MS = 10 * 60_000L
    }
}
