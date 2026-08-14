package com.hermes.android.ui.screen.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t

// ── Aether-style timeline constants ───────────────────────────────────────
private val TimelineGlyphWidth = 22.dp
private val TimelineIconSize = 18.dp
private val TimelineLineWidth = 2.dp
private val TimelineLineTopGap = 9.dp
private val TimelineLineBottomGap = 0.dp

/**
 * Aether-style timeline item for reasoning steps.
 */
internal sealed interface ReasoningTimelineItem {
    data class Summary(
        val title: String,
        val detail: String,
        val isStreaming: Boolean = false,
    ) : ReasoningTimelineItem

    data class Tool(
        val toolName: String,
        val isRunning: Boolean,
        val detail: String? = null,
    ) : ReasoningTimelineItem
}

/**
 * Aether-style reasoning timeline with glyphs and connecting lines.
 *
 * Shows a vertical timeline where each item is a row with:
 * - A glyph icon (psychology for reasoning, tool icon for tools)
 * - A vertical connecting line (unless last item)
 * - Title + detail text
 */
@Composable
internal fun ReasoningTimeline(
    items: List<ReasoningTimelineItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val hasDoneChunk = items.none { it is ReasoningTimelineItem.Summary && it.isStreaming }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isLast = index == items.lastIndex
            when (item) {
                is ReasoningTimelineItem.Summary -> ReasoningTimelineRow(
                    icon = Icons.Default.Psychology,
                    title = item.title,
                    detail = item.detail,
                    isStreaming = item.isStreaming,
                    isLast = isLast,
                )
                is ReasoningTimelineItem.Tool -> ReasoningTimelineRow(
                    icon = if (item.isRunning) null else Icons.Default.Psychology,
                    title = item.toolName,
                    detail = item.detail ?: "",
                    isStreaming = item.isRunning,
                    isLast = isLast,
                )
            }
        }
        if (hasDoneChunk) {
            ReasoningTimelineDoneRow()
        }
    }
}

/**
 * A single row in the reasoning timeline with a glyph and connecting line.
 */
@Composable
private fun ReasoningTimelineRow(
    icon: ImageVector?,
    title: String,
    detail: String,
    isStreaming: Boolean,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimelineGlyph(
            icon = icon,
            isLast = isLast,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Done row shown at the end of the timeline.
 */
@Composable
private fun ReasoningTimelineDoneRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimelineGlyph(
            icon = Icons.Default.Psychology,
            isLast = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = t("Reasoning complete", "تفکر کامل شد"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = t("Done", "انجام شد"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Timeline glyph with optional icon and connecting vertical line.
 */
@Composable
private fun TimelineGlyph(
    icon: ImageVector? = null,
    isLast: Boolean,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .width(TimelineGlyphWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Vertical connecting line (hidden for last item)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .padding(
                        top = TimelineIconSize + TimelineLineTopGap,
                        bottom = TimelineLineBottomGap,
                    )
                    .width(TimelineLineWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(lineColor),
            )
        }
        // Icon or dot
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(TimelineIconSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
            )
        }
    }
}

/**
 * Kept for backward compatibility — wraps a simple reasoning string
 * into a single timeline item.
 */
@Composable
internal fun ReasoningTimeline(
    title: String,
    preview: String,
    details: String,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(expanded) }

    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(lineColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!showDetails && preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
            Icon(
                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        AnimatedVisibility(visible = showDetails) {
            Column(
                modifier = Modifier.padding(start = 13.dp, top = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}