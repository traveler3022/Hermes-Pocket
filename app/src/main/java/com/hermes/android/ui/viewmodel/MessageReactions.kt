package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One emoji on a message: the user's, or the agent's own (react_to_message). */
data class MessageReaction(val emoji: String, val author: String) {
    val isMine: Boolean get() = author == AUTHOR_USER
}

internal const val AUTHOR_USER = "user"

/** Settings → Appearance → Message reactions (ThemeModeState): off unless the user turns it on, as on the desktop. */
const val MESSAGE_REACTIONS_PREF = "message_reactions"

/** The six iOS Tapback defaults in Apple's order, as the desktop offers them. */
val QUICK_REACTIONS = listOf("\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDE02", "\u203C\uFE0F", "\u2753")

/** Tapback semantics, the server's too: one reaction per author, the same emoji again retracts, null clears. */
internal fun applyReaction(
    reactions: List<MessageReaction>,
    emoji: String?,
    author: String = AUTHOR_USER,
): List<MessageReaction> {
    val previous = reactions.firstOrNull { it.author == author }
    val without = reactions.filter { it.author != author }
    return if (emoji.isNullOrBlank() || previous?.emoji == emoji) without else without + MessageReaction(emoji, author)
}

/** A `reactions` list as message.react, message.reaction and history carry it. */
internal fun parseReactions(element: JsonElement?): List<MessageReaction> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val emoji = (obj["emoji"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        MessageReaction(emoji, (obj["author"] as? JsonPrimitive)?.contentOrNull ?: AUTHOR_USER)
    }

/** The reactions a history message stores in its `display_metadata`, which may still be JSON text. */
internal fun reactionsOf(displayMetadata: JsonElement?): List<MessageReaction> {
    val meta = when (displayMetadata) {
        is JsonObject -> displayMetadata
        is JsonPrimitive -> displayMetadata.contentOrNull
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject }
        else -> null
    }
    return parseReactions(meta?.get("reactions"))
}

internal val ChatMessage.reactionsOrNull: List<MessageReaction>?
    get() = when (this) {
        is ChatMessage.User -> reactions
        is ChatMessage.Assistant -> reactions
        else -> null
    }

internal val ChatMessage.rowIdOrNull: Long?
    get() = when (this) {
        is ChatMessage.User -> rowId
        is ChatMessage.Assistant -> rowId
        else -> null
    }

/** [this] with [reactions], and with [rowId] once it is known. */
internal fun ChatMessage.withReactions(reactions: List<MessageReaction>, rowId: Long? = null): ChatMessage = when (this) {
    is ChatMessage.User -> copy(reactions = reactions, rowId = rowId ?: this.rowId)
    is ChatMessage.Assistant -> copy(reactions = reactions, rowId = rowId ?: this.rowId)
    else -> this
}

/**
 * The durable row of the reply [target] among the server's replies [server], or null
 * when it cannot be told apart for certain. Like [findUserRow]: the row id wins when
 * the bubble has one, otherwise the reply is found by its text, counting repeats of
 * the same text from the end on both sides.
 */
internal fun findAssistantRow(
    local: List<ChatMessage>,
    target: ChatMessage.Assistant,
    server: List<ChatMessage.Assistant>,
): Long? {
    target.rowId?.let { known -> return known.takeIf { id -> server.any { it.rowId == id } } }
    val said = target.text.trim()
    if (said.isEmpty()) return null
    val sameLocal = local.filterIsInstance<ChatMessage.Assistant>().filter { it.text.trim() == said }
    val fromEnd = sameLocal.size - 1 - sameLocal.indexOfFirst { it.id == target.id }
    if (fromEnd !in sameLocal.indices) return null
    val sameServer = server.filter { it.rowId != null && it.text.trim() == said }
    return sameServer.getOrNull(sameServer.size - 1 - fromEnd)?.rowId
}
