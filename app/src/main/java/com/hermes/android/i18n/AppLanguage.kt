package com.hermes.android.i18n

import android.content.Context
import java.util.Locale

/** The in-app language choice; AUTO follows the device. */
enum class AppLanguage(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    ENGLISH("en", "English"),
    FARSI("fa", "فارسی");

    companion object {
        fun fromKey(key: String): AppLanguage = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/**
 * Non-Composable variant of `t()` (ui.i18n) for use in ViewModels and other non-UI
 * contexts (error messages emitted from background coroutines, etc.).
 *
 * Resolves the language the same way the composable `t()` does: app-level
 * override first, then device locale. The override is read from the same
 * SharedPreferences key `AppLanguageState` uses, so this stays in sync
 * with the in-app toggle without the ViewModel having to observe Compose
 * state.
 */
fun tForContext(context: Context, en: String, fa: String): String {
    val prefs = context.getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)
    val overrideKey = prefs.getString("app_language", "auto") ?: "auto"
    val override = AppLanguage.fromKey(overrideKey)
    if (override == AppLanguage.ENGLISH) return en
    if (override == AppLanguage.FARSI) return fa

    val language = if (android.os.Build.VERSION.SDK_INT >= 24) {
        context.resources.configuration.locales[0]?.language
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale?.language
    } ?: Locale.getDefault().language
    // Only Persian: "iw" (also matched here once) is Java's old code for Hebrew.
    return if (language.equals("fa", ignoreCase = true)) fa else en
}
