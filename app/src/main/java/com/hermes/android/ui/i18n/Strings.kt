package com.hermes.android.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.hermes.android.R
import com.hermes.android.i18n.AppLanguage
import java.util.Locale

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

/**
 * Wraps a screen so `stringResource` follows the in-app language choice: for anything but
 * AUTO, LocalResources comes from a context with that locale, so values-<lang>/ is used
 * whatever the device language is. LocalContext stays the activity.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val deviceResources = LocalResources.current
    val resources = remember(context, configuration, deviceResources, language) {
        if (language == AppLanguage.AUTO) {
            deviceResources
        } else {
            val localized = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language.key)) }
            context.createConfigurationContext(localized).resources
        }
    }
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalResources provides resources,
        content = content,
    )
}

@Composable
@ReadOnlyComposable
fun t(en: String, fa: String): String =
    // Same choice as stringResource (values-fa/), so leftover t() text never mixes languages.
    if (LocalResources.current.getString(R.string.ui_language) == "fa") fa else en
