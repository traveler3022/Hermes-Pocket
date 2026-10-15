// The full-screen audio player's look — the mesh page, the dissolving artwork, the thin slider and
// the transport row — is a light port of SimpMusic's "Apple Music" Now Playing style
// (github.com/maxrave-dev/SimpMusic, GPL-3.0): AppleMusicMeshBackdrop.kt, AppleMusicShared.kt and
// NowPlayingContentAppleMusic.kt.
package com.hermes.android.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.text.ErrorText
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.hermes.android.R
import com.hermes.android.ui.component.ZoomableImage
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.icons.filled.MusicNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The audio viewer: a full-width square of the file's artwork at the top, dissolving into a page
 * made of its own colours, with the title, a thin slider, the times and the transport hung from
 * the bottom. No artwork: a dark page with a note in the artwork's place.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AppleMusicAudio(player: Player, name: String, onBack: () -> Unit, openExternally: () -> Unit) {
    val flags = rememberBufferingFlag(player)
    val metadata = rememberCurrentMediaItemState(player).mediaMetadata
    val artworkData = metadata.artworkData
    val artwork = remember(artworkData) {
        artworkData?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()?.asImageBitmap() }
    }
    LightStatusBarIcons()

    var artworkOpen by remember { mutableStateOf(false) }
    if (artworkOpen && artworkData != null) {
        Dialog(
            onDismissRequest = { artworkOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ZoomableImage(model = artworkData, modifier = Modifier.background(Color.Black), onTap = { artworkOpen = false })
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MeshFallback)) {
        val artworkHeight = minOf(maxWidth, maxHeight * ARTWORK_MAX_HEIGHT_FRACTION)
        val artworkFade = artworkHeight * ARTWORK_FADE_FRACTION
        val mesh = rememberAppleMusicMesh(artwork, seed = name.hashCode())
        AppleMusicMeshBackdrop(mesh = mesh, seam = artworkHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight)
                .clickable(enabled = artwork != null, interactionSource = null, indication = null) { artworkOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    artwork, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().appleMusicVerticalFadeEdges(topFade = 0.dp, bottomFade = artworkFade),
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote, contentDescription = null,
                    modifier = Modifier.size(120.dp), tint = Color.White.copy(alpha = 0.35f),
                )
            }
            if (flags.buffering) CircularProgressIndicator(color = Color.White)
        }

        // The wash under the status bar and the two buttons: as dark as the artwork's top is bright.
        val statusBar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val washAlpha by animateFloatAsState(
            targetValue = TOP_WASH_MIN_ALPHA +
                (TOP_WASH_MAX_ALPHA - TOP_WASH_MIN_ALPHA) * (mesh?.topLuminance ?: if (artwork != null) 1f else 0f),
            animationSpec = tween(MESH_FADE_MS),
            label = "audioTopWash",
        )
        Box(modifier = Modifier.fillMaxWidth().height(statusBar + TOP_BAND_STRIP).background(topWashBrush(washAlpha)))

        // The player runs left to right; the back arrow follows the language.
        val rtl = LocalConfiguration.current.layoutDirection == View.LAYOUT_DIRECTION_RTL
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, colors = white) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_2))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = openExternally, colors = white) {
                    Icon(HxIcons.ExternalLink, contentDescription = stringResource(R.string.open_with_another_app))
                }
            }
        }

        val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = navigationBar + 36.dp),
        ) {
            Text(
                metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: name,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            )
            metadata.artist?.toString()?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it, style = MaterialTheme.typography.bodyLarge, color = SecondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            AppleMusicProgress(player)
            Spacer(Modifier.height(22.dp))
            AppleMusicTransportRow(player)
            ErrorText(
                player,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = Color(0xFFFF8A80),
            )
        }
    }
}

