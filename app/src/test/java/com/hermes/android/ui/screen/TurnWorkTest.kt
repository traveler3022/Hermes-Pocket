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
    fun `a tool with no assistant before it is not misattributed`() {
        val messages = listOf(user(), tool("t1"), assistant("a1", text = "hi"))

        val folded = buildTurnWork(messages, foldNarration = true)
        val unfolded = buildTurnWork(messages, foldNarration = false)

        // Folded, the turn owns it — the ending message is that turn.
        assertEquals(listOf("Tool:t1"), folded.getValue("a1").map(::describe))
        // Unfolded, nothing preceded it to carry it; it stays a card instead.
        assertEquals(emptyList<String>(), unfolded.getValue("a1").map(::describe))
    }

    // ── What stays on the surface ────────────────────────────────────────────

    private fun visible(messages: List<ChatMessage>, folded: Boolean) =
        visibleChatMessages(messages, buildTurnWork(messages, folded), searching = false).map { it.id }

    @Test
    fun `a turn with no assistant message still shows its tools`() {
        // Cut off mid-tool, or a turn that ended on a tool: nothing to fold into.
        val messages = listOf(user(), tool("t1"), tool("t2"))

        listOf(true, false).forEach { folded ->
            assertEquals("foldNarration=$folded", listOf("u0", "t1", "t2"), visible(messages, folded))
        }
    }

    @Test
    fun `unfolded, a tool before the turn's first message stays on the surface`() {
        val messages = listOf(user(), tool("t1"), assistant("a1", text = "hi"))

        assertEquals(listOf("u0", "t1", "a1"), visible(messages, folded = false))
    }

    @Test
    fun `a tool a trace carries leaves the flow`() {
        val messages = listOf(user(), assistant("a1", text = "look"), tool("t1"), assistant("a2", text = "done"))

        assertEquals(listOf("u0", "a2"), visible(messages, folded = true))
        assertEquals(listOf("u0", "a1", "a2"), visible(messages, folded = false))
    }

    @Test
    fun `a search folds nothing`() {
        val messages = listOf(user(), assistant("a1", text = "look"), tool("t1"), assistant("a2", text = "done"))

        assertEquals(messages, visibleChatMessages(messages, emptyMap(), searching = true))
    }

    @Test
    fun `the cache hands back the same list for an unchanged trace and a new one for a changed trace`() {
        val cache = TurnWorkCache()
        val first = listOf(user(), assistant("a1", reasoning = "r"), user(), assistant("a2", reasoning = "x"))
        val before = cache.build(first, foldNarration = true)

        val grown = first.dropLast(1) + assistant("a2", reasoning = "xy")
        val after = cache.build(grown, foldNarration = true)

        assertTrue(before.getValue("a1") === after.getValue("a1"))
        assertEquals(listOf("Reasoning:xy"), after.getValue("a2").map(::describe))
    }

    @Test
    fun `the window starts at the n-th question from the end`() {
        val messages = listOf(user(), assistant("a1"), user(), assistant("a2"), user(), assistant("a3"))

        assertEquals(4, chatWindowStart(messages, 1))
        assertEquals(2, chatWindowStart(messages, 2))
        assertEquals(0, chatWindowStart(messages, 10))
        assertEquals(0, chatWindowStart(emptyList(), 10))
    }

    private fun describe(item: HxTraceItem): String = when (item) {
        is HxTraceItem.Note -> "Note:${item.text}"
        is HxTraceItem.Reasoning -> "Reasoning:${item.text}"
        is HxTraceItem.Tool -> "Tool:${item.call.id}"
    }
}
