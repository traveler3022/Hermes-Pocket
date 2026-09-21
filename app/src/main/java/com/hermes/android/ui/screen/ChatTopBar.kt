package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState
import kotlinx.coroutines.delay

/**
 * Chat top bar (frames `7a` and `8c`) — the drawer-architecture chrome.
 *
 * Not yet wired into [ChatScreen]: the screen's current bar carries context,
 * search and the runtime shortcut, and this one routes those through a `⋮`
 * overflow the host has to supply. Landed as its own component so the switch
 * is one call site rather than a rewrite of the screen.
 *
 * - Leading ☰ opens the Workspace drawer, with an amber dot when something in
 *   it is waiting on the user — one pixel of state instead of a new screen.
 * - Trailing: new chat, then `⋮` for everything that used to be a floating
 *   button. Overflow sits on the outer edge, where a thumb reaches least.
 * - The centre is empty by default. It is used only while there is something
 *   to say about the connection ([ConnectionChip]) or, once connected, for the
 *   session title — never both.
 *
 * The buttons are [HxHeaderCircleButton], the same floating circles the
 * composer and the current bar use, so the chrome stays one design.
 */
@Composable
internal fun ChatTopBar(
    connection: ChatConnectionState,
    hasWorkspaceAttention: Boolean,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOverflow: () -> Unit,
    title: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            HxHeaderCircleButton(
                icon = Icons.Default.Menu,
                contentDescription = t("Open drawer", "باز کردن کشو"),
                onClick = onOpenDrawer,
            )
            if (hasWorkspaceAttention) {
                Spacer(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(HermesAmber),
                )
            }
        }

        // Centre: connection first, title second, nothing third.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            ConnectionChip(connection)
            if (connection == ChatConnectionState.Connected && !title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        HxHeaderCircleButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            contentDescription = t("New chat", "گفتگوی جدید"),
            onClick = onNewChat,
        )
        IconButton(onClick = onOverflow) {
            Icon(Icons.Default.MoreVert, contentDescription = t("More", "بیشتر"))
        }
    }
}

/**
 * "Connecting…" → "Connected" → nothing.
 *
 * The success state is the point of this component: it confirms for 1.2s and
 * then removes itself, so a healthy session has an empty top bar instead of a
 * permanent green badge the user stops reading on day two. Only failure
 * persists, because only failure needs an action.
 */
@Composable
private fun ConnectionChip(connection: ChatConnectionState) {
    var showConnected by remember { mutableStateOf(false) }

    LaunchedEffect(connection) {
        showConnected = connection == ChatConnectionState.Connected
        if (showConnected) {
            delay(CONNECTED_CHIP_MS)
            showConnected = false
        }
    }

    val visible = connection != ChatConnectionState.Connected || showConnected
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        when (connection) {
            ChatConnectionState.Connecting -> Chip(
                label = t("Connecting…", "در حال اتصال…"),
                accent = HermesAmber,
                spinner = true,
            )
            ChatConnectionState.Reconnecting -> Chip(
                label = t("Reconnecting…", "اتصال دوباره…"),
                accent = HermesAmber,
                spinner = true,
            )
            ChatConnectionState.Connected -> Chip(
                label = t("Connected", "متصل شد"),
                accent = HermesTeal,
            )
            // Disconnected and Failed both mean "no agent, tap to retry"; the
            // bar says so and stays saying so until it is true no longer.
            ChatConnectionState.Disconnected,
            ChatConnectionState.Failed,
            -> Chip(
                label = t("Disconnected · retry", "قطع شد · تلاش دوباره"),
                accent = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Chip(label: String, accent: Color, spinner: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (spinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = accent,
                strokeWidth = 1.6.dp,
            )
        } else {
            Spacer(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
    }
}

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

/** Send/voice controls fade while the gateway is still coming up. */
internal fun Modifier.composerEnabled(connected: Boolean): Modifier =
    alpha(if (connected) 1f else 0.55f)

private const val CONNECTED_CHIP_MS = 1_200L

/** Success teal — the connected state's own colour, brighter than the theme's tertiary. */
internal val HermesTeal = Color(0xFF26C6B4)
