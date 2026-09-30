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
import com.hermes.android.ui.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.hermes.android.ui.icons.filled.Language
import com.hermes.android.ui.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.icons.filled.Security
import androidx.compose.material.icons.filled.Build
import com.hermes.android.ui.icons.filled.Extension
import androidx.compose.material.icons.filled.Star
import com.hermes.android.ui.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption

/** Tools folder: Toolsets, Plugins, Skills and MCP servers, one row each. */
@Composable
internal fun ToolsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToMcp: () -> Unit,
) {
    SettingsFolder {
        SettingsCardGroup {
            SettingsNavRow(
                title = t("Toolsets", "گروه\u200Cهای ابزار"),
                subtitle = if (state.availableTools.isNotEmpty()) {
                    val enabled = state.availableTools.count { it.enabled }
                    t(
                        "$enabled of ${state.availableTools.size} on",
                        "$enabled از ${state.availableTools.size} فعال",
                    )
                } else {
                    t("Enable or disable tools", "فعال/غیرفعال کردن ابزارها")
                },
                icon = Icons.Default.Build,
                onClick = { onOpen(SettingsSection.TOOLSETS) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Plugins", "افزونه\u200Cها"),
                subtitle = t("Install and manage plugins", "نصب و مدیریت افزونه\u200Cها"),
                icon = Icons.Default.Extension,
                onClick = onNavigateToPlugins,
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Skills", "مهارت\u200Cها"),
                subtitle = t("Browse and manage skills", "مرور و مدیریت مهارت\u200Cها"),
                icon = Icons.Default.Star,
                onClick = onNavigateToSkills,
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("MCP Servers", "سرورهای MCP"),
                subtitle = t("Catalog, sign-in and connection tests", "کاتالوگ، ورود و تست اتصال"),
                icon = Icons.Default.Dns,
                onClick = onNavigateToMcp,
            )
        }
    }
}

/** The toolset switches. */
@Composable
internal fun ToolsetsSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    if (state.isLoadingTools) {
        LoadingIndicator(t("Loading tools…", "در حال بارگذاری ابزارها…"))
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
internal fun ToolRow(tool: ToolOption, viewModel: ConfigViewModel) {
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

