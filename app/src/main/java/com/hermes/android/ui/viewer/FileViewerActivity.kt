package com.hermes.android.ui.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hermes.android.ui.icons.filled.Fullscreen
import com.hermes.android.ui.icons.filled.FullscreenExit
import com.hermes.android.ui.icons.filled.MusicNote
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.core.content.ContextCompat
import com.hermes.android.service.AudioPlaybackService
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.ui.compose.material3.Player as Media3Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.indicator.DurationText
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.material3.text.ErrorText
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.linux.FileGate
import com.hermes.android.runtime.linux.GuestFiles
import com.hermes.android.runtime.linux.LinuxFilesProvider
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.ZoomableImage
import com.hermes.android.ui.component.fileKindOf
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.screen.openUrlExternally
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeModeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject

/**
 * The in-app viewer for a file: from a chat card, or from the Files app for a file of the
 * built-in Linux. [fileKindOf] picks the viewer; each one is a stock component —
 * Media3 ExoPlayer with its Compose Material 3 player for audio and video, the chat's own [HermesMarkdown] for
 * Markdown, WebView for HTML, Coil for images. Anything else goes to another app.
 */
@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {

    @Inject lateinit var gatewayClient: GatewayClient
    @Inject lateinit var fileGate: FileGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Another app (the Files app) gets here only through the exported LinuxFileViewer alias,
        // and any app can send that one extras: a file:// URL into the rootfs, which the
        // OTHER branch below would hand on with a read grant (~/.hermes/.env and its API keys).
        // From there only a document of the Linux Files root is taken, typed by that provider.
        val external = intent.component?.className != FileViewerActivity::class.java.name
        val source = if (external) {
            intent.data?.takeIf { it.scheme == "content" && it.authority == LinuxFilesProvider.authority(this) }
        } else {
            intent.getStringExtra(EXTRA_URL)?.let(Uri::parse) ?: intent.data
        }
        if (source == null) {
            finish()
            return
        }
        // A document of the Linux Files root is read as the file it is, so a page's
        // relative CSS/JS/images resolve next to it.
        val uri = linuxFileUri(source) ?: source
        val name = intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() && !external } ?: displayName(source)
        val mime = intent.type?.takeUnless { external } ?: if (source.scheme == "content") contentResolver.getType(source) else null
        val kind = fileKindOf(name, mime).takeIf { it != FileKind.OTHER } ?: fileKindOf(uri.toString())
        if (kind == FileKind.OTHER) {
            if (!external) openUrlExternally(this, uri.toString(), fileGate)
            finish()
            return
        }

        enableEdgeToEdge()
        val themeModeState = ThemeModeState(this)
        val appLanguageState = AppLanguageState(this)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides appLanguageState.language) {
                Hermes2Theme(
                    themeMode = themeModeState.mode,
                    colorTheme = themeModeState.colorTheme,
                    warmMode = themeModeState.warmMode,
                    appFont = themeModeState.appFont,
                    fontScalePct = themeModeState.fontScalePct,
                ) {
                    FileViewerScreen(
                        uri = uri, name = name, kind = kind,
                        readBytes = ::readBytes,
                        openExternally = { openUrlExternally(this, uri.toString(), fileGate) },
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    /** content:// of [LinuxFilesProvider] → file:// of the same file in the rootfs. */
    private fun linuxFileUri(uri: Uri): Uri? {
        if (uri.scheme != "content" || uri.authority != LinuxFilesProvider.authority(this)) return null
        return runCatching {
            val file = GuestFiles(ProotEnvironment.rootfsDir(this)).hostFile(DocumentsContract.getDocumentId(uri))
            Uri.fromFile(file).takeIf { file.isFile }
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()

    /** The file's bytes: from the rootfs, a content provider, or the gateway (remote runtime). */
    private suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            "file" -> {
                val file = File(URI(uri.toString())).canonicalFile
                val root = ProotEnvironment.rootfsDir(this@FileViewerActivity).canonicalPath + File.separator
                if (!file.path.startsWith(root)) throw IOException("File is outside the built-in Linux")
                if (file.length() > MAX_TEXT_BYTES) throw IOException("File too large to show")
                file.readBytes()
            }
            "content" -> contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Could not open the file")
            else -> gatewayClient.downloadFile(uri.toString())
        }
    }

    companion object {
        private const val EXTRA_URL = "com.hermes.android.viewer.URL"
        private const val EXTRA_NAME = "com.hermes.android.viewer.NAME"
        private const val MAX_TEXT_BYTES = 8L * 1024 * 1024

        /** Opens [url] (as resolved for the chat: file://, http(s)://, content://) in its viewer. */
        fun open(context: Context, url: String, name: String) {
            context.startActivity(intent(context, url, name))
        }

        /** The URL travels as an extra: a file:// data URI in an intent trips StrictMode. */
        fun intent(context: Context, url: String, name: String): Intent =
            Intent(context, FileViewerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_NAME, name)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewerScreen(
    uri: Uri,
    name: String,
    kind: FileKind,
    readBytes: suspend (Uri) -> ByteArray,
    openExternally: () -> Unit,
    onBack: () -> Unit,
) {
    var fullscreen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (!fullscreen) TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "برگشت"))
                    }
                },
                actions = {
                    IconButton(onClick = openExternally) {
                        Icon(HxIcons.ExternalLink, contentDescription = t("Open with another app", "باز کردن با برنامهٔ دیگر"))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (kind) {
                FileKind.AUDIO -> BackgroundAudio(uri, name) { player -> AudioControls(player, name) }
                FileKind.VIDEO -> MediaViewer(uri) { player -> VideoPlayer(player, fullscreen) { fullscreen = it } }
                FileKind.MARKDOWN -> MarkdownViewer(uri, readBytes, openExternally)
                FileKind.HTML -> HtmlViewer(uri, readBytes, openExternally)
                FileKind.IMAGE -> ImageViewer(uri)
                FileKind.OTHER -> Unit
            }
        }
    }
}

