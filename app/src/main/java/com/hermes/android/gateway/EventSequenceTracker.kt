package com.hermes.android.gateway

/**
 * Watches the per-session `seq` the gateway stamps on every event frame and
 * reports when frames went missing on the current socket.
 *
 * Tracking restarts with each socket ([reset]): the first frames after a
 * reconnect always jump past what the old socket saw, and that hole is
 * already covered by the chat's reconnect recovery.
 */
internal class EventSequenceTracker {
    private val lastSeen = HashMap<String, Long>()

    @Synchronized
    fun reset() = lastSeen.clear()

    /** Records [seq] for [sessionId]; true when frames between the previous one and it never arrived. */
    @Synchronized
    fun isGap(sessionId: String, seq: Long): Boolean {
        val previous = lastSeen[sessionId]
        if (previous != null && seq <= previous) return false
        lastSeen[sessionId] = seq
        return previous != null && seq > previous + 1
    }
}
