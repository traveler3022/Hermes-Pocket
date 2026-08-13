package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage

@Composable
internal fun ToolCallCard(message: ChatMessage.ToolCall) {
    var expanded by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id, message.isRunning) {
        if (message.isRunning) {
            kotlinx.coroutines.delay(1_000)
            expanded = true
        } else {
            expanded = false
        }
    }

    val colors = MaterialTheme.colorScheme
    val failed = message.error != null
    val accent = when {
        failed -> colors.error
        message.isRunning -> colors.primary
        else -> colors.tertiary
    }
    val pulse by rememberInfiniteTransition(label = "tool_shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2_600
                1f at 1_500 using LinearEasing
                1f at 2_600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "tool_shimmer_offset",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(360))
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = if (message.isRunning) 0.18f + pulse * 0.18f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (message.isRunning) Icons.Default.Terminal else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toolLabel(message.toolName),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        failed -> t("Failed", "ناموفق")
                        message.isRunning -> t("Running…", "در حال اجرا…")
                        message.durationS != null -> "Done · ${"%.1f".format(message.durationS)}s"
                        else -> t("Done", "انجام شد")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                message.argsText?.takeIf { it.isNotBlank() }?.let {
                    ToolCodeSection(label = t("Command", "دستور"), content = it)
                }
                message.resultText?.takeIf { it.isNotBlank() }?.let {
                    ToolCodeSection(label = t("Result", "نتیجه"), content = it)
                }
                message.error?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCodeSection(label: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun toolLabel(name: String): String = when (name.lowercase()) {
    "bash", "shell", "terminal" -> "Terminal"
    "fetch_web_url", "web_fetch", "tavily_search" -> "Web search"
    "file.read", "read_file" -> "Read file"
    "file.write", "write_file" -> "Write file"
    else -> name
}
