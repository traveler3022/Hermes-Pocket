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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
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
import com.hermes.android.ui.viewmodel.CronJob
import com.hermes.android.ui.viewmodel.CronViewModel

/**
 * Cron Scheduler screen — list, create, edit, pause/resume, delete jobs.
 * Rebuilt on the design system; also the first fully bilingual version of
 * this screen (the old one was hardcoded English throughout).
 *
 * Depends ONLY on [CronViewModel] — never on gateway or runtime.
 */
@Composable
fun CronScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CronViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Scheduled Jobs", "کارهای زمان‌بندی‌شده"),
        subtitle = if (uiState.jobs.isEmpty()) null else {
            t(
                "${uiState.jobs.count { it.enabled }} of ${uiState.jobs.size} active",
                "${uiState.jobs.count { it.enabled }} از ${uiState.jobs.size} فعال",
            )
        },
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            // Mockup-H shape: the create action is a FAB, not a top-bar icon.
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(t("New job", "کار جدید")) },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
                Spacer(Modifier.height(HxSpace.sm))
                Text(
                    t("Loading cron jobs…", "در حال بارگذاری کارها…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.jobs.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Schedule,
                    title = t("No scheduled jobs", "هنوز کاری زمان‌بندی نشده"),
                    caption = t(
                        "Run prompts automatically on a schedule — daily reports, backups, checks",
                        "پرامپت‌ها رو خودکار و زمان‌بندی‌شده اجرا کن — گزارش روزانه، بکاپ، بررسی‌ها",
                    ),
                    actionLabel = t("Create a job", "ساخت کار جدید"),
                    onAction = { viewModel.showCreateDialog() },
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
                items(uiState.jobs, key = { it.id }) { job ->
                    CronJobRow(job, viewModel)
                }
            }
        }

        if (uiState.showCreateDialog) {
            CreateJobDialog(viewModel)
        }
        uiState.editingJob?.let { job ->
            CreateJobDialog(viewModel, existingJob = job)
        }
    }
}

@Composable
private fun CronJobRow(job: CronJob, viewModel: CronViewModel) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(HxSpace.inner)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Cron expressions are write-only for most people — show
                    // the schedule in words first, raw expression next to it
                    // (approved design H).
                    val human = humanizeCron(job.schedule)
                    Text(
                        text = if (human != null) "${job.schedule} — $human" else job.schedule,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = job.enabled,
                    onCheckedChange = { viewModel.toggleJob(job.id, it) },
                )
            }
            if (job.promptPreview.isNotBlank()) {
                Text(
                    text = job.promptPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = HxSpace.xs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HxSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                StatusChip(
                    label = if (job.enabled) t("active", "فعال") else t("paused", "متوقف"),
                    color = if (job.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Last run result — failures must be visible at a glance
                // (approved design H); the field was fetched but never shown.
                job.lastStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    val failed = status.lowercase().let {
                        it.contains("error") || it.contains("fail")
                    }
                    StatusChip(
                        label = if (failed) t("last: failed", "آخرین: خطا")
                            else t("last: ok", "آخرین: موفق"),
                        color = if (failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    )
                }
                job.nextRunAt?.let {
                    Text(
                        text = t("next: $it", "بعدی: $it"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.startEditJob(job) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = t("Edit", "ویرایش"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButton(onClick = { viewModel.deleteJob(job.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = t("Delete", "حذف"),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

/**
 * Best-effort plain-language rendering of the common cron shapes — hourly,
 * every-N-minutes, daily, weekdays, weekly. Anything fancier returns null
 * and the raw expression stands alone.
 */
@Composable
private fun humanizeCron(schedule: String): String? {
    val parts = schedule.trim().split(Regex("\\s+"))
    if (parts.size != 5) return null
    val (min, hour, dom, month, dow) = parts
    fun clock(): String? {
        val h = hour.toIntOrNull() ?: return null
        val m = min.toIntOrNull() ?: return null
        return "%02d:%02d".format(h, m)
    }
    return when {
        min.startsWith("*/") && hour == "*" && dom == "*" && month == "*" && dow == "*" ->
            min.removePrefix("*/").toIntOrNull()?.let { n ->
                t("every $n min", "هر $n دقیقه")
            }
        min.toIntOrNull() != null && hour == "*" && dom == "*" && month == "*" && dow == "*" ->
            t("hourly", "هر ساعت")
        dom == "*" && month == "*" && dow == "*" ->
            clock()?.let { t("daily at $it", "هر روز $it") }
        dom == "*" && month == "*" && dow == "1-5" ->
            clock()?.let { t("weekdays at $it", "روزهای کاری $it") }
        dom == "*" && month == "*" && dow.toIntOrNull() != null ->
            clock()?.let { t("weekly at $it", "هفتگی $it") }
        else -> null
    }
}

/** Handles both create (existingJob = null) and edit (pre-filled; edit is a
 *  remove+add under the hood since cron.manage has no update verb, but the
 *  user just sees one form either way). */
@Composable
private fun CreateJobDialog(viewModel: CronViewModel, existingJob: CronJob? = null) {
    var name by remember(existingJob) { mutableStateOf(existingJob?.name ?: "") }
    var schedule by remember(existingJob) { mutableStateOf(existingJob?.schedule ?: "") }
    var prompt by remember(existingJob) { mutableStateOf(existingJob?.promptPreview ?: "") }
    val isEdit = existingJob != null
    val onDismiss = if (isEdit) viewModel::hideEditDialog else viewModel::hideCreateDialog

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) t("Edit Cron Job", "ویرایش کار") else t("Create Cron Job", "ساخت کار جدید")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("Name", "نام")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text(t("Schedule (cron expression)", "زمان‌بندی (عبارت cron)")) },
                    singleLine = true,
                    placeholder = { Text("0 9 * * *") },
                )
                // Common schedules one tap away (approved design H) — the
                // field stays editable for anything the presets don't cover.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        t("hourly", "هر ساعت") to "0 * * * *",
                        t("daily 9:00", "روزانه ۹") to "0 9 * * *",
                        t("weekdays 7:00", "روزهای کاری ۷") to "0 7 * * 1-5",
                        t("weekly", "هفتگی") to "0 9 * * 1",
                    ).forEach { (label, expr) ->
                        FilterChip(
                            selected = schedule == expr,
                            onClick = { schedule = expr },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                humanizeCron(schedule)?.let { human ->
                    Text(
                        text = human,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(t("Prompt", "پرامپت")) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && schedule.isNotBlank()) {
                        if (isEdit) {
                            viewModel.updateJob(existingJob!!.id, name, schedule, prompt)
                        } else {
                            viewModel.createJob(name, schedule, prompt)
                        }
                    }
                },
            ) { Text(if (isEdit) t("Save", "ذخیره") else t("Create", "ساخت")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "انصراف")) }
        },
    )
}
