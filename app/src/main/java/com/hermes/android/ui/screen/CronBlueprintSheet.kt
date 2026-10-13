package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.domain.cron.CronBlueprints.Blueprint
import com.hermes.android.domain.cron.CronBlueprints.Field
import com.hermes.android.domain.cron.CronBlueprints.FieldType
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.BlueprintTexts
import com.hermes.android.ui.viewmodel.CronUiState
import java.util.Locale

/**
 * Hermes' ready-made jobs (automation blueprints), grouped by category. Picking one opens
 * [BlueprintForm]; the job it creates is an ordinary one in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlueprintSheet(
    state: CronUiState,
    onPick: (Blueprint) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val persian = stringResource(R.string.en) == "fa"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
        ) {
            item { SheetHeader(stringResource(R.string.ready_made_jobs), onBack = null) }
            item {
                Text(
                    stringResource(R.string.pick_one_fill_in_a_few_blanks_and_hermes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = HxSpace.sm),
                )
            }
            when {
                state.blueprintsLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(HxSpace.lg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.blueprintsError != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                        Text(
                            stringResource(R.string.couldnt_load_them) + state.blueprintsError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
                    }
                }
                else -> state.blueprints.groupBy { it.category }.forEach { (category, list) ->
                    item(key = "category:$category") {
                        Text(
                            BlueprintTexts.text(category, persian).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = HxSpace.sm),
                        )
                    }
                    items(list, key = { it.key }) { blueprint ->
                        BlueprintRow(blueprint, persian) { onPick(blueprint) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueprintRow(blueprint: Blueprint, persian: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(HxSpace.inner)) {
            Text(BlueprintTexts.text(blueprint.title, persian), style = MaterialTheme.typography.titleSmall)
            if (blueprint.description.isNotBlank()) {
                Text(
                    BlueprintTexts.text(blueprint.description, persian),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (blueprint.scheduleHuman.isNotBlank()) {
                Text(
                    BlueprintTexts.schedule(blueprint.scheduleHuman, persian),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = HxSpace.xs),
                )
            }
        }
    }
}

/**
 * The blanks of one blueprint, prefilled with its defaults. Values go back to Hermes as
 * they are (English option values, `HH:MM` times); Hermes checks them and its reason for
 * a refusal shows here.
 */
@Composable
internal fun BlueprintForm(
    blueprint: Blueprint,
    creating: Boolean,
    error: String?,
    onCreate: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val persian = stringResource(R.string.en) == "fa"
    val values = remember(blueprint.key) {
        mutableStateMapOf<String, String>().apply { blueprint.fields.forEach { put(it.name, it.default) } }
    }
    val missing = blueprint.fields.any { !it.optional && values[it.name].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text(BlueprintTexts.text(blueprint.title, persian)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                blueprint.fields.forEach { field ->
                    FieldInput(field, values[field.name].orEmpty(), persian) { values[field.name] = it }
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !creating && !missing,
                onClick = { onCreate(values.filterValues { it.isNotBlank() }) },
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.create))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !creating, onClick = onDismiss) { Text(stringResource(R.string.cancel_2)) }
        },
    )
}

@Composable
private fun FieldInput(field: Field, value: String, persian: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(BlueprintTexts.text(field.label, persian), style = MaterialTheme.typography.labelMedium)
        when (field.type) {
            FieldType.TIME -> TimeInput(value, onChange)
            FieldType.ENUM, FieldType.WEEKDAYS -> {
                // Hermes' default is sometimes outside the listed options; keep it pickable.
                val options = if (value.isNotBlank() && value !in field.options && field.strict) {
                    listOf(value) + field.options
                } else {
                    field.options
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEach { option ->
                        FilterChip(
                            selected = value == option,
                            onClick = { onChange(option) },
                            label = {
                                Text(BlueprintTexts.text(option, persian), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
                // Suggestions only (where to deliver): any connected platform name is fine.
                if (!field.strict) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FieldType.TEXT -> OutlinedTextField(
                value = value,
                onValueChange = onChange,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (field.help.isNotBlank()) {
            Text(
                BlueprintTexts.text(field.help, persian),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** `HH:MM` as hour and minute chips, like the job editor. */
@Composable
private fun TimeInput(value: String, onChange: (String) -> Unit) {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in 0..23 } ?: 8
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
    // Locale.ROOT: in Persian the default locale writes ۰۸:۳۰, which Hermes refuses.
    fun set(h: Int, m: Int) = onChange(String.format(Locale.ROOT, "%02d:%02d", h, m))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (0..23).forEach { h ->
            FilterChip(
                selected = hour == h,
                onClick = { set(h, minute) },
                label = { Text("%02d".format(h), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
    MinuteChips(selected = minute) { set(hour, it) }
}
