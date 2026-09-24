package com.hermes.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.i18n.t
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import timber.log.Timber

/**
 * Holds the shell while the screen is on the back stack. Rotation, a theme change or a
 * split-screen resize recreates the Activity (it declares no configChanges); the shell used
 * to live in the view and was SIGKILLed with it, along with whatever ran in it.
 */
internal class LinuxTerminalSessionHolder : ViewModel() {
    var session: TerminalSession? = null

    override fun onCleared() {
        session?.finishIfRunning()
    }
}

/** A shell in the built-in Linux, drawn by Termux's terminal-view library. */
@Composable
fun LinuxTerminalScreen(
    createLaunchSpec: suspend () -> Result<ProotEnvironment.TerminalLaunchSpec>,
    onNavigateBack: () -> Unit,
) {
    val holder: LinuxTerminalSessionHolder = viewModel()
    val spec by produceState<Result<ProotEnvironment.TerminalLaunchSpec>?>(null) { value = createLaunchSpec() }
    var title by remember { mutableStateOf("Linux") }
    val modifiers = remember { StickyModifiers() }
    var terminal by remember { mutableStateOf<TerminalView?>(null) }

    HermesScaffold(title = title, onBack = onNavigateBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val result = spec
                when {
                    result == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    result.isFailure -> Text(
                        text = result.exceptionOrNull()?.message ?: t("Could not start the terminal.", "ترمینال اجرا نشد."),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> TerminalPane(
                        spec = result.getOrThrow(),
                        holder = holder,
                        modifiers = modifiers,
                        onTitle = { title = it.ifBlank { "Linux" } },
                        onReady = { terminal = it },
                        onFinishedEnter = onNavigateBack,
                    )
                }
            }
            KeyStrip(terminal, modifiers)
        }
    }
}

@Composable
private fun TerminalPane(
    spec: ProotEnvironment.TerminalLaunchSpec,
    holder: LinuxTerminalSessionHolder,
    modifiers: StickyModifiers,
    onTitle: (String) -> Unit,
    onReady: (TerminalView) -> Unit,
    onFinishedEnter: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = colors.background.toArgb()
    val foreground = colors.onBackground.toArgb()
    val fontPx = with(LocalDensity.current) { (12.dp.roundToPx() / 2 * 2).coerceAtLeast(MIN_FONT_PX) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // The emulator copies the scheme once, when it is created on the first layout,
            // so the scheme has to be right before the session exists.
            val colorsChanged = useColors(background, foreground)
            val view = TerminalView(context, null)
            val client = TerminalClient(view, modifiers, fontPx, onTitle, onFinishedEnter)
            view.setTerminalViewClient(client)
            view.setTextSize(fontPx)
            view.setTypeface(Typeface.MONOSPACE)
            view.isFocusableInTouchMode = true
            // A shell kept from before the Activity was recreated carries on in this view. It
            // ends in the holder, never here: finishIfRunning() is a SIGKILL, and this view goes
            // away on every rotation.
            val kept = holder.session
            val shell = if (kept != null) {
                kept.updateTerminalSessionClient(client)
                if (colorsChanged) kept.emulator?.mColors?.reset()
                onTitle(kept.title.orEmpty())
                kept
            } else {
                TerminalSession(
                    spec.executable, spec.workingDirectory, spec.arguments, spec.environment,
                    SCROLLBACK_ROWS, client,
                ).also { holder.session = it }
            }
            view.attachSession(shell)
            onReady(view)
            view.post { client.focusAndShowKeyboard() }
            view
        },
        update = { view ->
            if (useColors(background, foreground)) view.currentSession?.emulator?.mColors?.reset()
            view.setBackgroundColor(background)
            view.onScreenUpdated()
        },
    )
}

/** Makes [background] and [foreground] the terminal defaults; true when they changed. */
private fun useColors(background: Int, foreground: Int): Boolean {
    val defaults = TerminalColors.COLOR_SCHEME.mDefaultColors
    if (defaults[TextStyle.COLOR_INDEX_BACKGROUND] == background &&
        defaults[TextStyle.COLOR_INDEX_FOREGROUND] == foreground
    ) {
        return false
    }
    defaults[TextStyle.COLOR_INDEX_BACKGROUND] = background
    defaults[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
    TerminalColors.COLOR_SCHEME.setCursorColorForBackground()
    return true
}

/**
 * Ctrl and Alt from the key strip. They stay armed until the next key reaches the terminal,
 * whether it comes from the strip or from the soft keyboard.
 */
private class StickyModifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
}

private sealed interface StripKey {
    val label: String

    data class Code(override val label: String, val keyCode: Int) : StripKey
    data class Chars(override val label: String, val text: String) : StripKey
}

