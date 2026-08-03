package com.example.hermes.ui.components.thinking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * HxSmartThinkingBlock
 * بلوک هوشمند نمایش فرآیند تفکر (Thinking/Reasoning)
 * الهام گرفته از UX Aether اما پیاده‌سازی اختصاصی برای Hermes
 * تک‌فایل ماژولار (زیر ۳۰۰ خط)
 */

// --- تنظیمات رنگ و انیمیشن داخلی ---
private val HxThinkingDuration = 600
private val HxThinkingEasing = FastOutSlowInEasing

@Composable
private fun HxThinkingColors(): Triple<Color, Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val bg = scheme.primaryContainer.copy(alpha = 0.4f)
    val accent = scheme.primary
    val text = scheme.onSurfaceVariant
    return Triple(bg, accent, text)
}

@Composable
private fun HxThinkingGlyph(isExpanded: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "glyph_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(HxThinkingDuration, easing = HxThinkingEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun HxShimmerText(text: String, isVisible: Boolean) {
    if (!isVisible) return
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = HxThinkingEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )
    
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HxThinkingStepRow(step: String, index: Int, total: Int) {
    val (_, accentColor, _) = HxThinkingColors()
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.TopCenter) {
            if (index < total - 1) {
                Box(
                    modifier = Modifier.width(2.dp).height(40.dp)
                        .background(accentColor.copy(alpha = 0.3f))
                        .align(Alignment.TopCenter)
                )
            }
            Box(
                modifier = Modifier.size(8.dp)
                    .background(accentColor, RoundedCornerShape(4.dp))
                    .align(Alignment.TopCenter)
            )
        }
        
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HxSmartThinkingBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val (bgColor, accentColor, textColor) = HxThinkingColors()
    
    val steps = remember(reasoning) {
        reasoning.split("\n").filter { it.isNotBlank() && it.length > 5 }
    }
    
    val moodEmoji = remember(reasoning) {
        when {
            reasoning.contains("analyze", ignoreCase = true) || reasoning.contains("بررسی") -> "🤔"
            reasoning.contains("solve", ignoreCase = true) || reasoning.contains("حل") -> "💡"
            reasoning.contains("code", ignoreCase = true) || reasoning.contains("کد") -> "💻"
            else -> "✨"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HxThinkingGlyph(isExpanded = isExpanded)
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "در حال تفکر...",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                    if (!isExpanded && steps.isNotEmpty()) {
                        HxShimmerText(
                            text = steps.first().take(50) + if (steps.first().length > 50) "..." else "",
                            isVisible = true
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = moodEmoji, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.graphicsLayer { rotationZ = if (isExpanded) 180f else 0f }
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.8f),
                                    accentColor.copy(alpha = 0.2f)
                                )
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        steps.forEachIndexed { index, step ->
                            HxThinkingStepRow(step = step, index = index, total = steps.size)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}
