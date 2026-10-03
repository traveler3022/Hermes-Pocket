package com.hermes.android.runtime.remote

import okhttp3.Authenticator
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * How the app reaches hosts inside the user's tailnet (`…ts.net` names, 100.x addresses). The
 * in-app Tailscale node ([com.hermes.android.runtime.remote.tailscale.TailscaleNode]) carries them;
 * everything else goes out directly as before.
 */
interface TailnetRoute {
    /** Picks the in-app tunnel for tailnet hosts and no proxy for the rest. */
    val proxySelector: ProxySelector

    /** Answers the tunnel's password challenge. */
    val proxyAuthenticator: Authenticator

    /**
     * Brings the tunnel up when [host] is in the tailnet. Throws [IOException] with a reason the
     * user can act on (not signed in to Tailscale yet, unavailable on this device).
     */
    suspend fun ensureUp(host: String)

    /** No tailnet: every host is dialled directly (tests, and builds without the native library). */
    object None : TailnetRoute {
        override val proxySelector: ProxySelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        }
        override val proxyAuthenticator: Authenticator = Authenticator.NONE
        override suspend fun ensureUp(host: String) = Unit
    }
}

/** A `…ts.net` MagicDNS name or a Tailscale address (100.64.0.0/10, fd7a:115c:a1e0::/48). */
fun isTailnetHost(host: String): Boolean {
    val h = host.trim().trimEnd('.').lowercase().removePrefix("[").removeSuffix("]")
    if (h.endsWith(".ts.net")) return true
    val v4 = h.split('.')
    if (v4.size == 4 && v4.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) }) {
        val a = v4[0].toInt()
        val b = v4[1].toInt()
        return a == 100 && b in 64..127
    }
    return h.startsWith("fd7a:115c:a1e0:")
}
