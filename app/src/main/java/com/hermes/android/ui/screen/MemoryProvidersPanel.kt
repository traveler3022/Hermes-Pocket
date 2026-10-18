package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.memory.MemoryProviders.ConfigField
import com.hermes.android.domain.memory.MemoryProviders.FieldKind
import com.hermes.android.domain.memory.MemoryProviders.Provider
import com.hermes.android.domain.memory.MemoryProviders.ProviderState
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel.Busy
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel.Notice

/**
 * Under the memory files: which external memory provider the agent uses (with its
 * settings and install step), and erasing the built-in memory. [onBuiltInReset] reloads
 * the files shown above after an erase.
 */
@Composable
internal fun MemoryProvidersPanel(
    onBuiltInReset: () -> Unit,
    viewModel: MemoryProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.notice) {
        if (state.notice == Notice.Reset) onBuiltInReset()
    }
    var confirmReset by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        SettingsLabel(stringResource(R.string.external_memory))
        Text(
            stringResource(R.string.built_in_memory_is_always_on_one_extra),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val status = state.status
        when {
            state.isLoading && status == null -> CircularProgressIndicator(Modifier.size(24.dp))
            state.loadError != null && status == null -> Column {
                Text(
                    stringResource(R.string.couldnt_load) + state.loadError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = viewModel::load) { Text(stringResource(R.string.try_again)) }
            }
            status != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (status.active.isBlank()) {
                            stringResource(R.string.in_use_built_in_only)
                        } else {
                            stringResource(R.string.in_use_3, status.active)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.active.isNotBlank()) {
                        TextButton(enabled = state.busy == null, onClick = viewModel::turnOff) {
                            Text(stringResource(R.string.turn_off))
                        }
                    }
                }
                status.providers.forEach { provider ->
                    ProviderRow(provider, active = provider.name == status.active) { viewModel.edit(provider.name) }
                }
                Text(
                    stringResource(R.string.more_providers_honcho_mem0_supermemory),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Text(
                    stringResource(R.string.erase_built_in_memory),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = HxSpace.sm),
                )
                Text(
                    "MEMORY.md ${sizeText(status.memoryFileBytes)} · USER.md ${sizeText(status.userFileBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = state.busy == null && (status.memoryFileBytes > 0 || status.userFileBytes > 0),
                    onClick = { confirmReset = true },
                ) {
                    if (state.busy == Busy.RESETTING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.erase), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // The panel's own outcome line; an open editor shows its own.
        if (state.editing == null) state.notice?.let { NoticeText(it) }
    }

    if (confirmReset) {
        ResetDialog(
            onPick = { target ->
                confirmReset = false
                viewModel.resetBuiltIn(target)
            },
            onDismiss = { confirmReset = false },
        )
    }

    val editing = state.editing
    val provider = state.status?.providers?.firstOrNull { it.name == editing }
    if (editing != null) {
        ProviderEditor(
            name = editing,
            provider = provider,
            state = state,
            onValue = viewModel::setValue,
            onInstall = viewModel::install,
            onSave = viewModel::saveAndActivate,
            onDismiss = { viewModel.edit(null) },
        )
    }
}

