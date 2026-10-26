// The chat's audio card is a light port of SimpMusic's mini player and its "Apple Music" pieces
// (github.com/maxrave-dev/SimpMusic, GPL-3.0): MiniPlayer.kt for the layout and
// AppleMusicShared.kt for the thin slider and the press-inflate.
package com.hermes.android.ui.screen

import com.hermes.android.ui.design.HxRadius
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.text.ErrorText
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Download
import com.hermes.android.ui.icons.filled.MusicNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A small copy of the file's artwork, and the dark colour the card takes from it. */
private class AudioArtwork(val image: ImageBitmap, val tint: Color)

/**
 * The chat's audio card: artwork, title and artist, a white play/pause and the download button
 * on top; a thin slider and elapsed/total time under it, all on a dark colour taken from the
 * artwork. [player] is null until the first play: a note in the artwork's place, the file name,
 * an idle bar.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun InlineAudioCard(
    player: Player?,
    name: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val metadata = player?.let { rememberCurrentMediaItemState(it).mediaMetadata }
    val artworkData = metadata?.artworkData
    val artwork by produceState<AudioArtwork?>(null, artworkData) {
        val bytes = artworkData
        value = if (bytes == null) null else withContext(Dispatchers.Default) { runCatching { readArtwork(bytes) }.getOrNull() }
    }
    val fallback = lerp(MaterialTheme.colorScheme.primary, NeutralDark, 0.84f)
    val background by animateColorAsState(artwork?.tint ?: fallback, tween(TINT_FADE_MS), label = "audioCardTint")
    val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: name
    val artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() }

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(HxRadius.md), color = background, contentColor = Color.White) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(ARTWORK_SIZE)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = artwork?.image
                    if (image != null) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            Icons.Filled.MusicNote, contentDescription = null,
                            modifier = Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 4.dp)
                        .clickable(onClick = onOpen),
                ) {
                    Text(
                        title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (artist != null) {
                        Text(
                            artist, style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                val playModifier = Modifier.pressInflate().size(PLAY_SIZE).clip(CircleShape)
                if (player == null) {
                    IconButton(onClick = onPlay, modifier = playModifier, colors = white) {
                        Icon(
                            Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play),
                            modifier = Modifier.size(PLAY_ICON_SIZE),
                        )
                    }
                } else {
                    PlayPauseButton(player, modifier = playModifier, iconSize = PLAY_ICON_SIZE, colors = white)
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp), colors = white) {
                    Icon(
                        Icons.Default.Download, contentDescription = stringResource(R.string.download),
                        modifier = Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            // The bar and the time run left to right in any language, as in the viewer.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.padding(top = 10.dp, end = 6.dp)) {
                    if (player == null) {
                        AudioCardProgress(fraction = 0f, seekable = false, onSeek = {}, onSeekDone = {}, elapsedMs = 0L, durationMs = null)
                    } else {
                        PlayerProgress(player)
                    }
                }
            }
            if (player != null) {
                ErrorText(player, modifier = Modifier.padding(top = 4.dp, end = 6.dp), color = ErrorColor)
            }
        }
    }
}

/** The live bar: while a finger drags, the bar and the elapsed time follow it; the seek lands on release. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerProgress(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
    val duration = progress.durationMs.takeIf { it > 0L }
    val seekable = duration != null && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    var dragFraction by remember(player) { mutableStateOf<Float?>(null) }
    val position = progress.currentPositionMs.coerceAtLeast(0L)
    val fraction = dragFraction ?: duration?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val elapsed = dragFraction?.let { f -> duration?.let { (f * it).toLong() } } ?: position
    AudioCardProgress(
        fraction = fraction,
        seekable = seekable,
        onSeek = { dragFraction = it },
        onSeekDone = {
            val target = dragFraction
            if (target != null && duration != null) player.seekTo((target * duration).toLong())
            dragFraction = null
        },
        elapsedMs = elapsed,
        durationMs = duration,
    )
}

@Composable
private fun AudioCardProgress(
    fraction: Float,
    seekable: Boolean,
    onSeek: (Float) -> Unit,
    onSeekDone: () -> Unit,
    elapsedMs: Long,
    durationMs: Long?,
) {
    // Fixed shell: the track swells on touch inside a constant footprint, so the card keeps its height.
    Box(modifier = Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
        ThinSlider(
            value = fraction, enabled = seekable, onValueChange = onSeek, onValueChangeFinished = onSeekDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            formatTime(elapsedMs), style = MaterialTheme.typography.labelSmall, color = SecondaryText,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Left,
        )
        Text(
            durationMs?.let(::formatTime) ?: "--:--", style = MaterialTheme.typography.labelSmall, color = SecondaryText,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Right,
        )
    }
}

/** A thin track with no thumb that thickens while touched; both ends stay equally round. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(
        targetValue = if (pressed || dragged) 10.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "audioCardSliderInflate",
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
                            .background(TrackActive),
                    )
                }
            },
            thumb = { Spacer(Modifier.size(0.dp)) },
        )
    }
}

/** The control springs up while a finger is on it and settles back on release; no event is consumed. */
@Composable
private fun Modifier.pressInflate(pressedScale: Float = 1.3f): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "audioCardPressInflate",
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

/** Decodes [bytes] down to thumbnail size and reads the card's colour off it. Off the main thread. */
private fun readArtwork(bytes: ByteArray): AudioArtwork? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return AudioArtwork(bitmap.asImageBitmap(), tintOf(bitmap))
}

/**
 * The artwork's dominant hue, darkened for white text: pixels are averaged with more weight on
 * the vivid ones (a plain average turns most covers to mud), then the result is set to a fixed
 * dark lightness.
 */
private fun tintOf(bitmap: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_PX, SAMPLE_PX, true)
    val pixels = IntArray(SAMPLE_PX * SAMPLE_PX)
    small.getPixels(pixels, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
    if (small !== bitmap) small.recycle()
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var total = 0.0
    for (pixel in pixels) {
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val weight = 0.1 + saturationOf(r, g, b)
        red += r * weight
        green += g * weight
        blue += b * weight
        total += weight
    }
    if (total <= 0.0) return NeutralDark
    val r = (red / total).toFloat()
    val g = (green / total).toFloat()
    val b = (blue / total).toFloat()
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    if (delta < 0.0001f) return NeutralDark
    val hue = when (maxC) {
        r -> ((g - b) / delta).mod(6f)
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    val saturation = (saturationOf(r, g, b) * 1.15f).coerceIn(0f, MAX_TINT_SATURATION)
    return Color.hsl(hue, saturation, TINT_LIGHTNESS)
}

private fun saturationOf(r: Float, g: Float, b: Float): Float {
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    if (delta == 0f) return 0f
    val lightness = (maxC + minC) / 2f
    return (delta / (1f - abs(2f * lightness - 1f))).coerceIn(0f, 1f)
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val NeutralDark = Color(0xFF1C1C1E)
private val SecondaryText = Color.White.copy(alpha = 0.55f)
private val TrackInactive = Color.White.copy(alpha = 0.26f)
private val TrackActive = Color.White.copy(alpha = 0.92f)
private val ErrorColor = Color(0xFFFF8A80)

private val ARTWORK_SIZE = 52.dp
private val PLAY_SIZE = 44.dp
private val PLAY_ICON_SIZE = 34.dp
private const val THUMB_PX = 160
private const val SAMPLE_PX = 24
private const val TINT_LIGHTNESS = 0.2f
private const val MAX_TINT_SATURATION = 0.6f
private const val TINT_FADE_MS = 600
private const val PROGRESS_TICK_MS = 250L
