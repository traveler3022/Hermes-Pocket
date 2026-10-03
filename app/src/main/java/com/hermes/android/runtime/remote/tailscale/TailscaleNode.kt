package com.hermes.android.runtime.remote.tailscale

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import com.hermes.android.runtime.remote.TailnetRoute
import com.hermes.android.runtime.remote.isTailnetHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Authenticator
import okhttp3.Credentials
import org.json.JSONObject
import timber.log.Timber
import tsbridge.Tsbridge
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** One device in the user's tailnet. */
data class TailnetPeer(
    val name: String,
    /** MagicDNS name, `machine.tailnet.ts.net`. */
    val dnsName: String,
    val ip: String,
    val os: String,
    val online: Boolean,
    /** True once traffic goes straight to it; false while it goes through a Tailscale relay. */
    val direct: Boolean,
    /** The relay region (e.g. `tor`), when relayed. */
    val relay: String,
)

/** What the in-app Tailscale node is doing. */
data class TailscaleStatus(
    val state: State,
    /** The Tailscale login page for this device, while it needs one. */
    val authUrl: String = "",
    /** This device's MagicDNS name. */
    val self: String = "",
    val selfIp: String = "",
    /** The Tailscale account, e.g. an email. */
    val user: String = "",
    val peers: List<TailnetPeer> = emptyList(),
    val error: String? = null,
) {
    enum class State { Off, Starting, NeedsLogin, NeedsMachineAuth, Running, Stopped, Unavailable, Error }
}

/** The tunnel is up but this device is not signed in to Tailscale yet. */
class TailscaleLoginNeeded : IOException("Sign in to Tailscale first, with the same account as your server.")

/**
 * Tailscale inside the app (tsnet through gomobile): the app joins the user's tailnet itself, with
 * no Tailscale app and without taking Android's VPN slot. Its HTTP client reaches tailnet hosts
 * through a local CONNECT proxy that the Go side opens on 127.0.0.1 behind a random password.
 *
 * The node lives in this process; [com.hermes.android.service.HermesGatewayService] keeps the
 * process up while the app is connected.
 */
