package com.hermes.android.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.screen.InputBar
import com.hermes.android.ui.screen.MessageBubble
import com.hermes.android.ui.screen.WorkspaceDrawerSheet
import com.hermes.android.ui.screen.HxTraceItem
import com.hermes.android.ui.screen.rememberWorkspaceDestinations
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.SessionItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.screen.BuiltInLinuxDetectedContent
import com.hermes.android.ui.screen.RuntimeChoiceRow
import com.hermes.android.ui.screen.SettingsMenu
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.GatewayConnectionUi
import com.hermes.android.ui.viewmodel.InsightsData
import com.hermes.android.ui.viewmodel.RuntimeChoiceUi
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's real chat and drawer composables with sample data and saves
 * them as PNGs for the README. Skipped unless README_SHOTS=1, so the normal unit
 * test run is unchanged; .github/workflows/readme-shots.yml sets it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h860dp-xxhdpi")
class ReadmeScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("readmeShotsDir") ?: "build/readme-shots")

    @Before
    fun onlyWhenAsked() {
        assumeTrue(System.getenv("README_SHOTS") == "1")
        outDir.mkdirs()
    }

    @Test
    fun chatEnglish() = shot("chat-en") { Chat(fa = false) }

    @Test
    fun chatPersian() = shot("chat-fa", AppLanguage.FARSI) { Chat(fa = true) }

    @Test
    fun drawerEnglish() = shot("drawer-en") { Drawer(fa = false) }

    @Test
    fun drawerPersian() = shot("drawer-fa", AppLanguage.FARSI) { Drawer(fa = true) }

    @Test
    fun controlCenterEnglish() = shot("control-center-en") { ControlCenter() }

    @Test
    fun controlCenterPersian() = shot("control-center-fa", AppLanguage.FARSI) { ControlCenter() }

    @Test
    fun runtimeEnglish() = shot("runtime-en") { RuntimeChoice() }

    @Test
    fun runtimePersian() = shot("runtime-fa", AppLanguage.FARSI) { RuntimeChoice() }

    private fun shot(
        name: String,
        language: AppLanguage = AppLanguage.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        // Shimmers and pulses never settle; drive the clock by hand instead of waiting for idle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppLanguage provides language,
                LocalLayoutDirection provides if (language == AppLanguage.FARSI) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Hermes2Theme(themeMode = ThemeMode.DARK, colorTheme = ColorTheme.CARBON) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) { content() }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        // A software draw of the window: captureToImage's PixelCopy times out under Robolectric.
        val root = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnUiThread { root.draw(Canvas(bitmap)) }
        File(outDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

@Composable
private fun Chat(fa: Boolean) {
    val user = ChatMessage.User(
        id = "u1",
        timestamp = 0L,
        text = if (fa) "چی حافظه‌ی گوشیمو پر کرده؟" else "What's filling up my phone's storage?",
    )
    val tool = ChatMessage.ToolCall(
        id = "t1",
        timestamp = 0L,
        toolName = "terminal",
        argsText = "du -sh ~/* | sort -rh | head",
        resultText = "1.2G\tDownloads\n640M\t.cache",
        error = null,
        isRunning = false,
        durationS = 1.4,
        reasoningOwnerId = "a1",
    )
    val reply = ChatMessage.Assistant(
        id = "a1",
        timestamp = 0L,
        text = if (fa) {
            "بیشترین حجم مال این‌هاست:\n\n| پوشه | حجم |\n|---|---|\n| Downloads | 1.2 GB |\n| .cache | 640 MB |\n\nکش رو بی‌خطر می‌شه پاک کرد. پاکش کنم؟"
        } else {
            "Here's what takes the most space:\n\n| Folder | Size |\n|---|---|\n| Downloads | 1.2 GB |\n| .cache | 640 MB |\n\nThe cache is safe to clear. Want me to delete it?"
        },
        isStreaming = false,
        reasoning = "Check the largest folders in the home directory first.",
    )
    val trace = listOf(
        HxTraceItem.Reasoning(reply.reasoning.orEmpty(), messageId = "a1"),
        HxTraceItem.Tool(tool),
    )
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HxHeaderCircleButton(icon = HxIcons.MenuShort, contentDescription = "", onClick = {})
            Spacer(Modifier.weight(1f))
            HxHeaderCircleButton(icon = HxIcons.NewChat, contentDescription = "", onClick = {}, iconSize = 25.dp)
            Spacer(Modifier.width(8.dp))
            HxHeaderCircleButton(icon = Icons.Default.MoreVert, contentDescription = "", onClick = {})
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.padding(top = 4.dp))
            MessageBubble(message = user)
            MessageBubble(message = reply, isLastAssistant = true, traceItems = trace)
        }
        InputBar(
            text = "",
            isSending = false,
            slashCommands = emptyList(),
            onTextChange = {},
            onSend = {},
            onStop = {},
        )
    }
}

