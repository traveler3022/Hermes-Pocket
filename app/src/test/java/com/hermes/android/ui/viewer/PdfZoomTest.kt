package com.hermes.android.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfZoomTest {

    private val w = 1000f

    @Test
    fun `the scale stays between whole and the maximum`() {
        assertEquals(1f, PdfZoom.clampScale(0.3f))
        assertEquals(PdfZoom.MAX_SCALE, PdfZoom.clampScale(40f))
        assertEquals(2f, PdfZoom.clampScale(2f))
        assertEquals(1f, PdfZoom.clampScale(Float.NaN))
    }

    @Test
    fun `a whole document does not move sideways`() {
        assertEquals(0f, PdfZoom.clampOffsetX(-50f, w, 1f))
        assertEquals(0f, PdfZoom.clampOffsetX(80f, w, 1f))
    }

    @Test
    fun `a zoomed document pans only over its extra width`() {
        assertEquals(-1000f, PdfZoom.minOffsetX(w, 2f))
        assertEquals(-1000f, PdfZoom.clampOffsetX(-5000f, w, 2f))
        assertEquals(0f, PdfZoom.clampOffsetX(10f, w, 2f))
        assertEquals(-400f, PdfZoom.clampOffsetX(-400f, w, 2f))
    }

    @Test
    fun `zooming keeps the point under the fingers`() {
        // Document x under the focus: (focus - offset) / scale.
        val offset = PdfZoom.focusOffsetX(0f, focusX = 600f, from = 1f, to = 2f, frameWidth = w)
        assertEquals(600f / 1f, (600f - offset) / 2f, 0.01f)
        val back = PdfZoom.focusOffsetX(offset, focusX = 600f, from = 2f, to = 1f, frameWidth = w)
        assertEquals(0f, back, 0.01f)
    }

    @Test
    fun `zooming at the left edge cannot pull past it`() {
        assertEquals(0f, PdfZoom.focusOffsetX(0f, focusX = 0f, from = 1f, to = 3f, frameWidth = w))
    }

    @Test
    fun `the list scrolls so the point under the fingers stays`() {
        // List y under the focus is scroll + focusY / scale; it must not change.
        val delta = PdfZoom.focusScroll(focusY = 800f, from = 1f, to = 2f)
        assertEquals(800f / 1f, delta + 800f / 2f, 0.01f)
        assertEquals(0f, PdfZoom.focusScroll(0f, 1f, 3f))
        assertEquals(0f, PdfZoom.focusScroll(300f, 0f, 2f))
    }

    @Test
    fun `a double tap zooms in or back out`() {
        assertEquals(PdfZoom.DOUBLE_TAP_SCALE, PdfZoom.doubleTapTarget(1f))
        assertEquals(1f, PdfZoom.doubleTapTarget(1.2f))
    }

    @Test
    fun `pages re-render in steps`() {
        assertEquals(1f, PdfZoom.renderScale(1f))
        assertEquals(1.5f, PdfZoom.renderScale(1.1f))
        assertEquals(2f, PdfZoom.renderScale(2f))
        assertEquals(PdfZoom.MAX_SCALE, PdfZoom.renderScale(9f))
    }
}
