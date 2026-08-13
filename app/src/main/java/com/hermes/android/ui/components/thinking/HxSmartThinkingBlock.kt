package com.hermes.android.ui.components.thinking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.component.HermesMarkdown

/** Aether-inspired reasoning UI, driven by the real chat streaming state. */
@Composable
fun HxSmartThinkingBlock(
    reasoning: String,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionCompat()
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
    val transition = rememberInfiniteTransition(label = "thinking_pulse")
    val pulse by transition.animateFloat(
        initialValue = if (isLongTurn) 0.55f else 0.35f,
        targetValue = if (isLongTurn) 0.85f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isLongTurn) 1600 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val barAlpha = when {
        !isStreaming -> 0.4f
        reduceMotion -> 0.7f
        else -> pulse
    }
    val preview = remember(reasoning) {
        reasoning.takeLast(400).trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
    val emoji = remember(reasoning) {
        Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF\\u2B00-\\u2BFF]")
            .findAll(reasoning.takeLast(400)).lastOrNull()?.value
    }

    Column(
        modifier = modifier.fillMaxWidth().animateContentSize().padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = barAlpha)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isStreaming) "در حال تفکر" else "افکار",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            emoji?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        if (isStreaming && !expanded && preview.isNotEmpty()) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulse * 0.8f),
                maxLines = 1,
                modifier = Modifier.padding(start = 11.dp, bottom = 2.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            HermesMarkdown(
                markdown = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 11.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun rememberReduceMotionCompat(): Boolean = false
