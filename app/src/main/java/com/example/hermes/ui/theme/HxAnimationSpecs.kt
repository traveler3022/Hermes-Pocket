package com.example.hermes.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * تعریف انیمیشن‌های مشترک برای کل پروژه
 * الهام گرفته از Aether اما با استفاده از Easingهای استاندارد Compose
 */

object HxAnimationSpecs {
    
    /**
     * مدت زمان انیمیشن‌های کوتاه (مثل کلیک، تغییر آیکون)
     */
    const val DurationShort = 150
    
    /**
     * مدت زمان انیمیشن‌های متوسط (مثل باز شدن منو)
     */
    const val DurationMedium = 300
    
    /**
     * مدت زمان انیمیشن‌های بلند (مثل تایپ‌رایتر، ظاهر شدن محتوا)
     */
    const val DurationLong = 500
    
    /**
     * Easing برای حرکت‌های طبیعی و نرم
     * مشابه Aether اما با استفاده از FastOutSlowInEasing استاندارد
     */
    val smoothEasing = FastOutSlowInEasing
    
    /**
     * Easing خطی برای انیمیشن‌های تکرارشونده (مثل شیمِر)
     */
    val linearEasing = LinearEasing
    
    /**
     * Spring Specification برای انیمیشن‌های فیزیکی
     */
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    /**
     * Tween Specification برای انیمیشن‌های زمانی دقیق
     * @param duration مدت زمان انیمیشن
     * @param delay تأخیر قبل از شروع
     */
    fun tweenSpec(duration: Int = DurationMedium, delay: Int = 0) = 
        tween<Float>(durationMillis = duration, delayMillis = delay, easing = smoothEasing)
    
    /**
     * انیمیشن ضربان (Pulse) برای نشانگرهای در حال پردازش
     * @param targetValue مقدار هدف برای ضربان (مثلاً 0.6f تا 1f)
     */
    fun pulseSpec(targetValue: Float = 0.6f) = 
        tween<Float>(durationMillis = 800, easing = smoothEasing)
    
    /**
     * انیمیشن ظاهر شدن نرم برای محتوا
     */
    val contentFadeIn = tweenSpec(DurationMedium)
    
    /**
     * انیمیشن جمع‌شوندگی (Collapse/Expand)
     */
    val expandCollapse = springSpec
    
    /**
     * شعاع گردی استاندارد برای کامپوننت‌ها
     */
    val cornerRadiusSmall: Dp = 8.dp
    val cornerRadiusMedium: Dp = 12.dp
    val cornerRadiusLarge: Dp = 16.dp
    val cornerRadiusXLarge: Dp = 24.dp
    
    /**
     * اندازه‌های استاندارد برای Glyph و آیکون‌ها
     */
    val glyphSize: Dp = 24.dp
    val glyphSizeSmall: Dp = 16.dp
    
    /**
     * ضخامت خطوط اتصال در Timeline
     */
    val connectorLineWidth: Dp = 2.dp
    
    /**
     * فاصله استاندارد بین مراحل Thinking
     */
    val stepSpacing: Dp = 12.dp
}