@Singleton
class TailscaleNode @Inject constructor(
    @ApplicationContext private val context: Context,
) : TailnetRoute {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Calls into Go run on one thread at a time, so a slow status never overlaps a start or stop.
    private val goThread = Executors.newSingleThreadExecutor { r -> Thread(r, "tailscale") }
    private val lifecycle = Mutex()

    private val _status = MutableStateFlow(TailscaleStatus(TailscaleStatus.State.Off))
    val status: StateFlow<TailscaleStatus> = _status.asStateFlow()

    @Volatile private var proxy: Proxy? = null
    @Volatile private var secret: String = ""
    private var pollJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Starts the node; does nothing when it already runs. Throws [IOException] when it can't. */
    suspend fun start() {
        // Not cancellable: a start cut in half would leave the node running with nothing watching it.
        withContext(NonCancellable) { lifecycle.withLock { startLocked() } }
    }

    private suspend fun startLocked() {
        if (proxy != null) return
        _status.value = TailscaleStatus(TailscaleStatus.State.Starting)
        try {
            onGo {
                val dir = File(context.filesDir, "tailscale")
                Tsbridge.start(dir.path, deviceName(), NetInfo)
                val port = Tsbridge.proxyAddress().substringAfterLast(':').toInt()
                secret = Tsbridge.proxySecret()
                proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port))
            }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError lands here too: a build without the library for this CPU.
            Timber.e(e, "[Tailscale] Could not start")
            _status.value = TailscaleStatus(TailscaleStatus.State.Unavailable, error = e.message ?: e.toString())
            throw IOException("Tailscale could not start on this device: ${e.message ?: e}", e)
        }
        val version = runCatching { onGo { Tsbridge.version() } }.getOrDefault("?")
        Timber.i("[Tailscale] Started, tailscale $version")
        watchNetwork()
        pollJob = scope.launch {
            while (isActive) {
                val s = refresh()
                delay(if (s.state == TailscaleStatus.State.Running) RUNNING_POLL_MS else WAITING_POLL_MS)
            }
        }
    }

    /** Stops the node. Its login stays on the phone for the next start. */
    suspend fun stop() {
        lifecycle.withLock {
            if (proxy == null) return
            pollJob?.cancel()
            pollJob = null
            unwatchNetwork()
            proxy = null
            secret = ""
            runCatching { onGo { Tsbridge.stop() } }
            _status.value = TailscaleStatus(TailscaleStatus.State.Off)
        }
    }

    /** Reads the node's state now. */
    suspend fun refresh(): TailscaleStatus {
        if (proxy == null) return _status.value
        val s = runCatching { parse(onGo { Tsbridge.status() }) }
            .getOrElse { TailscaleStatus(TailscaleStatus.State.Error, error = it.message) }
        _status.value = s
        return s
    }

    /** The Tailscale login page for this device, asking the control server for one if needed. */
    suspend fun loginUrl(): String? {
        start()
        repeat(LOGIN_URL_TRIES) { attempt ->
            val s = refresh()
            if (s.state == TailscaleStatus.State.Running) return null
            if (s.authUrl.isNotEmpty()) return s.authUrl
            if (attempt == 0) runCatching { onGo { Tsbridge.login() } }
            delay(500)
        }
        return null
    }

    /** Takes this device out of the tailnet. */
    suspend fun logout() {
        if (proxy == null) return
        runCatching { onGo { Tsbridge.logout() } }.onFailure { Timber.w(it, "[Tailscale] Logout failed") }
        refresh()
    }

    /** The newest lines of the node's own log, for the doctor output. */
    suspend fun logs(): String = runCatching { onGo { Tsbridge.logs() } }.getOrDefault("")

    override val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val p = proxy
            val host = uri?.host
            return if (p != null && host != null && isTailnetHost(host)) listOf(p) else listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    override val proxyAuthenticator: Authenticator = Authenticator { route, response ->
        val key = secret
        if (route?.proxy?.type() != Proxy.Type.HTTP || key.isEmpty()) return@Authenticator null
        // Already sent and still refused (a restarted node has a new password): give up, don't loop.
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        response.request.newBuilder().header("Proxy-Authorization", Credentials.basic("hermes", key)).build()
    }

    override suspend fun ensureUp(host: String) {
        if (!isTailnetHost(host)) return
        start()
        val s = withTimeoutOrNull(CONNECT_WAIT_MS) {
            var s = refresh()
            while (s.state == TailscaleStatus.State.Starting || s.state == TailscaleStatus.State.Stopped) {
                delay(300)
                s = refresh()
            }
            s
        } ?: _status.value
        when (s.state) {
            TailscaleStatus.State.Running -> Unit
            TailscaleStatus.State.NeedsLogin -> throw TailscaleLoginNeeded()
            TailscaleStatus.State.NeedsMachineAuth ->
                throw IOException("Approve this device in the Tailscale admin console (Machines).")
            else -> throw IOException("Tailscale is not connected yet (${s.state}${s.error?.let { ": $it" }.orEmpty()}).")
        }
    }

    private suspend fun <T> onGo(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            goThread.submit(Callable { block() }).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val iface = lp.interfaceName.orEmpty()
                val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.hasGateway() }?.gateway?.hostAddress.orEmpty()
                goThread.execute { runCatching { Tsbridge.networkChanged(iface, gateway) } }
            }

            override fun onLost(network: Network) {
                goThread.execute { runCatching { Tsbridge.networkChanged("", "") } }
            }
        }
        // Android tells apps about network changes; without this, Tailscale's own check runs every 10 minutes.
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Timber.w(it, "[Tailscale] No network callback") }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private fun parse(json: String): TailscaleStatus {
        val o = JSONObject(json)
        val state = when (o.optString("state")) {
            "Off" -> TailscaleStatus.State.Off
            "Starting", "NoState" -> TailscaleStatus.State.Starting
            "NeedsLogin" -> TailscaleStatus.State.NeedsLogin
            "NeedsMachineAuth" -> TailscaleStatus.State.NeedsMachineAuth
            "Running" -> TailscaleStatus.State.Running
            "Stopped" -> TailscaleStatus.State.Stopped
            else -> TailscaleStatus.State.Error
        }
        val peers = o.optJSONArray("peers")?.let { arr ->
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                TailnetPeer(
                    name = p.optString("name"),
                    dnsName = p.optString("dns"),
                    ip = p.optString("ip"),
                    os = p.optString("os"),
                    online = p.optBoolean("online"),
                    direct = p.optString("curAddr").isNotEmpty(),
                    relay = p.optString("relay"),
                )
            }
        }.orEmpty()
        return TailscaleStatus(
            state = state,
            authUrl = o.optString("authURL"),
            self = o.optString("self"),
            selfIp = o.optString("selfIP"),
            user = o.optString("user"),
            peers = peers,
            error = o.optString("error").ifEmpty { null },
        )
    }

    /** How this phone shows up in the Tailscale admin console: `hermes-<model>`. */
    private fun deviceName(): String {
        val model = Build.MODEL.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (model.isEmpty()) "hermes" else "hermes-$model".take(60)
    }

    private companion object {
        const val RUNNING_POLL_MS = 10_000L
        const val WAITING_POLL_MS = 1_500L
        const val CONNECT_WAIT_MS = 25_000L
        const val LOGIN_URL_TRIES = 30
    }
}
