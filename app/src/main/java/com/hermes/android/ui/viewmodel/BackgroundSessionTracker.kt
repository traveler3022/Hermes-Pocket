package com.hermes.android.ui.viewmodel

/** What a chat is doing while the user is looking at a different one. */
data class SessionActivity(
    val isRunning: Boolean = false,
    val unreadReplies: Int = 0,
    val preview: String = "",
    /** The last turn ended in an error the user has not looked at yet. */
    val failed: Boolean = false,
)

/**
 * Turn state for the chats that are not on screen.
 *
 * The gateway streams every live session down the one socket tagged with its
 * own session id, so a chat keeps running while the user reads another one —
 * the same way each Telegram forum topic gets its own session key and its own
 * agent. The transcript itself is authoritative on the server and reloaded on
 * switch, so this only has to answer "is that chat busy, and did it reply
 * while I was away".
 */
internal class BackgroundSessionTracker {

    private val activity = LinkedHashMap<String, SessionActivity>()
    private val streamed = HashMap<String, StringBuilder>()

    fun snapshot(): Map<String, SessionActivity> = LinkedHashMap(activity)

    fun onTurnStart(sessionId: String) {
        if (sessionId.isBlank()) return
        streamed.remove(sessionId)
        activity[sessionId] = (activity[sessionId] ?: SessionActivity())
            .copy(isRunning = true, preview = "", failed = false)
    }

    /** The gateway reported an error for this chat's turn. */
    fun onError(sessionId: String) {
        if (sessionId.isBlank()) return
        streamed.remove(sessionId)
        activity[sessionId] = (activity[sessionId] ?: SessionActivity())
            .copy(isRunning = false, failed = true)
    }

    fun onDelta(sessionId: String, text: String) {
        if (sessionId.isBlank() || text.isEmpty()) return
        val buffer = streamed.getOrPut(sessionId) { StringBuilder() }
        buffer.append(text)
        // A long turn would otherwise grow this without bound; only the tail
        // is ever shown.
        if (buffer.length > MAX_BUFFER) buffer.delete(0, buffer.length - MAX_BUFFER)
        activity[sessionId] = (activity[sessionId] ?: SessionActivity())
            .copy(isRunning = true, preview = buffer.toString().toPreview())
    }

    /** [isActive] = the user is looking at this chat, so its reply is not unread. */
    fun onTurnEnd(sessionId: String, finalText: String, isActive: Boolean) {
        if (sessionId.isBlank()) return
        val current = activity[sessionId] ?: SessionActivity()
        val text = finalText.ifBlank { streamed[sessionId]?.toString() ?: "" }
        streamed.remove(sessionId)
        activity[sessionId] = current.copy(
            isRunning = false,
            preview = text.toPreview(),
            unreadReplies = if (isActive) 0 else current.unreadReplies + 1,
        )
    }

    /**
     * The server reports [sessionId] not running. A chat still shown busy lost
     * its message.complete (it went out while the socket was down, or before a
     * re-attach read `running` as still set); end its turn. Returns whether
     * anything changed.
     */
    fun onSettled(sessionId: String, isActive: Boolean): Boolean {
        if (activity[sessionId]?.isRunning != true) return false
        onTurnEnd(sessionId, "", isActive)
        return true
    }

    /** The user opened this chat: its replies are no longer unread, and its failure has been seen. */
    fun markRead(sessionId: String) {
        if (sessionId.isBlank()) return
        val current = activity[sessionId] ?: return
        if (current.unreadReplies == 0 && !current.failed) return
        activity[sessionId] = current.copy(unreadReplies = 0, failed = false)
    }

    /** Drop a deleted session so its badge cannot outlive it. */
    fun forget(sessionId: String) {
        activity.remove(sessionId)
        streamed.remove(sessionId)
    }

    private fun String.toPreview(): String =
        trim().replace(WHITESPACE, " ").takeLast(PREVIEW_CHARS)

    private companion object {
        private const val MAX_BUFFER = 2000
        private const val PREVIEW_CHARS = 120
        private val WHITESPACE = Regex("\\s+")
    }
}
