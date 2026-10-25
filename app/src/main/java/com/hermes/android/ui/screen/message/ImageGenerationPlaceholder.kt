package com.hermes.android.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Palette
import com.hermes.android.ui.viewmodel.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The `image_generate` call of this turn that is still running, if any. */
internal fun runningImageGeneration(items: List<HxTraceItem>): ChatMessage.ToolCall? =
    items.lastOrNull { it is HxTraceItem.Tool && it.call.toolName == "image_generate" && it.call.isRunning }
        ?.let { (it as HxTraceItem.Tool).call }

/**
 * Where the image will appear while `image_generate` runs, as Hermes desktop shows it
 * (generated-image-result.tsx): a frame in the asked aspect ratio with a light sweeping
 * over it. The ratio is only a hint of the shape, like the desktop's; the image itself
 * still comes in the reply.
 */
@Composable
internal fun ImageGenerationPlaceholder(call: ChatMessage.ToolCall, modifier: Modifier = Modifier) {
    val ratio = remember(call.argsText) { hintedRatio(call.argsText) }
    val base = MaterialTheme.colorScheme.surfaceVariant
    val glow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val reduceMotion = rememberReduceMotion()
    val sweep = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "image_generation").animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
            label = "image_generation_sweep",
        )
    }
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .widthIn(max = if (ratio < 1f) 200.dp else 320.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(16.dp))
            .background(base)
            // Read in the draw phase only, so the sweep never recomposes the reply.
            .drawBehind {
                val at = sweep?.value ?: return@drawBehind
                val x = size.width * at
                val band = size.width * 0.4f
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(glow.copy(alpha = 0f), glow, glow.copy(alpha = 0f)),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                stringResource(R.string.generating_image),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Hermes' `aspect_ratio` argument as a width/height ratio; landscape when absent, like the desktop. */
private fun hintedRatio(argsText: String?): Float {
    val asked = argsText?.let {
        runCatching { (Json.parseToJsonElement(it) as? JsonObject)?.get("aspect_ratio") as? JsonPrimitive }
            .getOrNull()?.content?.trim()?.lowercase()
    }
    return when (asked) {
        "square" -> 1f
        "portrait" -> 9f / 16f
        else -> 16f / 9f
    }
}
