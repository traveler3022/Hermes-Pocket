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
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import androidx.compose.material.icons.filled.Pets
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
import com.hermes.android.ui.theme.TopBarDisplay

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
    onNavigateToProjects: () -> Unit = {},
    onNavigateToPet: () -> Unit = {},
    onNavigateToBilling: () -> Unit = {},
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

    com.hermes.android.ui.design.HermesScaffold(
        title = section?.let { t(it.titleEn, it.titleFa) } ?: t("Control Center", "میز فرمان"),
        subtitle = if (section == null) t("Agent, server, and app configuration", "پیکربندی ایجنت، سرور و برنامه") else null,
        onBack = { if (section != null) section = null else onNavigateBack() },
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
                    serverUrl = serverConfig.serverUrl,
                    onOpen = { section = it },
                    onNavigateToRuntime = onNavigateToRuntime,
                    onNavigateToPlatforms = onNavigateToPlatforms,
                    onNavigateToPlugins = onNavigateToPlugins,
                    onNavigateToSkills = onNavigateToSkills,
                    onNavigateToCron = onNavigateToCron,
                    onNavigateToProjects = onNavigateToProjects,
                    onNavigateToPet = onNavigateToPet,
                    onNavigateToBilling = onNavigateToBilling,
                )
                SettingsSection.GENERAL -> GeneralTab(
                    state = uiState,
                    viewModel = viewModel,
                    themeModeState = themeModeState,
                    appLanguageState = appLanguageState,
                )
                SettingsSection.BEHAVIOR -> BehaviorSection(uiState, viewModel)
                SettingsSection.MEMORY -> MemorySection(uiState, viewModel)
                SettingsSection.MODELS -> ModelsTab(uiState, viewModel)
                SettingsSection.TOOLS -> ToolsTab(uiState, viewModel)
                SettingsSection.ADVANCED -> AdvancedSection(uiState, viewModel)
            }
        }
    }
}

/** Top-level Settings categories (drill-down targets). */
private enum class SettingsSection(val titleEn: String, val titleFa: String) {
    GENERAL("General", "عمومی"),
    BEHAVIOR("Agent Behavior", "رفتار عامل"),
    MEMORY("Memory", "حافظه"),
    MODELS("Models & Providers", "مدل‌ها و پرووایدرها"),
    TOOLS("Tools", "ابزارها"),
    ADVANCED("Advanced", "پیشرفته"),
}

/**
 * The Settings root, restructured as the Control Center (approved design E):
 * a live connection card, live stat tiles (active model / credits / 30-day
 * usage — `credits.view` and `insights.get` were backend capabilities no UI
 * ever surfaced), then the domain list with live values in the subtitles
 * where the data is already loaded.
 */
