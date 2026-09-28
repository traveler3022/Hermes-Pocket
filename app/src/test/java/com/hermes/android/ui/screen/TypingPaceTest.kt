package com.hermes.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingPaceTest {

    private fun TypingPace.run(seconds: Float, step: Float = 1f / 60) {
        var t = 0f
        while (t < seconds - 1e-4f) { advance(step); t += step }
    }

    @Test
    fun `what arrives is shown in about a second, whatever its size`() {
        for (size in listOf(50, 500, 5000)) {
            val pace = TypingPace(0)
            pace.retarget(size)
            pace.run(0.5f)
            assertTrue("$size: half-way", pace.shown > size * 0.4f && pace.shown < size * 0.6f)
            pace.run(0.6f)
            assertEquals("$size: done", size.toFloat(), pace.shown)
        }
    }

    @Test
    fun `a new arrival spreads the whole backlog over the next second`() {
        val pace = TypingPace(0)
        pace.retarget(100)
        pace.run(0.5f)
        val before = pace.shown
        pace.retarget(200)
        pace.run(TypingPace.TargetSeconds + 0.05f)
        assertEquals(200f, pace.shown)
        assertTrue(before < 100f)
    }

    @Test
    fun `a little text left is not crawled out slower than the floor`() {
        val pace = TypingPace(0)
        pace.retarget(3)
        pace.run(0.2f)
        assertTrue(pace.shown >= TypingPace.MinCharsPerSecond * 0.2f - 0.01f)
    }

    @Test
    fun `it stops at what has arrived and says so`() {
        val pace = TypingPace(0)
        pace.retarget(10)
        assertTrue(pace.advance(0.1f))
        assertFalse(pace.advance(5f))
        assertEquals(10f, pace.shown)
    }

    @Test
    fun `text that got shorter (a retry) is not shown past its end`() {
        val pace = TypingPace(100)
        pace.retarget(40)
        assertEquals(40f, pace.shown)
    }

    @Test
    fun `a cut never splits an emoji`() {
        val text = "ab😀c"
        assertEquals(2, typedCut(text, 3))
        assertEquals(4, typedCut(text, 4))
        assertEquals(text.length, typedCut(text, 99))
        assertEquals(0, typedCut(text, -1))
    }
}
