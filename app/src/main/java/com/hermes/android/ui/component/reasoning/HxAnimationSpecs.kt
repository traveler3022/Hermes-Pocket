package com.hermes.android.ui.component.reasoning

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * مشخصات انیمیشن‌های مشترک برای Thinking Block
 * الهام گرفته از Aether اما با بهینه‌سازی برای پروژه Hermes
 * 
 * ویژگی‌ها:
 * - Easing سفارشی برای حرکت‌های نرم
 * - Duration بهینه برای موبایل
 * - کاهش مصرف باتری با انیمیشن‌های هوشمند
 */

/**
 * Easing سفارشی شبیه به Aether برای حرکت‌های بسیار نرم
 * منحنی Cubic Bezier با کنترل نقطه‌های شروع و پایان
 */
val HxEasing: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

/**
 * Easing سریع برای ورود المان‌ها
 */
val HxFastInEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)

/**
 * Easing خروج نرم
 */
val HxSoftOutEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

/**
 * Duration استاندارد برای انیمیشن‌های Thinking
 */
object HxDuration {
    val Fast: Int = 150
    val Normal: Int = 300
    val Slow: Int = 450
    val Pulse: Int = 900
    val PulseSlow: Int = 1600
}

/**
 * AnimationSpec برای انیمیشن ضربان (Pulse) Thinking Bar
 * @param isLongTurn آیا نوبت طولانی است (بیش از ۲۰ ثانیه)
 * @return tween spec با duration مناسب
 */
@Composable
fun thinkingPulseSpec(isLongTurn: Boolean) = tween<Float>(
    durationMillis = if (isLongTurn) HxDuration.PulseSlow else HxDuration.Pulse,
    easing = FastOutSlowInEasing
)

/**
 * AnimationSpec برای باز/بسته شدن Thinking Block
 * با افکت Fade + Slide از پایین
 */
@Composable
fun thinkingExpandSpec() = tween<Int>(
    durationMillis = HxDuration.Normal,
    easing = HxEasing
)

/**
 * AnimationSpec برای ظاهر شدن Mood Sticker
 * با افکت Pop-in
 */
@Composable
fun moodStickerSpec() = tween<Float>(
    durationMillis = HxDuration.Fast,
    easing = HxFastInEasing
)

/**
 * AnimationSpec برای انیمیشن Typing Dots
 */
@Composable
fun typingDotSpec(index: Int) = tween<Float>(
    durationMillis = 600,
    delayMillis = index * 150,
    easing = FastOutSlowInEasing
)

/**
 * محاسبه سایز حباب پیام بر اساس نوع محتوا
 * @param hasThinking آیا بلوک Thinking وجود دارد
 * @param hasCodeBlock آیا بلوک کد وجود دارد
 * @return حداکثر عرض مناسب
 */
@Composable
fun messageBubbleMaxWidth(hasThinking: Boolean = false, hasCodeBlock: Boolean = false): Dp {
    return when {
        hasCodeBlock -> 500.dp  // فضای بیشتر برای کد
        hasThinking -> 480.dp   // فضای متعادل برای Thinking
        else -> 460.dp          // عرض استاندارد Hermes
    }
}

/**
 * محاسبه شعاع گوشه‌های حباب پیام
 * شکل نامتقارن برای آخرین پیام (ویژگی منحصر به فرد Hermes)
 * @param isLastMessage آیا این آخرین پیام است
 * @param isUserMessage آیا پیام کاربر است
 */
data class BubbleCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

@Composable
fun calculateBubbleCorners(
    isLastMessage: Boolean,
    isUserMessage: Boolean,
): BubbleCorners {
    val standardRadius = 16.dp
    val smallRadius = 4.dp
    
    return if (isLastMessage && isUserMessage) {
        // شکل نامتقارن برای آخرین پیام کاربر
        BubbleCorners(
            topStart = standardRadius,
            topEnd = standardRadius,
            bottomStart = standardRadius,
            bottomEnd = smallRadius,
        )
    } else {
        // شکل متقارن برای سایر پیام‌ها
        BubbleCorners(
            topStart = standardRadius,
            topEnd = standardRadius,
            bottomStart = standardRadius,
            bottomEnd = standardRadius,
        )
    }
}

/**
 * فاصله‌های استاندارد برای Thinking Block
 */
object HxSpacing {
    val BarWidth: Dp = 3.dp
    val BarHeight: Dp = 16.dp
    val BarToText: Dp = 8.dp
    val StepIndent: Dp = 11.dp
    val StepSpacing: Dp = 6.dp
    val HeaderPadding: Dp = 4.dp
    val ContentPadding: Dp = 8.dp
}
