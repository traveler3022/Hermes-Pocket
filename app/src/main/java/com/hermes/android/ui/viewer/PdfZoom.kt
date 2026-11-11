package com.hermes.android.ui.viewer

import kotlin.math.ceil

/**
 * The PDF viewer's zoom arithmetic, kept pure so it is tested. The page list is scaled from
 * its top-left corner by `scale` and moved sideways by `offsetX` (0 or less): it is laid out
 * 1/scale as tall as the frame, so the scaled list exactly fills the frame's height and
 * scrolls up and down by itself, while its extra width is panned with `offsetX`.
 */
internal object PdfZoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f

    /** A double tap on a whole document zooms to this. */
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Pages are re-rendered for the zoom in these steps, not on every pinch frame. */
    const val RENDER_STEP = 0.5f

    fun clampScale(scale: Float): Float =
        if (scale.isNaN()) MIN_SCALE else scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Sideways the scaled list may go from its left edge: between this (≤ 0) and 0. */
    fun minOffsetX(frameWidth: Float, scale: Float): Float = -(frameWidth * scale - frameWidth).coerceAtLeast(0f)

    fun clampOffsetX(offsetX: Float, frameWidth: Float, scale: Float): Float =
        offsetX.coerceIn(minOffsetX(frameWidth, scale), 0f)

    /**
     * The new sideways offset when the scale goes from [from] to [to] about the frame's
     * x [focusX]: the document point under it stays under it.
     */
    fun focusOffsetX(offsetX: Float, focusX: Float, from: Float, to: Float, frameWidth: Float): Float {
        if (from <= 0f) return clampOffsetX(offsetX, frameWidth, to)
        return clampOffsetX(focusX - (focusX - offsetX) * (to / from), frameWidth, to)
    }

    /**
     * How far (list px, forward positive) the list must scroll so the document point at the
     * frame's y [focusY] stays there while the scale goes from [from] to [to].
     */
    fun focusScroll(focusY: Float, from: Float, to: Float): Float {
        if (from <= 0f || to <= 0f) return 0f
        return focusY / from - focusY / to
    }

    /** Where a double tap at a scale of [scale] goes: back to whole, or in to [DOUBLE_TAP_SCALE]. */
    fun doubleTapTarget(scale: Float): Float = if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_SCALE

    /** The scale pages are rendered for at a zoom of [scale]: [scale] rounded up to a [RENDER_STEP]. */
    fun renderScale(scale: Float): Float {
        val s = clampScale(scale)
        return (ceil(s / RENDER_STEP) * RENDER_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
    }
}
