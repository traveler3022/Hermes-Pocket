package com.hermes.android

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.mutableStateOf
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
    @javax.inject.Inject
    lateinit var setupState: com.hermes.android.data.SetupState

    @javax.inject.Inject
    lateinit var runtimeSelection: com.hermes.android.runtime.RuntimeSelection

    /** Latest notification tap that reached the running activity (onNewIntent). */
    private val notificationOpen = mutableStateOf<NotificationOpen?>(null)

    /**
     * Text shared into the app, until the chat has taken it. Read from the launch
     * intent on every onCreate and handed to every chat screen created, it pasted
     * itself over the composer again after a rotation or when a chat was opened
     * from Tasks.
     */
    private val pendingShare = mutableStateOf<String?>(null)

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
        requestTermuxPermissionWhenSelected()

        pendingShare.value = if (savedInstanceState?.getBoolean(KEY_SHARE_TAKEN) == true) null else extractSharedText(intent)
        // Set when the user taps an agent-activity notification ("task done"):
        // opens the app straight into the session the result belongs to.
        val notificationSessionId = intent?.getStringExtra(
            com.hermes.android.service.AgentActivityNotifier.EXTRA_SESSION_ID
        )

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
                            startInSetup = !setupState.isComplete,
                            sharedText = pendingShare.value,
                            onSharedTextTaken = { pendingShare.value = null },
                            notificationSessionId = notificationSessionId,
                            notificationOpen = notificationOpen.value,
                            themeModeState = themeModeState,
                            appLanguageState = appLanguageState,
                        )
                    }
                }
            }
        }
    }

    /**
     * A notification tapped while the activity is alive lands here, not in
     * onCreate (the intent is CLEAR_TOP | SINGLE_TOP). Without this the tap only
     * brought back whatever chat was open, never the one the notification is about.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.hermes.android.service.AgentActivityNotifier.EXTRA_SESSION_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { notificationOpen.value = NotificationOpen(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHARE_TAKEN, pendingShare.value == null)
    }

    override fun onStart() {
        super.onStart()
        // Foreground = the strongest reconnect signal there is. onStartCommand
        // re-runs the connect path; it's a cheap no-op when already connected,
        // and it cuts any pending backoff wait when we're offline. This is also
        // the launch-path fallback: HermesApplication starts the gateway during
        // process init, but declines to when the process came up in the
        // background, and onStart always follows onCreate.
        com.hermes.android.service.HermesGatewayService.start(this)
    }

    /**
     * Termux's RUN_COMMAND permission only matters to the Termux runtime; the built-in Linux
     * runtime never talks to Termux, so ask only while Termux is the selected runtime —
     * including when the user switches to it later.
     */
    private fun requestTermuxPermissionWhenSelected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        lifecycleScope.launch {
            runtimeSelection.selected.collect { type ->
                if (type == com.hermes.android.runtime.RuntimeType.TERMUX &&
                    checkSelfPermission(TERMUX_RUN_COMMAND) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(TERMUX_RUN_COMMAND), 1002)
                }
            }
        }
    }

    /**
     * Ask, at most once per install, to be exempt from battery optimization.
     *
     * This used to run unconditionally in [onCreate], so a user who declined
     * got the same system dialog thrown in their face on every single app
     * launch, forever, with no way to make it stop short of granting it. That
     * is the kind of nagging that gets an app uninstalled — and it fired before
     * the first frame was even drawn, so a brand-new user's first experience of
     * Hermes was a permission dialog for an app they had not seen yet.
     *
     * Now: asked once, remembered, and never again. The exemption is a
     * nice-to-have for keeping the gateway socket alive in Doze, not a
     * requirement — the reconnect loop and the network callback already recover
     * from being killed.
     */
    @Suppress("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply()
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        // A device with no Settings activity for this action (some ROMs strip
        // it) must not take the whole app down on launch.
        runCatching { startActivity(intent) }
            .onFailure { timber.log.Timber.w(it, "[Main] battery optimization dialog unavailable") }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        return null
    }

    private companion object {
        const val PREFS_NAME = "hermes_prefs"
        const val KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
        const val KEY_SHARE_TAKEN = "share_taken"
        const val TERMUX_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    }
}

