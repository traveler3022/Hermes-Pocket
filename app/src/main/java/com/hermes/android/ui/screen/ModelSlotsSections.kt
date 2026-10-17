package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.config.AuxiliaryTask
import com.hermes.android.domain.config.MoaPreset
import com.hermes.android.domain.config.MoaSlot
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ModelPickerViewModel
import com.hermes.android.ui.viewmodel.ModelSlotsViewModel

/** What the model sheet is open for. */
private sealed interface SlotPick {
    data class Task(val task: AuxiliaryTask) : SlotPick
    data class AddReference(val preset: String) : SlotPick
    data class Reference(val preset: String, val index: Int) : SlotPick
    data class Aggregator(val preset: String) : SlotPick
}

/** Models › Task models: the model each auxiliary job (vision, titles, compression, …) uses. */
@Composable
internal fun AuxModelsSection(snackbarHostState: SnackbarHostState) {
    val viewModel: ModelSlotsViewModel = hiltViewModel()
    val picker: ModelPickerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerState by picker.uiState.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<SlotPick.Task?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAuxiliary() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    val aux = state.auxiliary
    when {
        aux == null && state.auxLoading -> LoadingIndicator(stringResource(R.string.ops_loading))
        aux == null -> SettingsFolder {
            Text(state.auxError ?: stringResource(R.string.ops_unavailable), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = viewModel::loadAuxiliary) { Text(stringResource(R.string.reload)) }
        }
        else -> SettingsFolder {
            Text(
                stringResource(R.string.aux_intro, aux.mainProvider.ifBlank { "?" } + " / " + aux.mainModel.ifBlank { "?" }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCardGroup {
                aux.tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp)
                    TaskRow(
                        task = task,
                        saving = state.savingTask == task.task,
                        onClick = { pick = SlotPick.Task(task) },
                    )
                }
            }
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = state.savingTask == null && aux.tasks.any { !it.isAuto },
            ) { Text(stringResource(R.string.aux_reset_all)) }
        }
    }

    pick?.let { current ->
        ModelPickerSheet(
            models = pickerState.models,
            favorites = pickerState.favorites,
            activeModel = current.task.model.takeIf { !current.task.isAuto },
            activeProvider = current.task.provider.takeIf { !current.task.isAuto },
            reasoningLevel = "",
            isLoading = pickerState.isLoading,
            onSelectModel = { model ->
                pick = null
                viewModel.setTaskModel(current.task.task, model)
            },
            onToggleFavorite = picker::toggleFavorite,
            onReasoningLevelChange = {},
            onDismiss = { pick = null },
            showEffort = false,
            title = taskLabel(current.task),
            autoOption = {
                pick = null
                if (!current.task.isAuto) viewModel.setTaskModel(current.task.task, null)
            },
            autoSelected = current.task.isAuto,
        )
    }

    state.confirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(confirm.model.modelId) },
            text = { Text(confirm.message) },
            confirmButton = { TextButton(onClick = viewModel::confirmExpensive) { Text(stringResource(R.string.aux_use_anyway)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.aux_reset_all)) },
            text = { Text(stringResource(R.string.aux_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetAuxiliary()
                }) { Text(stringResource(R.string.aux_reset_go)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TaskRow(task: AuxiliaryTask, saving: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !saving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(taskLabel(task), style = MaterialTheme.typography.bodyLarge)
            val hint = task.hint ?: taskHint(task.task)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = when {
                    !task.isAuto -> task.provider + " / " + task.model.ifBlank { "?" }
                    task.effectiveModel != null -> stringResource(R.string.aux_follows, task.effectiveModel)
                    else -> stringResource(R.string.aux_auto)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (task.isAuto) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun taskLabel(task: AuxiliaryTask): String = task.label ?: when (task.task) {
    "vision" -> stringResource(R.string.aux_task_vision)
    "compression" -> stringResource(R.string.aux_task_compression)
    "skills_hub" -> stringResource(R.string.aux_task_skills_hub)
    "approval" -> stringResource(R.string.aux_task_approval)
    "mcp" -> stringResource(R.string.aux_task_mcp)
    "title_generation" -> stringResource(R.string.aux_task_title_generation)
    "review" -> stringResource(R.string.aux_task_review)
    "voice_chat" -> stringResource(R.string.aux_task_voice_chat)
    "triage_specifier" -> stringResource(R.string.aux_task_triage_specifier)
    "kanban_decomposer" -> stringResource(R.string.aux_task_kanban_decomposer)
    "profile_describer" -> stringResource(R.string.aux_task_profile_describer)
    "curator" -> stringResource(R.string.aux_task_curator)
    else -> task.task
}

@Composable
private fun taskHint(task: String): String? = when (task) {
    "vision" -> stringResource(R.string.aux_hint_vision)
    "compression" -> stringResource(R.string.aux_hint_compression)
    "approval" -> stringResource(R.string.aux_hint_approval)
    "title_generation" -> stringResource(R.string.aux_hint_title_generation)
    "review" -> stringResource(R.string.aux_hint_review)
    "curator" -> stringResource(R.string.aux_hint_curator)
    else -> null
}

/** Models › Mixture of Agents: each preset's reference models, aggregator and cadence. */
@Composable
internal fun MoaSection(snackbarHostState: SnackbarHostState) {
    val viewModel: ModelSlotsViewModel = hiltViewModel()
    val picker: ModelPickerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerState by picker.uiState.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<SlotPick?>(null) }

    LaunchedEffect(Unit) { viewModel.loadMoa() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    val draft = state.moaDraft
    when {
        draft == null && state.moaLoading -> LoadingIndicator(stringResource(R.string.ops_loading))
        draft == null -> SettingsFolder {
            Text(state.moaError ?: stringResource(R.string.ops_unavailable), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = viewModel::loadMoa) { Text(stringResource(R.string.reload)) }
        }
        else -> SettingsFolder {
            Text(
                stringResource(R.string.moa_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.presets.size > 1) {
                Text(stringResource(R.string.moa_default_preset), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.presets.keys.forEach { name ->
                        FilterChip(
                            selected = name == draft.defaultPreset,
                            onClick = { viewModel.setDefaultPreset(name) },
                            label = { Text(name) },
                        )
                    }
                }
            }
            draft.presets.forEach { (name, preset) ->
                PresetCard(
                    name = name,
                    preset = preset,
                    isDefault = name == draft.defaultPreset,
                    viewModel = viewModel,
                    onPick = { pick = it },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::saveMoa, enabled = state.moaDirty && !state.savingMoa) {
                    Text(stringResource(R.string.moa_save))
                }
                OutlinedButton(onClick = viewModel::discardMoa, enabled = state.moaDirty && !state.savingMoa) {
                    Text(stringResource(R.string.moa_discard))
                }
                if (state.savingMoa) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }

    pick?.let { current ->
        val presets = draft?.presets.orEmpty()
        val (title, active) = when (current) {
            is SlotPick.AddReference -> stringResource(R.string.moa_add_reference) to null
            is SlotPick.Reference -> stringResource(R.string.moa_reference) to presets[current.preset]?.referenceModels?.getOrNull(current.index)
            is SlotPick.Aggregator -> stringResource(R.string.moa_aggregator) to presets[current.preset]?.aggregator
            is SlotPick.Task -> "" to null
        }
        ModelPickerSheet(
            models = pickerState.models,
            favorites = pickerState.favorites,
            activeModel = active?.model,
            activeProvider = active?.provider,
            reasoningLevel = "",
            isLoading = pickerState.isLoading,
            onSelectModel = { model: ModelOption ->
                pick = null
                when (current) {
                    is SlotPick.AddReference -> viewModel.addReference(current.preset, model)
                    is SlotPick.Reference -> viewModel.replaceReference(current.preset, current.index, model)
                    is SlotPick.Aggregator -> viewModel.setAggregator(current.preset, model)
                    is SlotPick.Task -> Unit
                }
            },
            onToggleFavorite = picker::toggleFavorite,
            onReasoningLevelChange = {},
            onDismiss = { pick = null },
            showEffort = false,
            title = title,
        )
    }
}

@Composable
private fun PresetCard(
    name: String,
    preset: MoaPreset,
    isDefault: Boolean,
    viewModel: ModelSlotsViewModel,
    onPick: (SlotPick) -> Unit,
) {
    SettingsCardGroup {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (isDefault) {
                    Text(
                        stringResource(R.string.moa_is_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = preset.enabled, onCheckedChange = { viewModel.setPresetEnabled(name, it) })
        }

        SlotHeader(stringResource(R.string.moa_references))
        preset.referenceModels.forEachIndexed { index, slot ->
            SlotRow(
                slot = slot,
                onClick = { onPick(SlotPick.Reference(name, index)) },
                trailing = {
                    Switch(
                        checked = slot.enabled,
                        onCheckedChange = { viewModel.setReferenceEnabled(name, index, it) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = { viewModel.removeReference(name, index) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.moa_remove))
                    }
                },
            )
        }
        if (preset.referenceModels.none { it.enabled && it.isComplete }) {
            Text(
                stringResource(R.string.moa_needs_reference),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        TextButton(
            onClick = { onPick(SlotPick.AddReference(name)) },
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.moa_add_reference), modifier = Modifier.padding(start = 6.dp))
        }

        SlotHeader(stringResource(R.string.moa_aggregator))
        SlotRow(slot = preset.aggregator, onClick = { onPick(SlotPick.Aggregator(name)) }, trailing = {})

        SlotHeader(stringResource(R.string.moa_fanout))
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = preset.fanout == "user_turn",
                onClick = { viewModel.setFanout(name, "user_turn") },
                label = { Text(stringResource(R.string.moa_fanout_turn)) },
            )
            FilterChip(
                selected = preset.fanout == "per_iteration",
                onClick = { viewModel.setFanout(name, "per_iteration") },
                label = { Text(stringResource(R.string.moa_fanout_step)) },
            )
            if (preset.fanout.startsWith("every_n:")) {
                FilterChip(selected = true, onClick = {}, label = { Text(preset.fanout) })
            }
        }
    }
}

@Composable
private fun SlotHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SlotRow(slot: MoaSlot, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                slot.model.ifBlank { stringResource(R.string.moa_pick_model) },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (slot.isComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (slot.provider.isNotBlank()) {
                Text(slot.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}