/**
 * One ExoPlayer for the file, shown through Media3's Compose Material 3 components by
 * [content]. Paused when the screen leaves the foreground and released with it, so nothing
 * keeps playing after back.
 */
@Composable
private fun MediaViewer(uri: Uri, content: @Composable (ExoPlayer) -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Time runs left to right in any language: the slider, the times and back/forward.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content(player) }
}

/**
 * Audio plays in [AudioPlaybackService], reached through a MediaController, so it goes on in
 * the background with its notification; leaving the viewer does not stop it. Opening the file
 * that is already playing picks it up where it is instead of starting over.
 */
@Composable
private fun BackgroundAudio(uri: Uri, name: String, content: @Composable (Player) -> Unit) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(uri) {
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val built = runCatching { future.get() }.getOrNull() ?: return@addListener
            val id = AudioPlaybackService.itemId(uri.toString())
            if (built.currentMediaItem?.mediaId != id) {
                built.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(id)
                        .setUri(uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                        .build(),
                )
                built.prepare()
            }
            built.play()
            controller = built
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    val player = controller
    if (player == null) {
        Loading()
        return
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content(player) }
}

/** Playback state the stock components don't expose: buffering and playing. */
private class PlaybackFlags(player: Player) {
    var buffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    var playing by mutableStateOf(player.isPlaying)
}

