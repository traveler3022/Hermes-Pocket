package com.hermes.android.ui.component.reasoning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme

/**
 * پالت رنگی هوشمند برای بلوک‌های Thinking/Reasoning
 * الهام گرفته از Aether اما تطبیق یافته با MaterialTheme پروژه Hermes
 * 
 * ویژگی‌ها:
 * - کنتراست بالا برای خوانایی
 * - سازگار با تم‌های Light/Dark
 * - استفاده از رنگ‌های اصلی پروژه با تقویت شفافیت
 */
@Immutable
data class ReasoningColors(
    val accentBar: Color,
    val accentBarPulseMin: Float,
    val accentBarPulseMax: Float,
    val backgroundColor: Color,
    val contentColor: Color,
    val headerColor: Color,
    val previewAlpha: Float,
    val collapsedOverlay: Color,
    val glyphBorder: Color,
    val glyphBackground: Color,
    val stepBullet: Color,
    val connectorLine: Color,
)

@Composable
fun reasoningColors(): ReasoningColors {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.value == 0xFFFFFFFFL
    
    return if (isLight) {
        lightReasoningColors(colors)
    } else {
        darkReasoningColors(colors)
    }
}

@Composable
private fun lightReasoningColors(colors: ColorScheme): ReasoningColors {
    // Accent Bar: رنگ اصلی با شفافیت دینامیک برای انیمیشن ضربان
    val accentBase = colors.primary
    val accentBg = colors.primaryContainer.copy(alpha = 0.5f)
    
    return ReasoningColors(
        accentBar = accentBase,
        accentBarPulseMin = 0.4f,
        accentBarPulseMax = 0.9f,
        backgroundColor = accentBg,
        contentColor = colors.onPrimaryContainer,
        headerColor = colors.primary.copy(alpha = 0.8f),
        previewAlpha = 0.7f,
        collapsedOverlay = Color.Black.copy(alpha = 0.03f),
        glyphBorder = colors.outlineVariant,
        glyphBackground = colors.surface,
        stepBullet = colors.tertiary,
        connectorLine = colors.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun darkReasoningColors(colors: ColorScheme): ReasoningColors {
    // Accent Bar: رنگ روشن‌تر برای کنتراست در تم تاریک
    val accentBase = colors.primary
    val accentBg = colors.primaryContainer.copy(alpha = 0.15f)
    
    return ReasoningColors(
        accentBar = accentBase,
        accentBarPulseMin = 0.5f,
        accentBarPulseMax = 1.0f,
        backgroundColor = accentBg,
        contentColor = colors.onPrimaryContainer,
        headerColor = colors.primary.copy(alpha = 0.9f),
        previewAlpha = 0.8f,
        collapsedOverlay = Color.White.copy(alpha = 0.02f),
        glyphBorder = colors.outlineVariant.copy(alpha = 0.3f),
        glyphBackground = colors.surfaceVariant,
        stepBullet = colors.tertiary,
        connectorLine = colors.outlineVariant.copy(alpha = 0.4f),
    )
}

/**
 * رنگ‌های مخصوص Mood Sticker (ایموجی احساسی)
 * بازگشت رنگ پس‌زمینه نیمه‌شفاف برای هایلایت ایموجی
 */
@Composable
fun moodStickerBackground(): Color {
    val colors = MaterialTheme.colorScheme
    return colors.secondaryContainer.copy(alpha = 0.3f)
}

/**
 * محاسبه رنگ نوار پیشرفت Thinking بر اساس وضعیت
 * @param progress درصد پیشرفت (0.0 تا 1.0)
 * @param isStreaming آیا در حال پردازش است
 */
@Composable
fun thinkingProgressColor(progress: Float, isStreaming: Boolean): Color {
    val colors = reasoningColors()
    return if (isStreaming) {
        colors.accentBar
    } else {
        colors.accentBar.copy(alpha = 0.5f)
    }
}
