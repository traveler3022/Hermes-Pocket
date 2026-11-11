package com.hermes.android.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxStatus
import com.hermes.android.ui.design.HxTone
import com.hermes.android.ui.design.hxPressScale
import com.hermes.android.ui.icons.filled.Forum
import com.hermes.android.ui.icons.filled.Inventory2
import com.hermes.android.ui.icons.filled.PushPin
import com.hermes.android.ui.icons.outlined.Folder
import com.hermes.android.ui.icons.outlined.PermMedia
import com.hermes.android.ui.viewmodel.SessionItem
import kotlinx.coroutines.delay

/** A Workspace destination: one of the drawer's tiles. */
internal data class WorkspaceDestination(
    val label: String,
    val icon: ImageVector,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

/** Per-session attention state, as the host reads it from approvals and background turns. */
internal enum class SessionPulse { None, Running, Waiting, Failed }

/** Which chats the list shows: all of them, those with a turn running, or those that need the user. */
internal enum class DrawerFilter { All, Running, NeedsYou }

/** A chat needs the user when it waits on them, failed unseen, or has a reply they have not read. */
internal fun DrawerFilter.keeps(pulse: SessionPulse, unread: Int): Boolean = when (this) {
    DrawerFilter.All -> true
    DrawerFilter.Running -> pulse == SessionPulse.Running
    DrawerFilter.NeedsYou -> pulse == SessionPulse.Waiting || pulse == SessionPulse.Failed || unread > 0
}

/** What a row's disc shows, the most pressing first. */
internal enum class RowTone { Waiting, Running, Failed, Reply, Idle }

internal fun rowTone(pulse: SessionPulse, unread: Int): RowTone = when {
    pulse == SessionPulse.Waiting -> RowTone.Waiting
    pulse == SessionPulse.Running -> RowTone.Running
    pulse == SessionPulse.Failed -> RowTone.Failed
    unread > 0 -> RowTone.Reply
    else -> RowTone.Idle
}

/** How long ago, in the units a row counts in. */
internal enum class AgeUnit { Now, Minute, Hour, Day, Week, Month, Year }

/** [epochMillis] at [nowMillis] as a count and a unit; null without a time. */
internal fun ageOf(epochMillis: Long, nowMillis: Long): Pair<Long, AgeUnit>? {
    if (epochMillis <= 0) return null
    val m = ((nowMillis - epochMillis) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> 0L to AgeUnit.Now
        m < 60 -> m to AgeUnit.Minute
        m < 60 * 24 -> m / 60 to AgeUnit.Hour
        m < 60 * 24 * 7 -> m / (60 * 24) to AgeUnit.Day
        m < 60 * 24 * 30 -> m / (60 * 24 * 7) to AgeUnit.Week
        m < 60 * 24 * 365 -> m / (60 * 24 * 30) to AgeUnit.Month
        else -> m / (60 * 24 * 365) to AgeUnit.Year
    }
}

/** A row's compact age ("now", "5m", "3h"), in the app's language; empty without a time. */
@Composable
private fun shortAge(epochMillis: Long, nowMillis: Long): String {
    val (count, unit) = ageOf(epochMillis, nowMillis) ?: return ""
    val n = count.toInt()
    return when (unit) {
        AgeUnit.Now -> stringResource(R.string.age_now)
        AgeUnit.Minute -> stringResource(R.string.age_minutes, n)
        AgeUnit.Hour -> stringResource(R.string.age_hours, n)
        AgeUnit.Day -> stringResource(R.string.age_days, n)
        AgeUnit.Week -> stringResource(R.string.age_weeks, n)
        AgeUnit.Month -> stringResource(R.string.age_months, n)
        AgeUnit.Year -> stringResource(R.string.age_years, n)
    }
}

/**
 * The drawer as Workspace router.
 *
 * From the top: the profile title and search, the destinations as tiles two to
 * a row, the All / Running / Needs you filters, the chat list, and a footer with
 * a full-width New chat pill and Settings, the list fading out above it.
 *
 * A chat row is a status disc, the title over what the chat is doing (in words
 * as well as by the disc's colour and icon), and its age. A pinned chat with
 * nothing going on shows a pin in the disc's place. "Show all" expands the
 * unfiltered list in place; a filter lists every chat it keeps. Long-press
 * opens the host's per-session menu (rename / pin / delete).
 *
 * The tiles, filters, row and footer follow Kyllene's SessionsSidebar
 * (NavGrid, AttentionFilters, SessionRowContent, BottomBar), rewritten for
 * Material 3 here. Copyright (c) 2026 Giton22, MIT License.
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
    pinnedIds: Set<String> = emptySet(),
    onSessionLongClick: (SessionItem) -> Unit = {},
    visibleSessions: Int = 6,
) {
    // Content only: the host's ModalDrawerSheet owns width, shape and colours,
    // and the rename / delete dialogs that hang off it.
    var showAll by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(DrawerFilter.All) }
    val shown = when (filter) {
        DrawerFilter.All -> if (showAll) sessions else sessions.take(visibleSessions)
        else -> sessions.filter { filter.keeps(pulseOf(it), unreadOf(it)) }
    }
    // Ages move on while the drawer sits open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val ground = MaterialTheme.colorScheme.surface
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
                        .clip(RoundedCornerShape(HxRadius.sm))
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

            // ── Destinations, two to a row ───────────────────────────────
            if (destinations.isNotEmpty()) DrawerNavGrid(destinations)

            // ── Filters ──────────────────────────────────────────────────
            // Kept while a filter is on, so an emptied list (another profile)
            // can still be set back to All.
            if (sessions.isNotEmpty() || filter != DrawerFilter.All) {
                DrawerFilters(
                    selected = filter,
                    running = sessions.count { pulseOf(it) == SessionPulse.Running },
                    needsYou = sessions.count { DrawerFilter.NeedsYou.keeps(pulseOf(it), unreadOf(it)) },
                    onSelect = { filter = it },
                )
            }

            // ── Sessions, the list fading out above the footer ───────────
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                ) {
                    items(
                        items = shown,
                        key = { it.id },
                    ) { session ->
                        DrawerSessionRow(
                            title = session.title,
                            isPinned = session.id in pinnedIds,
                            pulse = pulseOf(session),
                            unread = unreadOf(session),
                            age = shortAge(session.updatedAt, now),
                            isActive = session.id == activeSessionId,
                            onClick = { onSessionClick(session) },
                            onLongClick = { onSessionLongClick(session) },
                        )
                    }
                    if (filter != DrawerFilter.All && shown.isEmpty()) {
                        item(key = "filter-empty") {
                            Text(
                                text = stringResource(
                                    if (filter == DrawerFilter.Running) R.string.drawer_none_running
                                    else R.string.drawer_none_waiting,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HxTone.textTertiary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            )
                        }
                    }
                    if (filter == DrawerFilter.All && !showAll && sessions.size > visibleSessions) {
                        item(key = "show-all") {
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
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground))),
                )
            }

            // ── Footer: full-width New chat + Settings ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ground)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NewChatPill(onClick = onNewChat, modifier = Modifier.weight(1f))
                // A gear, not a person: this button has always opened Settings,
                // and a generic avatar gave no hint of that.
                HxHeaderCircleButton(
                    icon = HxIcons.Settings,
                    contentDescription = stringResource(R.string.drawer_settings),
                    onClick = onSettings,
                    size = 46.dp,
                )
            }
        }
    }
}

/** The destinations as tiles, two to a row; a row whose label wraps keeps its neighbour the same height. */
@Composable
private fun DrawerNavGrid(destinations: List<WorkspaceDestination>) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        destinations.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { DrawerNavTile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A tile: an accent icon and its label on a raised fill, with a count when one is actionable. */
@Composable
private fun DrawerNavTile(destination: WorkspaceDestination, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HxRadius.md)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(HxTone.cardStrong, shape)
            .clickable(role = Role.Button, onClick = destination.onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            // Large text wraps rather than cutting the name off; the tile grows.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // A count only appears when it is actionable — "3 waiting", never "0".
        destination.badge?.takeIf { it > 0 }?.let { count ->
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** All / Running / Needs you, each with its count when there is one. */
@Composable
private fun DrawerFilters(
    selected: DrawerFilter,
    running: Int,
    needsYou: Int,
    onSelect: (DrawerFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DrawerFilter.entries.forEach { entry ->
            val (label, count, countColor) = when (entry) {
                DrawerFilter.All -> Triple(stringResource(R.string.all), null, HxTone.textTertiary)
                DrawerFilter.Running -> Triple(stringResource(R.string.running), running, HxStatus.running)
                DrawerFilter.NeedsYou -> Triple(stringResource(R.string.drawer_filter_needs_you), needsYou, HxStatus.waiting)
            }
            DrawerFilterChip(
                text = label,
                count = count?.takeIf { it > 0 },
                countColor = countColor,
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/** A 32dp pill inside a full-height touch target; the picked one is drawn in the text colour. */
@Composable
private fun DrawerFilterChip(
    text: String,
    count: Int?,
    countColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val content = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.onSurface, shape)
                    } else {
                        Modifier
                            .background(HxTone.cardStrong, shape)
                            .border(1.dp, HxTone.hairline, shape)
                    },
                )
                .indication(interaction, LocalIndication.current)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) content else countColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A chat row: a status disc, the title over what the chat is doing, and its
 * age. A pinned chat with nothing going on shows a pin instead of the disc.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    title: String,
    isPinned: Boolean,
    pulse: SessionPulse,
    unread: Int,
    age: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = rowTone(pulse, unread)
    val toneColor = when (tone) {
        RowTone.Waiting -> HxStatus.waiting
        RowTone.Running, RowTone.Reply -> HxStatus.running
        RowTone.Failed -> HxStatus.error
        RowTone.Idle -> HxTone.textMuted
    }
    // Said in words as well as by the disc: every state the chat is in, joined.
    val statusLine = listOfNotNull(
        stringResource(R.string.status_waiting).takeIf { pulse == SessionPulse.Waiting },
        stringResource(R.string.status_in_progress).takeIf { pulse == SessionPulse.Running },
        stringResource(R.string.status_failed).takeIf { pulse == SessionPulse.Failed },
        stringResource(R.string.status_new_reply).takeIf { unread > 0 },
    ).joinToString(" · ").ifEmpty { null }
    val pinnedLabel = stringResource(R.string.pinned)
    val shape = RoundedCornerShape(HxRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 48.dp)
            // The open chat sits on a raised fill rather than being told apart by
            // colour alone.
            .clip(shape)
            .then(if (isActive) Modifier.background(HxTone.cardStrong, shape) else Modifier)
            // Long-press is where rename / pin / delete live.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val pinOnly = isPinned && tone == RowTone.Idle
        if (pinOnly) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = pinnedLabel,
                    modifier = Modifier.size(14.dp),
                    tint = HxTone.textMuted,
                )
            }
        } else {
            StatusDisc(tone, toneColor)
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val titleStyle = MaterialTheme.typography.bodyLarge
                Text(
                    text = title,
                    style = titleStyle.copy(lineHeight = titleStyle.fontSize * 1.3f),
                    fontWeight = if (isActive || tone != RowTone.Idle) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPinned && !pinOnly) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = pinnedLabel,
                        modifier = Modifier.size(12.dp),
                        tint = HxTone.textMuted,
                    )
                }
            }
            if (statusLine != null) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = toneColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (age.isNotEmpty()) {
            Text(
                text = age,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = HxTone.textMuted,
                maxLines = 1,
                // Level with the title when a status line follows it.
                modifier = if (statusLine != null) Modifier.align(Alignment.Top).padding(top = 3.dp) else Modifier,
            )
        }
    }
}

