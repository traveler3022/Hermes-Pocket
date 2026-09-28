package com.hermes.android.ui.screen

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hermes.android.ui.component.PREVIEW_MAX_HEIGHT
import com.hermes.android.ui.component.PREVIEW_MIN_HEIGHT
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.i18n.t
import java.util.UUID
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * `::preview{file="…"}` — an HTML file the agent wrote, rendered LIVE inside the reply, after
 * Hermes desktop's apps/desktop/src/components/assistant-ui/inline-preview-directive.tsx:
 *  - read once the turn settles (mid-stream the file is often mid-write);
 *  - a theme prelude first (the app's colours as --foreground, --muted-foreground, --accent,
 *    --border, --card; no margin; transparent background), so the page's own styles win;
 *  - height follows the content inside 120..1200, width is adopted once from the first report;
 *  - `window.hermes.send(prompt)` / `data-hermes-send` send a prompt as a hidden user turn,
 *    one per second, at most 500 characters;
 *  - a page that can't be read is the ordinary file card.
 * Unlike the desktop, a button above the frame opens the page in the full-screen viewer, where
 * its links and the files beside it work.
 * The desktop's sandboxed iframe talks through postMessage; here the page talks through a
 * JavaScript interface, trusted only with the token this mount injected. The page gets no
 * file access and can't navigate the frame away.
 */

private const val PREVIEW_DEFAULT_HEIGHT = 280
private const val PREVIEW_RESIZE_TOLERANCE = 4
private const val PREVIEW_MAX_INTENT_LENGTH = 500
private const val PREVIEW_INTENT_THROTTLE_MS = 1000L
private const val PREVIEW_BRIDGE = "hermesInline"

/** The page's size reports and its `hermes.send`, both tagged with the mount's token. */
internal fun previewScripts(token: String): String =
    "<script>(function(){var t=\"$token\";var b=window.$PREVIEW_BRIDGE;if(!b)return;" +
        // Height is the document's scrollHeight; width the union of the body children's boxes
        // (the document itself always fills the viewport).
        // A page laid out wider than the frame (fixed widths made for a desktop column) is
        // shrunk once to fit; otherwise its right side ran off the chat.
        "var fitted=false;function fit(){var d=document.documentElement;var y=document.body;" +
        "if(fitted||!d||!y)return;var sw=d.scrollWidth,vw=d.clientWidth;" +
        "if(vw>0&&sw>vw+1){y.style.zoom=String(vw/sw);fitted=true}}" +
        "var lastH=0,lastW=0;function post(){var d=document.documentElement;var y=document.body;" +
        "var h=Math.max(d?d.scrollHeight:0,y?y.scrollHeight:0);" +
        "var w=0;if(y){var kids=y.children;var L=Infinity,R=0;for(var i=0;i<kids.length;i++){" +
        "var r=kids[i].getBoundingClientRect();if(r.width===0&&r.height===0)continue;" +
        "if(r.left<L)L=r.left;if(r.right>R)R=r.right}if(R>L)w=R-L}w=Math.ceil(w);" +
        "if(Math.abs(h-lastH)>1||Math.abs(w-lastW)>1){lastH=h;lastW=w;b.size(t,h,w)}}" +
        "if(typeof ResizeObserver===\"function\"){var ro=new ResizeObserver(post);" +
        "ro.observe(document.documentElement);if(document.body)ro.observe(document.body)}" +
        "addEventListener(\"load\",function(){fit();post()});fit();post();" +
        "function send(p){if(typeof p!==\"string\"||!p.trim())return false;" +
        "b.send(t,p.slice(0,$PREVIEW_MAX_INTENT_LENGTH));return true}" +
        "window.hermes={send:send};" +
        "addEventListener(\"click\",function(e){var el=e.target&&e.target.closest?" +
        "e.target.closest(\"[data-hermes-send]\"):null;" +
        "if(el)send(el.getAttribute(\"data-hermes-send\")||\"\")},true)})()</script>"

/** The style prelude that makes the page read as part of the chat; the page's own styles override it. */
internal fun previewThemePrelude(vars: Map<String, String>, dark: Boolean): String {
    val tokens = vars.entries.joinToString(";") { (name, value) -> "$name:$value" }
    val scheme = if (dark) "dark" else "light"
    return "<style>:root{$tokens;color-scheme:$scheme}" +
        "html,body{margin:0;padding:0;background:transparent;color:var(--foreground,inherit)}</style>"
}

/** Prelude first, then the scripts before `</body>` when there is one (after the page's own markup). */
internal fun withPreviewChrome(doc: String, token: String, prelude: String): String {
    val scripts = previewScripts(token)
    val bodyClose = Regex("""</body\s*>""", RegexOption.IGNORE_CASE).find(doc)
    val framed = if (bodyClose != null) {
        doc.substring(0, bodyClose.range.first) + scripts + doc.substring(bodyClose.range.first)
    } else {
        doc + scripts
    }
    return prelude + framed
}

private fun cssColor(color: Color): String {
    fun channel(value: Float) = (value * 255).roundToInt().coerceIn(0, 255)
    return "rgba(${channel(color.red)},${channel(color.green)},${channel(color.blue)},${"%.3f".format(java.util.Locale.US, color.alpha)})"
}

