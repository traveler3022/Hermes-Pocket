package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t

/**
 * The Settings root (Control Center), laid out like Aether's settings hub:
 * a few grouped cards of navigation rows, with everything else nested one
 * level down so the root stays short.
 */
@Composable
internal fun SettingsMenu(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    connection: com.hermes.android.ui.viewmodel.GatewayConnectionUi,
    serverUrl: String,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToRuntime: () -> Unit,
    onNavigateToCron: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToSetup: () -> Unit = {},
    onNavigateToLinux: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

        // Connection
        SettingsCardGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToRuntime)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverUrl.ifBlank { t("No server configured", "سروری تنظیم نشده") },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = t("Server & connection settings", "تنظیمات سرور و اتصال"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                com.hermes.android.ui.design.StatusChip(label = connLabel, color = connColor)
            }
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Setup wizard", "راه‌اندازی اولیه"),
                subtitle = t("Runtime, AI provider, API key and model", "محیط اجرا، ارائه‌دهنده، کلید API و مدل"),
                icon = Icons.Default.AutoAwesome,
                onClick = onNavigateToSetup,
            )
        }

        // Agent
        SettingsCardGroup {
            SettingsNavRow(
                title = t("General Settings", "تنظیمات عمومی"),
                subtitle = t(
                    "Approval: ${state.approvalMode} · platforms, memory, advanced",
                    "تأیید: ${approvalModeFa(state.approvalMode)} · پلتفرم‌ها، حافظه، پیشرفته",
                ),
                icon = Icons.Default.Settings,
                onClick = { onOpen(SettingsSection.GENERAL_SETTINGS) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Models", "مدل‌ها"),
                subtitle = state.activeModel?.let { model ->
                    "${state.activeProvider ?: "?"} / $model · ${state.reasoning}"
                } ?: t("Model, API keys, reasoning", "مدل، کلید API، عمق تفکر"),
                icon = Icons.Default.SwapHoriz,
                onClick = { onOpen(SettingsSection.MODELS) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Tools", "ابزارها"),
                subtitle = if (state.availableTools.isNotEmpty()) {
                    val enabled = state.availableTools.count { it.enabled }
                    t(
                        "$enabled of ${state.availableTools.size} toolsets · plugins, skills, scheduler",
                        "$enabled از ${state.availableTools.size} گروه · افزونه‌ها، مهارت‌ها، زمان‌بندی",
                    )
                } else {
                    t("Toolsets, plugins, skills, scheduler", "ابزارها، افزونه‌ها، مهارت‌ها، زمان‌بندی")
                },
                icon = Icons.Default.Build,
                onClick = { onOpen(SettingsSection.TOOLS) },
            )
        }

        // Workspace
        SettingsCardGroup {
            SettingsNavRow(
                title = t("Linux (Alpine)", "لینوکس (Alpine)"),
                subtitle = t("Terminal, files, packages", "ترمینال، فایل‌ها، بسته‌ها"),
                icon = Icons.Default.Terminal,
                onClick = onNavigateToLinux,
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Projects", "پروژه‌ها"),
                subtitle = state.insights?.let {
                    t("${it.sessions} sessions in ${it.days} days", "${it.sessions} گفتگو در ${it.days} روز")
                } ?: t("Browse sessions by project", "مرور گفتگوها بر اساس پروژه"),
                icon = Icons.Default.Folder,
                onClick = onNavigateToProjects,
            )
        }

        // App
        SettingsCardGroup {
            SettingsNavRow(
                title = t("Appearance", "ظاهر"),
                subtitle = t("Theme, font, avatar, language", "تم، فونت، آواتار، زبان"),
                icon = Icons.Default.Palette,
                onClick = { onOpen(SettingsSection.APPEARANCE) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("About", "درباره"),
                subtitle = t("Version and updates", "نسخه و به‌روزرسانی"),
                icon = Icons.Default.Info,
                onClick = { onOpen(SettingsSection.ABOUT) },
            )
        }
    }
}

/** A rounded card holding a stack of settings rows (Aether's card group). */
@Composable
internal fun SettingsCardGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        content = content,
    )
}

/** One tappable row that opens a sub-page: icon, title, one-line subtitle, chevron. */
@Composable
internal fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Persian labels for approvals.mode values (hub subtitle). */
internal fun approvalModeFa(mode: String): String = when (mode) {
    "manual" -> "دستی"
    "smart" -> "هوشمند"
    "off" -> "خاموش"
    else -> mode
}
