package com.hermes.android.domain.session

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A chat's sessions as the chat, sessions and model screens use them; SessionRepository
 * owns the live/stored id semantics behind it.
 */
interface ChatSessions {

    /** Attach to a session given EITHER id kind (stored ids resume, live ids activate). */
    suspend fun attach(sessionId: String, preferLive: Boolean = false, trackSession: Boolean = true): AttachedSession

    /** Runs [call] on [liveId], re-attaching through [storedId] once when the gateway dropped it. */
    suspend fun <T> onLiveSession(
        liveId: String,
        storedId: String?,
        onRebound: (AttachedSession) -> Unit,
        call: suspend (liveId: String) -> T,
    ): T

    /** The STORED id of the chat the server used last, or null when it has none. */
    suspend fun mostRecentId(): String?

    /** A new chat, working in [cwd] when given; null when the server named no session. */
    suspend fun createChat(cwd: String? = null): AttachedSession?

    /** Lends [block] a throwaway session, closed after. */
    suspend fun <T> withScratchSession(block: suspend (liveId: String) -> T): T

    /** The server's stored chats, as session.list sends them. */
    suspend fun storedChats(): JsonElement

    /** The transcript of a LIVE session as the server stores it, row ids included. */
    suspend fun history(liveId: String): JsonElement

    /** A new chat that goes on from a copy of [liveId]'s; its id, or null when none came back. */
    suspend fun branch(liveId: String): String?

    /** Token use of a LIVE session. */
    suspend fun usage(liveId: String): JsonObject?

    /** Hands [text] to the turn running in [liveId]. */
    suspend fun steer(liveId: String, text: String): SteerResult

    /** Stops the turn running in [liveId], then the background processes it started. */
    suspend fun stopTurn(liveId: String)

    /** A chat's raw history rows, and the live id they were read through. */
    suspend fun storedMessages(sessionId: String): StoredMessages

    suspend fun rename(sessionId: String, title: String)

    suspend fun reasoningLevel(liveSessionId: String?): String

    /** Session-scoped with a live id, the global default without one; returns the level set. */
    suspend fun setReasoningLevel(rawLevel: String, liveSessionId: String?, persistGlobal: Boolean = true): String

    /** Pops the last user+assistant/tool turn from a LIVE session. Returns count removed. */
    suspend fun undoLastTurn(liveSessionId: String): Int
}

/**
 * A session the client is attached to: [liveId] is what every subsequent
 * RPC/event speaks; [raw] is the full server payload (messages included)
 * for the transcript parser.
 */
data class AttachedSession(
    val liveId: String,
    val raw: JsonObject,
) {
    /** The stored id the server bound [liveId] to, when the payload names it. */
    val storedId: String?
        get() = (raw["stored_session_id"] ?: raw["session_key"])
            .let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
}

/** What session.steer made of a message sent mid-turn: taken, or refused with the server's [note]. */
data class SteerResult(val accepted: Boolean, val note: String?)

/** A chat's raw history rows, and the live id they were read through. */
data class StoredMessages(val liveId: String, val messages: JsonArray?)
