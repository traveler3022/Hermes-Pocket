package com.hermes.android.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Download
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Full-screen images, the way Telegram's PhotoViewer handles them: swipe sideways
 * through [images], swipe up or down to close (the backdrop fades as the image
 * leaves), pinch or double-tap to zoom ([ZoomableImage]). A zoomed image keeps its
 * drags for panning; zoom back out to turn the page or close.
 */
@Composable
fun ImageGallery(
    images: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (images.isEmpty()) return
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, images.lastIndex)) { images.size }
    var dragY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(LocalDensity.current) { maxHeight.toPx() }
        val flingPx = with(LocalDensity.current) { ImageViewing.CLOSE_FLING_DP.dp.toPx() }
        val wobblePx = with(LocalDensity.current) { ImageViewing.CLOSE_WOBBLE_DP.dp.toPx() }
        val progress = ImageViewing.fade(dragY, height)

        HorizontalPager(
            state = pager,
            key = { images[it] },
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BackdropAlpha * (1f - progress)))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dragY += delta },
                    onDragStopped = { velocity ->
                        if (ImageViewing.closes(dragY, velocity, height, flingPx, wobblePx)) {
                            val direction = if (dragY != 0f) sign(dragY) else sign(velocity)
                            animate(dragY, direction * height, animationSpec = tween(150)) { v, _ -> dragY = v }
                            onDismiss()
                        } else {
                            animate(dragY, 0f) { v, _ -> dragY = v }
                        }
                    },
                ),
        ) { page ->
            val zoom = remember { ZoomState() }
            // Turned away from, a page is whole again when it comes back.
            LaunchedEffect(pager.settledPage == page) { if (pager.settledPage != page) zoom.reset() }
            ZoomableImage(
                model = images[page],
                modifier = Modifier.offset { IntOffset(0, dragY.roundToInt()) },
                onTap = onDismiss,
                state = zoom,
            )
        }

        if (images.size > 1) {
            Text(
                text = "${pager.currentPage + 1} / ${images.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 1f - progress),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
        IconButton(
            onClick = { onSave(images[pager.currentPage]) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.save_image), tint = Color.White)
        }
    }
}

private const val BackdropAlpha = 0.92f
