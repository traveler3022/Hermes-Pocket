package com.hermes.android.ui.screen.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

import com.hermes.android.ui.screen.message.ToolGroupIndent

// ── Aether-style animation constants ──────────────────────────────────────
private const val ToolTransitionDurationMillis = 360
private val ToolTransitionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

/**
 * Aether-style collapsible disclosure for agent work summaries.
 *
 * Shows a clickable header with animated arrow rotation, and a
 * content area that expands/collapses with animation.
 */
@Composable
internal fun AgentWorkSummaryDisclosure(
    title: String,
    stateKey: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(initiallyExpanded) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
        label = "agent_work_arrow_rotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ToolGroupIndent),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}