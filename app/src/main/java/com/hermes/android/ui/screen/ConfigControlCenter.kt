package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HxIconColors
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.AutoAwesome
import com.hermes.android.ui.icons.filled.BarChart
import com.hermes.android.ui.icons.filled.CloudDownload
import com.hermes.android.ui.icons.filled.DataUsage
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.Extension
import com.hermes.android.ui.icons.filled.Folder
import com.hermes.android.ui.icons.filled.Forum
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.icons.filled.Link
import com.hermes.android.ui.icons.filled.Palette
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.icons.filled.RocketLaunch
import com.hermes.android.ui.icons.filled.Schedule
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.icons.filled.Terminal
import com.hermes.android.ui.icons.filled.WorkOutline
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.GatewayConnectionUi

/**
 * Settings shown in the Control Center design whose backend is not wired here yet
 * (the update banner, the scheduled-jobs stat).
 * Their UI is built and kept off; flip this once the gateway side lands.
 */
internal object PendingSettings {
    const val ENABLED = false
}

/**
 * The Control Center root (design 10): a connection card, live stats, then one
 * short grouped list. Each row shows its current value on the trailing side and
 * opens either a settings section or one of the app's own screens.
 */
@Composable
internal fun ControlCenterMenu(
    state: ConfigUiState,
    connection: GatewayConnectionUi,
    serverUrl: String,
    themeModeState: ThemeModeState?,
    appLanguageState: AppLanguageState?,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToRuntime: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToCron: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToPlatforms: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToLinux: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToOps: () -> Unit,
) {
    val toolsOn = state.availableTools.count { it.enabled }
    val toolsTotal = state.availableTools.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        ConnectionCard(
            connection = connection,
            serverUrl = serverUrl,
            onNavigateToRuntime = onNavigateToRuntime,
            onNavigateToSetup = onNavigateToSetup,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                value = if (toolsTotal > 0) "$toolsOn/$toolsTotal" else "—",
                label = t("Tools on", "ابزار روشن"),
            )
            if (PendingSettings.ENABLED) {
                StatTile(value = "—", label = t("Scheduled jobs", "کار زمان‌بندی"))
            }
            StatTile(
                value = state.insights?.let { "${it.sessions}" } ?: "—",
                label = stringResource(R.string.sessions_30d),
            )
        }

        if (PendingSettings.ENABLED) {
            UpdateBanner(onUpdate = { onOpen(SettingsSection.ABOUT) })
        }

        // Each group its own card, no section titles, a short gap between cards
        // (Telegram's settings layout).
        Spacer(Modifier.height(4.dp))
        // ── Agent ──
        CcGroup(first = true) {
            CcRow(
                title = stringResource(R.string.personality),
                icon = Icons.Default.Face,
                tint = HxIconColors.pink,
                value = state.personality.ifBlank { t("Default", "پیش‌فرض") },
                monoValue = state.personality.isNotBlank(),
                onClick = { onOpen(SettingsSection.PERSONALITY) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.models),
                icon = Icons.Default.Psychology,
                tint = HxIconColors.purple,
                subtitle = state.activeModel?.let { "${state.activeProvider ?: "?"} / $it" },
                onClick = { onOpen(SettingsSection.MODELS) },
            )
        }

        // ── Appearance, then capabilities & permissions, in one card ──
        CcGroup {
            CcRow(
                title = stringResource(R.string.appearance),
                icon = Icons.Default.Palette,
                tint = HxIconColors.purple,
                value = themeModeState?.let { "${themeModeLabel(it.mode)} · ${t(it.colorTheme.displayEn, it.colorTheme.displayFa)}" },
                onClick = { onOpen(SettingsSection.APPEARANCE) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.tools),
                icon = Icons.Default.Key,
                tint = HxIconColors.orange,
                value = if (toolsTotal > 0) t("$toolsOn of $toolsTotal", "$toolsOn از $toolsTotal") else null,
                onClick = { onOpen(SettingsSection.TOOLSETS) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.command_approval_2),
                icon = Icons.Default.Security,
                tint = HxIconColors.green,
                value = t(state.approvalMode, approvalModeFa(state.approvalMode)),
                onClick = { onOpen(SettingsSection.APPROVAL) },
            )
            CcDivider()
            CcRow(
                title = t("Skills", "مهارت‌ها"),
                icon = Icons.Default.AutoAwesome,
                tint = HxIconColors.blue,
                onClick = onNavigateToSkills,
            )
            CcDivider()
            CcRow(
                title = t("Plugins", "افزونه‌ها"),
                icon = Icons.Default.Extension,
                tint = HxIconColors.cyan,
                onClick = onNavigateToPlugins,
            )
            CcDivider()
            CcRow(
                title = t("MCP servers", "سرورهای MCP"),
                icon = Icons.Default.Link,
                tint = HxIconColors.indigo,
                onClick = onNavigateToMcp,
            )
        }

        // ── Work ──
        CcGroup {
            CcRow(
                title = stringResource(R.string.projects),
                icon = Icons.Default.Folder,
                tint = HxIconColors.blue,
                subtitle = stringResource(R.string.browse_sessions_by_project),
                onClick = onNavigateToProjects,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.scheduler),
                icon = Icons.Default.Schedule,
                tint = HxIconColors.orange,
                subtitle = stringResource(R.string.scheduled_agent_jobs),
                onClick = onNavigateToCron,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.kanban_board),
                icon = Icons.Default.WorkOutline,
                tint = HxIconColors.green,
                onClick = { onOpen(SettingsSection.KANBAN) },
            )
        }

        // ── Messengers & connections ──
        CcGroup {
            CcRow(
                title = stringResource(R.string.connections_and_platforms),
                icon = Icons.Default.Forum,
                tint = HxIconColors.cyan,
                subtitle = stringResource(R.string.connections_and_platforms_subtitle),
                onClick = onNavigateToPlatforms,
            )
        }

        // ── App ──
        CcGroup {
            CcRow(
                title = stringResource(R.string.analytics_title),
                icon = Icons.Default.BarChart,
                tint = HxIconColors.blue,
                value = state.insights?.let { t("${it.sessions} sessions", "${it.sessions} جلسه") },
                onClick = onNavigateToAnalytics,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.achievements_title),
                icon = Icons.Default.Star,
                tint = HxIconColors.orange,
                onClick = onNavigateToAchievements,
            )
        }

        // ── System ──
        CcGroup {
            CcRow(
                title = stringResource(R.string.updates),
                icon = Icons.Default.CloudDownload,
                tint = HxIconColors.green,
                subtitle = stringResource(R.string.app_and_hermes_core_versions),
                onClick = { onOpen(SettingsSection.ABOUT) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.linux),
                icon = Icons.Default.Terminal,
                tint = HxIconColors.gray,
                subtitle = stringResource(R.string.terminal_files_packages),
                onClick = onNavigateToLinux,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.storage),
                icon = Icons.Default.DataUsage,
                tint = HxIconColors.blue,
                onClick = onNavigateToStorage,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.ops_title),
                icon = Icons.Default.Build,
                tint = HxIconColors.orange,
                subtitle = stringResource(R.string.ops_nav_subtitle),
                onClick = onNavigateToOps,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.advanced),
                icon = Icons.Default.Settings,
                tint = HxIconColors.gray,
                subtitle = stringResource(R.string.env_mcp_console_log),
                onClick = { onOpen(SettingsSection.ADVANCED) },
            )
        }
    }
}