/** Slider and times. While a finger drags, the bar and the times follow it; the seek lands on release. */
@OptIn(UnstableApi::class)
@Composable
private fun AppleMusicProgress(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
    val duration = progress.durationMs.takeIf { it > 0L }
    val seekable = duration != null && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val position = progress.currentPositionMs.coerceAtLeast(0L)
    val fraction = dragFraction
        ?: duration?.let { (position.toFloat() / it).coerceIn(0f, 1f) }
        ?: 0f
    // Fixed 18dp shell: the track swells on touch inside a constant footprint, so nothing above moves.
    Box(modifier = Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
        AppleMusicThinSlider(
            value = fraction,
            enabled = seekable,
            activeColor = TrackActive,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val target = dragFraction
                if (target != null && duration != null) player.seekTo((target * duration).toLong())
                dragFraction = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val elapsed = dragFraction?.let { f -> duration?.let { (f * it).toLong() } } ?: position
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            formatTime(elapsed), style = MaterialTheme.typography.bodyMedium, color = SecondaryText,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Left,
        )
        Text(
            duration?.let { "-" + formatTime((it - elapsed).coerceAtLeast(0L)) } ?: "--:--",
            style = MaterialTheme.typography.bodyMedium, color = SecondaryText,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Right,
        )
    }
}

