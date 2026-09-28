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
    // Deleting asked nothing: one tap beside Edit and the job was gone for good.
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Delete job?", "کار حذف شود؟")) },
            text = { Text(t("\"${job.name}\" will stop running and be removed.", "«${job.name}» دیگر اجرا نمی‌شود و حذف می‌شود.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteJob(job.id)
                }) { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel", "انصراف")) }
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
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
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
                    t("daily at $time", "هر روز $time")
                } else {
                    t(
                        "${names.joinToString(", ")} at $time",
                        "${names.joinToString("، ")} ساعت $time",
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
    t("Sat", "شنبه"),
    t("Sun", "یک"),
    t("Mon", "دو"),
    t("Tue", "سه"),
    t("Wed", "چهار"),
    t("Thu", "پنج"),
    t("Fri", "جمعه"),
)

/** Five-minute steps: the interesting minutes of an hour, without 60 chips to scroll. */
@Composable
private fun MinuteChips(selected: Int, onPick: (Int) -> Unit) {
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
        title = { Text(if (isEdit) t("Edit Cron Job", "ویرایش کار") else t("Create Cron Job", "ساخت کار جدید")) },
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
                                t("☀️ Morning digest", "☀️ گزارش صبحگاهی"),
                                "morning-digest" to "0 8 * * *",
                                t(
                                    "Good morning! Give me a short digest: server health and disk space, " +
                                        "anything notable in logs since yesterday, unfinished tasks from our " +
                                        "recent sessions, and 3 suggestions for today. Keep it brief.",
                                    "صبح بخیر! یک گزارش کوتاه بده: وضعیت سرور و فضای دیسک، " +
                                        "نکات مهم لاگ‌ها از دیروز تا الان، کارهای نیمه‌تمام سشن‌های اخیر، " +
                                        "و ۳ پیشنهاد برای امروز. خلاصه بنویس.",
                                ),
                            ),
                            Triple(
                                t("🩺 Nightly check", "🩺 چک شبانه"),
                                "nightly-check" to "0 23 * * *",
                                t(
                                    "Run a quick health check of the server (services, disk, errors in logs) " +
                                        "and summarize anything that needs my attention.",
                                    "یک بررسی سریع سلامت سرور انجام بده (سرویس‌ها، دیسک، خطاهای لاگ) " +
                                        "و هرچیزی که نیاز به توجه من داره رو خلاصه کن.",
                                ),
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
                    label = { Text(t("Name", "نام")) },
                    singleLine = true,
                )
                Text(
                    text = t("When should it run?", "کی اجرا شود؟"),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        ScheduleMode.DAILY to t("Every day", "هر روز"),
                        ScheduleMode.DAYS to t("Certain days", "روزهای خاص"),
                        ScheduleMode.HOURLY to t("Every hour", "هر ساعت"),
                        ScheduleMode.MINUTES to t("Every few minutes", "هر چند دقیقه"),
                        ScheduleMode.CUSTOM to t("Custom", "سفارشی"),
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
                                text = t("Days", "روزها"),
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
                            text = t("Hour", "ساعت"),
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
                            text = t("Minute", "دقیقه"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        MinuteChips(selected = pick.minute) { pick = pick.copy(minute = it) }
                    }
                    ScheduleMode.HOURLY -> {
                        Text(
                            text = t("At which minute past the hour", "در دقیقه‌ی چندم هر ساعت"),
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
                                            t("every $n min", "هر $n دقیقه"),
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
                            label = { Text(t("Cron expression", "عبارت cron")) },
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
                            val jobId = existingJob?.id; if (jobId != null) viewModel.updateJob(jobId, name, schedule, prompt)
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