@Composable
private fun Drawer(fa: Boolean) {
    val titles = if (fa) {
        listOf("پاک‌سازی حافظه‌ی گوشی", "خلاصه‌ی اخبار صبح", "درست کردن اسکریپت بیلد", "برنامه‌ی سفر شیراز", "ترجمه‌ی فصل ۳")
    } else {
        listOf("Clean up phone storage", "Morning news digest", "Fix the build script", "Trip plan for Shiraz", "Translate chapter 3")
    }
    val sessions = titles.mapIndexed { i, title -> SessionItem("s$i", title, null, 1000L - i) }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(322.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
    ) {
        WorkspaceDrawerSheet(
            sessions = sessions,
            activeSessionId = "s0",
            destinations = rememberWorkspaceDestinations(
                onAgent = {},
                onScheduled = {},
                onGroups = {},
                onMedia = {},
                onFiles = {},
                onKanban = {},
            ),
            onSearch = {},
            onNewChat = {},
            onSessionClick = {},
            onAccount = {},
        )
    }
}

/** ConfigScreen's top level, on Built-in Linux so no server address shows. */
@Composable
private fun ControlCenter() {
    HermesScaffold(
        title = t("Control Center", "میز فرمان"),
        subtitle = t("Agent, server, and app configuration", "پیکربندی ایجنت، سرور و برنامه"),
        onBack = {},
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            SettingsMenu(
                state = ConfigUiState(
                    activeProvider = "openrouter",
                    activeModel = "claude-sonnet-4.5",
                    insights = InsightsData(days = 30, sessions = 42, messages = 610),
                ),
                connection = GatewayConnectionUi(state = ChatConnectionState.Connected),
                serverUrl = t("Built-in Linux", "لینوکس داخلی"),
                onOpen = {},
                onNavigateToRuntime = {},
                onNavigateToCron = {},
                onNavigateToProjects = {},
            )
        }
    }
}

/** SetupScreen's runtime step: the three places Hermes can run, Built-in Linux picked. */
@Composable
private fun RuntimeChoice() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                Text(
                    t("Step 1 of 4", "مرحله ۱ از ۴"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(progress = { 0.25f }, modifier = Modifier.fillMaxWidth())
            Text(t("Where should Hermes run?", "Hermes کجا اجرا شود؟"), style = MaterialTheme.typography.headlineSmall)
            RuntimeChoiceRow(
                selected = RuntimeChoiceUi.BuiltInLinux,
                enabled = true,
                onSelect = {},
                showServer = true,
            )
            Text(
                t(
                    "Built-in Linux runs Hermes inside this app, with nothing else to install. " +
                        "Termux uses the separate Termux app. Remote/Server connects to Hermes on your own server. " +
                        "You can switch later in Settings.",
                    "لینوکس داخلی Hermes را داخل همین اپ اجرا می‌کند و چیز دیگری لازم نیست. " +
                        "Termux از اپ جداگانه‌ی Termux استفاده می‌کند. سرور راه دور به Hermes روی سرور خودت وصل می‌شود. " +
                        "بعداً از تنظیمات هم می‌شود عوضش کرد.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BuiltInLinuxDetectedContent(diskFreeBytes = 12_400_000_000L, onStartInstall = {})
        }
    }
}
