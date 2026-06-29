package com.hermes.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.screen.ChatScreen
import com.hermes.android.ui.screen.ConfigScreen
import com.hermes.android.ui.screen.CronScreen
import com.hermes.android.ui.screen.OnboardingScreen
import com.hermes.android.ui.screen.PlatformsScreen
import com.hermes.android.ui.screen.SessionsScreen
import com.hermes.android.ui.screen.SkillsScreen
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeModeState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsToRequest = mutableListOf<String>()
            if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("com.termux.permission.RUN_COMMAND")
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("android.permission.POST_NOTIFICATIONS")
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest.toTypedArray(), 1001)
            }
        }

        requestBatteryOptimizationExemption()

        val sharedText = extractSharedText(intent)

        val prefs = getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        val themeModeState = ThemeModeState(this)
        val appLanguageState = AppLanguageState(this)

        setContent {
            CompositionLocalProvider(LocalAppLanguage provides appLanguageState.language) {
                Hermes2Theme(themeMode = themeModeState.mode, colorTheme = themeModeState.colorTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppRoot(
                            sharedText = sharedText,
                            onboardingCompleted = onboardingCompleted,
                            onOnboardingComplete = {
                                prefs.edit().putBoolean("onboarding_completed", true).apply()
                            },
                            themeModeState = themeModeState,
                            appLanguageState = appLanguageState,
                        )
                    }
                }
            }
        }
    }

    @Suppress("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }
}

private enum class Screen { CHAT, CONFIG, PLATFORMS, SESSIONS, SKILLS, CRON, RUNTIME, ONBOARDING }

@Composable
private fun AppRoot(
    sharedText: String? = null,
    onboardingCompleted: Boolean = true,
    onOnboardingComplete: () -> Unit = {},
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    var screen by remember {
        mutableStateOf(if (onboardingCompleted) Screen.CHAT else Screen.ONBOARDING)
    }

    var pendingSharedText by remember { mutableStateOf(sharedText) }

    when (screen) {
        Screen.ONBOARDING -> OnboardingScreen(
            onComplete = {
                onOnboardingComplete()
                screen = Screen.CHAT
            },
        )
        Screen.CHAT -> {
            ChatScreen(
                onNavigateToSettings = { screen = Screen.CONFIG },
                onNavigateToSessions = { screen = Screen.SESSIONS },
                onNavigateToRuntime = { screen = Screen.RUNTIME },
                sharedText = pendingSharedText,
            )
            LaunchedEffect(Unit) {
                pendingSharedText = null
            }
        }
        Screen.CONFIG -> ConfigScreen(
            onNavigateBack = { screen = Screen.CHAT },
            onNavigateToPlatforms = { screen = Screen.PLATFORMS },
            onNavigateToSkills = { screen = Screen.SKILLS },
            onNavigateToCron = { screen = Screen.CRON },
            onNavigateToRuntime = { screen = Screen.RUNTIME },
            themeModeState = themeModeState,
            appLanguageState = appLanguageState,
        )
        Screen.PLATFORMS -> PlatformsScreen(
            onNavigateBack = { screen = Screen.CONFIG },
        )
        Screen.SESSIONS -> SessionsScreen(
            onNavigateBack = { screen = Screen.CHAT },
        )
        Screen.SKILLS -> SkillsScreen(
            onNavigateBack = { screen = Screen.CONFIG },
        )
        Screen.CRON -> CronScreen(
            onNavigateBack = { screen = Screen.CONFIG },
        )
        Screen.RUNTIME -> com.hermes.android.ui.screen.RuntimeSetupScreen(
            onNavigateBack = { screen = Screen.CHAT },
        )
    }
}
