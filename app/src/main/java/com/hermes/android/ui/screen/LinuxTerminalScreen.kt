package com.hermes.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.i18n.t
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import kotlin.math.roundToInt

/**
 * An interactive shell in the built-in Alpine rootfs — a port of Aether's `AlpineTerminalScreen`
 * (Termux's terminal emulator on a pty running proot `sh -i`), with its extra-keys bar.
 */
@Composable
fun LinuxTerminalScreen(
    createLaunchSpec: suspend () -> Result<ProotEnvironment.TerminalLaunchSpec>,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var launchSpec by remember { mutableStateOf<ProotEnvironment.TerminalLaunchSpec?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }
    var title by remember { mutableStateOf("Linux") }
    var controlDown by remember { mutableStateOf(false) }
    var altDown by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val textSizePx = remember(context) { defaultTerminalTextSizePx(context) }
    val typeface = remember { terminalTypeface() }
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) AndroidColor.BLACK else AndroidColor.WHITE
    val foreground = if (isDark) AndroidColor.WHITE else AndroidColor.BLACK

    LaunchedEffect(isDark) {
        val scheme = TerminalColors.COLOR_SCHEME
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = background
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        scheme.setCursorColorForBackground()
        terminalView?.onScreenUpdated()
    }

    LaunchedEffect(Unit) {
        createLaunchSpec().fold(
            onSuccess = { launchSpec = it },
            onFailure = { errorMessage = it.message ?: "Unable to start the terminal." },
        )
    }

    DisposableEffect(terminalSession) {
        val session = terminalSession
        onDispose { session?.finishIfRunning() }
    }

    HermesScaffold(title = title, onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            val spec = launchSpec
            when {
                errorMessage.isNotBlank() -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }

                spec == null -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                else -> AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { viewContext ->
                        val view = TerminalView(viewContext, null).apply {
                            setBackgroundColor(background)
                            setTextSize(textSizePx)
                            setTypeface(typeface)
                            isFocusable = true
                            isFocusableInTouchMode = true
                        }
                        val session = TerminalSession(
                            spec.executable,
                            spec.workingDirectory,
                            spec.arguments,
                            spec.environment,
                            2_000,
                            SessionClient(
                                context = viewContext,
                                invalidate = { view.onScreenUpdated() },
                                updateTitle = { title = it.ifBlank { "Linux" } },
                            ),
                        )
                        view.setTerminalViewClient(ViewClient(view))
                        view.attachSession(session)
                        view.requestFocus()
                        view.post {
                            view.updateSize()
                            showKeyboard(view)
                        }
                        terminalView = view
                        terminalSession = session
                        view
                    },
                    update = { view -> view.setBackgroundColor(background) },
                )
            }

            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                ExtraKeysBar(
                    terminalView = terminalView,
                    terminalSession = terminalSession,
                    controlDown = controlDown,
                    altDown = altDown,
                    onControlDownChange = { controlDown = it },
                    onAltDownChange = { altDown = it },
                )
            }
        }
    }
}

