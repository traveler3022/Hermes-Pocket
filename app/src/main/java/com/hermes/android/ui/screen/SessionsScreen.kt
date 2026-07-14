package com.hermes.android.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.HistoryMessage
import com.hermes.android.ui.viewmodel.SessionSortOrder
import com.hermes.android.ui.viewmodel.SessionSummary
import com.hermes.android.ui.viewmodel.SessionsEffect
import com.hermes.android.ui.viewmodel.SessionsUiState
import com.hermes.android.ui.viewmodel.SessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sessions screen, rebuilt on the design system (ui/design/DesignSystem.kt):
 * list view with search, pinning, usage insights, and a per-session history
 * detail. All state/actions come from [SessionsViewModel] — this file is
 * presentation only.
 */
@Composable
fun SessionsScreen(
    onNavigateBack: () -> Unit = {},
    onResumeSession: (String) -> Unit = {},
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionsEffect.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, effect.text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, effect.title)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(t("Delete session", "حذف گفتگو")) },
            text = { Text(t("Are you sure you want to delete this session?", "آیا از حذف این گفتگو مطمئنید؟")) },
            confirmButton = {
                TextButton(onClick = { viewModel.executeDelete() }) {
                    Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(t("Cancel", "انصراف"))
                }
            },
        )
    }

    uiState.showRenameDialog?.let { renameState ->
        var newTitle by remember(renameState) { mutableStateOf(renameState.currentTitle) }
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text(t("Rename session", "تغییر نام گفتگو")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(t("Title", "عنوان")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!renameState.preview.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.suggestTitle(renameState.preview) { suggestion ->
                                    newTitle = suggestion
                                }
                            },
                        ) {
                            Text(t("Suggest title", "پیشنهاد عنوان"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.renameSession(renameState.sessionId, newTitle) },
                    enabled = newTitle.isNotBlank(),
                ) {
                    Text(t("Save", "ذخیره"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRenameDialog() }) {
                    Text(t("Cancel", "انصراف"))
                }
            },
        )
    }

    val inHistoryDetail = uiState.selectedSessionId != null

    HermesScaffold(
        title = if (inHistoryDetail) {
            uiState.sessions.find { it.id == uiState.selectedSessionId }?.title
                ?: t("Session history", "تاریخچه گفتگو")
        } else {
            t("Sessions", "گفتگوها")
        },
        subtitle = if (inHistoryDetail) null else {
            t("${uiState.sessions.size} conversations", "${uiState.sessions.size} گفتگو")
        },
        onBack = { if (inHistoryDetail) viewModel.closeHistory() else onNavigateBack() },
        actions = { if (!inHistoryDetail) SortMenu(uiState, viewModel) },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (inHistoryDetail) {
                HistoryDetailView(
                    messages = uiState.selectedSessionHistory,
                    isLoading = uiState.isLoadingHistory,
                    usage = uiState.selectedSessionUsage,
                    onResumeSession = {
                        uiState.selectedSessionId?.let { onResumeSession(it) }
                    },
                )
            } else {
                SessionsList(uiState, viewModel)
            }
        }
    }
}

// ── Top-bar sort/refresh menu ─────────────────────────────────────────────

@Composable
private fun SortMenu(state: SessionsUiState, viewModel: SessionsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.SwapVert, contentDescription = t("Sort", "مرتب‌سازی"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(t("Refresh sessions", "به‌روزرسانی گفتگوها")) },
                onClick = { viewModel.loadSessions(); expanded = false },
            )
            listOf(
                SessionSortOrder.NEWEST_FIRST to t("Newest first", "جدیدترین"),
                SessionSortOrder.OLDEST_FIRST to t("Oldest first", "قدیمی‌ترین"),
                SessionSortOrder.NAME_AZ to t("Name A-Z", "نام الف-ی"),
            ).forEach { (order, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (state.sortOrder == order) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = { viewModel.setSortOrder(order); expanded = false },
                )
            }
        }
    }
}

// ── Sessions list ─────────────────────────────────────────────────────────

