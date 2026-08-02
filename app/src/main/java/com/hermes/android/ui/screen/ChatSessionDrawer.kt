package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hermes.android.ui.design.HxGradientActionPill
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.TodoItemUi
import com.hermes.android.ui.viewmodel.TodoStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionDrawerRow(
    session: SessionItem,
    isActive: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val relativeTime = formatRelativeTime(session.updatedAt)
    val messageCountText = session.messageCount?.let { count ->
        t("$count messages", "$count پیام")
    }
    val subtitle = buildString {
        if (isPinned) append("📌 ")
        if (messageCountText != null) {
            append(messageCountText)
            append(" · ")
        }
        append(relativeTime)
    }

    var showMenu by remember { mutableStateOf(false) }

    Box {
        // Aether-style row: a fully rounded surface that tints when active,
        // no hard border. The active row carries a soft primary fill plus a
        // leading accent bar so it still reads clearly inside every theme.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else Color.Transparent,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(start = 14.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .width(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = it.take(80),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Aether-style long-press action menu: a floating rounded card with a
        // soft shadow, instead of Material's dropdown.
        SharedDrawerActionMenu(
            expanded = showMenu,
            isPinned = isPinned,
            onDismiss = { showMenu = false },
            onPin = {
                showMenu = false
                onPin()
            },
            onRename = {
                showMenu = false
                onRename()
            },
            onDelete = {
                showMenu = false
                onDelete()
            },
        )
    }
}

/**
 * Floating action menu for a long-pressed drawer row — the Aether idea of a
 * soft, rounded popup card with scale+fade entrance.
 */
@Composable
private fun SharedDrawerActionMenu(
    expanded: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 42),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 176.dp, max = 220.dp)
                    .hxSoftShadow(radius = 16.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SharedDrawerActionRow(
                    icon = Icons.Default.PushPin,
                    label = if (isPinned) t("Unpin", "برداشتن سنجاق") else t("Pin", "سنجاق کردن"),
                    onClick = onPin,
                )
                SharedDrawerActionRow(
                    icon = Icons.Default.Edit,
                    label = t("Rename", "تغییر نام"),
                    onClick = onRename,
                )
                SharedDrawerActionRow(
                    icon = Icons.Default.Delete,
                    label = t("Delete", "حذف"),
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SharedDrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

// ── Aether-style drawer shell ──────────────────────────────────────────────

private val HxDrawerOverlayFadeHeight = 18.dp

/**
 * The Aether-inspired session drawer: rounded sheet, a gradient-faded header
 * that floats over the scrolling list, circular floating actions for search
 * and settings, and a gradient "New chat" pill at the bottom.
 *
 * Built against our design tokens and ViewModel state so all six themes keep
 * working. The rename/delete confirm dialogs stay in ChatScreen (driven by
 * the same ViewModel state as before).
 */
@Composable
internal fun HermesDrawerContent(
    assistantName: String,
    sessions: List<SessionItem>,
    activeSessionId: String?,
    drawerSearchQuery: String,
    drawerSortNewest: Boolean,
    drawerPinnedIds: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onSessionClick: (String) -> Unit,
    onRenameAssistant: () -> Unit,
    onTasks: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val overlayHeight = with(density) {
        if (overlayHeightPx > 0) overlayHeightPx.toDp() else 132.dp
    }
    val dismissSearch = {
        searchExpanded = false
        onSearchQueryChange("")
    }

    val filteredSessions = remember(sessions, drawerSearchQuery, drawerSortNewest, drawerPinnedIds) {
        var list = sessions
        val q = drawerSearchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter { it.title.lowercase().contains(q) }
        }
        list = if (drawerSortNewest) {
            list.sortedByDescending { it.updatedAt }
        } else {
            list.sortedBy { it.updatedAt }
        }
        val pinned = list.filter { it.id in drawerPinnedIds }
        val unpinned = list.filter { it.id !in drawerPinnedIds }
        pinned + unpinned
    }

    Box(modifier = Modifier.fillMaxSize().padding(bottom = 18.dp)) {
        if (filteredSessions.isEmpty()) {
            Text(
                text = if (drawerSearchQuery.isNotEmpty()) {
                    t("No results", "نتیجه‌ای یافت نشد")
                } else {
                    t("No saved sessions yet", "هنوز گفتگویی ذخیره نشده")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = overlayHeight - HxDrawerOverlayFadeHeight + 12.dp,
                ),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = overlayHeight - HxDrawerOverlayFadeHeight,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "drawer-task-desk") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onTasks)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = t("Task Desk", "میز کار"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                items(filteredSessions, key = { it.id }) { session ->
                    SessionDrawerRow(
                        session = session,
                        isActive = session.id == activeSessionId,
                        isPinned = session.id in drawerPinnedIds,
                        onClick = { onSessionClick(session.id) },
                        onLongClick = { onRenameSession(session.id, session.title) },
                        onPin = { onTogglePin(session.id) },
                        onRename = { onRenameSession(session.id, session.title) },
                        onDelete = { onDeleteSession(session.id) },
                    )
                }
            }
        }

        // Floating header: title + circular actions, fading into the list via
        // a gradient so sessions scroll underneath instead of hard-clipping.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(drawerOverlayBodyGradient(MaterialTheme.colorScheme.surface))
                .onSizeChanged { overlayHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = assistantName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onRenameAssistant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HxHeaderCircleButton(
                            icon = if (searchExpanded || drawerSearchQuery.isNotEmpty()) {
                                Icons.Default.Close
                            } else {
                                Icons.Default.Search
                            },
                            contentDescription = t("Search chats", "جستجو در گفتگوها"),
                            onClick = {
                                if (searchExpanded || drawerSearchQuery.isNotEmpty()) dismissSearch()
                                else searchExpanded = true
                            },
                            size = 46.dp,
                        )
                        HxHeaderCircleButton(
                            icon = Icons.Default.Settings,
                            contentDescription = t("Settings", "تنظیمات"),
                            onClick = {
                                dismissSearch()
                                onSettings()
                            },
                            size = 46.dp,
                        )
                    }
                }
                AnimatedVisibility(visible = searchExpanded || drawerSearchQuery.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        DrawerSearchField(
                            value = drawerSearchQuery,
                            sortNewest = drawerSortNewest,
                            onValueChange = onSearchQueryChange,
                            onToggleSort = onToggleSort,
                        )
                    }
                }
                Spacer(Modifier.height(if (searchExpanded || drawerSearchQuery.isNotEmpty()) 10.dp else 12.dp))
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HxDrawerOverlayFadeHeight)
                    .background(drawerOverlayTailGradient(MaterialTheme.colorScheme.surface)),
            )
        }

        // Floating gradient "New chat" pill.
        HxGradientActionPill(
            label = t("New chat", "گفتگوی جدید"),
            icon = Icons.Default.Edit,
            onClick = {
                dismissSearch()
                onNewChat()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 18.dp),
        )
    }
}

