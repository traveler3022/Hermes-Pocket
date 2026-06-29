package com.hermes.android.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

enum class AppLanguage(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    ENGLISH("en", "English"),
    FARSI("fa", "فارسی");

    companion object {
        fun fromKey(key: String): AppLanguage = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

class AppLanguageState(context: Context) {
    private val prefs = context.getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)

    var language: AppLanguage by mutableStateOf(
        AppLanguage.fromKey(prefs.getString("app_language", "auto") ?: "auto")
    )
        private set

    fun updateLanguage(newLang: AppLanguage) {
        language = newLang
        prefs.edit().putString("app_language", newLang.key).apply()
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.AUTO }

@Composable
@ReadOnlyComposable
fun t(en: String, fa: String): String {
    val override = LocalAppLanguage.current
    if (override == AppLanguage.ENGLISH) return en
    if (override == AppLanguage.FARSI) return fa

    val config = LocalConfiguration.current
    val language = if (android.os.Build.VERSION.SDK_INT >= 24) {
        config.locales[0]?.language
    } else {
        @Suppress("DEPRECATION")
        config.locale?.language
    } ?: Locale.getDefault().language
    return if (language.equals("fa", ignoreCase = true) || language.equals("iw", ignoreCase = true)) fa else en
}
