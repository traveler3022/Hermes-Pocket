package com.hermes.android.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One labelled slice of a [HxSplitBar]. A zero-[value] part is dropped. */
data class HxBarPart(
    val label: String,
    val value: Long,
    val color: Color,
)

private const val BarAnimationMillis = 420

/**
 * A single horizontal bar split into proportional parts, with a legend under it.
 *
 * Four numbers on their own — input, output, total, calls — say how much but not
 * how it divides, and the split is the interesting part: a turn that is mostly
 * input reads very differently from one that is mostly generated output. The bar
 * shows that ratio at a glance; the legend keeps the exact figures.
 *
 * Laid out with weights rather than drawn on a Canvas, so it inherits text
 * scaling and layout direction for free — the bar reads right-to-left in Farsi
 * without a second code path.
 */
@Composable
fun HxSplitBar(
    parts: List<HxBarPart>,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val visible = parts.filter { it.value > 0 }
    if (visible.isEmpty()) return
    val total = visible.sumOf { it.value }.toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            visible.forEach { part ->
                // Animating the share rather than the width keeps the segments
                // summing to the full bar at every frame of the transition.
                val share by animateFloatAsState(
                    targetValue = part.value / total,
                    animationSpec = tween(BarAnimationMillis),
                    label = "split_bar_share_${part.label}",
                )
                Box(
                    modifier = Modifier
                        .weight(share.coerceAtLeast(0.0001f))
                        .fillMaxHeight()
                        .background(part.color),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            visible.forEach { part ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(part.color),
                    )
                    Text(
                        text = "${part.label} ${hxCompactCount(part.value)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Token counts as humans read them: 24.5k rather than 24512.
 *
 * Deliberately not localised into Farsi digits — these sit beside model ids and
 * raw figures that stay Latin, and mixing the two numbering systems in one row
 * is harder to scan than either alone.
 */
fun hxCompactCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}
