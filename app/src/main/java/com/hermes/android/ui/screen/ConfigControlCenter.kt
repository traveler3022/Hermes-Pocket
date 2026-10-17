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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hermes.android.R
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.AutoAwesome
import com.hermes.android.ui.icons.filled.BarChart
import com.hermes.android.ui.icons.filled.Code
import com.hermes.android.ui.icons.filled.CloudDownload
import com.hermes.android.ui.icons.filled.DataUsage
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.Extension
import com.hermes.android.ui.icons.filled.Folder
import com.hermes.android.ui.icons.filled.Forum
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.icons.filled.Language
import com.hermes.android.ui.icons.filled.Link
import com.hermes.android.ui.icons.filled.Mic
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
    onNavigateToWebhooks: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToLinux: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToOps: () -> Unit,
) {
    val agentTint = MaterialTheme.colorScheme.primary
    val capabilityTint = MaterialTheme.colorScheme.tertiary
    val workTint = MaterialTheme.colorScheme.secondary
    val systemTint = MaterialTheme.colorScheme.onSurfaceVariant

    val toolsOn = state.availableTools.count { it.enabled }
    val toolsTotal = state.availableTools.size

    val context = LocalContext.current
    var isPhoneAssistant by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        isPhoneAssistant = holdsAssistantRole(context)
        onPauseOrDispose { }
    }

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

        // ── Agent ──
        CcGroup(t("Agent", "ایجنت")) {
            CcRow(
                title = stringResource(R.string.personality),
                icon = Icons.Default.Face,
                tint = agentTint,
                value = state.personality.ifBlank { t("Default", "پیش‌فرض") },
                monoValue = state.personality.isNotBlank(),
                onClick = { onOpen(SettingsSection.PERSONALITY) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.models),
                icon = Icons.Default.Psychology,
                tint = agentTint,
                subtitle = state.activeModel?.let { "${state.activeProvider ?: "?"} / $it" },
                onClick = { onOpen(SettingsSection.MODELS) },
            )
        }

        // ── Capabilities & permissions ──
        CcGroup(t("Capabilities & permissions", "قابلیت‌ها و مجوزها")) {
            CcRow(
                title = stringResource(R.string.tools),
                icon = Icons.Default.Key,
                tint = capabilityTint,
                value = if (toolsTotal > 0) t("$toolsOn of $toolsTotal", "$toolsOn از $toolsTotal") else null,
                onClick = { onOpen(SettingsSection.TOOLSETS) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.command_approval_2),
                icon = Icons.Default.Security,
                tint = capabilityTint,
                value = t(state.approvalMode, approvalModeFa(state.approvalMode)),
                onClick = { onOpen(SettingsSection.APPROVAL) },
            )
            CcDivider()
            CcRow(
                title = t("Skills", "مهارت‌ها"),
                icon = Icons.Default.AutoAwesome,
                tint = capabilityTint,
                onClick = onNavigateToSkills,
            )
            CcDivider()
            CcRow(
                title = t("Plugins", "افزونه‌ها"),
                icon = Icons.Default.Extension,
                tint = capabilityTint,
                onClick = onNavigateToPlugins,
            )
            CcDivider()
            CcRow(
                title = t("MCP servers", "سرورهای MCP"),
                icon = Icons.Default.Link,
                tint = capabilityTint,
                onClick = onNavigateToMcp,
            )
        }

        // ── Work ──
        CcGroup(t("Work", "کارها")) {
            CcRow(
                title = stringResource(R.string.projects),
                icon = Icons.Default.Folder,
                tint = workTint,
                subtitle = stringResource(R.string.browse_sessions_by_project),
                onClick = onNavigateToProjects,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.scheduler),
                icon = Icons.Default.Schedule,
                tint = workTint,
                subtitle = stringResource(R.string.scheduled_agent_jobs),
                onClick = onNavigateToCron,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.kanban_board),
                icon = Icons.Default.WorkOutline,
                tint = workTint,
                onClick = { onOpen(SettingsSection.KANBAN) },
            )
        }

        // ── Messengers & connections ──
        CcGroup(t("Messengers & connections", "پیام‌رسان‌ها و اتصالات")) {
            CcRow(
                title = stringResource(R.string.platforms),
                icon = Icons.Default.Forum,
                tint = agentTint,
                subtitle = stringResource(R.string.telegram_discord_slack),
                onClick = onNavigateToPlatforms,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.webhooks_title),
                icon = Icons.Default.Code,
                tint = agentTint,
                onClick = onNavigateToWebhooks,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.assistant_title),
                icon = Icons.Default.Mic,
                tint = agentTint,
                value = if (isPhoneAssistant) t("On", "فعال") else t("Not set", "تنظیم نشده"),
                onClick = { onOpen(SettingsSection.PHONE_ASSISTANT) },
            )
        }

        // ── App ──
        CcGroup(t("App", "برنامه")) {
            CcRow(
                title = stringResource(R.string.appearance),
                icon = Icons.Default.Palette,
                tint = systemTint,
                value = themeModeState?.let { "${themeModeLabel(it.mode)} · ${t(it.colorTheme.displayEn, it.colorTheme.displayFa)}" },
                onClick = { onOpen(SettingsSection.APPEARANCE) },
            )
            if (appLanguageState != null) {
                CcDivider()
                CcRow(
                    title = stringResource(R.string.language),
                    icon = Icons.Default.Language,
                    tint = systemTint,
                    value = languageLabel(appLanguageState.language),
                    onClick = { onOpen(SettingsSection.LANGUAGE) },
                )
            }
            CcDivider()
            CcRow(
                title = stringResource(R.string.analytics_title),
                icon = Icons.Default.BarChart,
                tint = systemTint,
                value = state.insights?.let { t("${it.sessions} sessions", "${it.sessions} جلسه") },
                onClick = onNavigateToAnalytics,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.achievements_title),
                icon = Icons.Default.Star,
                tint = systemTint,
                onClick = onNavigateToAchievements,
            )
        }

        // ── System ──
        CcGroup(t("System", "سیستم")) {
            CcRow(
                title = stringResource(R.string.updates),
                icon = Icons.Default.CloudDownload,
                tint = systemTint,
                subtitle = stringResource(R.string.app_and_hermes_core_versions),
                onClick = { onOpen(SettingsSection.ABOUT) },
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.linux),
                icon = Icons.Default.Terminal,
                tint = systemTint,
                subtitle = stringResource(R.string.terminal_files_packages),
                onClick = onNavigateToLinux,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.storage),
                icon = Icons.Default.DataUsage,
                tint = systemTint,
                onClick = onNavigateToStorage,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.ops_title),
                icon = Icons.Default.Build,
                tint = systemTint,
                subtitle = stringResource(R.string.ops_nav_subtitle),
                onClick = onNavigateToOps,
            )
            CcDivider()
            CcRow(
                title = stringResource(R.string.advanced),
                icon = Icons.Default.Settings,
                tint = systemTint,
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
            onClick = onNavigateToRuntime,
            trailing = { StatusChip(label = connLabel, color = connColor) },
        )
        CcDivider()
        CcRow(
            title = stringResource(R.string.setup_wizard),
            icon = Icons.Default.RocketLaunch,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            subtitle = stringResource(R.string.runtime_ai_provider_api_key_and_model),
            onClick = onNavigateToSetup,
        )
    }
}

/** Section title + one rounded group of rows. */
@Composable
private fun CcGroup(title: String, content: @Composable () -> Unit) {
    SectionHeader(text = title)
    SettingsGroup { content() }
}

/** Divider aligned with the row text (row padding 16 + icon 34 + gap 12). */
@Composable
private fun CcDivider() = GroupDivider(startIndent = 62.dp)

/**
 * One Control Center row: tinted icon, title, an optional one-line subtitle,
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
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.size(8.dp))
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
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
private fun languageLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.AUTO -> stringResource(R.string.auto)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.FARSI -> "فارسی"
}
