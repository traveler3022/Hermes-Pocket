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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.hermes.android.ui.i18n.AppLanguage
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

@Composable
internal fun ModelsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    // Load providers on first composition
    val providersLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!providersLoaded.value) {
            viewModel.loadProviders()
            providersLoaded.value = true
        }
    }

    var showAddProviderDialog by remember { mutableStateOf(false) }

    if (state.isLoadingModels && state.isLoadingProviders) {
        LoadingIndicator(t("Loading models...", "در حال بارگذاری مدلها..."))
        return
    }

    val grouped = remember(state.availableModels) {
        state.availableModels.groupBy { it.provider }
    }
    val modelProviders = remember(grouped) { grouped.keys.sorted() }

    // Track selected provider — default to activeProvider or first available
    var selectedProvider by remember(modelProviders, state.activeProvider) {
        mutableStateOf(
            state.activeProvider?.takeIf { it in modelProviders } ?: modelProviders.firstOrNull()
        )
    }

    // null selectedProvider means "All providers" — show every model from
    // every configured provider instead of forcing one to be picked first.
    val filteredModels = remember(selectedProvider, grouped, state.availableModels) {
        if (selectedProvider == null) state.availableModels else grouped[selectedProvider].orEmpty()
    }

    // One search box over EVERY model from EVERY provider (approved design
    // F): typing switches the picker into a flat cross-provider result list.
    var modelSearch by remember { mutableStateOf("") }
    val searchResults = remember(modelSearch, state.availableModels) {
        if (modelSearch.isBlank()) emptyList()
        else state.availableModels.filter { model ->
            model.modelId.contains(modelSearch, ignoreCase = true) ||
                model.name.contains(modelSearch, ignoreCase = true) ||
                model.provider.contains(modelSearch, ignoreCase = true)
        }.take(30)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ══════════════════════════════════════════════════════════════
        // ── Active model hero (design F) ──
        // ══════════════════════════════════════════════════════════════
        item(key = "__active_model_hero") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = t("Active model", "مدل فعال"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (state.activeModel != null) {
                            "${state.activeProvider ?: "?"} / ${state.activeModel}"
                        } else {
                            t("No model selected", "مدلی انتخاب نشده")
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // ── Cross-provider model search ──
        item(key = "__model_search") {
            OutlinedTextField(
                value = modelSearch,
                onValueChange = { modelSearch = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(t("Search all models…", "جستجو در همهٔ مدل‌ها…"))
                },
                singleLine = true,
                trailingIcon = {
                    if (modelSearch.isNotEmpty()) {
                        TextButton(onClick = { modelSearch = "" }) {
                            Text(t("Clear", "پاک کردن"))
                        }
                    }
                },
            )
        }

        if (modelSearch.isNotBlank()) {
            if (searchResults.isEmpty()) {
                item(key = "__search_empty") {
                    Text(
                        text = t("No matching models", "مدلی پیدا نشد"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                items(
                    items = searchResults,
                    key = { "search:${it.provider}/${it.modelId}" },
                ) { model ->
                    val isActive = model.provider == state.activeProvider &&
                        model.modelId == state.activeModel
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectModel(model)
                                modelSearch = ""
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.modelId,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                                Text(
                                    text = model.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isActive) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // ── Provider Management Section ──
        // ══════════════════════════════════════════════════════════════
        item(key = "__provider_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("API Providers", "پرووایدرهای API"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.loadProviders() }) {
                        Text(t("Refresh", "بارگذاری مجدد"))
                    }
                    FilledTonalButton(onClick = { showAddProviderDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Add", "افزودن"))
                    }
                }
            }
        }

        if (state.isLoadingProviders) {
            item(key = "__providers_loading") {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (state.providers.isEmpty()) {
            item(key = "__providers_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = t(
                            "No providers configured yet.",
                            "هنوز پرووایدری تنظیم نشده.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // ── Provider cards ──
            items(
                items = state.providers,
                key = { it.slug },
            ) { provider ->
                ProviderCard(
                    provider = provider,
                    credentials = state.credentialPool[provider.slug].orEmpty(),
                    isExpanded = state.expandedProviderSlug == provider.slug,
                    onToggleExpand = { viewModel.toggleProviderExpanded(provider.slug) },
                    onRemove = { viewModel.removeProvider(provider.slug) },
                    onSetCredential = { key -> viewModel.setCredential(provider.slug, key) },
                    onRemoveCredential = { viewModel.removeCredential(provider.slug) },
                    onSetPrimary = { viewModel.setPrimaryProvider(provider) },
                )
            }
        }

        // ══════════════════════════════════════════════════════════════
        // ── Model Selection Section ──
        // ══════════════════════════════════════════════════════════════
        item(key = "__model_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("Select Model", "انتخاب مدل"),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { viewModel.loadModels() }) {
                    Text(t("Refresh", "بارگذاری مجدد"))
                }
            }
        }

        if (grouped.isEmpty()) {
            item(key = "__empty_models") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = t(
                            "No models loaded. Make sure Hermes gateway is running, then tap Refresh.",
                            "مدلی بارگذاری نشد. مطمئن شو gateway هرمس روشنه، بعد بزن بارگذاری مجدد.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        // ── API Provider dropdown ──
        item(key = "__provider_dropdown") {
            ProviderDropdown(
                providers = modelProviders,
                selected = selectedProvider,
                onSelect = { selectedProvider = it },
            )
        }

        // ── Model dropdown (filtered by provider) ──
        item(key = "__model_dropdown") {
            ModelDropdown(
                models = filteredModels,
                selected = filteredModels.firstOrNull {
                    it.provider == state.activeProvider && it.modelId == state.activeModel
                },
                onSelect = { viewModel.selectModel(it) },
                showProviderLabel = selectedProvider == null,
            )
        }

        // ── Endpoint info ──
        selectedProvider?.let { provider ->
            item(key = "__endpoint") {
                EndpointCard(provider = provider)
            }
        }

        // ── API Key for this provider ──
        selectedProvider?.let { provider ->
            val needsKey = filteredModels.any { it.requiresApiKey }
            if (needsKey) {
                item(key = "__apikey") {
                    ApiKeyRow(
                        provider = provider,
                        onSaveKey = { slug, key -> viewModel.saveApiKey(slug, key) },
                    )
                }
            }
        }
    }

    // ── Add Provider Dialog ──
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onAdd = { slug, baseUrl, model, key ->
                viewModel.addProvider(slug, baseUrl, model, key)
                showAddProviderDialog = false
            },
        )
    }

}

