package com.hermes.android.ui.components.thinking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.component.HermesMarkdown

/** Aether-like reasoning: compact live status first, detailed timeline on expand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HxSmartThinkingBlock(
    reasoning: String,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var streamingSeconds by remember(isStreaming) { mutableStateOf(0) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                streamingSeconds++
            }
        }
    }
    val isLongTurn = streamingSeconds > 20
    val pulse by rememberInfiniteTransition(label = "hx_reasoning_pulse").animateFloat(
        initialValue = if (isLongTurn) 0.42f else 0.22f,
        targetValue = if (isLongTurn) 0.72f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isLongTurn) 1600 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val latest = remember(reasoning) {
        reasoning.takeLast(500).trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
    val steps = remember(reasoning) { reasoningToTimelineSteps(reasoning) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .background(colors.surfaceVariant.copy(alpha = if (isStreaming) 0.34f else 0.22f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThinkingGlyph(active = isStreaming, alpha = pulse)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isStreaming && latest.isBlank()) {
                    ShimmerLine(text = "در حال فکر کردن...")
                } else {
                    TypewriterLine(
                        text = when {
                            isStreaming && latest.isNotBlank() -> latest
                            else -> "Thought process"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "Timeline باز است" else "برای دیدن مسیر فکر لمس کن",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = onToggle,
                containerColor = colors.surface,
                contentColor = colors.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(
                        text = if (isStreaming) "در حال فکر کردن" else "فرآیند تفکر",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    if (steps.isEmpty()) {
                        HermesMarkdown(
                            markdown = reasoning.ifBlank { "در انتظار reasoning..." },
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        steps.forEachIndexed { index, step ->
                            ReasoningTimelineRow(
                                title = step.title,
                                detail = step.detail,
                                isLast = index == steps.lastIndex && !isStreaming,
                                active = isStreaming && index == steps.lastIndex,
                            )
                        }
                        if (!isStreaming) ReasoningDoneRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingGlyph(active: Boolean, alpha: Float) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = if (active) alpha else 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ShimmerLine(text: String) {
    val offset by rememberInfiniteTransition(label = "hx_shimmer").animateFloat(
        initialValue = -360f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                360f at 1500 using LinearEasing
                360f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
        start = androidx.compose.ui.geometry.Offset(offset - 120f, 0f),
        end = androidx.compose.ui.geometry.Offset(offset + 120f, 0f),
    )
    Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(brush = brush))
}

@Composable
private fun TypewriterLine(text: String, style: TextStyle, color: Color) {
    var rendered by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            rendered = ""
            return@LaunchedEffect
        }
        if (!text.startsWith(rendered)) rendered = ""
        while (rendered.length < text.length) {
            val next = (rendered.length + 3).coerceAtMost(text.length)
            rendered = text.substring(0, next)
            kotlinx.coroutines.delay(18)
        }
    }
    Text(
        text = rendered.ifBlank { text },
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ReasoningTimelineRow(title: String, detail: String, isLast: Boolean, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(18.dp).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.16f)),
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(if (active) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.72f)),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReasoningDoneRow() {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text("Done", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

private data class ReasoningStep(val title: String, val detail: String)

private fun reasoningToTimelineSteps(reasoning: String): List<ReasoningStep> {
    return reasoning
        .lines()
        .map { it.trim().trimStart('-', '*', '•').trim() }
        .filter { it.isNotBlank() }
        .takeLast(8)
        .mapIndexed { index, line ->
            val split = line.indexOfAny(charArrayOf(':', '：', '—', '-'))
            if (split in 4 until (line.length - 3)) {
                ReasoningStep(
                    title = line.take(split).trim().ifBlank { "Step ${index + 1}" },
                    detail = line.drop(split + 1).trim(),
                )
            } else {
                ReasoningStep(title = "Step ${index + 1}", detail = line)
            }
        }
}
