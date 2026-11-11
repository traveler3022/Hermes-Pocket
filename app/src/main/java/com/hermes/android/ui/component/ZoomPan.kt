package com.hermes.android.ui.component

import kotlin.math.max
import kotlin.math.min

/**
 * How far a zoomed picture may be dragged from its resting place, (x, y): half of what the
 * picture overhangs the frame each way, so it never leaves an edge of black it could cover.
 *
 * The picture is drawn with ContentScale.Fit, so its on-screen size is the fitted one, not the
 * frame's: clamping with the frame let a wide picture be dragged up and down into the letterbox.
 * Until the picture's size is known ([imageWidth]/[imageHeight] not positive), it falls back to
 * the frame, as before.
 *
 * Adapted from slopus/happy (MIT License, Copyright (c) 2026 Happy Coder Contributors).
 */
internal fun zoomPanLimit(
    imageWidth: Float,
    imageHeight: Float,
    frameWidth: Float,
    frameHeight: Float,
    scale: Float,
): Pair<Float, Float> {
    val known = imageWidth > 0f && imageHeight > 0f && imageWidth.isFinite() && imageHeight.isFinite()
    val fit = if (known) min(frameWidth / imageWidth, frameHeight / imageHeight) else 0f
    val shownWidth = if (known) imageWidth * fit else frameWidth
    val shownHeight = if (known) imageHeight * fit else frameHeight
    return max(0f, (shownWidth * scale - frameWidth) / 2) to max(0f, (shownHeight * scale - frameHeight) / 2)
}
