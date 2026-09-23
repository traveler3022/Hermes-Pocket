package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [findUserRow] aims every destructive rewind, so a wrong answer deletes turns the
 * user meant to keep. It must find the right row or none at all.
 */
class MessageRewindTest {

    private var seq = 0

    private fun local(text: String, rowId: Long? = null, queued: Boolean = false) =
        ChatMessage.User(id = "l${seq++}", timestamp = 0, text = text, rowId = rowId, queued = queued)

    private fun reply() = ChatMessage.Assistant(id = "a${seq++}", timestamp = 0, text = "ok", isStreaming = false, reasoning = null)

    private fun stored(text: String, rowId: Long) =
        ChatMessage.User(id = "s$rowId", timestamp = 0, text = text, rowId = rowId)

    @Test
    fun `a known row id is used as is`() {
        val target = local("two", rowId = 3)
        val server = listOf(stored("one", 1), stored("two", 3))

        assertEquals(3L, findUserRow(listOf(target), target, server))
    }

    @Test
    fun `a known row id the server no longer has finds nothing`() {
        val target = local("two", rowId = 3)

        assertNull(findUserRow(listOf(target), target, listOf(stored("one", 1))))
    }

    @Test
    fun `a message sent live is found by its text`() {
        val one = local("one"); val two = local("two"); val three = local("three")
        val chat = listOf(one, reply(), two, reply(), three, reply())
        val server = listOf(stored("one", 1), stored("two", 3), stored("three", 5))

        assertEquals(3L, findUserRow(chat, two, server))
    }

    @Test
    fun `a steer stored after a later turn does not shift the target`() {
        // Seen on a live gateway: the steer bubble sits after "two" on screen, but the
        // agent picked it up after "three", so counting positions aims at the wrong row.
        val two = local("two"); val steer = local("${STEER_PREFIX}and add !"); val three = local("three")
        val chat = listOf(local("one"), reply(), two, steer, reply(), three, reply())
        val server = listOf(stored("one", 1), stored("two", 3), stored("three", 5), stored("and add !", 7))

        assertEquals(3L, findUserRow(chat, two, server))
        assertEquals(7L, findUserRow(chat, steer, server))
        assertEquals(5L, findUserRow(chat, three, server))
    }

    @Test
    fun `repeated text is matched by its place among the repeats, from the end`() {
        val first = local("again"); val second = local("again")
        val chat = listOf(first, reply(), local("other"), reply(), second, reply())
        val server = listOf(stored("again", 1), stored("other", 3), stored("again", 5))

        assertEquals(1L, findUserRow(chat, first, server))
        assertEquals(5L, findUserRow(chat, second, server))
    }

    @Test
    fun `attachment references appended on the wire still match`() {
        val target = local("look at this")
        val server = listOf(stored("look at this\n[file: /tmp/a.txt]", 9))

        assertEquals(9L, findUserRow(listOf(target), target, server))
    }

    @Test
    fun `text the server never stored finds nothing`() {
        val target = local("never sent")

        assertNull(findUserRow(listOf(target), target, listOf(stored("one", 1))))
    }

    @Test
    fun `a message still waiting for Hermes is not counted among the repeats`() {
        val sent = local("hi"); val waiting = local("hi", queued = true)
        val chat = listOf(sent, reply(), waiting)

        assertEquals(1L, findUserRow(chat, sent, listOf(stored("hi", 1))))
    }

    @Test
    fun `turnsFrom counts the target and every user turn after it`() {
        val server = listOf(stored("one", 1), stored("two", 3), stored("three", 5))

        assertEquals(3, turnsFrom(server, 1))
        assertEquals(1, turnsFrom(server, 5))
        assertEquals(0, turnsFrom(server, 42))
    }

    @Test
    fun `a refused row falls back to the hidden rows since the previous shown turn, newest first`() {
        // Seen on a phone: 1843 "[System: The active model … changed]" (hidden by
        // session.history), 1844 the prompt, merged by the server into turn 1843.
        val server = listOf(stored("hi", 1839), stored("hi", 1841), stored("hi", 1844))

        assertEquals(listOf(1843L, 1842L), hiddenRowsBefore(server, 1844))
    }

    @Test
    fun `no gap before the target means nothing to fall back to`() {
        val server = listOf(stored("one", 5), stored("two", 6))

        assertEquals(emptyList<Long>(), hiddenRowsBefore(server, 6))
    }

    @Test
    fun `the fallback is capped`() {
        assertEquals((99L downTo 92L).toList(), hiddenRowsBefore(listOf(stored("x", 100)), 100))
    }
}
