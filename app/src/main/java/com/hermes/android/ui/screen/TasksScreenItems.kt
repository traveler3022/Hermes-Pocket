package com.hermes.android.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

@Composable
internal fun DelegationRow(
    delegation: SessionRepository.DelegationStatus,
    onTogglePaused: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
    ) {
        Row(
            modifier = Modifier.padding(HxSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (delegation.active.isEmpty()) {
                    t("No subagents running", "ساب‌ایجنتی در حال اجرا نیست")
                } else {
                    t(
                        "${delegation.active.size} subagent(s) running",
                        "${delegation.active.size} ساب‌ایجنت در حال اجرا",
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTogglePaused) {
                Text(
                    if (delegation.paused) t("Resume spawning", "ازسرگیری spawn")
                    else t("Pause spawning", "توقف spawn"),
                )
            }
        }
    }
}

@Composable
internal fun LoadingBox() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { CircularProgressIndicator() }
}

@Composable
internal fun TaskRow(
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
internal fun ResultSheet(
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
internal fun NewTaskDialog(
    isLaunching: Boolean,
    models: List<SessionRepository.ModelChoice>,
    onDismiss: () -> Unit,
    onLaunch: (title: String, prompt: String, effort: String?, model: SessionRepository.ModelChoice?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    // " " = use the default effort; otherwise a per-task override.
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

                // ── Plan builder ────────────────────────────────────────────
                // Instead of one textarea for the whole prompt, the user builds
                // a numbered list of steps. Only one step is "open" (being
                // edited) at a time; the rest are collapsed cards the user can
                // edit, delete, or reorder. The final prompt is assembled by
                // joining all steps with newlines and a "Step N:" prefix.
                var steps by remember { mutableStateOf(listOf("")) }
                var openStep by remember { mutableStateOf(0) } // index of the step being edited

                steps.forEachIndexed { index, stepText ->
                    if (index == openStep) {
                        // ── Open step: textarea + "Save & next" ──
                        OutlinedTextField(
                            value = stepText,
                            onValueChange = { newVal ->
                                steps = steps.toMutableList().also { it[index] = newVal }
                            },
                            label = { Text(t("Step ${index + 1}", "گام ${index + 1}")) },
                            minLines = 2, maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (steps.size > 1) {
                                TextButton(onClick = {
                                    // Remove this step, move openStep back
                                    steps = steps.toMutableList().also { it.removeAt(index) }
                                    openStep = (openStep - 1).coerceAtLeast(0)
                                }) { Text(t("Delete", "حذف")) }
                            }
                            TextButton(onClick = {
                                if (stepText.isNotBlank()) {
                                    steps = steps + ""
                                    openStep = steps.lastIndex
                                }
                            }) { Text(t("Save & next", "ذخیره و بعدی")) }
                        }
                    } else {
                        // ── Collapsed step: preview + edit/delete/reorder ──
                        Surface(
                            shape = RoundedCornerShape(HxRadius.sm),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = HxSpace.sm, vertical = HxSpace.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = HxSpace.xs),
                                )
                                Text(
                                    text = stepText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                // Move up
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            steps = steps.toMutableList().also {
                                                val tmp = it[index - 1]
                                                it[index - 1] = it[index]
                                                it[index] = tmp
                                            }
                                            if (openStep == index) openStep = index - 1
                                            else if (openStep == index - 1) openStep = index
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = t("Move up", "بالا"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                // Move down
                                IconButton(
                                    onClick = {
                                        if (index < steps.size - 1) {
                                            steps = steps.toMutableList().also {
                                                val tmp = it[index + 1]
                                                it[index + 1] = it[index]
                                                it[index] = tmp
                                            }
                                            if (openStep == index) openStep = index + 1
                                            else if (openStep == index + 1) openStep = index
                                        }
                                    },
                                    enabled = index < steps.size - 1,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = t("Move down", "پایین"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                // Edit
                                IconButton(
                                    onClick = { openStep = index },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = t("Edit", "ویرایش"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                // Delete
                                IconButton(
                                    onClick = {
                                        steps = steps.toMutableList().also { it.removeAt(index) }
                                        if (openStep > index) openStep--
                                        if (steps.isEmpty()) {
                                            steps = listOf("")
                                            openStep = 0
                                        } else if (openStep >= steps.size) {
                                            openStep = steps.lastIndex
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = t("Delete", "حذف"),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Assemble the final prompt from all steps — formatted as a
                // numbered plan so the agent understands the structure and
                // can use its todo tool to track each step.
                val prompt = steps
                    .filter { it.isNotBlank() }
                    .mapIndexed { i, step -> "${i + 1}. ${step.trim()}" }
                    .joinToString("\n")
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
                    var modelMode by remember { mutableStateOf(0) } // 0=default, 1=custom
                    var selectedProvider by remember { mutableStateOf<String?>(null) }
                    val providers = remember(models) {
                        models.map { it.provider }.distinct().sorted()
                    }

                    Text(t("Model", "مدل"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = modelMode == 0,
                            onClick = {
                                modelMode = 0
                                model = null
                                selectedProvider = null
                            },
                            label = { Text(t("Default", "پیش‌فرض"), style = MaterialTheme.typography.labelSmall) },
                        )
                        FilterChip(
                            selected = modelMode == 1,
                            onClick = {
                                modelMode = 1
                                model = null
                                selectedProvider = null
                            },
                            label = { Text(t("Custom", "کاستوم"), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    if (modelMode == 0) {
                        Text(
                            t(
                                "Uses the current provider and model from settings",
                                "از پرووایدر و مدل فعلی تنظیمات استفاده می‌کند",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // Step 1: pick provider
                        if (selectedProvider == null) {
                            Text(
                                t("Select provider", "پرووایدر را انتخاب کنید"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                providers.forEach { provider ->
                                    val count = models.count { it.provider == provider }
                                    FilterChip(
                                        selected = false,
                                        onClick = { selectedProvider = provider },
                                        label = {
                                            Column {
                                                Text(provider, style = MaterialTheme.typography.labelSmall)
                                                Text(
                                                    "$count model(s)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            // Step 2: pick model
                            val modelsForProvider = models.filter { it.provider == selectedProvider }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t("Select model", "مدل را انتخاب کنید"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                modelsForProvider.forEach { choice ->
                                    FilterChip(
                                        selected = model == choice,
                                        onClick = { model = choice },
                                        label = { Text(choice.modelId, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                            TextButton(onClick = { selectedProvider = null; model = null }) {
                                Text(
                                    t("← Change provider", "← تغییر پرووایدر"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
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
