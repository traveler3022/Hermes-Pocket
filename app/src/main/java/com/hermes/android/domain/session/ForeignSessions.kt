package com.hermes.android.domain.session

/**
 * Claude Code and Codex CLI sessions found on the machine Hermes runs on (their own log
 * folders, `~/.claude/projects` and `~/.codex/sessions`). Importing copies one into this
 * profile's history as a normal chat; importing it again opens the same chat.
 */
interface ForeignSessions {

    /** One page, newest first. [source] is `claude` or `codex`; null = both. */
    suspend fun list(source: String?, offset: Int): ForeignSessionPage

    /** The last messages of [id], to look at before importing. */
    suspend fun preview(id: String): ForeignSessionPreview

    /** Imports [id] (or finds the earlier import) and returns its Hermes session id. */
    suspend fun import(id: String): String
}

data class ForeignSessionPage(
    val sessions: List<ForeignSession>,
    /** Offset of the next page; null on the last one. */
    val nextOffset: Int?,
    /** The server machine's host name. */
    val host: String,
    /** Logs on this page Hermes could not read. */
    val unreadable: Int,
)

data class ForeignSession(
    /** An opaque handle; the server never takes a path from the client. */
    val id: String,
    val source: String,
    /** "Claude Code" or "Codex CLI". */
    val label: String,
    val title: String,
    val cwd: String?,
    /** Seconds since the epoch. */
    val modifiedAt: Long,
    val turnCount: Int,
    val excerpt: String,
)

data class ForeignSessionPreview(
    val messages: List<ForeignMessage>,
    val total: Int,
    /** Only the last messages came, or some were cut short. */
    val truncated: Boolean,
    /** The Hermes session of an earlier import, if there is one. */
    val importedSessionId: String?,
    val cwd: String?,
)

data class ForeignMessage(val role: String, val content: String)
