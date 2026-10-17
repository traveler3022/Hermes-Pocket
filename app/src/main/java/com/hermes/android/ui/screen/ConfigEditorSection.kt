package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.config.ConfigField
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.viewmodel.ConfigEditorUiState
import com.hermes.android.ui.viewmodel.ConfigEditorViewModel

/** Settings › Advanced › config.yaml: the server's settings as a form, or the raw file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigEditorSection(snackbarHostState: SnackbarHostState) {
    val viewModel: ConfigEditorViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message ?: return@LaunchedEffect
        viewModel.clearMessages()
        snackbarHostState.showSnackbar(text)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            val modes = listOf(false to stringResource(R.string.config_mode_form), true to stringResource(R.string.config_mode_raw))
            modes.forEachIndexed { index, (raw, label) ->
                SegmentedButton(
                    selected = state.raw == raw,
                    onClick = { viewModel.setRaw(raw) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) { Text(label, maxLines = 1) }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.raw -> RawConfigEditor(state, viewModel)
                else -> ConfigFormEditor(state, viewModel)
            }
        }
    }
}

@Composable
private fun RawConfigEditor(state: ConfigEditorUiState, viewModel: ConfigEditorViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.rawConfig?.path?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            text = stringResource(R.string.config_raw_warning),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = state.rawDraft,
            onValueChange = viewModel::setRawDraft,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::load, modifier = Modifier.weight(1f), enabled = !state.isSaving) {
                Text(stringResource(R.string.reload))
            }
            Button(onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = state.rawChanged && !state.isSaving) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun ConfigFormEditor(state: ConfigEditorUiState, viewModel: ConfigEditorViewModel) {
    val form = state.form ?: return
    var open by remember { mutableStateOf(setOf<String>()) }
    val query = state.query.trim()
    val fields = if (query.isEmpty()) form.fields else form.fields.filter {
        it.key.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }
    // The server's tab order first, then the rest alphabetically, as the dashboard does.
    val groups = fields.groupBy { it.category }.toList().sortedWith(
        compareBy<Pair<String, List<ConfigField>>> { (category, _) ->
            form.categoryOrder.indexOf(category).let { if (it < 0) Int.MAX_VALUE else it }
        }.thenBy { it.first },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                )
            }
            for ((category, groupFields) in groups) {
                // A search shows every match without opening groups by hand.
                val expanded = query.isNotEmpty() || category in open
                item(key = "category:$category") {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { open = if (category in open) open - category else open + category }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            val changed = groupFields.count { it.key in state.edits }
                            Text(
                                text = if (changed > 0) stringResource(R.string.config_changed_count, changed) else groupFields.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (changed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
                if (expanded) {
                    items(groupFields, key = { it.key }) { field ->
                        ConfigFieldRow(
                            field = field,
                            text = state.edits[field.key] ?: field.value,
                            changed = field.key in state.edits,
                            onChange = { viewModel.edit(field.key, it) },
                        )
                    }
                }
            }
        }
        if (state.edits.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.config_changed_count, state.edits.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::discardEdits, enabled = !state.isSaving) {
                        Text(stringResource(R.string.config_discard))
                    }
                    Button(onClick = viewModel::save, enabled = !state.isSaving) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigFieldRow(field: ConfigField, text: String, changed: Boolean, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = field.key,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (field.description.isNotBlank()) {
                    Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (field.type == ConfigField.Type.FLAG) {
                Switch(checked = text == "true", onCheckedChange = { onChange(it.toString()) })
            }
        }
        when (field.type) {
            ConfigField.Type.FLAG -> Unit
            ConfigField.Type.CHOICE -> ChoiceField(field.options, text, onChange)
            ConfigField.Type.NUMBER -> OutlinedTextField(
                value = text,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            ConfigField.Type.LIST -> OutlinedTextField(
                value = text,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            ConfigField.Type.TEXT -> OutlinedTextField(
                value = text,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ChoiceField(options: List<String>, selected: String, onChange: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val none = stringResource(R.string.config_choice_none)
    Box {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifEmpty { none }, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // A value the schema doesn't list (set by hand) stays choosable.
            (if (selected.isNotEmpty() && selected !in options) options + selected else options).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { none }) },
                    onClick = { onChange(option); menu = false },
                )
            }
        }
    }
}
