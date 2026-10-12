package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.android.data.KanbanRepository
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.Inventory2
import com.hermes.android.ui.viewmodel.KanbanViewModel

/** The columns a person reads, in the board's order (`plugin_api.py` BOARD_COLUMNS). */
private val COLUMNS = listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done")

@Composable
private fun columnLabel(column: String): String = when (column) {
    "triage" -> t("Triage", "تقسیم")
    "todo" -> t("To do", "در صف")
    "scheduled" -> t("Scheduled", "زمان‌بندی")
    "ready" -> t("Ready", "آماده")
    "running" -> t("Running", "در حال اجرا")
    "blocked" -> t("Blocked", "متوقف")
    "review" -> t("Review", "بازبینی")
    "done" -> t("Done", "انجام‌شده")
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
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Kanban", "کانبان"),
        subtitle = t("Tasks your profiles work on", "کارهایی که پروفایل‌ها انجام می‌دهند"),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.available) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.loadAssignees()
                        creating = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(t("New task", "کار جدید")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.available) {
                HermesEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = t("Kanban is off", "کانبان خاموش است"),
                    caption = t(
                        "Turn it on in Settings › General. Termux has no Kanban: use the built-in Linux or a server.",
                        "از تنظیمات › عمومی روشنش کن. ترموکس کانبان ندارد: از لینوکس داخلی یا سرور استفاده کن.",
                    ),
                )
                return@Column
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                COLUMNS.forEach { column ->
                    val count = state.columns[column]?.size ?: 0
                    FilterChip(
                        selected = column == state.column,
                        onClick = { viewModel.selectColumn(column) },
                        label = { Text(if (count > 0) "${columnLabel(column)} · $count" else columnLabel(column)) },
                    )
                }
            }
            val tasks = state.columns[state.column].orEmpty()
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                tasks.isEmpty() -> Text(
                    t("Nothing here.", "این‌جا خالی است."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(HxSpace.screen),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
                ) {
                    items(tasks, key = { it.id }) { task -> TaskCard(task) { viewModel.open(task) } }
                }
            }
        }
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

