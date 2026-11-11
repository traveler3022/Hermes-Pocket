package com.hermes.android.ui.screen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hermes.android.ui.viewmodel.ChatMessage

/*
 * How the chat list scrolls on its own. Kept out of ChatScreen so the rules can be
 * tested: each one once broke as "the chat keeps jumping while it is still writing".
 */

/**
 * What re-pins the list: the id of the newest user message in [visible]. It changes
 * only when the user starts a new turn — never while the agent streams text, adds a
 * tool card, a status line or its own placeholder — so the list is moved once per turn.
 */
internal fun chatPinKey(visible: List<ChatMessage>): String? =
    visible.lastOrNull { it is ChatMessage.User }?.id

/**
 * Where the newest user message sits in [visible], the list the LazyColumn renders,
 * or -1. It must be counted there: the full message list still holds the rows the
 * chat hides, so its index points further down and pushed the message off the screen.
 */
internal fun chatPinIndex(visible: List<ChatMessage>): Int =
    visible.indexOfLast { it is ChatMessage.User }

/** Whether a row exists after the last one on screen, i.e. the jump-to-bottom button shows. */
internal fun chatAwayFromBottom(totalItems: Int, lastVisibleIndex: Int?): Boolean =
    totalItems != 0 && (lastVisibleIndex == null || lastVisibleIndex < totalItems - 1)

/**
 * Puts the newest user message at the top of the list once per turn, the Gemini /
 * ChatGPT pattern: the question stays in view and the reply streams in under it.
 * Instant [LazyListState.scrollToItem], so no animation is left in flight for the
 * next event to cancel and restart.
 */
@Composable
internal fun PinNewestTurn(listState: LazyListState, visible: List<ChatMessage>) {
    LaunchedEffect(chatPinKey(visible)) {
        val index = chatPinIndex(visible)
        if (index >= 0) listState.scrollToItem(index)
    }
}
