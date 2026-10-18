package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.icons.filled.Language
import com.hermes.android.ui.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption

@Composable
internal fun ProviderDropdown(
    providers: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.api_provider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = com.hermes.android.ui.design.settingsCardColor(),
            ),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected ?: stringResource(R.string.all_providers),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    // "All providers" — shows every model from every configured
                    // provider in one flat, searchable list instead of forcing
                    // a provider to be picked first just to browse models.
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.all_providers),
                                fontWeight = if (selected == null) FontWeight.Medium else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelect(null)
                            expanded = false
                        },
                    )
                    HorizontalDivider()
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = provider,
                                    fontWeight = if (provider == selected)
                                        FontWeight.Medium
                                    else
                                        FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelect(provider)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModelDropdown(
    models: List<ModelOption>,
    selected: ModelOption?,
    onSelect: (ModelOption) -> Unit,
    showProviderLabel: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    // Some providers expose hundreds of models (OpenRouter ~900). The
    // previous implementation used a Compose DropdownMenu capped at 50
    // rows because DropdownMenu is NOT lazy — rendering 900 rows at once
    // would jank hard. That cap meant users couldn't see the full list
    // even when they wanted to browse it.
    //
    // Now we open a full-screen Dialog containing a LazyColumn. Lazy
    // rendering means 9 or 9000 models cost the same per frame — only
    // visible rows compose. The dialog can be scrolled end-to-end, so
    // the user can see every model the provider exposes, with the same
    // search-as-you-type filter to actually find one in a long list.
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models
        else models.filter { it.modelId.contains(query, ignoreCase = true) }
    }

    Column {
        Text(
            text = stringResource(R.string.model),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = com.hermes.android.ui.design.settingsCardColor(),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { query = ""; expanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.modelId
                            ?: stringResource(R.string.select_model_2),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selected != null) {
                        Text(
                            text = "✓ " + stringResource(R.string.active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded)
                        Icons.Default.ExpandLess
                    else
                        Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 560.dp),
                ) {
                    // Header — title + close button.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.select_model_3),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.text, filtered.size, models.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Search box — always visible now that we don't cap rows.
                    // Helps when the provider exposes hundreds of models.
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = {
                            Text(stringResource(R.string.search_models, models.size))
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    // LazyColumn — only visible rows compose, so even 900+
                    // models scroll smoothly without the 50-row cap.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_match),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filtered,
                                key = { "${it.provider}/${it.modelId}" },
                            ) { model ->
                                val isActive = model.provider == selected?.provider &&
                                    model.modelId == selected?.modelId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(model)
                                            expanded = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.modelId,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                        )
                                        // When browsing "All providers", the same
                                        // model id can appear under more than one
                                        // provider — label it so the two rows are
                                        // distinguishable instead of looking like
                                        // duplicates.
                                        if (showProviderLabel) {
                                            Text(
                                                text = model.provider,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (isActive) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProviderCard(
    provider: HermesProviderConfig,
    credentials: List<CredentialEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onSetCredential: (String) -> Unit,
    onAddCredential: (String) -> Unit,
    onRemoveCredential: (credentialId: String) -> Unit,
    onSetPrimary: () -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSetKeyDialog by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // One card fill for every provider; the Primary chip marks the main one.
            containerColor = com.hermes.android.ui.design.settingsCardColor(),
        ),
    ) {
        Column {
            // ── Header row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (provider.isPrimary)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = provider.slug,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (provider.isPrimary) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(R.string.primary), style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(14.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        labelColor = MaterialTheme.colorScheme.onPrimary,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    border = null,
                                )
                            }
                        }
                        if (provider.baseUrl.isNotBlank()) {
                            Text(
                                text = provider.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Key configured badge
                    if (credentials.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            // ── Expanded content ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Details
                    DetailRow(stringResource(R.string.base_url), provider.baseUrl)
                    if (provider.defaultModel.isNotBlank()) {
                        DetailRow(stringResource(R.string.default_model), provider.defaultModel)
                    }

                    // ── Primary action ──
                    if (!provider.isPrimary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = onSetPrimary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Star, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.set_primary), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // API key pool — Hermes rotates between multiple keys
                    // per provider automatically (fill_first by default);
                    // see credential-pools.md. Show every entry, not just one.
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (credentials.size > 1) {
                                stringResource(R.string.api_keys, credentials.size)
                            } else {
                                stringResource(R.string.api_key)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row {
                            if (credentials.isNotEmpty()) {
                                TextButton(onClick = { showAddKeyDialog = true }) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.add_key), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            TextButton(onClick = { showSetKeyDialog = true }) {
                                Icon(Icons.Default.Key, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (credentials.isEmpty()) stringResource(R.string.set_key)
                                    else stringResource(R.string.replace_first_key),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    if (credentials.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_key_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (credentials.size > 1) {
                            Text(
                                text = stringResource(R.string.tried_in_priority_order_when_the_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        credentials.sortedBy { it.priority }.forEach { credential ->
                            CredentialRow(
                                credential = credential,
                                onRemove = { onRemoveCredential(credential.id ?: credential.index.toString()) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Add key dialog (append, doesn't disturb existing keys) ──
    if (showAddKeyDialog) {
        SetKeyDialog(
            providerSlug = provider.slug,
            onDismiss = { showAddKeyDialog = false },
            onSet = { key ->
                onAddCredential(key)
                showAddKeyDialog = false
            },
        )
    }


    // ── Delete confirmation ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_provider)) },
            text = {
                Text(
                    stringResource(R.string.this_will_remove_from_config_yaml_and, provider.slug)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Set key dialog ──
    if (showSetKeyDialog) {
        SetKeyDialog(
            providerSlug = provider.slug,
            onDismiss = { showSetKeyDialog = false },
            onSet = { key ->
                onSetCredential(key)
                showSetKeyDialog = false
            },
        )
    }
}

