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
import com.hermes.android.ui.viewmodel.MESSAGE_REACTIONS_PREF

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AppFont(val key: String, val displayEn: String, val displayFa: String) {
    // Bundled Vazirmatn — shapes Persian correctly and stays clean for Latin.
    VAZIR("vazir", "Vazirmatn", "وزیر"),
    // The phone's own system font (whatever the user set device-wide).
    SYSTEM("system", "System font", "فونت گوشی");

    companion object {
        fun fromKey(key: String): AppFont = entries.firstOrNull { it.key == key } ?: VAZIR
    }
}

enum class ColorTheme(val key: String, val displayEn: String, val displayFa: String) {
    HERMES("hermes", "Hermes", "هرمس"),
    BLUE_EYE("blue_eye", "Blue Eye", "آبی چشم"),
    // The key stays "claude" so a saved choice survives.
    MOCHA("claude", "Mocha", "موکا"),
    MIDNIGHT("midnight", "Midnight", "میدنایت"),
    INDIGO_PRO("indigo_pro", "Indigo", "ایندیگو"),
    CARBON("carbon", "Carbon", "کربن");

    companion object {
        // Carbon (near-black + neutral grey) is the default.
        fun fromKey(key: String): ColorTheme = entries.firstOrNull { it.key == key } ?: CARBON
    }
}

