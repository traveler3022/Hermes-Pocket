package com.example.hermes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * رنگ‌های اختصاصی برای بلوک Thinking/Reasoning
 * الهام گرفته از Aether اما با استفاده از MaterialTheme پروژه
 */

object HxReasoningColors {
    
    /**
     * رنگ اصلی نوار کناری (Accent Bar)
     * بر اساس primary رنگ تم، اما با اشباع بیشتر برای جلب توجه
     */
    val accentBar: Color
        get() = MaterialTheme.colorScheme.primary
    
    /**
     * رنگ پس‌زمینه بلوک Thinking (بسیار شفاف)
     */
    val background: Color
        get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
    
    /**
     * رنگ متن اصلی در حالت Thinking
     */
    val textPrimary: Color
        get() = MaterialTheme.colorScheme.onSurface
    
    /**
     * رنگ متن ثانویه (برای توضیحات)
     */
    val textSecondary: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant
    
    /**
     * رنگ خط اتصال در Timeline
     */
    val connectorLine: Color
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    
    /**
     * رنگ Glyph (آیکون وضعیت)
     */
    val glyphBackground: Color
        get() = MaterialTheme.colorScheme.primaryContainer
    
    val glyphContent: Color
        get() = MaterialTheme.colorScheme.onPrimaryContainer
    
    /**
     * رنگ شیمِر (انیمیشن بارگذاری)
     */
    val shimmerBase: Color
        get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    
    val shimmerHighlight: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    
    /**
     * رنگ موفقیت (برای مراحل تکمیل شده)
     */
    val success: Color
        get() = Color(0xFF4CAF50)
    
    /**
     * رنگ در حال پردازش (انیمیشن ضربان)
     */
    val processing: Color
        get() = MaterialTheme.colorScheme.primary
    
    /**
     * رنگ خطا
     */
    val error: Color
        get() = Color(0xFFF44336)
}
