package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.config.EnvKey
import com.hermes.android.ui.icons.filled.DeleteOutline
import com.hermes.android.ui.icons.filled.Visibility
import com.hermes.android.ui.icons.filled.VisibilityOff
import com.hermes.android.ui.viewmodel.EnvKeysViewModel

/** Settings › Advanced › `.env` keys: each key with its masked value; show, edit, add, remove. */
@Composable
internal fun EnvKeysSection(snackbarHostState: SnackbarHostState) {
    val viewModel: EnvKeysViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message ?: return@LaunchedEffect
        viewModel.clearMessages()
        snackbarHostState.showSnackbar(text)
    }

    // editing: the key getting a new value; adding: a key not in the list yet.
    var editing by remember { mutableStateOf<EnvKey?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }

    if (adding || editing != null) {
        EnvKeyDialog(
            key = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { key, value ->
                viewModel.save(key, value)
                adding = false
                editing = null
            },
        )
    }
    removing?.let { key ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.env_key_remove_title, key)) },
            text = { Text(stringResource(R.string.env_key_remove_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(key); removing = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.env_keys_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilterChip(
                        selected = state.showUnset,
                        onClick = { viewModel.setShowUnset(!state.showUnset) },
                        label = { Text(stringResource(R.string.env_keys_show_unset)) },
                    )
                    FilledTonalButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.env_key_add), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (state.isLoading && state.keys.isEmpty()) {
            item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
        } else if (state.visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.env_keys_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.visible, key = { it.key }) { key ->
            EnvKeyRow(
                key = key,
                revealed = state.revealed[key.key],
                busy = state.busy == key.key,
                onReveal = { viewModel.reveal(key.key) },
                onEdit = { editing = key },
                onRemove = { removing = key.key },
            )
        }
    }
}

@Composable
private fun EnvKeyRow(
    key: EnvKey,
    revealed: String?,
    busy: Boolean,
    onReveal: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(key.key, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                if (key.description.isNotBlank()) {
                    Text(
                        key.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    text = when {
                        revealed != null -> revealed
                        key.isSet -> key.preview ?: "••••"
                        else -> stringResource(R.string.env_key_not_set)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (key.isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else if (key.isSet) {
                IconButton(onClick = onReveal) {
                    Icon(
                        if (revealed != null) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(if (revealed != null) R.string.env_key_hide else R.string.env_key_show),
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            if (key.isSet) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

/** Adds a key ([key] null) or gives an existing one a new value. The old value is never prefilled. */
@Composable
private fun EnvKeyDialog(key: EnvKey?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(key?.key.orEmpty()) }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (key == null) stringResource(R.string.env_key_add) else key.key) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (key == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.uppercase().replace(' ', '_') },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.env_key_name)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                } else if (key.description.isNotBlank()) {
                    Text(key.description, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.env_key_value)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, value) }, enabled = name.isNotBlank() && value.isNotEmpty()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
