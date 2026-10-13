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
                    AddProviderStep.CONNECT -> stringResource(R.string.add_provider)
                    AddProviderStep.LOADING -> stringResource(R.string.connecting_2)
                    AddProviderStep.PICK -> stringResource(R.string.choose_a_model)
                    AddProviderStep.MANUAL -> stringResource(R.string.model_not_auto_detected)
                },
            )
        },
        text = {
            when (step) {
                AddProviderStep.CONNECT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = slug,
                        onValueChange = { slug = it.lowercase().replace(" ", "_") },
                        label = { Text(stringResource(R.string.provider_name_slug)) },
                        placeholder = { Text("openai, anthropic, xai...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.base_url_2)) },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.api_key)) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Security, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text(
                        stringResource(R.string.next_well_ask_this_endpoint_for_its_real),
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
                        stringResource(R.string.fetching_model_list_from_the_endpoint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AddProviderStep.PICK -> Column(
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    Text(
                        stringResource(R.string.models_found_at_this_endpoint, models.size),
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
                        Text(stringResource(R.string.my_model_isnt_listed_type_it_manually))
                    }
                }
                AddProviderStep.MANUAL -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!probeError.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.couldnt_fetch_models, probeError.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = manualModel,
                        onValueChange = { manualModel = it },
                        label = { Text(stringResource(R.string.model_name)) },
                        placeholder = { Text("gpt-4o, claude-sonnet-4-20250514...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Psychology, null) },
                    )
                    Text(
                        stringResource(R.string.warning_this_name_isnt_verified_against),
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
                ) { Text(stringResource(R.string.fetch_models)) }
                AddProviderStep.LOADING -> {}
                AddProviderStep.PICK -> TextButton(
                    onClick = { onAdd(slug, baseUrl, selectedModel.orEmpty(), apiKey) },
                    enabled = !selectedModel.isNullOrBlank(),
                ) { Text(stringResource(R.string.add)) }
                AddProviderStep.MANUAL -> TextButton(
                    onClick = { onAdd(slug, baseUrl, manualModel, apiKey) },
                    enabled = manualModel.isNotBlank(),
                ) { Text(stringResource(R.string.add_anyway)) }
            }
        },
        dismissButton = {
            when (step) {
                AddProviderStep.CONNECT -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                AddProviderStep.LOADING -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                AddProviderStep.PICK, AddProviderStep.MANUAL -> TextButton(onClick = { step = AddProviderStep.CONNECT }) {
                    Text(stringResource(R.string.back))
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
        title = { Text(stringResource(R.string.api_key_for, providerSlug)) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text("sk-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Security, null) },
                // Masked, and a password keyboard so the key isn't learned or suggested later.
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(key) },
                enabled = key.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.remove_key)) },
            text = { Text(stringResource(R.string.this_key_will_be_removed_from_the)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showConfirm = false
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

