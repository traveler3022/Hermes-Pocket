package com.hermes.android.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/** Calls into the touch layer (assets/desktop/touch.js) inside the noVNC page. */
internal class DesktopRemote(private val web: WebView) {
    fun type(text: String) = call("type", text)

    fun key(name: String) = call("key", name)

    /** Stored on window first, so a page that is still loading picks the mode up too. */
    fun setTouchMode(touch: Boolean) {
        val mode = JSONObject.quote(if (touch) "touch" else "pad")
        web.evaluateJavascript("window.hermesDeskMode = $mode; window.hermesDesk && hermesDesk.setMode($mode);", null)
    }

    private fun call(function: String, argument: String) {
        web.evaluateJavascript("window.hermesDesk && hermesDesk.$function(${JSONObject.quote(argument)});", null)
    }
}

/** Receives the remote pointer position of every tap on the desktop. */
internal class DesktopClickBridge(private val web: WebView, private val handler: (x: Int, y: Int) -> Unit) {
    @JavascriptInterface
    fun onClick(x: Int, y: Int) {
        web.post { handler(x, y) }
    }

    companion object {
        const val NAME = "HermesDeskBridge"
    }
}

/**
 * An invisible editor for the soft keyboard. Nothing is kept here: each edit the keyboard
 * makes is replayed on the remote desktop as typed text and key presses.
 */
@SuppressLint("ViewConstructor")
internal class RemoteKeyboardView(context: Context, private val remote: () -> DesktopRemote?) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun show() {
        requestFocus()
        inputMethods().showSoftInput(this, 0)
    }

    fun hide() {
        inputMethods().hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    private fun inputMethods() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Visible-password text keeps most keyboards from holding words back for suggestions.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_GO or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return Connection()
    }

    private inner class Connection : BaseInputConnection(this, false) {
        /** Text the keyboard is still composing, already shown on the remote side. */
        private var composing = ""

        private fun showComposing(next: String) {
            val shared = composing.commonPrefixWith(next).length
            val removed = composing.substring(shared)
            val added = next.substring(shared)
            composing = next
            val target = remote() ?: return
            repeat(removed.codePointCount(0, removed.length)) { target.key("Backspace") }
            if (added.isNotEmpty()) target.type(added)
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            showComposing(text.toString())
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            showComposing(text.toString())
            composing = ""
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val target = remote() ?: return true
            repeat(beforeLength) { target.key("Backspace") }
            repeat(afterLength) { target.key("Delete") }
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            remote()?.key("Enter")
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            val target = remote() ?: return true
            val named = NamedKeys[event.keyCode]
            if (named != null) {
                target.key(named)
            } else {
                val char = event.unicodeChar
                if (char > 0) target.type(String(Character.toChars(char)))
            }
            return true
        }
    }

    private companion object {
        val NamedKeys = mapOf(
            KeyEvent.KEYCODE_DEL to "Backspace",
            KeyEvent.KEYCODE_FORWARD_DEL to "Delete",
            KeyEvent.KEYCODE_ENTER to "Enter",
            KeyEvent.KEYCODE_NUMPAD_ENTER to "Enter",
            KeyEvent.KEYCODE_TAB to "Tab",
            KeyEvent.KEYCODE_ESCAPE to "Escape",
            KeyEvent.KEYCODE_DPAD_LEFT to "ArrowLeft",
            KeyEvent.KEYCODE_DPAD_RIGHT to "ArrowRight",
            KeyEvent.KEYCODE_DPAD_UP to "ArrowUp",
            KeyEvent.KEYCODE_DPAD_DOWN to "ArrowDown",
        )
    }
}
