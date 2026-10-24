package com.hermes.android.domain.session

import kotlinx.serialization.json.JsonElement

/**
 * Archived chats: out of the chat lists, messages kept on the server, back with one tap.
 * The flag lives on the server's session row, so every client sees the same archive.
 */
interface ArchivedChats {

    /** Archives [sessionId] (either id kind), or brings it back when [archived] is false. */
    suspend fun setArchived(sessionId: String, archived: Boolean)

    /** The archived chats, newest activity first, as `{sessions: [...]}` with session.list's row keys. */
    suspend fun archived(): JsonElement
}
