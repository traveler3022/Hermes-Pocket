package com.hermes.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hermes.android.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Hermes2 typography.
 *
 * Vazirmatn gives Persian UI text proper shaping/spacing while remaining clean
 * for English technical labels, model names, and logs.
 */
/** Builds the app typography around a chosen [fontFamily]. Defaults to
 *  Vazirmatn; pass [FontFamily.Default] to use the phone's own system font.
 *  Keeping one family across every text style is deliberate — a single,
 *  consistent reading rhythm is easier on the eyes than mixing faces.
 *
 *  [fontScalePct] applies a uniform percentage (80..140, default 100) to every
 *  fontSize/lineHeight in the scale, so a single slider in Settings scales
 *  the whole app's text together. */
fun hermesTypography(
    fontFamily: FontFamily = Vazirmatn,
    fontScalePct: Int = 100,
): Typography {
    val s = fontScalePct / 100f
    fun sp(value: Int) = (value * s).sp
    fun spF(value: Float) = (value * s).sp
    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(54),
            lineHeight = sp(64),
            letterSpacing = spF(-0.25f),
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(42),
            lineHeight = sp(52),
            letterSpacing = sp(0),
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(30),
            lineHeight = sp(40),
            letterSpacing = sp(0),
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = sp(22),
            lineHeight = sp(30),
            letterSpacing = sp(0),
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = sp(16),
            lineHeight = sp(24),
            letterSpacing = spF(0.1f),
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = sp(14),
            lineHeight = sp(22),
            letterSpacing = spF(0.1f),
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(15),
            lineHeight = sp(24),
            letterSpacing = sp(0),
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(14),
            lineHeight = sp(22),
            letterSpacing = sp(0),
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(12),
            lineHeight = sp(20),
            letterSpacing = sp(0),
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = sp(12),
            lineHeight = sp(18),
            letterSpacing = sp(0),
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = sp(11),
            lineHeight = sp(16),
            letterSpacing = sp(0),
        ),
    )
}
