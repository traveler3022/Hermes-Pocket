package com.hermes.android.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.outlined.Folder
import com.hermes.android.ui.icons.outlined.PermMedia
import com.hermes.android.ui.icons.filled.Forum
import com.hermes.android.ui.icons.filled.Inventory2
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxStatus
import com.hermes.android.ui.viewmodel.SessionItem

/** A Workspace destination in the drawer's upper block. */
internal data class WorkspaceDestination(
    val label: String,
    val icon: ImageVector,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

/** Per-session attention state — the only metadata the row still carries. */
internal enum class SessionPulse { None, Running, Waiting, Failed }

/**
 * The drawer as Workspace router (frame `7b`).
 *
 * "Show all" expands the list in place — the drawer keeps scrolling rather than
 * handing off to another screen. The search icon opens the Sessions screen.
 * The host orders the list: pinned first, then by the drawer's sort setting.
 *
 * The shape is the ChatGPT/Claude split: a title row, a search affordance, a
 * small block of destinations, a divider, then plain session names — one line
 * each, no counts, no timestamps, no previews. Density roughly doubles, and
 * the name is what people actually scan for.
 *
 * The one exception is agentic state, which chat cannot show: a session that
 * is running, waiting on approval or failed gets a coloured dot, and the
 * destination block carries a badge. That is the whole reason the drawer is a
 * router and not a history list — it answers "what happened while I was away"
 * without opening anything.
 *
 * Long-press still opens the host's per-session menu (rename / pin / delete),
 * so nothing the current drawer can do is lost by switching.
 */
@Composable
internal fun WorkspaceDrawerSheet(
    sessions: List<SessionItem>,
    activeSessionId: String?,
    destinations: List<WorkspaceDestination>,
    onSearch: () -> Unit,
    onNewChat: () -> Unit,
    onSessionClick: (SessionItem) -> Unit,
    onSettings: () -> Unit,
    profileTitle: String? = null,
    onProfileClick: () -> Unit = {},
    pulseOf: (SessionItem) -> SessionPulse = { SessionPulse.None },
    unreadOf: (SessionItem) -> Int = { 0 },
    onSessionLongClick: (SessionItem) -> Unit = {},
    visibleSessions: Int = 6,
) {
    // Content only: the host's ModalDrawerSheet owns width, shape and colours,
    // and the rename / delete dialogs that hang off it.
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) sessions else sessions.take(visibleSessions)
    run {
        Column(Modifier.statusBarsPadding()) {

            // ── Title + search ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The title is the profile switcher: which agent these chats belong to.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onProfileClick)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = profileTitle ?: stringResource(R.string.hermes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.switch_profile),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HxHeaderCircleButton(
                    icon = HxIcons.Search,
                    contentDescription = stringResource(R.string.search_chats),
                    onClick = onSearch,
                )
            }

            // ── Destinations ─────────────────────────────────────────────
            destinations.forEach { destination ->
                DrawerDestinationRow(destination)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── Sessions: names only ─────────────────────────────────────
            LazyColumn(Modifier.weight(1f)) {
                items(
                    items = shown,
                    key = { it.id },
                ) { session ->
                    DrawerSessionRow(
                        title = session.title,
                        pulse = pulseOf(session),
                        unread = unreadOf(session),
                        isActive = session.id == activeSessionId,
                        onClick = { onSessionClick(session) },
                        onLongClick = { onSessionLongClick(session) },
                    )
                }
                if (!showAll && sessions.size > visibleSessions) {
                    item {
                        Text(
                            text = stringResource(R.string.show_all),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAll = true }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            // ── Footer: new chat + settings ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The same chat bubble as the chat header's new-chat button.
                    Icon(
                        HxIcons.NewChat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.chat),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.weight(1f))
                // A gear, not a person: this button has always opened Settings,
                // and a generic avatar gave no hint of that.
                HxHeaderCircleButton(
                    icon = HxIcons.Settings,
                    contentDescription = stringResource(R.string.drawer_settings),
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun DrawerDestinationRow(destination: WorkspaceDestination) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = destination.onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.bodyLarge,
        )
        // A count only appears when it is actionable — "3 waiting", never "0".
        destination.badge?.takeIf { it > 0 }?.let { count ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(HermesAmber)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BadgeTextOnAmber,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    title: String,
    pulse: SessionPulse,
    unread: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The open chat sits on a soft pill rather than being told apart by
            // colour alone — blue text read as a link, and colour is no cue at
            // all for someone who cannot tell it apart.
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isActive) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                else Modifier,
            )
            // Long-press is where rename / pin / delete live now that the row
            // itself carries no buttons.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // One colour per meaning (HxStatus). Running used to be the theme's
        // primary, which in the default Hermes palette IS this amber — so a
        // chat that was working and one waiting on you showed the same dot.
        val dot = when (pulse) {
            SessionPulse.Running -> HxStatus.running
            SessionPulse.Waiting -> HxStatus.waiting
            SessionPulse.Failed -> HxStatus.error
            SessionPulse.None -> null
        }
        if (unread > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (unread > 99) "99+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (dot != null) {
            Spacer(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
        }
    }
}

/** Warning amber — the approval card's colour, for anything waiting on the user. */
internal val HermesAmber = HxStatus.waiting

/** Near-black, so a count on [HermesAmber] stays readable in either theme. */
private val BadgeTextOnAmber = Color(0xFF1A1205)

/**
 * The drawer's destinations (frame `7b`), wired to callbacks the host screen owns.
 * Kept here so the drawer, the badge source and the labels stay in one place.
 */
@Composable
internal fun rememberWorkspaceDestinations(
    onAgent: () -> Unit,
    onScheduled: () -> Unit,
    onGroups: () -> Unit,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onKanban: (() -> Unit)? = null,
): List<WorkspaceDestination> {
    val agent = stringResource(R.string.agent)
    val scheduled = stringResource(R.string.scheduled_2)
    val groups = stringResource(R.string.group_chats)
    val media = stringResource(R.string.media)
    val files = stringResource(R.string.files)
    val kanban = stringResource(R.string.kanban)
    // Remembered, as the name says: a new list on every call made the drawer
    // recompose along with the chat behind it.
    return remember(
        agent, scheduled, groups, media, files, kanban,
        onAgent, onScheduled, onGroups, onMedia, onFiles, onKanban,
    ) {
        // The board is the work desk now, so it leads when it is switched on.
        listOfNotNull(
            onKanban?.let { WorkspaceDestination(kanban, Icons.Default.Inventory2, null, it) },
            WorkspaceDestination(agent, HxIcons.Bot, null, onAgent),
            WorkspaceDestination(scheduled, HxIcons.Clock, null, onScheduled),
            WorkspaceDestination(groups, Icons.Default.Forum, null, onGroups),
            WorkspaceDestination(media, Icons.Outlined.PermMedia, null, onMedia),
            WorkspaceDestination(files, Icons.Outlined.Folder, null, onFiles),
        )
    }
}
