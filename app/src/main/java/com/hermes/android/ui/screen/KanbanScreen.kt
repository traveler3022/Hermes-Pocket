package com.hermes.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.android.R
import com.hermes.android.domain.kanban.KanbanRepository
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.icons.filled.AttachFile
import com.hermes.android.ui.icons.filled.AutoAwesome
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.icons.filled.Inventory2
import com.hermes.android.ui.viewer.FileViewerActivity
import com.hermes.android.ui.viewmodel.KanbanViewModel

/** The columns a person reads, in the board's order (`plugin_api.py` BOARD_COLUMNS). */
private val COLUMNS = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")

/** The thinking levels a task can be set to (`hermes_constants.VALID_REASONING_EFFORTS` plus off). */
private val EFFORTS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")

@Composable
private fun columnLabel(column: String): String = when (column) {
    "triage" -> stringResource(R.string.triage)
    "todo" -> stringResource(R.string.to_do)
    "scheduled" -> stringResource(R.string.scheduled)
    "ready" -> stringResource(R.string.ready)
    "running" -> stringResource(R.string.running)
    "blocked" -> stringResource(R.string.blocked)
    "review" -> stringResource(R.string.review)
    "done" -> stringResource(R.string.done)
    "archived" -> stringResource(R.string.archived)
    else -> column
}

/**
 * Hermes Kanban on the user's server: tasks the profiles work on. The server's
 * dispatcher runs each ready task as its assignee; this shows the board, adds tasks and
 * makes the moves a person makes (unblock, approve, send back, archive).
 */
