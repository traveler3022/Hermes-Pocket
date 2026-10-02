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
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(36),
            lineHeight = sp(44),
            letterSpacing = sp(0),
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(30),
            lineHeight = sp(40),
            letterSpacing = sp(0),
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sp(26),
            lineHeight = sp(34),
            letterSpacing = sp(0),
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = sp(24),
            lineHeight = sp(32),
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
        // Every Material button, text button and tab draws its label in labelLarge. It was
        // missing here, so it fell back to Material's default in the system font: buttons were
        // in a different face and weight from the rest of the app.
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = sp(14),
            lineHeight = sp(20),
            letterSpacing = spF(0.1f),
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

/**
 * Aether's type scale (its getAetherTypography): larger body text with taller lines and
 * semibold headings. Only sizes and weights change; the chosen font and the font-size
 * slider still apply.
 */
fun Typography.aetherSized(fontScalePct: Int): Typography {
    val s = fontScalePct / 100f
    fun TextStyle.sized(size: Float, line: Float, weight: FontWeight, spacing: Float = 0f) = copy(
        fontSize = (size * s).sp,
        lineHeight = (line * s).sp,
        fontWeight = weight,
        letterSpacing = spacing.sp,
    )
    return copy(
        headlineLarge = headlineLarge.sized(34f, 40f, FontWeight.SemiBold, -0.9f),
        headlineMedium = headlineMedium.sized(29f, 36f, FontWeight.SemiBold, -0.5f),
        titleLarge = titleLarge.sized(24f, 31f, FontWeight.SemiBold),
        titleMedium = titleMedium.sized(18f, 25f, FontWeight.Medium),
        bodyLarge = bodyLarge.sized(17f, 28f, FontWeight.Normal),
        bodyMedium = bodyMedium.sized(15f, 24f, FontWeight.Normal),
        bodySmall = bodySmall.sized(13f, 18f, FontWeight.Normal),
        labelLarge = labelLarge.sized(14f, 20f, FontWeight.Medium),
        labelMedium = labelMedium.sized(13f, 18f, FontWeight.Medium),
    )
}
