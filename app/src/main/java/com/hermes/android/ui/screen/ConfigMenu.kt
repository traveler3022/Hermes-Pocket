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
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.BarChart
import com.hermes.android.ui.icons.filled.DataUsage
import com.hermes.android.ui.icons.filled.Folder
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import com.hermes.android.ui.icons.filled.Schedule
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.icons.filled.SwapHoriz
import com.hermes.android.ui.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.StatTile

/**
 * The Settings root, restructured as the Control Center (approved design E):
 * a live connection card, live stat tiles (active model / 30-day usage -
 * `insights.get` was a backend capability no UI ever surfaced), then the
 * domain list with live values in the subtitles
 * where the data is already loaded.
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
    onNavigateToStorage: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        val (connColor, connLabel) = when (connection.state) {
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connected ->
                MaterialTheme.colorScheme.primary to stringResource(R.string.connected_2)
            com.hermes.android.ui.viewmodel.ChatConnectionState.Connecting ->
                MaterialTheme.colorScheme.tertiary to stringResource(R.string.connecting_2)
            com.hermes.android.ui.viewmodel.ChatConnectionState.Reconnecting ->
                MaterialTheme.colorScheme.tertiary to stringResource(R.string.reconnecting_2)
            com.hermes.android.ui.viewmodel.ChatConnectionState.Failed ->
                MaterialTheme.colorScheme.error to stringResource(R.string.connection_failed_2)
            com.hermes.android.ui.viewmodel.ChatConnectionState.Disconnected ->
                MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.not_connected)
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
                        text = serverUrl.ifBlank { stringResource(R.string.no_server_configured) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.server_connection_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                com.hermes.android.ui.design.StatusChip(label = connLabel, color = connColor)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSetup)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_wizard), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.runtime_ai_provider_api_key_and_model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                label = stringResource(R.string.active_model),
            )
            StatTile(
                value = state.insights?.let { "${it.sessions}" } ?: "\u2014",
                label = stringResource(R.string.sessions_30d),
            )
        }

        val tiles = listOf(
            DomainSpec(
                title = stringResource(R.string.general_settings),
                subtitle = stringResource(R.string.approval_personality_platforms_advanced),
                icon = Icons.Default.Security,
                onClick = { onOpen(SettingsSection.GENERAL_SETTINGS) },
            ),
            DomainSpec(
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.language_theme_font),
                icon = Icons.Default.Palette,
                onClick = { onOpen(SettingsSection.APPEARANCE) },
            ),
            DomainSpec(
                title = stringResource(R.string.models),
                subtitle = state.activeModel?.let { model ->
                    "${state.activeProvider ?: "?"} / $model"
                } ?: stringResource(R.string.model_api_keys),
                icon = Icons.Default.SwapHoriz,
                onClick = { onOpen(SettingsSection.MODELS) },
            ),
            DomainSpec(
                title = stringResource(R.string.tools),
                subtitle = if (state.availableTools.isNotEmpty()) {
                    val enabled = state.availableTools.count { it.enabled }
                    stringResource(R.string.of_toolsets_on, enabled, state.availableTools.size)
                } else {
                    stringResource(R.string.toolsets_plugins_skills)
                },
                icon = Icons.Default.Key,
                onClick = { onOpen(SettingsSection.TOOLS) },
            ),
            DomainSpec(
                title = stringResource(R.string.linux),
                subtitle = stringResource(R.string.terminal_files_packages),
                icon = Icons.Default.Terminal,
                onClick = onNavigateToLinux,
            ),
            DomainSpec(
                title = stringResource(R.string.scheduler),
                subtitle = stringResource(R.string.scheduled_agent_jobs),
                icon = Icons.Default.Schedule,
                onClick = onNavigateToCron,
            ),
            DomainSpec(
                title = stringResource(R.string.projects),
                subtitle = stringResource(R.string.browse_sessions_by_project),
                icon = Icons.Default.Folder,
                onClick = onNavigateToProjects,
            ),
            DomainSpec(
                title = stringResource(R.string.updates),
                subtitle = stringResource(R.string.app_and_hermes_core_versions),
                icon = Icons.Default.Info,
                onClick = { onOpen(SettingsSection.ABOUT) },
            ),
            DomainSpec(
                title = stringResource(R.string.analytics_title),
                subtitle = stringResource(R.string.analytics_nav_subtitle),
                icon = Icons.Default.BarChart,
                onClick = onNavigateToAnalytics,
            ),
            DomainSpec(
                title = stringResource(R.string.achievements_title),
                subtitle = stringResource(R.string.achievements_nav_subtitle),
                icon = Icons.Default.Star,
                onClick = onNavigateToAchievements,
            ),
            DomainSpec(
                title = stringResource(R.string.storage),
                subtitle = stringResource(R.string.find_and_clear_leftover_files),
                icon = Icons.Default.DataUsage,
                onClick = onNavigateToStorage,
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