@Composable
fun KanbanScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: KanbanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var orchestrating by remember { mutableStateOf(false) }
    var bulkAssigning by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showingHealth by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBoard)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = stringResource(R.string.kanban),
        subtitle = stringResource(R.string.tasks_your_profiles_work_on),
        onBack = onNavigateBack,
        actions = {
            if (state.available) {
                IconButton(onClick = {
                    if (filterOpen) viewModel.clearFilter() else viewModel.loadAssignees()
                    filterOpen = !filterOpen
                }) {
                    Icon(
                        if (filterOpen) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(R.string.filter),
                        tint = if (state.filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.available && state.board != null) {
                BoardPicker(
                    boards = state.boards,
                    selected = state.board,
                    isBusy = state.isBusy,
                    onOpen = viewModel::refreshBoards,
                    onSelect = viewModel::selectBoard,
                    onCreate = viewModel::createBoard,
                )
            }
            if (state.available) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.show_archived)) },
                            leadingIcon = {
                                if (state.showArchived) Icon(Icons.Default.Check, contentDescription = null) else Box(Modifier.size(24.dp))
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.setShowArchived(!state.showArchived)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.orchestration)) },
                            onClick = {
                                menuOpen = false
                                viewModel.loadAssignees()
                                viewModel.loadOrchestration()
                                orchestrating = true
                            },
                        )
                        if (state.board != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_this_board)) },
                                enabled = !state.isBusy,
                                onClick = {
                                    menuOpen = false
                                    viewModel.exportBoard()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_a_board)) },
                            enabled = !state.isBusy,
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/x-tar", "application/octet-stream"))
                            },
                        )
                    }
                }
            }
        },
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.available && state.selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.loadAssignees()
                        creating = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_task)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.available) {
                HermesEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = stringResource(R.string.kanban_is_off),
                    caption = stringResource(R.string.turn_it_on_in_settings_general_termux),
                )
                return@Column
            }
            if (state.diagnostics.isNotEmpty()) {
                val worst = state.diagnostics.first().diagnostics.firstOrNull()?.severity
                TextButton(
                    onClick = { showingHealth = true },
                    modifier = Modifier.padding(horizontal = HxSpace.screen),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = severityColor(worst),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(HxSpace.sm))
                    Text(
                        stringResource(R.string.tasks_need_attention, state.diagnostics.size),
                        color = severityColor(worst),
                    )
                }
            }
            if (filterOpen) {
                FilterBar(
                    query = state.query,
                    assignee = state.assigneeFilter,
                    assignees = (state.assignees + state.columns.values.flatten().mapNotNull { it.assignee }).distinct(),
                    onQuery = viewModel::setQuery,
                    onAssignee = viewModel::setAssigneeFilter,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                (if (state.showArchived) COLUMNS + "archived" else COLUMNS).forEach { column ->
                    val count = state.shown[column]?.size ?: 0
                    FilterChip(
                        selected = column == state.column,
                        onClick = { viewModel.selectColumn(column) },
                        label = { Text(if (count > 0) "${columnLabel(column)} · $count" else columnLabel(column)) },
                    )
                }
            }
            val tasks = state.shown[state.column].orEmpty()
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                tasks.isEmpty() -> Text(
                    if (state.filtering) stringResource(R.string.no_task_matches_the_filter) else stringResource(R.string.nothing_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(HxSpace.screen),
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        val selecting = state.selected.isNotEmpty()
                        TaskCard(
                            task = task,
                            selected = task.id in state.selected,
                            onClick = { if (selecting) viewModel.toggleSelected(task.id) else viewModel.open(task) },
                            onLongClick = { viewModel.toggleSelected(task.id) },
                        )
                    }
                }
            }
            if (state.selected.isNotEmpty()) {
                SelectionBar(
                    count = state.selected.size,
                    isBusy = state.isBusy,
                    onReady = { viewModel.bulkMove("ready") },
                    onDone = { viewModel.bulkMove("done") },
                    onAssign = {
                        viewModel.loadAssignees()
                        bulkAssigning = true
                    },
                    onArchive = viewModel::bulkArchive,
                    onSelectAll = viewModel::selectColumnTasks,
                    onClear = viewModel::clearSelection,
                )
            }
        }
    }

    if (bulkAssigning) {
        AlertDialog(
            onDismissRequest = { bulkAssigning = false },
            title = { Text(stringResource(R.string.assign_tasks_to, state.selected.size)) },
            text = {
                AssigneePicker(current = "", assignees = state.assignees, enabled = !state.isBusy) { profile ->
                    bulkAssigning = false
                    viewModel.bulkAssign(profile)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { bulkAssigning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showingHealth) {
        HealthSheet(
            diagnostics = state.diagnostics,
            onOpen = { id ->
                showingHealth = false
                viewModel.open(id)
            },
            onDismiss = { showingHealth = false },
        )
    }
    if (orchestrating) {
        OrchestrationSheet(
            settings = state.orchestration,
            profiles = state.assignees,
            isBusy = state.isBusy,
            onSave = { settings -> viewModel.saveOrchestration(settings) { orchestrating = false } },
            onDismiss = { orchestrating = false },
        )
    }

    state.detail?.let { detail ->
        TaskSheet(
            detail = detail,
            isBusy = state.isBusy,
            assignees = state.assignees,
            log = state.log,
            isLogLoading = state.isLogLoading,
            inspections = state.inspections,
            onLoadAssignees = viewModel::loadAssignees,
            onLoadLog = { viewModel.loadLog(detail.task.id) },
            onInspect = viewModel::inspect,
            onReclaim = { viewModel.reclaim(detail.task.id) },
            onReassign = { profile -> viewModel.reassign(detail.task.id, profile, running = detail.task.status == "running") },
            onMove = { status, reason -> viewModel.move(detail.task.id, status, reason) },
            onComment = { text, done -> viewModel.comment(detail.task.id, text, done) },
            onDelete = { viewModel.delete(detail.task.id) },
            onDismiss = viewModel::close,
            boardTasks = state.columns.values.flatten(),
            isSpecifying = state.isSpecifying,
            onSpecify = { viewModel.specify(detail.task.id) },
            onOpenAttachment = { file -> FileViewerActivity.open(context, viewModel.attachmentUrl(file), file.filename) },
            onAddParent = viewModel::addParent,
            onUnlink = viewModel::unlink,
            onUpload = viewModel::upload,
            onDeleteAttachment = viewModel::deleteAttachment,
            estimate = state.estimate,
            isEstimating = state.isEstimating,
            onEstimate = { viewModel.estimate(detail.task.id) },
            modelOptions = state.modelOptions,
            onLoadModelOptions = viewModel::loadModelOptions,
            onRunSettings = { model, provider, effort -> viewModel.setRunSettings(detail.task.id, model, provider, effort) },
            homeChannels = state.homeChannels,
            onHomeChannel = viewModel::setHomeChannel,
        )
    }
    if (creating) {
        NewTaskSheet(
            assignees = state.assignees,
            isBusy = state.isBusy,
            onCreate = { title, body, assignee -> viewModel.create(title, body, assignee) { creating = false } },
            onDismiss = { creating = false },
        )
    }
}

/** Words to look for and whose tasks to show. */
@Composable
private fun FilterBar(
    query: String,
    assignee: String?,
    assignees: List<String>,
    onQuery: (String) -> Unit,
    onAssignee: (String?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = HxSpace.screen),
        verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.search_tasks)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            FilterChip(selected = assignee == null, onClick = { onAssignee(null) }, label = { Text(stringResource(R.string.everyone)) })
            FilterChip(
                selected = assignee == KanbanViewModel.UNASSIGNED,
                onClick = { onAssignee(KanbanViewModel.UNASSIGNED) },
                label = { Text(stringResource(R.string.unassigned)) },
            )
            assignees.forEach { name ->
                FilterChip(selected = assignee == name, onClick = { onAssignee(name) }, label = { Text("@$name") })
            }
        }
    }
}