class ThemeModeState(context: Context) {
    private val prefs = context.getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)

    var mode: ThemeMode by mutableStateOf(
        ThemeMode.fromKey(prefs.getString("theme_mode", "system") ?: "system")
    )
        private set

    var colorTheme: ColorTheme by mutableStateOf(
        ColorTheme.fromKey(prefs.getString("color_theme", "carbon") ?: "carbon")
    )
        private set

    // Night/warm reading mode — shifts background/surface toward a soft
    // amber tint (f.lux-style) to cut blue-light emission during long
    // sessions, independent of which color theme is active.
    var warmMode: Boolean by mutableStateOf(prefs.getBoolean("warm_mode", false))
        private set

    // Which font face the whole app is drawn with (Vazirmatn or the phone's
    // own system font). One family across every screen = one reading rhythm.
    var appFont: AppFont by mutableStateOf(
        AppFont.fromKey(prefs.getString("app_font", "vazir") ?: "vazir")
    )
        private set

    // Font size multiplier applied app-wide. Stored as an integer 80..140 (%)
    // so it survives prefs round-trip cleanly; converted to float when
    // building typography. 100 = the designer-set baseline; 80 = smaller,
    // 140 = larger. Default 100.
    var fontScalePct: Int by mutableStateOf(
        prefs.getInt("font_scale_pct", 100)
    )
        private set

    // An agent turn arrives as many messages: the gateway opens a new one for
    // every stretch of narration between tool calls. By default only the
    // message that ends a turn stays on the chat surface and the rest fold into
    // its trace, which keeps the chat readable but hides the running commentary
    // some people want to watch. Turning this on puts every fragment back in
    // the flow.
    var showInlineNarration: Boolean by mutableStateOf(
        prefs.getBoolean("show_inline_narration", false)
    )
        private set

    // Tapback reactions on messages. Off by default, as on the desktop: turning it on
    // also gives the agent its react_to_message tool (ChatViewModel tells the gateway).
    var messageReactions: Boolean by mutableStateOf(
        prefs.getBoolean(MESSAGE_REACTIONS_PREF, false)
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

    fun updateWarmMode(enabled: Boolean) {
        warmMode = enabled
        prefs.edit().putBoolean("warm_mode", enabled).apply()
    }

    fun updateShowInlineNarration(enabled: Boolean) {
        showInlineNarration = enabled
        prefs.edit().putBoolean("show_inline_narration", enabled).apply()
    }

    fun updateMessageReactions(enabled: Boolean) {
        messageReactions = enabled
        prefs.edit().putBoolean(MESSAGE_REACTIONS_PREF, enabled).apply()
    }

    fun updateAppFont(newFont: AppFont) {
        appFont = newFont
        prefs.edit().putString("app_font", newFont.key).apply()
    }

    fun updateFontScalePct(pct: Int) {
        val clamped = pct.coerceIn(80, 140)
        fontScalePct = clamped
        prefs.edit().putInt("font_scale_pct", clamped).apply()
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

// ── Midnight palette ──

private val MidnightLightColors = lightColorScheme(
    primary = midnight_light_primary,
    onPrimary = midnight_light_onPrimary,
    primaryContainer = midnight_light_primaryContainer,
    onPrimaryContainer = midnight_light_onPrimaryContainer,
    secondary = midnight_light_secondary,
    onSecondary = midnight_light_onSecondary,
    secondaryContainer = midnight_light_secondaryContainer,
    onSecondaryContainer = midnight_light_onSecondaryContainer,
    tertiary = midnight_light_tertiary,
    onTertiary = midnight_light_onTertiary,
    tertiaryContainer = midnight_light_tertiaryContainer,
    onTertiaryContainer = midnight_light_onTertiaryContainer,
    error = midnight_light_error,
    onError = midnight_light_onError,
    errorContainer = midnight_light_errorContainer,
    onErrorContainer = midnight_light_onErrorContainer,
    background = midnight_light_background,
    onBackground = midnight_light_onBackground,
    surface = midnight_light_surface,
    onSurface = midnight_light_onSurface,
    surfaceVariant = midnight_light_surfaceVariant,
    onSurfaceVariant = midnight_light_onSurfaceVariant,
    outline = midnight_light_outline,
    outlineVariant = midnight_light_outlineVariant,
)

private val MidnightDarkColors = darkColorScheme(
    primary = midnight_dark_primary,
    onPrimary = midnight_dark_onPrimary,
    primaryContainer = midnight_dark_primaryContainer,
    onPrimaryContainer = midnight_dark_onPrimaryContainer,
    secondary = midnight_dark_secondary,
    onSecondary = midnight_dark_onSecondary,
    secondaryContainer = midnight_dark_secondaryContainer,
    onSecondaryContainer = midnight_dark_onSecondaryContainer,
    tertiary = midnight_dark_tertiary,
    onTertiary = midnight_dark_onTertiary,
    tertiaryContainer = midnight_dark_tertiaryContainer,
    onTertiaryContainer = midnight_dark_onTertiaryContainer,
    error = midnight_dark_error,
    onError = midnight_dark_onError,
    errorContainer = midnight_dark_errorContainer,
    onErrorContainer = midnight_dark_onErrorContainer,
    background = midnight_dark_background,
    onBackground = midnight_dark_onBackground,
    surface = midnight_dark_surface,
    onSurface = midnight_dark_onSurface,
    surfaceVariant = midnight_dark_surfaceVariant,
    onSurfaceVariant = midnight_dark_onSurfaceVariant,
    outline = midnight_dark_outline,
    outlineVariant = midnight_dark_outlineVariant,
)

// ── Indigo palette ──

private val IndigoLightColors = lightColorScheme(
    primary = indigo_light_primary,
    onPrimary = indigo_light_onPrimary,
    primaryContainer = indigo_light_primaryContainer,
    onPrimaryContainer = indigo_light_onPrimaryContainer,
    secondary = indigo_light_secondary,
    onSecondary = indigo_light_onSecondary,
    secondaryContainer = indigo_light_secondaryContainer,
    onSecondaryContainer = indigo_light_onSecondaryContainer,
    tertiary = indigo_light_tertiary,
    onTertiary = indigo_light_onTertiary,
    tertiaryContainer = indigo_light_tertiaryContainer,
    onTertiaryContainer = indigo_light_onTertiaryContainer,
    error = indigo_light_error,
    onError = indigo_light_onError,
    errorContainer = indigo_light_errorContainer,
    onErrorContainer = indigo_light_onErrorContainer,
    background = indigo_light_background,
    onBackground = indigo_light_onBackground,
    surface = indigo_light_surface,
    onSurface = indigo_light_onSurface,
    surfaceVariant = indigo_light_surfaceVariant,
    onSurfaceVariant = indigo_light_onSurfaceVariant,
    outline = indigo_light_outline,
    outlineVariant = indigo_light_outlineVariant,
)

private val IndigoDarkColors = darkColorScheme(
    primary = indigo_dark_primary,
    onPrimary = indigo_dark_onPrimary,
    primaryContainer = indigo_dark_primaryContainer,
    onPrimaryContainer = indigo_dark_onPrimaryContainer,
    secondary = indigo_dark_secondary,
    onSecondary = indigo_dark_onSecondary,
    secondaryContainer = indigo_dark_secondaryContainer,
    onSecondaryContainer = indigo_dark_onSecondaryContainer,
    tertiary = indigo_dark_tertiary,
    onTertiary = indigo_dark_onTertiary,
    tertiaryContainer = indigo_dark_tertiaryContainer,
    onTertiaryContainer = indigo_dark_onTertiaryContainer,
    error = indigo_dark_error,
    onError = indigo_dark_onError,
    errorContainer = indigo_dark_errorContainer,
    onErrorContainer = indigo_dark_onErrorContainer,
    background = indigo_dark_background,
    onBackground = indigo_dark_onBackground,
    surface = indigo_dark_surface,
    onSurface = indigo_dark_onSurface,
    surfaceVariant = indigo_dark_surfaceVariant,
    onSurfaceVariant = indigo_dark_onSurfaceVariant,
    outline = indigo_dark_outline,
    outlineVariant = indigo_dark_outlineVariant,
)

// ── Carbon — Perplexity-inspired: near-black + teal ──

private val CarbonLightColors = lightColorScheme(
    primary = carbon_light_primary,
    onPrimary = carbon_light_onPrimary,
    primaryContainer = carbon_light_primaryContainer,
    onPrimaryContainer = carbon_light_onPrimaryContainer,
    secondary = carbon_light_secondary,
    onSecondary = carbon_light_onSecondary,
    secondaryContainer = carbon_light_secondaryContainer,
    onSecondaryContainer = carbon_light_onSecondaryContainer,
    tertiary = carbon_light_tertiary,
    onTertiary = carbon_light_onTertiary,
    tertiaryContainer = carbon_light_tertiaryContainer,
    onTertiaryContainer = carbon_light_onTertiaryContainer,
    error = carbon_light_error,
    onError = carbon_light_onError,
    errorContainer = carbon_light_errorContainer,
    onErrorContainer = carbon_light_onErrorContainer,
    background = carbon_light_background,
    onBackground = carbon_light_onBackground,
    surface = carbon_light_surface,
    onSurface = carbon_light_onSurface,
    surfaceVariant = carbon_light_surfaceVariant,
    onSurfaceVariant = carbon_light_onSurfaceVariant,
    outline = carbon_light_outline,
    outlineVariant = carbon_light_outlineVariant,
)

private val CarbonDarkColors = darkColorScheme(
    primary = carbon_dark_primary,
    onPrimary = carbon_dark_onPrimary,
    primaryContainer = carbon_dark_primaryContainer,
    onPrimaryContainer = carbon_dark_onPrimaryContainer,
    secondary = carbon_dark_secondary,
    onSecondary = carbon_dark_onSecondary,
    secondaryContainer = carbon_dark_secondaryContainer,
    onSecondaryContainer = carbon_dark_onSecondaryContainer,
    tertiary = carbon_dark_tertiary,
    onTertiary = carbon_dark_onTertiary,
    tertiaryContainer = carbon_dark_tertiaryContainer,
    onTertiaryContainer = carbon_dark_onTertiaryContainer,
    error = carbon_dark_error,
    onError = carbon_dark_onError,
    errorContainer = carbon_dark_errorContainer,
    onErrorContainer = carbon_dark_onErrorContainer,
    background = carbon_dark_background,
    onBackground = carbon_dark_onBackground,
    surface = carbon_dark_surface,
    onSurface = carbon_dark_onSurface,
    surfaceVariant = carbon_dark_surfaceVariant,
    onSurfaceVariant = carbon_dark_onSurfaceVariant,
    outline = carbon_dark_outline,
    outlineVariant = carbon_dark_outlineVariant,
)

// ── Mocha palette ──

private val MochaLightColors = lightColorScheme(
    primary = mocha_light_primary,
    onPrimary = mocha_light_onPrimary,
    primaryContainer = mocha_light_primaryContainer,
    onPrimaryContainer = mocha_light_onPrimaryContainer,
    secondary = mocha_light_secondary,
    onSecondary = mocha_light_onSecondary,
    secondaryContainer = mocha_light_secondaryContainer,
    onSecondaryContainer = mocha_light_onSecondaryContainer,
    tertiary = mocha_light_tertiary,
    onTertiary = mocha_light_onTertiary,
    tertiaryContainer = mocha_light_tertiaryContainer,
    onTertiaryContainer = mocha_light_onTertiaryContainer,
    error = mocha_light_error,
    onError = mocha_light_onError,
    errorContainer = mocha_light_errorContainer,
    onErrorContainer = mocha_light_onErrorContainer,
    background = mocha_light_background,
    onBackground = mocha_light_onBackground,
    surface = mocha_light_surface,
    onSurface = mocha_light_onSurface,
    surfaceVariant = mocha_light_surfaceVariant,
    onSurfaceVariant = mocha_light_onSurfaceVariant,
    outline = mocha_light_outline,
    outlineVariant = mocha_light_outlineVariant,
)

private val MochaDarkColors = darkColorScheme(
    primary = mocha_dark_primary,
    onPrimary = mocha_dark_onPrimary,
    primaryContainer = mocha_dark_primaryContainer,
    onPrimaryContainer = mocha_dark_onPrimaryContainer,
    secondary = mocha_dark_secondary,
    onSecondary = mocha_dark_onSecondary,
    secondaryContainer = mocha_dark_secondaryContainer,
    onSecondaryContainer = mocha_dark_onSecondaryContainer,
    tertiary = mocha_dark_tertiary,
    onTertiary = mocha_dark_onTertiary,
    tertiaryContainer = mocha_dark_tertiaryContainer,
    onTertiaryContainer = mocha_dark_onTertiaryContainer,
    error = mocha_dark_error,
    onError = mocha_dark_onError,
    errorContainer = mocha_dark_errorContainer,
    onErrorContainer = mocha_dark_onErrorContainer,
    background = mocha_dark_background,
    onBackground = mocha_dark_onBackground,
    surface = mocha_dark_surface,
    onSurface = mocha_dark_onSurface,
    surfaceVariant = mocha_dark_surfaceVariant,
    onSurfaceVariant = mocha_dark_onSurfaceVariant,
    outline = mocha_dark_outline,
    outlineVariant = mocha_dark_outlineVariant,
)

@Composable
fun Hermes2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.CARBON,
    dynamicColor: Boolean = false,
    warmMode: Boolean = false,
    appFont: AppFont = AppFont.VAZIR,
    fontScalePct: Int = 100,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    // Wallpaper colours come with all their surface roles; only the hand-made palettes need them derived.
    val isDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (colorTheme) {
            ColorTheme.HERMES -> if (useDark) DarkColors else LightColors
            ColorTheme.BLUE_EYE -> if (useDark) BlueDarkColors else BlueLightColors
            ColorTheme.MOCHA -> if (useDark) MochaDarkColors else MochaLightColors
            ColorTheme.MIDNIGHT -> if (useDark) MidnightDarkColors else MidnightLightColors
            ColorTheme.INDIGO_PRO -> if (useDark) IndigoDarkColors else IndigoLightColors
            ColorTheme.CARBON -> if (useDark) CarbonDarkColors else CarbonLightColors
        }
    }

    val fontFamily = when (appFont) {
        AppFont.VAZIR -> Vazirmatn
        AppFont.SYSTEM -> androidx.compose.ui.text.font.FontFamily.Default
    }

    MaterialTheme(
        colorScheme = (if (warmMode) colorScheme.warmed() else colorScheme)
            .let { if (isDynamic) it else it.withSurfaceContainers(useDark) },
        typography = hermesTypography(fontFamily, fontScalePct),
        content = content
    )
}

