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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
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

    // Nested navigation: null = the top-level category menu; a value = drilled
    // into that category. The back arrow pops one level (category -> menu ->
    // out of Settings), so Settings can grow deep without one giant scroll.
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(section?.let { t(it.titleEn, it.titleFa) } ?: t("Settings", "تنظیمات"))
                },
                navigationIcon = {
                    IconButton(onClick = { if (section != null) section = null else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (section) {
                null -> SettingsMenu(
                    onOpen = { section = it },
                    onNavigateToRuntime = onNavigateToRuntime,
                    onNavigateToPlatforms = onNavigateToPlatforms,
                    onNavigateToPlugins = onNavigateToPlugins,
                    onNavigateToSkills = onNavigateToSkills,
                    onNavigateToCron = onNavigateToCron,
                )
                SettingsSection.GENERAL -> GeneralTab(
                    state = uiState,
                    viewModel = viewModel,
                    themeModeState = themeModeState,
                    appLanguageState = appLanguageState,
                )
                SettingsSection.MEMORY -> MemorySection(uiState, viewModel)
                SettingsSection.MODELS -> ModelsTab(uiState, viewModel)
                SettingsSection.TOOLS -> ToolsTab(uiState, viewModel)
            }
        }
    }
}

/** Top-level Settings categories (drill-down targets). */
private enum class SettingsSection(val titleEn: String, val titleFa: String) {
    GENERAL("General", "عمومی"),
    MEMORY("Memory", "حافظه"),
    MODELS("Models & Providers", "مدل‌ها و پرووایدرها"),
    TOOLS("Tools", "ابزارها"),
}

/**
 * The Settings root: a scannable list of categories. In-app categories drill
 * into a sub-page ([onOpen]); the rest jump to their own full screens.
 */
