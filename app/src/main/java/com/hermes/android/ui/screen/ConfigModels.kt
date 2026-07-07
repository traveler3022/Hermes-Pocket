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
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import androidx.compose.material.icons.filled.SwapHoriz
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
@OptIn(ExperimentalLayoutApi::class)
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

    val filteredModels = remember(selectedProvider, grouped) {
        grouped[selectedProvider].orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Quick model switch (manual input) ──
        item(key = "__quick_model_switch") {
            QuickModelSwitch(state, viewModel)
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
                    TextButton(onClick = { viewModel.loadCredits() }) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(t("Credits", "اعتبار"))
                    }
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

        // ── One-tap auto-failover: chain every provider so Hermes switches
        //    automatically when one fails (error / quota / billing). ──
        item(key = "__auto_failover") {
            OutlinedButton(
                onClick = { viewModel.enableAutoFailoverAllProviders() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.fallbackProviders.isEmpty()) {
                        t("Auto-switch between all providers", "جابه‌جایی خودکار بین همه پرووایدرها")
                    } else {
                        t(
                            "Auto-switch on: ${state.fallbackProviders.size} providers",
                            "خودکار روی ${state.fallbackProviders.size} پرووایدر",
                        )
                    }
                )
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
            // ── Fallback chain visualization ──
            if (state.fallbackProviders.isNotEmpty()) {
                item(key = "__fallback_chain") {
                    FallbackChainBar(state.fallbackProviders)
                }
            }

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
                    onAddCredential = { key, label -> viewModel.addCredential(provider.slug, key, label) },
                    onRemoveCredential = { index -> viewModel.removeCredential(provider.slug, index) },
                    onSetStrategy = { strategy -> viewModel.setProviderStrategy(provider.slug, strategy) },
                    onSetPrimary = { viewModel.setPrimaryProvider(provider) },
                    onToggleFallback = { viewModel.toggleFallback(provider.slug) },
                    onMoveFallback = { up -> viewModel.moveFallback(provider.slug, up) },
                    onMoveCredential = { index, up -> viewModel.moveCredential(provider.slug, index, up) },
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

    // ── Credits dialog ──
    if (state.creditsText != null || state.isLoadingCredits) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCredits() },
            title = { Text(t("Credits", "اعتبار")) },
            text = {
                if (state.isLoadingCredits) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(state.creditsText.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCredits() }) { Text(t("Close", "بستن")) }
            },
        )
    }
}

@Composable
internal fun QuickModelSwitch(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    var customProvider by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Quick Model Switch", "تغییر سریع مدل"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = t(
                    "Current: ${state.activeProvider ?: "?"} / ${state.activeModel ?: "?"}",
                    "فعلی: ${state.activeProvider ?: "?"} / ${state.activeModel ?: "?"}",
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            OutlinedTextField(
                value = customProvider,
                onValueChange = { customProvider = it },
                label = { Text(t("Provider (e.g. xiaomi, gemini, openai)", "پرووایدر")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = customModel,
                onValueChange = { customModel = it },
                label = { Text(t("Model ID (e.g. mimo-v2.5-free)", "شناسه مدل")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (customProvider.isNotBlank() && customModel.isNotBlank()) {
                        viewModel.selectModel(
                            ModelOption(
                                provider = customProvider.trim(),
                                modelId = customModel.trim(),
                                name = customModel.trim(),
                                requiresApiKey = false,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = customProvider.isNotBlank() && customModel.isNotBlank(),
            ) {
                Text(t("Switch Model", "تغییر مدل"))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.disconnectModel() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Disconnect Model", "قطع اتصال مدل"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FallbackChainBar(fallbackProviders: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = t("Fallback Chain", "زنجیره جایگزین"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                fallbackProviders.forEachIndexed { index, slug ->
                    if (index > 0) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(slug, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
    }
}

