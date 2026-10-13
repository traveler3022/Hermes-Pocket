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
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Extension
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
        title = stringResource(R.string.plugins),
        subtitle = if (uiState.plugins.isEmpty()) null else {
            stringResource(R.string.of_enabled, uiState.plugins.count { it.enabled }, uiState.plugins.size)
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.reloadPlugins() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
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
                    stringResource(R.string.loading_plugins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.plugins.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.no_plugins_available),
                    caption = stringResource(R.string.plugins_the_gateway_exposes_will_appear),
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
                                    if (plugin.source == "bundled") stringResource(R.string.bundled) else null
                                },
                                icon = Icons.Default.Extension,
                                iconTint = if (plugin.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                trailing = {
                                    // Every listed row switches, bundled ones too, as on desktop.
                                    Switch(
                                        checked = plugin.enabled,
                                        onCheckedChange = { viewModel.togglePlugin(plugin, it) },
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
