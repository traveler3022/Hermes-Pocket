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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AutoAwesome
import com.hermes.android.ui.icons.filled.Schedule
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
    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    HermesScaffold(
        title = stringResource(R.string.scheduled_jobs),
        subtitle = if (uiState.jobs.isEmpty()) null else {
            stringResource(R.string.of_active, uiState.jobs.count { it.enabled }, uiState.jobs.size)
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.openBlueprints() }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.ready_made_jobs))
            }
        },
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            // Mockup-H shape: the create action is a FAB, not a top-bar icon.
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_job)) },
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
                    stringResource(R.string.loading_cron_jobs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.jobs.isEmpty() -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                HermesEmptyState(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.no_scheduled_jobs),
                    caption = stringResource(R.string.run_prompts_automatically_on_a_schedule),
                    actionLabel = stringResource(R.string.create_a_job),
                    onAction = { viewModel.showCreateDialog() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.openBlueprints() },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = HxSpace.xl),
                ) { Text(stringResource(R.string.or_start_from_a_ready_made_job)) }
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
                    CronJobRow(job, viewModel, uiState.runningJobIds)
                }
            }
        }

        if (uiState.showCreateDialog) {
            CreateJobDialog(viewModel)
        }
        uiState.editingJob?.let { job ->
            CreateJobDialog(viewModel, existingJob = job)
        }
        if (uiState.showBlueprints) {
            BlueprintSheet(
                state = uiState,
                onPick = { viewModel.pickBlueprint(it) },
                onRetry = viewModel::loadBlueprints,
                onDismiss = viewModel::closeBlueprints,
            )
        }
        uiState.activeBlueprint?.let { blueprint ->
            BlueprintForm(
                blueprint = blueprint,
                creating = uiState.creatingFromBlueprint,
                error = uiState.blueprintError,
                onCreate = { values -> viewModel.createFromBlueprint(blueprint.key, values) },
                onDismiss = { viewModel.pickBlueprint(null) },
            )
        }
    }
}