/**
 * Navigation graph for the entire app.
 *
 * Routes:
 * - `setup` — first-run setup (runtime, provider, API key, model)
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
    startInSetup: Boolean = false,
    sharedText: String? = null,
    onSharedTextTaken: () -> Unit = {},
    notificationSessionId: String? = null,
    notificationOpen: NotificationOpen? = null,
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    val navController = rememberNavController()

    // Every back arrow is wrapped in dropUnlessResumed: after a pop, the leaving
    // screen stays on top for the 700 ms fade and still takes taps. A tap on the
    // chat's hamburger in that window hit Settings' back arrow (same corner) and
    // popped `chat` too, leaving an empty NavHost — a black screen.
    NavHost(
        navController = navController,
        startDestination = if (startInSetup) "setup" else "chat",
    ) {
        composable("setup") {
            com.hermes.android.ui.screen.SetupScreen(
                onFinished = {
                    if (!navController.popBackStack("config", inclusive = false)) {
                        navController.navigate("chat") { popUpTo("setup") { inclusive = true } }
                    }
                },
            )
        }

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
                onNavigateToCron = { navController.navigate("cron") },
                sharedText = shared,
                onSharedTextTaken = onSharedTextTaken,
                resumeSessionId = resumeId,
                themeModeState = themeModeState,
            )
        }

        composable("tasks") {
            com.hermes.android.ui.screen.TasksScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenInChat = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
            )
        }

        composable("config") {
            com.hermes.android.ui.screen.ConfigScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onNavigateToPlatforms = { navController.navigate("platforms") },
                onNavigateToPlugins = { navController.navigate("plugins") },
                onNavigateToSkills = { navController.navigate("skills") },
                onNavigateToCron = { navController.navigate("cron") },
                onNavigateToRuntime = { navController.navigate("runtime") },
                onNavigateToLinux = { navController.navigate("linux") },
                onNavigateToStorage = { navController.navigate("storage") },
                onNavigateToProjects = { navController.navigate("projects") },
                onNavigateToSetup = { navController.navigate("setup") },
                themeModeState = themeModeState,
                appLanguageState = appLanguageState,
            )
        }

        composable("projects") {
            com.hermes.android.ui.screen.ProjectsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
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

        composable("platforms") {
            com.hermes.android.ui.screen.PlatformsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("plugins") {
            com.hermes.android.ui.screen.PluginsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("sessions") {
            com.hermes.android.ui.screen.SessionsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onResumeSession = { sessionId ->
                    navController.navigate("chat?resumeSessionId=$sessionId") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
            )
        }

        composable("skills") {
            com.hermes.android.ui.screen.SkillsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("cron") {
            com.hermes.android.ui.screen.CronScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("linux") {
            com.hermes.android.ui.screen.LinuxToolsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenTerminal = { navController.navigate("linux/terminal") },
                onOpenDesktop = { navController.navigate("linux/desktop") },
            )
        }

        composable("storage") {
            com.hermes.android.ui.screen.StorageScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("linux/desktop") {
            com.hermes.android.ui.screen.LinuxDesktopScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenViewer = { navController.navigate("linux/desktop/viewer") },
            )
        }

        composable("linux/desktop/viewer") {
            com.hermes.android.ui.screen.LinuxDesktopViewerScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("linux/terminal") {
            val tools: com.hermes.android.ui.viewmodel.LinuxToolsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            com.hermes.android.ui.screen.LinuxTerminalScreen(
                createLaunchSpec = tools::terminalLaunchSpec,
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }

        composable("runtime") {
            com.hermes.android.ui.screen.RuntimeSetupScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }

    // A notification tapped while the app was already running: open the chat
    // it is about, rebuilt from the server like any other chat switch.
    LaunchedEffect(notificationOpen) {
        val open = notificationOpen ?: return@LaunchedEffect
        if (navController.currentDestination?.route == "setup") return@LaunchedEffect
        navController.navigate("chat?resumeSessionId=${Uri.encode(open.sessionId)}") {
            popUpTo("chat") { inclusive = true }
        }
    }
}

/** One notification tap; a class, not data, so tapping the same chat again still counts. */
private class NotificationOpen(val sessionId: String)
