package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.PluginsViewModel

/**
 * Plugins Manager — list, enable/disable, reload. Rebuilt on the design
 * system as one grouped list of toggle rows; also fully bilingual now
 * (the old version was hardcoded English).
 *
 * Depends ONLY on [PluginsViewModel].
 */
@Composable
fun PluginsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Plugins", "افزونه‌ها"),
        subtitle = if (uiState.plugins.isEmpty()) null else {
            t(
                "${uiState.plugins.count { it.enabled }} of ${uiState.plugins.size} enabled",
                "${uiState.plugins.count { it.enabled }} از ${uiState.plugins.size} فعال",
            )
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.reloadPlugins() }) {
                Icon(Icons.Default.Refresh, contentDescription = t("Reload", "بارگذاری مجدد"))
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(HxSpace.sm))
                Text(
                    t("Loading plugins…", "در حال بارگذاری افزونه‌ها…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.plugins.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Extension,
                    title = t("No plugins available", "افزونه‌ای موجود نیست"),
                    caption = t(
                        "Plugins the gateway exposes will appear here",
                        "افزونه‌هایی که گیت‌وی ارائه بده اینجا نمایش داده می‌شن",
                    ),
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = HxSpace.sm, bottom = HxSpace.xl),
            ) {
                item {
                    SettingsGroup {
                        uiState.plugins.forEachIndexed { index, plugin ->
                            if (index > 0) GroupDivider()
                            SettingRow(
                                title = plugin.name,
                                subtitle = plugin.description.ifBlank {
                                    if (plugin.source == "bundled") t("Bundled", "همراه برنامه") else null
                                },
                                icon = Icons.Default.Extension,
                                iconTint = if (plugin.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                trailing = {
                                    Switch(
                                        checked = plugin.enabled,
                                        onCheckedChange = { viewModel.togglePlugin(plugin.name, it) },
                                        enabled = plugin.source != "bundled",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