@Composable
private fun ProviderRow(provider: Provider, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(HxSpace.inner)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Text(provider.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (active) StatusChip(label = stringResource(R.string.in_use_4), color = MaterialTheme.colorScheme.primary)
                val (label, ok) = when {
                    !provider.dependenciesInstalled -> stringResource(R.string.needs_install) to false
                    provider.state == ProviderState.READY -> stringResource(R.string.ready_2) to true
                    provider.state == ProviderState.NEEDS_CONFIG -> stringResource(R.string.needs_settings) to false
                    provider.state == ProviderState.MISSING -> stringResource(R.string.not_found) to false
                    else -> stringResource(R.string.unavailable) to false
                }
                StatusChip(
                    label = label,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (provider.description.isNotBlank()) {
                Text(
                    provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    name: String,
    provider: Provider?,
    state: MemoryProvidersViewModel.UiState,
    onValue: (String, String) -> Unit,
    onInstall: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state.busy != null
    val visible = state.fields.filter { field -> field.showWhen.all { (k, v) -> state.values[k].orEmpty() == v } }
    val missingRequired = visible.any { field ->
        field.required && state.values[field.key].isNullOrBlank() && !(field.kind == FieldKind.SECRET && field.isSet)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                if (provider != null && !provider.dependenciesInstalled) {
                    Text(
                        stringResource(R.string.it_needs_packages_installed_on_the),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    provider.externalTools.forEach { command ->
                        Text(
                            command,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(enabled = !busy, onClick = onInstall) {
                        if (state.busy == Busy.INSTALLING) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.installing_can_take_minutes))
                        } else {
                            Text(stringResource(R.string.install))
                        }
                    }
                }
                state.install?.let { result ->
                    Text(
                        if (result.ok) stringResource(R.string.install_finished) else stringResource(R.string.install_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    result.steps.filter { !result.ok || it.status == "failed" }.forEach { step ->
                        Text(
                            "${step.status}: ${step.name}" + if (step.detail.isNotBlank()) "\n${step.detail}" else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    state.isLoadingFields -> CircularProgressIndicator(Modifier.size(24.dp))
                    visible.isEmpty() -> Text(
                        stringResource(R.string.nothing_to_set_for_this_one),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> visible.forEach { field ->
                        ConfigFieldInput(field, state.values[field.key].orEmpty(), enabled = !busy) { onValue(field.key, it) }
                    }
                }
                state.notice?.let { NoticeText(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !state.isLoadingFields && !missingRequired, onClick = onSave) {
                if (state.busy == Busy.SAVING) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save_and_use))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ConfigFieldInput(field: ConfigField, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val label = field.label + if (field.required) " *" else ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (field.kind) {
            FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = value.equals("true", ignoreCase = true),
                    enabled = enabled,
                    onCheckedChange = { onChange(it.toString()) },
                )
            }
            FieldKind.SELECT -> {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = value == option.value,
                            enabled = enabled,
                            onClick = { onChange(option.value) },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            else -> OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                label = { Text(label) },
                placeholder = {
                    Text(
                        if (field.kind == FieldKind.SECRET && field.isSet) {
                            stringResource(R.string.saved_leave_empty_to_keep_it)
                        } else {
                            field.placeholder
                        },
                    )
                },
                visualTransformation = if (field.kind == FieldKind.SECRET) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.kind) {
                        FieldKind.INTEGER -> KeyboardType.Number
                        FieldKind.NUMBER -> KeyboardType.Decimal
                        FieldKind.SECRET -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val help = listOf(field.description, field.url).filter { it.isNotBlank() }.joinToString("\n")
        if (help.isNotBlank()) {
            Text(help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ResetDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.erase_built_in_memory_2)) },
        text = {
            Text(
                stringResource(R.string.what_the_agent_remembered_there_is),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onPick("memory") }) { Text(stringResource(R.string.only_memory_md)) }
                TextButton(onClick = { onPick("user") }) { Text(stringResource(R.string.only_user_md)) }
                TextButton(onClick = { onPick("all") }) {
                    Text(stringResource(R.string.both), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_2)) }
            }
        },
    )
}

@Composable
private fun NoticeText(notice: Notice) {
    val (text, failed) = when (notice) {
        Notice.Saved -> stringResource(R.string.saved_and_turned_on) to false
        Notice.TurnedOff -> stringResource(R.string.turned_off_built_in_memory_only) to false
        Notice.Reset -> stringResource(R.string.erased) to false
        is Notice.Failed -> notice.reason to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> stringResource(R.string.empty_2)
    bytes < 1024 -> stringResource(R.string.b, bytes)
    else -> stringResource(R.string.kb, bytes / 1024)
}