/** Night/warm reading mode: nudges the large background/surface areas
 *  toward a soft amber tint (f.lux-style), reducing blue-light emission
 *  during long sessions. Text/icon colors are left untouched so the
 *  contrast ratios verified elsewhere stay intact. */
private fun androidx.compose.material3.ColorScheme.warmed(): androidx.compose.material3.ColorScheme {
    val warmTint = androidx.compose.ui.graphics.Color(0xFFFFD9A0)
    fun androidx.compose.ui.graphics.Color.warm(strength: Float) =
        androidx.compose.ui.graphics.lerp(this, warmTint, strength)
    return copy(
        background = background.warm(0.10f),
        surface = surface.warm(0.10f),
        surfaceVariant = surfaceVariant.warm(0.10f),
        primaryContainer = primaryContainer.warm(0.06f),
        secondaryContainer = secondaryContainer.warm(0.06f),
        tertiaryContainer = tertiaryContainer.warm(0.06f),
    )
}

/**
 * The palettes above set surface and surfaceVariant but none of Material 3's surface
 * container roles, and those are what dialogs, bottom sheets, menus and cards are painted
 * with. Left unset they fell back to Material's baseline, a lavender-tinted grey: every
 * dialog in the neutral Carbon theme came up faintly purple. Derived here from each
 * palette's own surface, stepping toward its text colour as Material's tones do.
 */
private fun androidx.compose.material3.ColorScheme.withSurfaceContainers(
    dark: Boolean,
): androidx.compose.material3.ColorScheme {
    fun step(fraction: Float) = androidx.compose.ui.graphics.lerp(surface, onSurface, fraction)
    val black = androidx.compose.ui.graphics.Color.Black
    val white = androidx.compose.ui.graphics.Color.White
    return copy(
        surfaceContainerLowest = if (dark) androidx.compose.ui.graphics.lerp(surface, black, 0.35f) else white,
        surfaceContainerLow = step(if (dark) 0.03f else 0.02f),
        surfaceContainer = step(if (dark) 0.05f else 0.04f),
        surfaceContainerHigh = step(if (dark) 0.08f else 0.06f),
        surfaceContainerHighest = step(if (dark) 0.11f else 0.09f),
        surfaceBright = step(if (dark) 0.12f else 0.0f),
        surfaceDim = if (dark) androidx.compose.ui.graphics.lerp(surface, black, 0.2f) else step(0.10f),
    )
}
