package com.hermes.android.ui.component

import android.os.SystemClock
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.first

/*
 * Streaming text fades in piece by piece, as Gemini's does (approach from Aether,
 * docs/streaming-animation.md): every piece of text that arrives gets its own
 * 400 ms fade, so pieces that arrived one after another are at different stages
 * at once and the newest words trail off softly instead of popping in.
 *
 * Only drawing changes: the text is laid out in full straight away, and the frame
 * clock is read in draw code alone, so markdown is not re-parsed per frame.
 */

internal const val REVEAL_FADE_MS = 400L

/** One piece of text that arrived together, [start] until [end] in the displayed text. */
internal data class RevealBatch(val start: Int, val end: Int, val startedAt: Long) {
    fun alpha(now: Long): Float {
        val p = ((now - startedAt).toFloat() / REVEAL_FADE_MS).coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}

/**
 * What one text run has shown so far, and which of its pieces are still fading.
 * New text is what lies past the previous length: a run that changed earlier on
 * (markdown closing a `**`) just shows the change, it does not fade it again.
 */
internal class RevealTrack {
    private var shown: Int? = null
    var batches: List<RevealBatch> = emptyList()
        private set

    /** The run now reads [length] chars; [fading] false only records it. */
    fun update(length: Int, now: Long, fading: Boolean) {
        val before = shown
        shown = length
        batches = batches
            .filter { it.alpha(now) < 1f && it.start < length }
            .map { if (it.end > length) it.copy(end = length) else it }
        if (!fading) return
        val from = before ?: 0
        if (length > from) batches = batches + RevealBatch(from, length, now)
    }
}

/** The clock a streaming reply's text runs fade by; one per reply. */
@Stable
class StreamingReveal internal constructor() {
    /** Whether text arriving now fades in (the reply is streaming). */
    internal var fading by mutableStateOf(false)

    /** Read in draw code only, so a tick redraws and does not recompose. */
    internal var frameMillis by mutableLongStateOf(now())

    /** The clock ticks until this time. */
    internal var activeUntil by mutableLongStateOf(0L)

    internal fun now(): Long = SystemClock.uptimeMillis()

    internal fun keepTicking(until: Long) {
        if (until > activeUntil) activeUntil = until
    }
}

internal val LocalStreamingReveal = staticCompositionLocalOf<StreamingReveal?> { null }

/**
 * Gives the text in [content] the fade while [streaming]. A fade under way when the
 * reply ends finishes. [enabled] false (the system's "Remove animations") leaves
 * text as it was: shown at once.
 */
@Composable
fun StreamingRevealHost(streaming: Boolean, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val reveal = remember { StreamingReveal() }
    reveal.fading = streaming
    LaunchedEffect(reveal) {
        while (true) {
            snapshotFlow { reveal.activeUntil }.first { it > reveal.now() }
            while (reveal.now() < reveal.activeUntil) {
                withFrameMillis { reveal.frameMillis = reveal.now() }
            }
            reveal.frameMillis = reveal.now()
        }
    }
    CompositionLocalProvider(LocalStreamingReveal provides reveal, content = content)
}

/**
 * A [Text] whose newly arrived characters fade in under a [StreamingRevealHost];
 * a plain [Text] anywhere else.
 */
@Composable
internal fun RevealingText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    val reveal = LocalStreamingReveal.current
    if (reveal == null) {
        Text(text = text, style = style, modifier = modifier, softWrap = softWrap, inlineContent = inlineContent)
        return
    }
    val track = remember { RevealTrack() }
    val batches = remember(text.length, reveal.fading) {
        track.update(text.length, reveal.now(), reveal.fading)
        track.batches
    }
    batches.maxOfOrNull { it.startedAt + REVEAL_FADE_MS }?.let { until ->
        SideEffect { reveal.keepTicking(until) }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val masks = remember { RevealMasks() }
    val paint = remember { Paint() }
    val fade = if (batches.isEmpty()) Modifier else Modifier.drawWithContent {
        val now = reveal.frameMillis
        val current = layout
        if (current == null || current.layoutInput.text != text || batches.none { it.alpha(now) < 1f }) {
            drawContent()
            return@drawWithContent
        }
        // Mask the drawn text, keeping its colours, inline code backgrounds and links.
        drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), paint) }
        drawContent()
        for ((batch, path) in masks.paths(current, batches)) {
            val alpha = batch.alpha(now)
            if (alpha < 1f) drawPath(path, Color.Black, alpha = 1f - alpha, blendMode = BlendMode.DstOut)
        }
        drawIntoCanvas { it.restore() }
    }
    Text(
        text = text,
        style = style,
        modifier = modifier.then(fade),
        softWrap = softWrap,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
    )
}

/** The outline of each fading piece, kept until the layout or the pieces change. */
private class RevealMasks {
    private var layout: TextLayoutResult? = null
    private var batches: List<RevealBatch> = emptyList()
    private var paths: List<Pair<RevealBatch, Path>> = emptyList()

    fun paths(current: TextLayoutResult, now: List<RevealBatch>): List<Pair<RevealBatch, Path>> {
        if (layout !== current || batches !== now) {
            layout = current
            batches = now
            val length = current.layoutInput.text.length
            paths = now.mapNotNull { batch ->
                val end = batch.end.coerceAtMost(length)
                if (end <= batch.start) null else batch to current.getPathForRange(batch.start, end)
            }
        }
        return paths
    }
}

/**
 * A whole block (a code card) fades in as one when it first appears in a streaming
 * reply; anywhere else, or once shown, it is drawn as is.
 */
@Composable
fun Modifier.streamingBlockReveal(): Modifier {
    val reveal = LocalStreamingReveal.current ?: return this
    val startedAt = remember { if (reveal.fading) reveal.now() else 0L }
    if (startedAt == 0L) return this
    SideEffect { reveal.keepTicking(startedAt + REVEAL_FADE_MS) }
    val batch = remember(startedAt) { RevealBatch(0, 1, startedAt) }
    return graphicsLayer { alpha = batch.alpha(reveal.frameMillis) }
}
