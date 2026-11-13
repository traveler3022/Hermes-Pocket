package com.hermes.android.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.component.rememberHighlightedCode
import com.hermes.android.ui.icons.filled.ContentCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Code and text files: the head of the file ([TEXT_PREVIEW_MAX_BYTES], [TEXT_PREVIEW_MAX_LINES]),
 * line-numbered, monospace, scrolling sideways instead of wrapping, coloured by the chat's
 * code highlighter where it knows the language. A binary file goes to another app.
 * The idea follows K9i-0/ccpocket's file peek (MIT License, Copyright (c) 2026 K9i).
 */
@Composable
internal fun TextFileViewer(
    uri: Uri,
    name: String,
    readHead: suspend (Uri, Int) -> Pair<ByteArray, Boolean>,
    openExternally: () -> Unit,
) {
    var preview by remember { mutableStateOf<TextPreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val notText = stringResource(R.string.viewer_not_text)
    LaunchedEffect(uri) {
        try {
            val (head, more) = readHead(uri, TEXT_PREVIEW_MAX_BYTES)
            val decoded = withContext(Dispatchers.Default) { decodeTextPreview(head, more) }
            if (decoded == null) error = notText else preview = decoded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Viewer] Could not read the text file")
            error = e.message ?: e.toString()
        }
    }
    val shown = preview
    when {
        error != null -> ViewerError(error.orEmpty(), openExternally)
        shown == null -> Loading()
        else -> TextLines(shown, highlightLanguageFor(name))
    }
}

/** [preview]'s lines with a number gutter and a copy button; [language] "" stays uncoloured. */
@Composable
internal fun TextLines(preview: TextPreview, language: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.copied)
    val tooLarge = stringResource(R.string.viewer_copy_too_large)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val full = remember(preview) { preview.text }
    val coloured = if (language.isEmpty()) {
        remember(full) { AnnotatedString(full) }
    } else {
        rememberHighlightedCode(full, language, MaterialTheme.colorScheme.surface)
    }
    val ranges = remember(preview) { lineRanges(preview.lines) }
    val digits = lineNumberDigits(preview.lines.size)
    val style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        textDirection = TextDirection.Ltr,
    )
    // Rows are measured one by one, so the sideways scroll needs the widest line up front.
    val measurer = rememberTextMeasurer()
    val charWidthPx = remember(style) { measurer.measure("0", style).size.width }
    val longest = remember(preview) { preview.lines.maxOfOrNull { line -> line.length + 3 * line.count { it == '\t' } } ?: 0 }
    val contentWidth = with(LocalDensity.current) { (charWidthPx * (longest + digits + 2)).toDp() } + 32.dp
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (preview.truncated) stringResource(R.string.viewer_text_truncated) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    // A clip travels through Binder: past its ~1 MB limit the copy fails.
                    val ok = runCatching { clipboard.setText(AnnotatedString(full)) }.isSuccess
                    Toast.makeText(context, if (ok) copied else tooLarge, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(18.dp),
                        tint = muted,
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(preview.lines.size) { index ->
                        val range = ranges[index]
                        Row {
                            Text(
                                text = (index + 1).toString().padStart(digits),
                                style = style,
                                color = muted,
                                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                            )
                            Text(
                                text = coloured.subSequence(range.first, range.last + 1),
                                style = style,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A PDF through the platform's PdfRenderer: one page per row, each rendered at the screen's
 * width when it scrolls into view and dropped when it leaves. The renderer and its file are
 * closed with the screen. A password-protected or broken file shows the error with the
 * open-elsewhere button.
 */
@Composable
internal fun PdfViewer(
    uri: Uri,
    openPdf: suspend (Uri) -> ParcelFileDescriptor,
    openExternally: () -> Unit,
) {
    var document by remember { mutableStateOf<OpenPdf?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val unreadable = stringResource(R.string.viewer_pdf_unreadable)
    LaunchedEffect(uri) {
        var opened: OpenPdf? = null
        try {
            withContext(Dispatchers.IO) { opened = OpenPdf.open(openPdf(uri)) }
            document = opened
        } catch (e: CancellationException) {
            opened?.close()
            throw e
        } catch (e: PdfUnreadableException) {
            Timber.w(e, "[Viewer] PdfRenderer refused the file")
            error = unreadable
        } catch (e: Exception) {
            Timber.w(e, "[Viewer] Could not open the PDF")
            error = e.message ?: e.toString()
        }
    }
    DisposableEffect(document) {
        val open = document
        onDispose { open?.close() }
    }
    val doc = document
    when {
        error != null -> ViewerError(error.orEmpty(), openExternally)
        doc == null -> Loading()
        else -> ZoomablePdfPages(doc)
    }
}

/** One document's zoom: scale, sideways offset, and the scale its pages are rendered for. */
@Stable
private class PdfZoomState {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var renderScale by mutableFloatStateOf(1f)
    var animation: Job? = null

    val zoomed: Boolean get() = scale > PdfZoom.MIN_SCALE

    /** Scales to [to] about the frame point ([focusX], [focusY]); returns how far the list must scroll. */
    fun zoomTo(to: Float, focusX: Float, focusY: Float, frameWidth: Float): Float {
        val from = scale
        val target = PdfZoom.clampScale(to)
        offsetX = PdfZoom.focusOffsetX(offsetX, focusX, from, target, frameWidth)
        scale = target
        return PdfZoom.focusScroll(focusY, from, target)
    }

    fun settle() {
        renderScale = PdfZoom.renderScale(scale)
    }
}

/**
 * The pages, zoomable: pinch (1x to 4x) about the fingers, double-tap to 2.5x or back,
 * drag sideways while zoomed. Up and down stays the list's own scroll and fling: the list
 * is scaled from its top-left and laid out 1/scale as tall as the frame, so it still fills
 * the frame and a finger moves it one to one. Once a zoom ends, the pages in view are
 * rendered again for it (up to [pdfPageBitmapSize]'s width cap), keeping the old bitmap
 * until the new one is ready.
 */
@Composable
private fun ZoomablePdfPages(doc: OpenPdf) {
    val zoom = remember(doc) { PdfZoomState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // On the parent, so it sees every event first (Initial pass): two fingers zoom
            // and are taken from the list; one finger on a zoomed document also pans
            // sideways, the list keeping its up-and-down scroll.
            .pointerInput(doc) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val frame = size.width.toFloat()
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1) {
                            if (!pinched) {
                                zoom.animation?.cancel()
                                pinched = true
                            }
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val pan = event.calculatePan()
                            var scroll = 0f
                            if (centroid != Offset.Unspecified) {
                                scroll = zoom.zoomTo(zoom.scale * event.calculateZoom(), centroid.x, centroid.y, frame)
                            }
                            zoom.offsetX = PdfZoom.clampOffsetX(zoom.offsetX + pan.x, frame, zoom.scale)
                            listState.dispatchRawDelta(scroll - pan.y / zoom.scale)
                            event.changes.forEach { it.consume() }
                        } else if (zoom.zoomed) {
                            val pan = event.calculatePan()
                            zoom.offsetX = PdfZoom.clampOffsetX(zoom.offsetX + pan.x, frame, zoom.scale)
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinched) zoom.settle()
                }
            },
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val frameWidth = widthPx.toFloat()
        val renderWidth = (widthPx * zoom.renderScale).roundToInt()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                // Read in placement only: a pinch frame relayouts, it does not recompose.
                .layout { measurable, _ ->
                    val height = (heightPx / zoom.scale).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(widthPx, height))
                    layout(widthPx, heightPx) {
                        placeable.placeWithLayer(0, 0) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offsetX
                        }
                    }
                }
                .pointerInput(doc) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            // [tap] is in the list's own px; the frame point is tap·scale + offset.
                            val focusX = tap.x * zoom.scale + zoom.offsetX
                            val focusY = tap.y * zoom.scale
                            val from = zoom.scale
                            val to = PdfZoom.doubleTapTarget(from)
                            zoom.animation?.cancel()
                            zoom.animation = scope.launch {
                                animate(from, to, animationSpec = tween(220)) { value, _ ->
                                    listState.dispatchRawDelta(zoom.zoomTo(value, focusX, focusY, frameWidth))
                                }
                                zoom.settle()
                            }
                        },
                    )
                },
        ) {
            items(doc.pageCount) { index -> PdfPage(doc, index, renderWidth) }
        }
    }
}

