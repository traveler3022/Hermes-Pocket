package com.hermes.android.ui.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.linux.GuestFiles
import com.hermes.android.runtime.linux.LinuxFilesProvider
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.HermesMarkdown
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
 * Media3 ExoPlayer + PlayerView for audio and video, the chat's own [HermesMarkdown] for
 * Markdown, WebView for HTML, Coil for images. Anything else goes to another app.
 */
@AndroidEntryPoint
class FileViewerActivity : ComponentActivity() {

    @Inject lateinit var gatewayClient: GatewayClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_URL)?.let(Uri::parse) ?: intent.data
        if (source == null) {
            finish()
            return
        }
        // A document of the Linux Files root is read as the file it is, so a page's
        // relative CSS/JS/images resolve next to it.
        val uri = linuxFileUri(source) ?: source
        val name = intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: displayName(source)
        val mime = intent.type ?: if (source.scheme == "content") contentResolver.getType(source) else null
        val kind = fileKindOf(name, mime).takeIf { it != FileKind.OTHER } ?: fileKindOf(uri.toString())
        if (kind == FileKind.OTHER) {
            openUrlExternally(this, uri.toString())
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
            // The URL travels as an extra: a file:// data URI in an intent trips StrictMode.
            context.startActivity(
                Intent(context, FileViewerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_NAME, name),
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewerScreen(
    uri: Uri,
    name: String,
    kind: FileKind,
    readBytes: suspend (Uri) -> ByteArray,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "برگشت"))
                    }
                },
                actions = {
                    IconButton(onClick = { openUrlExternally(context, uri.toString()) }) {
                        Icon(HxIcons.ExternalLink, contentDescription = t("Open with another app", "باز کردن با برنامهٔ دیگر"))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (kind) {
                FileKind.AUDIO, FileKind.VIDEO -> MediaViewer(uri, isVideo = kind == FileKind.VIDEO)
                FileKind.MARKDOWN -> MarkdownViewer(uri, readBytes)
                FileKind.HTML -> HtmlViewer(uri, readBytes)
                FileKind.IMAGE -> ImageViewer(uri)
                FileKind.OTHER -> Unit
            }
        }
    }
}

/**
 * ExoPlayer with PlayerView's stock controls: play/pause, seek bar, position and duration.
 * Paused when the screen leaves the foreground and released with it, so nothing keeps playing
 * after back. Audio keeps its controls on screen; video's full-screen button turns to landscape.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MediaViewer(uri: Uri, isVideo: Boolean) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    if (isVideo) {
                        setFullscreenButtonClickListener { full ->
                            (ctx as? Activity)?.requestedOrientation =
                                if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    } else {
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = false
                        showController()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let { code -> ViewerError(t("Can't play this file", "این فایل پخش نمی‌شود") + "\n" + code, uri) }
    }
}

/** Markdown through the chat's own renderer. */
@Composable
private fun MarkdownViewer(uri: Uri, readBytes: suspend (Uri) -> ByteArray) {
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        runCatching { readBytes(uri).toString(Charsets.UTF_8) }
            .onSuccess { text = it }
            .onFailure { error = it.message ?: it.toString() }
    }
    when {
        error != null -> ViewerError(error!!, uri)
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
private fun HtmlViewer(uri: Uri, readBytes: suspend (Uri) -> ByteArray) {
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
        ViewerError(error!!, uri)
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

/** Fit to screen; pinch to zoom and pan. */
@Composable
private fun ImageViewer(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offsetX = if (scale == 1f) 0f else offsetX + pan.x
        offsetY = if (scale == 1f) 0f else offsetY + pan.y
    }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
    )
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ViewerError(message: String, uri: Uri) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { openUrlExternally(context, uri.toString()) }) {
            Text(t("Open with another app", "باز کردن با برنامهٔ دیگر"))
        }
    }
}
