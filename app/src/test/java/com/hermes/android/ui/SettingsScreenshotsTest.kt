package com.hermes.android.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.hermes.android.domain.config.AuxiliaryModels
import com.hermes.android.domain.config.AuxiliaryTask
import com.hermes.android.domain.config.EnvKey
import com.hermes.android.domain.config.MoaConfig
import com.hermes.android.domain.config.MoaPreset
import com.hermes.android.domain.config.MoaSlot
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.screen.AboutSection
import com.hermes.android.ui.screen.AdvancedSection
import com.hermes.android.ui.screen.ApprovalCard
import com.hermes.android.ui.screen.AuxModelsSection
import com.hermes.android.ui.screen.ConfigEditorSection
import com.hermes.android.ui.screen.ControlCenterMenu
import com.hermes.android.ui.screen.EnvKeysSection
import com.hermes.android.ui.screen.GeneralTab
import com.hermes.android.ui.screen.KanbanSection
import com.hermes.android.ui.screen.LanguageSection
import com.hermes.android.ui.screen.MemorySection
import com.hermes.android.ui.screen.MoaSection
import com.hermes.android.ui.screen.ModelsTab
import com.hermes.android.ui.screen.PersonalitySection
import com.hermes.android.ui.screen.PhoneAssistantSection
import com.hermes.android.ui.screen.ProviderAccountsSection
import com.hermes.android.ui.screen.ProviderKeysSection
import com.hermes.android.ui.screen.ProvidersSection
import com.hermes.android.ui.screen.ReasoningCard
import com.hermes.android.ui.screen.SettingsFolder
import com.hermes.android.ui.screen.SettingsSection
import com.hermes.android.ui.screen.SoulCard
import com.hermes.android.ui.screen.ToolsetsSection
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.AboutUiState
import com.hermes.android.ui.viewmodel.AboutViewModel
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ConfigEditorUiState
import com.hermes.android.ui.viewmodel.ConfigEditorViewModel
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.EnvKeysUiState
import com.hermes.android.ui.viewmodel.EnvKeysViewModel
import com.hermes.android.ui.viewmodel.GatewayConnectionUi
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.InsightsData
import com.hermes.android.ui.viewmodel.KanbanSettingViewModel
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ModelPickerUiState
import com.hermes.android.ui.viewmodel.ModelPickerViewModel
import com.hermes.android.ui.viewmodel.ModelSlotsViewModel
import com.hermes.android.ui.viewmodel.ProviderRow
import com.hermes.android.ui.viewmodel.ProvidersUiState
import com.hermes.android.ui.viewmodel.ProvidersViewModel
import com.hermes.android.ui.viewmodel.ToolOption
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Renders every Settings page with sample data to PNGs, for design review.
 * Skipped unless SETTINGS_SHOTS=1 (readme-shots.yml sets it), so the normal
 * unit test run is unchanged. View models are relaxed mocks placed in the
 * composition's ViewModelStore, so the pages' own hiltViewModel() calls find them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "fa-w400dp-h1500dp-xxhdpi")
class SettingsScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("readmeShotsDir") ?: "build/readme-shots", "settings")

    @Before
    fun onlyWhenAsked() {
        assumeTrue(System.getenv("SETTINGS_SHOTS") == "1")
        outDir.mkdirs()
    }

    @Test fun rootDark() = shot("00-root-dark") { Root() }
    @Test fun rootLight() = shot("00-root-light", dark = false) { Root() }
    @Test @Config(qualifiers = "en-w400dp-h1500dp-xxhdpi")
    fun rootEnglish() = shot("00-root-en", language = AppLanguage.ENGLISH) { Root() }
    @Test fun appearance() = shot("01-appearance") { Page(SettingsSection.APPEARANCE) { GeneralTab(ThemeModeState(LocalContext.current), AppLanguageState(LocalContext.current)) } }
    @Test fun appearanceLight() = shot("01-appearance-light", dark = false) { Page(SettingsSection.APPEARANCE) { GeneralTab(ThemeModeState(LocalContext.current), AppLanguageState(LocalContext.current)) } }
    @Test fun language() = shot("02-language") { Page(SettingsSection.LANGUAGE) { LanguageSection(AppLanguageState(LocalContext.current)) } }
    @Test fun personality() = shot("03-personality") { Page(SettingsSection.PERSONALITY) { PersonalitySection(sample, config, onOpen = {}) } }
    @Test fun soul() = shot("04-soul") { Page(SettingsSection.SOUL) { SettingsFolder { SoulCard(sample, config) } } }
    @Test fun memory() = shot("05-memory") { Page(SettingsSection.MEMORY) { MemorySection(sample, config) } }
    @Test fun models() = shot("06-models") { Page(SettingsSection.MODELS) { ModelsTab(sample, config, onOpen = {}) } }
    @Test fun reasoning() = shot("07-reasoning") { Page(SettingsSection.REASONING) { SettingsFolder { ReasoningCard(sample, config, showTitle = false) } } }
    @Test fun auxModels() = shot("08-task-models") { Page(SettingsSection.AUX_MODELS) { AuxModelsSection(remember { SnackbarHostState() }) } }
    @Test fun moa() = shot("09-moa") { Page(SettingsSection.MOA) { MoaSection(remember { SnackbarHostState() }) } }
    @Test fun providers() = shot("10-providers") { Page(SettingsSection.PROVIDERS) { ProvidersSection(sample, config, remember { SnackbarHostState() }, onOpen = {}) } }
    @Test fun providerKeys() = shot("11-provider-keys") { Page(SettingsSection.PROVIDER_KEYS) { ProviderKeysSection(config, remember { SnackbarHostState() }, onOpen = {}) } }
    @Test fun providerAccounts() = shot("12-provider-accounts") { Page(SettingsSection.PROVIDER_ACCOUNTS) { ProviderAccountsSection(remember { SnackbarHostState() }, onOpen = {}) } }
    @Test fun approval() = shot("13-approval") { Page(SettingsSection.APPROVAL) { SettingsFolder { ApprovalCard(sample, config, showTitle = false) } } }
    @Test fun toolsets() = shot("14-toolsets") { Page(SettingsSection.TOOLSETS) { ToolsetsSection(sample, config) } }
    @Test fun advanced() = shot("15-advanced") { Page(SettingsSection.ADVANCED) { AdvancedSection(sample, config, onOpen = {}) } }
    @Test fun envKeys() = shot("16-env-keys") { Page(SettingsSection.ENV_KEYS) { EnvKeysSection(remember { SnackbarHostState() }) } }
    @Test fun configEditor() = shot("17-config-editor") { Page(SettingsSection.CONFIG_EDITOR) { ConfigEditorSection(remember { SnackbarHostState() }) } }
    @Test fun about() = shot("18-updates") { Page(SettingsSection.ABOUT) { AboutSection() } }
    @Test fun assistant() = shot("19-phone-assistant") { Page(SettingsSection.PHONE_ASSISTANT) { PhoneAssistantSection() } }
    @Test fun kanban() = shot("20-kanban") { Page(SettingsSection.KANBAN) { KanbanSection() } }

    // ── Sample data ──────────────────────────────────────────────────────

    private val sample = ConfigUiState(
        activeProvider = "openrouter",
        activeModel = "claude-sonnet-4.5",
        availableModels = listOf(
            ModelOption("openrouter", "claude-sonnet-4.5", "Claude Sonnet 4.5", false, "OpenRouter"),
            ModelOption("openrouter", "gpt-5", "GPT-5", false, "OpenRouter"),
            ModelOption("nous", "hermes-4-405b", "Hermes 4 405B", false, "Nous Portal"),
        ),
        availableTools = listOf(
            ToolOption("terminal", "Run shell commands", true, "terminal", 2),
            ToolOption("web", "Search and read the web", true, "web", 3),
            ToolOption("browser", "Drive a headless browser", false, "browser", 9),
            ToolOption("vision", "Look at images", true, "vision", 1),
            ToolOption("image_gen", "Generate images", false, "image_gen", 1),
        ),
        memoryUserMd = "Prefers Persian replies.\nWorks on an Android app in Kotlin.",
        memoryMd = "Server: racknerd, Ubuntu.\nThe build runs on GitHub Actions.",
        providers = listOf(
            HermesProviderConfig(
                slug = "openrouter",
                baseUrl = "https://openrouter.ai/api/v1",
                defaultModel = "claude-sonnet-4.5",
                credentials = listOf(CredentialEntry(1, "a", "main", "api_key", "sk-or…3f2a", 0, "ok", 128)),
                isPrimary = true,
            ),
        ),
        approvalMode = "smart",
        reasoning = "medium",
        personality = "helpful",
        soulMd = "You are Hermes, a calm and precise assistant.",
        insights = InsightsData(days = 30, sessions = 42, messages = 610),
    )

    private val config: ConfigViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(sample)
    }

    private fun viewModels(): Map<Class<out ViewModel>, ViewModel> = mapOf(
        ConfigViewModel::class.java to config,
        ProvidersViewModel::class.java to mockk<ProvidersViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                ProvidersUiState(
                    rows = listOf(
                        ProviderRow("openrouter", "OpenRouter", connected = true, modelCount = 312, isCurrent = true),
                        ProviderRow("nous", "Nous Portal", connected = true, modelCount = 8),
                        ProviderRow("anthropic", "Anthropic", connected = false, authType = "api_key", keyEnv = "ANTHROPIC_API_KEY"),
                        ProviderRow("openai", "OpenAI", connected = false, authType = "api_key", keyEnv = "OPENAI_API_KEY"),
                        ProviderRow("gemini", "Google Gemini", connected = false, authType = "api_key", keyEnv = "GEMINI_API_KEY"),
                    ),
                    accountsLoaded = true,
                ),
            )
        },
        AboutViewModel::class.java to mockk<AboutViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                AboutUiState(installedVersion = "3.1.2", installedCommit = "2cbe365c", hermesVersion = "0.21.7"),
            )
            every { launches } returns MutableSharedFlow()
        },
        KanbanSettingViewModel::class.java to mockk<KanbanSettingViewModel>(relaxed = true) {
            every { on } returns MutableStateFlow(true)
            every { applying } returns MutableStateFlow(false)
        },
        ModelSlotsViewModel::class.java to mockk<ModelSlotsViewModel>(relaxed = true) {
            val moa = MoaConfig(
                defaultPreset = "balanced",
                activePreset = "balanced",
                presets = mapOf(
                    "balanced" to MoaPreset(
                        enabled = true,
                        referenceModels = listOf(
                            MoaSlot("openrouter", "gpt-5"),
                            MoaSlot("openrouter", "gemini-2.5-pro"),
                        ),
                        aggregator = MoaSlot("openrouter", "claude-sonnet-4.5"),
                        fanout = "user_turn",
                    ),
                ),
            )
            every { uiState } returns MutableStateFlow(
                ModelSlotsViewModel.UiState(
                    auxiliary = AuxiliaryModels(
                        tasks = listOf(
                            AuxiliaryTask("vision", "auto", "", "", null, null, null, null),
                            AuxiliaryTask("title", "openrouter", "gpt-5-mini", "", null, null, null, null),
                            AuxiliaryTask("compression", "auto", "", "", null, null, null, null),
                            AuxiliaryTask("web_extract", "auto", "", "", null, null, null, null),
                        ),
                        mainProvider = "openrouter",
                        mainModel = "claude-sonnet-4.5",
                    ),
                    moa = moa,
                    moaDraft = moa,
                ),
            )
            every { messages } returns MutableSharedFlow()
        },
        ModelPickerViewModel::class.java to mockk<ModelPickerViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(ModelPickerUiState(models = sample.availableModels))
        },
        EnvKeysViewModel::class.java to mockk<EnvKeysViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                EnvKeysUiState(
                    keys = listOf(
                        EnvKey("OPENROUTER_API_KEY", "OpenRouter API key", "https://openrouter.ai/keys", "provider", true, "sk-o…3f2a", true, false),
                        EnvKey("TELEGRAM_BOT_TOKEN", "Telegram bot token", null, "messaging", true, "71…Qe", true, false),
                        EnvKey("FIRECRAWL_API_KEY", "Firecrawl for web extract", null, "tool", false, null, true, false),
                        EnvKey("HERMES_MAX_ITERATIONS", "Agent loop limit", null, "setting", true, "90", false, false),
                    ),
                ),
            )
        },
        MemoryProvidersViewModel::class.java to mockk<MemoryProvidersViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(MemoryProvidersViewModel.UiState(isLoading = false))
        },
        ConfigEditorViewModel::class.java to mockk<ConfigEditorViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(ConfigEditorUiState())
        },
    )

    // ── Frame ────────────────────────────────────────────────────────────

    /** A section the way ConfigScreen frames it: scaffold title from the section, back arrow. */
    @Composable
    private fun Page(section: SettingsSection, content: @Composable () -> Unit) {
        HermesScaffold(title = t(section.titleEn, section.titleFa), onBack = {}) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) { content() }
        }
    }

    @Composable
    private fun Root() {
        HermesScaffold(
            title = t("Control Center", "میز فرمان"),
            subtitle = t("Agent, server, and app configuration", "پیکربندی ایجنت، سرور و برنامه"),
            onBack = {},
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                ControlCenterMenu(
                    state = sample,
                    connection = GatewayConnectionUi(state = ChatConnectionState.Connected),
                    serverUrl = "hermes.tail1234.ts.net",
                    themeModeState = ThemeModeState(LocalContext.current),
                    appLanguageState = AppLanguageState(LocalContext.current),
                    onOpen = {},
                    onNavigateToRuntime = {},
                    onNavigateToSetup = {},
                    onNavigateToCron = {},
                    onNavigateToProjects = {},
                    onNavigateToPlatforms = {},
                    onNavigateToWebhooks = {},
                    onNavigateToPlugins = {},
                    onNavigateToSkills = {},
                    onNavigateToMcp = {},
                    onNavigateToLinux = {},
                    onNavigateToStorage = {},
                    onNavigateToAnalytics = {},
                    onNavigateToAchievements = {},
                    onNavigateToOps = {},
                )
            }
        }
    }

    private fun shot(
        name: String,
        language: AppLanguage = AppLanguage.FARSI,
        dark: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        // A store owner without a default factory: hiltViewModel() then skips Hilt's
        // factory and returns the mock already in the store.
        val store = ViewModelStore()
        val mocks = viewModels()
        val provider = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = mocks.getValue(modelClass) as T
            },
        )
        mocks.keys.forEach { provider[it] }
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalAppLanguage provides language,
                LocalLayoutDirection provides if (language == AppLanguage.FARSI) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Hermes2Theme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, colorTheme = ColorTheme.CARBON) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) { content() }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        val root = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnUiThread { root.draw(Canvas(bitmap)) }
        File(outDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
