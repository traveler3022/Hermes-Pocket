package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveredTranscriptTest {

    private fun user(id: String, text: String) = ChatMessage.User(id = id, timestamp = 0L, text = text)

    private fun assistant(id: String, text: String, streaming: Boolean = false) =
        ChatMessage.Assistant(id = id, timestamp = 0L, text = text, isStreaming = streaming, reasoning = null)

    @Test
    fun `the server snapshot replaces a transcript that missed the reply`() {
        val local = listOf(user("l1", "hi"), assistant("l2", "Hel", streaming = true))
        val snapshot = listOf(user("s1", "hi"), assistant("s2", "Hello there"))

        assertEquals(snapshot, mergeRecoveredTranscript(snapshot, local))
    }

    @Test
    fun `a message the server never received stays on screen`() {
        val local = listOf(user("l1", "hi"), assistant("l2", "Hello"), user("l3", "are you there?"))
        val snapshot = listOf(user("s1", "hi"), assistant("s2", "Hello"))

        assertEquals(snapshot + local.last(), mergeRecoveredTranscript(snapshot, local))
    }

    @Test
    fun `a message the server did receive is not shown twice`() {
        val local = listOf(user("l1", "hi"), assistant("l2", "Hello"), user("l3", "status?"))
        val snapshot = listOf(user("s1", "hi"), assistant("s2", "Hello"), user("s3", "status?"))

        assertEquals(snapshot, mergeRecoveredTranscript(snapshot, local))
    }

    @Test
    fun `an empty snapshot keeps the local transcript`() {
        val local = listOf(user("l1", "hi"), assistant("l2", "Hello"))

        assertEquals(local, mergeRecoveredTranscript(emptyList(), local))
    }
}
