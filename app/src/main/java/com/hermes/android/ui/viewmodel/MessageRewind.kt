package com.hermes.android.ui.viewmodel

/** Prefix the chat puts on a message sent mid-turn (session.steer); not part of what was sent. */
internal const val STEER_PREFIX = "↳ "

/** What [message] said on the wire: steered text loses the chat's marker. */
internal fun sentText(message: ChatMessage.User): String = message.text.removePrefix(STEER_PREFIX).trim()

/**
 * The durable row of [target] among the server's user turns [server], or null when
 * it cannot be told apart for certain.
 *
 * Edit, delete and retry all cut the stored transcript at this row, and a wrong row
 * destroys turns the user meant to keep, so position is never trusted: a steered
 * message lands in the stored history wherever the agent picked it up, not where
 * its bubble sits, and messages sent live carry no row id until the chat reloads.
 * The row id wins when the bubble has one; otherwise the turn is found by its text,
 * counting repeats of the same text from the end on both sides.
 */
internal fun findUserRow(
    local: List<ChatMessage>,
    target: ChatMessage.User,
    server: List<ChatMessage.User>,
): Long? {
    target.rowId?.let { known -> return known.takeIf { id -> server.any { it.rowId == id } } }
    val said = sentText(target)
    if (said.isEmpty()) return null
    val sameLocal = local.filterIsInstance<ChatMessage.User>()
        .filter { !it.queued && sentText(it) == said }
    val fromEnd = sameLocal.size - 1 - sameLocal.indexOfFirst { it.id == target.id }
    if (fromEnd !in sameLocal.indices) return null
    // Attachment references are appended to the text a message is sent with.
    val sameServer = server.filter { it.rowId != null && it.text.trim().let { t -> t == said || t.startsWith("$said\n") } }
    return sameServer.getOrNull(sameServer.size - 1 - fromEnd)?.rowId
}

/** How many user turns sit at or after [rowId] — the undos that remove it. */
internal fun turnsFrom(server: List<ChatMessage.User>, rowId: Long): Int {
    val index = server.indexOfFirst { it.rowId == rowId }
    return if (index < 0) 0 else server.size - index
}

/**
 * The rows a rewind at [rowId] falls back to when the server refuses it (4018), newest
 * first: the ids strictly between the previous user turn the server shows and [rowId].
 *
 * session.history hides "[System: …]" user rows — a model switch writes one just before
 * the next prompt — while the rewind lookup merges a user;user pair into ONE turn that
 * keeps the FIRST row's id. The bubble's own row is then unaddressable and the turn is
 * only reachable through the hidden row before it (seen: marker 1843, prompt 1844 → 4018).
 * Any id in the gap that is not a user turn is refused before anything is written, and
 * none can reach a turn the user sees, so trying them is safe.
 */
internal fun hiddenRowsBefore(server: List<ChatMessage.User>, rowId: Long, max: Int = 8): List<Long> {
    val previous = server.mapNotNull { it.rowId }.filter { it < rowId }.maxOrNull() ?: 0L
    return ((rowId - 1) downTo (previous + 1)).take(max)
}

/** What the user is told when a change to the chat (an edit, a retry, a delete) is refused. */
internal fun rewindFailure(e: Exception): String =
    if (e.message.orEmpty().startsWith("RPC error 4009:")) {
        "Hermes is still finishing the last reply. Try again in a moment."
    } else {
        "Could not change the chat: ${e.message}"
    }