/** What can be done to every picked task at once. */
@Composable
private fun SelectionBar(
    count: Int,
    isBusy: Boolean,
    onReady: () -> Unit,
    onDone: () -> Unit,
    onAssign: () -> Unit,
    onArchive: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = HxSpace.screen, vertical = HxSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.selected, count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll, enabled = !isBusy) { Text(stringResource(R.string.select_column)) }
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Button(onClick = onReady, enabled = !isBusy) { Text(stringResource(R.string.make_ready)) }
                OutlinedButton(onClick = onDone, enabled = !isBusy) { Text(stringResource(R.string.done)) }
                OutlinedButton(onClick = onAssign, enabled = !isBusy) { Text(stringResource(R.string.assign)) }
                OutlinedButton(onClick = onArchive, enabled = !isBusy) { Text(stringResource(R.string.archive)) }
            }
        }
    }
}

/**
 * The server's orchestration settings (`kanban:` in config.yaml): who splits a task without an
 * assignee, who gets a step nobody fits, and whether splitting and starting the next steps happen
 * on their own. Shared by every board.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrchestrationSheet(
    settings: KanbanRepository.Orchestration?,
    profiles: List<String>,
    isBusy: Boolean,
    onSave: (KanbanRepository.Orchestration) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            SheetHeader(stringResource(R.string.orchestration), onBack = null)
            if (settings == null) {
                CircularProgressIndicator(Modifier.size(24.dp))
                return@Column
            }
            var draft by remember(settings) { mutableStateOf(settings) }
            SheetSection(stringResource(R.string.splits_new_tasks))
            SheetHint(
                stringResource(R.string.the_profile_that_breaks_a_task_with_no)
            )
            ProfileChoice(draft.orchestratorProfile, profiles, draft.activeProfile, enabled = !isBusy) {
                draft = draft.copy(orchestratorProfile = it)
            }
            SheetSection(stringResource(R.string.gets_unmatched_steps))
            SheetHint(
                stringResource(R.string.who_gets_a_step_no_profiles_description)
            )
            ProfileChoice(draft.defaultAssignee, profiles, draft.activeProfile, enabled = !isBusy) {
                draft = draft.copy(defaultAssignee = it)
            }
            SettingSwitch(
                title = stringResource(R.string.split_on_its_own),
                hint = stringResource(R.string.new_tasks_without_an_assignee_are_split),
                checked = draft.autoDecompose,
                enabled = !isBusy,
            ) { draft = draft.copy(autoDecompose = it) }
            SettingSwitch(
                title = stringResource(R.string.start_the_next_steps),
                hint = stringResource(R.string.a_step_waiting_on_others_becomes_ready),
                checked = draft.autoPromoteChildren,
                enabled = !isBusy,
            ) { draft = draft.copy(autoPromoteChildren = it) }
            Button(
                onClick = { onSave(draft) },
                enabled = !isBusy && draft != settings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        }
    }
}

/** A profile, or "" for the server's choice (its active profile). */
@Composable
private fun ProfileChoice(current: String, profiles: List<String>, active: String, enabled: Boolean, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        FilterChip(
            selected = current.isEmpty(),
            onClick = { onPick("") },
            enabled = enabled,
            label = { Text(stringResource(R.string.active_profile, active)) },
        )
        (profiles + current).filter { it.isNotEmpty() }.distinct().forEach { name ->
            FilterChip(selected = current == name, onClick = { onPick(name) }, enabled = enabled, label = { Text("@$name") })
        }
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            SheetHint(hint)
        }
        Spacer(Modifier.width(HxSpace.sm))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** The board on screen, and a menu of the others plus "New board". */