private val StripKeys = listOf(
    StripKey.Code("Esc", KeyEvent.KEYCODE_ESCAPE),
    StripKey.Code("Tab", KeyEvent.KEYCODE_TAB),
    StripKey.Code("Enter", KeyEvent.KEYCODE_ENTER),
    StripKey.Code("⌫", KeyEvent.KEYCODE_DEL),
    StripKey.Code("←", KeyEvent.KEYCODE_DPAD_LEFT),
    StripKey.Code("↓", KeyEvent.KEYCODE_DPAD_DOWN),
    StripKey.Code("↑", KeyEvent.KEYCODE_DPAD_UP),
    StripKey.Code("→", KeyEvent.KEYCODE_DPAD_RIGHT),
    StripKey.Chars("-", "-"),
    StripKey.Chars("/", "/"),
    StripKey.Chars("|", "|"),
    StripKey.Chars("~", "~"),
    StripKey.Code("Home", KeyEvent.KEYCODE_MOVE_HOME),
    StripKey.Code("End", KeyEvent.KEYCODE_MOVE_END),
    StripKey.Code("PgUp", KeyEvent.KEYCODE_PAGE_UP),
    StripKey.Code("PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
    StripKey.Code("Del", KeyEvent.KEYCODE_FORWARD_DEL),
)

@Composable
private fun KeyStrip(terminal: TerminalView?, modifiers: StickyModifiers) {
    // Key labels keep their size under large system font settings, or the strip overflows.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        KeyStripRow(terminal, modifiers)
    }
}

@Composable
private fun KeyStripRow(terminal: TerminalView?, modifiers: StickyModifiers) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StripButton("Ctrl", armed = modifiers.ctrl) { modifiers.ctrl = !modifiers.ctrl }
            StripButton("Alt", armed = modifiers.alt) { modifiers.alt = !modifiers.alt }
            StripButton(t("Keyboard", "کیبورد")) { terminal?.let(::showKeyboard) }
            for (key in StripKeys) {
                StripButton(key.label) {
                    val view = terminal ?: return@StripButton
                    when (key) {
                        is StripKey.Code -> view.onKeyDown(key.keyCode, KeyEvent(KeyEvent.ACTION_DOWN, key.keyCode))
                        is StripKey.Chars -> key.text.codePoints().forEach { view.inputCodePoint(it, false, false) }
                    }
                    view.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun StripButton(label: String, armed: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (armed) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (armed) colors.onPrimary else colors.onSurface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** Both callbacks the terminal library needs, in one place. */
private class TerminalClient(
    private val view: TerminalView,
    private val modifiers: StickyModifiers,
    private var fontPx: Int,
    private val onTitle: (String) -> Unit,
    private val onFinishedEnter: () -> Unit,
) : TerminalSessionClient, TerminalViewClient {

    fun focusAndShowKeyboard() = showKeyboard(view)

    // The library may ask several times while handling one key, so release after it is done.
    private fun read(armed: Boolean, release: () -> Unit): Boolean {
        if (armed) view.post(release)
        return armed
    }

    override fun readControlKey(): Boolean = read(modifiers.ctrl) { modifiers.ctrl = false }
    override fun readAltKey(): Boolean = read(modifiers.alt) { modifiers.alt = false }
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onScale(scale: Float): Float {
        if (scale in 0.9f..1.1f) return scale
        fontPx = (fontPx + if (scale > 1f) FONT_STEP_PX else -FONT_STEP_PX).coerceIn(MIN_FONT_PX, MAX_FONT_PX)
        view.setTextSize(fontPx)
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) = focusAndShowKeyboard()
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = view.hasFocus()
    override fun copyModeChanged(copyMode: Boolean) = Unit
    // After the shell exits the library prints "press Enter"; Enter then leaves the screen.
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (session.isRunning || keyCode != KeyEvent.KEYCODE_ENTER) return false
        onFinishedEnter()
        return true
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() = Unit

    override fun onTextChanged(changedSession: TerminalSession) = view.onScreenUpdated()
    override fun onTitleChanged(changedSession: TerminalSession) = onTitle(changedSession.title.orEmpty())
    override fun onSessionFinished(finishedSession: TerminalSession) = view.onScreenUpdated()
    override fun onColorsChanged(session: TerminalSession) = view.onScreenUpdated()
    override fun onTerminalCursorStateChange(state: Boolean) = view.onScreenUpdated()
    override fun onBell(session: TerminalSession) = Unit
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        clipboard().setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val text = clipboard().primaryClip?.getItemAt(0)?.coerceToText(view.context)?.toString()
        if (!text.isNullOrEmpty()) session?.write(text)
    }

    private fun clipboard() = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun logError(tag: String, message: String) = Timber.tag(tag).e(message)
    override fun logWarn(tag: String, message: String) = Timber.tag(tag).w(message)
    override fun logInfo(tag: String, message: String) = Timber.tag(tag).i(message)
    override fun logDebug(tag: String, message: String) = Timber.tag(tag).d(message)
    override fun logVerbose(tag: String, message: String) = Timber.tag(tag).v(message)
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Timber.tag(tag).e(e, message)
    override fun logStackTrace(tag: String, e: Exception) = Timber.tag(tag).e(e)
}

private fun showKeyboard(view: TerminalView) {
    view.requestFocus()
    val input = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    input.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private const val SCROLLBACK_ROWS = 2_000
private const val FONT_STEP_PX = 2
private const val MIN_FONT_PX = 16
private const val MAX_FONT_PX = 72
