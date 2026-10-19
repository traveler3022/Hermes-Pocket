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
    // Two families, each with a day and a night palette; the app's light/dark
    // setting (or the phone's, on System) picks which one shows.
    CARBON("carbon", "Carbon", "کربن"),
    LAJVARD("lajvard", "Lajvard", "لاجورد");

    companion object {
        /**
         * Carbon is the default. The seven palettes before these two were folded in:
         * a saved choice moves to the family closest in feel — the tinted, warm or
         * coloured ones to Lajvard, the neutral ones to Carbon.
         */
        fun fromKey(key: String): ColorTheme = when (key) {
            "lajvard", "hermes", "blue_eye", "claude", "aether" -> LAJVARD
            else -> CARBON
        }
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

    // Hermes' terminal personality on the thinking line: its kaomoji face and
    // verb ("(◕‿◕) pondering...") in place of the breathing star. Off by
    // default — the plain line says what kind of work is happening; this is
    // for people who like the CLI's character.
    var kaomojiStatus: Boolean by mutableStateOf(prefs.getBoolean("kaomoji_status", false))
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

    fun updateKaomojiStatus(enabled: Boolean) {
        kaomojiStatus = enabled
        prefs.edit().putBoolean("kaomoji_status", enabled).apply()
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

// ── Palettes ──
// Every role is set explicitly, surface containers included: sheets, dialogs and
// menus are painted with those, and left unset they fall back to Material's
// lavender-tinted baseline. A theme is only these values — no screen checks which
// theme is on.

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
    surfaceContainerLowest = carbon_light_surfaceContainerLowest,
    surfaceContainerLow = carbon_light_surfaceContainerLow,
    surfaceContainer = carbon_light_surfaceContainer,
    surfaceContainerHigh = carbon_light_surfaceContainerHigh,
    surfaceContainerHighest = carbon_light_surfaceContainerHighest,
    surfaceBright = carbon_light_surfaceContainerLowest,
    surfaceDim = carbon_light_surfaceContainerHighest,
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
    surfaceContainerLowest = carbon_dark_surfaceContainerLowest,
    surfaceContainerLow = carbon_dark_surfaceContainerLow,
    surfaceContainer = carbon_dark_surfaceContainer,
    surfaceContainerHigh = carbon_dark_surfaceContainerHigh,
    surfaceContainerHighest = carbon_dark_surfaceContainerHighest,
    surfaceBright = carbon_dark_surfaceContainerHighest,
    surfaceDim = carbon_dark_surfaceContainerLowest,
)

private val LajvardLightColors = lightColorScheme(
    primary = lajvard_light_primary,
    onPrimary = lajvard_light_onPrimary,
    primaryContainer = lajvard_light_primaryContainer,
    onPrimaryContainer = lajvard_light_onPrimaryContainer,
    secondary = lajvard_light_secondary,
    onSecondary = lajvard_light_onSecondary,
    secondaryContainer = lajvard_light_secondaryContainer,
    onSecondaryContainer = lajvard_light_onSecondaryContainer,
    tertiary = lajvard_light_tertiary,
    onTertiary = lajvard_light_onTertiary,
    tertiaryContainer = lajvard_light_tertiaryContainer,
    onTertiaryContainer = lajvard_light_onTertiaryContainer,
    error = lajvard_light_error,
    onError = lajvard_light_onError,
    errorContainer = lajvard_light_errorContainer,
    onErrorContainer = lajvard_light_onErrorContainer,
    background = lajvard_light_background,
    onBackground = lajvard_light_onBackground,
    surface = lajvard_light_surface,
    onSurface = lajvard_light_onSurface,
    surfaceVariant = lajvard_light_surfaceVariant,
    onSurfaceVariant = lajvard_light_onSurfaceVariant,
    outline = lajvard_light_outline,
    outlineVariant = lajvard_light_outlineVariant,
    surfaceContainerLowest = lajvard_light_surfaceContainerLowest,
    surfaceContainerLow = lajvard_light_surfaceContainerLow,
    surfaceContainer = lajvard_light_surfaceContainer,
    surfaceContainerHigh = lajvard_light_surfaceContainerHigh,
    surfaceContainerHighest = lajvard_light_surfaceContainerHighest,
    surfaceBright = lajvard_light_surfaceContainerLowest,
    surfaceDim = lajvard_light_surfaceContainerHighest,
)

private val LajvardDarkColors = darkColorScheme(
    primary = lajvard_dark_primary,
    onPrimary = lajvard_dark_onPrimary,
    primaryContainer = lajvard_dark_primaryContainer,
    onPrimaryContainer = lajvard_dark_onPrimaryContainer,
    secondary = lajvard_dark_secondary,
    onSecondary = lajvard_dark_onSecondary,
    secondaryContainer = lajvard_dark_secondaryContainer,
    onSecondaryContainer = lajvard_dark_onSecondaryContainer,
    tertiary = lajvard_dark_tertiary,
    onTertiary = lajvard_dark_onTertiary,
    tertiaryContainer = lajvard_dark_tertiaryContainer,
    onTertiaryContainer = lajvard_dark_onTertiaryContainer,
    error = lajvard_dark_error,
    onError = lajvard_dark_onError,
    errorContainer = lajvard_dark_errorContainer,
    onErrorContainer = lajvard_dark_onErrorContainer,
    background = lajvard_dark_background,
    onBackground = lajvard_dark_onBackground,
    surface = lajvard_dark_surface,
    onSurface = lajvard_dark_onSurface,
    surfaceVariant = lajvard_dark_surfaceVariant,
    onSurfaceVariant = lajvard_dark_onSurfaceVariant,
    outline = lajvard_dark_outline,
    outlineVariant = lajvard_dark_outlineVariant,
    surfaceContainerLowest = lajvard_dark_surfaceContainerLowest,
    surfaceContainerLow = lajvard_dark_surfaceContainerLow,
    surfaceContainer = lajvard_dark_surfaceContainer,
    surfaceContainerHigh = lajvard_dark_surfaceContainerHigh,
    surfaceContainerHighest = lajvard_dark_surfaceContainerHighest,
    surfaceBright = lajvard_dark_surfaceContainerHighest,
    surfaceDim = lajvard_dark_surfaceContainerLowest,
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

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (colorTheme) {
            ColorTheme.CARBON -> if (useDark) CarbonDarkColors else CarbonLightColors
            ColorTheme.LAJVARD -> if (useDark) LajvardDarkColors else LajvardLightColors
        }
    }

    val fontFamily = when (appFont) {
        AppFont.VAZIR -> Vazirmatn
        AppFont.SYSTEM -> androidx.compose.ui.text.font.FontFamily.Default
    }

    MaterialTheme(
        colorScheme = if (warmMode) colorScheme.warmed() else colorScheme,
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
