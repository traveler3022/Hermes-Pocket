package com.hermes.android.ui.screen

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // ── Delete confirmation dialog ──────────────────────────────────────
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

    // ── Rename dialog ───────────────────────────────────────────────────
    uiState.showRenameDialog?.let { renameState ->
        var newTitle by remember(renameState) { mutableStateOf(renameState.currentTitle) }
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text(t("Rename session", "تغییر نام گفتگو")) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(t("Title", "عنوان")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inHistoryDetail)
                            uiState.sessions.find { it.id == uiState.selectedSessionId }?.title
                                ?: t("Session history", "تاریخچه گفتگو")
                        else
                            t("Sessions", "گفتگوها"),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (inHistoryDetail) viewModel.closeHistory() else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!inHistoryDetail) {
                        TopBarMenu(uiState, viewModel)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                SessionsTab(uiState, viewModel)
            }
        }
    }
}

// ── Top-bar overflow menu (sort + refresh) ──────────────────────────────

@Composable
private fun TopBarMenu(state: SessionsUiState, viewModel: SessionsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = t("More", "بیشتر"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(t("Refresh sessions", "به‌روزرسانی گفتگوها")) },
                onClick = { viewModel.loadSessions(); expanded = false },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        t("Newest first", "جدیدترین"),
                        color = if (state.sortOrder == SessionSortOrder.NEWEST_FIRST)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { viewModel.setSortOrder(SessionSortOrder.NEWEST_FIRST); expanded = false },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        t("Oldest first", "قدیمی‌ترین"),
                        color = if (state.sortOrder == SessionSortOrder.OLDEST_FIRST)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { viewModel.setSortOrder(SessionSortOrder.OLDEST_FIRST); expanded = false },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        t("Name A-Z", "نام الف-ی"),
                        color = if (state.sortOrder == SessionSortOrder.NAME_AZ)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { viewModel.setSortOrder(SessionSortOrder.NAME_AZ); expanded = false },
            )
        }
    }
}

// ── Sessions tab ────────────────────────────────────────────────────────

@Composable
private fun SessionsTab(state: SessionsUiState, viewModel: SessionsViewModel) {
    if (state.isLoadingSessions) {
        LoadingIndicator(t("Loading sessions...", "در حال بارگذاری گفتگوها..."))
        return
    }
    if (state.sessions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t("No sessions yet", "هنوز گفتگویی وجود ندارد"), style = MaterialTheme.typography.bodyLarge)
        }
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
        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(t("Search sessions...", "جستجوی گفتگوها...")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = t("Clear", "پاک کردن"))
                    }
                }
            },
            singleLine = true,
        )
        if (state.searchQuery.isNotBlank()) {
            Text(
                text = t("${displaySessions.size} of ${state.sessions.size} sessions",
                    "${displaySessions.size} از ${state.sessions.size} گفتگو"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Aggregate usage insights (insights.get) — only when present.
            state.insights?.let { ins ->
                item(key = "insights") { InsightsCard(ins) }
            }
            // Active agent processes (agents.list) — hidden when none.
            if (state.activeAgents.isNotEmpty()) {
                item(key = "agents") { ActiveAgentsCard(state.activeAgents) }
            }
            items(displaySessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    viewModel = viewModel,
                    isPinned = session.id in state.pinnedSessionIds,
                )
            }
        }
    }
}

// ── Session card with per-item 3-dot overflow menu ──────────────────────

@Composable
private fun SessionCard(
    session: SessionSummary,
    viewModel: SessionsViewModel,
    isPinned: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.loadSessionHistory(session.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (isPinned)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                session.lastMessagePreview?.let { preview ->
                    Text(
                        text = preview.take(80) + if (preview.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${session.messageCount} ${t("messages", "پیام")} · ${dateFormat.format(Date(session.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Single 3-dot overflow menu replaces all inline buttons
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = t("Options", "گزینه‌ها"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = {
                            Text(
                                t("Delete", "حذف"),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { viewModel.confirmDelete(session.id); menuExpanded = false },
                    )
                }
            }
        }
    }
}

// ── History detail view (replaces sessions list when a session is tapped) ──

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
        // "Continue chat" button always visible at top
        Button(
            onClick = onResumeSession,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(t("Continue this chat", "ادامه این گفتگو"))
        }

        // Token usage / cost (session.usage) — the user's main concern.
        usage?.takeIf { it.total > 0 || it.calls > 0 }?.let { u ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = t("Token usage", "مصرف توکن"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = "▸ in ${u.input} · out ${u.output} · total ${u.total} · ${u.calls} calls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    u.creditsLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    t("No messages found", "پیامی پیدا نشد"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    t("This session may be empty or inaccessible", "این گفتگو خالی یا در دسترس نیست"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val bgColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = bgColor),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = if (isUser) t("You", "شما") else "Hermes",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                )
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Insights & active agents ────────────────────────────────────────────

@Composable
private fun InsightsCard(insights: com.hermes.android.ui.viewmodel.InsightsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = t("Last ${insights.days} days", "${insights.days} روز اخیر"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = t(
                    "${insights.sessions} sessions · ${insights.messages} messages",
                    "${insights.sessions} گفتگو · ${insights.messages} پیام",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ActiveAgentsCard(agents: List<com.hermes.android.ui.viewmodel.AgentProcess>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = t("Active agents (${agents.size})", "ایجنت‌های فعال (${agents.size})"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            agents.take(8).forEach { a ->
                Text(
                    text = "• ${a.command}  —  ${a.status}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingIndicator(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}
