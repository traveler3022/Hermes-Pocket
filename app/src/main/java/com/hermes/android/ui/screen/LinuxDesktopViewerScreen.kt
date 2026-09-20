package com.hermes.android.ui.screen

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.runtime.linux.LinuxDesktop
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.LinuxDesktopViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.graphics.Color as AndroidColor

/**
 * The live desktop, straight from Aether's `AlpineChromeScreen`: noVNC in a WebView, with a
 * touchpad layer (one finger moves the pointer, two scroll, three drag) and the phone
 * keyboard bridged into the remote screen.
 */
@Composable
fun LinuxDesktopViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinuxDesktopViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFocusRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var webView by remember { mutableStateOf<WebView?>(null) }
    var imeBridgeValue by remember { mutableStateOf(newImeBridgeValue()) }
    var errorMessage by remember { mutableStateOf("") }
    var starting by remember { mutableStateOf(true) }
    /** false = touchpad (one finger nudges the pointer); true = the pointer follows the finger. */
    var directTouch by remember { mutableStateOf(false) }

    fun showKeyboard() {
        imeBridgeValue = newImeBridgeValue()
        textFocusRequester.requestFocus()
        scope.launch {
            delay(60)
            keyboardController?.show()
        }
    }

    fun connect() {
        scope.launch {
            starting = true
            viewModel.startViewing()
                .onSuccess {
                    errorMessage = ""
                    webView?.loadUrl(viewModel.viewerUrl)
                }
                .onFailure { errorMessage = it.message ?: "" }
            starting = false
        }
    }

    fun handleImeValueChange(next: TextFieldValue) {
        val delta = calculateImeDelta(imeBridgeValue.text, next.text)
        repeat(delta.backspaceCount) { webView?.sendVncKey("Backspace") }
        if (delta.insertedText.isNotEmpty()) webView?.sendVncText(delta.insertedText)
        imeBridgeValue = next
    }

    BackHandler(enabled = imeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    BackHandler(enabled = !imeVisible, onBack = onNavigateBack)

    LaunchedEffect(Unit) { connect() }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            // Leaving the screen kills the stream: nothing of this desktop is reachable
            // from anywhere while nobody is looking at it.
            viewModel.stopViewing()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "بازگشت"))
            }
            Text(
                text = t("Desktop", "دسکتاپ"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    directTouch = !directTouch
                    webView?.setPointerMode(if (directTouch) "touch" else "pad")
                },
            ) {
                Icon(
                    imageVector = if (directTouch) Icons.Default.TouchApp else Icons.Default.Mouse,
                    contentDescription = if (directTouch) {
                        t("Touch mode — tap where you want", "حالت لمس — هرجا بزنی همان‌جا")
                    } else {
                        t("Touchpad mode — drag to move the pointer", "حالت ماوس — با کشیدن نشانگر را ببر")
                    },
                )
            }
            IconButton(onClick = { showKeyboard() }) {
                Icon(Icons.Default.Keyboard, contentDescription = t("Keyboard", "کیبورد"))
            }
            IconButton(onClick = { connect() }) {
                Icon(Icons.Default.Refresh, contentDescription = t("Reconnect", "اتصال دوباره"))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (state == LinuxDesktop.State.Running) {
                DesktopVncWebView(
                    url = viewModel.viewerUrl,
                    onRemoteLeftClick = { _, y ->
                        scope.launch { if (viewModel.shouldShowKeyboard(y)) showKeyboard() }
                    },
                    pointerMode = { if (directTouch) "touch" else "pad" },
                    onCreated = { webView = it },
                    modifier = Modifier.fillMaxSize(),
                )
                // Invisible field: the IME types here and every change is forwarded to VNC.
                BasicTextField(
                    value = imeBridgeValue,
                    onValueChange = ::handleImeValueChange,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(textFocusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onDone = { webView?.sendVncKey("Enter") },
                        onGo = { webView?.sendVncKey("Enter") },
                        onNext = { webView?.sendVncKey("Enter") },
                        onSearch = { webView?.sendVncKey("Enter") },
                        onSend = { webView?.sendVncKey("Enter") },
                    ),
                )
            }
            if (starting || state == LinuxDesktop.State.Starting) {
                CircularProgressIndicator(color = Color.White)
            } else if (state != LinuxDesktop.State.Running) {
                Text(
                    text = errorMessage.ifBlank {
                        t("The desktop is not running.", "دسکتاپ روشن نیست.")
                    },
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DesktopVncWebView(
    url: String,
    onRemoteLeftClick: (Int, Int) -> Unit,
    pointerMode: () -> String,
    onCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                addJavascriptInterface(DesktopBridge(this, onRemoteLeftClick), "HermesDesktopBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        view.evaluateJavascript(ViewportScript, null)
                        // A reconnect rebuilds the input layer; the user's choice outlives it.
                        view.setPointerMode(pointerMode())
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                excludeSystemNavigationGestures()
                loadUrl(url)
                onCreated(this)
            }
        },
    )
}

private class DesktopBridge(
    private val webView: WebView,
    private val onRemoteLeftClick: (Int, Int) -> Unit,
) {
    @JavascriptInterface
    fun onLeftClick(x: Int, y: Int) {
        webView.post { onRemoteLeftClick(x, y) }
    }
}

private fun WebView.sendVncText(text: String) {
    evaluateJavascript("window.hermesVncInput && window.hermesVncInput.sendText(${JSONObject.quote(text)});", null)
}

/**
 * Sets the pointer mode, surviving the race with the input layer's own install: the global
 * is what [ViewportScript] reads when it comes up, the call is what a live layer listens to.
 */
private fun WebView.setPointerMode(mode: String) {
    evaluateJavascript(
        "window.hermesVncPointerMode = ${JSONObject.quote(mode)};" +
            "window.hermesVncInput && window.hermesVncInput.setPointerMode(${JSONObject.quote(mode)});",
        null,
    )
}

private fun WebView.sendVncKey(key: String) {
    evaluateJavascript("window.hermesVncInput && window.hermesVncInput.sendKey(${JSONObject.quote(key)});", null)
}

private fun WebView.excludeSystemNavigationGestures() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    fun updateExclusionRect() {
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateExclusionRect() }
    post(::updateExclusionRect)
}