@Composable
private fun PdfPage(doc: OpenPdf, index: Int, widthPx: Int) {
    // Kept across a new render width: a zoom swaps the sharper page in, never a blank one.
    var bitmap by remember(doc, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc, index, widthPx) {
        val rendered = withContext(Dispatchers.IO) {
            try {
                doc.render(index, widthPx)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "[Viewer] Could not render PDF page $index")
                null
            }
        }
        if (rendered != null || bitmap == null) bitmap = rendered
    }
    val page = bitmap
    if (page == null) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f / A4_HEIGHT_RATIO).background(Color.White))
    } else {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = stringResource(R.string.viewer_pdf_page, index + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height),
        )
    }
}

/** PdfRenderer could not read the file: protected by a password, or not a valid PDF. */
internal class PdfUnreadableException(cause: Throwable) : Exception(cause)

/**
 * An open PDF. PdfRenderer allows one page open at a time and is not thread-safe, so every
 * render and the close take the same lock; a render after close draws nothing.
 */
internal class OpenPdf private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount
    private val lock = Any()
    private var closed = false

    fun render(index: Int, targetWidthPx: Int): Bitmap? {
        synchronized(lock) {
            if (closed || index !in 0 until pageCount) return null
            return renderPage(index, targetWidthPx)
        }
    }

    private fun renderPage(index: Int, targetWidthPx: Int): Bitmap {
        val page = renderer.openPage(index)
        return try {
            val (width, height) = pdfPageBitmapSize(page.width, page.height, targetWidthPx)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Pages are drawn on transparency; paper is white.
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    companion object {
        /** Takes ownership of [descriptor]: it is closed here when the file cannot be read. */
        fun open(descriptor: ParcelFileDescriptor): OpenPdf =
            try {
                OpenPdf(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                throw PdfUnreadableException(e)
            }
    }
}