@Composable
private fun rememberPlaybackFlags(player: Player): PlaybackFlags {
    val flags = remember(player) { PlaybackFlags(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                flags.buffering = state == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                flags.playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return flags
}

/**
 * Media3's Material 3 [Media3Player]: a tap shows the controls, which fade out after a few
 * seconds of playback. Full screen hides the top bar and the system bars and turns landscape;
 * back leaves full screen first.
 */
@kotlin.OptIn(ExperimentalApi::class)
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(player: Player, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit) {
    val activity = LocalContext.current as? Activity
    val flags = rememberPlaybackFlags(player)
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, flags.playing) {
        if (controlsVisible && flags.playing) {
            delay(ControlsHideDelayMs)
            controlsVisible = false
        }
    }
    BackHandler(enabled = fullscreen) { onFullscreen(false) }
    DisposableEffect(fullscreen) {
        val window = activity?.window
        if (activity != null && window != null) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (fullscreen && activity != null) {
                activity.window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = null, indication = null) { controlsVisible = !controlsVisible },
    ) {
        Media3Player(
            player = player,
            modifier = Modifier.fillMaxSize(),
            showControls = controlsVisible,
            topControls = null,
            bottomControls = { p, visible ->
                PlayerDefaults.BottomControls(
                    p, visible,
                    right = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DurationText(it, Modifier.padding(start = 8.dp))
                            IconButton(onClick = { onFullscreen(!fullscreen) }) {
                                Icon(
                                    if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (fullscreen) t("Exit full screen", "خروج از تمام‌صفحه")
                                    else t("Full screen", "تمام‌صفحه"),
                                )
                            }
                        }
                    },
                )
            },
        )
        if (flags.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

/**
 * Audio: the file's embedded artwork (or a note icon), its title and artist when tagged,
 * then Media3's Material 3 slider, times and buttons.
 */
@OptIn(UnstableApi::class)
@Composable
private fun AudioControls(player: Player, name: String) {
    val flags = rememberPlaybackFlags(player)
    val metadata = rememberCurrentMediaItemState(player).mediaMetadata
    val artwork = remember(metadata.artworkData) {
        metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var artworkOpen by remember { mutableStateOf(false) }
        if (artworkOpen && metadata.artworkData != null) {
            Dialog(
                onDismissRequest = { artworkOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ZoomableImage(
                    model = metadata.artworkData,
                    modifier = Modifier.background(Color.Black),
                    onTap = { artworkOpen = false },
                )
            }
        }
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = artwork != null) { artworkOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Filled.MusicNote, contentDescription = null,
                    modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (flags.buffering) CircularProgressIndicator()
        }
        Spacer(Modifier.height(24.dp))
        Text(
            metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: name,
            style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        metadata.artist?.toString()?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(24.dp))
        ProgressSlider(player, Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth()) {
            PositionText(player)
            Spacer(Modifier.weight(1f))
            DurationText(player)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SeekBackButton(player)
            PlayPauseButton(
                player, modifier = Modifier.size(72.dp), iconSize = 40.dp,
                colors = IconButtonDefaults.filledIconButtonColors(),
            )
            SeekForwardButton(player)
        }
        ErrorText(player, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
    }
}

private const val ControlsHideDelayMs = 3_000L

/** Markdown through the chat's own renderer. */
@Composable
private fun MarkdownViewer(uri: Uri, readBytes: suspend (Uri) -> ByteArray, openExternally: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        runCatching { readBytes(uri).toString(Charsets.UTF_8) }
            .onSuccess { text = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    when {
        error != null -> ViewerError(error!!, openExternally)
        text == null -> Loading()
        else -> SelectionContainer {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                HermesMarkdown(
                    markdown = text!!,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

/**
 * A local page loads as its file:// URL, so relative CSS, scripts and images next to it load
 * too (its scripts still can't read other files). A remote gateway serves files as downloads,
 * which WebView won't show, so those are fetched and shown as HTML text.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlViewer(uri: Uri, readBytes: suspend (Uri) -> ByteArray, openExternally: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var inline by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val gatewayFile = uri.path?.endsWith("/api/files/download") == true
    if (gatewayFile) {
        LaunchedEffect(uri) {
            runCatching { readBytes(uri).toString(Charsets.UTF_8) }
                .onSuccess { inline = it }
                .onFailure { error = it.message ?: it.toString() }
        }
    }
    val context = LocalContext.current
    // Back walks the page's own history first, then leaves the viewer.
    BackHandler(enabled = webView != null) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else (context as? Activity)?.finish()
    }
    if (error != null) {
        ViewerError(error!!, openExternally)
        return
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.allowFileAccess = uri.scheme == "file"
                if (!gatewayFile) loadUrl(uri.toString())
                webView = this
            }
        },
        update = { view ->
            inline?.let { html ->
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
    if (gatewayFile && inline == null) Loading()
}

/** Fit to screen; pinch or double-tap to zoom, drag to pan. */
@Composable
private fun ImageViewer(uri: Uri) {
    ZoomableImage(model = uri, modifier = Modifier.background(Color.Black))
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ViewerError(message: String, openExternally: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = openExternally) {
            Text(t("Open with another app", "باز کردن با برنامهٔ دیگر"))
        }
    }
}
