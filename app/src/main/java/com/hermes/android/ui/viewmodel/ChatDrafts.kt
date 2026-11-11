package com.hermes.android.ui.viewmodel

/**
 * The composer's unsent text, kept per chat. One global draft followed the user from chat to
 * chat, so text typed in one chat could be sent to another.
 *
 * Keyed by the chat's stored id (the live id when none is known yet); a chat not created yet
 * uses [NEW_CHAT], moved to the real key once the chat gets its id. [read]/[write] are the
 * storage (null writes delete). Pure, so it is unit-tested without Android.
 */
internal class ChatDrafts(
    private val read: (key: String) -> String?,
    private val write: (key: String, text: String?) -> Unit,
) {
    /** The open chat as the drafts see it. */
    data class Chat(val liveId: String?, val storedId: String?) {
        val key: String get() = storedId ?: liveId ?: NEW_CHAT
        val isNew: Boolean get() = liveId == null && storedId == null
    }

    /** The key the composer's text belongs to; null until the first chat is known. */
    var owner: String? = null
        private set
    private var last: Chat? = null

    /**
     * The open chat changed to [chat] while the composer held [text]. [created]: [chat] is the
     * new chat that was just made, so the text typed in it stays. Returns the composer's new
     * text, or null to leave it as it is.
     */
    fun onChatChanged(chat: Chat, text: String, created: Boolean): String? {
        val previous = last
        last = chat
        val key = chat.key
        val current = owner
        if (current == key) return null
        if (current == null) {
            // Text from before any chat was known (the old single draft) waits for a real chat.
            if (chat.isNew && text.isNotEmpty()) return null
            owner = key
            if (text.isEmpty()) return read(key).orEmpty()
            write(key, text)
            return null
        }
        val sameChat = previous != null && (
            (previous.liveId != null && previous.liveId == chat.liveId) ||
                (previous.storedId != null && previous.storedId == chat.storedId) ||
                (created && previous.isNew)
            )
        if (sameChat) {
            // The same chat under a better key (its stored id arrived, or it was just created).
            val moved = read(current)
            write(current, null)
            if (moved != null) write(key, moved)
            owner = key
            return null
        }
        write(current, text)
        owner = key
        return read(key).orEmpty()
    }

    /** Stores [text] as the open chat's draft. */
    fun save(text: String) {
        owner?.let { write(it, text) }
    }

    /** The open chat's draft was sent. */
    fun clear() {
        owner?.let { write(it, null) }
    }

    companion object {
        const val NEW_CHAT = "__new_chat__"
    }
}
