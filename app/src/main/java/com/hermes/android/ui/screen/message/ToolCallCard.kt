package com.hermes.android.ui.screen.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage

internal data class ToolCallGroup(
    val tools: List<ChatMessage.ToolCall>,
    val isRunning: Boolean,
    val stateKey: String,
    val autoExpand: Boolean = false,
)

@Composable
internal fun ToolCallGroupCard(
    group: ToolCallGroup,
    onToggleGroup: (String) -> Unit,
    onToggleTool: (String) -> Unit,
) {
    if (group.tools.isEmpty()) return

    var headerVisible by remember(group.stateKey) { mutableStateOf(!group.autoExpand) }
    var expanded by remember(group.stateKey) { mutableStateOf(group.autoExpand) }
    var lastAutoExpanded by remember(group.stateKey) { mutableStateOf(group.autoExpand) }

    if (group.autoExpand != lastAutoExpanded) {
        headerVisible = !group.autoExpand
        expanded = group.autoExpand
        lastAutoExpanded = group.autoExpand
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val statusColor = if (group.isRunning) {
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
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(tween(260)) + expandVertically(tween(260), expandFrom = Alignment.Top),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleGroup(group.stateKey) }
                    .shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Transparent)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (group.isRunning) {
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
                Column(modifier = Modifier.weight(1f)) {
                    val label = if (group.isRunning) {
                        t("Executing tools…", "در حال اجرای ابزارها…")
                    } else {
                        t("Executed tools", "ابزارهای اجرا شده")
                    }
                    Text(
                        text = "$label ${group.tools.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260), expandFrom = Alignment.Top) +
                    fadeIn(tween(170, delayMillis = 40)),
            exit = shrinkVertically(tween(260), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.tools.forEach { tool ->
                    SingleToolCallCard(
                        message = tool,
                        onToggle = { onToggleTool(tool.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SingleToolCallCard(
    message: ChatMessage.ToolCall,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
) {
    var internalExpanded by remember { mutableStateOf(expanded) }

    val statusColor = when {
        message.error != null -> MaterialTheme.colorScheme.error
        message.isRunning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                        text = "${"%.1f".format(duration)}s",
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
                    .clickable { internalExpanded = !internalExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
