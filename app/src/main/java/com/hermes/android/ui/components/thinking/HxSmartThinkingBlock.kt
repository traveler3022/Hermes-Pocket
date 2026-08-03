package com.hermes.android.ui.components.thinking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ========================
// 1. رنگ‌ها و انیمیشن‌ها (داخل همین فایل برای کاهش تعداد فایل‌ها)
// ========================

private val ThinkingAccentColor: @Composable () -> Color = {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
}

private val ThinkingBackgroundColor: @Composable () -> Color = {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
}

private val ThinkingTextColor: @Composable () -> Color = {
    MaterialTheme.colorScheme.onSurfaceVariant
}

private val ShimmerColors = listOf(
    Color.LightGray.copy(alpha = 0.6f),
    Color.LightGray.copy(alpha = 0.2f),
    Color.LightGray.copy(alpha = 0.6f)
)

val HxEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
val HxDuration = 400

// ========================
// 2. کامپوننت‌های داخلی (Private Composables)
// ========================

/** آیکون متحرک اول خط */
@Composable
private fun ThinkingGlyph(isExpanded: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "glyph")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ThinkingAccentColor(),
            modifier = Modifier.rotate(if (isExpanded) 90f else rotation)
        )
    }
}

/** متن شیمِر دار برای حالت لودینگ */
@Composable
private fun ShimmerText(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startX by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = HxEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerX"
    )

    val brush = Brush.linearGradient(
        colors = ShimmerColors,
        start = androidx.compose.ui.geometry.Offset(startX, 0f),
        end = androidx.compose.ui.geometry.Offset(startX + 1f, 0f)
    )

    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Transparent,
        background = brush
    )
}

/** یک مرحله از استدلال */
@Composable
private fun ReasoningStep(stepText: String, index: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // دایره شماره مرحله
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(ThinkingAccentColor().copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ThinkingAccentColor()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // متن مرحله
        Column {
            Text(
                text = stepText,
                fontSize = 14.sp,
                color = ThinkingTextColor(),
                lineHeight = 20.sp
            )
            
            // خط اتصال اگر آخرین مرحله نیست
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(ThinkingAccentColor().copy(alpha = 0.3f))
                        .padding(start = 9.dp)
                )
            }
        }
    }
}

// ========================
// 3. کامپوننت اصلی (Public API)
// ========================

/**
 * بلوک هوشمند نمایش فرآیند تفکر (Thinking Block)
 * الهام گرفته از UX Aether اما با استایل Hermes
 *
 * @param reasoning متن کامل فرآیند تفکر
 * @param modifier مادیفایر خارجی
 */
@Composable
fun HxSmartThinkingBlock(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    // شبیه‌سازی پایان لودینگ بعد از 2 ثانیه
    LaunchedEffect(Unit) {
        delay(2000)
        isLoading = false
    }

    // تقسیم متن به مراحل (ساده‌سازی شده بر اساس خطوط جدید)
    val steps = reasoning.split("\n").filter { it.isNotBlank() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = ThinkingBackgroundColor(),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // هدر
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThinkingGlyph(isExpanded)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        ShimmerText("در حال تفکر...")
                    } else {
                        Text(
                            text = "فرآیند تفکر",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ThinkingAccentColor()
                        )
                        Text(
                            text = if (isExpanded) "ضربه برای بستن" else "ضربه برای مشاهده جزئیات",
                            fontSize = 12.sp,
                            color = ThinkingTextColor().copy(alpha = 0.7f)
                        )
                    }
                }

                // فلش وضعیت
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ThinkingTextColor(),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (isExpanded) 180f else 0f)
                )
            }

            // محتوای قابل انبساط
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // خط عمودی سمت چپ
                Box(modifier = Modifier.padding(start = 11.dp)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height((steps.size * 40).dp)
                            .background(ThinkingAccentColor().copy(alpha = 0.2f))
                    )
                }
                
                Spacer(modifier = Modifier.height(-40.dp))

                steps.forEachIndexed { index, step ->
                    ReasoningStep(stepText = step, index = index, totalSteps = steps.size)
                }
            }
        }
    }
}