private fun themeVars(colors: ColorScheme): Map<String, String> = mapOf(
    "--foreground" to cssColor(colors.onSurface),
    "--muted-foreground" to cssColor(colors.onSurfaceVariant),
    "--accent" to cssColor(colors.primary),
    "--border" to cssColor(colors.outlineVariant),
    "--card" to cssColor(colors.surfaceContainer),
)

/** What the page may say: its size, and a prompt. Called on a WebView thread. */
private class PreviewBridge(
    private val token: String,
    private val onSize: (height: Int, width: Int) -> Unit,
    private val onSend: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var lastIntentAt = 0L

    @JavascriptInterface
    fun size(from: String?, height: Double, width: Double) {
        if (from != token || !height.isFinite() || height <= 0) return
        val h = height.roundToInt().coerceIn(PREVIEW_MIN_HEIGHT, PREVIEW_MAX_HEIGHT)
        val w = if (width.isFinite() && width > 0) width.roundToInt() else 0
        main.post { onSize(h, w) }
    }

    @JavascriptInterface
    fun send(from: String?, prompt: String?) {
        if (from != token) return
        val text = prompt?.trim()?.take(PREVIEW_MAX_INTENT_LENGTH).orEmpty()
        if (text.isEmpty()) return
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (now - lastIntentAt < PREVIEW_INTENT_THROTTLE_MS) return
            lastIntentAt = now
        }
        main.post { onSend(text) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun InlinePreviewFrame(
    file: String,
    initialHeight: Int?,
    streaming: Boolean,
    readPreview: suspend (String) -> String?,
    onSend: (String) -> Unit,
    onOpen: () -> Unit,
    shareUri: suspend () -> Uri?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareFailed = t("Couldn't share the file", "فایل قابل اشتراک نیست")
    var doc by remember(file) { mutableStateOf<String?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var measured by remember(file) { mutableStateOf<Int?>(null) }
    var contentWidth by remember(file) { mutableStateOf<Int?>(null) }
    // One token per mount: only the document this mount injected can size the frame or speak.
    val token = remember { UUID.randomUUID().toString().replace("-", "") }
    val currentOnSend by rememberUpdatedState(onSend)

    LaunchedEffect(file, streaming) {
        if (streaming) return@LaunchedEffect
        val text = runCatching { readPreview(file) }.getOrNull()
        if (text.isNullOrEmpty()) failed = true else doc = text
    }

    if (failed) {
        ArtifactCard(
            emoji = "🌐", name = file.substringAfterLast('/'),
            actionLabel = t("Open", "باز کردن"),
            onAction = onOpen,
            onDownload = null,
        )
        return
    }

    val height by animateDpAsState((measured ?: initialHeight ?: PREVIEW_DEFAULT_HEIGHT).dp, tween(200), label = "previewHeight")
    val colors = MaterialTheme.colorScheme
    // Resolved once per mount, as on the desktop.
    val framed = remember(doc, token) {
        doc?.let { withPreviewChrome(it, token, previewThemePrelude(themeVars(colors), colors.surface.luminance() < 0.5f)) }
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // Name, share and full screen sit above the frame. The frame is for a single-file
        // widget; a page with links or files beside it works in the full-screen viewer,
        // where it loads from its own folder.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = file.substringAfterLast('/'),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(
                onClick = {
                    scope.launch {
                        val uri = shareUri()
                        if (uri == null) {
                            Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/html"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(file.substringAfterLast('/'), uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = t("Share", "اشتراک\u200Cگذاری"),
                    modifier = Modifier.size(16.dp),
                    tint = colors.primary,
                )
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                Icon(
                    HxIcons.ExternalLink,
                    contentDescription = t("Open full screen", "باز کردن در صفحهٔ کامل"),
                    modifier = Modifier.size(16.dp),
                    tint = colors.primary,
                )
            }
        }
        if (framed == null) {
            val pulse by rememberInfiniteTransition(label = "previewPulse").animateFloat(
                initialValue = 0.5f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "previewPulseAlpha",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .alpha(pulse)
                    .background(colors.onSurface.copy(alpha = 0.04f), RoundedCornerShape(6.dp)),
            )
        } else {
            val bridge = remember(token) {
                PreviewBridge(
                    token = token,
                    onSize = { h, w ->
                        // Tolerance keeps a vh-sized page from oscillating. Width adopts once: the
                        // first report is the content's span at full column width, and following it
                        // live would shrink %-width content toward nothing.
                        if (abs(h - (measured ?: initialHeight ?: PREVIEW_DEFAULT_HEIGHT)) > PREVIEW_RESIZE_TOLERANCE) measured = h
                        if (w > 0 && contentWidth == null) contentWidth = w
                    },
                    onSend = { currentOnSend(it) },
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        webViewClient = object : WebViewClient() {
                            // The frame stays this page: links and redirects don't take it anywhere.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        }
                        addJavascriptInterface(bridge, PREVIEW_BRIDGE)
                        loadDataWithBaseURL(null, framed, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier
                    .then(contentWidth?.let { Modifier.widthIn(max = it.dp) } ?: Modifier)
                    .fillMaxWidth()
                    .height(height),
            )
        }
    }
}