@Composable
private fun ExtraKeysBar(
    terminalView: TerminalView?,
    terminalSession: TerminalSession?,
    controlDown: Boolean,
    altDown: Boolean,
    onControlDownChange: (Boolean) -> Unit,
    onAltDownChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExtraKeyRow {
            ExtraKey("ESC") { terminalView.sendKey(KeyEvent.KEYCODE_ESCAPE, controlDown, altDown) }
            ExtraKey("TAB") { terminalView.sendKey(KeyEvent.KEYCODE_TAB, controlDown, altDown) }
            ExtraKey("CTRL", active = controlDown) { onControlDownChange(!controlDown) }
            ExtraKey("ALT", active = altDown) { onAltDownChange(!altDown) }
            ExtraKey("-") { terminalView.sendText("-", controlDown, altDown) }
            ExtraKey("/") { terminalView.sendText("/", controlDown, altDown) }
            ExtraKey("|") { terminalView.sendText("|", controlDown, altDown) }
            ExtraKey("HOME") { terminalView.sendKey(KeyEvent.KEYCODE_MOVE_HOME, controlDown, altDown) }
            ExtraKey("END") { terminalView.sendKey(KeyEvent.KEYCODE_MOVE_END, controlDown, altDown) }
            ExtraKey("PGUP") { terminalView.sendKey(KeyEvent.KEYCODE_PAGE_UP, controlDown, altDown) }
            ExtraKey("PGDN") { terminalView.sendKey(KeyEvent.KEYCODE_PAGE_DOWN, controlDown, altDown) }
        }
        ExtraKeyRow {
            ExtraKey(t("KEYB", "کیبورد")) { terminalView?.let(::showKeyboard) }
            ExtraKey("BKSP") { terminalView.sendKey(KeyEvent.KEYCODE_DEL, controlDown, altDown) }
            ExtraKey("DEL") { terminalView.sendKey(KeyEvent.KEYCODE_FORWARD_DEL, controlDown, altDown) }
            ExtraKey("LEFT") { terminalView.sendKey(KeyEvent.KEYCODE_DPAD_LEFT, controlDown, altDown) }
            ExtraKey("DOWN") { terminalView.sendKey(KeyEvent.KEYCODE_DPAD_DOWN, controlDown, altDown) }
            ExtraKey("UP") { terminalView.sendKey(KeyEvent.KEYCODE_DPAD_UP, controlDown, altDown) }
            ExtraKey("RIGHT") { terminalView.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, controlDown, altDown) }
            ExtraKey("ENTER") { terminalSession?.write("\r") }
        }
    }
}

@Composable
private fun ExtraKeyRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun ExtraKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .background(
                color = if (active) colors.primary.copy(alpha = 0.28f) else colors.surface,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) colors.onSurface else colors.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

private class SessionClient(
    private val context: Context,
    private val invalidate: () -> Unit,
    private val updateTitle: (String) -> Unit,
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = invalidate()
    override fun onTitleChanged(changedSession: TerminalSession) = updateTitle(changedSession.title.orEmpty())
    override fun onSessionFinished(finishedSession: TerminalSession) = invalidate()

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isNotEmpty()) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            session?.write(bytes, 0, bytes.size)
        }
    }

    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = invalidate()
    override fun onTerminalCursorStateChange(state: Boolean) = invalidate()
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}

private class ViewClient(private val view: TerminalView) : TerminalViewClient {
    override fun onScale(scale: Float): Float = scale.coerceIn(0.7f, 1.6f)
    override fun onSingleTapUp(e: MotionEvent) = showKeyboard(view)
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = view.hasFocus()
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() = Unit
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}

private fun showKeyboard(view: android.view.View) {
    view.post {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}

private fun defaultTerminalTextSizePx(context: Context): Int {
    val dip = context.resources.displayMetrics.density
    var size = (12f * dip).roundToInt()
    if (size % 2 == 1) size--
    return size.coerceIn((4f * dip).toInt(), 256)
}

private fun terminalTypeface(): Typeface {
    val systemMono = File("/system/fonts/DroidSansMono.ttf")
    return if (systemMono.isFile && systemMono.length() > 0L) Typeface.createFromFile(systemMono) else Typeface.MONOSPACE
}

private fun TerminalView?.sendKey(keyCode: Int, controlDown: Boolean, altDown: Boolean) {
    val view = this ?: return
    var keyMod = 0
    if (controlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
    if (altDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
    if (!view.handleKeyCode(keyCode, keyMod)) {
        var metaState = 0
        if (controlDown) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altDown) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        view.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
    }
    view.requestFocus()
}

private fun TerminalView?.sendText(text: String, controlDown: Boolean, altDown: Boolean) {
    val view = this ?: return
    text.codePoints().forEach { view.inputCodePoint(it, controlDown, altDown) }
    view.requestFocus()
}
