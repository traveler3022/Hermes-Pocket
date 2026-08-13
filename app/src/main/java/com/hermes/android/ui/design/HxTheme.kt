package com.hermes.android.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

object HxTheme {
    val lightColorScheme: ColorScheme
        @Composable get() = aetherLightColors(LightAetherPalette)

    val darkColorScheme: ColorScheme
        @Composable get() = aetherDarkColors(DarkAetherPalette)

    @Composable
    operator fun invoke(
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable () -> Unit,
    ) {
        val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme
        val layoutDirection = LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            MaterialTheme(colorScheme = colorScheme, content = content)
        }
    }
}

private fun aetherLightColors(p: AetherPalette) = lightColorScheme(
    primary = p.primary,
    onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondary = p.secondary,
    onSecondary = p.onSecondary,
    secondaryContainer = p.secondaryContainer,
    onSecondaryContainer = p.onSecondaryContainer,
    background = p.background,
    surface = p.surface,
    surfaceVariant = p.surfaceVariant,
    onSurface = p.onSurface,
    onSurfaceVariant = p.onSurfaceVariant,
    tertiary = p.tertiary,
    error = p.error,
    outline = p.outline,
    outlineVariant = p.outlineSoft,
)

private fun aetherDarkColors(p: AetherPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondary = p.secondary,
    onSecondary = p.onSecondary,
    secondaryContainer = p.secondaryContainer,
    onSecondaryContainer = p.onSecondaryContainer,
    background = p.background,
    surface = p.surface,
    surfaceVariant = p.surfaceVariant,
    onSurface = p.onSurface,
    onSurfaceVariant = p.onSurfaceVariant,
    tertiary = p.tertiary,
    error = p.error,
    outline = p.outline,
    outlineVariant = p.outlineSoft,
)

data class AetherPalette(
    val background: Color,
    val backgroundGradientTop: Color,
    val settingsBackground: Color,
    val sidebarBackground: Color,
    val sidebarControl: Color,
    val settingsIcon: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHigher: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val outlineSoft: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val error: Color,
    val messageBubble: Color,
    val scrim: Color,
)

val LightAetherPalette = AetherPalette(
    background = Color(0xFFFFFFFF),
    backgroundGradientTop = Color(0xFFFFFFFF),
    settingsBackground = Color(0xFFF2F2F7),
    sidebarBackground = Color(0xFFF9F9F9),
    sidebarControl = Color(0xFFF3F3F2),
    settingsIcon = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF3F3F2),
    surfaceHigher = Color(0xFFECECEC),
    surfaceVariant = Color(0xFFE5E5E5),
    outline = Color(0xFFD9D9D9),
    outlineSoft = Color(0xFFE7E7E7),
    onSurface = Color(0xFF202123),
    onSurfaceVariant = Color(0xFF6B6B6B),
    primary = Color(0xFFAD7BF9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1E5FF),
    onPrimaryContainer = Color(0xFF4D2F8E),
    secondary = Color(0xFF4A7B6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF1E8),
    onSecondaryContainer = Color(0xFF1E4A3B),
    tertiary = Color(0xFF9A7DF8),
    error = Color(0xFFB43E3E),
    messageBubble = Color(0xFFF0E3FF),
    scrim = Color(0x22000000),
)

val DarkAetherPalette = AetherPalette(
    background = Color(0xFF151619),
    backgroundGradientTop = Color(0xFF1B1D22),
    settingsBackground = Color(0xFF151619),
    sidebarBackground = Color(0xFF1C1F23),
    sidebarControl = Color(0xFF24282D),
    settingsIcon = Color(0xFFF3F1EC),
    surface = Color(0xFF1C1F23),
    surfaceHigh = Color(0xFF24282D),
    surfaceHigher = Color(0xFF2C3036),
    surfaceVariant = Color(0xFF343941),
    outline = Color(0xFF4A5059),
    outlineSoft = Color(0xFF3D424A),
    onSurface = Color(0xFFF3F1EC),
    onSurfaceVariant = Color(0xFFB9B4AA),
    primary = Color(0xFFC0AEFF),
    onPrimary = Color(0xFF251448),
    primaryContainer = Color(0xFF3A275F),
    onPrimaryContainer = Color(0xFFF0E9FF),
    secondary = Color(0xFF89C8AF),
    onSecondary = Color(0xFF143126),
    secondaryContainer = Color(0xFF24483A),
    onSecondaryContainer = Color(0xFFDDF6EA),
    tertiary = Color(0xFFD1C2FF),
    error = Color(0xFFFF8E8E),
    messageBubble = Color(0xFF32264A),
    scrim = Color(0x66000000),
)
