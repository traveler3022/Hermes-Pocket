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
import com.hermes.android.ui.viewmodel.ConfigTab
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption

// Fix: lost during the ChatScreen/ConfigScreen file-split extraction —
// EndpointCard uses this but it was never carried over to any of the
// new files. Restored from the pre-split ConfigScreen.kt.
private val knownEndpoints = mapOf(
    "openrouter" to "https://openrouter.ai/api/v1",
    "anthropic" to "https://api.anthropic.com",
    "openai" to "https://api.openai.com/v1",
    "google" to "https://generativelanguage.googleapis.com",
    "mistral" to "https://api.mistral.ai/v1",
    "groq" to "https://api.groq.com/openai/v1",
    "deepseek" to "https://api.deepseek.com",
    "together" to "https://api.together.xyz/v1",
    "fireworks" to "https://api.fireworks.ai/inference/v1",
    "cohere" to "https://api.cohere.ai/v1",
    "replicate" to "https://api.replicate.com/v1",
    "perplexity" to "https://api.perplexity.ai",
    "xai" to "https://api.x.ai/v1",
    "ollama" to "http://localhost:11434",
    "lmstudio" to "http://localhost:1234/v1",
)

@Composable
internal fun ProviderDropdown(
    providers: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("API Provider", "پرووایدر API"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                        text = selected ?: t("Select provider...", "پرووایدر رو انتخاب کن..."),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = t("Model", "مدل"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected?.modelId
                                ?: t("Select model...", "مدل رو انتخاب کن..."),
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
                                text = "✓ " + t("Active", "فعال"),
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
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    models.forEach { model ->
                        val isActive = model.provider == selected?.provider &&
                            model.modelId == selected?.modelId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = model.modelId,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isActive) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelect(model)
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
internal fun EndpointCard(provider: String) {
    val endpoint = remember(provider) {
        knownEndpoints[provider.lowercase()] ?: provider
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ApiKeyRow(
    provider: String,
    onSaveKey: (String, String) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(t("API Key", "کلید API")) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { onSaveKey(provider, apiKey) }) {
                Text(t("Save", "ذخیره"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderCard(
    provider: HermesProviderConfig,
    credentials: List<CredentialEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onAddCredential: (String, String?) -> Unit,
    onRemoveCredential: (Int) -> Unit,
    onSetStrategy: (String) -> Unit,
    onSetPrimary: () -> Unit = {},
    onToggleFallback: () -> Unit = {},
    onMoveFallback: (Boolean) -> Unit = {},
    onMoveCredential: (Int, Boolean) -> Unit = { _, _ -> },
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
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
                            MaterialTheme.colorScheme.onPrimaryContainer
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
                                    label = { Text(t("Primary", "اصلی"), style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(14.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        labelColor = MaterialTheme.colorScheme.onPrimary,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    border = null,
                                )
                            }
                            if (provider.isFallback) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(t("Fallback", "جایگزین"), style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        labelColor = MaterialTheme.colorScheme.onTertiary,
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
                    // Credential count badge
                    if (credentials.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${credentials.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = t("Delete", "حذف"),
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
                    DetailRow(t("Base URL", "آدرس"), provider.baseUrl)
                    if (provider.defaultModel.isNotBlank()) {
                        DetailRow(t("Default Model", "مدل پیشفرض"), provider.defaultModel)
                    }

                    // ── Primary / Fallback actions ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!provider.isPrimary) {
                            FilledTonalButton(
                                onClick = onSetPrimary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Star, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("Set primary", "کلید اصلی"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        FilterChip(
                            selected = provider.isFallback,
                            onClick = onToggleFallback,
                            label = {
                                Text(
                                    t("In fallback chain", "در زنجیره جایگزین"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        if (provider.isFallback) {
                            IconButton(onClick = { onMoveFallback(true) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, t("Up", "بالا"), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveFallback(false) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, t("Down", "پایین"), Modifier.size(18.dp))
                            }
                        }
                    }

                    // Strategy selector — the four strategies Hermes actually
                    // supports for a provider's credential pool.
                    Text(
                        text = t("Key rotation strategy", "استراتژی چرخش کلید"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val strategies = listOf(
                        "round_robin" to t("Round-robin", "چرخشی"),
                        "fill_first" to t("Fill first", "ترتیبی"),
                        "least_used" to t("Least used", "کم‌مصرف‌ترین"),
                        "random" to t("Random", "تصادفی"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        strategies.forEach { (value, label) ->
                            FilterChip(
                                selected = provider.strategy == value,
                                onClick = { onSetStrategy(value) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (provider.strategy == value) {
                                    { Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp)) }
                                } else null,
                            )
                        }
                    }

                    // Credential pool
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t("Keys (${credentials.size})", "کلیدها (${credentials.size})"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { showAddKeyDialog = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Add Key", "افزودن کلید"), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (credentials.isEmpty()) {
                        Text(
                            text = t("No keys configured", "کلیدی تنظیم نشده"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        credentials.forEachIndexed { pos, cred ->
                            CredentialRow(
                                credential = cred,
                                canMoveUp = pos > 0,
                                canMoveDown = pos < credentials.size - 1,
                                onRemove = { onRemoveCredential(cred.index) },
                                onMove = { up -> onMoveCredential(cred.index, up) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(t("Delete Provider?", "پرووایدر حذف شود؟")) },
            text = {
                Text(
                    t(
                        "This will remove \"${provider.slug}\" from config.yaml and credential pool.",
                        "\"${provider.slug}\" از config.yaml و credential pool حذف میشه.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showDeleteConfirm = false
                }) {
                    Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(t("Cancel", "لغو"))
                }
            },
        )
    }

    // ── Add key dialog ──
    if (showAddKeyDialog) {
        AddKeyDialog(
            providerSlug = provider.slug,
            onDismiss = { showAddKeyDialog = false },
            onAdd = { key, label ->
                onAddCredential(key, label)
                showAddKeyDialog = false
            },
        )
    }
}

@Composable
internal fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (slug: String, baseUrl: String, defaultModel: String, apiKey: String) -> Unit,
) {
    var slug by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add Provider", "افزودن پرووایدر")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().replace(" ", "_") },
                    label = { Text(t("Provider Name (slug)", "نام پرووایدر")) },
                    placeholder = { Text("openai, anthropic, xai...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(t("Base URL", "آدرس سرور")) },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text(t("Default Model", "مدل پیشفرض")) },
                    placeholder = { Text("gpt-4o, claude-sonnet-4-20250514...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Psychology, null) },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(t("API Key", "کلید API")) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Security, null) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(slug, baseUrl, defaultModel, apiKey) },
                enabled = slug.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text(t("Add", "افزودن"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
        },
    )
}

@Composable
internal fun AddKeyDialog(
    providerSlug: String,
    onDismiss: () -> Unit,
    onAdd: (key: String, label: String?) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add Key to $providerSlug", "افزودن کلید به $providerSlug")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(t("API Key", "کلید API")) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Security, null) },
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(t("Label (optional)", "برچسب (اختیاری)")) },
                    placeholder = { Text("primary, backup, team...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(key, label.ifBlank { null }) },
                enabled = key.isNotBlank(),
            ) { Text(t("Add", "افزودن")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
        },
    )
}

@Composable
internal fun CredentialRow(
    credential: CredentialEntry,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onRemove: () -> Unit,
    onMove: (Boolean) -> Unit = {},
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = when (credential.lastStatus) {
                    "ok" -> MaterialTheme.colorScheme.primary
                    "fail" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column {
                Text(
                    text = credential.label ?: "key #${credential.index}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = credential.tokenPreview,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (credential.requestCount > 0) {
                Text(
                    text = "${credential.requestCount} req",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            // Priority order (fill_first uses this order; top = tried first)
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, t("Higher priority", "اولویت بالاتر"), Modifier.size(16.dp))
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, t("Lower priority", "اولویت پایین‌تر"), Modifier.size(16.dp))
            }
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("Remove", "حذف"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(t("Remove Key?", "کلید حذف شود؟")) },
            text = { Text(t("This key will be removed from the credential pool.", "این کلید از credential pool حذف میشه.")) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showConfirm = false
                }) { Text(t("Remove", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(t("Cancel", "لغو")) }
            },
        )
    }
}

