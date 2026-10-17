package com.hermes.android.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.achievements.Achievement
import com.hermes.android.domain.achievements.Tier
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.icons.filled.StarBorder
import com.hermes.android.ui.viewmodel.AchievementsViewModel

/**
 * Hermes' achievements (the bundled hermes-achievements plugin): what is unlocked, what is
 * on its way with progress to the next tier, and the secret ones still hidden. Tap a badge
 * for what earns it.
 */
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HermesScaffold(
        title = stringResource(R.string.achievements_title),
        subtitle = stringResource(R.string.achievements_subtitle),
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading && !state.rescanning) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.loadError != null && state.items.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.achievements_title),
                    caption = state.loadError + "\n\n" + stringResource(R.string.achievements_unavailable),
                    actionLabel = stringResource(R.string.reload),
                    onAction = viewModel::load,
                )
                else -> AchievementList(state, viewModel)
            }
        }
    }
}

@Composable
private fun AchievementList(state: AchievementsViewModel.UiState, viewModel: AchievementsViewModel) {
    val categories = remember(state.items) {
        state.items.groupingBy { it.category }.eachCount().entries.sortedBy { it.key }
    }
    // Unlocked first (highest tier on top), then the closest to unlocking, secrets last.
    val shown = remember(state.items, state.filter) {
        state.items
            .filter { state.filter == null || it.category == state.filter }
            .sortedWith(
                compareBy<Achievement> { it.state.ordinal }
                    .thenByDescending { tierRank(it.tier) }
                    .thenByDescending { it.percent },
            )
    }
    val inProgress = remember(state.items) {
        state.items.count { it.state == Achievement.State.DISCOVERED && it.progress > 0 }
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, top = HxSpace.sm, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm), modifier = Modifier.fillMaxWidth()) {
                StatTile(value = "${state.unlocked} / ${state.total}", label = stringResource(R.string.achievements_unlocked))
                StatTile(value = inProgress.toString(), label = stringResource(R.string.achievements_in_progress))
            }
        }
        item(key = "scan") {
            ScanRow(state, onRescan = viewModel::rescan)
        }
        item(key = "categories") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("${stringResource(R.string.achievements_all)} · ${state.items.size}") },
                )
                categories.forEach { (category, count) ->
                    FilterChip(
                        selected = state.filter == category,
                        onClick = { viewModel.setFilter(if (state.filter == category) null else category) },
                        label = { Text("${achievementCategoryName(category)} · $count") },
                    )
                }
            }
        }
        // Prefixed so an id can never match the keys of the rows above.
        items(shown, key = { "ach:${it.id}" }) { item ->
            AchievementCard(
                item = item,
                expanded = expanded == item.id,
                onClick = { expanded = if (expanded == item.id) null else item.id },
            )
        }
    }
}

@Composable
private fun ScanRow(state: AchievementsViewModel.UiState, onRescan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = when {
                    state.scanning || state.rescanning -> stringResource(R.string.achievements_scanning)
                    else -> state.scanError ?: state.loadError.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!state.scanning && !state.rescanning && (state.scanError ?: state.loadError) != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRescan, enabled = !state.rescanning && !state.scanning) {
                Text(stringResource(R.string.achievements_rescan))
            }
        }
        if (state.scanning || state.rescanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@Composable
private fun AchievementCard(item: Achievement, expanded: Boolean, onClick: () -> Unit) {
    val unlocked = item.state == Achievement.State.UNLOCKED
    val secret = item.state == Achievement.State.SECRET
    val medal = tierColor(item.tier)
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (unlocked) 0.7f else 0.35f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HxRadius.md)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(HxSpace.inner).animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(HxSpace.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) medal.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        unlocked -> Icons.Default.Star
                        secret -> Icons.Default.Lock
                        else -> Icons.Default.StarBorder
                    },
                    contentDescription = null,
                    tint = if (unlocked) medal else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    Text(
                        text = if (secret) stringResource(R.string.achievements_secret) else item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.tier?.let { TierBadge(it, medal) }
                }
                if (!secret) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val threshold = item.nextThreshold
                if (!secret && threshold != null && (item.nextTier != null || !unlocked)) {
                    LinearProgressIndicator(
                        progress = { item.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = item.nextTier?.let {
                            stringResource(R.string.achievements_next, item.progress.toString(), threshold.toString(), tierName(it))
                        } ?: stringResource(R.string.achievements_step, "${item.percent}%", "100%"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded && item.criteria.isNotBlank()) {
                    Text(
                        text = item.criteria,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = HxSpace.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun TierBadge(tier: Tier, color: Color) {
    Text(
        text = tierName(tier),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Rank for sorting; no medal ranks below all of them. */
private fun tierRank(tier: Tier?): Int = tier?.ordinal ?: -1

private fun tierColor(tier: Tier?): Color = when (tier) {
    Tier.COPPER -> Color(0xFFB87333)
    Tier.SILVER -> Color(0xFF9EA7B0)
    Tier.GOLD -> Color(0xFFE0A800)
    Tier.DIAMOND -> Color(0xFF4FC3F7)
    Tier.OLYMPIAN -> Color(0xFFAB47BC)
    null -> Color(0xFF8D8D8D)
}

@Composable
private fun tierName(tier: Tier): String = stringResource(
    when (tier) {
        Tier.COPPER -> R.string.tier_copper
        Tier.SILVER -> R.string.tier_silver
        Tier.GOLD -> R.string.tier_gold
        Tier.DIAMOND -> R.string.tier_diamond
        Tier.OLYMPIAN -> R.string.tier_olympian
    },
)

@Composable
private fun achievementCategoryName(category: String): String = when (category) {
    "Agent Autonomy" -> stringResource(R.string.ach_cat_agent_autonomy)
    "Debugging Chaos" -> stringResource(R.string.ach_cat_debugging_chaos)
    "Hermes Native" -> stringResource(R.string.ach_cat_hermes_native)
    "Lifestyle" -> stringResource(R.string.ach_cat_lifestyle)
    "Model Lore" -> stringResource(R.string.ach_cat_model_lore)
    "Research/Web" -> stringResource(R.string.ach_cat_research_web)
    "Tool Mastery" -> stringResource(R.string.ach_cat_tool_mastery)
    "Vibe Coding" -> stringResource(R.string.ach_cat_vibe_coding)
    else -> category
}
