package com.hermes.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thinking sheet is read newest-first, so what [buildTimelineRows] must
 * never do is bury — or drop — the model's latest thought.
 */
class ReasoningRowsTest {

    private fun rowsOf(reasoning: String) =
        buildTimelineRows(listOf(HxTraceItem.Reasoning(reasoning)))
            .filterIsInstance<TimelineRow.Text>()

    @Test
    fun `unstructured reasoning becomes one row per paragraph`() {
        // As one row it could not take part in the newest-first ordering: the
        // latest thought sat at the bottom of a wall of text.
        val rows = rowsOf("First I check the logs.\n\nThen I patch it.\n\nDone.")

        assertEquals(3, rows.size)
        assertEquals("First I check the logs.", rows[0].detail)
        assertEquals("Done.", rows[2].detail)
    }

    @Test
    fun `when there is too much thinking the OLDEST paragraphs go`() {
        val reasoning = (1..80).joinToString("\n\n") { "thought $it" }

        val rows = rowsOf(reasoning)

        assertTrue("something must be dropped", rows.size < 80)
        // The last row is the newest thought, and it survived.
        assertEquals("thought 80", rows.last().detail)
    }

    @Test
    fun `a single huge paragraph keeps its end, not its start`() {
        val tail = "and this is the conclusion."
        val rows = rowsOf("x".repeat(9_000) + " " + tail)

        val detail = rows.single().detail
        assertTrue("the newest words must survive", detail.endsWith(tail))
        assertTrue("the cut is marked", detail.startsWith("…"))
    }

    @Test
    fun `reasoning the model gave headings to still becomes one row per step`() {
        val rows = rowsOf(
            """
            ## Looking around
            checked the config

            ## Fixing
            patched the parser
            """.trimIndent(),
        )

        assertEquals(listOf("Looking around", "Fixing"), rows.map { it.title })
    }
}