private const val ImeBridgeSentinelLength = 32
private val ImeBridgeSentinel = "​".repeat(ImeBridgeSentinelLength)

private fun newImeBridgeValue(): TextFieldValue =
    TextFieldValue(text = ImeBridgeSentinel, selection = TextRange(ImeBridgeSentinel.length))

private data class ImeDelta(val backspaceCount: Int, val insertedText: String)

/** What the IME changed between two values, as backspaces + inserted text. */
private fun calculateImeDelta(previous: String, next: String): ImeDelta {
    var prefixLength = 0
    val sharedLength = minOf(previous.length, next.length)
    while (prefixLength < sharedLength && previous[prefixLength] == next[prefixLength]) {
        prefixLength += 1
    }
    var suffixLength = 0
    while (
        suffixLength < previous.length - prefixLength &&
        suffixLength < next.length - prefixLength &&
        previous[previous.length - suffixLength - 1] == next[next.length - suffixLength - 1]
    ) {
        suffixLength += 1
    }
    val removed = previous.substring(prefixLength, previous.length - suffixLength)
    return ImeDelta(
        backspaceCount = removed.codePointCount(0, removed.length),
        insertedText = next.substring(prefixLength, next.length - suffixLength),
    )
}

/** Aether's noVNC touchpad layer, renamed for Hermes. */
private val ViewportScript = """
    (() => {
      let viewport = document.querySelector('meta[name="viewport"]');
      if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.prepend(viewport);
      }
      viewport.content =
        'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

      let style = document.getElementById('hermes-mobile-style');
      if (!style) {
        style = document.createElement('style');
        style.id = 'hermes-mobile-style';
        document.head.appendChild(style);
      }
      style.textContent = `
        html, body {
          width: 100% !important;
          margin: 0 !important;
          overflow: hidden !important;
          overscroll-behavior: none !important;
        }
        #top_bar {
          box-sizing: border-box !important;
          min-height: 24px !important;
          padding: 4px 8px !important;
          flex: 0 0 24px !important;
          font: 500 10px/16px sans-serif !important;
        }
        #status {
          display: block !important;
          overflow: hidden !important;
          text-overflow: ellipsis !important;
          white-space: nowrap !important;
        }
        #sendCtrlAltDelButton { display: none !important; }
        #screen {
          min-width: 0 !important;
          min-height: 0 !important;
          overflow: hidden !important;
          touch-action: none !important;
        }
        #screen canvas { touch-action: none !important; }
        body > canvas[style*="z-index: 65535"] {
          transform: scale(var(--hermes-cursor-scale)) !important;
          transform-origin: top left !important;
          will-change: left, top, transform;
        }
      `;
      document.documentElement.style.setProperty(
        '--hermes-cursor-scale',
        String(1 / Math.max(1, window.devicePixelRatio)),
      );

      const fitViewer = () => {
        const height = window.innerHeight + 'px';
        document.documentElement.style.height = height;
        document.body.style.height = height;
        window.dispatchEvent(new Event('resize'));
      };

      const installTouchpad = () => {
        const canvas = document.querySelector('#screen canvas');
        if (!canvas || canvas.width < 1 || canvas.height < 1) return false;
        if (canvas.dataset.hermesTouchpad === 'true') return true;
        canvas.dataset.hermesTouchpad = 'true';

        let cursorX = canvas.width / 2;
        let cursorY = canvas.height / 2;
        let maxTouches = 0;
        let startTime = 0;
        let startX = 0;
        let startY = 0;
        let lastX = 0;
        let lastY = 0;
        let moved = false;
        let pendingDeltaX = 0;
        let pendingDeltaY = 0;
        let moveFrame = 0;
        let pendingScrollX = 0;
        let pendingScrollY = 0;
        let scrollFrame = 0;
        let gestureActive = false;
        let leftDragActive = false;
        // 'pad' is the touchpad: one finger nudges the pointer. 'touch' puts the pointer
        // exactly where the finger lands, the way the phone's own screen behaves.
        let pointerMode = window.hermesVncPointerMode === 'touch' ? 'touch' : 'pad';
        let zoom = 1;
        let panX = 0;
        let panY = 0;
        let pinchDistance = 0;
        let pinching = false;

        const centroid = touches => {
          let x = 0;
          let y = 0;
          for (const touch of touches) {
            x += touch.clientX;
            y += touch.clientY;
          }
          return { x: x / Math.max(1, touches.length), y: y / Math.max(1, touches.length) };
        };

        const stopTouch = event => {
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();
        };

        const mousePosition = () => {
          const rect = canvas.getBoundingClientRect();
          return {
            x: rect.left + cursorX * rect.width / Math.max(1, canvas.width),
            y: rect.top + cursorY * rect.height / Math.max(1, canvas.height),
          };
        };

        const applyTransform = () => {
          canvas.style.transformOrigin = '0 0';
          canvas.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) scale(' + zoom + ')';
        };

        // Keep the zoomed canvas covering the area it occupied at 1x — no dead margins.
        const clampPan = () => {
          const maxX = Math.max(0, canvas.offsetWidth * zoom - canvas.offsetWidth);
          const maxY = Math.max(0, canvas.offsetHeight * zoom - canvas.offsetHeight);
          panX = Math.min(0, Math.max(-maxX, panX));
          panY = Math.min(0, Math.max(-maxY, panY));
        };

        /** Zooms to [nextZoom] leaving the pixel under the anchor where it is. */
        const setZoom = (nextZoom, anchorX, anchorY) => {
          const previous = zoom;
          const next = Math.max(1, Math.min(4, nextZoom));
          if (Math.abs(next - previous) < 0.001) return;
          const rect = canvas.getBoundingClientRect();
          const baseLeft = rect.left - panX;
          const baseTop = rect.top - panY;
          const unitX = (anchorX - rect.left) / previous;
          const unitY = (anchorY - rect.top) / previous;
          zoom = next;
          panX = anchorX - unitX * zoom - baseLeft;
          panY = anchorY - unitY * zoom - baseTop;
          clampPan();
          applyTransform();
        };

        const resetZoom = () => {
          zoom = 1;
          panX = 0;
          panY = 0;
          applyTransform();
        };

        /** Puts the pointer under the finger. getBoundingClientRect already includes zoom. */
        const setCursorFromClient = (clientX, clientY) => {
          const rect = canvas.getBoundingClientRect();
          cursorX = Math.max(0, Math.min(canvas.width - 1,
            (clientX - rect.left) * canvas.width / Math.max(1, rect.width)));
          cursorY = Math.max(0, Math.min(canvas.height - 1,
            (clientY - rect.top) * canvas.height / Math.max(1, rect.height)));
        };

        const dispatchMouse = (target, type, button, buttons) => {
          const position = mousePosition();
          target.dispatchEvent(new MouseEvent(type, {
            bubbles: true,
            cancelable: true,
            clientX: position.x,
            clientY: position.y,
            button,
            buttons,
          }));
        };

        const emitMouse = (type, button, buttons) => dispatchMouse(canvas, type, button, buttons);

        const releaseCapture = button => {
          const proxy = document.getElementById('noVNC_mouse_capture_elem');
          if (document.captureElement && proxy) {
            dispatchMouse(proxy, 'mouseup', button, 0);
          } else {
            emitMouse('mouseup', button, 0);
          }
        };

        const moveCursor = (deltaX, deltaY) => {
          const rect = canvas.getBoundingClientRect();
          cursorX = Math.max(0, Math.min(canvas.width - 1,
            cursorX + deltaX * canvas.width / Math.max(1, rect.width)));
          cursorY = Math.max(0, Math.min(canvas.height - 1,
            cursorY + deltaY * canvas.height / Math.max(1, rect.height)));
          emitMouse('mousemove', 0, leftDragActive ? 1 : 0);
        };

        const flushCursorMove = () => {
          if (moveFrame !== 0) {
            cancelAnimationFrame(moveFrame);
            moveFrame = 0;
          }
          if (pendingDeltaX === 0 && pendingDeltaY === 0) return;
          const nextDeltaX = pendingDeltaX;
          const nextDeltaY = pendingDeltaY;
          pendingDeltaX = 0;
          pendingDeltaY = 0;
          moveCursor(nextDeltaX, nextDeltaY);
        };

        const scheduleCursorMove = (deltaX, deltaY) => {
          pendingDeltaX += deltaX;
          pendingDeltaY += deltaY;
          if (moveFrame !== 0) return;
          moveFrame = requestAnimationFrame(() => {
            moveFrame = 0;
            flushCursorMove();
          });
        };

        const emitWheel = (deltaX, deltaY) => {
          const position = mousePosition();
          canvas.dispatchEvent(new WheelEvent('wheel', {
            bubbles: true,
            cancelable: true,
            clientX: position.x,
            clientY: position.y,
            deltaX,
            deltaY,
            deltaMode: 0,
          }));
        };

        const flushScroll = () => {
          if (scrollFrame !== 0) {
            cancelAnimationFrame(scrollFrame);
            scrollFrame = 0;
          }
          if (pendingScrollX === 0 && pendingScrollY === 0) return;
          const nextScrollX = pendingScrollX;
          const nextScrollY = pendingScrollY;
          pendingScrollX = 0;
          pendingScrollY = 0;
          emitWheel(-nextScrollX * 2, -nextScrollY * 2);
        };

        const scheduleScroll = (deltaX, deltaY) => {
          pendingScrollX += deltaX;
          pendingScrollY += deltaY;
          if (scrollFrame !== 0) return;
          scrollFrame = requestAnimationFrame(() => {
            scrollFrame = 0;
            flushScroll();
          });
        };

        const keyDetails = {
          Backspace: { code: 'Backspace', keyCode: 8 },
          Delete: { code: 'Delete', keyCode: 46 },
          Enter: { code: 'Enter', keyCode: 13 },
          Tab: { code: 'Tab', keyCode: 9 },
          ArrowLeft: { code: 'ArrowLeft', keyCode: 37 },
          ArrowUp: { code: 'ArrowUp', keyCode: 38 },
          ArrowRight: { code: 'ArrowRight', keyCode: 39 },
          ArrowDown: { code: 'ArrowDown', keyCode: 40 },
        };

        const emitKey = (type, key, code, keyCode) => {
          canvas.dispatchEvent(new KeyboardEvent(type, {
            key,
            code,
            keyCode,
            which: keyCode,
            bubbles: true,
            cancelable: true,
          }));
        };

        const sendKey = key => {
          const details = keyDetails[key] || {
            code: 'Unidentified',
            keyCode: key.length === 1 ? key.toUpperCase().charCodeAt(0) : 0,
          };
          emitKey('keydown', key, details.code, details.keyCode);
          emitKey('keyup', key, details.code, details.keyCode);
        };

        const click = () => {
          emitMouse('mousemove', 0, 0);
          emitMouse('mousedown', 0, 1);
          releaseCapture(0);
        };

        const beginLeftDrag = () => {
          if (leftDragActive) return;
          emitMouse('mousemove', 0, 0);
          emitMouse('mousedown', 0, 1);
          leftDragActive = true;
        };

        const endLeftDrag = () => {
          if (!leftDragActive) return;
          flushCursorMove();
          releaseCapture(0);
          leftDragActive = false;
        };

        const sendGesture = type => {
          const position = mousePosition();
          canvas.dispatchEvent(new CustomEvent('gesturestart', {
            detail: { type, clientX: position.x, clientY: position.y },
            bubbles: true,
            cancelable: true,
          }));
        };

        const resetGesture = () => {
          if (moveFrame !== 0) cancelAnimationFrame(moveFrame);
          if (scrollFrame !== 0) cancelAnimationFrame(scrollFrame);
          moveFrame = 0;
          scrollFrame = 0;
          pendingDeltaX = 0;
          pendingDeltaY = 0;
          pendingScrollX = 0;
          pendingScrollY = 0;
          endLeftDrag();
          maxTouches = 0;
          moved = false;
          gestureActive = false;
          pinchDistance = 0;
          pinching = false;
        };

        document.addEventListener('touchstart', event => {
          const position = centroid(event.touches);
          if (maxTouches === 0) {
            const rect = canvas.getBoundingClientRect();
            if (position.x < rect.left || position.x > rect.right ||
                position.y < rect.top || position.y > rect.bottom) {
              return;
            }
            if (document.captureElement) releaseCapture(0);
            gestureActive = true;
            startTime = performance.now();
            moved = false;
          }
          if (!gestureActive) return;
          stopTouch(event);
          maxTouches = Math.max(maxTouches, event.touches.length);
          startX = position.x;
          startY = position.y;
          lastX = position.x;
          lastY = position.y;
          if (event.touches.length === 2) {
            pinchDistance = Math.hypot(
              event.touches[0].clientX - event.touches[1].clientX,
              event.touches[0].clientY - event.touches[1].clientY,
            );
            pinching = false;
            // A second finger means zoom or scroll, never a drag left over from the first.
            endLeftDrag();
          } else if (event.touches.length === 1 && pointerMode === 'touch') {
            setCursorFromClient(position.x, position.y);
            emitMouse('mousemove', 0, 0);
            beginLeftDrag();
          }
        }, { capture: true, passive: false });

        document.addEventListener('touchmove', event => {
          if (!gestureActive) return;
          stopTouch(event);
          const position = centroid(event.touches);
          const deltaX = position.x - lastX;
          const deltaY = position.y - lastY;
          if (Math.hypot(position.x - startX, position.y - startY) > 8) moved = true;
          if (maxTouches === 1 && event.touches.length === 1) {
            if (pointerMode === 'touch') {
              setCursorFromClient(position.x, position.y);
              emitMouse('mousemove', 0, leftDragActive ? 1 : 0);
            } else {
              scheduleCursorMove(deltaX, deltaY);
            }
          } else if (maxTouches === 2 && event.touches.length === 2) {
            const spread = Math.hypot(
              event.touches[0].clientX - event.touches[1].clientX,
              event.touches[0].clientY - event.touches[1].clientY,
            );
            if (pinchDistance === 0) pinchDistance = spread;
            // Fingers changing their distance is a pinch; fingers keeping it and moving
            // together is a scroll (or a pan, once there is something to pan over).
            if (pinching || Math.abs(spread - pinchDistance) > 24) {
              pinching = true;
              setZoom(zoom * (spread / Math.max(1, pinchDistance)), position.x, position.y);
              pinchDistance = spread;
            } else if (zoom > 1) {
              panX += deltaX;
              panY += deltaY;
              clampPan();
              applyTransform();
            } else {
              scheduleScroll(deltaX, deltaY);
            }
          } else if (maxTouches === 3 && event.touches.length === 3 && moved) {
            beginLeftDrag();
            scheduleCursorMove(deltaX, deltaY);
          }
          lastX = position.x;
          lastY = position.y;
        }, { capture: true, passive: false });

        document.addEventListener('touchend', event => {
          if (!gestureActive) return;
          stopTouch(event);
          if (leftDragActive && event.touches.length < 3) endLeftDrag();
          if (event.touches.length > 0) {
            const position = centroid(event.touches);
            lastX = position.x;
            lastY = position.y;
            return;
          }
          flushCursorMove();
          flushScroll();
          const isTap = !moved && performance.now() - startTime <= 450;
          if (isTap && maxTouches === 1) {
            // In direct-touch mode the finger landing already pressed the button, so
            // clicking again here would double every tap.
            if (pointerMode !== 'touch') click();
            // Tapping Chromium's address bar: select what is there, ready to be replaced.
            if (cursorY >= 34 && cursorY <= 100) {
              window.setTimeout(() => {
                emitKey('keydown', 'Control', 'ControlLeft', 17);
                emitKey('keydown', 'a', 'KeyA', 65);
                emitKey('keyup', 'a', 'KeyA', 65);
                emitKey('keyup', 'Control', 'ControlLeft', 17);
              }, 40);
            }
            if (window.HermesDesktopBridge) {
              window.HermesDesktopBridge.onLeftClick(Math.round(cursorX), Math.round(cursorY));
            }
          } else if (isTap && maxTouches === 2 && !pinching) {
            sendGesture('twotap');
          }
          maxTouches = 0;
          gestureActive = false;
          pinchDistance = 0;
          pinching = false;
        }, { capture: true, passive: false });

        document.addEventListener('touchcancel', event => {
          if (!gestureActive) return;
          stopTouch(event);
          resetGesture();
        }, { capture: true, passive: false });

        window.addEventListener('blur', resetGesture);

        window.hermesVncInput = {
          sendKey,
          setPointerMode(mode) {
            endLeftDrag();
            pointerMode = mode === 'touch' ? 'touch' : 'pad';
            window.hermesVncPointerMode = pointerMode;
          },
          resetZoom,
          sendText(text) {
            for (const character of text) {
              if (character === '\n') {
                sendKey('Enter');
              } else {
                sendKey(character);
              }
            }
          },
        };
        return true;
      };

      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          fitViewer();
          if (!installTouchpad()) {
            const interval = window.setInterval(() => {
              if (installTouchpad()) window.clearInterval(interval);
            }, 100);
          }
        });
      });
    })();
""".trimIndent()