// ── Pieces of the root ────────────────────────────────────────────────────

@Composable
private fun ConnectionCard(
    connection: GatewayConnectionUi,
    serverUrl: String,
    onNavigateToRuntime: () -> Unit,
    onNavigateToSetup: () -> Unit,
) {
    val (connColor, connLabel) = when (connection.state) {
        ChatConnectionState.Connected ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.connected_2)
        ChatConnectionState.Connecting ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.connecting_2)
        ChatConnectionState.Reconnecting ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.reconnecting_2)
        ChatConnectionState.Failed ->
            MaterialTheme.colorScheme.error to stringResource(R.string.connection_failed_2)
        ChatConnectionState.Disconnected ->
            MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.not_connected)
    }
    SettingsGroup {
        SettingRow(
            title = serverUrl.ifBlank { stringResource(R.string.no_server_configured) },
            subtitle = stringResource(R.string.server_connection_settings),
            icon = Icons.Default.Dns,
            iconTint = HxIconColors.blue,
            solidIcon = true,
            onClick = onNavigateToRuntime,
            trailing = { StatusChip(label = connLabel, color = connColor) },
        )
        CcDivider()
        CcRow(
            title = stringResource(R.string.setup_wizard),
            icon = Icons.Default.RocketLaunch,
            tint = HxIconColors.purple,
            subtitle = stringResource(R.string.runtime_ai_provider_api_key_and_model),
            onClick = onNavigateToSetup,
        )
    }
}

