package com.hermes.android.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ColorTheme(val key: String, val displayEn: String, val displayFa: String) {
    HERMES("hermes", "Hermes", "هرمس"),
    BLUE_EYE("blue_eye", "Blue Eye", "آبی چشم"),
    CLAUDE("claude", "Mocha", "موکا");

    companion object {
        fun fromKey(key: String): ColorTheme = entries.firstOrNull { it.key == key } ?: HERMES
    }
}

class ThemeModeState(context: Context) {
    private val prefs = context.getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)

    var mode: ThemeMode by mutableStateOf(
        ThemeMode.fromKey(prefs.getString("theme_mode", "system") ?: "system")
    )
        private set

    var colorTheme: ColorTheme by mutableStateOf(
        ColorTheme.fromKey(prefs.getString("color_theme", "hermes") ?: "hermes")
    )
        private set

    fun updateMode(newMode: ThemeMode) {
        mode = newMode
        prefs.edit().putString("theme_mode", newMode.key).apply()
    }

    fun updateColorTheme(newTheme: ColorTheme) {
        colorTheme = newTheme
        prefs.edit().putString("color_theme", newTheme.key).apply()
    }
}

// ── Default Hermes palette ──

private val LightColors = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    secondaryContainer = md_light_secondaryContainer,
    onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = md_light_tertiary,
    onTertiary = md_light_onTertiary,
    tertiaryContainer = md_light_tertiaryContainer,
    onTertiaryContainer = md_light_onTertiaryContainer,
    error = md_light_error,
    onError = md_light_onError,
    errorContainer = md_light_errorContainer,
    onErrorContainer = md_light_onErrorContainer,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    onSurfaceVariant = md_light_onSurfaceVariant,
    outline = md_light_outline,
    outlineVariant = md_light_outlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_onPrimary,
    primaryContainer = md_dark_primaryContainer,
    onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    secondaryContainer = md_dark_secondaryContainer,
    onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = md_dark_tertiary,
    onTertiary = md_dark_onTertiary,
    tertiaryContainer = md_dark_tertiaryContainer,
    onTertiaryContainer = md_dark_onTertiaryContainer,
    error = md_dark_error,
    onError = md_dark_onError,
    errorContainer = md_dark_errorContainer,
    onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant,
    onSurfaceVariant = md_dark_onSurfaceVariant,
    outline = md_dark_outline,
    outlineVariant = md_dark_outlineVariant,
)

// ── Blue Eye palette ──

private val BlueLightColors = lightColorScheme(
    primary = blue_light_primary,
    onPrimary = blue_light_onPrimary,
    primaryContainer = blue_light_primaryContainer,
    onPrimaryContainer = blue_light_onPrimaryContainer,
    secondary = blue_light_secondary,
    onSecondary = blue_light_onSecondary,
    secondaryContainer = blue_light_secondaryContainer,
    onSecondaryContainer = blue_light_onSecondaryContainer,
    tertiary = blue_light_tertiary,
    onTertiary = blue_light_onTertiary,
    tertiaryContainer = blue_light_tertiaryContainer,
    onTertiaryContainer = blue_light_onTertiaryContainer,
    error = blue_light_error,
    onError = blue_light_onError,
    errorContainer = blue_light_errorContainer,
    onErrorContainer = blue_light_onErrorContainer,
    background = blue_light_background,
    onBackground = blue_light_onBackground,
    surface = blue_light_surface,
    onSurface = blue_light_onSurface,
    surfaceVariant = blue_light_surfaceVariant,
    onSurfaceVariant = blue_light_onSurfaceVariant,
    outline = blue_light_outline,
    outlineVariant = blue_light_outlineVariant,
)

