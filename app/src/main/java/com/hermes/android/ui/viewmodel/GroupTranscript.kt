package com.hermes.android.ui.viewmodel

import com.hermes.android.data.GroupsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One row of a Group Chat as the room screen shows it. */
sealed interface GroupRow {
    val key: String

    data class User(override val key: String, val text: String) : GroupRow

    data class Member(
        override val key: String,
        val memberId: String,
        val name: String,
        val text: String,
    ) : GroupRow

    /** A line from the room itself: a member could not answer, the user stopped it, a cap was reached. */
    data class Note(override val key: String, val note: GroupNote) : GroupRow
}

sealed interface GroupNote {
    data class Failed(val member: String, val error: String) : GroupNote

    data class Waiting(val member: String) : GroupNote

    data object Stopped : GroupNote

    /** The ten-reply or three-round cap ended this message's discussion. */
    data object Capped : GroupNote
}

/**
 * Turns the room log into rows. Pure, so the order and the wording rules stay testable:
 * the log is the truth, and the screen is a projection of it.
 *
 * Shown: what the user and the members said, and the room's own outcomes the user
 * should know about. Left out: passes (a member with nothing to add says nothing),
 * settled turns, renames (the title shows the name), turns cancelled because a newer
 * message replaced them, and the gateway's authority bookkeeping.
 */
internal object GroupTranscript {

    fun rows(events: List<GroupsRepository.Event>, members: List<GroupsRepository.Member>): List<GroupRow> {
        val names = members.associate { it.memberId to it.title }
        fun nameOf(payload: JsonObject) = payload.str("member_id").let { names[it] ?: it }
        return events.sortedBy { it.seq }.mapNotNull { event ->
            val key = "e${event.seq}"
            val payload = event.payload
            when (event.kind) {
                "message.user" -> GroupRow.User(key, payload.str("text"))
                "message.member" -> GroupRow.Member(key, payload.str("member_id"), nameOf(payload), payload.str("text"))
                "turn.failed" -> GroupRow.Note(key, GroupNote.Failed(nameOf(payload), payload.str("error")))
                "turn.deferred" -> GroupRow.Note(key, GroupNote.Waiting(nameOf(payload)))
                "room.stop_requested" -> GroupRow.Note(key, GroupNote.Stopped)
                "room.activity" -> if (payload.str("status") == "bounded") GroupRow.Note(key, GroupNote.Capped) else null
                else -> null
            }
        }
    }

    /**
     * Whether the newest user message is still being discussed: the room writes a
     * `room.activity` (settled or capped) when a discussion ends, and a stop ends it too.
     */
    fun awaitingReplies(events: List<GroupsRepository.Event>): Boolean {
        val lastUser = events.lastOrNull { it.kind == "message.user" } ?: return false
        return events.none {
            it.seq > lastUser.seq && (it.kind == "room.activity" || it.kind == "room.stop_requested")
        }
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
}

/** `@` completion for the room composer. */
internal object GroupMentions {

    /** The `@word` being typed at the end of [input], without the `@`; null when none is. */
    fun query(input: String): String? {
        val word = input.substringAfterLast(' ').substringAfterLast('\n')
        return if (word.startsWith("@") && word.drop(1).all { it.isLetterOrDigit() || it in "._:-" }) word.drop(1) else null
    }

    /** Handles to offer for [query]: matching members, then `all` (everyone answers). */
    fun suggestions(query: String, members: List<GroupsRepository.Member>): List<String> {
        val q = query.lowercase()
        val matching = members.filter { it.handle.lowercase().startsWith(q) || it.title.lowercase().contains(q) }
            .map { it.handle }
        return (matching + listOf(ALL).filter { it.startsWith(q) }).distinct()
    }

    /** [input] with the `@word` being typed replaced by `@handle `. */
    fun complete(input: String, handle: String): String {
        val query = query(input) ?: return input
        return input.dropLast(query.length + 1) + "@$handle "
    }

    const val ALL = "all"
}
