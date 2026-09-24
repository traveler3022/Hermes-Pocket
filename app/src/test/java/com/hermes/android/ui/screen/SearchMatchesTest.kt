package com.hermes.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchesTest {

    @Test
    fun `matches ignore case and index the text as shown`() {
        assertEquals(listOf(0..2, 8..10), searchMatches("Log and LOG", "log"))
    }

    @Test
    fun `text whose lowercase is longer never yields a range outside it`() {
        val text = "İİİ Istanbul"
        val matches = searchMatches(text, "istanbul")
        assertEquals(listOf(4..11), matches)
        assertTrue(matches.all { it.last < text.length })
    }

    @Test
    fun `persian text is matched in place`() {
        assertEquals(listOf(5..8), searchMatches("سلام دنیا", "دنیا"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertEquals(emptyList<IntRange>(), searchMatches("anything", " "))
    }
}
