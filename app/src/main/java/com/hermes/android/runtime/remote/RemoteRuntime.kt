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
import com.hermes.android.runtime.remote.tailscale.TailscaleLoginNeeded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes on the user's own server: nothing to install or start on the phone, the app only connects.
 * The server keeps Hermes on 127.0.0.1 and shares it inside its tailnet with Tailscale Serve, so the
 * address is `https://<machine>.<tailnet>.ts.net`; nothing is exposed to the internet. The app joins
 * that tailnet itself ([TailnetRoute], Tailscale inside the app). Sign-in and the per-connection
 * tickets live in [RemoteAuth] and [RemoteAuthInterceptor].
 */
@Singleton
class RemoteRuntime @Inject constructor(
    private val settings: RemoteServerSettings,
    private val auth: RemoteAuth,
    private val tailnet: TailnetRoute,
) : HermesRuntime {

    override val type: RuntimeType = RuntimeType.REMOTE

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override val installProgress: StateFlow<InstallProgress?> = MutableStateFlow<InstallProgress?>(null).asStateFlow()

    /** The configured server, when its address is an encrypted one; nothing else is ever dialled. */
    private val serverBase: String?
        get() = remoteHttpBase(settings.config.value.serverUrl)?.takeIf { it.startsWith("https://") }

    override suspend fun detect(): DetectionResult {
        val base = serverBase
        if (base == null) {
            _state.value = RuntimeState.NotDetected
            return if (remoteHttpBase(settings.config.value.serverUrl) == null) {
                DetectionResult.Missing("No server yet", "Enter your Hermes server's address.", InstallAction.None)
            } else {
                DetectionResult.Incompatible("Only encrypted https:// addresses are allowed. Use your server's Tailscale Serve address (https://…ts.net).")
            }
        }
        _state.value = RuntimeState.Detecting
        val status = try {
            auth.probe(base)
        } catch (e: TailscaleLoginNeeded) {
            _state.value = RuntimeState.NotDetected
            return DetectionResult.Missing(
                "Sign in to Tailscale",
                "Tap Sign in to Tailscale and log in with the same account as your server.",
                InstallAction.None,
            )
        } catch (e: IOException) {
            val reason = e.message ?: "Can't reach $base"
            _state.value = RuntimeState.Error(reason, e)
            return DetectionResult.Incompatible(reason)
        }
        val problem = when {
            // A dashboard without a login runs agent commands for anyone who reaches it.
            !status.authRequired -> DetectionResult.Incompatible(
                "This server has no login. Set a Hermes dashboard username and password and " +
                    "dashboard.public_url (see the setup steps).",
            )
            !status.nativeSignIn -> DetectionResult.Incompatible("This server's Hermes is too old for app sign-in. Update it.")
            !auth.isSignedInTo(base) ->
                DetectionResult.Missing("Sign in needed", "Enter your Hermes username and password and tap Sign in.", InstallAction.None)
            else -> null
        }
        if (problem != null) {
            _state.value = RuntimeState.Error("Can't connect to $base yet")
            return problem
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
        // The socket to a tailnet address goes through the in-app Tailscale node; have it up first.
        tailnet.ensureUp(base.toHttpUrl().host)
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
        lines += "Encrypted (https): yes"
        return lines.joinToString("\n")
    }

    override suspend fun isHealthy(): Boolean {
        val base = serverBase ?: return false
        return runCatching { auth.probe(base) }.isSuccess
    }

    // No credential in the URL: RemoteAuthInterceptor adds a fresh one-time ticket on every dial.
    override fun getWebSocketUrl(): String = serverBase?.let(::remoteWebSocketUrl).orEmpty()

    override fun launchHostApp(): Boolean = false

    override fun getInstallInstructions(): InstallInstructions? = null
}
