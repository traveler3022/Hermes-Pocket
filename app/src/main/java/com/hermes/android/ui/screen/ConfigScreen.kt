package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ConfigTab
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption
import com.hermes.android.ui.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
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
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Settings", "تنظیمات")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                ConfigTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (uiState.selectedTab) {
                ConfigTab.GENERAL -> GeneralTab(
                    state = uiState,
                    viewModel = viewModel,
                    onNavigateToPlatforms = onNavigateToPlatforms,
                    onNavigateToPlugins = onNavigateToPlugins,
                    onNavigateToSkills = onNavigateToSkills,
                    onNavigateToCron = onNavigateToCron,
                    onNavigateToRuntime = onNavigateToRuntime,
                    themeModeState = themeModeState,
                    appLanguageState = appLanguageState,
                )
                ConfigTab.MODELS -> ModelsTab(uiState, viewModel)
                ConfigTab.TOOLS -> ToolsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun GeneralTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onNavigateToPlatforms: () -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToCron: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    if (state.isLoadingConfig) {
        LoadingIndicator("Loading config…")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- Theme toggle --
        if (themeModeState != null) {
            Text(
                text = t("Appearance", "ظاهر"),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = t("Theme", "تم"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> t("System", "سیستم")
                                ThemeMode.LIGHT -> t("Light", "روشن")
                                ThemeMode.DARK -> t("Dark", "تاریک")
                            }
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.mode == mode,
                                onClick = { themeModeState.updateMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        text = t("Color Theme", "رنگ تم"),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorTheme.entries.forEach { theme ->
                            val label = t(theme.displayEn, theme.displayFa)
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.colorTheme == theme,
                                onClick = { themeModeState.updateColorTheme(theme) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        // -- Language selector --
        if (appLanguageState != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = t("Language", "زبان"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val label = when (lang) {
                                AppLanguage.AUTO -> t("Auto", "خودکار")
                                AppLanguage.ENGLISH -> "English"
                                AppLanguage.FARSI -> "فارسی"
                            }
                            androidx.compose.material3.FilterChip(
                                selected = appLanguageState.language == lang,
                                onClick = { appLanguageState.updateLanguage(lang) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Backend & Capabilities",
            style = MaterialTheme.typography.titleMedium,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Active backend",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Provider: ${state.activeProvider ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Model: ${state.activeModel ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Provider configuration placeholder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = t("Provider 1  ·  Provider 2", "پرووایدر ۱  ·  پرووایدر ۲"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = t("Coming Soon", "به زودی"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        // -- Model Behavior Config --
        Text(
            text = t("Model Behavior", "رفتار مدل"),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Auto-approve (yolo) toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Auto-Approve (Yolo)", "تایید خودکار"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = t("Automatically approve tool calls", "تایید خودکار فراخوانی ابزارها"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = state.yolo,
                        onCheckedChange = { viewModel.setYolo(it) },
                    )
                }

                HorizontalDivider()

                // Reasoning effort level
                Column {
                    Text(
                        text = t("Reasoning", "استدلال"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t("Model effort level", "سطح تلاش مدل"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    var reasoningExpanded by remember { mutableStateOf(false) }
                    Box {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reasoningExpanded = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(
                                text = state.reasoning,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = reasoningExpanded,
                            onDismissRequest = { reasoningExpanded = false },
                        ) {
                            listOf("none", "brief", "standard", "extended").forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(level) },
                                    onClick = {
                                        viewModel.setReasoning(level)
                                        reasoningExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Thinking mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Show Thinking", "نمایش تفکر"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = t("Display model reasoning process", "نمایش فرآیند استدلال مدل"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = state.thinkingMode,
                        onCheckedChange = { viewModel.setThinkingMode(it) },
                    )
                }

                HorizontalDivider()

                // Fast mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t("Fast Mode", "حالت سریع"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Faster responses", "پاسخ‌های سریع‌تر"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = state.fast, onCheckedChange = { viewModel.setFast(it) })
                }

                HorizontalDivider()

                // Verbose mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t("Verbose", "فروند"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Detailed output", "خروجی تفصیلی"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = state.verbose, onCheckedChange = { viewModel.setVerbose(it) })
                }

                HorizontalDivider()

                // Compact mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t("Compact", "متراکم"), style = MaterialTheme.typography.titleSmall)
                        Text(t("Compact layout", "صفحه متراکم"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = state.compact, onCheckedChange = { viewModel.setCompact(it) })
                }
            }
        }

        // -- Personality & Appearance --
        Text(
            text = t("Personality & Appearance", "شخصیت و ظاهر"),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Personality text field
                Column {
                    Text(
                        text = t("Personality", "شخصیت"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t("Agent personality style", "سبک شخصیت عامل"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = state.personality,
                        onValueChange = { viewModel.setPersonality(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text(t("Enter personality", "شخصیت را وارد کنید")) },
                        singleLine = true,
                    )
                }

                HorizontalDivider()

                // Skin text field
                Column {
                    Text(
                        text = t("Skin", "پوسته"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t("UI theme/appearance", "تم ظاهری رابط"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = state.skin,
                        onValueChange = { viewModel.setSkin(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text(t("Enter skin name", "نام پوسته را وارد کنید")) },
                        singleLine = true,
                    )
                }

                HorizontalDivider()

                // Prompt text field
                Column {
                    Text(
                        text = t("System Prompt", "دستور سیستم"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t("Initial instructions for agent", "دستورات اولیه برای عامل"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = { viewModel.setPrompt(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text(t("Enter system prompt", "دستور سیستم را وارد کنید")) },
                        minLines = 3,
                    )
                }
            }
        }

        // Link to Runtime Setup / Termux Connection
        androidx.compose.material3.Button(
            onClick = onNavigateToRuntime,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Termux & Agent Connection", "اتصال ترموکس و عامل"))
        }

        // Link to Messaging Platforms
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToPlatforms,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Messaging Platforms", "پیام‌رسان‌ها"))
        }

        // Link to Plugins Manager
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToPlugins,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Plugins Manager", "مدیر افزونه‌ها"))
        }

        // Link to Skills
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToSkills,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Skills Browser", "مهارت‌ها"))
        }

        // Reload config without restart (reload.mcp / reload.env)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.reloadMcp() },
                modifier = Modifier.weight(1f),
            ) {
                Text(t("Reload MCP", "بارگذاری MCP"))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.reloadEnv() },
                modifier = Modifier.weight(1f),
            ) {
                Text(t("Reload env", "بارگذاری env"))
            }
        }

        // Link to Cron Jobs
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToCron,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Cron Scheduler", "زمان‌بندی"))
        }

        Text(
            text = t("Current Configuration", "پیکربندی فعلی"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = state.configYaml.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ModelsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    // Load providers on first composition
    val providersLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!providersLoaded.value) {
            viewModel.loadProviders()
            providersLoaded.value = true
        }
    }

    var showAddProviderDialog by remember { mutableStateOf(false) }

    if (state.isLoadingModels && state.isLoadingProviders) {
        LoadingIndicator(t("Loading models...", "در حال بارگذاری مدلها..."))
        return
    }

    val grouped = remember(state.availableModels) {
        state.availableModels.groupBy { it.provider }
    }
    val modelProviders = remember(grouped) { grouped.keys.sorted() }

    // Track selected provider — default to activeProvider or first available
    var selectedProvider by remember(modelProviders, state.activeProvider) {
        mutableStateOf(
            state.activeProvider?.takeIf { it in modelProviders } ?: modelProviders.firstOrNull()
        )
    }

    val filteredModels = remember(selectedProvider, grouped) {
        grouped[selectedProvider].orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Quick model switch (manual input) ──
        item(key = "__quick_model_switch") {
            QuickModelSwitch(state, viewModel)
        }

        // ══════════════════════════════════════════════════════════════
        // ── Provider Management Section ──
        // ══════════════════════════════════════════════════════════════
        item(key = "__provider_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("API Providers", "پرووایدرهای API"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.loadCredits() }) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(t("Credits", "اعتبار"))
                    }
                    TextButton(onClick = { viewModel.loadProviders() }) {
                        Text(t("Refresh", "بارگذاری مجدد"))
                    }
                    FilledTonalButton(onClick = { showAddProviderDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Add", "افزودن"))
                    }
                }
            }
        }

        if (state.isLoadingProviders) {
            item(key = "__providers_loading") {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (state.providers.isEmpty()) {
            item(key = "__providers_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = t(
                            "No providers configured yet.",
                            "هنوز پرووایدری تنظیم نشده.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // ── Fallback chain visualization ──
            if (state.fallbackProviders.isNotEmpty()) {
                item(key = "__fallback_chain") {
                    FallbackChainBar(state.fallbackProviders)
                }
            }

            // ── Provider cards ──
            items(
                items = state.providers,
                key = { it.slug },
            ) { provider ->
                ProviderCard(
                    provider = provider,
                    credentials = state.credentialPool[provider.slug].orEmpty(),
                    isExpanded = state.expandedProviderSlug == provider.slug,
                    onToggleExpand = { viewModel.toggleProviderExpanded(provider.slug) },
                    onRemove = { viewModel.removeProvider(provider.slug) },
                    onAddCredential = { key, label -> viewModel.addCredential(provider.slug, key, label) },
                    onRemoveCredential = { index -> viewModel.removeCredential(provider.slug, index) },
                    onSetStrategy = { strategy -> viewModel.setProviderStrategy(provider.slug, strategy) },
                    onSetPrimary = { viewModel.setPrimaryProvider(provider) },
                    onToggleFallback = { viewModel.toggleFallback(provider.slug) },
                    onMoveFallback = { up -> viewModel.moveFallback(provider.slug, up) },
                    onMoveCredential = { index, up -> viewModel.moveCredential(provider.slug, index, up) },
                )
            }
        }

        // ══════════════════════════════════════════════════════════════
        // ── Model Selection Section ──
        // ══════════════════════════════════════════════════════════════
        item(key = "__model_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("Select Model", "انتخاب مدل"),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { viewModel.loadModels() }) {
                    Text(t("Refresh", "بارگذاری مجدد"))
                }
            }
        }

        if (grouped.isEmpty()) {
            item(key = "__empty_models") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = t(
                            "No models loaded. Make sure Hermes gateway is running, then tap Refresh.",
                            "مدلی بارگذاری نشد. مطمئن شو gateway هرمس روشنه، بعد بزن بارگذاری مجدد.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        // ── API Provider dropdown ──
        item(key = "__provider_dropdown") {
            ProviderDropdown(
                providers = modelProviders,
                selected = selectedProvider,
                onSelect = { selectedProvider = it },
            )
        }

        // ── Model dropdown (filtered by provider) ──
        item(key = "__model_dropdown") {
            ModelDropdown(
                models = filteredModels,
                selected = filteredModels.firstOrNull {
                    it.provider == state.activeProvider && it.modelId == state.activeModel
                },
                onSelect = { viewModel.selectModel(it) },
            )
        }

        // ── Endpoint info ──
        selectedProvider?.let { provider ->
            item(key = "__endpoint") {
                EndpointCard(provider = provider)
            }
        }

        // ── API Key for this provider ──
        selectedProvider?.let { provider ->
            val needsKey = filteredModels.any { it.requiresApiKey }
            if (needsKey) {
                item(key = "__apikey") {
                    ApiKeyRow(
                        provider = provider,
                        onSaveKey = { slug, key -> viewModel.saveApiKey(slug, key) },
                    )
                }
            }
        }
    }

    // ── Add Provider Dialog ──
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onAdd = { slug, baseUrl, model, key ->
                viewModel.addProvider(slug, baseUrl, model, key)
                showAddProviderDialog = false
            },
        )
    }

    // ── Credits dialog ──
    if (state.creditsText != null || state.isLoadingCredits) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCredits() },
            title = { Text(t("Credits", "اعتبار")) },
            text = {
                if (state.isLoadingCredits) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(state.creditsText.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCredits() }) { Text(t("Close", "بستن")) }
            },
        )
    }
}

@Composable
private fun ProviderDropdown(
    providers: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("API Provider", "پرووایدر API"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected ?: t("Select provider...", "پرووایدر رو انتخاب کن..."),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = provider,
                                    fontWeight = if (provider == selected)
                                        FontWeight.Medium
                                    else
                                        FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelect(provider)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDropdown(
    models: List<ModelOption>,
    selected: ModelOption?,
    onSelect: (ModelOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("Model", "مدل"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected?.modelId
                                ?: t("Select model...", "مدل رو انتخاب کن..."),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selected != null) {
                            Text(
                                text = "✓ " + t("Active", "فعال"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    models.forEach { model ->
                        val isActive = model.provider == selected?.provider &&
                            model.modelId == selected?.modelId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = model.modelId,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isActive) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelect(model)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(provider: String) {
    val endpoint = remember(provider) {
        knownEndpoints[provider.lowercase()] ?: provider
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val knownEndpoints = mapOf(
    "openrouter" to "https://openrouter.ai/api/v1",
    "anthropic" to "https://api.anthropic.com",
    "openai" to "https://api.openai.com/v1",
    "google" to "https://generativelanguage.googleapis.com",
    "mistral" to "https://api.mistral.ai/v1",
    "groq" to "https://api.groq.com/openai/v1",
    "deepseek" to "https://api.deepseek.com",
    "together" to "https://api.together.xyz/v1",
    "fireworks" to "https://api.fireworks.ai/inference/v1",
    "cohere" to "https://api.cohere.ai/v1",
    "replicate" to "https://api.replicate.com/v1",
    "perplexity" to "https://api.perplexity.ai",
    "xai" to "https://api.x.ai/v1",
    "ollama" to "http://localhost:11434",
    "lmstudio" to "http://localhost:1234/v1",
)

@Composable
private fun ApiKeyRow(
    provider: String,
    onSaveKey: (String, String) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(t("API Key", "کلید API")) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { onSaveKey(provider, apiKey) }) {
                Text(t("Save", "ذخیره"))
            }
        }
    }
}

@Composable
private fun QuickModelSwitch(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    var customProvider by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Quick Model Switch", "تغییر سریع مدل"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = t(
                    "Current: ${state.activeProvider ?: "?"} / ${state.activeModel ?: "?"}",
                    "فعلی: ${state.activeProvider ?: "?"} / ${state.activeModel ?: "?"}",
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            OutlinedTextField(
                value = customProvider,
                onValueChange = { customProvider = it },
                label = { Text(t("Provider (e.g. xiaomi, gemini, openai)", "پرووایدر")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = customModel,
                onValueChange = { customModel = it },
                label = { Text(t("Model ID (e.g. mimo-v2.5-free)", "شناسه مدل")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (customProvider.isNotBlank() && customModel.isNotBlank()) {
                        viewModel.selectModel(
                            ModelOption(
                                provider = customProvider.trim(),
                                modelId = customModel.trim(),
                                name = customModel.trim(),
                                requiresApiKey = false,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = customProvider.isNotBlank() && customModel.isNotBlank(),
            ) {
                Text(t("Switch Model", "تغییر مدل"))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.disconnectModel() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Disconnect Model", "قطع اتصال مدل"))
            }
        }
    }
}


@Composable
private fun ToolsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    if (state.isLoadingTools) {
        LoadingIndicator("Loading tools…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.availableTools, key = { it.name }) { tool ->
            ToolRow(tool, viewModel)
        }
    }
}

@Composable
private fun ToolRow(tool: ToolOption, viewModel: ConfigViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tool.description.isNotBlank()) {
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${tool.toolCount} tools" + if (tool.tools.isNotEmpty()) ": ${tool.tools.take(6).joinToString(", ")}${if (tool.tools.size > 6) "…" else ""}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                tool.toolset?.let {
                    Text(
                        text = "toolset: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Switch(
                checked = tool.enabled,
                onCheckedChange = { viewModel.toggleTool(tool.name, it) },
            )
        }
    }
}

@Composable
private fun MemorySection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    if (state.isLoadingMemory) {
        LoadingIndicator(t("Loading memory...", "در حال بارگذاری حافظه..."))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("Memory files", "فایل‌های حافظه"),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { viewModel.loadMemory() }) {
                Text(t("Refresh", "بارگذاری مجدد"))
            }
        }
        MemoryFileCard(
            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
            title = "USER.md",
            content = state.memoryUserMd,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        MemoryFileCard(
            icon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
            title = "MEMORY.md",
            content = state.memoryMd,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun MemoryFileCard(
    icon: @Composable () -> Unit,
    title: String,
    content: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val displayText = content
        .ifBlank { t("Memory has not been created yet", "حافظه هنوز ساخته نشده") }
        .replace("(not found)", t("Memory has not been created yet", "حافظه هنوز ساخته نشده"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon()
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = contentColor)
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = contentColor,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// ── Provider Management Composables ──
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FallbackChainBar(fallbackProviders: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = t("Fallback Chain", "زنجیره جایگزین"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                fallbackProviders.forEachIndexed { index, slug ->
                    if (index > 0) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(slug, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    provider: HermesProviderConfig,
    credentials: List<CredentialEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onAddCredential: (String, String?) -> Unit,
    onRemoveCredential: (Int) -> Unit,
    onSetStrategy: (String) -> Unit,
    onSetPrimary: () -> Unit = {},
    onToggleFallback: () -> Unit = {},
    onMoveFallback: (Boolean) -> Unit = {},
    onMoveCredential: (Int, Boolean) -> Unit = { _, _ -> },
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            // ── Header row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (provider.isPrimary)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = provider.slug,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (provider.isPrimary) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(t("Primary", "اصلی"), style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(14.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        labelColor = MaterialTheme.colorScheme.onPrimary,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    border = null,
                                )
                            }
                            if (provider.isFallback) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(t("Fallback", "جایگزین"), style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        labelColor = MaterialTheme.colorScheme.onTertiary,
                                    ),
                                    border = null,
                                )
                            }
                        }
                        if (provider.baseUrl.isNotBlank()) {
                            Text(
                                text = provider.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Credential count badge
                    if (credentials.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${credentials.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = t("Delete", "حذف"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            // ── Expanded content ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Details
                    DetailRow(t("Base URL", "آدرس"), provider.baseUrl)
                    if (provider.defaultModel.isNotBlank()) {
                        DetailRow(t("Default Model", "مدل پیشفرض"), provider.defaultModel)
                    }

                    // ── Primary / Fallback actions ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!provider.isPrimary) {
                            FilledTonalButton(
                                onClick = onSetPrimary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Star, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Set primary", "کلید اصلی"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        FilterChip(
                            selected = provider.isFallback,
                            onClick = onToggleFallback,
                            label = {
                                Text(
                                    t("In fallback chain", "در زنجیره جایگزین"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        if (provider.isFallback) {
                            IconButton(onClick = { onMoveFallback(true) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, t("Up", "بالا"), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveFallback(false) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, t("Down", "پایین"), Modifier.size(18.dp))
                            }
                        }
                    }

                    // Strategy selector — the four strategies Hermes actually
                    // supports for a provider's credential pool.
                    Text(
                        text = t("Key rotation strategy", "استراتژی چرخش کلید"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val strategies = listOf(
                        "round_robin" to t("Round-robin", "چرخشی"),
                        "fill_first" to t("Fill first", "ترتیبی"),
                        "least_used" to t("Least used", "کم‌مصرف‌ترین"),
                        "random" to t("Random", "تصادفی"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        strategies.forEach { (value, label) ->
                            FilterChip(
                                selected = provider.strategy == value,
                                onClick = { onSetStrategy(value) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (provider.strategy == value) {
                                    { Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp)) }
                                } else null,
                            )
                        }
                    }

                    // Credential pool
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t("Keys (${credentials.size})", "کلیدها (${credentials.size})"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { showAddKeyDialog = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Add Key", "افزودن کلید"), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (credentials.isEmpty()) {
                        Text(
                            text = t("No keys configured", "کلیدی تنظیم نشده"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        credentials.forEachIndexed { pos, cred ->
                            CredentialRow(
                                credential = cred,
                                canMoveUp = pos > 0,
                                canMoveDown = pos < credentials.size - 1,
                                onRemove = { onRemoveCredential(cred.index) },
                                onMove = { up -> onMoveCredential(cred.index, up) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(t("Delete Provider?", "پرووایدر حذف شود؟")) },
            text = {
                Text(
                    t(
                        "This will remove \"${provider.slug}\" from config.yaml and credential pool.",
                        "\"${provider.slug}\" از config.yaml و credential pool حذف میشه.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showDeleteConfirm = false
                }) {
                    Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(t("Cancel", "لغو"))
                }
            },
        )
    }

    // ── Add key dialog ──
    if (showAddKeyDialog) {
        AddKeyDialog(
            providerSlug = provider.slug,
            onDismiss = { showAddKeyDialog = false },
            onAdd = { key, label ->
                onAddCredential(key, label)
                showAddKeyDialog = false
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CredentialRow(
    credential: CredentialEntry,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onRemove: () -> Unit,
    onMove: (Boolean) -> Unit = {},
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = when (credential.lastStatus) {
                    "ok" -> MaterialTheme.colorScheme.primary
                    "fail" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column {
                Text(
                    text = credential.label ?: "key #${credential.index}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = credential.tokenPreview,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (credential.requestCount > 0) {
                Text(
                    text = "${credential.requestCount} req",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            // Priority order (fill_first uses this order; top = tried first)
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, t("Higher priority", "اولویت بالاتر"), Modifier.size(16.dp))
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, t("Lower priority", "اولویت پایین‌تر"), Modifier.size(16.dp))
            }
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("Remove", "حذف"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(t("Remove Key?", "کلید حذف شود؟")) },
            text = { Text(t("This key will be removed from the credential pool.", "این کلید از credential pool حذف میشه.")) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showConfirm = false
                }) { Text(t("Remove", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(t("Cancel", "لغو")) }
            },
        )
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (slug: String, baseUrl: String, defaultModel: String, apiKey: String) -> Unit,
) {
    var slug by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add Provider", "افزودن پرووایدر")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().replace(" ", "_") },
                    label = { Text(t("Provider Name (slug)", "نام پرووایدر")) },
                    placeholder = { Text("openai, anthropic, xai...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(t("Base URL", "آدرس سرور")) },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text(t("Default Model", "مدل پیشفرض")) },
                    placeholder = { Text("gpt-4o, claude-sonnet-4-20250514...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Psychology, null) },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(t("API Key", "کلید API")) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Security, null) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(slug, baseUrl, defaultModel, apiKey) },
                enabled = slug.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text(t("Add", "افزودن"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
        },
    )
}

@Composable
private fun AddKeyDialog(
    providerSlug: String,
    onDismiss: () -> Unit,
    onAdd: (key: String, label: String?) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add Key to $providerSlug", "افزودن کلید به $providerSlug")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(t("API Key", "کلید API")) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Security, null) },
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(t("Label (optional)", "برچسب (اختیاری)")) },
                    placeholder = { Text("primary, backup, team...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(key, label.ifBlank { null }) },
                enabled = key.isNotBlank(),
            ) { Text(t("Add", "افزودن")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
        },
    )
}

@Composable
private fun LoadingIndicator(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
