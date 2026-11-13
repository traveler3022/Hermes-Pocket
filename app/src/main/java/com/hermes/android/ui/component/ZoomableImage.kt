package com.hermes.android.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One image's zoom: its scale and how far it is moved. Hoisted so a gallery can reset it. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var animation: Job? = null

    val zoomed: Boolean get() = scale > 1f

    /** Whole again, at once (a page the gallery turned away from). */
    fun reset() {
        stopAnimation()
        scale = 1f
        offset = Offset.Zero
    }

    internal fun stopAnimation() {
        animation?.cancel()
        animation = null
    }

    internal fun set(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = newOffset
    }

    /** A double tap at [tap]: animated in around that point, or back out to whole. */
    internal fun toggle(scope: CoroutineScope, tap: Offset, center: Offset, clamp: (Offset, Float) -> Offset) {
        stopAnimation()
        val target = ImageViewing.doubleTap(scale, tap.x, tap.y, center.x, center.y)
        val from = scale
        val start = offset
        val end = if (target.scale == 1f) Offset.Zero else clamp(Offset(target.offsetX, target.offsetY), target.scale)
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(ImageViewing.DOUBLE_TAP_MS)) { t, _ ->
                scale = from + (target.scale - from) * t
                offset = start + (end - start) * t
            }
        }
    }
}

/**
 * An image that fits the screen and zooms: pinch (1x–8x), drag while zoomed, double-tap to
 * zoom in to 3x at the tapped point or back out, animated. [onTap] gets single taps (e.g. to
 * close). At 1x a one-finger drag is not taken ([ImageViewing.decide]), so it can sit in a
 * pager ([ImageGallery]), which owns [state] to reset it when the page is turned away from.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    state: ZoomState = remember(model) { ZoomState() },
) {
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The picture's own size, once loaded: the fitted picture, not the frame, bounds the drag.
    var imageSize by remember(model) { mutableStateOf(Size.Unspecified) }

    // Keep the zoomed image covering the frame: no dragging it off into empty space.
    fun clamp(o: Offset, s: Float): Offset {
        val known = imageSize.isSpecified
        val (maxX, maxY) = zoomPanLimit(
            imageWidth = if (known) imageSize.width else 0f,
            imageHeight = if (known) imageSize.height else 0f,
            frameWidth = size.width.toFloat(),
            frameHeight = size.height.toFloat(),
            scale = s,
        )
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { imageSize = it.painter.intrinsicSize },
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { onTap?.invoke() },
                    onDoubleTap = { tap ->
                        state.toggle(scope, tap, Offset(size.width / 2f, size.height / 2f), ::clamp)
                    },
                )
            }
            .pointerInput(state) {
                // Like detectTransformGestures, but a one-finger drag on a whole picture is left
                // alone, so a pager around this can turn the page and a swipe down can close it.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moved = Offset.Zero
                    var gesture: ImageViewing.Gesture? = null
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val fingers = event.changes.count { it.pressed }
                        if (fingers == 0) break
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (gesture == null) {
                            moved += pan
                            gesture = ImageViewing.decide(
                                fingers, zoomChange, moved.x, moved.y, viewConfiguration.touchSlop, state.zoomed,
                            )
                            // Paging or closing: the pager's and the gallery's, untouched.
                            if (gesture == ImageViewing.Gesture.Page || gesture == ImageViewing.Gesture.Dismiss) break
                            if (gesture == ImageViewing.Gesture.Transform) state.stopAnimation()
                        }
                        if (gesture == ImageViewing.Gesture.Transform) {
                            val newScale = (state.scale * zoomChange).coerceIn(1f, ImageViewing.MAX_ZOOM)
                            val newOffset = if (newScale == 1f) Offset.Zero else clamp(state.offset + pan, newScale)
                            state.set(newScale, newOffset)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x
                translationY = state.offset.y
            },
    )
}
