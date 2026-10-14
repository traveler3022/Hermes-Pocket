package com.hermes.android.ui.component

/**
 * [widths] with the seam after column [index] moved by [delta]: that column and
 * the next one trade width and keep their sum, so the table does not change
 * size under the finger (Hermes Desktop's rule). Neither goes below [minWidth],
 * or half the pair when the pair is narrower than two of those.
 */
internal fun resizeSeam(widths: List<Float>, index: Int, delta: Float, minWidth: Float): List<Float> {
    if (index !in 0 until widths.lastIndex) return widths
    val pair = widths[index] + widths[index + 1]
    val min = minOf(minWidth, pair / 2)
    val leading = (widths[index] + delta).coerceIn(min, pair - min)
    return widths.mapIndexed { at, value ->
        when (at) {
            index -> leading
            index + 1 -> pair - leading
            else -> value
        }
    }
}
