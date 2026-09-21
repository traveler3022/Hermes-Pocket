package com.hermes.android.ui.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
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
 * Not yet wired into [ChatScreen], which still uses [HermesDrawerContent]:
 * this sheet drops in-drawer search, sort and pinning, and its destination
 * block needs routes the nav graph does not have yet. Landed as its own
 * component so the two can be compared on a device before the switch.
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
    onAccount: () -> Unit,
    pulseOf: (SessionItem) -> SessionPulse = { SessionPulse.None },
    onSessionLongClick: (SessionItem) -> Unit = {},
    onShowAll: (() -> Unit)? = null,
    visibleSessions: Int = 6,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.statusBarsPadding()) {

            // ── Title + search ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = t("Search chats", "جستجو در گفتگوها"),
                    )
                }
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
                    items = sessions.take(visibleSessions),
                    key = { it.id },
                ) { session ->
                    DrawerSessionRow(
                        title = session.title,
                        pulse = pulseOf(session),
                        isActive = session.id == activeSessionId,
                        onClick = { onSessionClick(session) },
                        onLongClick = { onSessionLongClick(session) },
                    )
                }
                if (onShowAll != null && sessions.size > visibleSessions) {
                    item {
                        Text(
                            text = t("Show all…", "مشاهده همه…"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onShowAll)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            // ── Footer: new chat + account ───────────────────────────────
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
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = t("Chat", "چت"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAccount) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = t("Account", "حساب"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
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
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press is where rename / pin / delete live now that the row
            // itself carries no buttons.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val dot = when (pulse) {
            SessionPulse.Running -> MaterialTheme.colorScheme.primary
            SessionPulse.Waiting -> HermesAmber
            SessionPulse.Failed -> MaterialTheme.colorScheme.error
            SessionPulse.None -> null
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
internal val HermesAmber = Color(0xFFF0B429)

/** Near-black, so a count on [HermesAmber] stays readable in either theme. */
private val BadgeTextOnAmber = Color(0xFF1A1205)

/**
 * The four destinations of frame `7b`, wired to callbacks the host screen owns.
 * Kept here so the drawer, the badge source and the labels stay in one place.
 */
@Composable
internal fun rememberWorkspaceDestinations(
    waitingCount: Int,
    onWorkbench: () -> Unit,
    onAgent: () -> Unit,
    onScheduled: () -> Unit,
    onServer: () -> Unit,
): List<WorkspaceDestination> = listOf(
    WorkspaceDestination(
        label = t("Workbench", "میز کار"),
        icon = Icons.Default.SpaceDashboard,
        badge = waitingCount,
        onClick = onWorkbench,
    ),
    WorkspaceDestination(t("Agent", "عامل"), Icons.Default.Tune, null, onAgent),
    WorkspaceDestination(t("Scheduled", "زمان‌بندی‌شده"), Icons.Default.Schedule, null, onScheduled),
    WorkspaceDestination(t("Server", "سرور"), Icons.Default.Dns, null, onServer),
)
