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