/**
 * A 28dp disc tinted for [tone]: a spinner while a turn runs, otherwise an icon
 * by shape as well as colour, so "waiting" and "failed" differ for someone who
 * cannot tell amber from red. The row says the same in words, so the disc is
 * silent to screen readers.
 */
@Composable
private fun StatusDisc(tone: RowTone, color: Color) {
    val fill = when (tone) {
        RowTone.Waiting -> HxStatus.waiting.copy(alpha = 0.14f)
        RowTone.Running, RowTone.Reply -> HxStatus.running.copy(alpha = 0.14f)
        RowTone.Failed -> HxStatus.error.copy(alpha = 0.14f)
        RowTone.Idle -> HxTone.cardStrong
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (tone == RowTone.Running) {
            if (rememberReduceMotion()) {
                Icon(HxIcons.CircleDot, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = color,
                    strokeWidth = 1.5.dp,
                )
            }
        } else {
            Icon(
                imageVector = when (tone) {
                    RowTone.Waiting -> HxIcons.ShieldAlert
                    RowTone.Failed -> HxIcons.X
                    RowTone.Reply -> HxIcons.MessageCircle
                    else -> HxIcons.MessageSquare
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** The accent pill that starts a chat, glowing in its own colour. */
@Composable
private fun NewChatPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val fill = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .hxPressScale(interaction, pressedScale = 0.97f)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = fill.copy(alpha = 0.4f),
                spotColor = fill.copy(alpha = 0.4f),
            )
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        // The same chat bubble as the chat header's new-chat button.
        Icon(
            HxIcons.NewChat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.new_chat),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The drawer's destinations (frame `7b`), wired to callbacks the host screen owns.
 * Kept here so the drawer, the badge source and the labels stay in one place.
 */
@Composable
internal fun rememberWorkspaceDestinations(
    onScheduled: () -> Unit,
    onGroups: () -> Unit,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onKanban: (() -> Unit)? = null,
): List<WorkspaceDestination> {
    val scheduled = stringResource(R.string.scheduled_2)
    val groups = stringResource(R.string.group_chats)
    val media = stringResource(R.string.media)
    val files = stringResource(R.string.files)
    val kanban = stringResource(R.string.kanban)
    // Remembered, as the name says: a new list on every call made the drawer
    // recompose along with the chat behind it.
    return remember(
        scheduled, groups, media, files, kanban,
        onScheduled, onGroups, onMedia, onFiles, onKanban,
    ) {
        // The board is the work desk now, so it leads when it is switched on.
        listOfNotNull(
            onKanban?.let { WorkspaceDestination(kanban, Icons.Default.Inventory2, null, it) },
            WorkspaceDestination(scheduled, HxIcons.Clock, null, onScheduled),
            WorkspaceDestination(groups, Icons.Default.Forum, null, onGroups),
            WorkspaceDestination(media, Icons.Outlined.PermMedia, null, onMedia),
            WorkspaceDestination(files, Icons.Outlined.Folder, null, onFiles),
        )
    }
}
