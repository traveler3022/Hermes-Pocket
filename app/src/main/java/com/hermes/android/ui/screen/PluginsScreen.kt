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
import com.hermes.android.ui.viewmodel.PluginItem
import com.hermes.android.ui.viewmodel.PluginsViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PluginItem?>(null) }
    var editing by remember { mutableStateOf<PluginItem?>(null) }

    if (showAdd) {
        AddPluginDialog(
            onAdd = { identifier, force -> showAdd = false; viewModel.installFromUrl(identifier, force) },
            onDismiss = { showAdd = false },
        )
    }
    if (showCatalog) {
        CatalogDialog(
            state = uiState,
            onInstall = { viewModel.installFromCatalog(it) },
            onDismiss = { showCatalog = false },
        )
    }
    removing?.let { plugin ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.plugin_remove_title, plugin.name)) },
            text = { Text(stringResource(R.string.plugin_remove_body)) },
            confirmButton = {
                TextButton(onClick = { removing = null; viewModel.remove(plugin) }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel_2)) } },
        )
    }
    editing?.let { plugin ->
        PluginSettingsDialog(
            plugin = plugin,
            onSave = { values -> editing = null; viewModel.saveSettings(plugin, values) },
            onDismiss = { editing = null },
        )
    }
    uiState.pendingConsent?.let { (plugin, lines) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConsent() },
            title = { Text(stringResource(R.string.plugin_consent_title, plugin.name)) },
            text = {
                Text(
                    lines.joinToString("\n").ifBlank { stringResource(R.string.plugin_consent_body) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.update(plugin, acceptCapabilities = true) }) {
                    Text(stringResource(R.string.plugin_consent_accept))
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissConsent() }) { Text(stringResource(R.string.cancel_2)) } },
        )
    }

    HermesScaffold(
        title = stringResource(R.string.plugins),
        subtitle = if (uiState.plugins.isEmpty()) null else {
            stringResource(R.string.of_enabled, uiState.plugins.count { it.enabled }, uiState.plugins.size)
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { showCatalog = true; viewModel.loadCatalog() }, enabled = !uiState.busy) {
                Icon(Icons.Default.Extension, contentDescription = stringResource(R.string.plugin_catalog))
            }
            IconButton(onClick = { showAdd = true }, enabled = !uiState.busy) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.plugin_add_url))
            }
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Every listed row switches, bundled ones too, as on desktop.
                                        Switch(
                                            checked = plugin.enabled,
                                            onCheckedChange = { viewModel.togglePlugin(plugin, it) },
                                        )
                                        PluginMenu(
                                            plugin = plugin,
                                            enabled = !uiState.busy,
                                            onUpdate = { viewModel.update(plugin) },
                                            onSettings = { editing = plugin },
                                            onRemove = { removing = plugin },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Update (catalog installs), settings (a declared schema), remove (user installs); nothing when none applies. */
@Composable
private fun PluginMenu(
    plugin: PluginItem,
    enabled: Boolean,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    if (plugin.catalogName == null && plugin.settings.isEmpty() && !plugin.removable) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (plugin.catalogName != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (plugin.updateAvailable) stringResource(R.string.plugin_update_available)
                            else stringResource(R.string.plugin_update),
                        )
                    },
                    onClick = { open = false; onUpdate() },
                )
            }
            if (plugin.settings.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_settings)) },
                    onClick = { open = false; onSettings() },
                )
            }
            if (plugin.removable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) },
                    onClick = { open = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun AddPluginDialog(onAdd: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_add_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Text(stringResource(R.string.plugin_add_url_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    singleLine = true,
                    placeholder = { Text("owner/repo") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = force, onCheckedChange = { force = it })
                    Text(stringResource(R.string.plugin_force), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(identifier, force) }, enabled = identifier.isNotBlank()) {
                Text(stringResource(R.string.plugin_install))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_2)) } },
    )
}

@Composable
private fun CatalogDialog(
    state: com.hermes.android.ui.viewmodel.PluginsUiState,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(state.catalog, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.catalog
        else state.catalog.filter { q in it.title.lowercase() || q in it.name.lowercase() || q in it.description.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_catalog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.plugin_catalog_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.catalogLoading -> CircularProgressIndicator()
                    state.catalogError != null -> Text(
                        stringResource(R.string.plugin_catalog_unavailable, state.catalogError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it.name }) { entry ->
                            Column(Modifier.padding(vertical = HxSpace.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            listOf(entry.tier, entry.category).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    if (entry.installed) {
                                        Text(stringResource(R.string.plugin_installed), style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        TextButton(onClick = { onInstall(entry.name) }, enabled = !state.busy) {
                                            Text(stringResource(R.string.plugin_install))
                                        }
                                    }
                                }
                                Text(
                                    entry.description.take(220),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Fields from the plugin's declared schema; secrets live in .env and are only shown as set or not. */
@Composable
private fun PluginSettingsDialog(
    plugin: PluginItem,
    onSave: (Map<String, JsonElement>) -> Unit,
    onDismiss: () -> Unit,
) {
    val texts = remember(plugin) {
        mutableStateMapOf<String, String>().apply {
            plugin.settings.filter { it.type != "secret" }.forEach { f ->
                val v = f.value ?: f.default
                put(f.key, (v as? JsonPrimitive)?.contentOrNull ?: v?.toString().orEmpty())
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_settings_title, plugin.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                plugin.settings.forEach { f ->
                    when (f.type) {
                        "secret" -> Text(
                            stringResource(
                                if (f.hasValue) R.string.plugin_secret_set else R.string.plugin_secret_unset,
                                f.label,
                                f.env.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, modifier = Modifier.weight(1f))
                            Switch(
                                checked = texts[f.key] == "true",
                                onCheckedChange = { texts[f.key] = it.toString() },
                            )
                        }
                        else -> {
                          val help = f.choices.takeIf { it.isNotEmpty() }?.joinToString(" / ")
                              ?: f.description.takeIf { it.isNotBlank() }
                          OutlinedTextField(
                            value = texts[f.key].orEmpty(),
                            onValueChange = { texts[f.key] = it },
                            label = { Text(f.label + if (f.required) " *" else "") },
                            supportingText = if (help != null) {
                                { Text(help) }
                            } else {
                                null
                            },
                            singleLine = f.type != "json",
                            modifier = Modifier.fillMaxWidth(),
                          )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = plugin.settings.filter { it.type != "secret" }.associate { f ->
                    val raw = texts[f.key].orEmpty()
                    f.key to when (f.type) {
                        "boolean" -> JsonPrimitive(raw == "true")
                        "number" -> raw.toLongOrNull()?.let { JsonPrimitive(it) }
                            ?: raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
                        "json" -> runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }
                        else -> JsonPrimitive(raw)
                    }
                }
                onSave(values)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_2)) } },
    )
}
