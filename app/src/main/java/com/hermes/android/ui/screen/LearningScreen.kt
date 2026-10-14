package com.hermes.android.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.memory.LearningNode
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.viewmodel.LearningViewModel
import com.hermes.android.ui.viewmodel.monthlyActivity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the agent learned (Hermes' learning graph): counts, activity by month, categories,
 * and a timeline of skills and memories, newest first. Tap one to read, edit or delete it.
 */
@Composable
fun LearningScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources follows the in-app language; LocalContext would use the device's.
    val resources = LocalResources.current
    val locale = resources.configuration.locales[0]

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    LearningViewModel.Event.Saved -> resources.getString(R.string.learning_saved)
                    is LearningViewModel.Event.Deleted -> event.message.ifBlank { resources.getString(R.string.done) }
                    is LearningViewModel.Event.Failed -> event.message
                },
            )
        }
    }

    HermesScaffold(
        title = stringResource(R.string.learning_title),
        subtitle = stringResource(R.string.learning_subtitle),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.nodes.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.loadError != null && state.nodes.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.learning_title),
                    caption = state.loadError,
                    actionLabel = stringResource(R.string.reload),
                    onAction = viewModel::load,
                )
                state.nodes.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.learning_empty),
                    caption = stringResource(R.string.learning_empty_caption),
                )
                else -> LearningTimeline(state, viewModel, locale)
            }
        }
    }

    state.open?.let { LearningNodeSheet(it, state, viewModel) }
}

@Composable
private fun LearningTimeline(state: LearningViewModel.UiState, viewModel: LearningViewModel, locale: java.util.Locale) {
    val zone = remember { ZoneId.systemDefault() }
    val activity = remember(state.nodes) { monthlyActivity(state.nodes, zone) }
    val categories = remember(state.nodes) {
        state.nodes.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
    }
    // Newest first; undated ones last, under their own header.
    val groups = remember(state.nodes, state.filter) {
        state.nodes
            .filter { state.filter == null || it.category == state.filter }
            .sortedByDescending { it.timestamp ?: Long.MIN_VALUE }
            .groupBy { node -> node.timestamp?.let { YearMonth.from(Instant.ofEpochSecond(it).atZone(zone)) } }
    }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, top = HxSpace.sm, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = state.nodes.count { it.kind == LearningNode.Kind.SKILL }.toString(),
                    label = stringResource(R.string.learning_skills),
                )
                StatTile(
                    value = state.nodes.count { it.kind == LearningNode.Kind.MEMORY }.toString(),
                    label = stringResource(R.string.learning_memories),
                )
            }
        }
        if (activity.size > 1) {
            item(key = "activity") { ActivityBars(activity, monthFormat) }
        }
        item(key = "categories") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("${stringResource(R.string.learning_all)} · ${state.nodes.size}") },
                )
                categories.forEach { (category, count) ->
                    FilterChip(
                        selected = state.filter == category,
                        onClick = { viewModel.setFilter(if (state.filter == category) null else category) },
                        label = { Text("${categoryName(category)} · $count") },
                    )
                }
            }
        }
        groups.forEach { (month, nodes) ->
            item(key = "month-${month ?: "none"}") {
                Text(
                    text = month?.format(monthFormat) ?: stringResource(R.string.learning_no_date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = HxSpace.md),
                )
            }
            items(nodes, key = { it.id }) { node ->
                NodeRow(
                    node = node,
                    date = node.timestamp?.let { Instant.ofEpochSecond(it).atZone(zone).format(dayFormat) },
                    onClick = { viewModel.open(node) },
                )
            }
        }
    }
}

/** Nodes per month as bars, oldest on the start side. */
@Composable
private fun ActivityBars(activity: List<Pair<YearMonth, Int>>, monthFormat: DateTimeFormatter) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val max = activity.maxOf { it.second }.coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(HxSpace.inner), verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            Text(
                stringResource(R.string.learning_activity),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                val slot = size.width / activity.size
                val barWidth = slot * 0.6f
                activity.forEachIndexed { index, (_, count) ->
                    val height = if (count == 0) 2.dp.toPx() else size.height * count / max
                    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                        size.width - (index + 1) * slot + (slot - barWidth) / 2
                    } else {
                        index * slot + (slot - barWidth) / 2
                    }
                    drawRoundRect(
                        color = if (count == 0) emptyColor else barColor,
                        topLeft = Offset(x, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    activity.first().first.format(monthFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    activity.last().first.format(monthFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NodeRow(node: LearningNode, date: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = HxSpace.inner, vertical = HxSpace.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    node.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                date?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = HxSpace.sm),
                    )
                }
            }
            Text(
                nodeMeta(node),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "creative · agent · used 3×" for a skill, "MEMORY.md" for a memory. */
@Composable
private fun nodeMeta(node: LearningNode): String = when (node.kind) {
    LearningNode.Kind.MEMORY -> memoryFile(node)
    LearningNode.Kind.SKILL -> buildList {
        add(categoryName(node.category))
        if (node.createdBy.isNotBlank()) add(node.createdBy)
        if (node.useCount > 0) add(stringResource(R.string.learning_uses, node.useCount))
        if (node.state != "active") add(node.state)
        if (node.pinned) add(stringResource(R.string.learning_pinned))
    }.joinToString(" · ")
}

private fun memoryFile(node: LearningNode): String = if (node.memorySource == "profile") "USER.md" else "MEMORY.md"

@Composable
private fun categoryName(category: String): String =
    if (category == "memory") stringResource(R.string.learning_memories) else category.replace('-', ' ')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearningNodeSheet(
    opened: LearningViewModel.Opened,
    state: LearningViewModel.UiState,
    viewModel: LearningViewModel,
) {
    var confirmDelete by remember(opened.node.id) { mutableStateOf(false) }
    val node = opened.node
    val linked = remember(node.id, state.links, state.nodes) {
        val labels = state.nodes.associate { it.id to it.label }
        state.links[node.id].orEmpty().mapNotNull { labels[it] }.sorted()
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Text(node.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(nodeMeta(node), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (linked.isNotEmpty()) {
                Text(
                    "${stringResource(R.string.learning_linked)}: ${linked.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                opened.loading -> CircularProgressIndicator(
                    modifier = Modifier.padding(HxSpace.lg).size(24.dp).align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                )
                opened.error != null -> Text(opened.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                opened.editing -> {
                    OutlinedTextField(
                        value = opened.draft,
                        onValueChange = viewModel::updateDraft,
                        enabled = !opened.busy,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = viewModel::cancelEdit,
                            enabled = !opened.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.cancel)) }
                        Button(
                            onClick = viewModel::save,
                            enabled = !opened.busy && opened.draft.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(HxRadius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            opened.content.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(HxSpace.md),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            enabled = !opened.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(if (node.kind == LearningNode.Kind.SKILL) R.string.learning_archive else R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = viewModel::startEdit,
                            enabled = !opened.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.edit)) }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val skill = node.kind == LearningNode.Kind.SKILL
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    if (skill) {
                        stringResource(R.string.learning_delete_skill_title, node.label)
                    } else {
                        stringResource(R.string.learning_delete_memory_title)
                    },
                )
            },
            text = {
                Text(
                    if (skill) {
                        stringResource(R.string.learning_delete_skill_body, node.id)
                    } else {
                        stringResource(R.string.learning_delete_memory_body, memoryFile(node))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text(
                        stringResource(if (skill) R.string.learning_archive else R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
