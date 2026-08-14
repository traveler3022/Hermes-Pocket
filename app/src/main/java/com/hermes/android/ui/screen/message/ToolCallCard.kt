package com.hermes.android.ui.screen.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
import kotlinx.coroutines.delay

// ── Aether-style animation constants ──────────────────────────────────────
private const val ToolInvocationCollapseThreshold = 3
private const val ToolTransitionDurationMillis = 360
private const val ToolInvocationAutoExpandDelayMillis = 1_000L
private const val ToolGroupCollapseStageDelayMillis = 180L
private val ToolTransitionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
internal val ToolGroupIndent = 14.dp

internal data class ToolCallGroup(
    val tools: List<ChatMessage.ToolCall>,
    val isRunning: Boolean,
    val stateKey: String,
    val autoExpand: Boolean = false,
)

/**
 * Aether-style tool invocation list.
 *
 * - Fewer than [ToolInvocationCollapseThreshold] tools → show inline (no header).
 * - At or above threshold → collapsible header with animated indent + arrow rotation.
 * - Staggered entry animation per tool card.
 * - State survives config changes (rememberSaveable).
 */
@Composable
internal fun ToolCallGroupCard(
    group: ToolCallGroup,
    onToggleGroup: (String) -> Unit,
    onToggleTool: (String) -> Unit,
) {
    if (group.tools.isEmpty()) return

    val isRunning = group.isRunning

    // Under threshold: show inline, no grouping header
    if (group.tools.size < ToolInvocationCollapseThreshold) {
        ToolInvocationCardsColumn(
            toolInvocations = group.tools,
            indent = 0.dp,
            topPadding = 6.dp,
        )
        return
    }

    // ── Grouped (≥ threshold) ─────────────────────────────────────────────
    var headerVisible by rememberSaveable(group.stateKey) { mutableStateOf(!group.autoExpand) }
    var expanded by rememberSaveable(group.stateKey) { mutableStateOf(group.autoExpand) }
    var lastAutoExpanded by rememberSaveable(group.stateKey) { mutableStateOf(group.autoExpand) }

    LaunchedEffect(group.autoExpand) {
        if (lastAutoExpanded != group.autoExpand) {
            if (group.autoExpand) {
                headerVisible = false
                expanded = true
            } else {
                headerVisible = true
                expanded = true
                delay(ToolGroupCollapseStageDelayMillis)
                expanded = false
            }
            lastAutoExpanded = group.autoExpand
        }
    }

    val childIndent by animateDpAsState(
        targetValue = if (headerVisible) ToolGroupIndent else 0.dp,
        animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
        label = "tool_group_indent",
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
        label = "tool_group_arrow_rotation",
    )

    val statusColor = if (isRunning) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Collapsible header ────────────────────────────────────────────
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 100,
                    easing = ToolTransitionEasing,
                ),
            ) + expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = statusColor,
                    )
                } else {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                }
                Text(
                    text = if (isRunning) {
                        t("Executing tools…", "در حال اجرای ابزارها…")
                    } else {
                        t("Executed tools", "ابزارهای اجرا شده")
                    } + " ${group.tools.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = if (expanded) {
                        t("Collapse tools", "جمع کردن ابزارها")
                    } else {
                        t("Expand tools", "باز کردن ابزارها")
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }

        // ── Expanded tool cards ───────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 90,
                    delayMillis = 40,
                    easing = ToolTransitionEasing,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
            ),
        ) {
            ToolInvocationCardsColumn(
                toolInvocations = group.tools,
                indent = childIndent,
                topPadding = 4.dp,
            )
        }
    }
}

/**
 * Column of tool cards with staggered entry animation (Aether-style).
 */
@Composable
private fun ToolInvocationCardsColumn(
    toolInvocations: List<ChatMessage.ToolCall>,
    indent: Dp = 0.dp,
    topPadding: Dp = 6.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        toolInvocations.forEach { tool ->
            ToolInvocationAnimatedCard(
                toolInvocation = tool,
                topPadding = topPadding,
            )
        }
    }
}

/**
 * Single tool card with staggered fade-in animation.
 */
@Composable
private fun ToolInvocationAnimatedCard(
    toolInvocation: ChatMessage.ToolCall,
    topPadding: Dp,
) {
    var visible by rememberSaveable(toolInvocation.id) { mutableStateOf(false) }
    LaunchedEffect(toolInvocation.id) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = ToolTransitionDurationMillis - 90,
                delayMillis = 30,
                easing = ToolTransitionEasing,
            ),
        ),
    ) {
        SingleToolCallCard(
            message = toolInvocation,
            topPadding = topPadding,
        )
    }
}

@Composable
internal fun SingleToolCallCard(
    message: ChatMessage.ToolCall,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    topPadding: Dp = 6.dp,
) {
    var internalExpanded by rememberSaveable(message.id) { mutableStateOf(expanded) }

    val statusColor = when {
        message.error != null -> MaterialTheme.colorScheme.error
        message.isRunning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (message.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.6.dp,
                    color = statusColor,
                )
            } else {
                Text(
                    text = if (message.error != null) "✕" else "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                message.durationS?.let { duration ->
                    Text(
                        text = "%.1fs".format(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val hasDetails = message.argsText?.isNotBlank() == true ||
                message.resultText?.isNotBlank() == true ||
                message.error?.isNotBlank() == true

        if (hasDetails) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = if (internalExpanded) t("Hide details", "مخفی کردن") else t("Show details", "نمایش جزئیات"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (internalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = internalExpanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    message.argsText?.takeIf { it.isNotBlank() }?.let { args ->
                        Text(
                            text = args,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    message.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    message.error?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(
                            text = "✕ ${err.take(220)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}