/** Back / play-pause / forward: plain white glyphs in a tight centred cluster that swell under a finger. */
@OptIn(UnstableApi::class)
@Composable
private fun AppleMusicTransportRow(player: Player) {
    val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeekBackButton(
            player, modifier = Modifier.appleMusicPressInflate().size(56.dp).clip(CircleShape),
            iconSize = 40.dp, colors = white,
        )
        PlayPauseButton(
            player, modifier = Modifier.appleMusicPressInflate().size(76.dp).clip(CircleShape),
            iconSize = 60.dp, colors = white,
        )
        SeekForwardButton(
            player, modifier = Modifier.appleMusicPressInflate().size(56.dp).clip(CircleShape),
            iconSize = 40.dp, colors = white,
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Light status and navigation bar icons over the dark page while it shows; the previous look comes back after. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, context) {
        val window = context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val bars = WindowCompat.getInsetsController(window, view)
            val status = bars.isAppearanceLightStatusBars
            val navigation = bars.isAppearanceLightNavigationBars
            bars.isAppearanceLightStatusBars = false
            bars.isAppearanceLightNavigationBars = false
            onDispose {
                bars.isAppearanceLightStatusBars = status
                bars.isAppearanceLightNavigationBars = navigation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class BufferingFlag(player: Player) {
    var buffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
}

@Composable
private fun rememberBufferingFlag(player: Player): BufferingFlag {
    val flag = remember(player) { BufferingFlag(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                flag.buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return flag
}

// ---- Shared pieces (SimpMusic AppleMusicShared.kt) ----

/**
 * True alpha fade at the top/bottom edges (DstIn mask), on a smoothstep curve: a linear ramp has a
 * corner where it leaves 0, and the eye reads that corner as a line.
 */
private fun Modifier.appleMusicVerticalFadeEdges(topFade: Dp, bottomFade: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx().coerceAtMost(size.height / 2f)
            val bottomPx = bottomFade.toPx().coerceAtMost(size.height / 2f)
            val topStop = if (size.height > 0f) topPx / size.height else 0f
            val bottomStop = if (size.height > 0f) 1f - bottomPx / size.height else 1f
            drawRect(
                brush = Brush.verticalGradient(colorStops = fadeEdgeStops(topStop, bottomStop)),
                blendMode = BlendMode.DstIn,
            )
        }

private fun fadeEdgeStops(topStop: Float, bottomStop: Float): Array<Pair<Float, Color>> =
    buildList {
        if (topStop > 0f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(topStop * t to Color.Black.copy(alpha = smoothstep(t)))
            }
        } else {
            add(0f to Color.Black)
        }
        if (bottomStop < 1f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(bottomStop + (1f - bottomStop) * t to Color.Black.copy(alpha = 1f - smoothstep(t)))
            }
        } else {
            add(1f to Color.Black)
        }
    }.toTypedArray()

/** The control springs up while a finger is on it and settles back on release; no event is consumed. */
@Composable
private fun Modifier.appleMusicPressInflate(pressedScale: Float = 1.35f): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "appleMusicPressInflate",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/** An 8.4dp track with no thumb that thickens to 16.8dp while touched; both ends stay equally round. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicThinSlider(
    value: Float,
    enabled: Boolean,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(
        targetValue = if (pressed || dragged) 16.8.dp else 8.4.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "appleMusicSliderInflate",
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = modifier,
            interactionSource = interactionSource,
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(TrackInactive),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .background(activeColor),
                    )
                }
            },
            thumb = { Spacer(Modifier.size(0.dp)) },
        )
    }
}

// ---- The mesh page (SimpMusic AppleMusicMeshBackdrop.kt) ----
//
// The artwork is averaged into a MESH_GRID square read bottom-up (row 0 is its bottom edge); every
// row but row 0 is shifted sideways by a seeded amount and mirrored half the time; the grid is
// resampled to MESH_TEX with smoothstep, then stretched and blurred. The colour right under the
// artwork is the colour it ended on, so there is no join to hide.

/** A mesh read off one artwork, and how bright the artwork's top band is (0..1). */
@Immutable
private class AppleMusicMesh(val image: ImageBitmap, val topLuminance: Float)

@Composable
private fun rememberAppleMusicMesh(artwork: ImageBitmap?, seed: Int): AppleMusicMesh? {
    var mesh by remember { mutableStateOf<AppleMusicMesh?>(null) }
    LaunchedEffect(artwork, seed) {
        val source = artwork
        if (source == null) {
            mesh = null
            return@LaunchedEffect
        }
        val read = withContext(Dispatchers.Default) { runCatching { meshOf(source, seed) }.getOrNull() }
        if (read != null) mesh = read
    }
    return mesh
}

/** [mesh] hung from [seam]: above it the first row is held (behind the artwork), below it the mesh fills the page. */
@Composable
private fun AppleMusicMeshBackdrop(mesh: AppleMusicMesh?, seam: Dp, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<AppleMusicMesh?>(null) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(mesh) {
        val next = mesh
        if (next == null) {
            shown = null
            incoming = null
            return@LaunchedEffect
        }
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect
        if (current == null) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MeshFallback)
            .blur(MESH_BLUR),
    ) {
        val seamY = seam.toPx().coerceIn(0f, size.height)
        shown?.let { drawMesh(it, seamY, alpha = 1f) }
        incoming?.let { drawMesh(it, seamY, alpha = fade.value) }
        // Just enough to keep white text readable over a bright artwork.
        drawRect(brush = smoothScrimBrush(from = Color.Black.copy(alpha = 0.06f), to = Color.Black.copy(alpha = 0.30f)))
    }
}

private fun DrawScope.drawMesh(mesh: AppleMusicMesh, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val width = size.width.roundToInt()
    val seam = seamY.roundToInt()
    if (seam > 0) {
        drawImage(
            image = mesh.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(MESH_TEX, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, seam),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
    drawImage(
        image = mesh.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(MESH_TEX, MESH_TEX),
        dstOffset = IntOffset(0, seam),
        dstSize = IntSize(width, (size.height.roundToInt() - seam).coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private fun meshOf(artwork: ImageBitmap, seed: Int): AppleMusicMesh? {
    if (artwork.width < 1 || artwork.height < 1) return null
    val width = MESH_SOURCE_PX
    val height = (MESH_SOURCE_PX.toLong() * artwork.height / artwork.width).toInt().coerceIn(1, MESH_SOURCE_PX * 2)
    val pixels = IntArray(width * height)
    readableScaled(artwork, width, height).getPixels(pixels, 0, width, 0, 0, width, height)

    val cols = MESH_GRID
    val rows = MESH_GRID
    val cells = cols * rows
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)
    for (y in 0 until height) {
        val rowBase = ((height - 1 - y) * rows / height) * cols
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = pixels[y * width + x]
            red[cell] += ((pixel shr 16) and 0xFF).toLong()
            green[cell] += ((pixel shr 8) and 0xFF).toLong()
            blue[cell] += (pixel and 0xFF).toLong()
            count[cell]++
        }
    }
    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
    }
    val topRow = (rows - 1) * cols until rows * cols
    val topPixels = topRow.sumOf { count[it].toLong() }.coerceAtLeast(1L)
    val topLuminance =
        (topRow.sumOf { 0.2126 * red[it] + 0.7152 * green[it] + 0.0722 * blue[it] } / topPixels / 255.0).toFloat().coerceIn(0f, 1f)
    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
    val image = Bitmap.createBitmap(texels, MESH_TEX, MESH_TEX, Bitmap.Config.ARGB_8888).asImageBitmap()
    return AppleMusicMesh(image, topLuminance)
}

/** A software copy of [artwork] at [width]x[height], whose pixels can be read. */
private fun readableScaled(artwork: ImageBitmap, width: Int, height: Int): Bitmap {
    var bitmap = artwork.asAndroidBitmap()
    if (bitmap.config == Bitmap.Config.HARDWARE) bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

private fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

// Averaging greys a block, so saturation gets back roughly what averaging took, and lightness is
// floored just off pure black.
private fun Int.lifted(): Int {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val lightness = (maxC + minC) / 2f
    val delta = maxC - minC
    if (delta == 0f) {
        val grey = (lightness.coerceAtLeast(MESH_FLOOR) * 255f).roundToInt()
        return argb(grey, grey, grey)
    }
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (maxC) {
        r -> ((g - b) / delta).mod(6f)
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    val color = Color.hsl(hue, (saturation * MESH_VIBRANCE).coerceIn(0f, 1f), lightness.coerceIn(MESH_FLOOR, 1f))
    return argb((color.red * 255f).roundToInt(), (color.green * 255f).roundToInt(), (color.blue * 255f).roundToInt())
}

/** A vertical gradient from [from] to [to] on a smoothstep curve. */
private fun smoothScrimBrush(from: Color, to: Color, steps: Int = 24): Brush =
    Brush.verticalGradient(
        colorStops = Array(steps + 1) { i ->
            val t = i / steps.toFloat()
            t to lerp(from, to, smoothstep(t))
        },
    )

/** Black at the top fading out downward, at [alpha] at its darkest. */
private fun topWashBrush(alpha: Float): Brush =
    Brush.verticalGradient(
        colorStops = Array(TOP_WASH_STEPS + 1) { i ->
            val p = i / TOP_WASH_STEPS.toFloat()
            p to Color.Black.copy(alpha = alpha * (1f - p).pow(1.5f))
        },
    )

private val SecondaryText = Color.White.copy(alpha = 0.55f)
private val TrackInactive = Color.White.copy(alpha = 0.26f)
private val TrackActive = Color.White.copy(alpha = 0.92f)

private const val FADE_EDGE_STEPS = 24
private const val MESH_GRID = 6
private const val MESH_TEX = 32
private const val MESH_SOURCE_PX = 120
private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f
private const val MESH_FADE_MS = 900
private val MESH_BLUR = 44.dp

/** The page until an artwork has been read, and the whole page without one. */
internal val MeshFallback = Color(0xFF121212)

private const val ARTWORK_FADE_FRACTION = 0.42f
private const val ARTWORK_MAX_HEIGHT_FRACTION = 0.62f

// The band under the status bar that the wash darkens: tall enough to cover the two buttons.
private val TOP_BAND_STRIP = 56.dp
private const val TOP_WASH_STEPS = 24
private const val TOP_WASH_MIN_ALPHA = 0.16f
private const val TOP_WASH_MAX_ALPHA = 0.65f

private val TRANSPORT_GAP = 58.dp
private const val PROGRESS_TICK_MS = 250L
