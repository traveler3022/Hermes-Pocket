package com.hermes.android.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AetherType {
    val headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.9).sp,
    )
    val headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    )
    val titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
    )
    val titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    )
    val bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 28.sp)
    val bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

fun aetherTypography() = Typography(
    headlineLarge = AetherType.headlineLarge,
    headlineMedium = AetherType.headlineMedium,
    titleLarge = AetherType.titleLarge,
    titleMedium = AetherType.titleMedium,
    bodyLarge = AetherType.bodyLarge,
    bodyMedium = AetherType.bodyMedium,
    bodySmall = AetherType.bodySmall,
    labelLarge = AetherType.labelLarge,
    labelMedium = AetherType.labelMedium,
)
