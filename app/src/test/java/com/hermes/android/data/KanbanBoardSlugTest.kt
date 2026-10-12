package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanBoardSlugTest {

    private val serverRule = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    @Test
    fun `a latin name becomes lowercase words joined by hyphens`() {
        assertEquals("atm10-server", boardSlug("  ATM10 Server! "))
    }

    @Test
    fun `a name with no latin letters gets a random slug the server accepts`() {
        val slug = boardSlug("اخبار روز")
        assertTrue(slug, slug.startsWith("board-") && serverRule.matches(slug))
    }

    @Test
    fun `a long name stays within the server's limit`() {
        val slug = boardSlug("a".repeat(30) + " " + "b".repeat(40))
        assertTrue(slug, serverRule.matches(slug))
    }
}