@Composable
private fun CronJobRow(job: CronJob, viewModel: CronViewModel, runningJobIds: Set<String>) {
    // Deleting asked nothing: one tap beside Edit and the job was gone for good.
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_job)) },
            text = { Text(stringResource(R.string.will_stop_running_and_be_removed, job.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteJob(job.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }
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
                    label = if (job.enabled) stringResource(R.string.active_2) else stringResource(R.string.paused),
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
                        label = if (failed) stringResource(R.string.last_failed)
                            else stringResource(R.string.last_ok),
                        color = if (failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    )
                }
                job.nextRunAt?.let {
                    Text(
                        text = stringResource(R.string.next, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                val running = job.id in runningJobIds
                IconButton(
                    onClick = { viewModel.runJobNow(job.id) },
                    enabled = !running,
                    modifier = Modifier.size(32.dp),
                ) {
                    if (running) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.run_now),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                IconButton(onClick = { viewModel.startEditJob(job) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
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
                stringResource(R.string.every_min, n)
            }
        min.toIntOrNull() != null && hour == "*" && dom == "*" && month == "*" && dow == "*" ->
            stringResource(R.string.hourly)
        dom == "*" && month == "*" && dow == "*" ->
            clock()?.let { stringResource(R.string.daily_at, it) }
        dom == "*" && month == "*" -> {
            // Any set of days, not just the two shapes this used to know: a job on
            // Saturday and Tuesday read as a bare "0 9 * * 6,2" in the list, which is
            // the same unreadable thing the create form used to ask for.
            val days = expandDow(dow)
            val time = clock()
            if (days == null || time == null) {
                null
            } else {
                val labels = weekNames()
                val names = WEEK_ORDER.withIndex()
                    .filter { it.value in days }
                    .map { labels[it.index] }
                if (names.size == 7) {
                    stringResource(R.string.daily_at, time)
                } else {
                    stringResource(
                        R.string.days_at_time,
                        names.joinToString(stringResource(R.string.list_separator)),
                        time,
                    )
                }
            }
        }
        else -> null
    }
}

/**
 * A schedule in the shape somebody actually thinks about one: a time of day, some days
 * of the week, or an interval — never five space-separated fields.
 *
 * Cron expressions are a fine storage format and a terrible thing to ask a person for.
 * The dialog used to hand over an empty box with `0 9 * * *` as a placeholder, which is
 * not a hint unless you already know the answer. This type is what the picker edits;
 * [toCron] is the only place the expression gets written, and [parseCron] reads one back
 * so editing an existing job opens on the same controls that would have created it.
 */
private enum class ScheduleMode { DAILY, DAYS, HOURLY, MINUTES, CUSTOM }

private data class SchedulePick(
    val mode: ScheduleMode,
    val hour: Int = 9,
    val minute: Int = 0,
    /** Cron day-of-week numbers, 0 = Sunday. Empty behaves as every day. */
    val days: Set<Int> = emptySet(),
    val everyN: Int = 15,
    /** Whatever was typed in the custom box, kept so switching modes does not lose it. */
    val raw: String = "",
)

private fun SchedulePick.toCron(): String = when (mode) {
    ScheduleMode.DAILY -> "$minute $hour * * *"
    ScheduleMode.DAYS ->
        if (days.isEmpty()) "$minute $hour * * *"
        else "$minute $hour * * ${days.sorted().joinToString(",")}"
    ScheduleMode.HOURLY -> "$minute * * * *"
    ScheduleMode.MINUTES -> "*/$everyN * * * *"
    ScheduleMode.CUSTOM -> raw
}

/**
 * Best-effort read of an existing expression. Anything the picker cannot represent
 * honestly — a day of the month, a specific month, a step in an odd field — opens in
 * Custom with the text intact, rather than being silently rounded to something else.
 */
private fun parseCron(schedule: String?): SchedulePick {
    val raw = schedule?.trim().orEmpty()
    if (raw.isEmpty()) return SchedulePick(ScheduleMode.DAILY)
    val parts = raw.split(Regex("\\s+"))
    if (parts.size != 5) return SchedulePick(ScheduleMode.CUSTOM, raw = raw)
    val (min, hour, dom, month, dow) = parts
    val step = min.removePrefix("*/").toIntOrNull()
    if (min.startsWith("*/") && step != null && hour == "*" && dom == "*" && month == "*" && dow == "*") {
        return SchedulePick(ScheduleMode.MINUTES, everyN = step, raw = raw)
    }
    val m = min.toIntOrNull() ?: return SchedulePick(ScheduleMode.CUSTOM, raw = raw)
    if (hour == "*" && dom == "*" && month == "*" && dow == "*") {
        return SchedulePick(ScheduleMode.HOURLY, minute = m, raw = raw)
    }
    val h = hour.toIntOrNull() ?: return SchedulePick(ScheduleMode.CUSTOM, raw = raw)
    if (dom != "*" || month != "*") return SchedulePick(ScheduleMode.CUSTOM, raw = raw)
    if (dow == "*") return SchedulePick(ScheduleMode.DAILY, hour = h, minute = m, raw = raw)
    val days = expandDow(dow) ?: return SchedulePick(ScheduleMode.CUSTOM, raw = raw)
    return SchedulePick(ScheduleMode.DAYS, hour = h, minute = m, days = days, raw = raw)
}

/** "1-5" and "0,6" both mean a set of days; anything else is not ours to interpret. */
private fun expandDow(dow: String): Set<Int>? {
    val out = mutableSetOf<Int>()
    for (chunk in dow.split(",")) {
        if (chunk.contains("-")) {
            val ends = chunk.split("-")
            if (ends.size != 2) return null
            val from = ends[0].toIntOrNull() ?: return null
            val to = ends[1].toIntOrNull() ?: return null
            if (from > to || from < 0 || to > 7) return null
            (from..to).forEach { out.add(it % 7) }
        } else {
            val one = chunk.toIntOrNull() ?: return null
            if (one < 0 || one > 7) return null
            out.add(one % 7)
        }
    }
    return out.takeIf { it.isNotEmpty() }
}

/** Saturday first: this app is read in Persian, and the week starts where the reader's does. */
private val WEEK_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)

/**
 * Day names in [WEEK_ORDER] order, resolved in one call so ordinary code can index them.
 * A `map { dayLabel(it) }` would have to make a composable call per element, which is a
 * rule this file does not need to go near.
 */
@Composable
private fun weekNames(): List<String> = listOf(
    stringResource(R.string.sat),
    stringResource(R.string.sun),
    stringResource(R.string.mon),
    stringResource(R.string.tue),
    stringResource(R.string.wed),
    stringResource(R.string.thu),
    stringResource(R.string.fri),
)

/** Five-minute steps: the interesting minutes of an hour, without 60 chips to scroll. */
@Composable
internal fun MinuteChips(selected: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (0..55 step 5).forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onPick(m) },
                label = { Text("%02d".format(m), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/** Handles both create (existingJob = null) and edit (pre-filled; edit is a
 *  remove+add under the hood since cron.manage has no update verb, but the
 *  user just sees one form either way). */
@Composable
private fun CreateJobDialog(viewModel: CronViewModel, existingJob: CronJob? = null) {
    var name by remember(existingJob) { mutableStateOf(existingJob?.name ?: "") }
    var pick by remember(existingJob) { mutableStateOf(parseCron(existingJob?.schedule)) }
    var prompt by remember(existingJob) { mutableStateOf(existingJob?.let { it.fullPrompt ?: it.promptPreview } ?: "") }
    val schedule = pick.toCron()
    val isEdit = existingJob != null
    val onDismiss = if (isEdit) viewModel::hideEditDialog else viewModel::hideCreateDialog

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) stringResource(R.string.edit_cron_job) else stringResource(R.string.create_cron_job)) },
        text = {
            // The picker is taller than the one field it replaced, and an AlertDialog
            // clips rather than scrolls; on a short screen the Create button used to be
            // the thing that vanished.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                if (!isEdit) {
                    // One-tap routine templates (delegation v1). The morning
                    // digest is the flagship proactive routine: the agent
                    // opens the day with a report instead of waiting to be
                    // asked. Fields stay editable after applying.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            Triple(
                                stringResource(R.string.morning_digest),
                                "morning-digest" to "0 8 * * *",
                                stringResource(R.string.good_morning_give_me_a_short_digest),
                            ),
                            Triple(
                                stringResource(R.string.nightly_check),
                                "nightly-check" to "0 23 * * *",
                                stringResource(R.string.run_a_quick_health_check_of_the_server),
                            ),
                        ).forEach { (label, idAndSchedule, promptText) ->
                            FilterChip(
                                selected = name == idAndSchedule.first,
                                onClick = {
                                    name = idAndSchedule.first
                                    pick = parseCron(idAndSchedule.second)
                                    prompt = promptText
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.when_should_it_run),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        ScheduleMode.DAILY to stringResource(R.string.every_day),
                        ScheduleMode.DAYS to stringResource(R.string.certain_days),
                        ScheduleMode.HOURLY to stringResource(R.string.every_hour),
                        ScheduleMode.MINUTES to stringResource(R.string.every_few_minutes),
                        ScheduleMode.CUSTOM to stringResource(R.string.custom),
                    ).forEach { (candidate, label) ->
                        FilterChip(
                            selected = pick.mode == candidate,
                            onClick = {
                                // Entering Custom starts from whatever was already picked,
                                // so it is an escape hatch to edit an expression rather
                                // than an empty box to invent one in.
                                pick = if (candidate == ScheduleMode.CUSTOM && pick.raw.isBlank()) {
                                    pick.copy(mode = candidate, raw = pick.toCron())
                                } else {
                                    pick.copy(mode = candidate)
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                // Only the controls this mode actually needs. A minute box next to
                // "every day at 9" is a question with no answer, and the old single
                // cron field was five of those at once.
                when (pick.mode) {
                    ScheduleMode.DAILY, ScheduleMode.DAYS -> {
                        if (pick.mode == ScheduleMode.DAYS) {
                            Text(
                                text = stringResource(R.string.days),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val labels = weekNames()
                                WEEK_ORDER.forEachIndexed { index, dow ->
                                    FilterChip(
                                        selected = dow in pick.days,
                                        onClick = {
                                            pick = pick.copy(
                                                days = if (dow in pick.days) pick.days - dow else pick.days + dow,
                                            )
                                        },
                                        label = {
                                            Text(labels[index], style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            (0..23).forEach { h ->
                                FilterChip(
                                    selected = pick.hour == h,
                                    onClick = { pick = pick.copy(hour = h) },
                                    label = { Text("%02d".format(h), style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.minute),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        MinuteChips(selected = pick.minute) { pick = pick.copy(minute = it) }
                    }
                    ScheduleMode.HOURLY -> {
                        Text(
                            text = stringResource(R.string.at_which_minute_past_the_hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        MinuteChips(selected = pick.minute) { pick = pick.copy(minute = it) }
                    }
                    ScheduleMode.MINUTES -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(5, 10, 15, 30).forEach { n ->
                                FilterChip(
                                    selected = pick.everyN == n,
                                    onClick = { pick = pick.copy(everyN = n) },
                                    label = {
                                        Text(
                                            stringResource(R.string.every_min, n),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    ScheduleMode.CUSTOM -> {
                        OutlinedTextField(
                            value = pick.raw,
                            onValueChange = { pick = pick.copy(raw = it) },
                            label = { Text(stringResource(R.string.cron_expression)) },
                            singleLine = true,
                            placeholder = { Text("0 9 * * *") },
                        )
                    }
                }

                // The sentence is the answer; the expression under it is only there so
                // anyone who does read cron can confirm the picker meant what they meant.
                humanizeCron(schedule)?.let { human ->
                    Text(
                        text = human,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = schedule.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.prompt)) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && schedule.isNotBlank()) {
                        if (isEdit) {
                            val jobId = existingJob?.id; if (jobId != null) viewModel.updateJob(jobId, name, schedule, prompt)
                        } else {
                            viewModel.createJob(name, schedule, prompt)
                        }
                    }
                },
            ) { Text(if (isEdit) stringResource(R.string.save) else stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_2)) }
        },
    )
}
