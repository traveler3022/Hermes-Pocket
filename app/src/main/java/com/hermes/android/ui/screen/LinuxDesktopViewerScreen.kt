package com.hermes.android.ui.screen

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.runtime.linux.LinuxDesktop
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.LinuxDesktopViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The agent's desktop, streamed through noVNC, driven with touch gestures and the phone keyboard. */
@Composable
fun LinuxDesktopViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinuxDesktopViewModel = hiltViewModel(),
) {
    val desktopState by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var remote by remember { mutableStateOf<DesktopRemote?>(null) }
    var keyboard by remember { mutableStateOf<RemoteKeyboardView?>(null) }
    var touchMode by rememberSaveable { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Null until startViewing() has proven the bridge's port is ours: the URL carries the VNC password.
    var page by remember { mutableStateOf<ViewerPage?>(null) }
    var connectJob by remember { mutableStateOf<Job?>(null) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun connect() {
        // A second start while one is under way would kill the bridge the first one is waiting on.
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            connecting = true
            viewModel.startViewing().fold(
                onSuccess = {
                    failure = null
                    page = ViewerPage(viewModel.viewerUrl, (page?.attempt ?: 0) + 1)
                },
                onFailure = { failure = it.message },
            )
            connecting = false
        }
    }

    // Streams only while the screen is in view: leaving the app closes the VNC bridge, and
    // coming back opens a fresh one. Chromium keeps running for the agent either way; only
    // the Stop button or the agent's `hermes-desktop stop` turns the browser off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> connect()
                Lifecycle.Event.ON_STOP -> viewModel.stopViewing()
                else -> Unit
            }
        }
        // Replays ON_START when the screen is already started, which is the first connect.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(touchMode, remote) { remote?.setTouchMode(touchMode) }
    DisposableEffect(Unit) {
        onDispose {
            web?.destroy()
            // Nothing streams once nobody is watching; the desktop keeps running for the agent.
            viewModel.stopViewing()
        }
    }
    BackHandler(enabled = keyboardOpen) { keyboard?.hide() }
    BackHandler(enabled = !keyboardOpen, onBack = onNavigateBack)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "بازگشت"))
                }
                Text(
                    text = t("Desktop", "دسکتاپ"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { touchMode = !touchMode }) {
                    Icon(
                        imageVector = if (touchMode) Icons.Default.TouchApp else Icons.Default.Mouse,
                        contentDescription = if (touchMode) {
                            t("Touch mode — tap where you want", "حالت لمس — هرجا بزنی همان‌جا")
                        } else {
                            t("Touchpad mode — drag to move the pointer", "حالت ماوس — با کشیدن نشانگر را ببر")
                        },
                    )
                }
                IconButton(onClick = { keyboard?.show() }) {
                    Icon(Icons.Default.Keyboard, contentDescription = t("Keyboard", "کیبورد"))
                }
                IconButton(onClick = ::connect) {
                    Icon(Icons.Default.Refresh, contentDescription = t("Reconnect", "اتصال دوباره"))
                }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (desktopState == LinuxDesktop.State.Running) {
                DesktopPage(
                    page = page,
                    touchMode = touchMode,
                    onTap = { _, y ->
                        scope.launch { if (viewModel.shouldShowKeyboard(y)) keyboard?.show() }
                    },
                    onCreated = {
                        web = it
                        remote = DesktopRemote(it)
                    },
                )
                AndroidView(
                    factory = { context -> RemoteKeyboardView(context) { remote }.also { keyboard = it } },
                    modifier = Modifier.align(Alignment.TopStart).size(1.dp).alpha(0f),
                )
            }
            when {
                connecting || desktopState == LinuxDesktop.State.Starting -> CircularProgressIndicator(color = Color.White)
                desktopState != LinuxDesktop.State.Running -> Text(
                    text = failure ?: t("The desktop is not running.", "دسکتاپ روشن نیست."),
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/** A noVNC page to load; [attempt] makes a reconnect to the same URL load it again. */
private data class ViewerPage(val url: String, val attempt: Int)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DesktopPage(
    page: ViewerPage?,
    touchMode: Boolean,
    onTap: (x: Int, y: Int) -> Unit,
    onCreated: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val touchScript = remember { context.assets.open("desktop/touch.js").bufferedReader().use { it.readText() } }
    val currentTouchMode by rememberUpdatedState(touchMode)
    val currentOnTap by rememberUpdatedState(onTap)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    DesktopClickBridge(this) { x, y -> currentOnTap(x, y) },
                    DesktopClickBridge.NAME,
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        view.evaluateJavascript(touchScript, null)
                        DesktopRemote(view).setTouchMode(currentTouchMode)
                    }
                }
                keepSystemGesturesOff(this)
                onCreated(this)
            }
        },
        update = { view ->
            if (page != null && view.tag != page) {
                view.tag = page
                view.loadUrl(page.url)
            }
        },
    )
}

/** Edge swipes belong to the desktop here, not to Android's back gesture. */
private fun keepSystemGesturesOff(view: View) {
    val cover = { view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height)) }
    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cover() }
    view.post { cover() }
}
