package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage

@Composable
internal fun ToolCallCard(message: ChatMessage.ToolCall) {
    val toolAccent = if (message.isRunning) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hxSoftShadow(radius = 10.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(toolAccent.copy(alpha = 0.07f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(
                        text = if (message.error != null) "\u2715" else "\u2713",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Text(
                    text = message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (message.isRunning) {
                    Text(
                        text = t("Running...", "در حال اجرا..."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                message.durationS?.let {
                    Text(
                        text = "${"%.1f".format(it)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message.argsText?.takeIf { it.isNotBlank() }?.let { args ->
                // Auto-expand while the tool is running so the user can watch
                // what it's doing; once finished, they can open it on demand.
                var argsExpanded by remember { mutableStateOf(message.isRunning) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { argsExpanded = !argsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t("Arguments", "آرگومان‌ها"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (argsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = argsExpanded) {
                    Text(
                        text = args,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                val isLongResult = result.length > 300
                var resultExpanded by remember { mutableStateOf(false) }
                val displayResult = if (isLongResult && !resultExpanded) {
                    result.take(300) + "\u2026"
                } else {
                    result
                }
                HermesMarkdown(
                    markdown = displayResult,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                if (isLongResult) {
                    TextButton(
                        onClick = { resultExpanded = !resultExpanded },
                        modifier = Modifier.padding(top = 0.dp),
                    ) {
                        Icon(
                            imageVector = if (resultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (resultExpanded) t("Collapse", "جمع کردن") else t("Show more", "بیشتر"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            message.error?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = "\u274C ${err.take(220)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
