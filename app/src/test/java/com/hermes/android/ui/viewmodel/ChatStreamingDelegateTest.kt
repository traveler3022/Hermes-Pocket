package com.hermes.android.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStreamingDelegateTest {

    private val state = MutableStateFlow(ChatUiState())
    private val delegate = ChatStreamingDelegate(CoroutineScope(Dispatchers.Unconfined), state)

    private fun startTurn(): String {
        val id = delegate.onMessageStart()
        state.value = state.value.copy(
            messages = state.value.messages + ChatMessage.Assistant(
                id = id,
                timestamp = 0L,
                text = "",
                isStreaming = true,
                reasoning = null,
            ),
        )
        return id
    }

    private fun assistants() = state.value.messages.filterIsInstance<ChatMessage.Assistant>()

    @Test
    fun `sealing an interim closes the live bubble and hands back a new id`() {
        val first = startTurn()
        delegate.enqueueDelta("Checking the logs.")

        val next = delegate.sealInterim("Checking the logs.")

        assertEquals(1, assistants().size)
        assertEquals("Checking the logs.", assistants()[0].text)
        assertFalse(assistants()[0].isStreaming)
        assertNotEquals(first, next)
        assertEquals(next, delegate.currentAssistantMessageId)
    }

    @Test
    fun `interim text the gateway never streamed still lands in the bubble`() {
        startTurn()

        delegate.sealInterim("Running the migration first.")

        assertEquals("Running the migration first.", assistants()[0].text)
    }

    @Test
    fun `an interim with nothing streaming is dropped`() {
        assertNull(delegate.sealInterim("stray"))
        assertNull(delegate.currentAssistantMessageId)
    }

    @Test
    fun `a blank interim is a no-op`() {
        startTurn()
        delegate.enqueueDelta("Working")

        assertNull(delegate.sealInterim("   "))
        assertEquals(true, assistants()[0].isStreaming)
    }

    @Test
    fun `a previewed final answer drops the text already sealed on screen`() {
        startTurn()
        delegate.sealInterim("The answer is 42.")

        assertEquals("", delegate.withoutSealedInterims("The answer is 42."))
        assertEquals("And here is why.", delegate.withoutSealedInterims("The answer is 42.\n\nAnd here is why."))
    }

    @Test
    fun `reset forgets the sealed interims of the finished turn`() {
        startTurn()
        delegate.sealInterim("The answer is 42.")

        delegate.reset()

        assertEquals("The answer is 42.", delegate.withoutSealedInterims("The answer is 42."))
    }
}
