package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The empty state of a new chat: nothing.
 *
 * Kept as a named composable rather than an `if` in the message list so the
 * intent survives the next refactor — the blank space is the design, not a gap
 * waiting for content. The composer is the only affordance, which is also the
 * one thing a user opening a new chat wants to touch.
 */
@Composable
internal fun EmptyChatBody(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth())
}