@Composable
private fun SettingsMenu(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    connection: com.hermes.android.ui.viewmodel.GatewayConnectionUi,
    serverUrl: String,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToRuntime: () -> Unit,
    onNavigateToPlatforms: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToCron: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToPet: () -> Unit,
    onNavigateToBilling: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // ── Connection card ────────────────────────────────────────────────
        val (connColor, connLabel) = when (connection.state) {
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connected ->
                MaterialTheme.colorScheme.primary to t("Connected", "متصل")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connecting ->
                MaterialTheme.colorScheme.tertiary to t("Connecting…", "در حال اتصال…")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Reconnecting ->
                MaterialTheme.colorScheme.tertiary to t("Reconnecting…", "اتصال دوباره…")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Failed ->
                MaterialTheme.colorScheme.error to t("Connection failed", "اتصال ناموفق")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Disconnected ->
                MaterialTheme.colorScheme.onSurfaceVariant to t("Not connected", "متصل نیست")
        }
        Spacer(Modifier.height(12.dp))
        com.hermes.android.ui.design.SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToRuntime)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverUrl.ifBlank { t("No server configured", "سروری تنظیم نشده") },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = t("Server & connection settings", "تنظیمات سرور و اتصال"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                com.hermes.android.ui.design.StatusChip(label = connLabel, color = connColor)
            }
        }

        // ── Live stat tiles ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                value = state.activeModel ?: "—",
                label = t("Active model", "مدل فعال"),
            )
            StatTile(
                value = state.creditsSummary ?: "—",
                label = t("Credits", "اعتبار"),
            )
            StatTile(
                value = state.insights?.let { "${it.sessions}" } ?: "—",
                label = t("Sessions / 30d", "جلسه / ۳۰ روز"),
            )
        }

        // ── Domain grid (mockup E's dgrid: 2-per-row tiles, live subtitles) ─
        val tiles = listOf(
            DomainSpec(
                title = t("Models", "مدل‌ها"),
                subtitle = state.activeModel?.let { model ->
                    "${state.activeProvider ?: "?"} / $model"
                } ?: t("Model, API keys", "مدل، کلید API"),
                icon = Icons.Default.SwapHoriz,
                onClick = { onOpen(SettingsSection.MODELS) },
            ),
            DomainSpec(
                title = t("Behavior", "رفتار"),
                subtitle = t(
                    "Approval: ${state.approvalMode} · ${state.reasoning}",
                    "تأیید: ${approvalModeFa(state.approvalMode)} · تفکر: ${state.reasoning}",
                ),
                icon = Icons.Default.Security,
                onClick = { onOpen(SettingsSection.BEHAVIOR) },
            ),
            DomainSpec(
                title = t("Memory", "حافظه"),
                subtitle = t("USER.md · MEMORY.md", "USER.md · MEMORY.md"),
                icon = Icons.Default.Psychology,
                onClick = { onOpen(SettingsSection.MEMORY) },
            ),
            DomainSpec(
                title = t("Tools", "ابزارها"),
                subtitle = if (state.availableTools.isNotEmpty()) {
                    val enabled = state.availableTools.count { it.enabled }
                    t(
                        "$enabled of ${state.availableTools.size} toolsets on",
                        "$enabled از ${state.availableTools.size} گروه فعال",
                    )
                } else {
                    t("Enable or disable tools", "فعال/غیرفعال کردن ابزارها")
                },
                icon = Icons.Default.Key,
                onClick = { onOpen(SettingsSection.TOOLS) },
            ),
            DomainSpec(
                title = t("Skills", "مهارت‌ها"),
                subtitle = t("Browse and manage skills", "مرور و مدیریت مهارت‌ها"),
                icon = Icons.Default.Star,
                onClick = onNavigateToSkills,
            ),
            DomainSpec(
                title = t("Plugins", "افزونه‌ها"),
                subtitle = t("Install and manage plugins", "نصب و مدیریت افزونه‌ها"),
                icon = Icons.Default.Extension,
                onClick = onNavigateToPlugins,
            ),
            DomainSpec(
                title = t("Scheduler", "زمان‌بندی"),
                subtitle = t("Scheduled agent jobs", "کارهای زمان‌بندی‌شده"),
                icon = Icons.Default.Schedule,
                onClick = onNavigateToCron,
            ),
            DomainSpec(
                title = t("Platforms", "پلتفرم‌ها"),
                subtitle = t("Telegram, Discord, Slack", "تلگرام، دیسکورد، اسلک"),
                icon = Icons.Default.Link,
                onClick = onNavigateToPlatforms,
            ),
            DomainSpec(
                title = t("Projects", "پروژه‌ها"),
                subtitle = t("Browse sessions by project", "مرور گفتگوها بر اساس پروژه"),
                icon = Icons.Default.Folder,
                onClick = onNavigateToProjects,
            ),
            DomainSpec(
                title = t("Billing", "صورتحساب"),
                subtitle = t("Balance and auto-reload", "موجودی و شارژ خودکار"),
                icon = Icons.Default.AccountBalanceWallet,
                onClick = onNavigateToBilling,
            ),
            DomainSpec(
                title = t("Pet", "پت"),
                subtitle = t("Adopt and manage your pet", "انتخاب و مدیریت پت"),
                icon = Icons.Default.Pets,
                onClick = onNavigateToPet,
            ),
            DomainSpec(
                title = t("Advanced", "پیشرفته"),
                subtitle = t("env · MCP · console · log", "env · MCP · کنسول · لاگ"),
                icon = Icons.Default.Terminal,
                onClick = { onOpen(SettingsSection.ADVANCED) },
            ),
            DomainSpec(
                title = t("Appearance", "ظاهر"),
                subtitle = t("Theme, font, avatar, language", "تم، فونت، آواتار، زبان"),
                icon = Icons.Default.Language,
                onClick = { onOpen(SettingsSection.GENERAL) },
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.chunked(2).forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTiles.forEach { spec ->
                        com.hermes.android.ui.design.DomainTile(
                            title = spec.title,
                            subtitle = spec.subtitle,
                            icon = spec.icon,
                            onClick = spec.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One entry of the Control Center domain grid. */
private data class DomainSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Persian labels for approvals.mode values (hub subtitle). */
private fun approvalModeFa(mode: String): String = when (mode) {
    "manual" -> "دستی"
    "smart" -> "هوشمند"
    "off" -> "خاموش"
    else -> mode
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    // Client-side appearance/identity/language only — everything that talks
    // to the server moved to its own section (Behavior, Models, Advanced).
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
                    // FlowRow (not Row) so chips wrap to the next line instead
                    // of getting squeezed horizontally — 6 themes in a single
                    // Row was forcing each chip too narrow and Persian labels
                    // like "ایندیگو" were rendering one character per line.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ColorTheme.entries.forEach { theme ->
                            val label = t(theme.displayEn, theme.displayFa)
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.colorTheme == theme,
                                onClick = { themeModeState.updateColorTheme(theme) },
                                label = { Text(label, maxLines = 1) },
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
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = t("Font", "فونت"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFont.entries.forEach { font ->
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.appFont == font,
                                onClick = { themeModeState.updateAppFont(font) },
                                label = { Text(t(font.displayEn, font.displayFa)) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    // Font size slider — scales the entire app typography
                    // together (80%..140%). 100% = designer baseline.
                    Text(
                        text = t("Font size", "اندازه فونت"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = themeModeState.fontScalePct.toFloat(),
                            onValueChange = { themeModeState.updateFontScalePct(it.toInt()) },
                            valueRange = 80f..140f,
                            steps = 11,  // 80,85,...,140 → 5% increments
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${themeModeState.fontScalePct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
        }

        // -- Personalization: top bar identity --
        // Lets the user pick what the chat top bar shows to represent the
        // assistant (their chosen name, or the avatar image), set the name
        // itself, and adjust the avatar size when shown. Tapping the
        // identity in the top bar opens this section.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Top bar", "نوار بالا"),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = t("Display in top bar", "نمایش در نوار بالا"),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TopBarDisplay.entries.forEach { mode ->
                        androidx.compose.material3.FilterChip(
                            selected = themeModeState?.topBarDisplay == mode,
                            onClick = { themeModeState?.updateTopBarDisplay(mode) },
                            label = { Text(t(mode.displayEn, mode.displayFa), maxLines = 1) },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = t("Assistant name", "نام دستیار"),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = themeModeState?.assistantName ?: "Hermes",
                    onValueChange = { themeModeState?.updateAssistantName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(t("Hermes", "هرمس")) },
                )
                if (themeModeState?.topBarDisplay == TopBarDisplay.AVATAR) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = t("Avatar size", "اندازه آواتار"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = (themeModeState?.avatarSizeDp ?: 36).toFloat(),
                            onValueChange = { themeModeState?.updateAvatarSizeDp(it.toInt()) },
                            valueRange = 28f..48f,
                            steps = 9,  // 28,30,...,48 → 2dp increments
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${themeModeState?.avatarSizeDp ?: 36} dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
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

        // -- Assistant avatar (client-side, shown next to agent replies) --
        Text(
            text = t("Avatar", "آواتار"),
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
            }
        }
    }
}

/**
 * Agent Behavior (approved design G): approval mode with risk copy, the
 * 7-level reasoning effort, the personality preset, and the SOUL.md
 * identity editor. Every control maps to a real server write —
 * approvals.mode / reasoning / display.personality via config.set,
 * SOUL.md via the verified shell.exec pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BehaviorSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Command approval (approvals.mode) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Command Approval", "تأیید فرمان‌ها"),
                    style = MaterialTheme.typography.titleSmall,
                )
                // Segmented control, one piece per mode — the mockup-G shape.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        "manual" to t("Manual", "دستی"),
                        "smart" to t("Smart", "هوشمند"),
                        "off" to t("Off", "خاموش"),
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.approvalMode == mode,
                            onClick = { viewModel.setApprovalMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                val approvalDescription = when (state.approvalMode) {
                    "manual" -> t(
                        "Every risky command asks for your permission before running — the safest mode.",
                        "هر فرمان پرریسک قبل از اجرا از شما اجازه می‌گیرد — امن‌ترین حالت.",
                    )
                    "smart" -> t(
                        "Low-risk commands run automatically; risky ones still ask.",
                        "فرمان‌های کم‌خطر خودکار اجرا می‌شوند؛ پرریسک‌ها همچنان می‌پرسند.",
                    )
                    "off" -> t(
                        "Nothing asks for permission (yolo). Only for servers you can afford to lose.",
                        "هیچ‌چیز اجازه نمی‌گیرد (yolo). فقط برای سروری که از دست دادنش مهم نیست.",
                    )
                    else -> state.approvalMode
                }
                Text(
                    text = approvalDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.approvalMode == "off") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }

        // ── Reasoning effort (agent.reasoning_effort) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Reasoning depth", "عمق تفکر"),
                    style = MaterialTheme.typography.titleSmall,
                )
                // 7-segment fill bar (mockup-G shape): tap a segment to set
                // the level; segments up to the current level are filled.
                val levels = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
                val currentIdx = levels.indexOf(state.reasoning).let { if (it < 0) 3 else it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    levels.forEachIndexed { i, level ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setReasoning(level) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (i <= currentIdx) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "none",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.reasoning,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = t(
                        "Also switchable mid-session from the chat input bar.",
                        "وسط جلسه هم از نوار ورودی چت قابل تغییر است.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        // ── Personality preset (display.personality) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
        }

        // ── SOUL.md — the agent's persistent identity (first slot of the
        //    system prompt). Mockup-G shape: header row with an Edit action,
        //    a monospace preview when collapsed, the editor when editing. ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var editingSoul by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t("SOUL.md — persistent identity", "SOUL.md — هویت پایدار عامل"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingSoul = !editingSoul }) {
                        Text(if (editingSoul) t("Close", "بستن") else t("Edit", "ویرایش"))
                    }
                }
                Text(
                    text = t(
                        "The agent's persistent voice & identity — first part of its system prompt",
                        "هویت و لحن ماندگار ایجنت — اولین بخش از دستور سیستم",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                when {
                    state.isLoadingSoul -> CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    editingSoul -> {
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
                    else -> Text(
                        text = state.soulMd.ifBlank { t("(empty — tap Edit)", "(خالی — روی ویرایش بزنید)") },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Advanced (approved design I): env editor + reload, MCP servers editor +
 * reload, a command console over shell.exec (with process.stop as the
 * emergency brake), the live gateway stderr log, and the raw config view.
 */
@Composable
private fun AdvancedSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Environment variables (~/.hermes/.env) — mockup-I shape: a
        //    compact card with an Edit action; the editor and its warning
        //    only unfold when asked for. ──
        var editingEnv by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t("Environment Variables (.env)", "متغیرهای محیطی (.env)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingEnv = !editingEnv }) {
                        Text(if (editingEnv) t("Close", "بستن") else t("Edit", "ویرایش"))
                    }
                }
                Text(
                    text = t("~/.hermes/.env — API keys and other env vars", "~/.hermes/.env — کلیدهای API و سایر متغیرها"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (editingEnv) {
                // Explicit, unmissable warning — this file is raw shell-
                // sourced key=value config read directly into the agent
                // process; a bad edit here can break the agent's startup or
                // wipe a working API key with no undo.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = t(
                            "Advanced setting. If you don't know what this does, don't touch it — a bad edit here can break the agent or the whole system.",
                            "تنظیمات پیشرفته. اگه نمی‌دونی این چیه، دستش نزن — یه ویرایش اشتباه اینجا می‌تونه ایجنت یا کل سیستم رو خراب کنه.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
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
        }

        // ── MCP servers (config.yaml: mcp_servers) — same collapsed shape. ──
        var editingMcp by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t("MCP Servers", "سرورهای MCP"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingMcp = !editingMcp }) {
                        Text(if (editingMcp) t("Close", "بستن") else t("Edit", "ویرایش"))
                    }
                }
                Text(
                    text = t("Raw JSON — config.yaml's mcp_servers section", "JSON خام — بخش mcp_servers فایل config.yaml"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (editingMcp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = t(
                            "Advanced setting. Invalid JSON here will fail to save; a wrong server entry can stop MCP tools from loading.",
                            "تنظیمات پیشرفته. JSON نامعتبر ذخیره نمی‌شه؛ یه ورودی اشتباه می‌تونه باعث بشه ابزارهای MCP لود نشن.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
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
        }

        // ── Command console (shell.exec) ──
        Text(
            text = t("Command Console", "کنسول فرمان"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
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
                    text = t(
                        "Run one-off shell commands on the server — for diagnostics without SSH.",
                        "اجرای فرمان‌های تکی روی سرور — برای عیب‌یابی بدون SSH.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                state.consoleEntries.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "$ ${entry.command}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = entry.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                            ),
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 12,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                var consoleInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = consoleInput,
                    onValueChange = { consoleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("df -h /") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                    ),
                    singleLine = true,
                    enabled = !state.isConsoleRunning,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.runConsoleCommand(consoleInput)
                            consoleInput = ""
                        },
                        enabled = consoleInput.isNotBlank() && !state.isConsoleRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isConsoleRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(t("Run", "اجرا"))
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopProcesses() },
                        modifier = Modifier.weight(1f),
                    ) { Text(t("Stop processes", "توقف فرایندها")) }
                }
            }
        }

        // ── Gateway log (gateway.stderr stream) ──
        Text(
            text = t("Gateway Log", "لاگ گیت‌وی"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
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
                    text = t(
                        "Live server stderr — fills in as events arrive while the app is open.",
                        "stderr زندهٔ سرور — تا وقتی برنامه باز است با رسیدن رویدادها پر می‌شود.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (state.gatewayLog.isEmpty()) {
                    Text(
                        text = t("No log lines yet", "هنوز خطی نرسیده"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = state.gatewayLog.takeLast(40).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
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