@Composable
private fun SettingsMenu(
    onOpen: (SettingsSection) -> Unit,
    onNavigateToRuntime: () -> Unit,
    onNavigateToPlatforms: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToCron: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SettingsMenuRow(
            Icons.Default.Language,
            t("General", "عمومی"),
            t("Appearance, language, backend, raw config", "ظاهر، زبان، بک‌اند، پیکربندی خام"),
        ) { onOpen(SettingsSection.GENERAL) }
        SettingsMenuRow(
            Icons.Default.Psychology,
            t("Memory", "حافظه"),
            t("USER.md and MEMORY.md", "فایل‌های USER.md و MEMORY.md"),
        ) { onOpen(SettingsSection.MEMORY) }
        SettingsMenuRow(
            Icons.Default.SwapHoriz,
            t("Models & Providers", "مدل‌ها و پرووایدرها"),
            t("Model switch, API keys, credits", "تعویض مدل، کلید API، اعتبار"),
        ) { onOpen(SettingsSection.MODELS) }
        SettingsMenuRow(
            Icons.Default.Security,
            t("Tools", "ابزارها"),
            t("Enable or disable agent tools", "فعال یا غیرفعال کردن ابزارها"),
        ) { onOpen(SettingsSection.TOOLS) }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        SettingsMenuRow(
            Icons.Default.Dns,
            t("Server & Connection", "سرور و اتصال"),
            t("Remote server address and token", "آدرس و توکن سرور"),
        ) { onNavigateToRuntime() }
        SettingsMenuRow(
            Icons.Default.Link,
            t("Messaging Platforms", "پیام‌رسان‌ها"),
            t("Telegram, WhatsApp, …", "تلگرام، واتساپ، …"),
        ) { onNavigateToPlatforms() }
        SettingsMenuRow(
            Icons.Default.AccountBalanceWallet,
            t("Plugins Manager", "مدیر افزونه‌ها"),
            t("Install and manage plugins", "نصب و مدیریت افزونه‌ها"),
        ) { onNavigateToPlugins() }
        SettingsMenuRow(
            Icons.Default.Star,
            t("Skills", "مهارت‌ها"),
            t("Browse and manage skills", "مرور و مدیریت مهارت‌ها"),
        ) { onNavigateToSkills() }
        SettingsMenuRow(
            Icons.Default.Schedule,
            t("Cron Scheduler", "زمان‌بندی"),
            t("Scheduled agent jobs", "کارهای زمان‌بندی‌شده"),
        ) { onNavigateToCron() }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun GeneralTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
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
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = t("Warm / Night mode", "حالت گرم / شب"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = t(
                                    "Shifts screens toward a warm amber tint to reduce blue light for long sessions.",
                                    "صفحات را به سمت رنگ کهربایی گرم متمایل می‌کند تا نور آبی در استفاده طولانی کمتر شود.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = themeModeState.warmMode,
                            onCheckedChange = { themeModeState.updateWarmMode(it) },
                        )
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

        // -- Agent Behavior --
        // Only settings that actually affect the agent (and therefore this
        // app) live here. The old screen also exposed a pile of server-TUI
        // cosmetics (skin/compact/fast/verbose/...) — some sent config keys
        // that don't exist in Hermes at all, and the rest only restyle the
        // terminal UI on the server, which an Android client never sees.
        Text(
            text = t("Agent Behavior", "رفتار ایجنت"),
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
                // Command approval mode (approvals.mode) — "off" is the
                // persistent equivalent of --yolo.
                Column {
                    Text(
                        text = t("Command Approval", "تأیید دستورات"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t(
                            "How dangerous commands get approved",
                            "دستورات خطرناک چطور تأیید بشن",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    var approvalExpanded by remember { mutableStateOf(false) }
                    val approvalLabel = when (state.approvalMode) {
                        "manual" -> t("Manual — always ask me", "دستی — همیشه از من بپرس")
                        "smart" -> t("Smart — auto-approve low-risk", "هوشمند — کم‌خطرها خودکار")
                        "off" -> t("Off — approve everything (yolo)", "خاموش — همه‌چیز خودکار (yolo)")
                        else -> state.approvalMode
                    }
                    Box {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { approvalExpanded = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(
                                text = approvalLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = approvalExpanded,
                            onDismissRequest = { approvalExpanded = false },
                        ) {
                            listOf(
                                "manual" to t("Manual — always ask me", "دستی — همیشه از من بپرس"),
                                "smart" to t("Smart — auto-approve low-risk", "هوشمند — کم‌خطرها خودکار"),
                                "off" to t("Off — approve everything (yolo)", "خاموش — همه‌چیز خودکار (yolo)"),
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setApprovalMode(mode)
                                        approvalExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                // Reasoning effort lives on the chat screen's quick-switcher
                // (the "+" menu) — same setting, same RPC, no need for a
                // second control here that just duplicates it.
            }
        }

        // -- Personality & Identity --
        Text(
            text = t("Personality & Identity", "شخصیت و هویت"),
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
                // Avatar shown next to agent replies in chat — a real
                // uploaded image, not an emoji picker. Client-side only
                // (local prefs), no gateway RPC.
                Column {
                    Text(
                        text = t("Avatar", "آواتار"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val avatarPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) { uri -> uri?.let { viewModel.setAvatarUri(it) } }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.avatarUri != null) {
                                AsyncImage(
                                    model = state.avatarUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = "⚕",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                            Text(t("Upload image", "آپلود عکس"))
                        }
                        if (state.avatarUri != null) {
                            TextButton(onClick = { viewModel.clearAvatarUri() }) {
                                Text(t("Reset", "بازنشانی"))
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Personality preset name (display.personality). Saved on
                // button press — the old field fired a server write on every
                // keystroke.
                Column {
                    Text(
                        text = t("Personality", "شخصیت"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t(
                            "Preset name, e.g. helpful / kawaii / pirate",
                            "اسم یک پریست، مثل helpful / kawaii / pirate",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    var personalityText by remember(state.personality) { mutableStateOf(state.personality) }
                    OutlinedTextField(
                        value = personalityText,
                        onValueChange = { personalityText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text(t("Enter preset name", "اسم پریست را وارد کنید")) },
                        singleLine = true,
                    )
                    if (personalityText != state.personality) {
                        TextButton(
                            onClick = { viewModel.setPersonality(personalityText) },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(t("Save", "ذخیره")) }
                    }
                }

                HorizontalDivider()

                // SOUL.md — the agent's persistent identity (first slot of
                // the system prompt). This replaces the old "System Prompt"
                // free-text field, whose config key never existed in Hermes.
                Column {
                    Text(
                        text = t("Identity (SOUL.md)", "هویت (SOUL.md)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = t(
                            "The agent's persistent voice & identity — first part of its system prompt",
                            "هویت و لحن ماندگار ایجنت — اولین بخش از دستور سیستم",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (state.isLoadingSoul) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        var soulText by remember(state.soulMd) { mutableStateOf(state.soulMd) }
                        OutlinedTextField(
                            value = soulText,
                            onValueChange = { soulText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            placeholder = { Text(t("Who is your agent?", "ایجنتت کیه؟")) },
                            minLines = 4,
                        )
                        if (soulText != state.soulMd) {
                            TextButton(
                                onClick = { viewModel.saveSoul(soulText) },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text(t("Save SOUL.md", "ذخیره SOUL.md")) }
                        }
                    }
                }
            }
        }

        // ── Environment variables (~/.hermes/.env) — editable, not just a
        //    reload-from-disk button that had nothing to actually change
        //    what's on disk. ──
        Text(
            text = t("Environment Variables", "متغیرهای محیطی"),
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
                    text = t("~/.hermes/.env — API keys and other env vars", "~/.hermes/.env — کلیدهای API و سایر متغیرها"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                LaunchedEffect(Unit) { viewModel.loadEnvFile() }
                if (state.isLoadingEnv) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    var envText by remember(state.envText) { mutableStateOf(state.envText) }
                    OutlinedTextField(
                        value = envText,
                        onValueChange = { envText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadEnv() },
                            modifier = Modifier.weight(1f),
                        ) { Text(t("Reload", "بارگذاری مجدد")) }
                        if (envText != state.envText) {
                            Button(
                                onClick = { viewModel.saveEnvFile(envText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(t("Save", "ذخیره")) }
                        }
                    }
                }
            }
        }

        // ── MCP servers (config.yaml: mcp_servers) — same idea. ──
        Text(
            text = t("MCP Servers", "سرورهای MCP"),
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
                    text = t("Raw JSON — config.yaml's mcp_servers section", "JSON خام — بخش mcp_servers فایل config.yaml"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                LaunchedEffect(Unit) { viewModel.loadMcpServers() }
                if (state.isLoadingMcp) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    var mcpText by remember(state.mcpServersText) { mutableStateOf(state.mcpServersText) }
                    OutlinedTextField(
                        value = mcpText,
                        onValueChange = { mcpText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadMcp() },
                            modifier = Modifier.weight(1f),
                        ) { Text(t("Reload", "بارگذاری مجدد")) }
                        if (mcpText != state.mcpServersText) {
                            Button(
                                onClick = { viewModel.saveMcpServers(mcpText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(t("Save", "ذخیره")) }
                        }
                    }
                }
            }
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
