package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.TasksViewModel

/**
 * Task Desk (میز کار) — delegation v1.
 *
 * Fire a task, leave, come back to the result. Rows are the gateway's LIVE
 * sessions (`session.active_list`); a running task keeps working server-side
 * with the phone away, completion lands as a notification (AgentEventObserver)
 * and the transcript is always one tap away in the chat screen.
 *
 * Depends ONLY on [TasksViewModel] — never on gateway or runtime.
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

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
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
        when {
            uiState.isLoading && uiState.tasks.isEmpty() -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.tasks.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.WorkOutline,
                    title = t("No live tasks", "تسک زنده‌ای نیست"),
                    caption = t(
                        "Hand the agent a job and walk away — it keeps running on the server and you get notified when it's done",
                        "کار رو به ایجنت بسپر و برو — روی سرور ادامه می‌ده و وقتی تموم شد خبرت می‌کنه",
                    ),
                    actionLabel = t("Start a task", "شروع یک تسک"),
                    onAction = { showNewTaskDialog = true },
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HxSpace.screen, end = HxSpace.screen,
                    top = HxSpace.sm, bottom = HxSpace.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                items(uiState.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onOpen = { onOpenInChat(task.id) },
                        onInterrupt = { viewModel.interrupt(task.id) },
                        onClose = { viewModel.close(task.id) },
                    )
                }
            }
        }

        if (showNewTaskDialog) {
            NewTaskDialog(
                isLaunching = uiState.isLaunching,
                onDismiss = { showNewTaskDialog = false },
                onLaunch = { title, prompt ->
                    viewModel.launchTask(title, prompt) { showNewTaskDialog = false }
                },
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: com.hermes.android.data.SessionRepository.TaskRow,
    onOpen: () -> Unit,
    onInterrupt: () -> Unit,
    onClose: () -> Unit,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.isRunning) {
                    StatusChip(
                        label = t("Running", "در حال اجرا"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    StatusChip(
                        label = t("Idle", "پایان‌یافته"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = listOf(
                        task.model,
                        t("${task.messageCount} messages", "${task.messageCount} پیام"),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = t("Open in chat", "باز کردن در چت"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (task.isRunning) {
                    IconButton(onClick = onInterrupt) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = t("Interrupt", "توقف"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("Close task", "بستن تسک"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(
    isLaunching: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (title: String, prompt: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

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
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(t("Title (optional)", "عنوان (اختیاری)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(t("What should the agent do?", "ایجنت چی کار کنه؟")) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLaunch(title, prompt) },
                enabled = !isLaunching && prompt.isNotBlank(),
            ) {
                Text(if (isLaunching) t("Starting…", "در حال شروع…") else t("Start", "شروع"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLaunching) {
                Text(t("Cancel", "انصراف"))
            }
        },
    )
}