@Composable
private fun TaskCard(task: KanbanRepository.Task, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(HxSpace.md), verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                task.assignee?.let { "@$it" } ?: t("unassigned", "بدون مسئول"),
                task.progress?.let { (done, total) -> "$done/$total" },
                task.commentCount.takeIf { it > 0 }?.let { t("$it comments", "$it نظر") },
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
) {
    val task = detail.task
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
                    "${columnLabel(task.status)} · " + (task.assignee?.let { "@$it" } ?: t("unassigned", "بدون مسئول")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.body.isNotBlank()) item { HermesMarkdown(markdown = task.body) }
            if (task.lastError.isNotBlank()) {
                item { Text(task.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            val report = task.summary.ifBlank { task.result }
            if (report.isNotBlank()) {
                item { SheetSection(t("Worker's report", "گزارش کارگزار")) }
                item { HermesMarkdown(markdown = report) }
            }
            item {
                TaskActions(task.status, isBusy, onMove, onStopWorker = { confirmReclaim = true }) { confirmDelete = true }
            }
            if (task.status != "done" && task.status != "archived") {
                item {
                    TextButton(
                        onClick = {
                            onLoadAssignees()
                            reassigning = !reassigning
                        },
                        enabled = !isBusy,
                    ) { Text(t("Change who does it", "تغییر مسئول")) }
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
                                t(
                                    "The current worker is stopped first, then the new profile starts over.",
                                    "اول کارگزار فعلی متوقف می‌شود، بعد پروفایل جدید از اول شروع می‌کند.",
                                )
                            )
                        }
                    }
                }
            }
            if (detail.runs.isNotEmpty()) {
                item { HorizontalDivider() }
                item { SheetSection(t("Attempts", "تلاش‌ها")) }
                items(detail.runs.reversed(), key = { "run-${it.id}" }) { run ->
                    RunRow(run, inspections[run.id], onInspect = { onInspect(run.id) })
                }
            }
            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("Worker log", "لاگ کارگزار"),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLoadLog, enabled = !isLogLoading) {
                        Text(if (log == null) t("Show", "نمایش") else t("Refresh", "تازه‌سازی"))
                    }
                }
            }
            if (isLogLoading && log == null) item { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            log?.let { item { LogView(it) } }
            item { HorizontalDivider() }
            item { SheetSection(t("Comments", "نظرها")) }
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
                        placeholder = { Text(t("Add a comment for the worker", "نظری برای کارگزار بنویس")) },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onComment(comment) { comment = "" } }, enabled = comment.isNotBlank() && !isBusy) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = t("Send", "ارسال"))
                    }
                }
            }
        }
    }
    if (confirmReclaim) {
        AlertDialog(
            onDismissRequest = { confirmReclaim = false },
            title = { Text(t("Stop the worker?", "کارگزار متوقف شود؟")) },
            text = {
                Text(
                    t(
                        "The worker process is ended and the task goes back to the queue, so it starts over.",
                        "پردازش کارگزار بسته می‌شود و کار به صف برمی‌گردد تا از اول شروع شود.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReclaim = false
                    onReclaim()
                }) { Text(t("Stop", "توقف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReclaim = false }) { Text(t("Cancel", "لغو")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Delete this task?", "این کار حذف شود؟")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel", "لغو")) } },
        )
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
                Button(onClick = { onMove("done", null) }, enabled = !isBusy) { Text(t("Approve", "تأیید")) }
                OutlinedButton(onClick = { onMove("ready", null) }, enabled = !isBusy) { Text(t("Send back", "برگرداندن")) }
            }
            "blocked", "scheduled", "triage", "todo" ->
                Button(onClick = { onMove("ready", null) }, enabled = !isBusy) { Text(t("Make ready", "آماده کن")) }
            "ready", "running" -> {
                OutlinedButton(
                    onClick = { onMove("blocked", "Paused from the Hermes app") },
                    enabled = !isBusy,
                ) { Text(t("Pause", "توقف")) }
                if (status == "running") {
                    OutlinedButton(onClick = onStopWorker, enabled = !isBusy) { Text(t("Stop and retry", "توقف و شروع دوباره")) }
                }
            }
            "done" -> OutlinedButton(onClick = { onMove("archived", null) }, enabled = !isBusy) { Text(t("Archive", "بایگانی")) }
        }
        TextButton(onClick = onDelete, enabled = !isBusy) { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
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
            SheetHeader(t("New task", "کار جدید"), onBack = null)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(t("What should be done?", "چه کاری انجام شود؟")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(t("Details (optional)", "جزئیات (اختیاری)")) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            SheetSection(t("Who does it", "چه کسی انجامش دهد"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                FilterChip(
                    selected = assignee == null,
                    onClick = { assignee = null },
                    label = { Text(t("Let Hermes split it", "هرمس تقسیمش کند")) },
                )
                assignees.forEach { name ->
                    FilterChip(selected = assignee == name, onClick = { assignee = name }, label = { Text("@$name") })
                }
            }
            if (assignee == null) {
                SheetHint(
                    t(
                        "Hermes breaks it into steps and gives each to the profile whose description fits.",
                        "هرمس آن را به چند کار می‌شکند و هر کدام را به پروفایلی می‌دهد که توضیحش می‌خورد.",
                    )
                )
            }
            Button(
                onClick = { onCreate(title, body, assignee) },
                enabled = title.isNotBlank() && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t("Add task", "افزودن کار"))
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
            label = { Text(t("Nobody", "هیچ‌کس")) },
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
        val state = if (open) t("running", "در حال اجرا") else (run.outcome ?: run.status)
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
                TextButton(onClick = onInspect) { Text(t("Check worker", "بررسی کارگزار")) }
                inspection?.let { Text(inspectionLine(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun inspectionLine(found: KanbanRepository.Inspection): String =
    if (!found.alive) {
        t("Not running", "در حال اجرا نیست") + found.reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    } else {
        listOfNotNull(
            found.pid?.let { "PID $it" },
            found.cpuPercent?.let { "CPU ${"%.0f".format(it)}%" },
            found.memoryBytes?.let { "${it / (1024 * 1024)} MB" },
        ).joinToString(" · ").ifEmpty { t("Alive", "زنده") }
    }

@Composable
private fun duration(seconds: Long): String = when {
    seconds < 60 -> t("${seconds}s", "${seconds} ثانیه")
    seconds < 3600 -> t("${seconds / 60}m", "${seconds / 60} دقیقه")
    else -> t("${seconds / 3600}h ${seconds % 3600 / 60}m", "${seconds / 3600} ساعت و ${seconds % 3600 / 60} دقیقه")
}

/** The tail of the worker's output, in a fixed-width face; long lines scroll sideways. */
@Composable
private fun LogView(log: KanbanRepository.Log) {
    if (!log.exists) {
        SheetHint(t("No worker has started on this task yet.", "هنوز کارگزاری روی این کار شروع نکرده."))
        return
    }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(HxSpace.sm)) {
            if (log.truncated) {
                Text(
                    t("Showing the last part only.", "فقط بخش آخر نمایش داده می‌شود."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                log.text.ifEmpty { t("(empty)", "(خالی)") },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
