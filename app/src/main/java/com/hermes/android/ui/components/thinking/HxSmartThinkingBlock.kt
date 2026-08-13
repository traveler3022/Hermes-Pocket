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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.component.HermesMarkdown

/** Aether-inspired reasoning card driven by the real chat streaming state. */
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
    val transition = rememberInfiniteTransition(label = "thinking_card")
    val pulse by transition.animateFloat(
        initialValue = if (isLongTurn) 0.55f else 0.35f,
        targetValue = if (isLongTurn) 0.85f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isLongTurn) 1600 else 900,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking_pulse",
    )

    val colors = MaterialTheme.colorScheme
    val preview = remember(reasoning) {
        reasoning.takeLast(400).trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
    val emoji = remember(reasoning) {
        Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF\\u2B00-\\u2BFF]")
            .findAll(reasoning.takeLast(400)).lastOrNull()?.value
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = colors.primaryContainer.copy(alpha = 0.32f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.primary.copy(alpha = if (isStreaming) pulse * 0.12f else 0.06f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = if (isStreaming) pulse else 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isStreaming) "در حال تفکر" else "فرآیند تفکر",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    if (isStreaming && !expanded && preview.isNotEmpty()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    } else if (!isStreaming) {
                        Text(
                            text = if (expanded) "برای بستن ضربه بزن" else "برای مشاهده جزئیات ضربه بزن",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                emoji?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 17.dp, top = 10.dp)
                    .width(2.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.primary.copy(alpha = if (isStreaming) pulse else 0.35f)),
            )

            AnimatedVisibility(visible = expanded) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(colors.primary.copy(alpha = 0.22f)),
                    )
                    HermesMarkdown(
                        markdown = reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                }
            }
        }
    }
}
