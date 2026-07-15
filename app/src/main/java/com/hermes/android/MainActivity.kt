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
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
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
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("android.permission.POST_NOTIFICATIONS")
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest.toTypedArray(), 1001)
            }
        }

        requestBatteryOptimizationExemption()

        val sharedText = extractSharedText(intent)
        // Set when the user taps an agent-activity notification ("task done"):
        // opens the app straight into the session the result belongs to.
        val notificationSessionId = intent?.getStringExtra(
            com.hermes.android.service.AgentActivityNotifier.EXTRA_SESSION_ID
        )

        // Keep the gateway connection alive when the app is backgrounded.
        // Started unconditionally on every launch; onStartCommand() handles
        // "runtime not configured yet" gracefully.
        com.hermes.android.service.HermesGatewayService.start(this)

        val themeModeState = ThemeModeState(this)
        val appLanguageState = AppLanguageState(this)

        setContent {
            CompositionLocalProvider(LocalAppLanguage provides appLanguageState.language) {
                Hermes2Theme(
                    themeMode = themeModeState.mode,
                    colorTheme = themeModeState.colorTheme,
                    warmMode = themeModeState.warmMode,
                    appFont = themeModeState.appFont,
                    fontScalePct = themeModeState.fontScalePct,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        HermesNavHost(
                            sharedText = sharedText,
                            notificationSessionId = notificationSessionId,
                            themeModeState = themeModeState,
                            appLanguageState = appLanguageState,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Foreground = the strongest reconnect signal there is. onStartCommand
        // re-runs the connect path; it's a cheap no-op when already connected,
        // and it cuts any pending backoff wait when we're offline.
        com.hermes.android.service.HermesGatewayService.start(this)
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

/**
 * Navigation graph for the entire app.
 *
 * Routes:
 * - `chat` — main chat screen
 * - `config` — settings & configuration
 * - `platforms` — platform credentials
 * - `plugins` — plugin management
 * - `sessions` — session list & switcher
 * - `skills` — skill management
 * - `cron` — cron job management
 * - `runtime` — runtime setup & status
 */
@Composable
private fun HermesNavHost(
    sharedText: String? = null,
    notificationSessionId: String? = null,
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chat",
    ) {
        composable(
            route = "chat?sharedText={sharedText}&resumeSessionId={resumeSessionId}",
            arguments = listOf(
                navArgument("sharedText") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("resumeSessionId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val shared = backStackEntry.arguments?.getString("sharedText") ?: sharedText
            // Route arg (in-app navigation) wins; the notification extra only
            // seeds the initial destination on a cold notification tap.
            val resumeId = backStackEntry.arguments?.getString("resumeSessionId")
                ?: notificationSessionId
            com.hermes.android.ui.screen.ChatScreen(
                onNavigateToSettings = { navController.navigate("config") },
                onNavigateToSessions = { navController.navigate("sessions") },
                onNavigateToTasks = { navController.navigate("tasks") },
                onNavigateToRuntime = { navController.navigate("runtime") },
                sharedText = shared,
                resumeSessionId = resumeId,
                themeModeState = themeModeState,
            )
        }

        composable("tasks") {
            com.hermes.android.ui.screen.TasksScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenInChat = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
            )
        }

        composable("config") {
            com.hermes.android.ui.screen.ConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlatforms = { navController.navigate("platforms") },
                onNavigateToPlugins = { navController.navigate("plugins") },
                onNavigateToSkills = { navController.navigate("skills") },
                onNavigateToCron = { navController.navigate("cron") },
                onNavigateToRuntime = { navController.navigate("runtime") },
                onNavigateToProjects = { navController.navigate("projects") },
                onNavigateToPet = { navController.navigate("pet") },
                onNavigateToBilling = { navController.navigate("billing") },
                themeModeState = themeModeState,
                appLanguageState = appLanguageState,
            )
        }

        composable("projects") {
            com.hermes.android.ui.screen.ProjectsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
                onNewSession = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
            )
        }

        composable("pet") {
            com.hermes.android.ui.screen.PetScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("billing") {
            com.hermes.android.ui.screen.BillingScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("platforms") {
            com.hermes.android.ui.screen.PlatformsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("plugins") {
            com.hermes.android.ui.screen.PluginsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("sessions") {
            com.hermes.android.ui.screen.SessionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onResumeSession = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
            )
        }

        composable("skills") {
            com.hermes.android.ui.screen.SkillsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("cron") {
            com.hermes.android.ui.screen.CronScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("runtime") {
            com.hermes.android.ui.screen.RuntimeSetupScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
