package com.hermes.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption
import com.hermes.android.ui.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.AppFont
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState

/**
 * Configuration screen — model picker, tool toggles, config viewer.
 *
 * Depends ONLY on [ConfigViewModel] — never on gateway or runtime packages.
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPlatforms: () -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToCron: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    onNavigateToLinux: () -> Unit = {},
    onNavigateToProjects: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Live connection state for the hub's connection card — the same source
    // the Server Connection screen uses (RuntimeViewModel maps the gateway's
    // ConnectionState to the UI-facing type).
    val runtimeViewModel: com.hermes.android.ui.viewmodel.RuntimeViewModel = hiltViewModel()
    val connection by runtimeViewModel.connectionState.collectAsStateWithLifecycle()
    val serverConfig by runtimeViewModel.serverConfig.collectAsStateWithLifecycle()
    val runtimeChoice by runtimeViewModel.runtimeChoice.collectAsStateWithLifecycle()
    // On-device runtimes have no server URL; name the runtime instead of "No server configured".
    val connectionTitle = if (runtimeViewModel.isRemoteRuntime) {
        serverConfig.serverUrl
    } else when (runtimeChoice) {
        com.hermes.android.ui.viewmodel.RuntimeChoiceUi.BuiltInLinux -> t("Built-in Linux", "لینوکس داخلی")
        com.hermes.android.ui.viewmodel.RuntimeChoiceUi.Termux -> "Termux"
    }

    // Nested navigation: null = the top-level category menu; a value = drilled
    // into that category. The back arrow pops one level (category -> menu ->
    // out of Settings), so Settings can grow deep without one giant scroll.
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<SettingsSection?>(null) }

    androidx.activity.compose.BackHandler(enabled = section != null) { section = section?.parent }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    com.hermes.android.ui.design.HermesScaffold(
        title = section?.let { t(it.titleEn, it.titleFa) } ?: t("Control Center", "میز فرمان"),
        subtitle = if (section == null) t("Agent, server, and app configuration", "پیکربندی ایجنت، سرور و برنامه") else null,
        onBack = { if (section != null) section = section?.parent else onNavigateBack() },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (section) {
                null -> SettingsMenu(
                    state = uiState,
                    connection = connection,
                    serverUrl = connectionTitle,
                    onOpen = { section = it },
                    onNavigateToRuntime = onNavigateToRuntime,
                    onNavigateToLinux = onNavigateToLinux,
                    onNavigateToSetup = onNavigateToSetup,
                    onNavigateToCron = onNavigateToCron,
                    onNavigateToProjects = onNavigateToProjects,
                )
                SettingsSection.GENERAL_SETTINGS -> GeneralSettingsSection(
                    state = uiState,
                    onOpen = { section = it },
                    onNavigateToPlatforms = onNavigateToPlatforms,
                )
                SettingsSection.APPEARANCE -> GeneralTab(
                    themeModeState = themeModeState,
                    appLanguageState = appLanguageState,
                )
                SettingsSection.MEMORY -> MemorySection(uiState, viewModel)
                SettingsSection.MODELS -> ModelsTab(uiState, viewModel, onOpen = { section = it })
                SettingsSection.REASONING -> SettingsFolder { ReasoningCard(uiState, viewModel) }
                SettingsSection.APPROVAL -> SettingsFolder { ApprovalCard(uiState, viewModel) }
                SettingsSection.PERSONALITY -> PersonalitySection(
                    state = uiState,
                    viewModel = viewModel,
                    onOpen = { section = it },
                )
                SettingsSection.SOUL -> SettingsFolder { SoulCard(uiState, viewModel) }
                SettingsSection.TOOLSETS -> ToolsetsSection(uiState, viewModel)
                SettingsSection.TOOLS -> ToolsTab(
                    state = uiState,
                    onOpen = { section = it },
                    onNavigateToPlugins = onNavigateToPlugins,
                    onNavigateToSkills = onNavigateToSkills,
                )
                SettingsSection.ADVANCED -> AdvancedSection(uiState, viewModel)
                SettingsSection.ABOUT -> AboutSection()
            }
        }
    }
}

/**
 * Settings categories (drill-down targets). [parent] is where the back arrow
 * returns to; null means the Control Center menu.
 */
internal enum class SettingsSection(
    val titleEn: String,
    val titleFa: String,
    val parent: SettingsSection? = null,
) {
    GENERAL_SETTINGS("General Settings", "تنظیمات عمومی"),
    APPEARANCE("Appearance", "ظاهر", GENERAL_SETTINGS),
    APPROVAL("Command Approval", "تأیید فرمان‌ها", GENERAL_SETTINGS),
    PERSONALITY("Personality", "شخصیت", GENERAL_SETTINGS),
    SOUL("SOUL.md", "SOUL.md", PERSONALITY),
    MEMORY("Memory", "حافظه", PERSONALITY),
    ADVANCED("Advanced", "پیشرفته", GENERAL_SETTINGS),
    MODELS("Models & Providers", "مدل‌ها و پرووایدرها"),
    REASONING("Reasoning depth", "عمق تفکر", MODELS),
    TOOLS("Tools", "ابزارها"),
    TOOLSETS("Toolsets", "گروه\u200Cهای ابزار", TOOLS),
    ABOUT("Updates", "به\u200Cروزرسانی"),
}
