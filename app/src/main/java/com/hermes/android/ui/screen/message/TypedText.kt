package com.hermes.android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.max
import kotlin.math.min

/**
 * How far a streaming reply is typed out, by the rule of Telegram's
 * `MultiLayoutTypingAnimator` for streamed bot drafts: each time more text
 * arrives the speed is set so that everything not yet shown appears in
 * [TargetSeconds], never slower than a floor, and it holds until the next
 * arrival. Telegram measures the distance in pixels of line width; here it is
 * characters, since Compose lays the text out.
 */
internal class TypingPace(start: Int) {
    var shown: Float = start.toFloat()
        private set
    private var target = start
    private var speed = MinCharsPerSecond

    fun retarget(length: Int) {
        if (length < shown) shown = length.toFloat()
        target = length
        speed = max(MinCharsPerSecond, (target - shown) / TargetSeconds)
    }

    /** Moves on by [seconds]; true while there is still text to show. */
    fun advance(seconds: Float): Boolean {
        shown = min(target.toFloat(), shown + speed * seconds)
        return shown < target
    }

    companion object {
        /** Telegram's TARGET_DURATION_SEC. */
        const val TargetSeconds = 1.05f

        /** Telegram's floor is 40dp/s of line width, about six characters of body text. */
        const val MinCharsPerSecond = 6f
    }
}

/**
 * How many characters of [text] to show. A reply that was already complete when
 * it came on screen (history, a finished turn scrolled back into view) is shown
 * whole; one that is streaming is typed out with [TypingPace], and a reply that
 * finishes streaming keeps typing until it has caught up.
 */
@Composable
internal fun rememberTypedLength(text: String, streaming: Boolean): Int {
    val typed = remember { streaming }
    if (!typed) return text.length
    val pace = remember { TypingPace(text.length) }
    var shown by remember { mutableIntStateOf(text.length) }
    val length = text.length
    LaunchedEffect(length) {
        pace.retarget(length)
        shown = pace.shown.toInt()
        var last = -1L
        var more = shown < length
        while (more) {
            withFrameNanos { now ->
                if (last >= 0) more = pace.advance(((now - last) / 1e9f).coerceAtMost(MaxFrameSeconds))
                last = now
            }
            shown = pace.shown.toInt()
        }
    }
    return typedCut(text, shown)
}

/** [length] clamped to [text], never splitting a surrogate pair (an emoji). */
internal fun typedCut(text: String, length: Int): Int {
    val n = length.coerceIn(0, text.length)
    return if (n in 1 until text.length && text[n - 1].isHighSurrogate()) n - 1 else n
}

/** A stalled frame (app in background) does not dump the whole backlog at once. */
private const val MaxFrameSeconds = 0.1f
