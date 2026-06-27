package com.hermes.android.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Lightweight bilingual text helper for the current MVP.
 *
 * The app still has many legacy hard-coded strings. New/updated UI surfaces
 * should use [t] so Persian devices get readable labels without blocking on a
 * full resource-string migration.
 */
@Composable
@ReadOnlyComposable
fun t(en: String, fa: String): String {
    // Re-read configuration so Compose recomposes if the user changes locale.
    val config = LocalConfiguration.current
    val language = if (android.os.Build.VERSION.SDK_INT >= 24) {
        config.locales[0]?.language
    } else {
        @Suppress("DEPRECATION")
        config.locale?.language
    } ?: Locale.getDefault().language
    return if (language.equals("fa", ignoreCase = true) || language.equals("iw", ignoreCase = true)) fa else en
}
