package com.hermes.android.runtime.remote

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes on the user's own server: nothing to install or start on the phone, the app only connects.
 * Sign-in and the per-connection tickets live in [RemoteAuth] and [RemoteAuthInterceptor].
 */
@Singleton
class RemoteRuntime @Inject constructor(
    private val settings: RemoteServerSettings,
    private val auth: RemoteAuth,
) : HermesRuntime {

    override val type: RuntimeType = RuntimeType.REMOTE

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override val installProgress: StateFlow<InstallProgress?> = MutableStateFlow<InstallProgress?>(null).asStateFlow()

    private val serverBase: String? get() = remoteHttpBase(settings.config.value.serverUrl)

    override suspend fun detect(): DetectionResult {
        val base = serverBase
        if (base == null) {
            _state.value = RuntimeState.NotDetected
            return DetectionResult.Missing("No server yet", "Enter your Hermes server's address.", InstallAction.None)
        }
        _state.value = RuntimeState.Detecting
        val status = try {
            auth.probe(base)
        } catch (e: IOException) {
            val reason = "Can't reach $base: ${e.message}"
            _state.value = RuntimeState.Error(reason, e)
            return DetectionResult.Incompatible(reason)
        }
        val canConnect = !status.authRequired || auth.isSignedInTo(base) || settings.config.value.token.isNotBlank()
        if (!canConnect) {
            val result = if (status.nativeSignIn) {
                DetectionResult.Missing("Sign in needed", "Tap Sign in and log in with your server account.", InstallAction.None)
            } else {
                DetectionResult.Incompatible("This server's Hermes is too old for app sign-in. Update it, or use a session token.")
            }
            _state.value = RuntimeState.Error("Not signed in to $base")
            return result
        }
        // "Installed" means ready to start: for a server that is reachable and signed in to.
        val info = RuntimeInfo(type = type, path = base, hermesVersion = status.version ?: "remote")
        _state.value = RuntimeState.Installed(info)
        return DetectionResult.Available(info)
    }

    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult =
        InstallResult.Failure("Nothing to install: Hermes runs on your server.")

    override suspend fun verify(): VerifyResult {
        val base = serverBase ?: return VerifyResult.Failure("No server address set")
        return try {
            VerifyResult.Success(hermesVersion = auth.probe(base).version ?: "unknown", doctorOk = true)
        } catch (e: IOException) {
            VerifyResult.Failure("Can't reach $base: ${e.message}")
        }
    }

    override suspend fun startGateway(): GatewayHandle {
        val base = serverBase ?: throw IllegalStateException("No server address set")
        val handle = GatewayHandle(startedAt = System.currentTimeMillis(), webSocketUrl = getWebSocketUrl())
        _state.value = RuntimeState.Running(RuntimeInfo(type = type, path = base), handle)
        return handle
    }

    override suspend fun stopGateway(): StopResult {
        // Nothing runs on the phone; the client's disconnect closes the socket.
        _state.value = RuntimeState.NotDetected
        return StopResult.Success
    }

    override suspend fun fetchLogs() = Unit

    override suspend fun runDoctor(): String {
        val base = serverBase ?: return "No server address set."
        val lines = mutableListOf("Server: $base")
        try {
            val status = auth.probe(base)
            lines += "Reachable: yes (Hermes ${status.version ?: "?"})"
            lines += "Login required: ${if (status.authRequired) "yes" else "no"}"
            lines += "App sign-in offered: ${if (status.nativeSignIn) "yes" else "no"}"
        } catch (e: IOException) {
            lines += "Reachable: no (${e.message})"
        }
        val signedIn = auth.session.value?.takeIf { auth.isSignedInTo(base) }
        lines += if (signedIn != null) "Signed in as: ${signedIn.userId} (${signedIn.provider})" else "Signed in: no"
        lines += "Each connection uses a new one-time ticket: ${if (signedIn != null) "yes" else "no"}"
        return lines.joinToString("\n")
    }

    override suspend fun isHealthy(): Boolean {
        val base = serverBase ?: return false
        return runCatching { auth.probe(base) }.isSuccess
    }

    override fun getWebSocketUrl(): String {
        val base = serverBase ?: return ""
        val socket = remoteWebSocketUrl(base)
        // Signed in: RemoteAuthInterceptor adds a fresh one-time ticket on every dial.
        if (auth.isSignedInTo(base)) return socket
        // Servers without app sign-in: the static session token, as before.
        val token = settings.config.value.token.trim()
        return if (token.isEmpty()) socket else "$socket?token=${URLEncoder.encode(token, "UTF-8")}"
    }

    override fun launchHostApp(): Boolean = false

    override fun getInstallInstructions(): InstallInstructions? = null
}
