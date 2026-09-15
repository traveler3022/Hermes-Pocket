package com.hermes.android.ui.screen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.hermes.android.ui.viewmodel.ChatMessage
import kotlinx.coroutines.flow.first

/**
 * Turns added per page of a long chat. Counted in turns (a user message and
 * everything after it), not list items: one agent turn can hold dozens of tool
 * calls and narration fragments, so an item count showed only a turn or two.
 */
private const val PAGE_TURNS = 10

// Rows from the top of the list at which the next older page is added.
private const val LOAD_THRESHOLD = 3

/** Where the window starts: the [turns]-th user message from the end, or 0 when there are fewer. */
internal fun chatWindowStart(messages: List<ChatMessage>, turns: Int): Int {
    var seen = 0
    for (i in messages.indices.reversed()) {
        if (messages[i] is ChatMessage.User && ++seen == turns) return i
    }
    return 0
}

/**
 * The tail of [messages] a chat renders, grown by a page of turns each time
 * the user scrolls to the top of the list. Rebuilding and laying out a whole
 * long history on every streaming flush is what made long chats heavy, so
 * older turns are only added once they are asked for.
 *
 * [resetKey] (the loaded session) shrinks it back to one page; [enabled] =
 * false (an active search) returns everything, so no hit is ever hidden.
 */
@Composable
internal fun rememberChatHistoryWindow(
    messages: List<ChatMessage>,
    listState: LazyListState,
    resetKey: Any?,
    enabled: Boolean,
): List<ChatMessage> {
    var turns by remember(resetKey) { mutableIntStateOf(PAGE_TURNS) }
    val start = if (enabled) chatWindowStart(messages, turns) else 0
    LaunchedEffect(listState, start, resetKey) {
        if (start == 0) return@LaunchedEffect
        // Only a scroll reaching the top grows the window — a freshly opened
        // chat also sits at index 0 before it jumps to its latest message.
        snapshotFlow { listState.isScrollInProgress && listState.firstVisibleItemIndex < LOAD_THRESHOLD }
            .first { it }
        turns += PAGE_TURNS
    }
    return remember(messages, start) {
        if (start == 0) messages else messages.subList(start, messages.size)
    }
}
