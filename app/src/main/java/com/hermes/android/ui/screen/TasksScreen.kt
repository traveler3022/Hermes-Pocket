package com.hermes.android.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.SessionRepository
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.TasksViewModel

/**
 * Task Desk (میز کار) — delegation.
 *
 * Fire a task, leave, come back to the result. Two tabs: LIVE (running now,
 * from session.active_list) and HISTORY (finished, from session.list filtered
 * to this app's launches). Tapping a task opens its result inline; no need to
 * leave for the chat screen unless you want to continue it.
 *
 * Depends ONLY on [TasksViewModel].
 */
@Composable
fun TasksScreen(
    onNavigateBack: () -> Unit = {},
    onOpenInChat: (String) -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewTaskDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(showNewTaskDialog) {
        if (showNewTaskDialog) viewModel.loadModels()
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.loadHistory()
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Task Desk", "میز کار"),
        subtitle = uiState.tasks.count { it.isRunning }.takeIf { it > 0 }?.let { running ->
            t("$running running", "$running در حال اجرا")
        },
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTaskDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(t("New task", "تسک جدید")) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(t("Live", "زنده")) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(t("History", "تاریخچه")) },
                )
            }

            when (selectedTab) {
                0 -> LiveTab(
                    uiState = uiState,
                    onOpenResult = { viewModel.openResult(it.id, it.title) },
                    onOpenInChat = { onOpenInChat(it.id) },
                    onInterrupt = { viewModel.interrupt(it.id) },
                    onClose = { viewModel.close(it.id) },
                    onStart = { showNewTaskDialog = true },
                )
                else -> HistoryTab(
                    uiState = uiState,
                    onOpenResult = { viewModel.openResult(it.id, it.title) },
                    onOpenInChat = { onOpenInChat(it.id) },
                )
            }
        }

        if (showNewTaskDialog) {
            NewTaskDialog(
                isLaunching = uiState.isLaunching,
                models = uiState.models,
                onDismiss = { showNewTaskDialog = false },
                onLaunch = { title, prompt, effort, model ->
                    viewModel.launchTask(title, prompt, effort, model) { showNewTaskDialog = false }
                },
            )
        }

        uiState.openResult?.let { sheet ->
            ResultSheet(
                sheet = sheet,
                isLoading = uiState.isLoadingResult,
                onDismiss = { viewModel.closeResult() },
                onOpenInChat = {
                    viewModel.closeResult()
                    onOpenInChat(sheet.sessionId)
                },
                onRerun = {
                    // Re-run: same first user prompt, fresh session.
                    val firstUser = sheet.entries.firstOrNull { it.role == "user" }?.text
                    if (!firstUser.isNullOrBlank()) {
                        viewModel.closeResult()
                        viewModel.launchTask(sheet.title, firstUser)
                    }
                },
            )
        }
    }
}

