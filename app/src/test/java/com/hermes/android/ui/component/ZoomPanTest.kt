package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomPanTest {

    // A portrait phone frame.
    private val fw = 400f
    private val fh = 800f

    private fun assertPair(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.01f)
        assertEquals(expected.second, actual.second, 0.01f)
    }

    @Test
    fun `a whole picture does not move`() {
        assertPair(0f to 0f, zoomPanLimit(1600f, 900f, fw, fh, scale = 1f))
        assertPair(0f to 0f, zoomPanLimit(900f, 1600f, fw, fh, scale = 1f))
    }

    @Test
    fun `a wide picture zoomed in moves sideways but not into the letterbox`() {
        // 1600x900 fits as 400x225; at 2x it is 800x450 in a 400x800 frame.
        assertPair(200f to 0f, zoomPanLimit(1600f, 900f, fw, fh, scale = 2f))
    }

    @Test
    fun `a wide picture moves up and down only once taller than the frame`() {
        // At 8x: 3200x1800 -> (3200-400)/2, (1800-800)/2.
        assertPair(1400f to 500f, zoomPanLimit(1600f, 900f, fw, fh, scale = 8f))
    }

    @Test
    fun `a tall picture zoomed in`() {
        // 500x2000 fits as 200x800; at 3x it is 600x2400.
        assertPair(100f to 800f, zoomPanLimit(500f, 2000f, fw, fh, scale = 3f))
    }

    @Test
    fun `a square picture`() {
        // 1000x1000 fits as 400x400; at 2x 800x800 -> sideways only.
        assertPair(200f to 0f, zoomPanLimit(1000f, 1000f, fw, fh, scale = 2f))
        assertPair(1400f to 1200f, zoomPanLimit(1000f, 1000f, fw, fh, scale = 8f))
    }

    @Test
    fun `a small picture is fitted up like ContentScale Fit`() {
        // 100x50 fits as 400x200.
        assertPair(200f to 0f, zoomPanLimit(100f, 50f, fw, fh, scale = 2f))
    }

    @Test
    fun `unknown picture size falls back to the frame`() {
        assertPair(200f to 400f, zoomPanLimit(0f, 0f, fw, fh, scale = 2f))
        assertPair(200f to 400f, zoomPanLimit(Float.NaN, Float.NaN, fw, fh, scale = 2f))
    }
}
