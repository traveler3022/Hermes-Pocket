package com.hermes.android.ui.component

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The image viewer's gesture decisions, kept out of the composables so they can be
 * tested: which gesture a finger is making, where a double tap zooms to, and whether
 * a vertical drag closes the viewer.
 *
 * Adapted from slopus/happy (MIT License, Copyright (c) 2026 Happy Coder Contributors):
 * PictureViewing.decide and PictureDismissal, with this app's thresholds.
 */
internal object ImageViewing {

    /** A double tap zooms to this. */
    const val DOUBLE_TAP_ZOOM = 3f

    /** Pinching stops here. */
    const val MAX_ZOOM = 8f

    /** How long a double-tap zoom in or out takes, in ms. */
    const val DOUBLE_TAP_MS = 220

    /** A drag past this share of the screen height closes the viewer. */
    const val CLOSE_FRACTION = 0.18f

    /** Or a flick this fast (dp/s), however short... */
    const val CLOSE_FLING_DP = 1200f

    /** ...once it has moved this far (dp), so a tap's wobble never closes it. */
    const val CLOSE_WOBBLE_DP = 10f

    /** What a finger on a page turns out to be doing. */
    enum class Gesture {
        /** Up or down on a whole picture: the gallery drags it away and may close. */
        Dismiss,

        /** Pinching, or moving a zoomed picture inside its page. */
        Transform,

        /** Across a whole picture: the pager turns the page. */
        Page,
    }

    /**
     * Decides a gesture once it is past the touch [slop], or at once with a second finger
     * or a pinch; null while undecided. [movedX] and [movedY] are how far the finger has
     * gone in total. A zoomed picture only moves inside its page (no paging, no closing).
     */
    fun decide(fingers: Int, zoomChange: Float, movedX: Float, movedY: Float, slop: Float, zoomed: Boolean): Gesture? {
        if (fingers > 1 || zoomChange != 1f) return Gesture.Transform
        if (hypot(movedX, movedY) < slop) return null
        if (zoomed) return Gesture.Transform
        return if (abs(movedY) > abs(movedX)) Gesture.Dismiss else Gesture.Page
    }

    /** Where a double tap at a scale of [scale] goes: the target scale and its offset before clamping. */
    data class DoubleTap(val scale: Float, val offsetX: Float, val offsetY: Float)

    /**
     * A double tap at ([tapX], [tapY]) in a frame centred on ([centerX], [centerY]): zoomed
     * in, back to whole; whole, in to [DOUBLE_TAP_ZOOM] with the tapped point kept where it is
     * (the picture scales about the frame's centre, so it moves by (centre − tap)·(zoom − 1)).
     */
    fun doubleTap(scale: Float, tapX: Float, tapY: Float, centerX: Float, centerY: Float): DoubleTap {
        if (scale > 1f) return DoubleTap(1f, 0f, 0f)
        val grow = DOUBLE_TAP_ZOOM - 1f
        return DoubleTap(DOUBLE_TAP_ZOOM, (centerX - tapX) * grow, (centerY - tapY) * grow)
    }

    /**
     * Whether letting go of a vertical drag at [dragY] px, moving at [velocityY] px/s,
     * closes a viewer [height] px tall. [flingPx] and [wobblePx] are [CLOSE_FLING_DP] and
     * [CLOSE_WOBBLE_DP] in px. Flung back toward the middle, it stays, however far it was.
     */
    fun closes(dragY: Float, velocityY: Float, height: Float, flingPx: Float, wobblePx: Float): Boolean {
        val flungBack = dragY != 0f && velocityY != 0f && (dragY > 0) != (velocityY > 0) && abs(velocityY) > flingPx
        if (flungBack) return false
        return abs(dragY) > height * CLOSE_FRACTION || (abs(velocityY) > flingPx && abs(dragY) > wobblePx)
    }

    /** How far the backdrop has faded (0 at rest, 1 gone) at a drag of [dragY] px in a [height] px viewer. */
    fun fade(dragY: Float, height: Float): Float =
        if (height > 0f) (abs(dragY) / (height / 2)).coerceIn(0f, 1f) else 0f
}
