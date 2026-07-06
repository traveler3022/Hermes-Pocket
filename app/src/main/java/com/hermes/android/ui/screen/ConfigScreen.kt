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