/** Rounded, shadowed search pill with an inline sort toggle. */
@Composable
private fun DrawerSearchField(
    value: String,
    sortNewest: Boolean,
    onValueChange: (String) -> Unit,
    onToggleSort: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hxSoftShadow(radius = 12.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) {
                Text(
                    text = t("Search chats…", "جستجو در گفتگوها…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = onToggleSort) {
            Icon(
                Icons.Default.Sort,
                contentDescription = if (sortNewest) {
                    t("Newest first", "جدیدترین اول")
                } else {
                    t("Oldest first", "قدیمی‌ترین اول")
                },
                tint = if (!sortNewest) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun drawerOverlayBodyGradient(baseColor: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to baseColor.copy(alpha = 0.96f),
        0.25f to baseColor.copy(alpha = 0.85f),
        0.55f to baseColor.copy(alpha = 0.45f),
        0.82f to baseColor.copy(alpha = 0.15f),
        1.0f to Color.Transparent,
    ),
)

private fun drawerOverlayTailGradient(baseColor: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to baseColor.copy(alpha = 0.15f),
        0.5f to baseColor.copy(alpha = 0.05f),
        1.0f to Color.Transparent,
    ),
)


@Composable
internal fun AgentTodoCard(todos: List<TodoItemUi>) {
    var expanded by remember { mutableStateOf(false) }
    val done = todos.count { it.status == TodoStatus.COMPLETED }
    val current = todos.firstOrNull { it.status == TodoStatus.IN_PROGRESS }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Tasks", "کارها") + " $done/${todos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!expanded && current != null) {
                    Text(
                        text = current.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                todos.forEach { todo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = when (todo.status) {
                                TodoStatus.COMPLETED -> "✓"
                                TodoStatus.IN_PROGRESS -> "▸"
                                TodoStatus.CANCELLED -> "✕"
                                TodoStatus.PENDING -> "○"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (todo.status) {
                                TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                                TodoStatus.CANCELLED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = todo.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (todo.status == TodoStatus.COMPLETED ||
                                todo.status == TodoStatus.CANCELLED
                            ) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (todo.status == TodoStatus.COMPLETED) {
                                TextDecoration.LineThrough
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

// ── Feature #7: Connection retry banner ──────────────────────────────────

