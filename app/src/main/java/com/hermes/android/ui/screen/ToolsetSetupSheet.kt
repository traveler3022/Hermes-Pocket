package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.tools.ToolsetEnvVar
import com.hermes.android.domain.tools.ToolsetProvider
import com.hermes.android.ui.viewmodel.ToolsetSetupUiState
import com.hermes.android.ui.viewmodel.ToolsetSetupViewModel

/**
 * Which service a toolset uses (e.g. which web search) and that service's keys. The layout
 * follows Hy4ri/hermes-mobile's toolset page: one card per backend, the active one ticked,
 * its keys under it; a key is never shown back, only whether it is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsetSetupSheet(
    toolset: String,
    onDismiss: () -> Unit,
    viewModel: ToolsetSetupViewModel = hiltViewModel(),
) {
    LaunchedEffect(toolset) { viewModel.open(toolset) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = { viewModel.clearMessages(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.set_up, toolset),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.choose_the_service_these_tools_use_and),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            (state.error ?: state.message)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            ToolsetSetupBody(state, viewModel)
        }
    }
}

@Composable
private fun ToolsetSetupBody(state: ToolsetSetupUiState, viewModel: ToolsetSetupViewModel) {
    val config = state.config
    if (config == null) {
        if (state.isLoading) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    if (config.providers.isEmpty()) {
        Text(
            text = stringResource(R.string.this_toolset_has_no_services_to_choose),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        items(config.providers, key = { it.name }) { provider ->
            ProviderCard(provider, state.busy, viewModel)
        }
    }
}

@Composable
private fun ProviderCard(provider: ToolsetProvider, busy: String?, viewModel: ToolsetSetupViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        onClick = { if (!provider.isActive) viewModel.selectProvider(provider.name) },
        enabled = busy == null,
    ) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy == provider.name) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    RadioButton(
                        selected = provider.isActive,
                        onClick = { if (!provider.isActive) viewModel.selectProvider(provider.name) },
                        enabled = busy == null,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                    listOfNotNull(provider.badge, statusLabel(provider)).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = it.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            provider.tag?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            provider.envVars.forEach { env ->
                EnvVarField(env, busy, viewModel)
            }
        }
    }
}

@Composable
private fun statusLabel(provider: ToolsetProvider): String? = when (provider.status) {
    "ready" -> stringResource(R.string.ready_2)
    "needs_keys" -> stringResource(R.string.needs_a_key)
    "needs_auth" -> stringResource(R.string.needs_sign_in)
    null -> null
    else -> provider.status
}

@Composable
private fun EnvVarField(env: ToolsetEnvVar, busy: String?, viewModel: ToolsetSetupViewModel) {
    var value by remember(env.key, env.isSet) { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.padding(start = 12.dp, top = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(env.key) },
            placeholder = {
                Text(if (env.isSet) stringResource(R.string.set_type_to_replace) else env.prompt.orEmpty())
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = busy == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            env.url?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringResource(R.string.get_a_key)) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (env.isSet) {
                    TextButton(onClick = { viewModel.clearKey(env.key) }, enabled = busy == null) {
                        Text(stringResource(R.string.remove_3), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { viewModel.saveKey(env.key, value) }, enabled = busy == null && value.isNotBlank()) {
                    Text(if (busy == env.key) stringResource(R.string.saving) else stringResource(R.string.save))
                }
            }
        }
    }
}
