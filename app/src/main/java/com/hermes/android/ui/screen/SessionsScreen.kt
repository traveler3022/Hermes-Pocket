package com.hermes.android.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import com.hermes.android.ui.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import com.hermes.android.ui.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.viewmodel.BulkDelete
import com.hermes.android.ui.viewmodel.CleanupKind
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
            title = { Text(stringResource(R.string.delete_session)) },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_this)) },
            confirmButton = {
                TextButton(onClick = { viewModel.executeDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.cancel_2))
                }
            },
        )
    }

    uiState.showRenameDialog?.let { renameState ->
        var newTitle by remember(renameState) { mutableStateOf(renameState.currentTitle) }
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text(stringResource(R.string.rename_session)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.title)) },
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
                            Text(stringResource(R.string.suggest_title))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.renameSession(renameState.sessionId, newTitle) },
                    enabled = newTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRenameDialog() }) {
                    Text(stringResource(R.string.cancel_2))
                }
            },
        )
    }

    uiState.pendingBulkDelete?.let { pending ->
        val count = pending.ids.size
        AlertDialog(
            onDismissRequest = { viewModel.cancelBulkDelete() },
            title = { Text(stringResource(R.string.delete_chats, count)) },
            text = {
                Text(
                    when (pending) {
                        is BulkDelete.Selected -> stringResource(R.string.the_selected_chats_will_be_deleted_for)
                        is BulkDelete.Cleanup -> when (val kind = pending.kind) {
                            CleanupKind.Empty -> stringResource(R.string.chats_with_no_messages_will_be_deleted)
                            is CleanupKind.OlderThan -> stringResource(R.string.chats_started_more_than_days_ago_will_be, kind.days)
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.executeBulkDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelBulkDelete() }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }

    var askOlderThan by remember { mutableStateOf(false) }
    if (askOlderThan) {
        var days by remember { mutableStateOf("30") }
        val valid = days.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { askOlderThan = false },
            title = { Text(stringResource(R.string.delete_old_chats)) },
            text = {
                OutlinedTextField(
                    value = days,
                    onValueChange = { value -> days = value.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.older_than_days)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { valid?.let { viewModel.askCleanup(CleanupKind.OlderThan(it)) }; askOlderThan = false },
                    enabled = valid != null,
                ) { Text(stringResource(R.string.next_2)) }
            },
            dismissButton = {
                TextButton(onClick = { askOlderThan = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }

    val inHistoryDetail = uiState.selectedSessionId != null
    BackHandler(enabled = !inHistoryDetail && uiState.isSelecting) { viewModel.toggleSelecting() }
    // Back from a session's history returns to the list, like the top bar's arrow;
    // it used to leave the Sessions screen altogether.
    BackHandler(enabled = inHistoryDetail) { viewModel.closeHistory() }

    HermesScaffold(
        title = if (inHistoryDetail) {
            uiState.sessions.find { it.id == uiState.selectedSessionId }?.title
                ?: stringResource(R.string.session_history)
        } else if (uiState.isSelecting) {
            stringResource(R.string.selected, uiState.selectedIds.size)
        } else {
            stringResource(R.string.sessions)
        },
        subtitle = if (inHistoryDetail || uiState.isSelecting) null else {
            stringResource(R.string.conversations, uiState.sessions.size)
        },
        onBack = {
            when {
                inHistoryDetail -> viewModel.closeHistory()
                uiState.isSelecting -> viewModel.toggleSelecting()
                else -> onNavigateBack()
            }
        },
        actions = {
            when {
                inHistoryDetail -> Unit
                uiState.isSelecting -> {
                    TextButton(onClick = { viewModel.selectAll(visibleSessions(uiState).mapTo(linkedSetOf()) { it.id }) }) {
                        Text(stringResource(R.string.all))
                    }
                    IconButton(onClick = { viewModel.askBulkDelete() }, enabled = uiState.selectedIds.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                    }
                }
                else -> SortMenu(uiState, viewModel, onDeleteOld = { askOlderThan = true })
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (uiState.isDeletingBulk) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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
private fun SortMenu(state: SessionsUiState, viewModel: SessionsViewModel, onDeleteOld: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh_sessions)) },
                onClick = { viewModel.loadSessions(); expanded = false },
            )
            listOf(
                SessionSortOrder.NEWEST_FIRST to stringResource(R.string.newest_first),
                SessionSortOrder.OLDEST_FIRST to stringResource(R.string.oldest_first),
                SessionSortOrder.NAME_AZ to stringResource(R.string.name_a_z),
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
            if (state.sessions.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_chats)) },
                    onClick = { viewModel.toggleSelecting(); expanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_empty_chats)) },
                    onClick = { viewModel.askCleanup(CleanupKind.Empty); expanded = false },
                    enabled = !state.isDeletingBulk,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_old_chats_2)) },
                    onClick = { onDeleteOld(); expanded = false },
                    enabled = !state.isDeletingBulk,
                )
            }
        }
    }
}

// ── Sessions list ─────────────────────────────────────────────────────────

@Composable
private fun SessionsList(state: SessionsUiState, viewModel: SessionsViewModel) {
    if (state.isLoadingSessions) {
        LoadingIndicator(stringResource(R.string.loading_sessions))
        return
    }
    if (state.sessions.isEmpty()) {
        HermesEmptyState(
            icon = Icons.Default.Forum,
            title = stringResource(R.string.no_sessions_yet),
            caption = stringResource(R.string.conversations_you_start_with_the_agent),
        )
        return
    }

    val displaySessions = visibleSessions(state)

    Column(modifier = Modifier.fillMaxSize()) {
        // Search — pill-shaped, tonal, no heavy outline.
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen, vertical = HxSpace.sm),
            placeholder = { Text(stringResource(R.string.search_sessions)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
        if (state.searchQuery.isNotBlank()) {
            Text(
                text = stringResource(R.string.of_sessions, displaySessions.size, state.sessions.size),
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
                        StatTile(value = "${ins.sessions}", label = stringResource(R.string.sessions_3))
                        StatTile(value = "${ins.messages}", label = stringResource(R.string.messages))
                        StatTile(value = "${ins.days}", label = stringResource(R.string.days_2))
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
                    selecting = state.isSelecting,
                    selected = session.id in state.selectedIds,
                )
            }
        }
    }
}

/** The rows the list shows: search applied, pinned first, then the chosen order. */
private fun visibleSessions(state: SessionsUiState): List<SessionSummary> {
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
    return sort(pinned) + sort(unpinned)
}

// ── One session row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    viewModel: SessionsViewModel,
    isPinned: Boolean,
    selecting: Boolean,
    selected: Boolean,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HxRadius.md))
            // While selecting, a tap ticks the row; a long press starts selecting.
            .combinedClickable(
                onClick = {
                    if (selecting) viewModel.toggleSelected(session.id) else viewModel.loadSessionHistory(session.id)
                },
                onLongClick = { viewModel.toggleSelected(session.id) },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HxSpace.inner, top = HxSpace.md, bottom = HxSpace.md, end = HxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { viewModel.toggleSelected(session.id) })
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.pinned),
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
                    text = "${session.messageCount} ${stringResource(R.string.messages)} · ${dateFormat.format(Date(session.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            if (!selecting) Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_history)) },
                        onClick = { viewModel.loadSessionHistory(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                        onClick = { viewModel.togglePin(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        onClick = { viewModel.showRenameDialog(session.id, session.title); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_export)) },
                        onClick = { viewModel.shareSession(session.id); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { viewModel.confirmDelete(session.id); menuExpanded = false },
                    )
                }
            }
        }
    }
}

// ── History detail ────────────────────────────────────────────────────────

