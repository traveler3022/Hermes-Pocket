package com.hermes.android.ui.screen

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player as Media3Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.indicator.DurationText
import com.hermes.android.R
import com.hermes.android.domain.media.MediaPlayers
import com.hermes.android.ui.icons.filled.Download
import com.hermes.android.ui.icons.filled.Fullscreen
import com.hermes.android.ui.viewer.FileViewerActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The chat's inline players: one at a time for the whole app. Each is built on the viewer's
 * player ([MediaPlayers]: a file:// of the built-in Linux reads from disk, a server file streams
 * with the app's sign-in), and starting one pauses the one that played before. Main thread only.
 */
@Singleton
class InlinePlayback @Inject constructor(private val players: MediaPlayers) {
    private var active: Player? = null

    /** [contentType]: MUSIC for audio, MOVIE for video; phones pick their sound effects by it. */
    fun newPlayer(context: Context, contentType: Int): ExoPlayer =
        players.playerBuilder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(contentType).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

    /** [player] starts playing: the one active before it pauses. */
    fun claim(player: Player) {
        active?.takeIf { it !== player }?.pause()
        active = player
    }

    /** [player] is being released: no longer the active one. */
    fun forget(player: Player) {
        if (active === player) active = null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InlinePlaybackEntryPoint {
    fun inlinePlayback(): InlinePlayback
}

/**
 * One chat item's player: none until the first play, released when the item leaves the
 * composition or the screen stops (back to its poster).
 */
private class InlineItem(
    private val playback: InlinePlayback,
    private val context: Context,
    private val url: String,
    private val contentType: Int,
) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) player?.let(playback::claim)
        }
    }

    fun play() {
        val current = player ?: playback.newPlayer(context, contentType).also {
            it.addListener(listener)
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            player = it
        }
        playback.claim(current)
        current.play()
    }

    fun pause() {
        player?.pause()
    }

    fun release() {
        val current = player ?: return
        player = null
        current.removeListener(listener)
        playback.forget(current)
        current.release()
    }
}

@Composable
private fun rememberInlineItem(url: String, contentType: Int): InlineItem {
    val context = LocalContext.current
    val item = remember(url) {
        val playback = EntryPointAccessors
            .fromApplication(context.applicationContext, InlinePlaybackEntryPoint::class.java)
            .inlinePlayback()
        InlineItem(playback, context.applicationContext, url, contentType)
    }
    DisposableEffect(item) { onDispose { item.release() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, item) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) item.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return item
}

/**
 * Audio in a chat bubble: [InlineAudioCard]. The player is made on the first play; the title
 * opens the full viewer.
 */
@Composable
internal fun InlineAudioPlayer(url: String, name: String, onDownload: () -> Unit) {
    val context = LocalContext.current
    val item = rememberInlineItem(url, C.AUDIO_CONTENT_TYPE_MUSIC)
    InlineAudioCard(
        player = item.player,
        name = name,
        onPlay = item::play,
        onOpen = {
            item.pause()
            FileViewerActivity.open(context, url, name)
        },
        onDownload = onDownload,
    )
}

/**
 * Video in a chat bubble: a 16:9 poster with a play button; a tap plays it right there with
 * Media3's controls, and the full-screen button opens the full viewer.
 */
@kotlin.OptIn(ExperimentalApi::class)
@OptIn(UnstableApi::class)
@Composable
internal fun InlineVideoPlayer(url: String, name: String, onDownload: () -> Unit) {
    val context = LocalContext.current
    val item = rememberInlineItem(url, C.AUDIO_CONTENT_TYPE_MOVIE)
    val player = item.player
    val openViewer = {
        item.pause()
        FileViewerActivity.open(context, url, name)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            if (player == null) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable(onClick = item::play),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play),
                                tint = Color.White, modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            } else {
                InlineVideoSurface(player, onFullscreen = openViewer)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = openViewer)
                    .padding(vertical = 6.dp),
            )
            DownloadButton(onDownload)
        }
    }
}

/** The viewer's video controls, sized for a bubble: a tap shows them, they fade while playing. */
@kotlin.OptIn(ExperimentalApi::class)
@OptIn(UnstableApi::class)
@Composable
private fun InlineVideoSurface(player: Player, onFullscreen: () -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(InlineControlsHideDelayMs)
            controlsVisible = false
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) { controlsVisible = !controlsVisible },
        ) {
            Media3Player(
                player = player,
                modifier = Modifier.fillMaxSize(),
                // A TextureView scrolls and clips with the list; a SurfaceView would not.
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                showControls = controlsVisible,
                topControls = null,
                bottomControls = { p, visible ->
                    PlayerDefaults.BottomControls(
                        p, visible,
                        right = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DurationText(it, Modifier.padding(start = 8.dp))
                                IconButton(onClick = onFullscreen) {
                                    Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.full_screen))
                                }
                            }
                        },
                    )
                },
            )
            if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun DownloadButton(onDownload: () -> Unit) {
    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Default.Download,
            contentDescription = stringResource(R.string.download),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val InlineControlsHideDelayMs = 3_000L
