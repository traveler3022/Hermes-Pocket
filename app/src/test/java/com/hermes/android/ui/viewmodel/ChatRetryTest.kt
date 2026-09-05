package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Retry resubmits the last USER TURN. Steer lines are drawn like user
 * messages but are not turns, and treating them as such resent the nudge and
 * rewound the server one turn too far.
 */
class ChatRetryTest {

    private fun user(id: String, text: String, steer: Boolean = false) =
        ChatMessage.User(id = id, timestamp = 0L, text = text, isSteer = steer)

    private fun assistant(id: String) =
        ChatMessage.Assistant(id = id, timestamp = 0L, text = "ok", isStreaming = false, reasoning = null)

    @Test
    fun `ordinal counts user turns from zero`() {
        val target = listOf(
            user("u1", "first"), assistant("a1"),
            user("u2", "second"), assistant("a2"),
        ).retryTarget()

        assertEquals("second", target?.message?.text)
        assertEquals(1, target?.ordinal)
        assertEquals(2, target?.index)
    }

    @Test
    fun `a steer is neither resent nor counted`() {
        val messages = listOf(
            user("u1", "first"), assistant("a1"),
            user("u2", "second"),
            user("s1", "↳ hurry up", steer = true),
            assistant("a2"),
        )
        val target = messages.retryTarget()

        assertEquals("second", target?.message?.text)
        // Counting the steer would have said ordinal 2 and rewound past the
        // turn the user actually wants retried.
        assertEquals(1, target?.ordinal)
        assertEquals(2, target?.index)
    }

    @Test
    fun `the first turn is ordinal zero`() {
        val target = listOf(user("u1", "only")).retryTarget()
        assertEquals(0, target?.ordinal)
    }

    @Test
    fun `no user turn to retry`() {
        assertNull(listOf(assistant("a1")).retryTarget())
        assertNull(listOf(user("s1", "↳ nudge", steer = true)).retryTarget())
        assertNull(emptyList<ChatMessage>().retryTarget())
    }
}
