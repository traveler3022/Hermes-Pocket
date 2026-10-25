package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.analytics.UsageAnalytics.AuxiliaryUse
import com.hermes.android.domain.analytics.UsageAnalytics.Counted
import com.hermes.android.domain.analytics.UsageAnalytics.Day
import com.hermes.android.domain.analytics.UsageAnalytics.ModelUse
import com.hermes.android.domain.analytics.UsageAnalytics.Report
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxBarPart
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.HxSplitBar
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.design.hxCompactCount
import com.hermes.android.ui.viewmodel.AnalyticsUiState
import com.hermes.android.ui.viewmodel.AnalyticsViewModel
import java.util.Locale

private enum class AnalyticsTab { MODELS, SKILLS, TOOLS }

/**
 * Tokens and cost of the server over 7, 30 or 90 days: the totals, a cost per day, and what each
 * model (with the provider that billed it), skill and tool used. Lives in Settings; the chat
 * screen shows none of it.
 */
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.MODELS) }

    HermesScaffold(
        title = stringResource(R.string.analytics_title),
        subtitle = stringResource(R.string.analytics_subtitle),
        onBack = onNavigateBack,
        actions = {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = HxSpace.screen, end = HxSpace.screen,
                top = HxSpace.sm, bottom = HxSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            item(key = "window") { WindowChips(state, viewModel::selectDays) }
            state.loadError?.let { error ->
                item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            val report = state.report
            if (report == null) {
                return@LazyColumn
            }
            // The report of the window still loading after a chip tap is the old one; it shows
            // under the new chip until the new one lands.
            val todayOnly = report.days == AnalyticsUiState.TODAY
            if (todayOnly) {
                item(key = "today") { TodayCard(report.today) }
            }
            if (report.totals.sessions == 0L && report.models.isEmpty()) {
                if (!todayOnly) item(key = "empty") { Note(stringResource(R.string.analytics_empty)) }
                return@LazyColumn
            }
            if (!todayOnly) {
                item(key = "totals") { Totals(report) }
                if (report.daily.isNotEmpty()) {
                    item(key = "daily") { DailyUsage(report.daily) }
                }
            }
            item(key = "tabs") { TabChips(tab) { tab = it } }
            if (todayOnly) {
                item(key = "last-24h") { Note(stringResource(R.string.analytics_last_24h)) }
            }
            when (tab) {
                AnalyticsTab.MODELS -> {
                    if (report.models.isEmpty()) {
                        item(key = "no-models") { Note(stringResource(R.string.analytics_no_models)) }
                    }
                    // Indexed: the server can send two rows for one model/provider pair, and equal keys crash the list.
                    itemsIndexed(report.models, key = { i, m -> "model:$i:${m.provider}/${m.model}" }) { _, m -> ModelRow(m) }
                    if (report.auxiliary.isNotEmpty()) {
                        item(key = "aux-header") { SectionHeader(stringResource(R.string.analytics_auxiliary)) }
                        itemsIndexed(report.auxiliary, key = { i, a -> "aux:$i:${a.task}" }) { _, a -> AuxiliaryRow(a) }
                    }
                }
                AnalyticsTab.SKILLS -> countedList(report.skills, "skill", R.string.analytics_no_skills)
                AnalyticsTab.TOOLS -> countedList(report.tools, "tool", R.string.analytics_no_tools)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.countedList(
    rows: List<Counted>,
    prefix: String,
    emptyText: Int,
) {
    if (rows.isEmpty()) {
        item(key = "$prefix-empty") { Note(stringResource(emptyText)) }
        return
    }
    val top = rows.maxOf { it.count }.coerceAtLeast(1)
    itemsIndexed(rows, key = { i, r -> "$prefix:$i:${r.name}" }) { _, r -> CountedRow(r, top) }
}

@Composable
private fun WindowChips(state: AnalyticsUiState, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
        AnalyticsUiState.WINDOWS.forEach { days ->
            FilterChip(
                selected = state.days == days,
                onClick = { onSelect(days) },
                label = {
                    Text(
                        if (days == AnalyticsUiState.TODAY) {
                            stringResource(R.string.analytics_today)
                        } else {
                            stringResource(R.string.analytics_days, days)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TabChips(selected: AnalyticsTab, onSelect: (AnalyticsTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
        AnalyticsTab.entries.forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        stringResource(
                            when (tab) {
                                AnalyticsTab.MODELS -> R.string.models
                                AnalyticsTab.SKILLS -> R.string.skills
                                AnalyticsTab.TOOLS -> R.string.tools
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun Totals(report: Report) {
    val t = report.totals
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            StatTile(
                value = formatCost(t.cost),
                label = stringResource(if (t.costIsEstimate) R.string.analytics_cost_estimated else R.string.analytics_cost),
            )
            StatTile(value = "${t.sessions}", label = stringResource(R.string.analytics_sessions))
            StatTile(value = "${t.apiCalls}", label = stringResource(R.string.calls))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            StatTile(value = hxCompactCount(t.inputTokens), label = stringResource(R.string.in_text))
            StatTile(value = hxCompactCount(t.outputTokens), label = stringResource(R.string.out))
            StatTile(value = hxCompactCount(t.cacheReadTokens), label = stringResource(R.string.analytics_cache))
        }
        HxSplitBar(
            parts = listOf(
                HxBarPart(stringResource(R.string.in_text), t.inputTokens, MaterialTheme.colorScheme.primary),
                // Reasoning tokens are part of the output count, so they are taken out of it here
                // instead of being drawn twice.
                HxBarPart(
                    stringResource(R.string.out),
                    (t.outputTokens - t.reasoningTokens).coerceAtLeast(0L),
                    MaterialTheme.colorScheme.tertiary,
                ),
                HxBarPart(stringResource(R.string.analytics_reasoning), t.reasoningTokens, MaterialTheme.colorScheme.secondary),
            ),
        )
    }
}

/**
 * The "Today" chip's page head: tokens and cost of today's sessions up front, the split under
 * them. Read from today's calendar day, not the 24-hour window the server was asked for. The
 * server counts a session on the day it started, so a chat begun yesterday and still going adds
 * nothing here.
 */
@Composable
private fun TodayCard(today: Day) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.analytics_today))
            Spacer(Modifier.weight(1f))
            Text(today.day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (today.sessions == 0L && today.tokens == 0L) {
            Text(
                stringResource(R.string.analytics_today_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hxCompactCount(today.tokens),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    " " + stringResource(R.string.analytics_tokens),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp).weight(1f),
                )
                Text(
                    formatCost(today.cost),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HxSplitBar(
                parts = listOf(
                    HxBarPart(stringResource(R.string.in_text), today.inputTokens, MaterialTheme.colorScheme.primary),
                    HxBarPart(stringResource(R.string.out), today.outputTokens, MaterialTheme.colorScheme.tertiary),
                ),
                barHeight = 6.dp,
            )
            Text(
                stringResource(
                    R.string.analytics_day_line,
                    hxCompactCount(today.inputTokens),
                    hxCompactCount(today.outputTokens),
                    hxCompactCount(today.cacheReadTokens),
                    today.sessions.toInt(),
                    today.apiCalls.toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One bar per day, as tall as that day's tokens (in + out) against the busiest day: costs are
 * often all 0 (free or unpriced models), which left every bar the same. Tap a bar for that day's
 * numbers; the newest day is shown first.
 */
@Composable
private fun DailyUsage(daily: List<Day>) {
    val top = daily.maxOf { it.tokens }
    var selected by rememberSaveable(daily.size, daily.last().day) { mutableStateOf(daily.lastIndex) }
    val day = daily.getOrNull(selected) ?: daily.last()
    Card {
        SectionTitle(stringResource(R.string.analytics_daily_usage))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            daily.forEachIndexed { i, d ->
                val share = if (top > 0L) d.tokens.toFloat() / top else 0f
                val color = if (i == selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = i },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (d.tokens > 0L) share.coerceIn(0.04f, 1f) else 0.02f)
                            .background(
                                if (d.tokens > 0L) color else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labelStyle = MaterialTheme.typography.labelSmall
            Text(daily.first().day, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(daily.last().day, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.day,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                hxCompactCount(day.tokens) + if (day.cost > 0.0) " · " + formatCost(day.cost) else "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(
                R.string.analytics_day_line,
                hxCompactCount(day.inputTokens),
                hxCompactCount(day.outputTokens),
                hxCompactCount(day.cacheReadTokens),
                day.sessions.toInt(),
                day.apiCalls.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelRow(m: ModelUse) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.model,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.provider.isNotBlank()) {
                    Text(
                        m.provider,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(formatCost(m.cost), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            stringResource(
                R.string.analytics_model_line,
                hxCompactCount(m.inputTokens),
                hxCompactCount(m.outputTokens),
                m.sessions,
                m.apiCalls,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuxiliaryRow(a: AuxiliaryUse) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.task.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                if (a.models.isNotEmpty()) {
                    Text(
                        a.models.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCost(a.cost), style = MaterialTheme.typography.titleSmall)
                Text(
                    hxCompactCount(a.tokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CountedRow(row: Counted, top: Long) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${row.count}", style = MaterialTheme.typography.titleSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((row.count.toFloat() / top).coerceIn(0.02f, 1f))
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
        ) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = HxSpace.sm),
    )
}

/** Dollars, with more digits for the cents-sized amounts cheap models cost. Latin digits, like the token counts. */
private fun formatCost(usd: Double): String = when {
    usd <= 0.0 -> "$0"
    usd < 0.01 -> String.format(Locale.ROOT, "$%.4f", usd)
    else -> String.format(Locale.ROOT, "$%.2f", usd)
}