/** One group of rows in its own card, [CC_GROUP_GAP] below the previous one. */
@Composable
private fun CcGroup(first: Boolean = false, content: @Composable () -> Unit) {
    if (!first) Spacer(Modifier.height(CC_GROUP_GAP))
    SettingsGroup { content() }
}

/** The gap between two groups: enough to read as separate cards, under a line of text. */
private val CC_GROUP_GAP = 16.dp

/** Divider aligned with the row text (row padding 16 + icon 34 + gap 12). */
@Composable
private fun CcDivider() = GroupDivider(startIndent = 62.dp)

/**
 * One Control Center row: solid coloured icon tile, title, an optional one-line subtitle,
 * the current value on the trailing side, then a chevron.
 */
@Composable
internal fun CcRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    monoValue: Boolean = false,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = tint,
        solidIcon = true,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (monoValue) it.copy(fontFamily = FontFamily.Monospace) else it
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** Core-update notice for the root. Behind [PendingSettings] until the backend reports a new version here. */
@Composable
private fun UpdateBanner(onUpdate: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("A new Hermes core is out", "نسخهٔ جدید هستهٔ هرمس آمده"), style = MaterialTheme.typography.titleSmall)
            Text(
                t("Hermes goes offline for a few minutes to install it.", "هرمس چند دقیقه برای نصب خاموش می‌شود."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onUpdate) { Text(t("Update", "به‌روزرسانی")) }
                TextButton(onClick = { dismissed = true }) { Text(t("Later", "بعداً")) }
            }
        }
    }
}

// ── Small sections opened from the root ──────────────────────────────────

/** App language — moved out of Appearance so it has its own row on the root. */
@Composable
internal fun LanguageSection(appLanguageState: AppLanguageState?) {
    SettingsFolder {
        if (appLanguageState != null) SettingsCardGroup {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEachIndexed { i, lang ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val selected = appLanguageState.language == lang
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { appLanguageState.updateLanguage(lang) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = com.hermes.android.ui.design.HxSpace.row),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(languageLabel(lang), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Phone assistant: the existing card, now its own page under Messengers & connections. */
@Composable
internal fun PhoneAssistantSection() {
    SettingsFolder {
        SettingsCardGroup { AssistantSettingCard() }
    }
}

/** Kanban board switch, moved from Appearance to the Work group. */
@Composable
internal fun KanbanSection() {
    SettingsFolder {
        SettingsCardGroup {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = com.hermes.android.ui.design.HxSpace.row)) {
                KanbanSettingRow()
            }
        }
    }
}

// ── Labels ────────────────────────────────────────────────────────────────

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> t("System", "سیستم")
    ThemeMode.LIGHT -> t("Light", "روشن")
    ThemeMode.DARK -> t("Dark", "تاریک")
}

@Composable
internal fun languageLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.AUTO -> stringResource(R.string.auto)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.FARSI -> "فارسی"
}
