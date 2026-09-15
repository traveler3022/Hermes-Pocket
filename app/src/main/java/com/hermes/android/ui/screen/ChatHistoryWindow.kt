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

/** Messages added per page of a long chat. */
private const val PAGE_SIZE = 40

// Rows from the top of the list at which the next older page is added.
private const val LOAD_THRESHOLD = 3

/**
 * Where the window starts: at least [count] messages from the end, moved back
 * to a user message so a turn (and the trace folded into it) is never cut.
 */
internal fun chatWindowStart(messages: List<ChatMessage>, count: Int): Int {
    if (messages.size <= count) return 0
    var start = messages.size - count
    while (start > 0 && messages[start] !is ChatMessage.User) start--
    return start
}

/**
 * The tail of [messages] a chat renders, grown by a page each time the user
 * scrolls to the top of the list. Rebuilding and laying out a whole long
 * history on every streaming flush is what made long chats heavy, so older
 * turns are only added once they are asked for.
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
    var size by remember(resetKey) { mutableIntStateOf(PAGE_SIZE) }
    val start = if (enabled) chatWindowStart(messages, size) else 0
    LaunchedEffect(listState, start, resetKey) {
        if (start == 0) return@LaunchedEffect
        // Only a scroll reaching the top grows the window — a freshly opened
        // chat also sits at index 0 before it jumps to its latest message.
        snapshotFlow { listState.isScrollInProgress && listState.firstVisibleItemIndex < LOAD_THRESHOLD }
            .first { it }
        size += PAGE_SIZE
    }
    return remember(messages, start) {
        if (start == 0) messages else messages.subList(start, messages.size)
    }
}