@Composable
private fun LiveTab(
    uiState: TasksViewModel.TasksUiState,
    onOpenResult: (SessionRepository.TaskRow) -> Unit,
    onOpenInChat: (SessionRepository.TaskRow) -> Unit,
    onInterrupt: (SessionRepository.TaskRow) -> Unit,
    onClose: (SessionRepository.TaskRow) -> Unit,
    onStart: () -> Unit,
) {
    when {
        uiState.isLoading && uiState.tasks.isEmpty() -> LoadingBox()
        uiState.tasks.isEmpty() -> HermesEmptyState(
            icon = Icons.Default.WorkOutline,
            title = t("No live tasks", "تسک زنده‌ای نیست"),
            caption = t(
                "Hand the agent a job and walk away — it keeps running on the server and you get notified when it's done",
                "کار رو به ایجنت بسپر و برو — روی سرور ادامه می‌ده و وقتی تموم شد خبرت می‌کنه",
            ),
            actionLabel = t("Start a task", "شروع یک تسک"),
            onAction = onStart,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(HxSpace.screen, HxSpace.sm, HxSpace.screen, HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            items(uiState.tasks, key = { it.id }) { task ->
                TaskRow(task, onOpen = { onOpenResult(task) }, onInterrupt = { onInterrupt(task) },
                    onClose = { onClose(task) }, onOpenInChat = { onOpenInChat(task) })
            }
        }
    }
}

@Composable
private fun HistoryTab(
    uiState: TasksViewModel.TasksUiState,
    onOpenResult: (SessionRepository.TaskHistoryRow) -> Unit,
    onOpenInChat: (SessionRepository.TaskHistoryRow) -> Unit,
) {
    when {
        uiState.isLoadingHistory && uiState.history.isEmpty() -> LoadingBox()
        uiState.history.isEmpty() -> HermesEmptyState(
            icon = Icons.Default.WorkOutline,
            title = t("No finished tasks yet", "هنوز تسک تمام‌شده‌ای نیست"),
            caption = t(
                "Tasks you delegate show up here once they finish",
                "تسک‌هایی که واگذار می‌کنی بعد از پایان اینجا میان",
            ),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(HxSpace.screen, HxSpace.sm, HxSpace.screen, HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            items(uiState.history, key = { it.id }) { row ->
                Surface(
                    shape = RoundedCornerShape(HxRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(HxSpace.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onOpenResult(row) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = t("View result", "دیدن نتیجه"))
                            }
                            IconButton(onClick = { onOpenInChat(row) }) {
                                Icon(Icons.Default.Refresh, contentDescription = t("Continue in chat", "ادامه در چت"))
                            }
                        }
                        if (row.preview.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                row.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun TaskRow(
    task: SessionRepository.TaskRow,
    onOpen: () -> Unit,
    onInterrupt: () -> Unit,
    onClose: () -> Unit,
    onOpenInChat: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(HxSpace.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.isRunning) {
                    StatusChip(t("Running", "در حال اجرا"), MaterialTheme.colorScheme.primary)
                } else {
                    StatusChip(t("Idle", "پایان‌یافته"), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (task.preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = listOf(task.model, t("${task.messageCount} messages", "${task.messageCount} پیام"))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpen) {
                    Icon(Icons.Default.PlayArrow, contentDescription = t("View result", "دیدن نتیجه"),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenInChat) {
                    Icon(Icons.Default.Refresh, contentDescription = t("Open in chat", "باز کردن در چت"))
                }
                if (task.isRunning) {
                    IconButton(onClick = onInterrupt) {
                        Icon(Icons.Default.Stop, contentDescription = t("Interrupt", "توقف"),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = t("Close task", "بستن تسک"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(
    sheet: TasksViewModel.ResultSheet,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenInChat: () -> Unit,
    onRerun: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen)
                .padding(bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Text(sheet.title, style = MaterialTheme.typography.titleMedium)
            if (isLoading) {
                LoadingBox()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
                ) {
                    items(sheet.entries) { entry ->
                        val isUser = entry.role == "user"
                        Text(
                            text = (if (isUser) "🧑 " else "🤖 ") + entry.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    TextButton(onClick = onRerun) { Text(t("Re-run", "اجرای مجدد")) }
                    TextButton(onClick = onOpenInChat) { Text(t("Continue in chat", "ادامه در چت")) }
                }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(
    isLaunching: Boolean,
    models: List<SessionRepository.ModelChoice>,
    onDismiss: () -> Unit,
    onLaunch: (title: String, prompt: String, effort: String?, model: SessionRepository.ModelChoice?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    // "" = use the default effort; otherwise a per-task override.
    var effort by remember { mutableStateOf("") }
    // null = use the default model; otherwise a per-task override.
    var model by remember { mutableStateOf<SessionRepository.ModelChoice?>(null) }
    val effortOptions = listOf("", "low", "medium", "high", "xhigh")

    AlertDialog(
        onDismissRequest = { if (!isLaunching) onDismiss() },
        title = { Text(t("New task", "تسک جدید")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Text(
                    t(
                        "Runs in its own session on the server — you can close the app.",
                        "توی یک سشن مستقل روی سرور اجرا میشه — می‌تونی اپ رو ببندی.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(t("Title (optional)", "عنوان (اختیاری)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text(t("What should the agent do?", "ایجنت چی کار کنه؟")) },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    t("Thinking depth", "عمق تفکر"),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    effortOptions.forEach { opt ->
                        FilterChip(
                            selected = effort == opt,
                            onClick = { effort = opt },
                            label = {
                                Text(
                                    if (opt.isEmpty()) t("default", "پیش‌فرض") else opt,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                if (models.isNotEmpty()) {
                    Text(t("Model", "مدل"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = model == null,
                            onClick = { model = null },
                            label = { Text(t("default", "پیش‌فرض"), style = MaterialTheme.typography.labelSmall) },
                        )
                        models.forEach { choice ->
                            FilterChip(
                                selected = model == choice,
                                onClick = { model = choice },
                                label = { Text(choice.modelId, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLaunch(title, prompt, effort.ifEmpty { null }, model) },
                enabled = !isLaunching && prompt.isNotBlank(),
            ) { Text(if (isLaunching) t("Starting…", "در حال شروع…") else t("Start", "شروع")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLaunching) { Text(t("Cancel", "انصراف")) }
        },
    )
}
