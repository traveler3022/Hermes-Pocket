package com.hermes.android.gateway

import android.net.TrafficStats
import android.os.Process
import com.hermes.android.runtime.remote.TailnetRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.WebSocket

/**
 * Tells a dead link from a busy one, for a long-lived socket to the Hermes server.
 *
 * A deadline on a ping's answer (OkHttp's ping) takes a busy line for a dead one: the server
 * answers only once it has read an upload's whole frame, and a big download fills the line
 * ahead of the answer. Here a link is dead only when nothing at all comes from the server:
 * every [INTERVAL_MS] something new must have arrived, a frame on the socket or any byte the
 * route received from the server. The Tailscale tunnel counts everything the server's device
 * sends, the TCP acknowledgements of an upload included; a server reached directly falls back
 * to all that this app received. Each round gives an idle link something to answer ([probe]),
 * unless data is still waiting to go out, whose acknowledgements serve instead.
 */
class LinkWatch(
    private val tailnet: TailnetRoute,
    /** Its idle connections go with a dead link, so no later call is sent down one. */
    private val pool: ConnectionPool? = null,
) {

    /**
     * Watches [ws] while [current]; [frames] counts the frames received on it so far. Calls
     * [onDead] once if the link dies, and stops.
     */
    fun watch(
        scope: CoroutineScope,
        ws: WebSocket,
        frames: () -> Long,
        probe: (WebSocket) -> Boolean,
        current: () -> Boolean = { true },
        onDead: (reason: String) -> Unit,
    ): Job = scope.launch {
        val host = ws.request().url.host
        var seenFrames = frames()
        var seenBytes = receivedFrom(host)
        var expecting = false
        while (current()) {
            delay(INTERVAL_MS)
            if (!current()) break
            val nowFrames = frames()
            val nowBytes = receivedFrom(host)
            val heard = nowFrames != seenFrames || (nowBytes != null && seenBytes != null && nowBytes > seenBytes)
            seenFrames = nowFrames
            seenBytes = nowBytes
            if (expecting && !heard) {
                dead(onDead, "nothing from the server for ${INTERVAL_MS / 1000}s")
                break
            }
            expecting = true
            if (ws.queueSize() > 0) continue
            if (!probe(ws)) {
                dead(onDead, "the socket refused a probe")
                break
            }
        }
    }

    private fun dead(onDead: (String) -> Unit, reason: String) {
        pool?.evictAll()
        onDead(reason)
    }

    /**
     * Bytes received so far from [host]'s side: the tunnel's count for a tailnet server, else
     * everything this app received (a server reached directly has no count of its own).
     */
    private suspend fun receivedFrom(host: String): Long? =
        tailnet.receivedFrom(host)
            ?: runCatching { TrafficStats.getUidRxBytes(Process.myUid()) }.getOrNull()?.takeIf { it >= 0 }

    companion object {
        /** How long the server may stay silent while something from it is due. */
        const val INTERVAL_MS = 20_000L
    }
}
