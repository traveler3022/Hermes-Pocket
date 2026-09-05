package com.hermes.android.ui.screen

import com.hermes.android.ui.viewmodel.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [buildTurnWork] decides two things at once — what a trace contains, and which
 * assistant messages survive on the chat surface — so these cover both.
 */
class TurnWorkTest {

    private var seq = 0

    private fun user(text: String = "q") =
        ChatMessage.User(id = "u${seq++}", timestamp = seq.toLong(), text = text)

    private fun assistant(
        id: String,
        text: String = "",
        reasoning: String? = null,
    ) = ChatMessage.Assistant(
        id = id,
        timestamp = seq++.toLong(),
        text = text,
        isStreaming = false,
        reasoning = reasoning,
    )

    private fun tool(id: String, name: String = "bash") = ChatMessage.ToolCall(
        id = id,
        timestamp = seq++.toLong(),
        toolName = name,
        argsText = null,
        resultText = "ok",
        error = null,
        isRunning = false,
        durationS = 1.0,
    )

    // ── Folded: a turn collapses onto the message that ends it ───────────────

    private fun question(id: String, text: String = "which one?") =
        ChatMessage.InteractiveRequest(
            id = id,
            timestamp = seq++.toLong(),
            requestId = id,
            question = text,
            choices = listOf("a", "b"),
        )

    @Test
    fun `a question to the user ends the turn like a user message does`() {
        // Answering a clarify card adds no user message, so without this the
        // whole exchange stayed one turn: the message that asked the question
        // folded into whatever the agent said after the answer, and the reader
        // lost both the question's explanation and everything before it.
        val messages = listOf(
            user(),
            assistant("a1", text = "I need to know something"),
            question("q1"),
            assistant("a2", text = "done"),
        )

        val work = buildTurnWork(messages, foldNarration = true)

        assertEquals(setOf("a1", "a2"), work.keys)
    }

    @Test
    fun `work after a question is not filed under the message that asked`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "which file?"),
            question("q1"),
            tool("t1"),
            assistant("a2", text = "patched it"),
        )

        val work = buildTurnWork(messages, foldNarration = true)

        assertTrue(work.getValue("a1").isEmpty())
        assertEquals(listOf("t1"), work.getValue("a2").filterIsInstance<HxTraceItem.Tool>().map { it.call.id })
    }

    @Test
    fun `unfolded, a question closes the previous message's work`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "which file?"),
            question("q1"),
            tool("t1"),
            assistant("a2", text = "patched it"),
        )

        val work = buildTurnWork(messages, foldNarration = false)

        assertTrue(work.getValue("a1").isEmpty())
    }

    @Test
    fun `folded turn keeps only its ending message`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "let me look"),
            tool("t1"),
            assistant("a2", text = "found it"),
        )

        val work = buildTurnWork(messages, foldNarration = true)

        assertEquals(setOf("a2"), work.keys)
    }

    @Test
    fun `folded turn keeps narration, reasoning and tools in arrival order`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "let me look", reasoning = "checking"),
            tool("t1"),
            assistant("a2", text = "found it"),
        )

        val items = buildTurnWork(messages, foldNarration = true).getValue("a2")

        assertEquals(
            listOf("Reasoning:checking", "Note:let me look", "Tool:t1"),
            items.map(::describe),
        )
    }

    @Test
    fun `the ending message's own text is the answer, never a note`() {
        val messages = listOf(user(), assistant("a1", text = "the answer"))

        val items = buildTurnWork(messages, foldNarration = true).getValue("a1")

        assertTrue(items.none { it is HxTraceItem.Note })
    }

    @Test
    fun `work never crosses a question boundary`() {
        val messages = listOf(
            user("first"),
            assistant("a1", text = "one"),
            tool("t1"),
            user("second"),
            assistant("a2", text = "two"),
            tool("t2"),
        )

        val work = buildTurnWork(messages, foldNarration = true)

        assertEquals(listOf("Tool:t1"), work.getValue("a1").map(::describe))
        assertEquals(listOf("Tool:t2"), work.getValue("a2").map(::describe))
    }

    // ── Unfolded: every message stays, carrying only its own work ────────────

    @Test
    fun `unfolded keeps every assistant message on the surface`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "let me look"),
            tool("t1"),
            assistant("a2", text = "found it"),
        )

        val work = buildTurnWork(messages, foldNarration = false)

        assertEquals(setOf("a1", "a2"), work.keys)
    }

    @Test
    fun `unfolded still gives each message its own reasoning and tools`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "let me look", reasoning = "checking"),
            tool("t1"),
            assistant("a2", text = "found it", reasoning = "done"),
        )

        val work = buildTurnWork(messages, foldNarration = false)

        assertEquals(listOf("Reasoning:checking", "Tool:t1"), work.getValue("a1").map(::describe))
        assertEquals(listOf("Reasoning:done"), work.getValue("a2").map(::describe))
    }

    @Test
    fun `unfolded turns no narration into notes — it is visible in the chat`() {
        val messages = listOf(
            user(),
            assistant("a1", text = "let me look"),
            assistant("a2", text = "found it"),
        )

        val work = buildTurnWork(messages, foldNarration = false)

        assertTrue(work.values.flatten().none { it is HxTraceItem.Note })
    }

    // ── Shared: a message with no work still holds its place ────────────────

    @Test
    fun `a message that did no work is still listed, so it stays visible`() {
        val messages = listOf(user(), assistant("a1", text = "hello"))

        listOf(true, false).forEach { folded ->
            val work = buildTurnWork(messages, foldNarration = folded)
            assertTrue("foldNarration=$folded", work.containsKey("a1"))
            assertEquals("foldNarration=$folded", emptyList<HxTraceItem>(), work.getValue("a1"))
        }
    }

    @Test
    fun `a tool with no assistant before it is dropped rather than misattributed`() {
        val messages = listOf(user(), tool("t1"), assistant("a1", text = "hi"))

        val folded = buildTurnWork(messages, foldNarration = true)
        val unfolded = buildTurnWork(messages, foldNarration = false)

        // Folded, the turn owns it — the ending message is that turn.
        assertEquals(listOf("Tool:t1"), folded.getValue("a1").map(::describe))
        // Unfolded, nothing preceded it to carry it.
        assertEquals(emptyList<String>(), unfolded.getValue("a1").map(::describe))
    }

    private fun describe(item: HxTraceItem): String = when (item) {
        is HxTraceItem.Note -> "Note:${item.text}"
        is HxTraceItem.Reasoning -> "Reasoning:${item.text}"
        is HxTraceItem.Tool -> "Tool:${item.call.id}"
    }
}
