package com.hermes.android.ui.component

import com.hermes.android.ui.component.ImageViewing.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adapted from slopus/happy (MIT License, Copyright (c) 2026 Happy Coder Contributors):
 * PictureViewingTest's gesture cases and PictureDismissalTest, with this app's thresholds.
 */
class ImageViewingTest {

    @Test
    fun `a whole picture waits for the slop, then is dragged away or paged`() {
        assertNull(ImageViewing.decide(1, 1f, 3f, 4f, slop = 8f, zoomed = false))
        assertEquals(Gesture.Dismiss, ImageViewing.decide(1, 1f, 3f, 9f, slop = 8f, zoomed = false))
        assertEquals(Gesture.Dismiss, ImageViewing.decide(1, 1f, 0f, -9f, slop = 8f, zoomed = false))
        assertEquals(Gesture.Page, ImageViewing.decide(1, 1f, -9f, 3f, slop = 8f, zoomed = false))
        // Exactly diagonal is the pager's: a dismissal is mostly up or down.
        assertEquals(Gesture.Page, ImageViewing.decide(1, 1f, 7f, 7f, slop = 8f, zoomed = false))
    }

    @Test
    fun `zoomed in, a drag only moves the picture, never pages or dismisses`() {
        assertNull(ImageViewing.decide(1, 1f, 2f, 2f, slop = 8f, zoomed = true))
        assertEquals(Gesture.Transform, ImageViewing.decide(1, 1f, -40f, 2f, slop = 8f, zoomed = true))
        assertEquals(Gesture.Transform, ImageViewing.decide(1, 1f, 0f, 40f, slop = 8f, zoomed = true))
    }

    @Test
    fun `a second finger or a pinch zooms at once`() {
        assertEquals(Gesture.Transform, ImageViewing.decide(2, 1f, 0f, 0f, slop = 8f, zoomed = false))
        assertEquals(Gesture.Transform, ImageViewing.decide(1, 1.1f, 0f, 0f, slop = 8f, zoomed = false))
    }

    @Test
    fun `a double tap keeps the tapped point under the finger`() {
        val center = 200f to 400f
        val tap = 100f to 300f
        val t = ImageViewing.doubleTap(1f, tap.first, tap.second, center.first, center.second)
        assertEquals(ImageViewing.DOUBLE_TAP_ZOOM, t.scale)
        // The picture scales about the centre: the tapped point lands at c + (p - c)·s + offset.
        assertEquals(tap.first, center.first + (tap.first - center.first) * t.scale + t.offsetX, 0.01f)
        assertEquals(tap.second, center.second + (tap.second - center.second) * t.scale + t.offsetY, 0.01f)
        // At the centre nothing moves.
        val mid = ImageViewing.doubleTap(1f, 200f, 400f, 200f, 400f)
        assertEquals(0f, mid.offsetX)
        assertEquals(0f, mid.offsetY)
    }

    @Test
    fun `a double tap on a zoomed picture goes back to whole`() {
        assertEquals(ImageViewing.DoubleTap(1f, 0f, 0f), ImageViewing.doubleTap(3f, 10f, 10f, 200f, 400f))
        assertEquals(ImageViewing.DoubleTap(1f, 0f, 0f), ImageViewing.doubleTap(1.4f, 10f, 10f, 200f, 400f))
    }

    // A phone: 2000 px tall, fling 3600 px/s and wobble 30 px (1200 and 10 dp at 3x).
    private val h = 2000f
    private val fling = 3600f
    private val wobble = 30f

    @Test
    fun `a drag past the close share closes, up or down`() {
        assertFalse(ImageViewing.closes(0f, 0f, h, fling, wobble))
        assertFalse(ImageViewing.closes(h * ImageViewing.CLOSE_FRACTION - 1f, 0f, h, fling, wobble))
        assertTrue(ImageViewing.closes(h * ImageViewing.CLOSE_FRACTION + 1f, 0f, h, fling, wobble))
        assertTrue("up closes too", ImageViewing.closes(-600f, 0f, h, fling, wobble))
    }

    @Test
    fun `let go short it stays unless flung`() {
        assertFalse(ImageViewing.closes(100f, 600f, h, fling, wobble))
        assertTrue(ImageViewing.closes(80f, 4000f, h, fling, wobble))
        assertFalse("a tap's wobble", ImageViewing.closes(10f, 4000f, h, fling, wobble))
        assertFalse("flung back", ImageViewing.closes(500f, -4000f, h, fling, wobble))
    }

    @Test
    fun `the backdrop fades over half the height`() {
        assertEquals(0f, ImageViewing.fade(0f, h))
        assertEquals(0.5f, ImageViewing.fade(500f, h), 0.0001f)
        assertEquals(1f, ImageViewing.fade(-1500f, h))
        assertEquals(0f, ImageViewing.fade(100f, 0f))
    }
}
