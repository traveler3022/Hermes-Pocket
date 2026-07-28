package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.i18n.t

/**
 * The Settings root, restructured as the Control Center (approved design E):
 * a live connection card, live stat tiles (active model / credits / 30-day
 * usage - `credits.view` and `insights.get` were backend capabilities no UI
 * ever surfaced), then the domain list with live values in the subtitles
 * where the data is already loaded.
 */
@Composable
internal fun SettingsMenu(
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
        val (connColor, connLabel) = when (connection.state) {
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connected ->
                MaterialTheme.colorScheme.primary to t("Connected", "متصل")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connecting ->
                MaterialTheme.colorScheme.tertiary to t("Connecting\u2026", "در حال اتصال\u2026")
            com.hermes.android.ui.viewmodel.ChatConnectionState.Reconnecting ->
                MaterialTheme.colorScheme.tertiary to t("Reconnecting\u2026", "اتصال دوباره\u2026")
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                value = state.activeModel ?: "\u2014",
                label = t("Active model", "مدل فعال"),
            )
            StatTile(
                value = state.creditsSummary ?: "\u2014",
                label = t("Credits", "اعتبار"),
            )
            StatTile(
                value = state.insights?.let { "${it.sessions}" } ?: "\u2014",
                label = t("Sessions / 30d", "جلسه / ۳۰ روز"),
            )
        }

        val tiles = listOf(
            DomainSpec(
                title = t("Models", "مدل\u200Cها"),
                subtitle = state.activeModel?.let { model ->
                    "${state.activeProvider ?: "?"} / $model"
                } ?: t("Model, API keys", "مدل، کلید API"),
                icon = Icons.Default.SwapHoriz,
                onClick = { onOpen(SettingsSection.MODELS) },
            ),
            DomainSpec(
                title = t("Behavior", "رفتار"),
                subtitle = t(
                    "Approval: ${state.approvalMode} \u00B7 ${state.reasoning}",
                    "تأیید: ${approvalModeFa(state.approvalMode)} \u00B7 تفکر: ${state.reasoning}",
                ),
                icon = Icons.Default.Security,
                onClick = { onOpen(SettingsSection.BEHAVIOR) },
            ),
            DomainSpec(
                title = t("Memory", "حافظه"),
                subtitle = t("USER.md \u00B7 MEMORY.md", "USER.md \u00B7 MEMORY.md"),
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
                title = t("Skills", "مهارت\u200Cها"),
                subtitle = t("Browse and manage skills", "مرور و مدیریت مهارت\u200Cها"),
                icon = Icons.Default.Star,
                onClick = onNavigateToSkills,
            ),
            DomainSpec(
                title = t("Plugins", "افزونه\u200Cها"),
                subtitle = t("Install and manage plugins", "نصب و مدیریت افزونه\u200Cها"),
                icon = Icons.Default.Extension,
                onClick = onNavigateToPlugins,
            ),
            DomainSpec(
                title = t("Scheduler", "زمان\u200Cبندی"),
                subtitle = t("Scheduled agent jobs", "کارهای زمان\u200Cبندی\u200Cشده"),
                icon = Icons.Default.Schedule,
                onClick = onNavigateToCron,
            ),
            DomainSpec(
                title = t("Platforms", "پلتفرم\u200Cها"),
                subtitle = t("Telegram, Discord, Slack", "تلگرام، دیسکورد، اسلک"),
                icon = Icons.Default.Link,
                onClick = onNavigateToPlatforms,
            ),
            DomainSpec(
                title = t("Projects", "پروژه\u200Cها"),
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
                subtitle = t("env \u00B7 MCP \u00B7 console \u00B7 log", "env \u00B7 MCP \u00B7 کنسول \u00B7 لاگ"),
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
internal data class DomainSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Persian labels for approvals.mode values (hub subtitle). */
internal fun approvalModeFa(mode: String): String = when (mode) {
    "manual" -> "دستی"
    "smart" -> "هوشمند"
    "off" -> "خاموش"
    else -> mode
}
