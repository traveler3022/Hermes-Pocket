package com.hermes.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.viewmodel.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PinNewestTurn] on a real LazyColumn: the list moves once when a turn starts and
 * then leaves the reader alone while the agent streams, adds tool cards and status
 * lines — the "keeps jumping while it is still writing, can't read it" bug.
 * Rows are a fixed 100dp in a 400dp list, so four rows are on screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ChatScrollBehaviorTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope
    private var seq = 0L

    private fun user(id: String) = ChatMessage.User(id = id, timestamp = seq++, text = "q")

    private fun assistant(id: String, text: String = "a", streaming: Boolean = false) =
        ChatMessage.Assistant(id = id, timestamp = seq++, text = text, isStreaming = streaming, reasoning = null)

    private fun tool(id: String) = ChatMessage.ToolCall(
        id = id, timestamp = seq++, toolName = "terminal", argsText = "{}", resultText = null,
        error = null, isRunning = true, durationS = null,
    )

    private fun status(id: String) = ChatMessage.Status(id = id, timestamp = seq++, text = "…", isError = false)

    /** Ten old turns, then the open turn: its question followed by six rows of answer. */
    private fun history(): List<ChatMessage> = buildList {
        repeat(10) { add(user("old-u$it")); add(assistant("old-a$it")) }
        add(user("u-open"))
        repeat(6) { add(assistant("open-a$it")) }
    }

    private fun show(initial: List<ChatMessage>) {
        messages = initial
        rule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            Box(Modifier.height(400.dp)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
                PinNewestTurn(listState, messages)
            }
        }
        rule.waitForIdle()
    }

    private fun update(change: (List<ChatMessage>) -> List<ChatMessage>) {
        rule.runOnIdle { messages = change(messages) }
        rule.waitForIdle()
    }

    private fun position() = rule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }

    @Test
    fun opensWithTheNewestQuestionAtTheTop() {
        show(history())
        assertEquals(20, rule.runOnIdle { listState.firstVisibleItemIndex })
        assertEquals(0, rule.runOnIdle { listState.firstVisibleItemScrollOffset })
    }

    @Test
    fun theListStaysStillWhileTheAgentWorks() {
        show(history())
        val pinned = position()
        update { it + assistant("live", "", streaming = true) }
        var text = ""
        repeat(20) {
            text += "token "
            update { list -> list.map { m -> if (m.id == "live") assistant("live", text, streaming = true) else m } }
        }
        update { it + tool("t1") + status("s1") + tool("t2") }
        assertEquals(pinned, position())
    }

    @Test
    fun aReaderWhoScrolledIsNotPulledBack() {
        show(history())
        rule.runOnIdle { scope.launch { listState.scrollToItem(23) } }
        rule.waitForIdle()
        val reading = position()
        update { it + tool("t1") }
        update { it + assistant("live", "partial", streaming = true) }
        update { it + status("s1") }
        assertEquals(reading, position())
    }

    @Test
    fun aNewQuestionIsBroughtIntoView() {
        show(history())
        rule.runOnIdle { scope.launch { listState.scrollToItem(0) } }
        rule.waitForIdle()
        update { it + user("u-new") }
        val visible = rule.runOnIdle { listState.layoutInfo.visibleItemsInfo.map { it.key } }
        assertTrue("new question on screen, saw $visible", "u-new" in visible)
    }
}
