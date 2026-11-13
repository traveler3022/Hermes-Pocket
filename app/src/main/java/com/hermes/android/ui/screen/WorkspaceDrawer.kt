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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
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
import com.hermes.android.domain.projects.Projects
import com.hermes.android.domain.topics.ChatTopic
import com.hermes.android.domain.drawer.DrawerPrefs
import kotlinx.coroutines.delay

/** A Workspace destination: one of the drawer's tiles. */
internal data class WorkspaceDestination(
    val label: String,
    val icon: ImageVector,
    val badge: Int? = null,
    val onClick: () -> Unit,
    /** A stable name for Settings › Drawer to hide the tile by. */
    val id: String = label,
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
private fun shortAge(epochMillis: Long, nowMillis: Long): String =
    ageOf(epochMillis, nowMillis)?.let { shortAgeText(it) }.orEmpty()

/** [age] in the row's compact words ("now", "5m", "3h"). */
@Composable
internal fun shortAgeText(age: Pair<Long, AgeUnit>): String {
    val (count, unit) = age
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
    /** The profile's topics, in their order; their chats leave the other sections. */
    topics: List<ChatTopic> = emptyList(),
    onTopicClick: (ChatTopic) -> Unit = {},
    onTopicLongClick: (ChatTopic) -> Unit = {},
    /** The gateway's projects, as chips that narrow the list to one. */
    projects: List<Projects.ProjectSummary> = emptyList(),
    /** The Topics section shows drag handles; [onReordered] gets the order once the user is done. */
    reorderingTopics: Boolean = false,
    onReordered: (List<String>) -> Unit = {},
    /** What the user chose to show, in Settings › Appearance › Drawer. */
    layout: DrawerPrefs = DrawerPrefs(),
) {
    // Content only: the host's ModalDrawerSheet owns width, shape and colours,
    // and the rename / delete dialogs that hang off it.
    var showAll by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(DrawerFilter.All) }
    // A filter whose chip is turned off in Settings no longer narrows the list.
    val filter = when {
        !layout.filters -> DrawerFilter.All
        picked == DrawerFilter.Running && !layout.runningFilter -> DrawerFilter.All
        picked == DrawerFilter.NeedsYou && !layout.needsYouFilter -> DrawerFilter.All
        else -> picked
    }
    var pinnedCollapsed by rememberSaveable { mutableStateOf(false) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Projects: chips for those that hold a chat the list has ──────────
    fun Projects.ProjectSummary.holds(s: SessionItem) = s.id in sessionIds || s.lineageRootId?.let { it in sessionIds } == true
    val projectChips = projects
        .map { it to sessions.count { s -> it.holds(s) } }
        .filter { (_, count) -> count > 0 }
        .sortedWith(compareBy<Pair<Projects.ProjectSummary, Int>> { it.first.isNoProject }.thenByDescending { it.second })
    // Only worth a row when a chat sits in a real project.
    val showProjects = layout.projects && projectChips.any { !it.first.isNoProject }
    val project = projectChips.firstOrNull { it.first.id == projectId }?.first?.takeIf { showProjects }
    val projectOf: (SessionItem) -> String? = { s ->
        projects.firstOrNull { !it.isNoProject && it.holds(s) }?.label
    }
    val scoped = if (project == null) sessions else sessions.filter { project.holds(it) }

    // ── Topics, then Pinned, then the rest; each chat once ───────────────
    val topicRows = (if (layout.topics) topics else emptyList()).map { topic ->
        topic to (sessions.firstOrNull { it.id == topic.sessionId } ?: sessions.firstOrNull { it.lineageRootId == topic.sessionId })
    }.filter { (_, row) -> project == null || (row != null && project.holds(row)) }
    val topicChatIds = topicRows.mapTo(HashSet()) { it.first.sessionId } + topicRows.mapNotNull { it.second?.id }
    val topicChat: (ChatTopic, SessionItem?) -> SessionItem = { topic, row ->
        row ?: SessionItem(topic.sessionId, topic.name, null, 0L)
    }
    fun passes(s: SessionItem) = filter.keeps(pulseOf(s), unreadOf(s))
    fun busy(s: SessionItem) = rowTone(pulseOf(s), unreadOf(s)) != RowTone.Idle
    // Every section follows the status filter; the topic rows as the section shows them,
    // so moving one goes past its neighbour here.
    val filteredTopics = topicRows.filter { (topic, row) -> passes(topicChat(topic, row)) }
    val pinnedApart = if (layout.pinned) pinnedIds else emptySet()
    val pinned = scoped.filter { it.id in pinnedApart && it.id !in topicChatIds && passes(it) }
    val rest = scoped.filter { it.id !in pinnedApart && it.id !in topicChatIds && passes(it) }
    // Folded, Pinned still lists the open chat and those with something going on.
    val shownPinned = if (!pinnedCollapsed) pinned else pinned.filter { it.id == activeSessionId || busy(it) }
    val shown = if (filter != DrawerFilter.All || showAll) rest else rest.take(visibleSessions)
    val noneMatch = filter != DrawerFilter.All && filteredTopics.isEmpty() && pinned.isEmpty() && rest.isEmpty()
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

            // ── Sessions, the list fading out above the footer ───────────
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                ) {
                    // The destinations scroll with the chats, as the list's header.
                    val pages = if (layout.pages) destinations.filter { it.id !in layout.hiddenPages } else emptyList()
                    if (pages.isNotEmpty()) item(key = "nav") { DrawerNavGrid(pages) }

                    // ── Pinned ───────────────────────────────────────────
                    if (pinned.isNotEmpty()) {
                        item(key = "label-pinned") {
                            SectionToggle(
                                title = stringResource(R.string.pinned),
                                count = pinned.size,
                                collapsed = pinnedCollapsed,
                                onToggle = { pinnedCollapsed = !pinnedCollapsed },
                            )
                        }
                        items(items = shownPinned, key = { "pinned-${it.id}" }) { session ->
                            DrawerSessionRow(
                                title = session.title,
                                mark = RowMark.Pin,
                                pulse = pulseOf(session),
                                unread = unreadOf(session),
                                age = shortAge(session.updatedAt, now),
                                isActive = session.id == activeSessionId,
                                // Pinned chats sit apart from their project's others, so their row names it.
                                projectLabel = if (project == null) projectOf(session) else null,
                                onClick = { onSessionClick(session) },
                                onLongClick = { onSessionLongClick(session) },
                            )
                        }
                    }

                    // ── The filter chips head the rest, with no label of their own ──
                    // ── Topics: one line of chips, as the projects are ─────
                    if (filteredTopics.isNotEmpty()) {
                        item(key = "topics") {
                            if (reorderingTopics && filter == DrawerFilter.All) {
                                TopicReorder(
                                    rows = filteredTopics,
                                    projectLabel = { row -> if (project == null) row?.let(projectOf) else null },
                                    onDone = onReordered,
                                )
                            } else {
                                DrawerTopicChips(
                                    rows = filteredTopics,
                                    isActive = { topic, row ->
                                        topic.sessionId == activeSessionId || (row != null && row.id == activeSessionId)
                                    },
                                    toneOf = { topic, row ->
                                        val chat = topicChat(topic, row)
                                        rowTone(pulseOf(chat), unreadOf(chat))
                                    },
                                    onClick = onTopicClick,
                                    onLongClick = onTopicLongClick,
                                )
                            }
                        }
                    }
                    if (showProjects) {
                        item(key = "projects") {
                            DrawerProjectChips(
                                projects = projectChips,
                                selected = project?.id,
                                onSelect = { projectId = it },
                            )
                        }
                    }
                    // Kept while a filter is on, so an emptied list (another profile)
                    // can still be set back to All.
                    if (layout.filters && (sessions.isNotEmpty() || filter != DrawerFilter.All)) {
                        item(key = "filters") {
                            DrawerFilters(
                                selected = filter,
                                running = scoped.count { pulseOf(it) == SessionPulse.Running },
                                needsYou = scoped.count { DrawerFilter.NeedsYou.keeps(pulseOf(it), unreadOf(it)) },
                                onSelect = { picked = it },
                                showRunning = layout.runningFilter,
                                showNeedsYou = layout.needsYouFilter,
                            )
                        }
                    }
                    if (noneMatch) {
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
                    items(
                        items = shown,
                        key = { it.id },
                    ) { session ->
                        DrawerSessionRow(
                            title = session.title,
                            mark = if (session.id in pinnedIds) RowMark.Pin else RowMark.None,
                            pulse = pulseOf(session),
                            unread = unreadOf(session),
                            age = shortAge(session.updatedAt, now),
                            isActive = session.id == activeSessionId,
                            onClick = { onSessionClick(session) },
                            onLongClick = { onSessionLongClick(session) },
                        )
                    }
                    if (filter == DrawerFilter.All && !showAll && rest.size > visibleSessions) {
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
    showRunning: Boolean = true,
    showNeedsYou: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DrawerFilter.entries.filter {
            when (it) {
                DrawerFilter.All -> true
                DrawerFilter.Running -> showRunning
                DrawerFilter.NeedsYou -> showNeedsYou
            }
        }.forEach { entry ->
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
    mark: RowMark,
    pulse: SessionPulse,
    unread: Int,
    age: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** The chat's project, named after the title where the row sits apart from the project's others. */
    projectLabel: String? = null,
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
    val markLabel = when (mark) {
        RowMark.Pin -> stringResource(R.string.pinned)
        RowMark.Topic -> stringResource(R.string.topic_label)
        RowMark.None -> null
    }
    val markIcon = if (mark == RowMark.Topic) HxIcons.Hash else Icons.Default.PushPin
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
        // A marked chat with nothing going on shows its mark in the disc's place.
        val markOnly = mark != RowMark.None && tone == RowTone.Idle
        if (markOnly) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    markIcon,
                    contentDescription = markLabel,
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
                // Measured before the weighted title, and held narrow, so a long project name is cut first.
                projectLabel?.let { label ->
                    Text(
                        text = "· $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = HxTone.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 112.dp),
                    )
                }
                if (mark != RowMark.None && !markOnly) {
                    Icon(
                        markIcon,
                        contentDescription = markLabel,
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
 * The Topics section while it is being put in order: each row has a handle to drag it up or
 * down, the rows making way as it passes them, and Done on the label saves the order.
 * Follows Kyllene's ThreadReorder (SessionsSidebar.kt): Copyright (c) 2026 Giton22, MIT License.
 */
@Composable
private fun TopicReorder(
    rows: List<Pair<ChatTopic, SessionItem?>>,
    projectLabel: (SessionItem?) -> String?,
    onDone: (List<String>) -> Unit,
) {
    val order = remember { mutableStateListOf<String>().apply { addAll(rows.map { it.first.id }) } }
    // A topic added or removed meanwhile (on another device) joins or leaves the order where it stands.
    LaunchedEffect(rows) {
        val ids = rows.map { it.first.id }
        order.retainAll(ids)
        ids.filterNot { it in order }.forEach { order.add(it) }
    }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val byId = rows.associateBy { it.first.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 6.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.topics_drag_to_reorder).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.em),
                fontWeight = FontWeight.SemiBold,
                color = HxTone.textTertiary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onDone(order.toList()) }) { Text(stringResource(R.string.topic_reorder_done)) }
        }
        order.forEach { id ->
            val row = byId[id] ?: return@forEach
            // Keyed, so a row that swaps places keeps its drag going rather than starting over.
            key(id) {
                val lifted = dragging == id
                val dragLabel = stringResource(R.string.topic_drag, row.first.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else 0f }
                        .clip(RoundedCornerShape(HxRadius.md))
                        .then(if (lifted) Modifier.background(HxTone.cardStrong) else Modifier)
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(HxIcons.Hash, contentDescription = null, tint = HxTone.textMuted, modifier = Modifier.size(14.dp))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val titleStyle = MaterialTheme.typography.bodyLarge
                        Text(
                            text = row.first.name,
                            style = titleStyle.copy(lineHeight = titleStyle.fontSize * 1.3f),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        projectLabel(row.second)?.let { label ->
                            Text(
                                text = "· $label",
                                style = MaterialTheme.typography.labelMedium,
                                color = HxTone.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 112.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = id
                                        offset = 0f
                                    },
                                    onDragEnd = { dragging = null; offset = 0f },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val at = order.indexOf(id)
                                    // Past half a row, it takes the neighbour's place and the offset starts from there.
                                    if (offset > rowHeight / 2 && at < order.lastIndex) {
                                        order.add(at + 1, order.removeAt(at))
                                        offset -= rowHeight
                                    } else if (offset < -rowHeight / 2 && at > 0) {
                                        order.add(at - 1, order.removeAt(at))
                                        offset += rowHeight
                                    }
                                }
                            }
                            .semantics { contentDescription = dragLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(HxIcons.GripVertical, contentDescription = null, tint = HxTone.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** What marks a row besides its state: a pinned chat, or a topic's chat. */
internal enum class RowMark { None, Pin, Topic }

/**
 * A section's "TITLE · n" label as a toggle: the arrow points down while the section is
 * open and toward the reading direction while it is folded.
 */
@Composable
private fun SectionToggle(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (collapsed) R.string.section_collapsed else R.string.section_expanded)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(HxRadius.sm))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = state
            }
            .padding(start = 10.dp, end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${title.uppercase()} · $count",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.08.em),
            fontWeight = FontWeight.SemiBold,
            color = HxTone.textTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = HxTone.textTertiary,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (!collapsed) 0f else if (rtl) 90f else -90f),
        )
    }
}

/**
 * The topics as one line of chips that scrolls sideways, as the project chips do. A chip
 * names its topic after a mark of its state: a spinner while a turn runs, a dot in the
 * state's colour when it waits, failed or has a new reply, else the topic's #. The open
 * topic's chip is drawn in the text colour. Tap opens the topic's chat; a long press, its sheet.
 */
@Composable
private fun DrawerTopicChips(
    rows: List<Pair<ChatTopic, SessionItem?>>,
    isActive: (ChatTopic, SessionItem?) -> Boolean,
    toneOf: (ChatTopic, SessionItem?) -> RowTone,
    onClick: (ChatTopic) -> Unit,
    onLongClick: (ChatTopic) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { (topic, row) ->
            DrawerTopicChip(
                name = topic.name,
                tone = toneOf(topic, row),
                selected = isActive(topic, row),
                onClick = { onClick(topic) },
                onLongClick = { onLongClick(topic) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerTopicChip(
    name: String,
    tone: RowTone,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = CircleShape
    val content = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    val toneColor = when (tone) {
        RowTone.Waiting -> HxStatus.waiting
        RowTone.Running, RowTone.Reply -> HxStatus.running
        RowTone.Failed -> HxStatus.error
        RowTone.Idle -> HxTone.textMuted
    }
    // Said in words for a screen reader, as the row's status line says it.
    val state = when (tone) {
        RowTone.Waiting -> stringResource(R.string.status_waiting)
        RowTone.Running -> stringResource(R.string.status_in_progress)
        RowTone.Failed -> stringResource(R.string.status_failed)
        RowTone.Reply -> stringResource(R.string.status_new_reply)
        RowTone.Idle -> null
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                this.selected = selected
                state?.let { stateDescription = it }
            },
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
            when (tone) {
                RowTone.Idle -> Icon(
                    HxIcons.Hash,
                    contentDescription = null,
                    tint = if (selected) content else toneColor,
                    modifier = Modifier.size(13.dp),
                )
                RowTone.Running -> if (rememberReduceMotion()) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(toneColor))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp), color = toneColor, strokeWidth = 1.5.dp)
                }
                else -> Box(Modifier.size(7.dp).clip(CircleShape).background(toneColor))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

/** "All projects" and each project that holds a listed chat, with how many it holds. */
@Composable
private fun DrawerProjectChips(
    projects: List<Pair<Projects.ProjectSummary, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DrawerFilterChip(
            text = stringResource(R.string.all_projects),
            count = null,
            countColor = HxTone.textTertiary,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        projects.forEach { (project, count) ->
            DrawerFilterChip(
                text = project.label,
                count = count,
                countColor = HxTone.textTertiary,
                selected = project.id == selected,
                onClick = { onSelect(project.id) },
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
            onKanban?.let { WorkspaceDestination(kanban, Icons.Default.Inventory2, null, it, id = PAGE_KANBAN) },
            WorkspaceDestination(scheduled, HxIcons.Clock, null, onScheduled, id = PAGE_SCHEDULED),
            WorkspaceDestination(groups, Icons.Default.Forum, null, onGroups, id = PAGE_GROUPS),
            WorkspaceDestination(media, Icons.Outlined.PermMedia, null, onMedia, id = PAGE_MEDIA),
            WorkspaceDestination(files, Icons.Outlined.Folder, null, onFiles, id = PAGE_FILES),
        )
    }
}

/** The tiles' ids, as Settings › Drawer stores which are hidden. */
internal const val PAGE_KANBAN = "kanban"
internal const val PAGE_SCHEDULED = "scheduled"
internal const val PAGE_GROUPS = "groups"
internal const val PAGE_MEDIA = "media"
internal const val PAGE_FILES = "files"
