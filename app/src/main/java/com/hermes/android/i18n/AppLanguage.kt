package com.hermes.android.i18n

import android.content.Context
import com.hermes.android.R

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
 * override first, then the language Android picked for the resources. The override is read from the same
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

    // AUTO: whatever language Android picked for the resources (values-fa/ or not).
    return if (context.resources.getString(R.string.ui_language) == "fa") fa else en
}