@Composable
private fun BoardPicker(
    boards: List<KanbanRepository.Board>,
    selected: String?,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: (name: String, onDone: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val current = boards.firstOrNull { it.slug == selected }
    Box {
        TextButton(onClick = {
            onOpen()
            open = true
        }) {
            Text(
                current?.let { boardLabel(it) } ?: selected.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = HxSpace.xs),
            )
            Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.boards), modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            boards.forEach { board ->
                DropdownMenuItem(
                    text = { Text(if (board.total > 0) "${boardLabel(board)} · ${board.total}" else boardLabel(board)) },
                    leadingIcon = {
                        if (board.slug == selected) Icon(Icons.Default.Check, contentDescription = null)
                        else Box(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onSelect(board.slug)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_board)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    open = false
                    creating = true
                },
            )
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_board)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !isBusy,
                    onClick = { onCreate(name) { creating = false } },
                ) { Text(stringResource(R.string.create_2)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel_2)) } },
        )
    }
}

private fun boardLabel(board: KanbanRepository.Board): String =
    listOf(board.icon, board.name).filter { it.isNotBlank() }.joinToString(" ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(task: KanbanRepository.Task, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(HxSpace.md), verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                task.assignee?.let { "@$it" } ?: stringResource(R.string.unassigned_2),
                task.progress?.let { (done, total) -> "$done/$total" },
                task.commentCount.takeIf { it > 0 }?.let { stringResource(R.string.comments, it) },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val note = task.lastError.ifBlank { task.summary }
            if (note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.lastError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    detail: KanbanRepository.Detail,
    isBusy: Boolean,
    assignees: List<String>,
    log: KanbanRepository.Log?,
    isLogLoading: Boolean,
    inspections: Map<Long, KanbanRepository.Inspection>,
    onLoadAssignees: () -> Unit,
    onLoadLog: () -> Unit,
    onInspect: (runId: Long) -> Unit,
    onReclaim: () -> Unit,
    onReassign: (profile: String?) -> Unit,
    onMove: (status: String, reason: String?) -> Unit,
    onComment: (text: String, onDone: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    boardTasks: List<KanbanRepository.Task>,
    isSpecifying: Boolean,
    onSpecify: () -> Unit,
    onOpenAttachment: (KanbanRepository.Attachment) -> Unit,
    onAddParent: (parentId: String) -> Unit,
    onUnlink: (parentId: String, childId: String) -> Unit,
    onUpload: (android.net.Uri) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    estimate: KanbanRepository.Estimate?,
    isEstimating: Boolean,
    onEstimate: () -> Unit,
    modelOptions: List<KanbanRepository.ModelProvider>,
    onLoadModelOptions: () -> Unit,
    onRunSettings: (model: String?, provider: String?, effort: String?) -> Unit,
    homeChannels: List<KanbanRepository.HomeChannel>?,
    onHomeChannel: (platform: String, on: Boolean) -> Unit,
) {
    val task = detail.task
    var pickingParent by remember { mutableStateOf(false) }
    var editingRun by remember { mutableStateOf(false) }
    var deletingFile by remember { mutableStateOf<KanbanRepository.Attachment?>(null) }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onUpload) }
    var comment by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReclaim by remember { mutableStateOf(false) }
    var reassigning by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            item { Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Text(
                    "${columnLabel(task.status)} · " + (task.assignee?.let { "@$it" } ?: stringResource(R.string.unassigned_2)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.body.isNotBlank()) item { HermesMarkdown(markdown = task.body) }
            if (task.status == "triage") {
                item {
                    OutlinedButton(onClick = onSpecify, enabled = !isSpecifying && !isBusy) {
                        if (isSpecifying) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(HxSpace.sm))
                        Text(
                            if (isSpecifying) stringResource(R.string.writing_it_out)
                            else stringResource(R.string.write_out_the_details_with_ai)
                        )
                    }
                }
                item {
                    SheetHint(
                        stringResource(R.string.the_servers_model_writes_a_full_title)
                    )
                }
            }
            if (task.lastError.isNotBlank()) {
                item { Text(task.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            val report = task.summary.ifBlank { task.result }
            if (report.isNotBlank()) {
                item { SheetSection(stringResource(R.string.workers_report)) }
                item { HermesMarkdown(markdown = report) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.files), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { uploadLauncher.launch("*/*") }, enabled = !isBusy) { Text(stringResource(R.string.add_file)) }
                }
            }
            items(detail.attachments, key = { "file-${it.id}" }) { file ->
                AttachmentRow(file, onOpen = { onOpenAttachment(file) }, onDelete = { deletingFile = file })
            }
            item {
                TaskActions(task.status, isBusy, onMove, onStopWorker = { confirmReclaim = true }) { confirmDelete = true }
            }
            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.model_and_thinking), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(
                                task.modelOverride?.let { m -> task.providerOverride?.let { "$it/$m" } ?: m } ?: stringResource(R.string.profiles_model),
                                task.reasoningEffort?.let { effortLabel(it) } ?: stringResource(R.string.profiles_level),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.status != "done" && task.status != "archived") {
                        TextButton(onClick = {
                            onLoadModelOptions()
                            editingRun = true
                        }, enabled = !isBusy) { Text(stringResource(R.string.change)) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.estimate), style = MaterialTheme.typography.titleSmall)
                        estimate?.let { found ->
                            Text(
                                listOfNotNull(
                                    found.complexity?.let { stringResource(R.string.size, it) },
                                    stringResource(R.string.tokens, found.tokens),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (found.rationale.isNotBlank()) {
                                Text(found.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(onClick = onEstimate, enabled = !isEstimating) {
                        if (isEstimating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(if (estimate == null) stringResource(R.string.estimate_2) else stringResource(R.string.again))
                    }
                }
            }
            if (!homeChannels.isNullOrEmpty()) {
                item { SheetSection(stringResource(R.string.send_news_to)) }
                items(homeChannels, key = { "home-${it.platform}" }) { home ->
                    SettingSwitch(
                        title = "${home.platform.replaceFirstChar { it.uppercase() }} · ${home.name}",
                        hint = stringResource(R.string.updates_on_this_task_go_to_this_home),
                        checked = home.subscribed,
                        enabled = !isBusy,
                    ) { on -> onHomeChannel(home.platform, on) }
                }
            }
            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.waits_on), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (task.status != "done" && task.status != "archived") {
                        TextButton(onClick = { pickingParent = true }, enabled = !isBusy) { Text(stringResource(R.string.add)) }
                    }
                }
            }
            if (detail.parents.isEmpty()) {
                item { SheetHint(stringResource(R.string.starts_without_waiting_for_another_task)) }
            }
            items(detail.parents, key = { "parent-${it.id}" }) { parent ->
                LinkRow(parent, isBusy) { onUnlink(parent.id, task.id) }
            }
            if (detail.children.isNotEmpty()) {
                item { SheetSection(stringResource(R.string.waiting_on_this)) }
                items(detail.children, key = { "child-${it.id}" }) { child ->
                    LinkRow(child, isBusy) { onUnlink(task.id, child.id) }
                }
            }
            if (task.status != "done" && task.status != "archived") {
                item {
                    TextButton(
                        onClick = {
                            onLoadAssignees()
                            reassigning = !reassigning
                        },
                        enabled = !isBusy,
                    ) { Text(stringResource(R.string.change_who_does_it)) }
                }
                if (reassigning) {
                    item {
                        AssigneePicker(task.assignee, assignees, enabled = !isBusy) { profile ->
                            reassigning = false
                            onReassign(profile)
                        }
                    }
                    if (task.status == "running") {
                        item {
                            SheetHint(
                                stringResource(R.string.the_current_worker_is_stopped_first_then)
                            )
                        }
                    }
                }
            }
            if (detail.runs.isNotEmpty()) {
                item { HorizontalDivider() }
                item { SheetSection(stringResource(R.string.attempts)) }
                items(detail.runs.reversed(), key = { "run-${it.id}" }) { run ->
                    RunRow(run, inspections[run.id], onInspect = { onInspect(run.id) })
                }
            }
            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.worker_log),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLoadLog, enabled = !isLogLoading) {
                        Text(if (log == null) stringResource(R.string.show) else stringResource(R.string.refresh))
                    }
                }
            }
            if (isLogLoading && log == null) item { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            log?.let { item { LogView(it) } }
            item { HorizontalDivider() }
            item { SheetSection(stringResource(R.string.comments_2)) }
            items(detail.comments) { c ->
                Column {
                    Text(c.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = { Text(stringResource(R.string.add_a_comment_for_the_worker)) },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onComment(comment) { comment = "" } }, enabled = comment.isNotBlank() && !isBusy) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }
    deletingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            title = { Text(stringResource(R.string.remove_2, file.filename)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingFile = null
                    onDeleteAttachment(file.id)
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingFile = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editingRun) {
        RunSettingsDialog(
            model = task.modelOverride,
            provider = task.providerOverride,
            effort = task.reasoningEffort,
            options = modelOptions,
            onSave = { model, provider, effort ->
                editingRun = false
                onRunSettings(model, provider, effort)
            },
            onDismiss = { editingRun = false },
        )
    }
    if (pickingParent) {
        val linked = detail.parents.map { it.id }.toSet() + detail.children.map { it.id } + task.id
        val choices = boardTasks.filter { it.id !in linked && it.status != "archived" }
        AlertDialog(
            onDismissRequest = { pickingParent = false },
            title = { Text(stringResource(R.string.wait_for_which_task)) },
            text = {
                if (choices.isEmpty()) {
                    Text(stringResource(R.string.no_other_task_on_this_board))
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(choices, key = { it.id }) { choice ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pickingParent = false
                                        onAddParent(choice.id)
                                    }
                                    .padding(vertical = HxSpace.sm),
                            ) {
                                Text(choice.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(columnLabel(choice.status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingParent = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmReclaim) {
        AlertDialog(
            onDismissRequest = { confirmReclaim = false },
            title = { Text(stringResource(R.string.stop_the_worker)) },
            text = {
                Text(
                    stringResource(R.string.the_worker_process_is_ended_and_the_task)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReclaim = false
                    onReclaim()
                }) { Text(stringResource(R.string.stop), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReclaim = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_this_task)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun effortLabel(effort: String): String = when (effort) {
    "none" -> stringResource(R.string.thinking_off)
    else -> stringResource(R.string.thinking, effort)
}

@Composable
private fun severityColor(severity: String?) = when (severity) {
    "critical", "error" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

/** The board's tasks with trouble and what is wrong with each; a tap opens the task. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthSheet(
    diagnostics: List<KanbanRepository.TaskDiagnostics>,
    onOpen: (taskId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            item { SheetHeader(stringResource(R.string.board_health), onBack = null) }
            items(diagnostics, key = { it.taskId }) { row ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(row.taskId) },
                ) {
                    Column(Modifier.padding(HxSpace.md), verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (row.status.isNotEmpty()) {
                            Text(columnLabel(row.status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        row.diagnostics.forEach { d ->
                            Text(d.title, style = MaterialTheme.typography.bodyMedium, color = severityColor(d.severity))
                            if (d.detail.isNotBlank()) Text(d.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The model a task runs on and how hard it thinks, instead of its profile's. The models are the
 * server's own list (`/model-options`); with none, the model is typed.
 */
@Composable
private fun RunSettingsDialog(
    model: String?,
    provider: String?,
    effort: String?,
    options: List<KanbanRepository.ModelProvider>,
    onSave: (model: String?, provider: String?, effort: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedProvider by remember { mutableStateOf(provider) }
    var pickedModel by remember { mutableStateOf(model.orEmpty()) }
    var pickedEffort by remember { mutableStateOf(effort) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_and_thinking)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                item { SheetSection(stringResource(R.string.model)) }
                item {
                    FilterChip(
                        selected = pickedModel.isBlank(),
                        onClick = {
                            pickedModel = ""
                            pickedProvider = null
                        },
                        label = { Text(stringResource(R.string.profiles_model)) },
                    )
                }
                if (options.isEmpty()) {
                    item {
                        OutlinedTextField(
                            value = pickedModel,
                            onValueChange = { pickedModel = it },
                            label = { Text(stringResource(R.string.model_name_2)) },
                            singleLine = true,
                        )
                    }
                } else {
                    options.forEach { group ->
                        item { Text(group.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                                group.models.forEach { name ->
                                    FilterChip(
                                        selected = pickedModel == name && pickedProvider == group.slug,
                                        onClick = {
                                            pickedModel = name
                                            pickedProvider = group.slug
                                        },
                                        label = { Text(name) },
                                    )
                                }
                            }
                        }
                    }
                }
                item { SheetSection(stringResource(R.string.thinking_2)) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                        FilterChip(selected = pickedEffort == null, onClick = { pickedEffort = null }, label = { Text(stringResource(R.string.profiles)) })
                        EFFORTS.forEach { level ->
                            FilterChip(selected = pickedEffort == level, onClick = { pickedEffort = level }, label = { Text(level) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = pickedModel.trim().ifEmpty { null }
                onSave(chosen, if (chosen == null) null else pickedProvider, pickedEffort)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A file on the task: its name and size; a tap opens it in the app's viewer. */
@Composable
private fun AttachmentRow(file: KanbanRepository.Attachment, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(HxSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HxSpace.sm))
            Text(file.filename, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(fileSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun fileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}

/** A linked task by title and column, with a button that removes the link. */
@Composable
private fun LinkRow(linked: KanbanRepository.LinkedTask, isBusy: Boolean, onUnlink: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(linked.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (linked.status.isNotEmpty()) {
                Text(columnLabel(linked.status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onUnlink, enabled = !isBusy) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_link))
        }
    }
}

/** The moves a person makes; the worker's own moves (start, complete) are the dispatcher's. */
@Composable
private fun TaskActions(
    status: String,
    isBusy: Boolean,
    onMove: (String, String?) -> Unit,
    onStopWorker: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        when (status) {
            "review" -> {
                Button(onClick = { onMove("done", null) }, enabled = !isBusy) { Text(stringResource(R.string.approve)) }
                OutlinedButton(onClick = { onMove("ready", null) }, enabled = !isBusy) { Text(stringResource(R.string.send_back)) }
            }
            "blocked", "scheduled", "triage", "todo" ->
                Button(onClick = { onMove("ready", null) }, enabled = !isBusy) { Text(stringResource(R.string.make_ready)) }
            "ready", "running" -> {
                OutlinedButton(
                    onClick = { onMove("blocked", "Paused from the Hermes app") },
                    enabled = !isBusy,
                ) { Text(stringResource(R.string.pause)) }
                if (status == "running") {
                    OutlinedButton(onClick = onStopWorker, enabled = !isBusy) { Text(stringResource(R.string.stop_and_retry)) }
                }
            }
            "done" -> OutlinedButton(onClick = { onMove("archived", null) }, enabled = !isBusy) { Text(stringResource(R.string.archive)) }
        }
        TextButton(onClick = onDelete, enabled = !isBusy) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskSheet(
    assignees: List<String>,
    isBusy: Boolean,
    onCreate: (title: String, body: String, assignee: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // Null: no assignee — the server's decomposer splits it into tasks for the right profiles.
    var assignee by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            SheetHeader(stringResource(R.string.new_task), onBack = null)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.what_should_be_done)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.details_optional)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            SheetSection(stringResource(R.string.who_does_it))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                FilterChip(
                    selected = assignee == null,
                    onClick = { assignee = null },
                    label = { Text(stringResource(R.string.let_hermes_split_it)) },
                )
                assignees.forEach { name ->
                    FilterChip(selected = assignee == name, onClick = { assignee = name }, label = { Text("@$name") })
                }
            }
            if (assignee == null) {
                SheetHint(
                    stringResource(R.string.hermes_breaks_it_into_steps_and_gives)
                )
            }
            Button(
                onClick = { onCreate(title, body, assignee) },
                enabled = title.isNotBlank() && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.add_task))
            }
        }
    }
}

/** Nobody, or one of the profiles; the current one is marked. */
@Composable
private fun AssigneePicker(current: String?, assignees: List<String>, enabled: Boolean, onPick: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        FilterChip(
            selected = current == null,
            onClick = { if (current != null) onPick(null) },
            enabled = enabled,
            label = { Text(stringResource(R.string.nobody)) },
        )
        assignees.forEach { name ->
            FilterChip(
                selected = current == name,
                onClick = { if (current != name) onPick(name) },
                enabled = enabled,
                label = { Text("@$name") },
            )
        }
    }
}

/** One attempt: who ran it, how it ended, how long it took; an open one can be checked live. */
@Composable
private fun RunRow(run: KanbanRepository.Run, inspection: KanbanRepository.Inspection?, onInspect: () -> Unit) {
    val open = run.endedAt == null
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val seconds = ((run.endedAt ?: (System.currentTimeMillis() / 1000)) - run.startedAt).coerceAtLeast(0)
        val state = if (open) stringResource(R.string.running_2) else (run.outcome ?: run.status)
        Text(
            listOfNotNull("#${run.id}", run.profile?.let { "@$it" }, state, duration(seconds)).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (run.error.isNotBlank()) {
            Text(run.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (run.summary.isNotBlank()) {
            Text(run.summary, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onInspect) { Text(stringResource(R.string.check_worker)) }
                inspection?.let { Text(inspectionLine(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun inspectionLine(found: KanbanRepository.Inspection): String =
    if (!found.alive) {
        stringResource(R.string.not_running) + found.reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    } else {
        listOfNotNull(
            found.pid?.let { "PID $it" },
            found.cpuPercent?.let { "CPU ${"%.0f".format(it)}%" },
            found.memoryBytes?.let { "${it / (1024 * 1024)} MB" },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.alive) }
    }

@Composable
private fun duration(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.s, seconds)
    seconds < 3600 -> stringResource(R.string.m, seconds / 60)
    else -> stringResource(R.string.h_m, seconds / 3600, seconds % 3600 / 60)
}

/** The tail of the worker's output, in a fixed-width face; long lines scroll sideways. */
@Composable
private fun LogView(log: KanbanRepository.Log) {
    if (!log.exists) {
        SheetHint(stringResource(R.string.no_worker_has_started_on_this_task_yet))
        return
    }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(HxSpace.sm)) {
            if (log.truncated) {
                Text(
                    stringResource(R.string.showing_the_last_part_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                log.text.ifEmpty { stringResource(R.string.empty) },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
