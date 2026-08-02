package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.aetherSurfaceHigh
import com.hermes.android.ui.viewmodel.ChatMessage
import kotlinx.coroutines.delay

/**
 * Aether-style tool execution card.
 *
 * Unlike the old tinted "box with spinner" card, Aether renders tool calls as
 * document text: a plain title row ("Executing bash command…", shimmering
 * while it runs) that expands into monospace "Command" / "Result" blocks on
 * tap. The tool auto-expands ~1s after it starts (so you can watch it work)
 * and collapses again once it completes.
 */
@Composable
internal fun ToolCallCard(message: ChatMessage.ToolCall) {
    val humanTool = remember(message.toolName) {
        message.toolName
            .replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val hasDetail = !message.argsText.isNullOrBlank() || !message.resultText.isNullOrBlank()
    var expanded by remember(message.isRunning) { mutableStateOf(message.isRunning) }

    // Aether behaviour: expand shortly after a tool starts, collapse when it
    // finishes — but never override a manual tap once it has settled.
    LaunchedEffect(message.isRunning) {
        if (message.isRunning) {
            delay(1000)
            expanded = true
        } else {
            delay(180)
            expanded = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(360))
            .clickable(enabled = hasDetail) { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Status: a small spinner while running; a tool glyph when done.
            if (message.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val icon = when {
                    message.error != null -> Icons.Default.Terminal
                    isBashTool(message.toolName) -> Icons.Default.Terminal
                    isWebTool(message.toolName) -> Icons.Default.Language
                    else -> Icons.Default.Build
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (message.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (message.isRunning) {
                ShimmerToolTitle(
                    text = t("Executing $humanTool…", "در حال اجرای $humanTool…"),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = if (message.error != null) {
                        t("$humanTool failed", "$humanTool ناموفق بود")
                    } else {
                        t("Executed $humanTool", "$humanTool اجرا شد")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            message.durationS?.let {
                Text(
                    text = "${"%.1f".format(it)}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasDetail) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.argsText?.takeIf { it.isNotBlank() }?.let { args ->
                    ToolDetailBlock(label = t("Command", "دستور"), content = args)
                }
                message.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                    ToolDetailBlock(label = t("Result", "نتیجه"), content = result)
                }
                message.error?.takeIf { it.isNotBlank() }?.let { err ->
                    Text(
                        text = err.take(220),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolDetailBlock(label: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.aetherSurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/** Shimmering title while a tool is executing (Aether's animated status). */
@Composable
private fun ShimmerToolTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "tool_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tool_shimmer_progress",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val brush = Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.95f),
            base.copy(alpha = 0.45f),
        ),
        start = Offset(-320f + progress * 960f, 0f),
        end = Offset(progress * 960f, 0f),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        brush = brush,
    )
}

private fun isBashTool(name: String): Boolean {
    val n = name.lowercase()
    return "bash" in n || "shell" in n || "terminal" in n || "exec" in n || "run" in n || "command" in n
}

private fun isWebTool(name: String): Boolean {
    val n = name.lowercase()
    return "web" in n || "search" in n || "fetch" in n || "http" in n || "url" in n
}
