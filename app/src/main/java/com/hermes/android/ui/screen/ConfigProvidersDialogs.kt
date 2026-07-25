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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption
import kotlinx.coroutines.launch

private enum class AddProviderStep { CONNECT, LOADING, PICK, MANUAL }

@Composable
internal fun AddProviderDialog(
    onDismiss: () -> Unit,
    onFetchModels: suspend (baseUrl: String, apiKey: String) -> ConfigViewModel.ProviderProbe,
    onAdd: (slug: String, baseUrl: String, defaultModel: String, apiKey: String) -> Unit,
) {
    var slug by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    var step by remember { mutableStateOf(AddProviderStep.CONNECT) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var manualModel by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun fetch() {
        step = AddProviderStep.LOADING
        scope.launch {
            val probe = onFetchModels(baseUrl, apiKey)
            if (probe.models.isNotEmpty()) {
                models = probe.models
                selectedModel = probe.models.first()
                step = AddProviderStep.PICK
            } else {
                probeError = probe.error
                step = AddProviderStep.MANUAL
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    AddProviderStep.CONNECT -> t("Add Provider", "افزودن پرووایدر")
                    AddProviderStep.LOADING -> t("Connecting…", "در حال اتصال…")
                    AddProviderStep.PICK -> t("Choose a Model", "انتخاب مدل")
                    AddProviderStep.MANUAL -> t("Model Not Auto-Detected", "مدل خودکار پیدا نشد")
                },
            )
        },
        text = {
            when (step) {
                AddProviderStep.CONNECT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(t("API Key", "کلید API")) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Security, null) },
                    )
                    Text(
                        t(
                            "Next, we'll ask this endpoint for its real model list — same as connecting from Hermes itself.",
                            "بعدش لیست واقعی مدل‌های این سرور رو می‌گیریم — دقیقاً مثل اتصال از خود Hermes.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AddProviderStep.LOADING -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("Fetching model list from the endpoint…", "در حال گرفتن لیست مدل از سرور…"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AddProviderStep.PICK -> Column(
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    Text(
                        t("${models.size} models found at this endpoint:", "${models.size} مدل در این سرور پیدا شد:"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(models) { m ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedModel = m }
                                    .padding(vertical = 6.dp),
                            ) {
                                RadioButton(
                                    selected = selectedModel == m,
                                    onClick = { selectedModel = m },
                                )
                                Text(
                                    m,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { step = AddProviderStep.MANUAL }) {
                        Text(t("My model isn't listed — type it manually", "مدلم لیست نیست — دستی وارد می‌کنم"))
                    }
                }
                AddProviderStep.MANUAL -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!probeError.isNullOrBlank()) {
                        Text(
                            t("Couldn't fetch models: $probeError", "گرفتن لیست مدل ناموفق بود: $probeError"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = manualModel,
                        onValueChange = { manualModel = it },
                        label = { Text(t("Model Name", "نام مدل")) },
                        placeholder = { Text("gpt-4o, claude-sonnet-4-20250514...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Psychology, null) },
                    )
                    Text(
                        t(
                            "Warning: this name isn't verified against the endpoint — a typo will fail to connect.",
                            "توجه: این اسم با سرور چک نشده — اگه اشتباه تایپ بشه، اتصال شکست می‌خوره.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                AddProviderStep.CONNECT -> TextButton(
                    onClick = { fetch() },
                    enabled = slug.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                ) { Text(t("Fetch Models", "گرفتن لیست مدل")) }
                AddProviderStep.LOADING -> {}
                AddProviderStep.PICK -> TextButton(
                    onClick = { onAdd(slug, baseUrl, selectedModel.orEmpty(), apiKey) },
                    enabled = !selectedModel.isNullOrBlank(),
                ) { Text(t("Add", "افزودن")) }
                AddProviderStep.MANUAL -> TextButton(
                    onClick = { onAdd(slug, baseUrl, manualModel, apiKey) },
                    enabled = manualModel.isNotBlank(),
                ) { Text(t("Add Anyway", "افزودن به هر حال")) }
            }
        },
        dismissButton = {
            when (step) {
                AddProviderStep.CONNECT -> TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
                AddProviderStep.LOADING -> TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
                AddProviderStep.PICK, AddProviderStep.MANUAL -> TextButton(onClick = { step = AddProviderStep.CONNECT }) {
                    Text(t("Back", "بازگشت"))
                }
            }
        },
    )
}

@Composable
internal fun SetKeyDialog(
    providerSlug: String,
    onDismiss: () -> Unit,
    onSet: (key: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("API Key for $providerSlug", "کلید API برای $providerSlug")) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(t("API Key", "کلید API")) },
                placeholder = { Text("sk-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Security, null) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(key) },
                enabled = key.isNotBlank(),
            ) { Text(t("Save", "ذخیره")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
        },
    )
}

@Composable
internal fun CredentialRow(
    credential: CredentialEntry,
    onRemove: () -> Unit,
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

