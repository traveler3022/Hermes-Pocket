package com.hermes.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hermes.android.service.HermesGatewayService
import com.hermes.android.ui.screen.ChatScreen
import com.hermes.android.ui.screen.ConfigScreen
import com.hermes.android.ui.screen.CronScreen
import com.hermes.android.ui.screen.PlatformsScreen
import com.hermes.android.ui.screen.SessionsScreen
import com.hermes.android.ui.screen.SkillsScreen
import com.hermes.android.ui.theme.Hermes2Theme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity — single-activity architecture with simple screen state.
 *
 * Starts [HermesGatewayService] on create to keep the gateway connection
 * alive in the background (ADR-004).
 *
 * Reference: ADR-002 (Native Compose), ADR-004 (Foreground Service),
 *            Phase 1.5 Rule 1
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the gateway foreground service
        HermesGatewayService.start(this)

        setContent {
            Hermes2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Screen { CHAT, CONFIG, PLATFORMS, SESSIONS, SKILLS, CRON, RUNTIME }

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf(Screen.CHAT) }
    when (screen) {
        Screen.CHAT -> ChatScreen(
            onNavigateToSettings = { screen = Screen.CONFIG },
            onNavigateToSessions = { screen = Screen.SESSIONS },
            onNavigateToRuntime = { screen = Screen.RUNTIME },
        )
        Screen.CONFIG -> ConfigScreen(
            onNavigateBack = { screen = Screen.CHAT },
            onNavigateToPlatforms = { screen = Screen.PLATFORMS },
            onNavigateToSkills = { screen = Screen.SKILLS },
            onNavigateToCron = { screen = Screen.CRON },
            onNavigateToRuntime = { screen = Screen.RUNTIME },
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