private val BlueDarkColors = darkColorScheme(
    primary = blue_dark_primary,
    onPrimary = blue_dark_onPrimary,
    primaryContainer = blue_dark_primaryContainer,
    onPrimaryContainer = blue_dark_onPrimaryContainer,
    secondary = blue_dark_secondary,
    onSecondary = blue_dark_onSecondary,
    secondaryContainer = blue_dark_secondaryContainer,
    onSecondaryContainer = blue_dark_onSecondaryContainer,
    tertiary = blue_dark_tertiary,
    onTertiary = blue_dark_onTertiary,
    tertiaryContainer = blue_dark_tertiaryContainer,
    onTertiaryContainer = blue_dark_onTertiaryContainer,
    error = blue_dark_error,
    onError = blue_dark_onError,
    errorContainer = blue_dark_errorContainer,
    onErrorContainer = blue_dark_onErrorContainer,
    background = blue_dark_background,
    onBackground = blue_dark_onBackground,
    surface = blue_dark_surface,
    onSurface = blue_dark_onSurface,
    surfaceVariant = blue_dark_surfaceVariant,
    onSurfaceVariant = blue_dark_onSurfaceVariant,
    outline = blue_dark_outline,
    outlineVariant = blue_dark_outlineVariant,
)

// ── Claude-style warm palette ──

private val ClaudeLightColors = lightColorScheme(
    primary = claude_light_primary,
    onPrimary = claude_light_onPrimary,
    primaryContainer = claude_light_primaryContainer,
    onPrimaryContainer = claude_light_onPrimaryContainer,
    secondary = claude_light_secondary,
    onSecondary = claude_light_onSecondary,
    secondaryContainer = claude_light_secondaryContainer,
    onSecondaryContainer = claude_light_onSecondaryContainer,
    tertiary = claude_light_tertiary,
    onTertiary = claude_light_onTertiary,
    tertiaryContainer = claude_light_tertiaryContainer,
    onTertiaryContainer = claude_light_onTertiaryContainer,
    error = claude_light_error,
    onError = claude_light_onError,
    errorContainer = claude_light_errorContainer,
    onErrorContainer = claude_light_onErrorContainer,
    background = claude_light_background,
    onBackground = claude_light_onBackground,
    surface = claude_light_surface,
    onSurface = claude_light_onSurface,
    surfaceVariant = claude_light_surfaceVariant,
    onSurfaceVariant = claude_light_onSurfaceVariant,
    outline = claude_light_outline,
    outlineVariant = claude_light_outlineVariant,
)

private val ClaudeDarkColors = darkColorScheme(
    primary = claude_dark_primary,
    onPrimary = claude_dark_onPrimary,
    primaryContainer = claude_dark_primaryContainer,
    onPrimaryContainer = claude_dark_onPrimaryContainer,
    secondary = claude_dark_secondary,
    onSecondary = claude_dark_onSecondary,
    secondaryContainer = claude_dark_secondaryContainer,
    onSecondaryContainer = claude_dark_onSecondaryContainer,
    tertiary = claude_dark_tertiary,
    onTertiary = claude_dark_onTertiary,
    tertiaryContainer = claude_dark_tertiaryContainer,
    onTertiaryContainer = claude_dark_onTertiaryContainer,
    error = claude_dark_error,
    onError = claude_dark_onError,
    errorContainer = claude_dark_errorContainer,
    onErrorContainer = claude_dark_onErrorContainer,
    background = claude_dark_background,
    onBackground = claude_dark_onBackground,
    surface = claude_dark_surface,
    onSurface = claude_dark_onSurface,
    surfaceVariant = claude_dark_surfaceVariant,
    onSurfaceVariant = claude_dark_onSurfaceVariant,
    outline = claude_dark_outline,
    outlineVariant = claude_dark_outlineVariant,
)

@Composable
fun Hermes2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.HERMES,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (colorTheme) {
            ColorTheme.HERMES -> if (useDark) DarkColors else LightColors
            ColorTheme.BLUE_EYE -> if (useDark) BlueDarkColors else BlueLightColors
            ColorTheme.CLAUDE -> if (useDark) ClaudeDarkColors else ClaudeLightColors
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HermesTypography,
        content = content
    )
}
