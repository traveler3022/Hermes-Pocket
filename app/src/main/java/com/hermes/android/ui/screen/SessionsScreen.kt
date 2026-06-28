package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.HistoryMessage
import com.hermes.android.ui.viewmodel.SessionSortOrder
import com.hermes.android.ui.viewmodel.SessionSummary
import com.hermes.android.ui.viewmodel.SessionsUiState
import com.hermes.android.ui.viewmodel.SessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import com.hermes.android.ui.i18n.t

/**
 * Sessions & Memory screen — browse past sessions, view USER.md / MEMORY.md.
 *
 * Depends ONLY on [SessionsViewModel] — never on gateway or runtime.
 *
 * Reference: Phase 1.5 Rule 1
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableStateOf(0) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val memoryState by viewModel.memoryState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ── Delete confirmation dialog (#9) ───────────────────────────────
    if (uiState.showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(t("Delete session", "حذف گفتگو")) },
            text = {
                Text(
                    t(
                        "Are you sure you want to delete this session?",
                        "آیا از حذف این گفتگو مطمئنید؟",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.executeDelete() }) {
                    Text(
                        t("Delete", "حذف"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(t("Cancel", "انصراف"))
                }
            },
        )
    }

    // ── Rename dialog (#6) ────────────────────────────────────────────
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Sessions & Memory", "گفتگوها و حافظه")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(t("Sessions", "گفتگوها")) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(t("Memory", "حافظه")) },
                )
            }

            when (selectedTab) {
                0 -> SessionsTab(uiState, viewModel)
                1 -> MemoryTab(memoryState)
            }
        }
    }
}

@Composable
private fun SessionsTab(
    state: SessionsUiState,
    viewModel: SessionsViewModel,
) {
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

    // Apply search filter
    val filteredSessions = if (state.searchQuery.isBlank()) {
        state.sessions
    } else {
        val query = state.searchQuery.lowercase()
        state.sessions.filter { session ->
            session.title.lowercase().contains(query) ||
                (session.lastMessagePreview?.lowercase()?.contains(query) == true)
        }
    }

    // Separate pinned and unpinned
    val pinned = filteredSessions.filter { it.id in state.pinnedSessionIds }
    val unpinned = filteredSessions.filter { it.id !in state.pinnedSessionIds }

    // Sort unpinned sessions
    val sortedUnpinned = when (state.sortOrder) {
        SessionSortOrder.NEWEST_FIRST -> unpinned.sortedByDescending { it.updatedAt }
        SessionSortOrder.OLDEST_FIRST -> unpinned.sortedBy { it.updatedAt }
        SessionSortOrder.NAME_AZ -> unpinned.sortedBy { it.title.lowercase() }
    }

    // Sort pinned sessions with the same sort order
    val sortedPinned = when (state.sortOrder) {
        SessionSortOrder.NEWEST_FIRST -> pinned.sortedByDescending { it.updatedAt }
        SessionSortOrder.OLDEST_FIRST -> pinned.sortedBy { it.updatedAt }
        SessionSortOrder.NAME_AZ -> pinned.sortedBy { it.title.lowercase() }
    }

    // Pinned first, then sorted unpinned
    val displaySessions = sortedPinned + sortedUnpinned

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search bar (#17) and sort button (#19) ────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(t("Search sessions...", "جستجوی گفتگوها...")) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = t("Clear", "پاک کردن"))
                        }
                    }
                },
                singleLine = true,
            )

            // Sort dropdown (#19)
            SortDropdown(state.sortOrder, viewModel)
        }

        // Filtered count
        if (state.searchQuery.isNotBlank()) {
            Text(
                text = t(
                    "${displaySessions.size} of ${state.sessions.size} sessions",
                    "${displaySessions.size} از ${state.sessions.size} گفتگو",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.selectedSessionId != null) {
                item {
                    SelectedSessionHistory(state.selectedSessionHistory)
                }
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

@Composable
private fun SortDropdown(
    currentSort: SessionSortOrder,
    viewModel: SessionsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = t("Sort", "مرتب‌سازی"),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    t("Newest first", "جدیدترین"),
                    color = if (currentSort == SessionSortOrder.NEWEST_FIRST)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = {
                viewModel.setSortOrder(SessionSortOrder.NEWEST_FIRST)
                expanded = false
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    t("Oldest first", "قدیمی‌ترین"),
                    color = if (currentSort == SessionSortOrder.OLDEST_FIRST)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = {
                viewModel.setSortOrder(SessionSortOrder.OLDEST_FIRST)
                expanded = false
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    t("Name A-Z", "نام الف-ی"),
                    color = if (currentSort == SessionSortOrder.NAME_AZ)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = {
                viewModel.setSortOrder(SessionSortOrder.NAME_AZ)
                expanded = false
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionSummary,
    viewModel: SessionsViewModel,
    isPinned: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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
                    text = "${session.messageCount} messages · ${dateFormat.format(Date(session.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Action buttons row
            Row {
                // Pin button (#18)
                IconButton(onClick = { viewModel.togglePin(session.id) }) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) t("Unpin", "برداشتن سنجاق") else t("Pin", "سنجاق"),
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Rename button (#6)
                IconButton(onClick = { viewModel.showRenameDialog(session.id, session.title) }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = t("Rename", "تغییر نام"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Share/Export button (#20, #21)
                IconButton(onClick = { viewModel.shareSession(session.id) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = t("Share", "اشتراک‌گذاری"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Delete button (now with confirmation, #9)
                IconButton(onClick = { viewModel.confirmDelete(session.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = t("Delete", "حذف"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedSessionHistory(messages: List<HistoryMessage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Selected session", "گفتگوی انتخاب‌شده"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (messages.isEmpty()) {
                Text(
                    text = t("No messages loaded yet", "هنوز پیامی بارگذاری نشده"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                messages.takeLast(12).forEach { msg ->
                    Text(
                        text = "${msg.role}: ${msg.content.take(240)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryTab(state: com.hermes.android.ui.viewmodel.MemoryUiState) {
    if (state.isLoading) {
        LoadingIndicator(t("Loading memory...", "در حال بارگذاری حافظه..."))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // USER.md
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "USER.md",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = state.userMd.ifBlank { t("Memory has not been created yet", "حافظه هنوز ساخته نشده") }.replace("(not found)", t("Memory has not been created yet", "حافظه هنوز ساخته نشده")),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // MEMORY.md
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "MEMORY.md",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    text = state.memoryMd.ifBlank { t("Memory has not been created yet", "حافظه هنوز ساخته نشده") }.replace("(not found)", t("Memory has not been created yet", "حافظه هنوز ساخته نشده")),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicator(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
