package com.hermes.android.ui.viewmodel

import com.hermes.android.data.GroupsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room log → rows, in the event shapes a 0.21.4 gateway writes (`gateway/hosted_room_discussion.py`). */
class GroupTranscriptTest {

    private val members = listOf(
        GroupsRepository.Member("m-default", "default", "hermes", "Hermes"),
        GroupsRepository.Member("m-critic", "critic", "critic", ""),
    )

    private fun event(seq: Long, kind: String, vararg payload: Pair<String, Any?>) = GroupsRepository.Event(
        seq = seq,
        kind = kind,
        actorKind = "",
        actorId = "",
        payload = JsonObject(payload.associate { (k, v) -> k to JsonPrimitive(v?.toString()) }),
        createdAt = 0.0,
    )

    @Test
    fun `what was said shows in log order, with each member's name`() {
        val rows = GroupTranscript.rows(
            listOf(
                event(2, "message.member", "member_id" to "m-default", "text" to "Saturday?"),
                event(1, "message.user", "text" to "Plan a picnic", "thread_id" to "main"),
                event(4, "message.member", "member_id" to "m-critic", "text" to "Bring water."),
            ),
            members,
        )
        assertEquals(
            listOf(
                GroupRow.User("e1", "Plan a picnic"),
                GroupRow.Member("e2", "m-default", "Hermes", "Saturday?"),
                // No display name: the handle stands in.
                GroupRow.Member("e4", "m-critic", "critic", "Bring water."),
            ),
            rows,
        )
    }

    @Test
    fun `passes, settled turns, renames and superseded turns stay out`() {
        val rows = GroupTranscript.rows(
            listOf(
                event(3, "turn.settled", "member_id" to "m-critic", "passed" to true),
                event(5, "room.renamed", "name" to "Picnic"),
                event(6, "turn.cancelled", "member_id" to "m-critic", "reason" to "superseded_by_newer_user_event"),
                event(7, "room.activity", "status" to "settled", "reason_code" to "silent_round"),
                event(8, "authority.claimed"),
            ),
            members,
        )
        assertTrue(rows.toString(), rows.isEmpty())
    }

    @Test
    fun `the room's own outcomes become notes`() {
        val rows = GroupTranscript.rows(
            listOf(
                event(1, "turn.failed", "member_id" to "m-critic", "error" to "rate limited"),
                event(2, "turn.deferred", "member_id" to "m-default", "reason" to "member_unavailable"),
                event(3, "room.stop_requested", "cancel_id" to "app-stop-1"),
                event(4, "room.activity", "status" to "bounded", "reason_code" to "max_messages"),
            ),
            members,
        ).map { (it as GroupRow.Note).note }
        assertEquals(
            listOf(
                GroupNote.Failed("critic", "rate limited"),
                GroupNote.Waiting("Hermes"),
                GroupNote.Stopped,
                GroupNote.Capped,
            ),
            rows,
        )
    }

    @Test
    fun `a message is being answered until the room settles it or it is stopped`() {
        val asked = listOf(event(1, "message.user", "text" to "hi"))
        assertTrue(GroupTranscript.awaitingReplies(asked))
        assertTrue(GroupTranscript.awaitingReplies(asked + event(2, "message.member", "member_id" to "m-default", "text" to "hey")))
        assertFalse(GroupTranscript.awaitingReplies(asked + event(3, "room.activity", "status" to "settled")))
        assertFalse(GroupTranscript.awaitingReplies(asked + event(3, "room.stop_requested")))
        // A newer message reopens it.
        assertTrue(
            GroupTranscript.awaitingReplies(
                asked + event(3, "room.activity", "status" to "settled") + event(4, "message.user", "text" to "and?"),
            ),
        )
        assertFalse(GroupTranscript.awaitingReplies(emptyList()))
    }

    @Test
    fun `typing @ offers the members, then everyone`() {
        assertNull(GroupMentions.query("hello there"))
        assertEquals("", GroupMentions.query("ask @"))
        assertEquals("cr", GroupMentions.query("ask @cr"))
        assertEquals(listOf("hermes", "critic", "all"), GroupMentions.suggestions("", members))
        assertEquals(listOf("critic"), GroupMentions.suggestions("cr", members))
        assertEquals(listOf("all"), GroupMentions.suggestions("a", members))
        assertEquals("ask @critic ", GroupMentions.complete("ask @cr", "critic"))
    }
}