@Composable
private fun SessionsList(state: SessionsUiState, viewModel: SessionsViewModel) {
    if (state.isLoadingSessions) {
        LoadingIndicator(t("Loading sessions...", "در حال بارگذاری گفتگوها..."))
        return
    }
    if (state.sessions.isEmpty()) {
        HermesEmptyState(
            icon = Icons.Default.Forum,
            title = t("No sessions yet", "هنوز گفتگویی وجود ندارد"),
            caption = t(
                "Conversations you start with the agent will show up here",
                "گفتگوهایی که با ایجنت شروع کنی اینجا نمایش داده می‌شن",
            ),
        )
        return
    }

    val filteredSessions = if (state.searchQuery.isBlank()) state.sessions else {
        val q = state.searchQuery.lowercase()
        state.sessions.filter {
            it.title.lowercase().contains(q) || it.lastMessagePreview?.lowercase()?.contains(q) == true
        }
    }
    val pinned = filteredSessions.filter { it.id in state.pinnedSessionIds }
    val unpinned = filteredSessions.filter { it.id !in state.pinnedSessionIds }
    val sort = { list: List<SessionSummary> ->
        when (state.sortOrder) {
            SessionSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.updatedAt }
            SessionSortOrder.OLDEST_FIRST -> list.sortedBy { it.updatedAt }
            SessionSortOrder.NAME_AZ -> list.sortedBy { it.title.lowercase() }
        }
    }
    val displaySessions = sort(pinned) + sort(unpinned)

    Column(modifier = Modifier.fillMaxSize()) {
        // Search — pill-shaped, tonal, no heavy outline.
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen, vertical = HxSpace.sm),
            placeholder = { Text(t("Search sessions...", "جستجوی گفتگوها...")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = t("Clear", "پاک کردن"), modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
        if (state.searchQuery.isNotBlank()) {
            Text(
                text = t(
                    "${displaySessions.size} of ${state.sessions.size} sessions",
                    "${displaySessions.size} از ${state.sessions.size} گفتگو",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HxSpace.screen, end = HxSpace.screen,
                top = HxSpace.sm, bottom = HxSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            state.insights?.let { ins ->
                item(key = "insights") {
                    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                        StatTile(value = "${ins.sessions}", label = t("sessions", "گفتگو"))
                        StatTile(value = "${ins.messages}", label = t("messages", "پیام"))
                        StatTile(value = "${ins.days}", label = t("days", "روز"))
                    }
                    Spacer(Modifier.height(HxSpace.xs))
                }
            }
            if (state.activeAgents.isNotEmpty()) {
                item(key = "agents") { ActiveAgentsCard(state) }
            }
            items(displaySessions, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    viewModel = viewModel,
                    isPinned = session.id in state.pinnedSessionIds,
                )
            }
        }
    }
}

// ── One session row ───────────────────────────────────────────────────────

@Composable
private fun SessionRow(
    session: SessionSummary,
    viewModel: SessionsViewModel,
    isPinned: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HxRadius.md))
            .clickable { viewModel.loadSessionHistory(session.id) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HxSpace.inner, top = HxSpace.md, bottom = HxSpace.md, end = HxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = t("Pinned", "سنجاق‌شده"),
                            modifier = Modifier.size(13.dp).padding(end = 0.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                session.lastMessagePreview?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "${session.messageCount} ${t("messages", "پیام")} · ${dateFormat.format(Date(session.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = t("Options", "گزینه‌ها"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(t("View history", "مشاهده تاریخچه")) },
                        onClick = { viewModel.loadSessionHistory(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) t("Unpin", "برداشتن سنجاق") else t("Pin", "سنجاق")) },
                        onClick = { viewModel.togglePin(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Rename", "تغییر نام")) },
                        onClick = { viewModel.showRenameDialog(session.id, session.title); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Share / Export", "اشتراک‌گذاری / خروجی")) },
                        onClick = { viewModel.shareSession(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) },
                        onClick = { viewModel.confirmDelete(session.id); menuExpanded = false },
                    )
                }
            }
        }
    }
}

// ── History detail ────────────────────────────────────────────────────────

@Composable
private fun HistoryDetailView(
    messages: List<HistoryMessage>,
    isLoading: Boolean,
    usage: com.hermes.android.ui.viewmodel.SessionUsage?,
    onResumeSession: () -> Unit,
) {
    if (isLoading) {
        LoadingIndicator(t("Loading messages...", "در حال بارگذاری پیام‌ها..."))
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onResumeSession,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen, vertical = HxSpace.sm),
        ) {
            Text(t("Continue this chat", "ادامه این گفتگو"))
        }

        usage?.takeIf { it.total > 0 || it.calls > 0 }?.let { u ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
            ) {
                StatTile(value = "${u.input}", label = t("in", "ورودی"))
                StatTile(value = "${u.output}", label = t("out", "خروجی"))
                StatTile(value = "${u.total}", label = t("total", "کل"))
                StatTile(value = "${u.calls}", label = t("calls", "فراخوانی"))
            }
            u.creditsLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = HxSpace.screen),
                )
            }
        }

        if (messages.isEmpty()) {
            HermesEmptyState(
                icon = Icons.Default.Forum,
                title = t("No messages found", "پیامی پیدا نشد"),
                caption = t("This session may be empty or inaccessible", "این گفتگو خالی یا در دسترس نیست"),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = HxSpace.md, vertical = HxSpace.xs),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                items(messages, key = { it.role + it.content.take(40) + messages.indexOf(it) }) { msg ->
                    HistoryMessageBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun HistoryMessageBubble(msg: HistoryMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(HxRadius.md),
            color = if (isUser) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Column(modifier = Modifier.padding(HxSpace.md)) {
                Text(
                    text = if (isUser) t("You", "شما") else "Hermes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Active agents ─────────────────────────────────────────────────────────

@Composable
private fun ActiveAgentsCard(state: SessionsUiState) {
    val agents = state.activeAgents
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(HxSpace.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Text(
                    text = t("Active agents (${agents.size})", "ایجنت‌های فعال (${agents.size})"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            agents.take(8).forEach { a ->
                Text(
                    text = "${a.command}  —  ${a.status}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// LoadingIndicator: internal fun in ConfigComponents.kt (same package, no import needed)
